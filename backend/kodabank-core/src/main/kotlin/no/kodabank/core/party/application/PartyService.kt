package no.kodabank.core.party.application

import no.kodabank.core.party.domain.*
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.TenantId
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class PartyService(
    private val store: TenantAwareClient
) {
    companion object {
        private const val CATEGORY = "Party"
    }

    fun registerParty(
        tenantId: TenantId,
        nationalId: String,
        firstName: String,
        lastName: String,
        email: String? = null,
        phone: String? = null,
        address: String? = null,
        correlationId: String? = null
    ): PartyState {
        val partyId = UUID.randomUUID().toString()
        val event = PartyRegistered(
            partyId = partyId,
            tenantId = tenantId.value,
            nationalIdHash = hashNationalId(nationalId),
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone,
            address = address
        )
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, partyId, null, listOf(toRequest(event, metadata)))
        return loadParty(tenantId, partyId)
    }

    fun verifyIdentity(
        tenantId: TenantId,
        partyId: String,
        verificationMethod: String = "BankID",
        correlationId: String? = null
    ): PartyState {
        val current = loadParty(tenantId, partyId)
        val event = PartyIdentityVerified(
            partyId = partyId,
            verificationMethod = verificationMethod,
            verifiedAt = Instant.now().toString(),
            level = "HIGH"
        )
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, partyId, current.version, listOf(toRequest(event, metadata)))
        return loadParty(tenantId, partyId)
    }

    fun getParty(tenantId: TenantId, partyId: String): PartyState {
        return loadParty(tenantId, partyId)
    }

    private fun loadParty(tenantId: TenantId, partyId: String): PartyState {
        val stream = store.readStream(CATEGORY, tenantId, partyId)
        if (stream.events.isEmpty()) {
            throw PartyNotFoundException(partyId)
        }
        val events = stream.events.map { fromRecorded(it) }
        return PartyState.rebuild(events)
    }

    private fun toRequest(event: PartyEvent, metadata: Map<String, Any?>): NewEventRequest {
        val (eventType, payload) = when (event) {
            is PartyRegistered -> "PartyRegistered" to mapOf<String, Any?>(
                "partyId" to event.partyId, "tenantId" to event.tenantId,
                "nationalIdHash" to event.nationalIdHash,
                "firstName" to event.firstName, "lastName" to event.lastName,
                "email" to event.email, "phone" to event.phone, "address" to event.address
            )
            is PartyContactUpdated -> "PartyContactUpdated" to mapOf<String, Any?>(
                "partyId" to event.partyId, "email" to event.email,
                "phone" to event.phone, "address" to event.address
            )
            is PartyIdentityVerified -> "PartyIdentityVerified" to mapOf<String, Any?>(
                "partyId" to event.partyId, "verificationMethod" to event.verificationMethod,
                "verifiedAt" to event.verifiedAt, "level" to event.level
            )
            is PartyStatusChanged -> "PartyStatusChanged" to mapOf<String, Any?>(
                "partyId" to event.partyId, "oldStatus" to event.oldStatus,
                "newStatus" to event.newStatus, "reason" to event.reason
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    private fun fromRecorded(recorded: RecordedEvent): Pair<PartyEvent, Int> {
        val p = recorded.payload
        val event: PartyEvent = when (recorded.eventType) {
            "PartyRegistered" -> PartyRegistered(
                partyId = p["partyId"] as String,
                tenantId = p["tenantId"] as String,
                nationalIdHash = p["nationalIdHash"] as String,
                firstName = p["firstName"] as String,
                lastName = p["lastName"] as String,
                email = p["email"] as? String,
                phone = p["phone"] as? String,
                address = p["address"] as? String
            )
            "PartyContactUpdated" -> PartyContactUpdated(
                partyId = p["partyId"] as String,
                email = p["email"] as? String,
                phone = p["phone"] as? String,
                address = p["address"] as? String
            )
            "PartyIdentityVerified" -> PartyIdentityVerified(
                partyId = p["partyId"] as String,
                verificationMethod = p["verificationMethod"] as String,
                verifiedAt = p["verifiedAt"] as String,
                level = p["level"] as String
            )
            "PartyStatusChanged" -> PartyStatusChanged(
                partyId = p["partyId"] as String,
                oldStatus = p["oldStatus"] as String,
                newStatus = p["newStatus"] as String,
                reason = p["reason"] as String
            )
            else -> throw IllegalArgumentException("Unknown event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }

    private fun hashNationalId(nationalId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(nationalId.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun buildMetadata(correlationId: String?, tenantId: TenantId): Map<String, Any?> =
        buildMap {
            put("tenantId", tenantId.value)
            put("sourceService", "kodabank-core")
            correlationId?.let { put("correlationId", it) }
        }
}

class PartyNotFoundException(partyId: String) : RuntimeException("Party not found: $partyId")
