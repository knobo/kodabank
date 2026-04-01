package no.kodabank.core.account.domain

import java.math.BigDecimal
import java.time.Instant

sealed interface CurrentAccountEvent {
    val accountId: String
}

data class CurrentAccountOpened(
    override val accountId: String,
    val tenantId: String,
    val partyId: String,
    val iban: String,
    val currency: String,
    val productId: String,
    val accountName: String
) : CurrentAccountEvent

data class FundsDeposited(
    override val accountId: String,
    val amount: BigDecimal,
    val currency: String,
    val reference: String,
    val balanceAfter: BigDecimal,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
    val remittanceInfo: String? = null
) : CurrentAccountEvent

data class FundsWithdrawn(
    override val accountId: String,
    val amount: BigDecimal,
    val currency: String,
    val reference: String,
    val balanceAfter: BigDecimal,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
    val remittanceInfo: String? = null
) : CurrentAccountEvent

data class FundsReserved(
    override val accountId: String,
    val reservationId: String,
    val amount: BigDecimal,
    val expiresAt: Instant,
    val description: String
) : CurrentAccountEvent

data class ReservationReleased(
    override val accountId: String,
    val reservationId: String,
    val releasedAmount: BigDecimal
) : CurrentAccountEvent

data class ReservationCaptured(
    override val accountId: String,
    val reservationId: String,
    val capturedAmount: BigDecimal,
    val paymentRef: String
) : CurrentAccountEvent

data class AccountFrozen(
    override val accountId: String,
    val reason: String,
    val frozenAt: Instant
) : CurrentAccountEvent

data class AccountClosed(
    override val accountId: String,
    val closedAt: Instant,
    val finalBalance: BigDecimal
) : CurrentAccountEvent

// -- Savings Account Events --

sealed interface SavingsAccountEvent {
    val accountId: String
}

data class SavingsAccountOpened(
    override val accountId: String,
    val tenantId: String,
    val partyId: String,
    val iban: String,
    val currency: String,
    val productId: String,
    val accountName: String,
    val interestRate: BigDecimal
) : SavingsAccountEvent

data class SavingsDeposited(
    override val accountId: String,
    val amount: BigDecimal,
    val sourceAccountId: String,
    val reference: String,
    val balanceAfter: BigDecimal
) : SavingsAccountEvent

data class SavingsWithdrawn(
    override val accountId: String,
    val amount: BigDecimal,
    val targetAccountId: String,
    val reference: String,
    val balanceAfter: BigDecimal
) : SavingsAccountEvent

data class InterestRateChanged(
    override val accountId: String,
    val oldRate: BigDecimal,
    val newRate: BigDecimal,
    val effectiveDate: String
) : SavingsAccountEvent

data class InterestCredited(
    override val accountId: String,
    val period: String,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal
) : SavingsAccountEvent
