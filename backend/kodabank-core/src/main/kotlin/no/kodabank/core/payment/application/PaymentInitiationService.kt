package no.kodabank.core.payment.application

import no.kodabank.core.payment.domain.*
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.TenantId
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class PaymentInitiationService(
    private val store: TenantAwareClient
) {
    companion object {
        private const val CATEGORY = "PaymentInitiation"
    }

    fun requestPayment(
        tenantId: TenantId,
        debtorAccountId: String,
        debtorIban: String,
        creditorIban: String,
        creditorName: String,
        amount: BigDecimal,
        currency: String = "NOK",
        reference: String? = null,
        remittanceInfo: String? = null,
        correlationId: String? = null
    ): PaymentInitiationState {
        require(amount > BigDecimal.ZERO) { "Payment amount must be positive" }

        val paymentId = UUID.randomUUID().toString()
        val event = PaymentRequested(
            paymentId = paymentId,
            tenantId = tenantId.value,
            debtorAccountId = debtorAccountId,
            debtorIban = debtorIban,
            creditorIban = creditorIban,
            creditorName = creditorName,
            amount = amount,
            currency = currency,
            reference = reference,
            remittanceInfo = remittanceInfo
        )
        val metadata = buildMetadata(correlationId ?: "pay-$paymentId", tenantId)
        store.append(CATEGORY, tenantId, paymentId, null, listOf(toRequest(event, metadata)))
        return loadPayment(tenantId, paymentId)
    }

    fun validatePayment(
        tenantId: TenantId,
        paymentId: String,
        debtorBankCode: String
    ): PaymentInitiationState {
        val current = loadPayment(tenantId, paymentId)
        require(current.status == PaymentStatus.REQUESTED) { "Payment must be in REQUESTED status" }

        val creditorBankCode = current.creditorIban.substring(4, 8) // NO{check}{bankCode:4}...
        val paymentType = if (creditorBankCode == debtorBankCode) PaymentType.INTERNAL else PaymentType.INTERBANK

        val event = PaymentValidated(paymentId = paymentId, paymentType = paymentType)
        val metadata = buildMetadata("pay-$paymentId", tenantId)
        store.append(CATEGORY, tenantId, paymentId, current.version, listOf(toRequest(event, metadata)))
        return loadPayment(tenantId, paymentId)
    }

    fun acceptPayment(
        tenantId: TenantId,
        paymentId: String,
        executionId: String
    ): PaymentInitiationState {
        val current = loadPayment(tenantId, paymentId)
        require(current.status == PaymentStatus.VALIDATED) { "Payment must be VALIDATED" }

        val event = PaymentAccepted(paymentId = paymentId, executionId = executionId)
        val metadata = buildMetadata("pay-$paymentId", tenantId)
        store.append(CATEGORY, tenantId, paymentId, current.version, listOf(toRequest(event, metadata)))
        return loadPayment(tenantId, paymentId)
    }

    fun rejectPayment(
        tenantId: TenantId,
        paymentId: String,
        reason: String
    ): PaymentInitiationState {
        val current = loadPayment(tenantId, paymentId)
        val event = PaymentRejected(paymentId = paymentId, reason = reason)
        val metadata = buildMetadata("pay-$paymentId", tenantId)
        store.append(CATEGORY, tenantId, paymentId, current.version, listOf(toRequest(event, metadata)))
        return loadPayment(tenantId, paymentId)
    }

    fun getPayment(tenantId: TenantId, paymentId: String): PaymentInitiationState {
        return loadPayment(tenantId, paymentId)
    }

    private fun loadPayment(tenantId: TenantId, paymentId: String): PaymentInitiationState {
        val stream = store.readStream(CATEGORY, tenantId, paymentId)
        if (stream.events.isEmpty()) throw PaymentNotFoundException(paymentId)
        val events = stream.events.map { fromRecorded(it) }
        return PaymentInitiationState.rebuild(events)
    }

    private fun toRequest(event: PaymentInitiationEvent, metadata: Map<String, Any?>): NewEventRequest {
        val (eventType, payload) = when (event) {
            is PaymentRequested -> "PaymentRequested" to mapOf<String, Any?>(
                "paymentId" to event.paymentId, "tenantId" to event.tenantId,
                "debtorAccountId" to event.debtorAccountId, "debtorIban" to event.debtorIban,
                "creditorIban" to event.creditorIban, "creditorName" to event.creditorName,
                "amount" to event.amount, "currency" to event.currency,
                "reference" to event.reference, "remittanceInfo" to event.remittanceInfo
            )
            is PaymentValidated -> "PaymentValidated" to mapOf<String, Any?>(
                "paymentId" to event.paymentId, "paymentType" to event.paymentType.name
            )
            is PaymentRejected -> "PaymentRejected" to mapOf<String, Any?>(
                "paymentId" to event.paymentId, "reason" to event.reason
            )
            is PaymentAccepted -> "PaymentAccepted" to mapOf<String, Any?>(
                "paymentId" to event.paymentId, "executionId" to event.executionId
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    private fun fromRecorded(recorded: RecordedEvent): Pair<PaymentInitiationEvent, Int> {
        val p = recorded.payload
        val event: PaymentInitiationEvent = when (recorded.eventType) {
            "PaymentRequested" -> PaymentRequested(
                paymentId = p["paymentId"] as String, tenantId = p["tenantId"] as String,
                debtorAccountId = p["debtorAccountId"] as String, debtorIban = p["debtorIban"] as String,
                creditorIban = p["creditorIban"] as String, creditorName = p["creditorName"] as String,
                amount = BigDecimal(p["amount"].toString()), currency = p["currency"] as String,
                reference = p["reference"] as? String, remittanceInfo = p["remittanceInfo"] as? String
            )
            "PaymentValidated" -> PaymentValidated(
                paymentId = p["paymentId"] as String,
                paymentType = PaymentType.valueOf(p["paymentType"] as String)
            )
            "PaymentRejected" -> PaymentRejected(
                paymentId = p["paymentId"] as String, reason = p["reason"] as String
            )
            "PaymentAccepted" -> PaymentAccepted(
                paymentId = p["paymentId"] as String, executionId = p["executionId"] as String
            )
            else -> throw IllegalArgumentException("Unknown event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }

    private fun buildMetadata(correlationId: String, tenantId: TenantId): Map<String, Any?> =
        mapOf("tenantId" to tenantId.value, "sourceService" to "kodabank-core", "correlationId" to correlationId)
}

class PaymentNotFoundException(paymentId: String) : RuntimeException("Payment not found: $paymentId")
