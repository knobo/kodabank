package no.kodabank.core.card.application

import no.kodabank.core.card.domain.*
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.TenantId
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CardService(
    private val store: TenantAwareClient
) {
    companion object {
        private const val CATEGORY = "Card"
    }

    fun issueCard(
        tenantId: TenantId,
        partyId: String,
        accountId: String,
        cardType: CardType,
        cardNumberMasked: String,
        expiryDate: String,
        cardholderName: String,
        correlationId: String? = null
    ): CardState {
        val cardId = UUID.randomUUID().toString()
        val event = CardIssued(
            cardId = cardId,
            tenantId = tenantId.value,
            partyId = partyId,
            accountId = accountId,
            cardType = cardType,
            cardNumberMasked = cardNumberMasked,
            expiryDate = expiryDate,
            cardholderName = cardholderName
        )
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, cardId, null, listOf(CardEventMapper.toRequest(event, metadata)))
        return loadCard(tenantId, cardId)
    }

    fun activateCard(
        tenantId: TenantId,
        cardId: String,
        correlationId: String? = null
    ): CardState {
        val current = loadCard(tenantId, cardId)
        require(current.status == CardStatus.ISSUED) { "Card must be in ISSUED status to activate, current: ${current.status}" }

        val event = CardActivated(cardId = cardId)
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, cardId, current.version, listOf(CardEventMapper.toRequest(event, metadata)))
        return loadCard(tenantId, cardId)
    }

    fun blockCard(
        tenantId: TenantId,
        cardId: String,
        reason: String,
        correlationId: String? = null
    ): CardState {
        val current = loadCard(tenantId, cardId)
        require(current.status == CardStatus.ACTIVE) { "Card must be in ACTIVE status to block, current: ${current.status}" }

        val event = CardBlocked(cardId = cardId, reason = reason)
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, cardId, current.version, listOf(CardEventMapper.toRequest(event, metadata)))
        return loadCard(tenantId, cardId)
    }

    fun unblockCard(
        tenantId: TenantId,
        cardId: String,
        correlationId: String? = null
    ): CardState {
        val current = loadCard(tenantId, cardId)
        require(current.status == CardStatus.BLOCKED) { "Card must be in BLOCKED status to unblock, current: ${current.status}" }

        val event = CardUnblocked(cardId = cardId)
        val metadata = buildMetadata(correlationId, tenantId)
        store.append(CATEGORY, tenantId, cardId, current.version, listOf(CardEventMapper.toRequest(event, metadata)))
        return loadCard(tenantId, cardId)
    }

    fun getCard(tenantId: TenantId, cardId: String): CardState {
        return loadCard(tenantId, cardId)
    }

    private fun loadCard(tenantId: TenantId, cardId: String): CardState {
        val stream = store.readStream(CATEGORY, tenantId, cardId)
        if (stream.events.isEmpty()) {
            throw CardNotFoundException(cardId)
        }
        val events = stream.events.map { CardEventMapper.fromRecorded(it) }
        return CardState.rebuild(events)
    }

    private fun buildMetadata(correlationId: String?, tenantId: TenantId): Map<String, Any?> =
        buildMap {
            put("tenantId", tenantId.value)
            put("sourceService", "kodabank-core")
            correlationId?.let { put("correlationId", it) }
        }
}

class CardNotFoundException(cardId: String) :
    RuntimeException("Card not found: $cardId")
