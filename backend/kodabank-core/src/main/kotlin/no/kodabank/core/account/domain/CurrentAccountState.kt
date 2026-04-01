package no.kodabank.core.account.domain

import java.math.BigDecimal
import java.time.Instant

enum class AccountStatus { DRAFT, ACTIVE, FROZEN, CLOSED }

data class Reservation(
    val reservationId: String,
    val amount: BigDecimal,
    val expiresAt: Instant,
    val description: String
)

data class CurrentAccountState(
    val accountId: String,
    val tenantId: String,
    val partyId: String,
    val iban: String,
    val balance: BigDecimal,
    val currency: String,
    val productId: String,
    val accountName: String,
    val reservations: Map<String, Reservation>,
    val status: AccountStatus,
    val version: Int
) {
    val availableBalance: BigDecimal
        get() = balance - reservations.values.fold(BigDecimal.ZERO) { acc, r -> acc + r.amount }

    companion object {
        val EMPTY = CurrentAccountState(
            accountId = "",
            tenantId = "",
            partyId = "",
            iban = "",
            balance = BigDecimal.ZERO,
            currency = "NOK",
            productId = "",
            accountName = "",
            reservations = emptyMap(),
            status = AccountStatus.DRAFT,
            version = 0
        )

        fun evolve(state: CurrentAccountState, event: CurrentAccountEvent, version: Int): CurrentAccountState =
            when (event) {
                is CurrentAccountOpened -> state.copy(
                    accountId = event.accountId,
                    tenantId = event.tenantId,
                    partyId = event.partyId,
                    iban = event.iban,
                    currency = event.currency,
                    productId = event.productId,
                    accountName = event.accountName,
                    status = AccountStatus.ACTIVE,
                    version = version
                )
                is FundsDeposited -> state.copy(
                    balance = event.balanceAfter,
                    version = version
                )
                is FundsWithdrawn -> state.copy(
                    balance = event.balanceAfter,
                    version = version
                )
                is FundsReserved -> state.copy(
                    reservations = state.reservations + (event.reservationId to Reservation(
                        reservationId = event.reservationId,
                        amount = event.amount,
                        expiresAt = event.expiresAt,
                        description = event.description
                    )),
                    version = version
                )
                is ReservationReleased -> state.copy(
                    reservations = state.reservations - event.reservationId,
                    version = version
                )
                is ReservationCaptured -> state.copy(
                    balance = state.balance - event.capturedAmount,
                    reservations = state.reservations - event.reservationId,
                    version = version
                )
                is AccountFrozen -> state.copy(
                    status = AccountStatus.FROZEN,
                    version = version
                )
                is AccountClosed -> state.copy(
                    status = AccountStatus.CLOSED,
                    balance = event.finalBalance,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<CurrentAccountEvent, Int>>): CurrentAccountState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
