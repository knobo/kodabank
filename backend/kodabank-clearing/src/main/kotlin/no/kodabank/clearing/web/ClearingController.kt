package no.kodabank.clearing.web

import no.kodabank.clearing.application.BatchNotFoundException
import no.kodabank.clearing.application.ClearingService
import no.kodabank.clearing.application.TransferPolicyRejectedException
import no.kodabank.clearing.domain.ClearingBatchState
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/clearing")
class ClearingController(
    private val clearingService: ClearingService
) {
    @PostMapping("/items")
    fun addClearingItem(@RequestBody req: AddClearingItemRequest): ResponseEntity<ClearingBatchState> {
        val batch = clearingService.addClearingItem(
            debtorTenant = req.debtorTenant,
            debtorIban = req.debtorIban,
            creditorTenant = req.creditorTenant,
            creditorIban = req.creditorIban,
            creditorName = req.creditorName,
            amount = req.amount,
            currency = req.currency,
            reference = req.reference,
            paymentExecutionId = req.paymentExecutionId,
            correlationId = req.correlationId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(batch)
    }

    @PostMapping("/batches/{batchId}/process")
    fun processBatch(@PathVariable batchId: String): ClearingBatchState {
        return clearingService.processBatch(batchId)
    }

    @GetMapping("/batches/{batchId}")
    fun getBatch(@PathVariable batchId: String): ClearingBatchState {
        return clearingService.getBatch(batchId)
    }

    @ExceptionHandler(TransferPolicyRejectedException::class)
    fun handlePolicyRejected(e: TransferPolicyRejectedException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("code" to "TRANSFER_POLICY_REJECTED", "message" to (e.message ?: "")))

    @ExceptionHandler(BatchNotFoundException::class)
    fun handleNotFound(e: BatchNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("code" to "BATCH_NOT_FOUND", "message" to (e.message ?: "")))
}

data class AddClearingItemRequest(
    val debtorTenant: String,
    val debtorIban: String,
    val creditorTenant: String,
    val creditorIban: String,
    val creditorName: String,
    val amount: BigDecimal,
    val currency: String = "NOK",
    val reference: String? = null,
    val paymentExecutionId: String,
    val correlationId: String? = null
)
