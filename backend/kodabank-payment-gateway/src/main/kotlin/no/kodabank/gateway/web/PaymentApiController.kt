package no.kodabank.gateway.web

import jakarta.servlet.http.HttpServletRequest
import no.kodabank.gateway.application.PaymentOrderService
import no.kodabank.gateway.application.SubscriptionService
import no.kodabank.gateway.config.authenticatedMerchant
import no.kodabank.gateway.domain.*
import no.kodabank.shared.domain.TenantId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1")
class PaymentApiController(
    private val paymentOrderService: PaymentOrderService,
    private val subscriptionService: SubscriptionService
) {

    // -- Payment Order endpoints --

    @PostMapping("/payments")
    fun createPayment(
        request: HttpServletRequest,
        @RequestBody body: CreatePaymentRequest
    ): ResponseEntity<PaymentOrderState> {
        val merchant = request.authenticatedMerchant()
        val order = paymentOrderService.createOrder(
            merchantId = merchant.merchantId,
            tenantId = TenantId(merchant.tenantId),
            amount = body.amount,
            currency = body.currency,
            description = body.description,
            callbackUrl = body.callbackUrl,
            expiresInMinutes = body.expiresInMinutes ?: 30,
            items = body.items?.map { PaymentOrderItem(it.name, it.quantity, it.unitPrice) } ?: emptyList()
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(order)
    }

    @GetMapping("/payments/{id}")
    fun getPayment(
        request: HttpServletRequest,
        @PathVariable id: String
    ): PaymentOrderState {
        val merchant = request.authenticatedMerchant()
        return paymentOrderService.getOrder(TenantId(merchant.tenantId), id)
    }

    @PostMapping("/payments/{id}/capture")
    fun capturePayment(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody(required = false) body: CaptureRequest?
    ): PaymentOrderState {
        val merchant = request.authenticatedMerchant()
        return paymentOrderService.capture(TenantId(merchant.tenantId), id, body?.amount)
    }

    @PostMapping("/payments/{id}/cancel")
    fun cancelPayment(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: CancelRequest
    ): PaymentOrderState {
        val merchant = request.authenticatedMerchant()
        return paymentOrderService.cancel(TenantId(merchant.tenantId), id, body.reason)
    }

    @PostMapping("/payments/{id}/refund")
    fun refundPayment(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: RefundRequest
    ): PaymentOrderState {
        val merchant = request.authenticatedMerchant()
        return paymentOrderService.refund(TenantId(merchant.tenantId), id, body.amount, body.reason)
    }

    // -- Subscription endpoints --

    @PostMapping("/subscriptions")
    fun createSubscription(
        request: HttpServletRequest,
        @RequestBody body: CreateSubscriptionRequest
    ): ResponseEntity<SubscriptionState> {
        val merchant = request.authenticatedMerchant()
        val subscription = subscriptionService.create(
            merchantId = merchant.merchantId,
            tenantId = TenantId(merchant.tenantId),
            userId = body.userId,
            payerAccountId = body.payerAccountId,
            amount = body.amount,
            currency = body.currency,
            interval = body.interval,
            description = body.description,
            startDate = body.startDate
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(subscription)
    }

    @GetMapping("/subscriptions/{id}")
    fun getSubscription(
        request: HttpServletRequest,
        @PathVariable id: String
    ): SubscriptionState {
        val merchant = request.authenticatedMerchant()
        return subscriptionService.getSubscription(TenantId(merchant.tenantId), id)
    }

    @PostMapping("/subscriptions/{id}/pause")
    fun pauseSubscription(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: PauseRequest
    ): SubscriptionState {
        val merchant = request.authenticatedMerchant()
        return subscriptionService.pause(TenantId(merchant.tenantId), id, body.reason)
    }

    @PostMapping("/subscriptions/{id}/resume")
    fun resumeSubscription(
        request: HttpServletRequest,
        @PathVariable id: String
    ): SubscriptionState {
        val merchant = request.authenticatedMerchant()
        return subscriptionService.resume(TenantId(merchant.tenantId), id)
    }

    @PostMapping("/subscriptions/{id}/cancel")
    fun cancelSubscription(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: CancelRequest
    ): SubscriptionState {
        val merchant = request.authenticatedMerchant()
        return subscriptionService.cancel(TenantId(merchant.tenantId), id, body.reason)
    }
}

// -- Request DTOs --

data class CreatePaymentRequest(
    val amount: BigDecimal,
    val currency: String = "NOK",
    val description: String,
    val callbackUrl: String? = null,
    val expiresInMinutes: Long? = 30,
    val items: List<ItemRequest>? = null
)

data class ItemRequest(
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

data class CaptureRequest(
    val amount: BigDecimal? = null
)

data class CancelRequest(
    val reason: String
)

data class RefundRequest(
    val amount: BigDecimal? = null,
    val reason: String
)

data class CreateSubscriptionRequest(
    val userId: String,
    val payerAccountId: String,
    val amount: BigDecimal,
    val currency: String = "NOK",
    val interval: SubscriptionInterval,
    val description: String,
    val startDate: String
)

data class PauseRequest(
    val reason: String
)
