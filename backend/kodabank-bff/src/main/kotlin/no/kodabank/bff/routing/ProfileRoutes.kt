package no.kodabank.bff.routing

import jakarta.servlet.http.HttpServletRequest
import no.kodabank.bff.auth.SessionData
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/{tenant}")
class ProfileRoutes(
    private val coreServiceClient: CoreServiceClient
) {

    private val log = LoggerFactory.getLogger(ProfileRoutes::class.java)

    @GetMapping("/profile")
    fun getProfile(
        @PathVariable tenant: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        return try {
            val partyData = coreServiceClient.getParty(session.tenantId, session.partyId)
            if (partyData == null) {
                // Fall back to session data if core service has no party record
                ResponseEntity.ok(
                    ProfileResponse(
                        partyId = session.partyId,
                        firstName = session.firstName,
                        lastName = session.lastName,
                        email = null,
                        phone = null
                    )
                )
            } else {
                ResponseEntity.ok(
                    ProfileResponse(
                        partyId = partyData.get("partyId")?.asText() ?: session.partyId,
                        firstName = partyData.get("firstName")?.asText() ?: session.firstName,
                        lastName = partyData.get("lastName")?.asText() ?: session.lastName,
                        email = partyData.get("email")?.asText(),
                        phone = partyData.get("phone")?.asText()
                    )
                )
            }
        } catch (e: Exception) {
            log.warn("Core party service unavailable for tenant={}, falling back to session data", tenant, e)
            ResponseEntity.ok(
                ProfileResponse(
                    partyId = session.partyId,
                    firstName = session.firstName,
                    lastName = session.lastName,
                    email = null,
                    phone = null
                )
            )
        }
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

data class ProfileResponse(
    val partyId: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?
)
