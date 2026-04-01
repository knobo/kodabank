package no.kodabank.bff.routing

import jakarta.servlet.http.HttpServletRequest
import no.kodabank.bff.auth.SessionData
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/{tenant}")
class CardRoutes(
    private val readModelQueries: ReadModelQueries
) {

    @GetMapping("/cards")
    fun listCards(
        @PathVariable tenant: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        val cards = readModelQueries.getCardsForParty(session.tenantId, session.partyId)
        return ResponseEntity.ok(cards.map { it.toResponse() })
    }

    private fun validateSession(request: HttpServletRequest, tenant: String): SessionData? {
        val session = request.getAttribute("kodabank.session") as? SessionData ?: return null
        if (session.tenantId != tenant) return null
        return session
    }

    private fun unauthorizedResponse(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("error" to "unauthorized", "message" to "Valid session required"))
    }
}
