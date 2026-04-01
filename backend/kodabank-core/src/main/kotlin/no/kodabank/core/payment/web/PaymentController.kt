package no.kodabank.core.payment.web

import no.kodabank.core.payment.application.PaymentInitiationService
import no.kodabank.core.payment.application.PaymentNotFoundException
import no.kodabank.core.payment.domain.PaymentInitiationState
import no.kodabank.shared.domain.TenantId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/internal/payments")
class PaymentController(
    private val paymentService: PaymentInitiationService
) {
    @PostMapping("/initiate")
    fun initiatePayment(@RequestBody req: InitiatePaymentRequest): ResponseEntity<PaymentInitiationState> {
        val state = paymentService.requestPayment(
            tenantId = TenantId(req.tenantId),
            debtorAccountId = req.debtorAccountId,
            debtorIban = req.debtorIban,
            creditorIban = req.creditorIban,
            creditorName = req.creditorName,
            amount = req.amount,
            currency = req.currency,
            reference = req.reference,
            remittanceInfo = req.remittanceInfo,
            correlationId = req.correlationId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    @GetMapping("/{paymentId}")
    fun getPayment(
        @PathVariable paymentId: String,
        @RequestParam tenantId: String
    ): PaymentInitiationState {
        return paymentService.getPayment(TenantId(tenantId), paymentId)
    }

    @ExceptionHandler(PaymentNotFoundException::class)
    fun handleNotFound(e: PaymentNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("code" to "PAYMENT_NOT_FOUND", "message" to (e.message ?: "")))
}

data class InitiatePaymentRequest(
    val tenantId: String,
    val debtorAccountId: String,
    val debtorIban: String,
    val creditorIban: String,
    val creditorName: String,
    val amount: BigDecimal,
    val currency: String = "NOK",
    val reference: String? = null,
    val remittanceInfo: String? = null,
    val correlationId: String? = null
)
