package no.kodabank.shared.auth

import no.kodabank.shared.domain.TenantId
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Extracts tenant and user context from the JWT token in the security context.
 */
object TenantContext {

    fun currentTenantId(): TenantId {
        val jwt = currentJwt()
        val tenantId = jwt.getClaimAsString("tenant_id")
            ?: throw IllegalStateException("JWT missing tenant_id claim")
        return TenantId(tenantId)
    }

    fun currentPartyId(): String {
        val jwt = currentJwt()
        return jwt.getClaimAsString("party_id")
            ?: throw IllegalStateException("JWT missing party_id claim")
    }

    fun currentUserId(): String {
        return currentJwt().subject
    }

    private fun currentJwt(): Jwt {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("No authentication in security context")
        return authentication.principal as? Jwt
            ?: throw IllegalStateException("Principal is not a JWT")
    }
}
