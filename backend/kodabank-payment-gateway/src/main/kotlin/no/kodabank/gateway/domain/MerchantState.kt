package no.kodabank.gateway.domain

data class MerchantState(
    val merchantId: String,
    val tenantId: String,
    val merchantName: String,
    val apiKeyHash: String,
    val callbackUrl: String,
    val active: Boolean,
    val createdAt: String,
    val version: Int
) {
    companion object {
        val EMPTY = MerchantState("", "", "", "", "", false, "", 0)

        fun evolve(state: MerchantState, event: MerchantEvent, version: Int): MerchantState =
            when (event) {
                is MerchantRegistered -> state.copy(
                    merchantId = event.merchantId,
                    tenantId = event.tenantId,
                    merchantName = event.merchantName,
                    apiKeyHash = event.apiKeyHash,
                    callbackUrl = event.callbackUrl,
                    active = true,
                    createdAt = event.createdAt,
                    version = version
                )
                is MerchantApiKeyRotated -> state.copy(
                    apiKeyHash = event.newApiKeyHash,
                    version = version
                )
                is MerchantDeactivated -> state.copy(
                    active = false,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<MerchantEvent, Int>>): MerchantState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
