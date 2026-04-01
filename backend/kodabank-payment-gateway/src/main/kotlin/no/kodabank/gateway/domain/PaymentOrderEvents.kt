package no.kodabank.gateway.domain

import java.math.BigDecimal

sealed interface PaymentOrderEvent {
    val orderId: String
}

data class PaymentOrderItem(
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

data class PaymentOrderCreated(
    override val orderId: String,
    val merchantId: String,
    val tenantId: String,
    val amount: BigDecimal,
    val currency: String,
    val description: String,
    val callbackUrl: String?,
    val expiresAt: String,
    val items: List<PaymentOrderItem>
) : PaymentOrderEvent

data class PaymentOrderAuthorized(
    override val orderId: String,
    val userId: String,
    val payerAccountId: String,
    val authorizedAt: String
) : PaymentOrderEvent

data class PaymentOrderCaptured(
    override val orderId: String,
    val capturedAmount: BigDecimal,
    val capturedAt: String
) : PaymentOrderEvent

data class PaymentOrderCancelled(
    override val orderId: String,
    val reason: String,
    val cancelledAt: String
) : PaymentOrderEvent

data class PaymentOrderRefunded(
    override val orderId: String,
    val refundedAmount: BigDecimal,
    val reason: String,
    val refundedAt: String
) : PaymentOrderEvent

data class PaymentOrderExpired(
    override val orderId: String,
    val expiredAt: String
) : PaymentOrderEvent
