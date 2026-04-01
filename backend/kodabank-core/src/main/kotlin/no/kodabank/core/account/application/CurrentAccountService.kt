package no.kodabank.core.account.application

import no.kodabank.core.account.domain.*
import no.kodabank.shared.client.ConcurrencyConflictException
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.Iban
import no.kodabank.shared.domain.TenantId
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class CurrentAccountService(
    private val store: TenantAwareClient
) {
    companion object {
        private const val CATEGORY = "CurrentAccount"
    }

    fun openAccount(
        tenantId: TenantId,
        partyId: String,
        iban: String,
        currency: String,
        productId: String,
        accountName: String,
        correlationId: String? = null
    ): CurrentAccountState {
        val accountId = UUID.randomUUID().toString()
        val event = CurrentAccountOpened(
            accountId = accountId,
            tenantId = tenantId.value,
            partyId = partyId,
            iban = iban,
            currency = currency,
            productId = productId,
            accountName = accountName
        )
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, accountId, null, listOf(CurrentAccountEventMapper.toRequest(event, metadata)))
        return loadAccount(tenantId, accountId)
    }

    fun deposit(
        tenantId: TenantId,
        accountId: String,
        amount: BigDecimal,
        reference: String,
        counterpartyName: String? = null,
        counterpartyIban: String? = null,
        remittanceInfo: String? = null,
        correlationId: String? = null
    ): CurrentAccountState {
        val current = loadAccount(tenantId, accountId)
        require(current.status == AccountStatus.ACTIVE) { "Account is not active: ${current.status}" }
        require(amount > BigDecimal.ZERO) { "Deposit amount must be positive" }

        val event = FundsDeposited(
            accountId = accountId,
            amount = amount,
            currency = current.currency,
            reference = reference,
            balanceAfter = current.balance + amount,
            counterpartyName = counterpartyName,
            counterpartyIban = counterpartyIban,
            remittanceInfo = remittanceInfo
        )
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, accountId, current.version, listOf(CurrentAccountEventMapper.toRequest(event, metadata)))
        return loadAccount(tenantId, accountId)
    }

    fun withdraw(
        tenantId: TenantId,
        accountId: String,
        amount: BigDecimal,
        reference: String,
        counterpartyName: String? = null,
        counterpartyIban: String? = null,
        remittanceInfo: String? = null,
        correlationId: String? = null
    ): CurrentAccountState {
        val current = loadAccount(tenantId, accountId)
        require(current.status == AccountStatus.ACTIVE) { "Account is not active: ${current.status}" }
        require(amount > BigDecimal.ZERO) { "Withdrawal amount must be positive" }
        require(current.availableBalance >= amount) { "Insufficient funds: available=${current.availableBalance}, requested=$amount" }

        val event = FundsWithdrawn(
            accountId = accountId,
            amount = amount,
            currency = current.currency,
            reference = reference,
            balanceAfter = current.balance - amount,
            counterpartyName = counterpartyName,
            counterpartyIban = counterpartyIban,
            remittanceInfo = remittanceInfo
        )
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, accountId, current.version, listOf(CurrentAccountEventMapper.toRequest(event, metadata)))
        return loadAccount(tenantId, accountId)
    }

    fun getAccount(tenantId: TenantId, accountId: String): CurrentAccountState {
        return loadAccount(tenantId, accountId)
    }

    private fun loadAccount(tenantId: TenantId, accountId: String): CurrentAccountState {
        val stream = store.readStream(CATEGORY, tenantId, accountId)
        if (stream.events.isEmpty()) {
            throw AccountNotFoundException(accountId)
        }
        val events = stream.events.map { CurrentAccountEventMapper.fromRecorded(it) }
        return CurrentAccountState.rebuild(events)
    }

    private fun buildMetadata(correlationId: String?, tenantId: TenantId): Map<String, Any?> =
        buildMap {
            put("tenantId", tenantId.value)
            put("sourceService", "kodabank-core")
            correlationId?.let { put("correlationId", it) }
        }
}

class AccountNotFoundException(accountId: String) :
    RuntimeException("Account not found: $accountId")
