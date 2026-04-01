package no.kodabank.gateway.web

import no.kodabank.gateway.application.MerchantService
import no.kodabank.gateway.application.PaymentOrderService
import no.kodabank.gateway.domain.PaymentOrderItem
import no.kodabank.gateway.domain.PaymentOrderState
import no.kodabank.gateway.domain.PaymentOrderStatus
import no.kodabank.shared.domain.TenantId
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

/**
 * Internal endpoints for payment order operations.
 * These are NOT protected by the API key filter (path starts with /api/internal/).
 * Intended for service-to-service calls from the BFF.
 */
@RestController
@RequestMapping("/api/internal/payments")
class InternalPaymentController(
    private val paymentOrderService: PaymentOrderService,
    private val merchantService: MerchantService
) {

    @GetMapping("/{orderId}")
    fun getPaymentOrder(
        @PathVariable orderId: String,
        @RequestParam tenantId: String
    ): CheckoutOrderResponse {
        val order = paymentOrderService.getOrder(TenantId(tenantId), orderId)
        val merchantName = try {
            merchantService.getMerchant(TenantId(tenantId), order.merchantId).merchantName
        } catch (_: Exception) {
            "Ukjent butikk"
        }
        return CheckoutOrderResponse.from(order, merchantName)
    }

    @PostMapping("/{orderId}/authorize")
    fun authorizePaymentOrder(
        @PathVariable orderId: String,
        @RequestBody body: AuthorizeRequest
    ): CheckoutOrderResponse {
        val order = paymentOrderService.authorize(
            tenantId = TenantId(body.tenantId),
            orderId = orderId,
            userId = body.userId,
            payerAccountId = body.payerAccountId
        )
        val merchantName = try {
            merchantService.getMerchant(TenantId(body.tenantId), order.merchantId).merchantName
        } catch (_: Exception) {
            "Ukjent butikk"
        }
        return CheckoutOrderResponse.from(order, merchantName)
    }
}

data class AuthorizeRequest(
    val tenantId: String,
    val userId: String,
    val payerAccountId: String
)

data class CheckoutOrderResponse(
    val orderId: String,
    val merchantId: String,
    val merchantName: String,
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
        fun from(order: PaymentOrderState, merchantName: String) = CheckoutOrderResponse(
            orderId = order.orderId,
            merchantId = order.merchantId,
            merchantName = merchantName,
            tenantId = order.tenantId,
            amount = order.amount,
            currency = order.currency,
            description = order.description,
            callbackUrl = order.callbackUrl,
            expiresAt = order.expiresAt,
            items = order.items,
            status = order.status,
            userId = order.userId,
            payerAccountId = order.payerAccountId,
            capturedAmount = order.capturedAmount,
            refundedAmount = order.refundedAmount,
            version = order.version
        )
    }
}
