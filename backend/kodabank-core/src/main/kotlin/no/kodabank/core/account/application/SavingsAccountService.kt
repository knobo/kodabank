package no.kodabank.core.account.application

import no.kodabank.core.account.domain.*
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.domain.TenantId
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class SavingsAccountService(
    private val store: TenantAwareClient
) {
    companion object {
        private const val CATEGORY = "SavingsAccount"
    }

    fun openAccount(
        tenantId: TenantId,
        partyId: String,
        iban: String,
        currency: String,
        productId: String,
        accountName: String,
        interestRate: BigDecimal,
        correlationId: String? = null
    ): SavingsAccountState {
        val accountId = UUID.randomUUID().toString()
        val event = SavingsAccountOpened(
            accountId = accountId,
            tenantId = tenantId.value,
            partyId = partyId,
            iban = iban,
            currency = currency,
            productId = productId,
            accountName = accountName,
            interestRate = interestRate
        )
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, accountId, null, listOf(toRequest(event, metadata)))
        return loadAccount(tenantId, accountId)
    }

    fun deposit(
        tenantId: TenantId,
        accountId: String,
        amount: BigDecimal,
        sourceAccountId: String,
        reference: String,
        correlationId: String? = null
    ): SavingsAccountState {
        val current = loadAccount(tenantId, accountId)
        require(current.status == AccountStatus.ACTIVE) { "Account is not active" }
        require(amount > BigDecimal.ZERO) { "Deposit amount must be positive" }

        val event = SavingsDeposited(
            accountId = accountId,
            amount = amount,
            sourceAccountId = sourceAccountId,
            reference = reference,
            balanceAfter = current.balance + amount
        )
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, accountId, current.version, listOf(toRequest(event, metadata)))
        return loadAccount(tenantId, accountId)
    }

    fun withdraw(
        tenantId: TenantId,
        accountId: String,
        amount: BigDecimal,
        targetAccountId: String,
        reference: String,
        correlationId: String? = null
    ): SavingsAccountState {
        val current = loadAccount(tenantId, accountId)
        require(current.status == AccountStatus.ACTIVE) { "Account is not active" }
        require(amount > BigDecimal.ZERO) { "Withdrawal amount must be positive" }
        require(current.balance >= amount) { "Insufficient funds" }

        val event = SavingsWithdrawn(
            accountId = accountId,
            amount = amount,
            targetAccountId = targetAccountId,
            reference = reference,
            balanceAfter = current.balance - amount
        )
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, accountId, current.version, listOf(toRequest(event, metadata)))
        return loadAccount(tenantId, accountId)
    }

    fun getAccount(tenantId: TenantId, accountId: String): SavingsAccountState {
        return loadAccount(tenantId, accountId)
    }

    private fun loadAccount(tenantId: TenantId, accountId: String): SavingsAccountState {
        val stream = store.readStream(CATEGORY, tenantId, accountId)
        if (stream.events.isEmpty()) {
            throw AccountNotFoundException(accountId)
        }
        val events = stream.events.map { SavingsAccountEventMapper.fromRecorded(it) }
        return SavingsAccountState.rebuild(events)
    }

    private fun toRequest(event: SavingsAccountEvent, metadata: Map<String, Any?>): NewEventRequest {
        val (eventType, payload) = when (event) {
            is SavingsAccountOpened -> "SavingsAccountOpened" to mapOf<String, Any?>(
                "accountId" to event.accountId, "tenantId" to event.tenantId,
                "partyId" to event.partyId, "iban" to event.iban,
                "currency" to event.currency, "productId" to event.productId,
                "accountName" to event.accountName, "interestRate" to event.interestRate
            )
            is SavingsDeposited -> "SavingsDeposited" to mapOf<String, Any?>(
                "accountId" to event.accountId, "amount" to event.amount,
                "sourceAccountId" to event.sourceAccountId, "reference" to event.reference,
                "balanceAfter" to event.balanceAfter
            )
            is SavingsWithdrawn -> "SavingsWithdrawn" to mapOf<String, Any?>(
                "accountId" to event.accountId, "amount" to event.amount,
                "targetAccountId" to event.targetAccountId, "reference" to event.reference,
                "balanceAfter" to event.balanceAfter
            )
            is InterestRateChanged -> "InterestRateChanged" to mapOf<String, Any?>(
                "accountId" to event.accountId, "oldRate" to event.oldRate,
                "newRate" to event.newRate, "effectiveDate" to event.effectiveDate
            )
            is InterestCredited -> "InterestCredited" to mapOf<String, Any?>(
                "accountId" to event.accountId, "period" to event.period,
                "amount" to event.amount, "balanceAfter" to event.balanceAfter
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    private fun buildMetadata(correlationId: String?, tenantId: TenantId): Map<String, Any?> =
        buildMap {
            put("tenantId", tenantId.value)
            put("sourceService", "kodabank-core")
            correlationId?.let { put("correlationId", it) }
        }
}

object SavingsAccountEventMapper {
    fun fromRecorded(recorded: no.kodabank.shared.client.RecordedEvent): Pair<SavingsAccountEvent, Int> {
        val p = recorded.payload
        val event: SavingsAccountEvent = when (recorded.eventType) {
            "SavingsAccountOpened" -> SavingsAccountOpened(
                accountId = p["accountId"] as String,
                tenantId = p["tenantId"] as String,
                partyId = p["partyId"] as String,
                iban = p["iban"] as String,
                currency = p["currency"] as String,
                productId = p["productId"] as String,
                accountName = p["accountName"] as String,
                interestRate = toBigDecimal(p["interestRate"])
            )
            "SavingsDeposited" -> SavingsDeposited(
                accountId = p["accountId"] as String,
                amount = toBigDecimal(p["amount"]),
                sourceAccountId = p["sourceAccountId"] as String,
                reference = p["reference"] as String,
                balanceAfter = toBigDecimal(p["balanceAfter"])
            )
            "SavingsWithdrawn" -> SavingsWithdrawn(
                accountId = p["accountId"] as String,
                amount = toBigDecimal(p["amount"]),
                targetAccountId = p["targetAccountId"] as String,
                reference = p["reference"] as String,
                balanceAfter = toBigDecimal(p["balanceAfter"])
            )
            "InterestRateChanged" -> InterestRateChanged(
                accountId = p["accountId"] as String,
                oldRate = toBigDecimal(p["oldRate"]),
                newRate = toBigDecimal(p["newRate"]),
                effectiveDate = p["effectiveDate"] as String
            )
            "InterestCredited" -> InterestCredited(
                accountId = p["accountId"] as String,
                period = p["period"] as String,
                amount = toBigDecimal(p["amount"]),
                balanceAfter = toBigDecimal(p["balanceAfter"])
            )
            else -> throw IllegalArgumentException("Unknown event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }

    private fun toBigDecimal(value: Any?): BigDecimal = when (value) {
        is BigDecimal -> value
        is Number -> BigDecimal(value.toString())
        is String -> BigDecimal(value)
        else -> throw IllegalArgumentException("Cannot convert $value to BigDecimal")
    }
}
