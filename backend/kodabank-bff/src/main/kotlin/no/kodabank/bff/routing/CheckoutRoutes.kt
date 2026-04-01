package no.kodabank.bff.routing

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import no.kodabank.bff.auth.SessionData
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * BFF routes for the payment checkout flow.
 * These allow the nettbank frontend to fetch payment order details
 * and authorize payments on behalf of the logged-in user.
 */
@RestController
@RequestMapping("/api/v1/{tenant}/checkout")
class CheckoutRoutes(
    private val paymentGatewayClient: PaymentGatewayClient,
    private val readModelQueries: ReadModelQueries
) {

    private val log = LoggerFactory.getLogger(CheckoutRoutes::class.java)

    /**
     * Fetch payment order details for display on the checkout page.
     */
    @GetMapping("/{orderId}")
    fun getCheckoutOrder(
        @PathVariable tenant: String,
        @PathVariable orderId: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        return try {
            val order = paymentGatewayClient.getPaymentOrder(session.tenantId, orderId)
            if (order == null) {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "not_found", "message" to "Payment order not found"))
            } else {
                ResponseEntity.ok(order)
            }
        } catch (e: Exception) {
            log.error("Failed to fetch checkout order {} for tenant={}", orderId, tenant, e)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(mapOf("error" to "service_error", "message" to "Failed to fetch payment order"))
        }
    }

    /**
     * Authorize a payment order. The user selects which account to pay from.
     */
    @PostMapping("/{orderId}/authorize")
    fun authorizeCheckout(
        @PathVariable tenant: String,
        @PathVariable orderId: String,
        @RequestBody body: AuthorizeCheckoutRequest,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = validateSession(request, tenant) ?: return unauthorizedResponse()

        // Verify the selected account belongs to this user
        val accounts = readModelQueries.getAccountsForParty(session.tenantId, session.partyId)
        if (accounts.none { it.accountId == body.accountId }) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "forbidden", "message" to "Account does not belong to this user"))
        }

        return try {
            val result = paymentGatewayClient.authorizePaymentOrder(
                tenantId = session.tenantId,
                orderId = orderId,
                userId = session.partyId,
                payerAccountId = body.accountId
            )
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("Failed to authorize checkout order {} for tenant={} party={}", orderId, tenant, session.partyId, e)
            val message = e.message ?: "Failed to authorize payment"
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "authorization_failed", "message" to message))
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

data class AuthorizeCheckoutRequest(
    @JsonProperty("accountId") val accountId: String
)
