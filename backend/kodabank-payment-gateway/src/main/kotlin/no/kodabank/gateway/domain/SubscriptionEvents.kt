package no.kodabank.gateway.domain

import java.math.BigDecimal

enum class SubscriptionInterval {
    DAILY, WEEKLY, MONTHLY
}

sealed interface SubscriptionEvent {
    val subscriptionId: String
}

data class SubscriptionCreated(
    override val subscriptionId: String,
    val merchantId: String,
    val tenantId: String,
    val userId: String,
    val payerAccountId: String,
    val amount: BigDecimal,
    val currency: String,
    val interval: SubscriptionInterval,
    val description: String,
    val startDate: String
) : SubscriptionEvent

data class SubscriptionCharged(
    override val subscriptionId: String,
    val chargeId: String,
    val amount: BigDecimal,
    val chargedAt: String
) : SubscriptionEvent

data class SubscriptionPaused(
    override val subscriptionId: String,
    val reason: String,
    val pausedAt: String
) : SubscriptionEvent

data class SubscriptionResumed(
    override val subscriptionId: String,
    val resumedAt: String
) : SubscriptionEvent

data class SubscriptionCancelled(
    override val subscriptionId: String,
    val reason: String,
    val cancelledAt: String
) : SubscriptionEvent
