package no.kodabank.core.payment.domain

import java.math.BigDecimal

enum class PaymentType { INTERNAL, INTERBANK }
enum class PaymentStatus { REQUESTED, VALIDATED, REJECTED, ACCEPTED, EXECUTING, COMPLETED, FAILED }

// -- Payment Initiation Events --

sealed interface PaymentInitiationEvent {
    val paymentId: String
}

data class PaymentRequested(
    override val paymentId: String,
    val tenantId: String,
    val debtorAccountId: String,
    val debtorIban: String,
    val creditorIban: String,
    val creditorName: String,
    val amount: BigDecimal,
    val currency: String,
    val reference: String?,
    val remittanceInfo: String?
) : PaymentInitiationEvent

data class PaymentValidated(
    override val paymentId: String,
    val paymentType: PaymentType
) : PaymentInitiationEvent

data class PaymentRejected(
    override val paymentId: String,
    val reason: String
) : PaymentInitiationEvent

data class PaymentAccepted(
    override val paymentId: String,
    val executionId: String
) : PaymentInitiationEvent

// -- Payment Execution Events --

sealed interface PaymentExecutionEvent {
    val executionId: String
}

data class PaymentExecutionStarted(
    override val executionId: String,
    val paymentId: String,
    val tenantId: String,
    val paymentType: PaymentType
) : PaymentExecutionEvent

data class DebtorAccountDebited(
    override val executionId: String,
    val accountId: String,
    val amount: BigDecimal,
    val newBalance: BigDecimal
) : PaymentExecutionEvent

data class CreditorAccountCredited(
    override val executionId: String,
    val accountId: String,
    val amount: BigDecimal,
    val newBalance: BigDecimal
) : PaymentExecutionEvent

data class ClearingSubmitted(
    override val executionId: String,
    val clearingRef: String,
    val creditorBankTenant: String
) : PaymentExecutionEvent

data class ClearingConfirmed(
    override val executionId: String,
    val clearingRef: String
) : PaymentExecutionEvent

data class PaymentExecutionCompleted(
    override val executionId: String,
    val completedAt: String
) : PaymentExecutionEvent

data class PaymentExecutionFailed(
    override val executionId: String,
    val reason: String
) : PaymentExecutionEvent
