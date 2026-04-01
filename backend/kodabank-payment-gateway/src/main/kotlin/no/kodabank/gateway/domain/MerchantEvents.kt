package no.kodabank.gateway.domain

sealed interface MerchantEvent {
    val merchantId: String
}

data class MerchantRegistered(
    override val merchantId: String,
    val tenantId: String,
    val merchantName: String,
    val apiKeyHash: String,
    val callbackUrl: String,
    val createdAt: String
) : MerchantEvent

data class MerchantApiKeyRotated(
    override val merchantId: String,
    val newApiKeyHash: String,
    val rotatedAt: String
) : MerchantEvent

data class MerchantDeactivated(
    override val merchantId: String,
    val reason: String,
    val deactivatedAt: String
) : MerchantEvent
