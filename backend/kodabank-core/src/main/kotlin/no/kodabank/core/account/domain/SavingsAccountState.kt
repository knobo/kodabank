package no.kodabank.core.account.domain

import java.math.BigDecimal

data class SavingsAccountState(
    val accountId: String,
    val tenantId: String,
    val partyId: String,
    val iban: String,
    val balance: BigDecimal,
    val currency: String,
    val productId: String,
    val accountName: String,
    val interestRate: BigDecimal,
    val status: AccountStatus,
    val version: Int
) {
    companion object {
        val EMPTY = SavingsAccountState(
            accountId = "",
            tenantId = "",
            partyId = "",
            iban = "",
            balance = BigDecimal.ZERO,
            currency = "NOK",
            productId = "",
            accountName = "",
            interestRate = BigDecimal.ZERO,
            status = AccountStatus.DRAFT,
            version = 0
        )

        fun evolve(state: SavingsAccountState, event: SavingsAccountEvent, version: Int): SavingsAccountState =
            when (event) {
                is SavingsAccountOpened -> state.copy(
                    accountId = event.accountId,
                    tenantId = event.tenantId,
                    partyId = event.partyId,
                    iban = event.iban,
                    currency = event.currency,
                    productId = event.productId,
                    accountName = event.accountName,
                    interestRate = event.interestRate,
                    status = AccountStatus.ACTIVE,
                    version = version
                )
                is SavingsDeposited -> state.copy(
                    balance = event.balanceAfter,
                    version = version
                )
                is SavingsWithdrawn -> state.copy(
                    balance = event.balanceAfter,
                    version = version
                )
                is InterestRateChanged -> state.copy(
                    interestRate = event.newRate,
                    version = version
                )
                is InterestCredited -> state.copy(
                    balance = event.balanceAfter,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<SavingsAccountEvent, Int>>): SavingsAccountState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
