package no.kodabank.core.card.web

import no.kodabank.core.card.application.CardNotFoundException
import no.kodabank.core.card.application.CardService
import no.kodabank.core.card.domain.CardState
import no.kodabank.core.card.domain.CardType
import no.kodabank.shared.domain.TenantId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/internal/cards")
class CardController(
    private val cardService: CardService
) {

    @PostMapping
    fun issueCard(@RequestBody req: IssueCardRequest): ResponseEntity<CardState> {
        val state = cardService.issueCard(
            tenantId = TenantId(req.tenantId),
            partyId = req.partyId,
            accountId = req.accountId,
            cardType = req.cardType,
            cardNumberMasked = req.cardNumberMasked,
            expiryDate = req.expiryDate,
            cardholderName = req.cardholderName,
            correlationId = req.correlationId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    @PostMapping("/{cardId}/activate")
    fun activateCard(
        @PathVariable cardId: String,
        @RequestBody req: CardActionRequest
    ): CardState {
        return cardService.activateCard(
            tenantId = TenantId(req.tenantId),
            cardId = cardId,
            correlationId = req.correlationId
        )
    }

    @PostMapping("/{cardId}/block")
    fun blockCard(
        @PathVariable cardId: String,
        @RequestBody req: BlockCardRequest
    ): CardState {
        return cardService.blockCard(
            tenantId = TenantId(req.tenantId),
            cardId = cardId,
            reason = req.reason,
            correlationId = req.correlationId
        )
    }

    @PostMapping("/{cardId}/unblock")
    fun unblockCard(
        @PathVariable cardId: String,
        @RequestBody req: CardActionRequest
    ): CardState {
        return cardService.unblockCard(
            tenantId = TenantId(req.tenantId),
            cardId = cardId,
            correlationId = req.correlationId
        )
    }

    @GetMapping("/{cardId}")
    fun getCard(
        @PathVariable cardId: String,
        @RequestParam tenantId: String
    ): CardState {
        return cardService.getCard(TenantId(tenantId), cardId)
    }

    // -- Error Handling --

    @ExceptionHandler(CardNotFoundException::class)
    fun handleNotFound(e: CardNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("code" to "CARD_NOT_FOUND", "message" to (e.message ?: "")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("code" to "BAD_REQUEST", "message" to (e.message ?: "")))
}

// -- Request DTOs --

data class IssueCardRequest(
    val tenantId: String,
    val partyId: String,
    val accountId: String,
    val cardType: CardType,
    val cardNumberMasked: String,
    val expiryDate: String,
    val cardholderName: String,
    val correlationId: String? = null
)

data class CardActionRequest(
    val tenantId: String,
    val correlationId: String? = null
)

data class BlockCardRequest(
    val tenantId: String,
    val reason: String,
    val correlationId: String? = null
)
