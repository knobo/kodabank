package no.kodabank.bff.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestTemplate
import java.util.Base64

/**
 * Simulates BankID login for the demo environment.
 *
 * Accepts a national ID and tenant, looks up the matching Keycloak user
 * via the direct access grant (resource owner password credentials flow),
 * and returns session info including a BFF-managed session cookie.
 */
@RestController
@RequestMapping("/api/v1/{tenant}/auth")
class BankIdSimulator(
    private val sessionManager: SessionManager,
    private val jdbc: JdbcTemplate,
    @Value("\${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private val keycloakIssuerUri: String
) {

    private val log = LoggerFactory.getLogger(BankIdSimulator::class.java)
    private val restTemplate = RestTemplate()

    companion object {
        private const val CLIENT_ID = "kodabank-bff"
        private const val CLIENT_SECRET = "bff-secret"
        private const val DEMO_PASSWORD = "demo"
    }

    /**
     * Maps national IDs to Keycloak usernames for the demo environment.
     */
    private val nationalIdToUsername = mapOf(
        "01019012345" to "ola@fjordbank",
        "15038945678" to "kari@fjordbank",
        "12068712345" to "per@trollbank",
        "22049156789" to "ingrid@trollbank",
        "05079834567" to "lars@trollbank",
        "30119067890" to "astrid@nordlys",
        "18057523456" to "erik@nordlys",
        "09028498765" to "solveig@nordlys"
    )

    data class BankIdRequest(
        @JsonProperty("nationalId") val nationalId: String
    )

    data class BankIdResponse(
        val sessionId: String,
        val partyId: String,
        val firstName: String,
        val lastName: String
    )

    data class ErrorResponse(
        val error: String,
        val message: String
    )

    @PostMapping("/bankid")
    fun simulateBankIdLogin(
        @PathVariable tenant: String,
        @RequestBody request: BankIdRequest,
        response: HttpServletResponse
    ): ResponseEntity<*> {
        log.info("BankID login attempt for national_id={} tenant={}", request.nationalId, tenant)

        val username = nationalIdToUsername[request.nationalId]
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse("unknown_user", "No user found for the given national ID"))

        // Verify the user belongs to the requested tenant
        if (!username.endsWith("@$tenant")) {
            log.warn("User {} does not belong to tenant {}", username, tenant)
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse("tenant_mismatch", "User does not belong to tenant $tenant"))
        }

        return try {
            val tokenResponse = requestTokenFromKeycloak(username, tenant)
            val accessToken = tokenResponse.get("access_token").asText()
            val refreshToken = tokenResponse.get("refresh_token")?.asText()

            // Decode JWT claims to get user info
            val claims = decodeJwtPayload(accessToken)
            val firstName = claims.get("given_name")?.asText() ?: ""
            val lastName = claims.get("family_name")?.asText() ?: ""

            // Look up partyId from readmodel by matching name and tenant
            val partyId = lookupPartyId(tenant, firstName, lastName)

            val session = sessionManager.createSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                partyId = partyId,
                tenantId = tenant,
                username = username,
                firstName = firstName,
                lastName = lastName,
                response = response
            )

            log.info("BankID login successful for user={} tenant={} sessionId={}", username, tenant, session.sessionId)

            ResponseEntity.ok(
                BankIdResponse(
                    sessionId = session.sessionId,
                    partyId = partyId,
                    firstName = firstName,
                    lastName = lastName
                )
            )
        } catch (e: Exception) {
            log.error("BankID login failed for user={} tenant={}", username, tenant, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse("auth_failed", "Authentication failed: ${e.message}"))
        }
    }

    @PostMapping("/logout")
    fun logout(
        @PathVariable tenant: String,
        request: jakarta.servlet.http.HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        sessionManager.destroySession(request, response)
        log.info("Logout for tenant={}", tenant)
        return ResponseEntity.ok(mapOf("status" to "logged_out"))
    }

    @PostMapping("/session")
    fun getSessionInfo(
        @PathVariable tenant: String,
        request: jakarta.servlet.http.HttpServletRequest
    ): ResponseEntity<*> {
        val session = sessionManager.getSession(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse("no_session", "No active session"))

        if (session.tenantId != tenant) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse("tenant_mismatch", "Session belongs to a different tenant"))
        }

        return ResponseEntity.ok(
            BankIdResponse(
                sessionId = session.sessionId,
                partyId = session.partyId,
                firstName = session.firstName,
                lastName = session.lastName
            )
        )
    }

    private fun lookupPartyId(tenantId: String, firstName: String, lastName: String): String {
        val results = jdbc.queryForList(
            "SELECT party_id FROM rm_customers WHERE tenant_id = ? AND first_name = ? AND last_name = ? LIMIT 1",
            String::class.java,
            tenantId, firstName, lastName
        )
        return results.firstOrNull() ?: ""
    }

    private fun requestTokenFromKeycloak(username: String, tenant: String): JsonNode {
        val tokenUrl = "$keycloakIssuerUri/protocol/openid-connect/token"

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "password")
        body.add("client_id", CLIENT_ID)
        body.add("client_secret", CLIENT_SECRET)
        body.add("username", username)
        body.add("password", DEMO_PASSWORD)
        body.add("scope", "openid profile email tenant:$tenant")

        val entity = HttpEntity(body, headers)

        val response = restTemplate.postForEntity(tokenUrl, entity, JsonNode::class.java)

        return response.body
            ?: throw IllegalStateException("Empty response from Keycloak token endpoint")
    }

    private fun decodeJwtPayload(jwt: String): JsonNode {
        val parts = jwt.split(".")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid JWT format")
        }
        val payload = Base64.getUrlDecoder().decode(parts[1])
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        return mapper.readTree(payload)
    }
}
