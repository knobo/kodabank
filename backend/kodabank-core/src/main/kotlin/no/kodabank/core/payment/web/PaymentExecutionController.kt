package no.kodabank.core.payment.web

import no.kodabank.core.payment.application.PaymentExecutionNotFoundException
import no.kodabank.core.payment.application.PaymentExecutionService
import no.kodabank.core.payment.application.PaymentNotFoundException
import no.kodabank.core.payment.domain.PaymentExecutionState
import no.kodabank.shared.domain.TenantId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/internal/payments")
class PaymentExecutionController(
    private val executionService: PaymentExecutionService
) {

    @PostMapping("/execute")
    fun executePayment(@RequestBody req: ExecutePaymentRequest): ResponseEntity<PaymentExecutionState> {
        val state = executionService.executePayment(
            tenantId = TenantId(req.tenantId),
            paymentId = req.paymentId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    @GetMapping("/executions/{executionId}")
    fun getExecution(
        @PathVariable executionId: String,
        @RequestParam tenantId: String
    ): PaymentExecutionState {
        return executionService.getExecution(TenantId(tenantId), executionId)
    }

    @ExceptionHandler(PaymentExecutionNotFoundException::class)
    fun handleExecutionNotFound(e: PaymentExecutionNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("code" to "EXECUTION_NOT_FOUND", "message" to (e.message ?: "")))

    @ExceptionHandler(PaymentNotFoundException::class)
    fun handlePaymentNotFound(e: PaymentNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("code" to "PAYMENT_NOT_FOUND", "message" to (e.message ?: "")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("code" to "BAD_REQUEST", "message" to (e.message ?: "")))

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(e: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("code" to "EXECUTION_ERROR", "message" to (e.message ?: "")))
}

data class ExecutePaymentRequest(
    val tenantId: String,
    val paymentId: String
)
