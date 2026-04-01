package no.kodabank.gateway.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.kodabank.gateway.application.MerchantService
import no.kodabank.gateway.domain.MerchantState
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates merchant API requests using Bearer token API keys.
 * Extracts the key from the Authorization header, hashes it with SHA-256,
 * and looks up the merchant by hash.
 */
@Component
class ApiKeyFilter(
    private val merchantService: MerchantService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI

        // Skip authentication for actuator and merchant admin endpoints
        if (path.startsWith("/actuator") || path.startsWith("/api/v1/admin")) {
            filterChain.doFilter(request, response)
            return
        }

        // Only protect /api/v1/ endpoints
        if (!path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response)
            return
        }

        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header")
            return
        }

        val rawApiKey = authHeader.removePrefix("Bearer ").trim()
        if (rawApiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Empty API key")
            return
        }

        val apiKeyHash = MerchantService.hashApiKey(rawApiKey)
        val merchant = merchantService.findByApiKeyHash(apiKeyHash)

        if (merchant == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key")
            return
        }

        request.setAttribute(MERCHANT_ATTRIBUTE, merchant)
        filterChain.doFilter(request, response)
    }

    companion object {
        const val MERCHANT_ATTRIBUTE = "authenticatedMerchant"
    }
}

/**
 * Helper to extract the authenticated merchant from the request.
 */
fun HttpServletRequest.authenticatedMerchant(): MerchantState =
    getAttribute(ApiKeyFilter.MERCHANT_ATTRIBUTE) as MerchantState
