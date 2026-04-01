package no.kodabank.core.card.domain

enum class CardType { DEBIT, CREDIT }
enum class CardStatus { ISSUED, ACTIVE, BLOCKED }

data class CardState(
    val cardId: String,
    val tenantId: String,
    val partyId: String,
    val accountId: String,
    val cardType: CardType,
    val cardNumberMasked: String,
    val expiryDate: String,
    val cardholderName: String,
    val status: CardStatus,
    val version: Int
) {
    companion object {
        val EMPTY = CardState(
            cardId = "",
            tenantId = "",
            partyId = "",
            accountId = "",
            cardType = CardType.DEBIT,
            cardNumberMasked = "",
            expiryDate = "",
            cardholderName = "",
            status = CardStatus.ISSUED,
            version = 0
        )

        fun evolve(state: CardState, event: CardEvent, version: Int): CardState =
            when (event) {
                is CardIssued -> state.copy(
                    cardId = event.cardId,
                    tenantId = event.tenantId,
                    partyId = event.partyId,
                    accountId = event.accountId,
                    cardType = event.cardType,
                    cardNumberMasked = event.cardNumberMasked,
                    expiryDate = event.expiryDate,
                    cardholderName = event.cardholderName,
                    status = CardStatus.ISSUED,
                    version = version
                )
                is CardActivated -> state.copy(
                    status = CardStatus.ACTIVE,
                    version = version
                )
                is CardBlocked -> state.copy(
                    status = CardStatus.BLOCKED,
                    version = version
                )
                is CardUnblocked -> state.copy(
                    status = CardStatus.ACTIVE,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<CardEvent, Int>>): CardState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
