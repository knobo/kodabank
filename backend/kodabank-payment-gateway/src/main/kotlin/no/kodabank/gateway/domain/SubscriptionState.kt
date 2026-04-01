package no.kodabank.gateway.domain

import java.math.BigDecimal

enum class SubscriptionStatus {
    ACTIVE, PAUSED, CANCELLED
}

data class SubscriptionState(
    val subscriptionId: String,
    val merchantId: String,
    val tenantId: String,
    val userId: String,
    val payerAccountId: String,
    val amount: BigDecimal,
    val currency: String,
    val interval: SubscriptionInterval,
    val description: String,
    val startDate: String,
    val status: SubscriptionStatus,
    val totalCharged: BigDecimal,
    val chargeCount: Int,
    val version: Int
) {
    companion object {
        val EMPTY = SubscriptionState(
            "", "", "", "", "", BigDecimal.ZERO, "", SubscriptionInterval.MONTHLY,
            "", "", SubscriptionStatus.ACTIVE, BigDecimal.ZERO, 0, 0
        )

        fun evolve(state: SubscriptionState, event: SubscriptionEvent, version: Int): SubscriptionState =
            when (event) {
                is SubscriptionCreated -> state.copy(
                    subscriptionId = event.subscriptionId,
                    merchantId = event.merchantId,
                    tenantId = event.tenantId,
                    userId = event.userId,
                    payerAccountId = event.payerAccountId,
                    amount = event.amount,
                    currency = event.currency,
                    interval = event.interval,
                    description = event.description,
                    startDate = event.startDate,
                    status = SubscriptionStatus.ACTIVE,
                    version = version
                )
                is SubscriptionCharged -> state.copy(
                    totalCharged = state.totalCharged + event.amount,
                    chargeCount = state.chargeCount + 1,
                    version = version
                )
                is SubscriptionPaused -> state.copy(
                    status = SubscriptionStatus.PAUSED,
                    version = version
                )
                is SubscriptionResumed -> state.copy(
                    status = SubscriptionStatus.ACTIVE,
                    version = version
                )
                is SubscriptionCancelled -> state.copy(
                    status = SubscriptionStatus.CANCELLED,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<SubscriptionEvent, Int>>): SubscriptionState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
