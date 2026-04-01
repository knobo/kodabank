package no.kodabank.gateway.application

import no.kodabank.gateway.domain.*
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.TenantId
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class SubscriptionService(
    private val store: TenantAwareClient
) {
    companion object {
        const val CATEGORY = "Subscription"
    }

    fun create(
        merchantId: String,
        tenantId: TenantId,
        userId: String,
        payerAccountId: String,
        amount: BigDecimal,
        currency: String,
        interval: SubscriptionInterval,
        description: String,
        startDate: String
    ): SubscriptionState {
        val subscriptionId = UUID.randomUUID().toString()

        val event = SubscriptionCreated(
            subscriptionId = subscriptionId,
            merchantId = merchantId,
            tenantId = tenantId.value,
            userId = userId,
            payerAccountId = payerAccountId,
            amount = amount,
            currency = currency,
            interval = interval,
            description = description,
            startDate = startDate
        )

        store.append(
            CATEGORY, tenantId, subscriptionId, null,
            listOf(subscriptionEventToRequest(event))
        )

        return loadSubscription(tenantId, subscriptionId)
    }

    fun charge(tenantId: TenantId, subscriptionId: String): SubscriptionState {
        val subscription = loadSubscription(tenantId, subscriptionId)
        require(subscription.status == SubscriptionStatus.ACTIVE) {
            "Subscription must be ACTIVE to charge, current: ${subscription.status}"
        }

        val chargeId = UUID.randomUUID().toString()
        val event = SubscriptionCharged(
            subscriptionId = subscriptionId,
            chargeId = chargeId,
            amount = subscription.amount,
            chargedAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, subscriptionId, subscription.version,
            listOf(subscriptionEventToRequest(event))
        )

        return loadSubscription(tenantId, subscriptionId)
    }

    fun pause(tenantId: TenantId, subscriptionId: String, reason: String): SubscriptionState {
        val subscription = loadSubscription(tenantId, subscriptionId)
        require(subscription.status == SubscriptionStatus.ACTIVE) {
            "Subscription must be ACTIVE to pause, current: ${subscription.status}"
        }

        val event = SubscriptionPaused(
            subscriptionId = subscriptionId,
            reason = reason,
            pausedAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, subscriptionId, subscription.version,
            listOf(subscriptionEventToRequest(event))
        )

        return loadSubscription(tenantId, subscriptionId)
    }

    fun resume(tenantId: TenantId, subscriptionId: String): SubscriptionState {
        val subscription = loadSubscription(tenantId, subscriptionId)
        require(subscription.status == SubscriptionStatus.PAUSED) {
            "Subscription must be PAUSED to resume, current: ${subscription.status}"
        }

        val event = SubscriptionResumed(
            subscriptionId = subscriptionId,
            resumedAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, subscriptionId, subscription.version,
            listOf(subscriptionEventToRequest(event))
        )

        return loadSubscription(tenantId, subscriptionId)
    }

    fun cancel(tenantId: TenantId, subscriptionId: String, reason: String): SubscriptionState {
        val subscription = loadSubscription(tenantId, subscriptionId)
        require(subscription.status != SubscriptionStatus.CANCELLED) {
            "Subscription is already cancelled"
        }

        val event = SubscriptionCancelled(
            subscriptionId = subscriptionId,
            reason = reason,
            cancelledAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, subscriptionId, subscription.version,
            listOf(subscriptionEventToRequest(event))
        )

        return loadSubscription(tenantId, subscriptionId)
    }

    fun getSubscription(tenantId: TenantId, subscriptionId: String): SubscriptionState =
        loadSubscription(tenantId, subscriptionId)

    private fun loadSubscription(tenantId: TenantId, subscriptionId: String): SubscriptionState {
        val stream = store.readStream(CATEGORY, tenantId, subscriptionId)
        if (stream.events.isEmpty()) throw SubscriptionNotFoundException(subscriptionId)
        val parsed = stream.events.map { parseSubscriptionEvent(it) }
        return SubscriptionState.rebuild(parsed)
    }

    private fun subscriptionEventToRequest(event: SubscriptionEvent): NewEventRequest {
        val metadata = mapOf<String, Any?>("sourceService" to "kodabank-payment-gateway")
        val (eventType, payload) = when (event) {
            is SubscriptionCreated -> "SubscriptionCreated" to mapOf<String, Any?>(
                "subscriptionId" to event.subscriptionId,
                "merchantId" to event.merchantId,
                "tenantId" to event.tenantId,
                "userId" to event.userId,
                "payerAccountId" to event.payerAccountId,
                "amount" to event.amount,
                "currency" to event.currency,
                "interval" to event.interval.name,
                "description" to event.description,
                "startDate" to event.startDate
            )
            is SubscriptionCharged -> "SubscriptionCharged" to mapOf<String, Any?>(
                "subscriptionId" to event.subscriptionId,
                "chargeId" to event.chargeId,
                "amount" to event.amount,
                "chargedAt" to event.chargedAt
            )
            is SubscriptionPaused -> "SubscriptionPaused" to mapOf<String, Any?>(
                "subscriptionId" to event.subscriptionId,
                "reason" to event.reason,
                "pausedAt" to event.pausedAt
            )
            is SubscriptionResumed -> "SubscriptionResumed" to mapOf<String, Any?>(
                "subscriptionId" to event.subscriptionId,
                "resumedAt" to event.resumedAt
            )
            is SubscriptionCancelled -> "SubscriptionCancelled" to mapOf<String, Any?>(
                "subscriptionId" to event.subscriptionId,
                "reason" to event.reason,
                "cancelledAt" to event.cancelledAt
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    private fun parseSubscriptionEvent(recorded: RecordedEvent): Pair<SubscriptionEvent, Int> {
        val p = recorded.payload
        val event: SubscriptionEvent = when (recorded.eventType) {
            "SubscriptionCreated" -> SubscriptionCreated(
                subscriptionId = p["subscriptionId"] as String,
                merchantId = p["merchantId"] as String,
                tenantId = p["tenantId"] as String,
                userId = p["userId"] as String,
                payerAccountId = p["payerAccountId"] as String,
                amount = BigDecimal(p["amount"].toString()),
                currency = p["currency"] as String,
                interval = SubscriptionInterval.valueOf(p["interval"] as String),
                description = p["description"] as String,
                startDate = p["startDate"] as String
            )
            "SubscriptionCharged" -> SubscriptionCharged(
                subscriptionId = p["subscriptionId"] as String,
                chargeId = p["chargeId"] as String,
                amount = BigDecimal(p["amount"].toString()),
                chargedAt = p["chargedAt"] as String
            )
            "SubscriptionPaused" -> SubscriptionPaused(
                subscriptionId = p["subscriptionId"] as String,
                reason = p["reason"] as String,
                pausedAt = p["pausedAt"] as String
            )
            "SubscriptionResumed" -> SubscriptionResumed(
                subscriptionId = p["subscriptionId"] as String,
                resumedAt = p["resumedAt"] as String
            )
            "SubscriptionCancelled" -> SubscriptionCancelled(
                subscriptionId = p["subscriptionId"] as String,
                reason = p["reason"] as String,
                cancelledAt = p["cancelledAt"] as String
            )
            else -> throw IllegalArgumentException("Unknown event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }
}

class SubscriptionNotFoundException(subscriptionId: String) :
    RuntimeException("Subscription not found: $subscriptionId")
