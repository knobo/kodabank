package no.kodabank.core.account.domain

import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import java.math.BigDecimal
import java.time.Instant

object CurrentAccountEventMapper {

    fun toRequest(event: CurrentAccountEvent, metadata: Map<String, Any?> = emptyMap()): NewEventRequest {
        val (eventType, payload) = when (event) {
            is CurrentAccountOpened -> "CurrentAccountOpened" to mapOf(
                "accountId" to event.accountId,
                "tenantId" to event.tenantId,
                "partyId" to event.partyId,
                "iban" to event.iban,
                "currency" to event.currency,
                "productId" to event.productId,
                "accountName" to event.accountName
            )
            is FundsDeposited -> "FundsDeposited" to mapOf(
                "accountId" to event.accountId,
                "amount" to event.amount,
                "currency" to event.currency,
                "reference" to event.reference,
                "balanceAfter" to event.balanceAfter,
                "counterpartyName" to event.counterpartyName,
                "counterpartyIban" to event.counterpartyIban,
                "remittanceInfo" to event.remittanceInfo
            )
            is FundsWithdrawn -> "FundsWithdrawn" to mapOf(
                "accountId" to event.accountId,
                "amount" to event.amount,
                "currency" to event.currency,
                "reference" to event.reference,
                "balanceAfter" to event.balanceAfter,
                "counterpartyName" to event.counterpartyName,
                "counterpartyIban" to event.counterpartyIban,
                "remittanceInfo" to event.remittanceInfo
            )
            is FundsReserved -> "FundsReserved" to mapOf(
                "accountId" to event.accountId,
                "reservationId" to event.reservationId,
                "amount" to event.amount,
                "expiresAt" to event.expiresAt.toString(),
                "description" to event.description
            )
            is ReservationReleased -> "ReservationReleased" to mapOf(
                "accountId" to event.accountId,
                "reservationId" to event.reservationId,
                "releasedAmount" to event.releasedAmount
            )
            is ReservationCaptured -> "ReservationCaptured" to mapOf(
                "accountId" to event.accountId,
                "reservationId" to event.reservationId,
                "capturedAmount" to event.capturedAmount,
                "paymentRef" to event.paymentRef
            )
            is AccountFrozen -> "AccountFrozen" to mapOf(
                "accountId" to event.accountId,
                "reason" to event.reason,
                "frozenAt" to event.frozenAt.toString()
            )
            is AccountClosed -> "AccountClosed" to mapOf(
                "accountId" to event.accountId,
                "closedAt" to event.closedAt.toString(),
                "finalBalance" to event.finalBalance
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    fun fromRecorded(recorded: RecordedEvent): Pair<CurrentAccountEvent, Int> {
        val p = recorded.payload
        val event: CurrentAccountEvent = when (recorded.eventType) {
            "CurrentAccountOpened" -> CurrentAccountOpened(
                accountId = p["accountId"] as String,
                tenantId = p["tenantId"] as String,
                partyId = p["partyId"] as String,
                iban = p["iban"] as String,
                currency = p["currency"] as String,
                productId = p["productId"] as String,
                accountName = p["accountName"] as String
            )
            "FundsDeposited" -> FundsDeposited(
                accountId = p["accountId"] as String,
                amount = toBigDecimal(p["amount"]),
                currency = p["currency"] as String,
                reference = p["reference"] as String,
                balanceAfter = toBigDecimal(p["balanceAfter"]),
                counterpartyName = p["counterpartyName"] as? String,
                counterpartyIban = p["counterpartyIban"] as? String,
                remittanceInfo = p["remittanceInfo"] as? String
            )
            "FundsWithdrawn" -> FundsWithdrawn(
                accountId = p["accountId"] as String,
                amount = toBigDecimal(p["amount"]),
                currency = p["currency"] as String,
                reference = p["reference"] as String,
                balanceAfter = toBigDecimal(p["balanceAfter"]),
                counterpartyName = p["counterpartyName"] as? String,
                counterpartyIban = p["counterpartyIban"] as? String,
                remittanceInfo = p["remittanceInfo"] as? String
            )
            "FundsReserved" -> FundsReserved(
                accountId = p["accountId"] as String,
                reservationId = p["reservationId"] as String,
                amount = toBigDecimal(p["amount"]),
                expiresAt = Instant.parse(p["expiresAt"] as String),
                description = p["description"] as String
            )
            "ReservationReleased" -> ReservationReleased(
                accountId = p["accountId"] as String,
                reservationId = p["reservationId"] as String,
                releasedAmount = toBigDecimal(p["releasedAmount"])
            )
            "ReservationCaptured" -> ReservationCaptured(
                accountId = p["accountId"] as String,
                reservationId = p["reservationId"] as String,
                capturedAmount = toBigDecimal(p["capturedAmount"]),
                paymentRef = p["paymentRef"] as String
            )
            "AccountFrozen" -> AccountFrozen(
                accountId = p["accountId"] as String,
                reason = p["reason"] as String,
                frozenAt = Instant.parse(p["frozenAt"] as String)
            )
            "AccountClosed" -> AccountClosed(
                accountId = p["accountId"] as String,
                closedAt = Instant.parse(p["closedAt"] as String),
                finalBalance = toBigDecimal(p["finalBalance"])
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
