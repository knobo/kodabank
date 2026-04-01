package no.kodabank.gateway.domain

import java.math.BigDecimal

enum class PaymentOrderStatus {
    CREATED, AUTHORIZED, CAPTURED, CANCELLED, REFUNDED, EXPIRED
}

data class PaymentOrderState(
    val orderId: String,
    val merchantId: String,
    val tenantId: String,
    val amount: BigDecimal,
    val currency: String,
    val description: String,
    val callbackUrl: String?,
    val expiresAt: String,
    val items: List<PaymentOrderItem>,
    val status: PaymentOrderStatus,
    val userId: String?,
    val payerAccountId: String?,
    val capturedAmount: BigDecimal?,
    val refundedAmount: BigDecimal?,
    val version: Int
) {
    companion object {
        val EMPTY = PaymentOrderState(
            "", "", "", BigDecimal.ZERO, "", "", null, "",
            emptyList(), PaymentOrderStatus.CREATED, null, null, null, null, 0
        )

        fun evolve(state: PaymentOrderState, event: PaymentOrderEvent, version: Int): PaymentOrderState =
            when (event) {
                is PaymentOrderCreated -> state.copy(
                    orderId = event.orderId,
                    merchantId = event.merchantId,
                    tenantId = event.tenantId,
                    amount = event.amount,
                    currency = event.currency,
                    description = event.description,
                    callbackUrl = event.callbackUrl,
                    expiresAt = event.expiresAt,
                    items = event.items,
                    status = PaymentOrderStatus.CREATED,
                    version = version
                )
                is PaymentOrderAuthorized -> state.copy(
                    status = PaymentOrderStatus.AUTHORIZED,
                    userId = event.userId,
                    payerAccountId = event.payerAccountId,
                    version = version
                )
                is PaymentOrderCaptured -> state.copy(
                    status = PaymentOrderStatus.CAPTURED,
                    capturedAmount = event.capturedAmount,
                    version = version
                )
                is PaymentOrderCancelled -> state.copy(
                    status = PaymentOrderStatus.CANCELLED,
                    version = version
                )
                is PaymentOrderRefunded -> state.copy(
                    status = PaymentOrderStatus.REFUNDED,
                    refundedAmount = event.refundedAmount,
                    version = version
                )
                is PaymentOrderExpired -> state.copy(
                    status = PaymentOrderStatus.EXPIRED,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<PaymentOrderEvent, Int>>): PaymentOrderState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
