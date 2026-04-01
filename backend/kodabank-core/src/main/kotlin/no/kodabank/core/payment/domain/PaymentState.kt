package no.kodabank.core.payment.domain

import java.math.BigDecimal

data class PaymentInitiationState(
    val paymentId: String,
    val tenantId: String,
    val debtorAccountId: String,
    val debtorIban: String,
    val creditorIban: String,
    val creditorName: String,
    val amount: BigDecimal,
    val currency: String,
    val reference: String?,
    val remittanceInfo: String?,
    val paymentType: PaymentType?,
    val executionId: String?,
    val status: PaymentStatus,
    val rejectionReason: String?,
    val version: Int
) {
    companion object {
        val EMPTY = PaymentInitiationState(
            "", "", "", "", "", "", BigDecimal.ZERO, "NOK",
            null, null, null, null, PaymentStatus.REQUESTED, null, 0
        )

        fun evolve(state: PaymentInitiationState, event: PaymentInitiationEvent, version: Int): PaymentInitiationState =
            when (event) {
                is PaymentRequested -> state.copy(
                    paymentId = event.paymentId,
                    tenantId = event.tenantId,
                    debtorAccountId = event.debtorAccountId,
                    debtorIban = event.debtorIban,
                    creditorIban = event.creditorIban,
                    creditorName = event.creditorName,
                    amount = event.amount,
                    currency = event.currency,
                    reference = event.reference,
                    remittanceInfo = event.remittanceInfo,
                    status = PaymentStatus.REQUESTED,
                    version = version
                )
                is PaymentValidated -> state.copy(
                    paymentType = event.paymentType,
                    status = PaymentStatus.VALIDATED,
                    version = version
                )
                is PaymentRejected -> state.copy(
                    status = PaymentStatus.REJECTED,
                    rejectionReason = event.reason,
                    version = version
                )
                is PaymentAccepted -> state.copy(
                    executionId = event.executionId,
                    status = PaymentStatus.ACCEPTED,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<PaymentInitiationEvent, Int>>): PaymentInitiationState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}

data class PaymentExecutionState(
    val executionId: String,
    val paymentId: String,
    val tenantId: String,
    val paymentType: PaymentType?,
    val debtorDebited: Boolean,
    val creditorCredited: Boolean,
    val clearingRef: String?,
    val clearingConfirmed: Boolean,
    val completed: Boolean,
    val failed: Boolean,
    val failReason: String?,
    val version: Int
) {
    companion object {
        val EMPTY = PaymentExecutionState(
            "", "", "", null, false, false, null, false, false, false, null, 0
        )

        fun evolve(state: PaymentExecutionState, event: PaymentExecutionEvent, version: Int): PaymentExecutionState =
            when (event) {
                is PaymentExecutionStarted -> state.copy(
                    executionId = event.executionId,
                    paymentId = event.paymentId,
                    tenantId = event.tenantId,
                    paymentType = event.paymentType,
                    version = version
                )
                is DebtorAccountDebited -> state.copy(
                    debtorDebited = true,
                    version = version
                )
                is CreditorAccountCredited -> state.copy(
                    creditorCredited = true,
                    version = version
                )
                is ClearingSubmitted -> state.copy(
                    clearingRef = event.clearingRef,
                    version = version
                )
                is ClearingConfirmed -> state.copy(
                    clearingConfirmed = true,
                    version = version
                )
                is PaymentExecutionCompleted -> state.copy(
                    completed = true,
                    version = version
                )
                is PaymentExecutionFailed -> state.copy(
                    failed = true,
                    failReason = event.reason,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<PaymentExecutionEvent, Int>>): PaymentExecutionState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
