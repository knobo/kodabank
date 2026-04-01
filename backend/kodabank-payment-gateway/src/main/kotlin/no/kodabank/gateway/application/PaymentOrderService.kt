package no.kodabank.gateway.application

import no.kodabank.gateway.domain.*
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.TenantId
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class PaymentOrderService(
    private val store: TenantAwareClient
) {
    companion object {
        const val CATEGORY = "PaymentOrder"
    }

    fun createOrder(
        merchantId: String,
        tenantId: TenantId,
        amount: BigDecimal,
        currency: String,
        description: String,
        callbackUrl: String?,
        expiresInMinutes: Long = 30,
        items: List<PaymentOrderItem> = emptyList()
    ): PaymentOrderState {
        val orderId = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(expiresInMinutes, ChronoUnit.MINUTES)

        val event = PaymentOrderCreated(
            orderId = orderId,
            merchantId = merchantId,
            tenantId = tenantId.value,
            amount = amount,
            currency = currency,
            description = description,
            callbackUrl = callbackUrl,
            expiresAt = expiresAt.toString(),
            items = items
        )

        store.append(
            CATEGORY, tenantId, orderId, null,
            listOf(orderEventToRequest(event))
        )

        return loadOrder(tenantId, orderId)
    }

    fun authorize(
        tenantId: TenantId,
        orderId: String,
        userId: String,
        payerAccountId: String
    ): PaymentOrderState {
        val order = loadOrder(tenantId, orderId)
        require(order.status == PaymentOrderStatus.CREATED) {
            "Payment order must be in CREATED status to authorize, current: ${order.status}"
        }

        val event = PaymentOrderAuthorized(
            orderId = orderId,
            userId = userId,
            payerAccountId = payerAccountId,
            authorizedAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, orderId, order.version,
            listOf(orderEventToRequest(event))
        )

        return loadOrder(tenantId, orderId)
    }

    fun capture(tenantId: TenantId, orderId: String, amount: BigDecimal? = null): PaymentOrderState {
        val order = loadOrder(tenantId, orderId)
        require(order.status == PaymentOrderStatus.AUTHORIZED) {
            "Payment order must be in AUTHORIZED status to capture, current: ${order.status}"
        }

        val captureAmount = amount ?: order.amount

        val event = PaymentOrderCaptured(
            orderId = orderId,
            capturedAmount = captureAmount,
            capturedAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, orderId, order.version,
            listOf(orderEventToRequest(event))
        )

        return loadOrder(tenantId, orderId)
    }

    fun cancel(tenantId: TenantId, orderId: String, reason: String): PaymentOrderState {
        val order = loadOrder(tenantId, orderId)
        require(order.status in setOf(PaymentOrderStatus.CREATED, PaymentOrderStatus.AUTHORIZED)) {
            "Payment order must be in CREATED or AUTHORIZED status to cancel, current: ${order.status}"
        }

        val event = PaymentOrderCancelled(
            orderId = orderId,
            reason = reason,
            cancelledAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, orderId, order.version,
            listOf(orderEventToRequest(event))
        )

        return loadOrder(tenantId, orderId)
    }

    fun refund(
        tenantId: TenantId,
        orderId: String,
        amount: BigDecimal? = null,
        reason: String
    ): PaymentOrderState {
        val order = loadOrder(tenantId, orderId)
        require(order.status == PaymentOrderStatus.CAPTURED) {
            "Payment order must be in CAPTURED status to refund, current: ${order.status}"
        }

        val refundAmount = amount ?: order.capturedAmount ?: order.amount

        val event = PaymentOrderRefunded(
            orderId = orderId,
            refundedAmount = refundAmount,
            reason = reason,
            refundedAt = Instant.now().toString()
        )

        store.append(
            CATEGORY, tenantId, orderId, order.version,
            listOf(orderEventToRequest(event))
        )

        return loadOrder(tenantId, orderId)
    }

    fun getOrder(tenantId: TenantId, orderId: String): PaymentOrderState =
        loadOrder(tenantId, orderId)

    private fun loadOrder(tenantId: TenantId, orderId: String): PaymentOrderState {
        val stream = store.readStream(CATEGORY, tenantId, orderId)
        if (stream.events.isEmpty()) throw PaymentOrderNotFoundException(orderId)
        val parsed = stream.events.map { parseOrderEvent(it) }
        return PaymentOrderState.rebuild(parsed)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOrderEvent(recorded: RecordedEvent): Pair<PaymentOrderEvent, Int> {
        val p = recorded.payload
        val event: PaymentOrderEvent = when (recorded.eventType) {
            "PaymentOrderCreated" -> {
                val rawItems = p["items"] as? List<Map<String, Any?>> ?: emptyList()
                val items = rawItems.map { item ->
                    PaymentOrderItem(
                        name = item["name"] as String,
                        quantity = (item["quantity"] as Number).toInt(),
                        unitPrice = BigDecimal(item["unitPrice"].toString())
                    )
                }
                PaymentOrderCreated(
                    orderId = p["orderId"] as String,
                    merchantId = p["merchantId"] as String,
                    tenantId = p["tenantId"] as String,
                    amount = BigDecimal(p["amount"].toString()),
                    currency = p["currency"] as String,
                    description = p["description"] as String,
                    callbackUrl = p["callbackUrl"] as? String,
                    expiresAt = p["expiresAt"] as String,
                    items = items
                )
            }
            "PaymentOrderAuthorized" -> PaymentOrderAuthorized(
                orderId = p["orderId"] as String,
                userId = p["userId"] as String,
                payerAccountId = p["payerAccountId"] as String,
                authorizedAt = p["authorizedAt"] as String
            )
            "PaymentOrderCaptured" -> PaymentOrderCaptured(
                orderId = p["orderId"] as String,
                capturedAmount = BigDecimal(p["capturedAmount"].toString()),
                capturedAt = p["capturedAt"] as String
            )
            "PaymentOrderCancelled" -> PaymentOrderCancelled(
                orderId = p["orderId"] as String,
                reason = p["reason"] as String,
                cancelledAt = p["cancelledAt"] as String
            )
            "PaymentOrderRefunded" -> PaymentOrderRefunded(
                orderId = p["orderId"] as String,
                refundedAmount = BigDecimal(p["refundedAmount"].toString()),
                reason = p["reason"] as String,
                refundedAt = p["refundedAt"] as String
            )
            "PaymentOrderExpired" -> PaymentOrderExpired(
                orderId = p["orderId"] as String,
                expiredAt = p["expiredAt"] as String
            )
            else -> throw IllegalArgumentException("Unknown event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }

    private fun orderEventToRequest(event: PaymentOrderEvent): NewEventRequest {
        val metadata = mapOf<String, Any?>("sourceService" to "kodabank-payment-gateway")
        val (eventType, payload) = when (event) {
            is PaymentOrderCreated -> "PaymentOrderCreated" to mapOf<String, Any?>(
                "orderId" to event.orderId,
                "merchantId" to event.merchantId,
                "tenantId" to event.tenantId,
                "amount" to event.amount,
                "currency" to event.currency,
                "description" to event.description,
                "callbackUrl" to event.callbackUrl,
                "expiresAt" to event.expiresAt,
                "items" to event.items.map { item ->
                    mapOf("name" to item.name, "quantity" to item.quantity, "unitPrice" to item.unitPrice)
                }
            )
            is PaymentOrderAuthorized -> "PaymentOrderAuthorized" to mapOf<String, Any?>(
                "orderId" to event.orderId,
                "userId" to event.userId,
                "payerAccountId" to event.payerAccountId,
                "authorizedAt" to event.authorizedAt
            )
            is PaymentOrderCaptured -> "PaymentOrderCaptured" to mapOf<String, Any?>(
                "orderId" to event.orderId,
                "capturedAmount" to event.capturedAmount,
                "capturedAt" to event.capturedAt
            )
            is PaymentOrderCancelled -> "PaymentOrderCancelled" to mapOf<String, Any?>(
                "orderId" to event.orderId,
                "reason" to event.reason,
                "cancelledAt" to event.cancelledAt
            )
            is PaymentOrderRefunded -> "PaymentOrderRefunded" to mapOf<String, Any?>(
                "orderId" to event.orderId,
                "refundedAmount" to event.refundedAmount,
                "reason" to event.reason,
                "refundedAt" to event.refundedAt
            )
            is PaymentOrderExpired -> "PaymentOrderExpired" to mapOf<String, Any?>(
                "orderId" to event.orderId,
                "expiredAt" to event.expiredAt
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }
}

class PaymentOrderNotFoundException(orderId: String) :
    RuntimeException("Payment order not found: $orderId")
