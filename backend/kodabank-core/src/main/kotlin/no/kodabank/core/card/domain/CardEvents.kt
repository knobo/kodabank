package no.kodabank.core.card.domain

sealed interface CardEvent {
    val cardId: String
}

data class CardIssued(
    override val cardId: String,
    val tenantId: String,
    val partyId: String,
    val accountId: String,
    val cardType: CardType,
    val cardNumberMasked: String,
    val expiryDate: String,
    val cardholderName: String
) : CardEvent

data class CardActivated(
    override val cardId: String
) : CardEvent

data class CardBlocked(
    override val cardId: String,
    val reason: String
) : CardEvent

data class CardUnblocked(
    override val cardId: String
) : CardEvent
