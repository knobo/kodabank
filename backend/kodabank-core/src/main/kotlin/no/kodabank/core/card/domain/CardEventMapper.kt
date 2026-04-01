package no.kodabank.core.card.domain

import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent

object CardEventMapper {

    fun toRequest(event: CardEvent, metadata: Map<String, Any?> = emptyMap()): NewEventRequest {
        val (eventType, payload) = when (event) {
            is CardIssued -> "CardIssued" to mapOf(
                "cardId" to event.cardId,
                "tenantId" to event.tenantId,
                "partyId" to event.partyId,
                "accountId" to event.accountId,
                "cardType" to event.cardType.name,
                "cardNumberMasked" to event.cardNumberMasked,
                "expiryDate" to event.expiryDate,
                "cardholderName" to event.cardholderName
            )
            is CardActivated -> "CardActivated" to mapOf(
                "cardId" to event.cardId
            )
            is CardBlocked -> "CardBlocked" to mapOf(
                "cardId" to event.cardId,
                "reason" to event.reason
            )
            is CardUnblocked -> "CardUnblocked" to mapOf(
                "cardId" to event.cardId
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    fun fromRecorded(recorded: RecordedEvent): Pair<CardEvent, Int> {
        val p = recorded.payload
        val event: CardEvent = when (recorded.eventType) {
            "CardIssued" -> CardIssued(
                cardId = p["cardId"] as String,
                tenantId = p["tenantId"] as String,
                partyId = p["partyId"] as String,
                accountId = p["accountId"] as String,
                cardType = CardType.valueOf(p["cardType"] as String),
                cardNumberMasked = p["cardNumberMasked"] as String,
                expiryDate = p["expiryDate"] as String,
                cardholderName = p["cardholderName"] as String
            )
            "CardActivated" -> CardActivated(
                cardId = p["cardId"] as String
            )
            "CardBlocked" -> CardBlocked(
                cardId = p["cardId"] as String,
                reason = p["reason"] as String
            )
            "CardUnblocked" -> CardUnblocked(
                cardId = p["cardId"] as String
            )
            else -> throw IllegalArgumentException("Unknown event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }
}
