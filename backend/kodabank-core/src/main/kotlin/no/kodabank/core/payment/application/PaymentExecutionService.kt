package no.kodabank.core.payment.application

import com.fasterxml.jackson.databind.JsonNode
import no.kodabank.core.account.application.CurrentAccountService
import no.kodabank.core.payment.domain.*
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.TenantId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class PaymentExecutionService(
    private val store: TenantAwareClient,
    private val paymentInitiationService: PaymentInitiationService,
    private val currentAccountService: CurrentAccountService,
    @Value("\${kodabank.clearing-url:http://localhost:8083}") private val clearingUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val clearingClient: RestClient = RestClient.builder()
        .baseUrl(clearingUrl)
        .build()

    companion object {
        private const val CATEGORY = "PaymentExecution"
    }

    /**
     * Start execution of an accepted payment.
     * Orchestrates: debit debtor -> credit creditor (internal) or submit to clearing (interbank) -> complete.
     */
    fun executePayment(tenantId: TenantId, paymentId: String): PaymentExecutionState {
        val payment = paymentInitiationService.getPayment(tenantId, paymentId)
        require(payment.status == PaymentStatus.ACCEPTED) { "Payment must be ACCEPTED to execute, current: ${payment.status}" }
        requireNotNull(payment.paymentType) { "Payment type must be determined before execution" }
        requireNotNull(payment.executionId) { "Execution ID must be set on accepted payment" }

        val executionId = payment.executionId
        val correlationId = "exec-$executionId"

        // 1. Record execution started
        val startedEvent = PaymentExecutionStarted(
            executionId = executionId,
            paymentId = paymentId,
            tenantId = tenantId.value,
            paymentType = payment.paymentType
        )
        appendEvent(tenantId, executionId, null, startedEvent, correlationId)
        log.info("Payment execution started: executionId={}, paymentId={}, type={}", executionId, paymentId, payment.paymentType)

        try {
            // 2. Debit debtor account
            val debitResult = currentAccountService.withdraw(
                tenantId = tenantId,
                accountId = payment.debtorAccountId,
                amount = payment.amount,
                reference = "Payment $paymentId",
                counterpartyName = payment.creditorName,
                counterpartyIban = payment.creditorIban,
                remittanceInfo = payment.remittanceInfo,
                correlationId = correlationId
            )

            val current = loadExecution(tenantId, executionId)
            val debitedEvent = DebtorAccountDebited(
                executionId = executionId,
                accountId = payment.debtorAccountId,
                amount = payment.amount,
                newBalance = debitResult.balance
            )
            appendEvent(tenantId, executionId, current.version, debitedEvent, correlationId)
            log.info("Debtor account debited: accountId={}, amount={}", payment.debtorAccountId, payment.amount)

            // 3. Route based on payment type
            when (payment.paymentType) {
                PaymentType.INTERNAL -> executeInternalCredit(tenantId, executionId, payment, correlationId)
                PaymentType.INTERBANK -> executeInterbankClearing(tenantId, executionId, payment, correlationId)
            }

            // 4. Mark execution completed
            val completionState = loadExecution(tenantId, executionId)
            val completedEvent = PaymentExecutionCompleted(
                executionId = executionId,
                completedAt = Instant.now().toString()
            )
            appendEvent(tenantId, executionId, completionState.version, completedEvent, correlationId)
            log.info("Payment execution completed: executionId={}", executionId)
        } catch (e: Exception) {
            log.error("Payment execution failed: executionId={}, reason={}", executionId, e.message, e)
            val failState = loadExecution(tenantId, executionId)
            val failedEvent = PaymentExecutionFailed(
                executionId = executionId,
                reason = e.message ?: "Unknown error"
            )
            appendEvent(tenantId, executionId, failState.version, failedEvent, correlationId)
        }

        return loadExecution(tenantId, executionId)
    }

    fun getExecution(tenantId: TenantId, executionId: String): PaymentExecutionState {
        return loadExecution(tenantId, executionId)
    }

    private fun executeInternalCredit(
        tenantId: TenantId,
        executionId: String,
        payment: PaymentInitiationState,
        correlationId: String
    ) {
        // For internal payments, the creditor is in the same tenant.
        // Look up creditor account by IBAN from the readmodel.
        val creditorAccountId = lookupAccountIdByIban(tenantId, payment.creditorIban)

        val creditResult = currentAccountService.deposit(
            tenantId = tenantId,
            accountId = creditorAccountId,
            amount = payment.amount,
            reference = "Payment from ${payment.debtorIban}",
            counterpartyName = null,
            counterpartyIban = payment.debtorIban,
            remittanceInfo = payment.remittanceInfo,
            correlationId = correlationId
        )

        val current = loadExecution(tenantId, executionId)
        val creditedEvent = CreditorAccountCredited(
            executionId = executionId,
            accountId = creditorAccountId,
            amount = payment.amount,
            newBalance = creditResult.balance
        )
        appendEvent(tenantId, executionId, current.version, creditedEvent, correlationId)
        log.info("Creditor account credited (internal): accountId={}, amount={}", creditorAccountId, payment.amount)
    }

    private fun executeInterbankClearing(
        tenantId: TenantId,
        executionId: String,
        payment: PaymentInitiationState,
        correlationId: String
    ) {
        // Determine creditor bank tenant from IBAN bank code
        val creditorBankCode = payment.creditorIban.substring(4, 8)
        val creditorTenant = resolveCreditorTenant(creditorBankCode)

        val body = mapOf(
            "debtorTenant" to tenantId.value,
            "debtorIban" to payment.debtorIban,
            "creditorTenant" to creditorTenant,
            "creditorIban" to payment.creditorIban,
            "creditorName" to payment.creditorName,
            "amount" to payment.amount,
            "currency" to payment.currency,
            "reference" to payment.reference,
            "paymentExecutionId" to executionId,
            "correlationId" to correlationId
        )

        val response = clearingClient.post()
            .uri("/api/clearing/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)

        val clearingRef = response?.get("batchId")?.asText() ?: "clearing-${UUID.randomUUID()}"

        val current = loadExecution(tenantId, executionId)
        val submittedEvent = ClearingSubmitted(
            executionId = executionId,
            clearingRef = clearingRef,
            creditorBankTenant = creditorTenant
        )
        appendEvent(tenantId, executionId, current.version, submittedEvent, correlationId)
        log.info("Clearing submitted: executionId={}, clearingRef={}, creditorTenant={}", executionId, clearingRef, creditorTenant)
    }

    /**
     * Resolve the creditor tenant ID from the bank code in the IBAN.
     * Uses the readmodel rm_tenants table projected by TenantProjection.
     */
    private fun resolveCreditorTenant(bankCode: String): String {
        // For the demo, tenant IDs follow a convention where the bank code maps to a tenant.
        // In production this would query rm_tenants by bank_code.
        // We use the store to scan tenants and match by bank code embedded in the IBAN prefix.
        val tenants = store.readCategory("BankTenant")
        for (event in tenants) {
            if (event.eventType == "BankTenantCreated") {
                val eventBankCode = event.payload["bankCode"] as? String
                if (eventBankCode == bankCode) {
                    return event.payload["tenantId"] as String
                }
            }
        }
        throw IllegalStateException("No tenant found for bank code: $bankCode")
    }

    /**
     * Look up an account ID by IBAN within the same tenant.
     * Scans the CurrentAccount event category for the matching IBAN.
     */
    private fun lookupAccountIdByIban(tenantId: TenantId, iban: String): String {
        val events = store.readCategoryForTenant("CurrentAccount", tenantId)
        for (event in events) {
            if (event.eventType == "CurrentAccountOpened") {
                val eventIban = event.payload["iban"] as? String
                if (eventIban == iban) {
                    return event.payload["accountId"] as String
                }
            }
        }
        throw IllegalStateException("No account found for IBAN $iban in tenant ${tenantId.value}")
    }

    private fun loadExecution(tenantId: TenantId, executionId: String): PaymentExecutionState {
        val stream = store.readStream(CATEGORY, tenantId, executionId)
        if (stream.events.isEmpty()) throw PaymentExecutionNotFoundException(executionId)
        val events = stream.events.map { fromRecorded(it) }
        return PaymentExecutionState.rebuild(events)
    }

    private fun appendEvent(
        tenantId: TenantId,
        executionId: String,
        expectedVersion: Int?,
        event: PaymentExecutionEvent,
        correlationId: String
    ) {
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, executionId, expectedVersion, listOf(toRequest(event, metadata)))
    }

    private fun toRequest(event: PaymentExecutionEvent, metadata: Map<String, Any?>): NewEventRequest {
        val (eventType, payload) = when (event) {
            is PaymentExecutionStarted -> "PaymentExecutionStarted" to mapOf<String, Any?>(
                "executionId" to event.executionId, "paymentId" to event.paymentId,
                "tenantId" to event.tenantId, "paymentType" to event.paymentType.name
            )
            is DebtorAccountDebited -> "DebtorAccountDebited" to mapOf<String, Any?>(
                "executionId" to event.executionId, "accountId" to event.accountId,
                "amount" to event.amount, "newBalance" to event.newBalance
            )
            is CreditorAccountCredited -> "CreditorAccountCredited" to mapOf<String, Any?>(
                "executionId" to event.executionId, "accountId" to event.accountId,
                "amount" to event.amount, "newBalance" to event.newBalance
            )
            is ClearingSubmitted -> "ClearingSubmitted" to mapOf<String, Any?>(
                "executionId" to event.executionId, "clearingRef" to event.clearingRef,
                "creditorBankTenant" to event.creditorBankTenant
            )
            is ClearingConfirmed -> "ClearingConfirmed" to mapOf<String, Any?>(
                "executionId" to event.executionId, "clearingRef" to event.clearingRef
            )
            is PaymentExecutionCompleted -> "PaymentExecutionCompleted" to mapOf<String, Any?>(
                "executionId" to event.executionId, "completedAt" to event.completedAt
            )
            is PaymentExecutionFailed -> "PaymentExecutionFailed" to mapOf<String, Any?>(
                "executionId" to event.executionId, "reason" to event.reason
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    private fun fromRecorded(recorded: RecordedEvent): Pair<PaymentExecutionEvent, Int> {
        val p = recorded.payload
        val event: PaymentExecutionEvent = when (recorded.eventType) {
            "PaymentExecutionStarted" -> PaymentExecutionStarted(
                executionId = p["executionId"] as String, paymentId = p["paymentId"] as String,
                tenantId = p["tenantId"] as String, paymentType = PaymentType.valueOf(p["paymentType"] as String)
            )
            "DebtorAccountDebited" -> DebtorAccountDebited(
                executionId = p["executionId"] as String, accountId = p["accountId"] as String,
                amount = BigDecimal(p["amount"].toString()), newBalance = BigDecimal(p["newBalance"].toString())
            )
            "CreditorAccountCredited" -> CreditorAccountCredited(
                executionId = p["executionId"] as String, accountId = p["accountId"] as String,
                amount = BigDecimal(p["amount"].toString()), newBalance = BigDecimal(p["newBalance"].toString())
            )
            "ClearingSubmitted" -> ClearingSubmitted(
                executionId = p["executionId"] as String, clearingRef = p["clearingRef"] as String,
                creditorBankTenant = p["creditorBankTenant"] as String
            )
            "ClearingConfirmed" -> ClearingConfirmed(
                executionId = p["executionId"] as String, clearingRef = p["clearingRef"] as String
            )
            "PaymentExecutionCompleted" -> PaymentExecutionCompleted(
                executionId = p["executionId"] as String, completedAt = p["completedAt"] as String
            )
            "PaymentExecutionFailed" -> PaymentExecutionFailed(
                executionId = p["executionId"] as String, reason = p["reason"] as String
            )
            else -> throw IllegalArgumentException("Unknown execution event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }

    private fun buildMetadata(correlationId: String, tenantId: TenantId): Map<String, Any?> =
        mapOf("tenantId" to tenantId.value, "sourceService" to "kodabank-core", "correlationId" to correlationId)
}

class PaymentExecutionNotFoundException(executionId: String) :
    RuntimeException("Payment execution not found: $executionId")
