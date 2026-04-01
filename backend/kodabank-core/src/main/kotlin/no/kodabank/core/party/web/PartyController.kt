package no.kodabank.core.party.web

import no.kodabank.core.party.application.PartyNotFoundException
import no.kodabank.core.party.application.PartyService
import no.kodabank.core.party.domain.PartyState
import no.kodabank.shared.domain.TenantId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/internal/parties")
class PartyController(
    private val partyService: PartyService
) {
    @PostMapping
    fun registerParty(@RequestBody req: RegisterPartyRequest): ResponseEntity<PartyState> {
        val state = partyService.registerParty(
            tenantId = TenantId(req.tenantId),
            nationalId = req.nationalId,
            firstName = req.firstName,
            lastName = req.lastName,
            email = req.email,
            phone = req.phone,
            address = req.address,
            correlationId = req.correlationId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    @PostMapping("/{partyId}/verify")
    fun verifyIdentity(
        @PathVariable partyId: String,
        @RequestParam tenantId: String
    ): PartyState {
        return partyService.verifyIdentity(TenantId(tenantId), partyId)
    }

    @GetMapping("/{partyId}")
    fun getParty(
        @PathVariable partyId: String,
        @RequestParam tenantId: String
    ): PartyState {
        return partyService.getParty(TenantId(tenantId), partyId)
    }

    @ExceptionHandler(PartyNotFoundException::class)
    fun handleNotFound(e: PartyNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("code" to "PARTY_NOT_FOUND", "message" to (e.message ?: "")))
}

data class RegisterPartyRequest(
    val tenantId: String,
    val nationalId: String,
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val correlationId: String? = null
)
