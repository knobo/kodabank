package no.kodabank.admin.tenant.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.kodabank.admin.tenant.domain.*
import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.StreamIds
import no.kodabank.shared.domain.TenantId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class TenantProvisioningService(
    private val client: KodaStoreClient,
    private val tenantClient: TenantAwareClient,
    private val keycloakAdminService: KeycloakAdminService
) {
    private val log = LoggerFactory.getLogger(TenantProvisioningService::class.java)

    companion object {
        const val TENANT_CATEGORY = "BankTenant"
        const val PRODUCT_CATEGORY = "ProductCatalog"

        private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    }

    // -- Tenant operations --

    fun createTenant(definition: BankDefinition): TenantState {
        val tenant = definition.tenant
        val tenantId = TenantId(tenant.id)
        val ibanPrefix = "NO${tenant.bankCode}"

        val event = BankTenantCreated(
            tenantId = tenant.id,
            bankName = tenant.bankName,
            bankCode = tenant.bankCode,
            ibanPrefix = ibanPrefix,
            country = tenant.country,
            currency = tenant.currency,
            branding = BrandingInfo(
                primaryColor = tenant.branding.primaryColor,
                secondaryColor = tenant.branding.secondaryColor,
                logo = tenant.branding.logo,
                tagline = tenant.branding.tagline
            )
        )

        val streamId = StreamIds.tenantStreamId(TENANT_CATEGORY, tenantId)
        client.append(
            streamId = streamId,
            expectedVersion = null,
            events = listOf(toEventRequest("BankTenantCreated", event))
        )

        log.info("Created bank tenant: {} ({})", tenant.bankName, tenant.id)

        // Create products
        for (product in definition.products) {
            createProduct(tenantId, product)
        }

        return loadTenant(tenantId) ?: error("Failed to load tenant after creation: ${tenant.id}")
    }

    fun registerBank(
        ownerUserId: String,
        bankName: String,
        currency: String,
        branding: BrandingInfo,
        accessPolicy: AccessPolicy,
        transferPolicy: TransferPolicy
    ): TenantState {
        val bankCode = generateNextBankCode()
        val tenantId = "bank${UUID.randomUUID().toString().replace("-", "").substring(0, 8)}"
        val ibanPrefix = "NO$bankCode"
        val country = "NO"

        val event = BankTenantRegistered(
            tenantId = tenantId,
            ownerUserId = ownerUserId,
            bankName = bankName,
            bankCode = bankCode,
            ibanPrefix = ibanPrefix,
            country = country,
            currency = currency,
            accessPolicy = accessPolicy,
            transferPolicy = transferPolicy
        )

        val streamId = StreamIds.tenantStreamId(TENANT_CATEGORY, TenantId(tenantId))
        client.append(
            streamId = streamId,
            expectedVersion = null,
            events = listOf(toEventRequest("BankTenantRegistered", event))
        )

        log.info("Registered bank tenant: {} ({}) by owner {}", bankName, tenantId, ownerUserId)

        // Create Keycloak client scope so tenantId is available as a JWT claim
        try {
            keycloakAdminService.createTenantScope(tenantId)
        } catch (e: Exception) {
            log.error("Failed to create Keycloak scope for tenant {}: {}", tenantId, e.message, e)
            // Don't fail the registration - the scope can be created manually later
        }

        return loadTenant(TenantId(tenantId)) ?: error("Failed to load tenant after registration: $tenantId")
    }

    fun updateTransferPolicy(tenantId: TenantId, transferPolicy: TransferPolicy): TenantState {
        val currentState = loadTenant(tenantId)
            ?: throw IllegalArgumentException("Tenant not found: ${tenantId.value}")

        val event = TransferPolicyUpdated(
            tenantId = tenantId.value,
            policyType = transferPolicy.type,
            whitelist = transferPolicy.whitelist,
            domainCode = transferPolicy.domainCode
        )

        val streamId = StreamIds.tenantStreamId(TENANT_CATEGORY, tenantId)
        client.append(
            streamId = streamId,
            expectedVersion = currentState.version,
            events = listOf(toEventRequest("TransferPolicyUpdated", event))
        )

        log.info("Updated transfer policy for tenant: {} to {}", tenantId.value, transferPolicy.type)
        return loadTenant(tenantId)!!
    }

    fun updateAccessPolicy(tenantId: TenantId, accessPolicy: AccessPolicy): TenantState {
        val currentState = loadTenant(tenantId)
            ?: throw IllegalArgumentException("Tenant not found: ${tenantId.value}")

        val event = AccessPolicyUpdated(
            tenantId = tenantId.value,
            policyType = accessPolicy.type,
            webhookUrl = accessPolicy.webhookUrl
        )

        val streamId = StreamIds.tenantStreamId(TENANT_CATEGORY, tenantId)
        client.append(
            streamId = streamId,
            expectedVersion = currentState.version,
            events = listOf(toEventRequest("AccessPolicyUpdated", event))
        )

        log.info("Updated access policy for tenant: {} to {}", tenantId.value, accessPolicy.type)
        return loadTenant(tenantId)!!
    }

    fun importFromYaml(yamlContent: String): BankDefinition {
        return yamlMapper.readValue(yamlContent, BankDefinition::class.java)
    }

    fun updateBranding(tenantId: TenantId, branding: BankBrandingUpdated): TenantState {
        val currentState = loadTenant(tenantId)
            ?: throw IllegalArgumentException("Tenant not found: ${tenantId.value}")

        val streamId = StreamIds.tenantStreamId(TENANT_CATEGORY, tenantId)
        client.append(
            streamId = streamId,
            expectedVersion = currentState.version,
            events = listOf(toEventRequest("BankBrandingUpdated", branding))
        )

        log.info("Updated branding for tenant: {}", tenantId.value)
        return loadTenant(tenantId)!!
    }

    fun loadTenant(tenantId: TenantId): TenantState? {
        val streamId = StreamIds.tenantStreamId(TENANT_CATEGORY, tenantId)
        val state = client.readStream(streamId)
        if (state.events.isEmpty()) return null

        val events = state.events.map { recordedEvent ->
            val event = deserializeTenantEvent(recordedEvent)
            event to recordedEvent.streamVersion
        }
        return TenantState.rebuild(events)
    }

    fun listTenants(): List<TenantState> {
        val allEvents = client.readCategory(TENANT_CATEGORY)
        if (allEvents.isEmpty()) return emptyList()

        // Group events by streamId, then rebuild each tenant
        return allEvents
            .groupBy { it.streamId }
            .map { (_, events) ->
                val tenantEvents = events.map { recordedEvent ->
                    deserializeTenantEvent(recordedEvent) to recordedEvent.streamVersion
                }
                TenantState.rebuild(tenantEvents)
            }
            .filter { it.tenantId.isNotBlank() }
    }

    // -- Product operations --

    fun createProduct(tenantId: TenantId, product: ProductDefinition) {
        val event = ProductDefined(
            tenantId = tenantId.value,
            productId = product.id,
            name = product.name,
            type = product.type,
            fees = product.fees,
            features = product.features,
            interestRate = product.interestRate
        )

        val streamId = StreamIds.tenantStreamId(PRODUCT_CATEGORY, tenantId)
        client.append(
            streamId = streamId,
            expectedVersion = null,
            events = listOf(toEventRequest("ProductDefined", event))
        )

        log.info("Defined product '{}' for tenant: {}", product.name, tenantId.value)
    }

    fun listProducts(tenantId: TenantId): List<ProductDefined> {
        val streamId = StreamIds.tenantStreamId(PRODUCT_CATEGORY, tenantId)
        val state = client.readStream(streamId)
        return state.events
            .filter { it.eventType == "ProductDefined" }
            .map { deserializeProductDefined(it) }
    }

    // -- Bank code generation --

    /**
     * Generates the next available 4-digit bank code by scanning existing tenants.
     * Starts at 1000 and increments, skipping codes already in use.
     */
    private fun generateNextBankCode(): String {
        val existingCodes = listTenants().map { it.bankCode }.toSet()
        var code = 1000
        while (code <= 9999) {
            val candidate = code.toString()
            if (candidate !in existingCodes) {
                return candidate
            }
            code++
        }
        throw IllegalStateException("No available bank codes remaining")
    }

    // -- Event serialization helpers --

    private fun toEventRequest(eventType: String, event: Any): NewEventRequest {
        @Suppress("UNCHECKED_CAST")
        val payload = yamlMapper.convertValue(event, Map::class.java) as Map<String, Any?>
        return NewEventRequest(eventType = eventType, payload = payload)
    }

    private fun deserializeTenantEvent(recorded: RecordedEvent): TenantEvent {
        val payload = recorded.payload
        return when (recorded.eventType) {
            "BankTenantCreated" -> {
                @Suppress("UNCHECKED_CAST")
                val brandingMap = payload["branding"] as? Map<String, Any?> ?: emptyMap()
                BankTenantCreated(
                    tenantId = payload["tenantId"] as String,
                    bankName = payload["bankName"] as String,
                    bankCode = payload["bankCode"] as String,
                    ibanPrefix = payload["ibanPrefix"] as String,
                    country = payload["country"] as String,
                    currency = payload["currency"] as String,
                    branding = BrandingInfo(
                        primaryColor = brandingMap["primaryColor"] as? String ?: "",
                        secondaryColor = brandingMap["secondaryColor"] as? String ?: "",
                        logo = brandingMap["logo"] as? String ?: "",
                        tagline = brandingMap["tagline"] as? String ?: ""
                    )
                )
            }
            "BankTenantRegistered" -> {
                @Suppress("UNCHECKED_CAST")
                val accessPolicyMap = payload["accessPolicy"] as? Map<String, Any?> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val transferPolicyMap = payload["transferPolicy"] as? Map<String, Any?> ?: emptyMap()
                BankTenantRegistered(
                    tenantId = payload["tenantId"] as String,
                    ownerUserId = payload["ownerUserId"] as String,
                    bankName = payload["bankName"] as String,
                    bankCode = payload["bankCode"] as String,
                    ibanPrefix = payload["ibanPrefix"] as String,
                    country = payload["country"] as String,
                    currency = payload["currency"] as String,
                    accessPolicy = AccessPolicy(
                        type = AccessPolicyType.valueOf(accessPolicyMap["type"] as String),
                        webhookUrl = accessPolicyMap["webhookUrl"] as? String
                    ),
                    transferPolicy = TransferPolicy(
                        type = TransferPolicyType.valueOf(transferPolicyMap["type"] as String),
                        whitelist = (transferPolicyMap["whitelist"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        domainCode = transferPolicyMap["domainCode"] as? String
                    )
                )
            }
            "TransferPolicyUpdated" -> {
                @Suppress("UNCHECKED_CAST")
                TransferPolicyUpdated(
                    tenantId = payload["tenantId"] as String,
                    policyType = TransferPolicyType.valueOf(payload["policyType"] as String),
                    whitelist = (payload["whitelist"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    domainCode = payload["domainCode"] as? String
                )
            }
            "AccessPolicyUpdated" -> AccessPolicyUpdated(
                tenantId = payload["tenantId"] as String,
                policyType = AccessPolicyType.valueOf(payload["policyType"] as String),
                webhookUrl = payload["webhookUrl"] as? String
            )
            "BankBrandingUpdated" -> BankBrandingUpdated(
                tenantId = payload["tenantId"] as String,
                primaryColor = payload["primaryColor"] as? String,
                secondaryColor = payload["secondaryColor"] as? String,
                logo = payload["logo"] as? String,
                tagline = payload["tagline"] as? String
            )
            "NostroAccountConfigured" -> NostroAccountConfigured(
                tenantId = payload["tenantId"] as String,
                nostroAccountId = payload["nostroAccountId"] as String,
                initialBalance = toBigDecimal(payload["initialBalance"])
            )
            else -> throw IllegalArgumentException("Unknown tenant event type: ${recorded.eventType}")
        }
    }

    private fun deserializeProductDefined(recorded: RecordedEvent): ProductDefined {
        val payload = recorded.payload
        @Suppress("UNCHECKED_CAST")
        return ProductDefined(
            tenantId = payload["tenantId"] as String,
            productId = payload["productId"] as String,
            name = payload["name"] as String,
            type = payload["type"] as String,
            fees = payload["fees"] as? Map<String, Any?> ?: emptyMap(),
            features = payload["features"] as? Map<String, Any?> ?: emptyMap(),
            interestRate = (payload["interestRate"])?.let { toBigDecimal(it) }
        )
    }

    private fun toBigDecimal(value: Any?): BigDecimal = when (value) {
        is BigDecimal -> value
        is Number -> BigDecimal(value.toString())
        is String -> BigDecimal(value)
        else -> BigDecimal.ZERO
    }
}
