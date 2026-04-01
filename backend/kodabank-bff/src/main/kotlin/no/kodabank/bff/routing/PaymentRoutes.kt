package no.kodabank.bff.routing

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import no.kodabank.bff.auth.SessionData
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant

@RestController
@RequestMapping("/api/v1/{tenant}")
class PaymentRoutes(
    private val readModelQueries: ReadModelQueries,
    private val coreServiceClient: CoreServiceClient
) {

    private val log = LoggerFactory.getLogger(PaymentRoutes::class.java)

    data class InitiatePaymentRequest(
        @JsonProperty("debtorAccountId") val debtorAccountId: String,
        @JsonProperty("debtorIban") val debtorIban: String,
        @JsonProperty("creditorIban") val creditorIban: String,
        @JsonProperty("creditorName") val creditorName: String,
        @JsonProperty("amount") val amount: BigDecimal,
        @JsonProperty("currency") val currency: String = "NOK",
        @JsonProperty("reference") val reference: String? = null,
        @JsonProperty("remittanceInfo") val remittanceInfo: String? = null
    )

    data class InternalTransferRequest(
        @JsonProperty("fromAccountId") val fromAccountId: String,
        @JsonProperty("toAccountId") val toAccountId: String,
        @JsonProperty("amount") val amount: BigDecimal,
        @JsonProperty("reference") val reference: String? = null
    )

    @PostMapping("/payments")
    fun initiatePayment(
        @PathVariable tenant: String,
        @RequestBody body: InitiatePaymentRequest,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        // Verify the debtor account belongs to this party
        val accounts = readModelQueries.getAccountsForParty(session.tenantId, session.partyId)
        if (accounts.none { it.accountId == body.debtorAccountId }) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "forbidden", "message" to "Account does not belong to this user"))
        }

        return try {
            val result = coreServiceClient.initiatePayment(
                tenantId = session.tenantId,
                debtorAccountId = body.debtorAccountId,
                debtorIban = body.debtorIban,
                creditorIban = body.creditorIban,
                creditorName = body.creditorName,
                amount = body.amount,
                currency = body.currency,
                reference = body.reference,
                remittanceInfo = body.remittanceInfo
            )
            ResponseEntity.status(HttpStatus.CREATED).body(result)
        } catch (e: Exception) {
            log.error("Failed to initiate payment for tenant={} party={}", tenant, session.partyId, e)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(mapOf("error" to "service_error", "message" to "Failed to initiate payment"))
        }
    }

    @GetMapping("/payments")
    fun listPayments(
        @PathVariable tenant: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        val payments = readModelQueries.getPaymentsForParty(session.tenantId, session.partyId)
        return ResponseEntity.ok(payments.map { it.toResponse() })
    }

    @GetMapping("/payments/{id}")
    fun getPayment(
        @PathVariable tenant: String,
        @PathVariable id: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        return try {
            val result = coreServiceClient.getPayment(session.tenantId, id)
            if (result == null) {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "not_found", "message" to "Payment not found"))
            } else {
                ResponseEntity.ok(result)
            }
        } catch (e: Exception) {
            log.error("Failed to get payment {} for tenant={}", id, tenant, e)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(mapOf("error" to "service_error", "message" to "Failed to retrieve payment status"))
        }
    }

    @PostMapping("/transfers/internal")
    fun internalTransfer(
        @PathVariable tenant: String,
        @RequestBody body: InternalTransferRequest,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        // Verify both accounts belong to this party
        val accounts = readModelQueries.getAccountsForParty(session.tenantId, session.partyId)
        val fromAccount = accounts.find { it.accountId == body.fromAccountId }
        val toAccount = accounts.find { it.accountId == body.toAccountId }

        if (fromAccount == null || toAccount == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "forbidden", "message" to "Both accounts must belong to this user"))
        }

        return try {
            val result = coreServiceClient.initiatePayment(
                tenantId = session.tenantId,
                debtorAccountId = body.fromAccountId,
                debtorIban = fromAccount.iban,
                creditorIban = toAccount.iban,
                creditorName = "${session.firstName} ${session.lastName}",
                amount = body.amount,
                currency = fromAccount.currency,
                reference = body.reference ?: "Internal transfer"
            )
            ResponseEntity.status(HttpStatus.CREATED).body(result)
        } catch (e: Exception) {
            log.error("Failed to execute internal transfer for tenant={} party={}", tenant, session.partyId, e)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(mapOf("error" to "service_error", "message" to "Failed to execute transfer"))
        }
    }

    private fun validateSession(request: HttpServletRequest, tenant: String): SessionData? {
        val session = request.getAttribute("kodabank.session") as? SessionData ?: return null
        if (session.tenantId != tenant) return null
        return session
    }

    private fun unauthorizedResponse(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("error" to "unauthorized", "message" to "Valid session required"))
    }
}

data class PaymentResponse(
    val paymentId: String,
    val debtorAccountId: String,
    val debtorIban: String,
    val creditorIban: String,
    val creditorName: String?,
    val amount: BigDecimal,
    val currency: String,
    val paymentType: String,
    val status: String,
    val reference: String?,
    val createdAt: Instant,
    val completedAt: Instant?
)

internal fun PaymentView.toResponse() = PaymentResponse(
    paymentId = paymentId,
    debtorAccountId = debtorAccountId,
    debtorIban = debtorIban,
    creditorIban = creditorIban,
    creditorName = creditorName,
    amount = amount,
    currency = currency,
    paymentType = paymentType,
    status = status,
    reference = reference,
    createdAt = createdAt,
    completedAt = completedAt
)
