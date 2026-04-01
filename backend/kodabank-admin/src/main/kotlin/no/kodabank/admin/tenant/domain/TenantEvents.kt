package no.kodabank.admin.tenant.domain

sealed interface TenantEvent {
    val tenantId: String
}

data class BankTenantCreated(
    override val tenantId: String,
    val bankName: String,
    val bankCode: String,
    val ibanPrefix: String,
    val country: String,
    val currency: String,
    val branding: BrandingInfo
) : TenantEvent

data class BankTenantRegistered(
    override val tenantId: String,
    val ownerUserId: String,
    val bankName: String,
    val bankCode: String,
    val ibanPrefix: String,
    val country: String,
    val currency: String,
    val accessPolicy: AccessPolicy,
    val transferPolicy: TransferPolicy
) : TenantEvent

data class TransferPolicyUpdated(
    override val tenantId: String,
    val policyType: TransferPolicyType,
    val whitelist: List<String> = emptyList(),
    val domainCode: String? = null
) : TenantEvent

data class AccessPolicyUpdated(
    override val tenantId: String,
    val policyType: AccessPolicyType,
    val webhookUrl: String? = null
) : TenantEvent

data class BankBrandingUpdated(
    override val tenantId: String,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val logo: String? = null,
    val tagline: String? = null
) : TenantEvent

data class NostroAccountConfigured(
    override val tenantId: String,
    val nostroAccountId: String,
    val initialBalance: java.math.BigDecimal
) : TenantEvent

data class BrandingInfo(
    val primaryColor: String,
    val secondaryColor: String,
    val logo: String,
    val tagline: String
)

// -- Policy types --

enum class TransferPolicyType { OPEN, CLOSED, WHITELIST, DOMAIN_CODE }
enum class AccessPolicyType { AUTO_APPROVE, MANUAL, WEBHOOK }

data class TransferPolicy(
    val type: TransferPolicyType,
    val whitelist: List<String> = emptyList(),
    val domainCode: String? = null
)

data class AccessPolicy(
    val type: AccessPolicyType,
    val webhookUrl: String? = null
)

// -- Tenant status --

enum class TenantStatus { PENDING, ACTIVE, SUSPENDED }
