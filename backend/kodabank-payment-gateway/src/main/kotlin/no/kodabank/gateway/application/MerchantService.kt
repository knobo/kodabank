package no.kodabank.gateway.application

import no.kodabank.gateway.domain.*
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.TenantId
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class MerchantService(
    private val store: TenantAwareClient
) {
    fun register(
        tenantId: TenantId,
        merchantName: String,
        callbackUrl: String
    ): Pair<MerchantState, String> {
        val merchantId = UUID.randomUUID().toString()
        val rawApiKey = generateApiKey()
        val apiKeyHash = hashApiKey(rawApiKey)

        val event = MerchantRegistered(
            merchantId = merchantId,
            tenantId = tenantId.value,
            merchantName = merchantName,
            apiKeyHash = apiKeyHash,
            callbackUrl = callbackUrl,
            createdAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, merchantId, null,
            listOf(merchantEventToRequest(event))
        )

        return loadMerchant(tenantId, merchantId) to rawApiKey
    }

    fun rotateApiKey(tenantId: TenantId, merchantId: String): Pair<MerchantState, String> {
        val merchant = loadMerchant(tenantId, merchantId)
        require(merchant.active) { "Merchant is not active" }

        val rawApiKey = generateApiKey()
        val apiKeyHash = hashApiKey(rawApiKey)

        val event = MerchantApiKeyRotated(
            merchantId = merchantId,
            newApiKeyHash = apiKeyHash,
            rotatedAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, merchantId, merchant.version,
            listOf(merchantEventToRequest(event))
        )

        return loadMerchant(tenantId, merchantId) to rawApiKey
    }

    fun deactivate(tenantId: TenantId, merchantId: String, reason: String): MerchantState {
        val merchant = loadMerchant(tenantId, merchantId)
        require(merchant.active) { "Merchant is already deactivated" }

        val event = MerchantDeactivated(
            merchantId = merchantId,
            reason = reason,
            deactivatedAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, merchantId, merchant.version,
            listOf(merchantEventToRequest(event))
        )

        return loadMerchant(tenantId, merchantId)
    }

    fun getMerchant(tenantId: TenantId, merchantId: String): MerchantState =
        loadMerchant(tenantId, merchantId)

    /**
     * Find a merchant by API key hash. Scans all merchant streams in the category.
     */
    fun findByApiKeyHash(apiKeyHash: String): MerchantState? {
        val events = store.readCategory(CATEGORY)
        if (events.isEmpty()) return null

        // Group events by stream and rebuild each merchant to check apiKeyHash
        val byStream = events.groupBy { it.streamId }
        for ((_, streamEvents) in byStream) {
            val parsed = streamEvents.map { parseMerchantEvent(it) }
            val state = MerchantState.rebuild(parsed)
            if (state.apiKeyHash == apiKeyHash && state.active) {
                return state
            }
        }
        return null
    }

    private fun loadMerchant(tenantId: TenantId, merchantId: String): MerchantState {
        val stream = store.readStream(CATEGORY, tenantId, merchantId)
        if (stream.events.isEmpty()) throw MerchantNotFoundException(merchantId)
        val parsed = stream.events.map { parseMerchantEvent(it) }
        return MerchantState.rebuild(parsed)
    }

    private fun merchantEventToRequest(event: MerchantEvent): NewEventRequest {
        val metadata = mapOf<String, Any?>("sourceService" to "kodabank-payment-gateway")
        val (eventType, payload) = when (event) {
            is MerchantRegistered -> "MerchantRegistered" to mapOf<String, Any?>(
                "merchantId" to event.merchantId,
                "tenantId" to event.tenantId,
                "merchantName" to event.merchantName,
                "apiKeyHash" to event.apiKeyHash,
                "callbackUrl" to event.callbackUrl,
                "createdAt" to event.createdAt
            )
            is MerchantApiKeyRotated -> "MerchantApiKeyRotated" to mapOf<String, Any?>(
                "merchantId" to event.merchantId,
                "newApiKeyHash" to event.newApiKeyHash,
                "rotatedAt" to event.rotatedAt
            )
            is MerchantDeactivated -> "MerchantDeactivated" to mapOf<String, Any?>(
                "merchantId" to event.merchantId,
                "reason" to event.reason,
                "deactivatedAt" to event.deactivatedAt
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    private fun parseMerchantEvent(recorded: RecordedEvent): Pair<MerchantEvent, Int> {
        val p = recorded.payload
        val event: MerchantEvent = when (recorded.eventType) {
            "MerchantRegistered" -> MerchantRegistered(
                merchantId = p["merchantId"] as String,
                tenantId = p["tenantId"] as String,
                merchantName = p["merchantName"] as String,
                apiKeyHash = p["apiKeyHash"] as String,
                callbackUrl = p["callbackUrl"] as String,
                createdAt = p["createdAt"] as String
            )
            "MerchantApiKeyRotated" -> MerchantApiKeyRotated(
                merchantId = p["merchantId"] as String,
                newApiKeyHash = p["newApiKeyHash"] as String,
                rotatedAt = p["rotatedAt"] as String
            )
            "MerchantDeactivated" -> MerchantDeactivated(
                merchantId = p["merchantId"] as String,
                reason = p["reason"] as String,
                deactivatedAt = p["deactivatedAt"] as String
            )
            else -> throw IllegalArgumentException("Unknown event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }

    companion object {
        const val CATEGORY = "Merchant"

        fun hashApiKey(rawKey: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(rawKey.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

        private fun generateApiKey(): String =
            "pgw_" + UUID.randomUUID().toString().replace("-", "")
    }
}

class MerchantNotFoundException(merchantId: String) :
    RuntimeException("Merchant not found: $merchantId")
