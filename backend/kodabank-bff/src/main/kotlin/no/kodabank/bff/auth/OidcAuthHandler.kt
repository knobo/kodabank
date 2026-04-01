package no.kodabank.bff.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles standard OIDC Authorization Code + PKCE flow.
 *
 * Login initiation redirects the user to Keycloak's authorize endpoint.
 * The callback exchanges the authorization code for tokens and creates
 * a BFF session, then redirects to the frontend dashboard.
 */
@RestController
class OidcAuthHandler(
    private val sessionManager: SessionManager,
    private val jdbc: JdbcTemplate,
    @Value("\${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private val keycloakIssuerUri: String,
    @Value("\${kodabank.frontend-url}")
    private val frontendUrl: String,
    @Value("\${kodabank.bff-base-url}")
    private val bffBaseUrl: String,
    @Value("\${kodabank.core-url}")
    private val coreUrl: String
) {

    private val log = LoggerFactory.getLogger(OidcAuthHandler::class.java)
    private val restTemplate = RestTemplate()
    private val objectMapper = ObjectMapper()

    companion object {
        private const val CLIENT_ID = "kodabank-bff"
        private const val CLIENT_SECRET = "bff-secret"
        private const val CODE_VERIFIER_LENGTH = 64
    }

    /**
     * Stores pending authorization requests keyed by state parameter.
     * Each entry holds the PKCE code_verifier and tenant for the flow.
     */
    private data class PendingAuth(
        val codeVerifier: String,
        val tenant: String,
        val returnTo: String = "",
        val createdAt: Long = System.currentTimeMillis()
    )

    private val pendingAuths = ConcurrentHashMap<String, PendingAuth>()

    /**
     * Initiates the OIDC authorization code flow with PKCE for a specific tenant.
     */
    @GetMapping("/api/v1/{tenant}/auth/login")
    fun login(
        @PathVariable tenant: String,
        response: HttpServletResponse
    ) {
        initiateOidcLogin(tenant, response)
    }

    /**
     * Tenant-independent OIDC login. Used for platform-level actions
     * like bank creation where the user doesn't belong to a tenant yet.
     * After login, redirects back to the frontend root.
     */
    @GetMapping("/api/v1/auth/login")
    fun platformLogin(
        @RequestParam(required = false, defaultValue = "") returnTo: String,
        response: HttpServletResponse
    ) {
        initiateOidcLogin("_platform", response, returnTo)
    }

    @GetMapping("/api/v1/auth/my-banks")
    fun myBanks(request: HttpServletRequest): ResponseEntity<*> {
        val session = sessionManager.getSession(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "no_session"))
        if (session.sub.isBlank()) {
            return ResponseEntity.ok(emptyList<Any>())
        }
        val banks = jdbc.queryForList(
            "SELECT tenant_id, bank_name, primary_color FROM rm_tenants WHERE owner_user_id = ? ORDER BY created_at DESC",
            session.sub
        )
        return ResponseEntity.ok(banks.map { row ->
            mapOf(
                "id" to (row["tenant_id"] as? String ?: ""),
                "name" to (row["bank_name"] as? String ?: ""),
                "primaryColor" to (row["primary_color"] as? String ?: "#6366f1")
            )
        })
    }

    @GetMapping("/api/v1/auth/me")
    fun me(request: HttpServletRequest): ResponseEntity<*> {
        val session = sessionManager.getSession(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "no_session", "authenticated" to false))
        return ResponseEntity.ok(mapOf(
            "authenticated" to true,
            "username" to session.username,
            "firstName" to session.firstName,
            "lastName" to session.lastName,
            "tenantId" to session.tenantId
        ))
    }

    private fun initiateOidcLogin(tenant: String, response: HttpServletResponse, returnTo: String = "") {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = generateState()

        pendingAuths[state] = PendingAuth(
            codeVerifier = codeVerifier,
            tenant = tenant,
            returnTo = returnTo
        )

        // Clean up old pending auth entries (older than 10 minutes)
        val cutoff = System.currentTimeMillis() - 600_000
        pendingAuths.entries.removeIf { it.value.createdAt < cutoff }

        val scope = if (tenant == "_platform") "openid profile email" else "openid profile email tenant:$tenant"

        val authorizeUrl = buildString {
            append(keycloakIssuerUri)
            append("/protocol/openid-connect/auth")
            append("?response_type=code")
            append("&client_id=$CLIENT_ID")
            append("&redirect_uri=")
            append(java.net.URLEncoder.encode("$bffBaseUrl/api/v1/auth/callback", "UTF-8"))
            append("&scope=")
            append(java.net.URLEncoder.encode(scope, "UTF-8"))
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=S256")
        }

        log.info("OIDC login initiated for tenant={}, redirecting to Keycloak", tenant)
        response.sendRedirect(authorizeUrl)
    }

    /**
     * Handles the OIDC callback after Keycloak authentication.
     * Exchanges the authorization code for tokens, creates a session,
     * and redirects to the frontend dashboard.
     */
    @GetMapping("/api/v1/auth/callback")
    fun callback(
        @RequestParam code: String,
        @RequestParam state: String,
        @RequestParam(required = false) error: String?,
        @RequestParam(name = "error_description", required = false) errorDescription: String?,
        response: HttpServletResponse
    ) {
        if (error != null) {
            log.error("OIDC callback error: {} - {}", error, errorDescription)
            response.sendRedirect("$frontendUrl/login?error=$error")
            return
        }

        val pendingAuth = pendingAuths.remove(state)
        if (pendingAuth == null) {
            log.warn("OIDC callback with unknown or expired state parameter")
            response.sendRedirect("$frontendUrl/login?error=invalid_state")
            return
        }

        val tenant = pendingAuth.tenant
        val codeVerifier = pendingAuth.codeVerifier

        try {
            val tokenResponse = exchangeCodeForTokens(code, codeVerifier)
            val accessToken = tokenResponse.get("access_token").asText()
            val refreshToken = tokenResponse.get("refresh_token")?.asText()

            // Decode JWT claims to get user info
            val claims = decodeJwtPayload(accessToken)
            val sub = claims.get("sub")?.asText() ?: ""
            val firstName = claims.get("given_name")?.asText() ?: ""
            val lastName = claims.get("family_name")?.asText() ?: ""
            val username = claims.get("preferred_username")?.asText() ?: sub

            // Extract tenant from the token if available, otherwise use the one from state
            val tokenTenant = claims.get("tenant_id")?.asText() ?: tenant

            // Look up partyId from readmodel, auto-provision if eligible
            val partyId = lookupPartyId(tokenTenant, sub, firstName, lastName)

            // If no partyId and not _platform, user is not a member → redirect to join page
            if (partyId.isEmpty() && tokenTenant != "_platform") {
                log.warn("User={} has no approved membership for tenant={}, redirecting to join", sub, tokenTenant)
                response.sendRedirect("$frontendUrl/$tokenTenant/join?error=not_a_member")
                return
            }

            val session = sessionManager.createSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                partyId = partyId,
                tenantId = tokenTenant,
                username = username,
                firstName = firstName,
                lastName = lastName,
                sub = sub,
                response = response
            )

            log.info(
                "OIDC login successful for user={} tenant={} sessionId={}",
                username, tokenTenant, session.sessionId
            )

            val redirectUrl = if (pendingAuth.returnTo.isNotBlank()) {
                "$frontendUrl${pendingAuth.returnTo}"
            } else if (tokenTenant == "_platform") {
                frontendUrl
            } else {
                "$frontendUrl/$tokenTenant/dashboard"
            }
            response.sendRedirect(redirectUrl)
        } catch (e: Exception) {
            log.error("OIDC token exchange failed for tenant={}", tenant, e)
            response.sendRedirect("$frontendUrl/$tenant/login?error=auth_failed")
        }
    }

    private fun exchangeCodeForTokens(code: String, codeVerifier: String): JsonNode {
        val tokenUrl = "$keycloakIssuerUri/protocol/openid-connect/token"

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "authorization_code")
        body.add("code", code)
        body.add("redirect_uri", "$bffBaseUrl/api/v1/auth/callback")
        body.add("client_id", CLIENT_ID)
        body.add("client_secret", CLIENT_SECRET)
        body.add("code_verifier", codeVerifier)

        val entity = HttpEntity(body, headers)
        val response = restTemplate.postForEntity(tokenUrl, entity, JsonNode::class.java)

        return response.body
            ?: throw IllegalStateException("Empty response from Keycloak token endpoint")
    }

    /**
     * Resolves partyId for the logged-in user, with the following priority:
     * 1. Approved membership (user_id = Keycloak sub) — most reliable
     * 2. Customer record by national_id or name match
     * 3. Auto-provision (only for AUTO_APPROVE banks or if user is the bank owner)
     */
    private fun lookupPartyId(tenantId: String, sub: String, firstName: String, lastName: String): String {
        if (tenantId == "_platform") return ""

        // 1. Check approved membership → join to rm_customers via email to get the real party_id
        //    (rm_memberships.party_id is a phantom UUID from the event and doesn't match the actual party)
        val byMembership = jdbc.queryForList(
            """SELECT c.party_id FROM rm_memberships m
               JOIN rm_customers c ON c.email = m.email AND c.tenant_id = m.tenant_id
               WHERE m.tenant_id = ? AND m.user_id = ? AND m.status = 'APPROVED' AND m.email IS NOT NULL
               LIMIT 1""",
            String::class.java, tenantId, sub
        )
        if (byMembership.isNotEmpty()) return byMembership.first()

        // 2. Check customer record by name
        val byCustomer = jdbc.queryForList(
            "SELECT party_id FROM rm_customers WHERE tenant_id = ? AND first_name = ? AND last_name = ? LIMIT 1",
            String::class.java, tenantId, firstName, lastName
        )
        if (byCustomer.isNotEmpty()) return byCustomer.first()

        // 3. Check if user is the bank owner or the bank has AUTO_APPROVE
        val tenantInfo = jdbc.queryForList(
            "SELECT owner_user_id, access_policy_type FROM rm_tenants WHERE tenant_id = ? LIMIT 1",
            tenantId
        )
        val isOwner = tenantInfo.firstOrNull()?.get("owner_user_id") == sub
        val isAutoApprove = tenantInfo.firstOrNull()?.get("access_policy_type") == "AUTO_APPROVE"

        if (!isOwner && !isAutoApprove) {
            log.warn("User={} attempted login to tenant={} without approved membership", sub, tenantId)
            return ""
        }

        // Auto-provision: create party and default account
        return try {
            provisionNewCustomer(tenantId, sub, firstName, lastName)
        } catch (e: Exception) {
            log.warn("Auto-provisioning failed for user={} tenant={}: {}", sub, tenantId, e.message)
            ""
        }
    }

    private fun provisionNewCustomer(tenantId: String, sub: String, firstName: String, lastName: String): String {
        // Create party in core service
        val partyBody = mapOf(
            "tenantId" to tenantId,
            "nationalId" to sub,
            "firstName" to firstName,
            "lastName" to lastName
        )
        val partyResponse = restTemplate.postForObject(
            "$coreUrl/api/internal/parties",
            partyBody,
            Map::class.java
        ) ?: throw IllegalStateException("No response from core party creation")

        val partyId = partyResponse["partyId"] as? String
            ?: throw IllegalStateException("No partyId in response: $partyResponse")

        // Get bank's IBAN prefix and next account sequence
        val bankInfo = jdbc.queryForMap(
            "SELECT iban_prefix, bank_code FROM rm_tenants WHERE tenant_id = ? LIMIT 1", tenantId
        )
        val bankCode = bankInfo["bank_code"] as String
        val accountCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM rm_accounts WHERE tenant_id = ?", Long::class.java, tenantId
        ) ?: 0L
        val accountSeq = (accountCount + 1).toInt()

        // Generate IBAN: NO + bankCode + zero-padded account number + check digit placeholder
        val accountNumber = accountSeq.toString().padStart(10, '0')
        val ibanRaw = "NO00$bankCode$accountNumber"
        val iban = computeNorwegianIban(bankCode, accountSeq)

        // Create default current account
        val accountBody = mapOf(
            "tenantId" to tenantId,
            "partyId" to partyId,
            "iban" to iban,
            "currency" to "NOK",
            "accountName" to "Brukskonto",
            "productId" to "STANDARD"
        )
        restTemplate.postForObject("$coreUrl/api/internal/accounts/current", accountBody, Map::class.java)

        // Activate the bank if still PENDING
        jdbc.update(
            "UPDATE rm_tenants SET status = 'ACTIVE' WHERE tenant_id = ? AND status = 'PENDING'", tenantId
        )

        log.info("Auto-provisioned customer partyId={} for tenant={}", partyId, tenantId)
        return partyId
    }

    private fun computeNorwegianIban(bankCode: String, seq: Int): String {
        val accountNumber = seq.toString().padStart(10, '0')
        // Norwegian BBAN = 11 digits: 4 bank digits + 6 account + 1 check (simplified: use 0 as check)
        val bban = (bankCode + accountNumber).padStart(11, '0').take(11)
        // MOD-97 check digits
        val rearranged = bban + "2328" + "00" // NO = 23 28 in number form
        val mod97 = rearranged.toBigInteger().mod(97.toBigInteger()).toInt()
        val checkDigits = (98 - mod97).toString().padStart(2, '0')
        return "NO$checkDigits${bban}"
    }

    private fun decodeJwtPayload(jwt: String): JsonNode {
        val parts = jwt.split(".")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid JWT format")
        }
        val payload = Base64.getUrlDecoder().decode(parts[1])
        return objectMapper.readTree(payload)
    }

    private fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val bytes = ByteArray(CODE_VERIFIER_LENGTH)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun generateState(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
