package no.kodabank.bff.routing

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Client for service-to-service calls to the payment gateway's internal API.
 * These endpoints bypass the merchant API key filter.
 */
@Component
class PaymentGatewayClient(
    @Value("\${kodabank.payment-gateway-url}") private val gatewayUrl: String
) {
    private val restClient: RestClient = RestClient.builder()
        .baseUrl(gatewayUrl)
        .build()

    fun getPaymentOrder(tenantId: String, orderId: String): JsonNode? {
        return restClient.get()
            .uri("/api/internal/payments/{orderId}?tenantId={tenantId}", orderId, tenantId)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode::class.java)
    }

    fun authorizePaymentOrder(
        tenantId: String,
        orderId: String,
        userId: String,
        payerAccountId: String
    ): JsonNode? {
        val body = mapOf(
            "tenantId" to tenantId,
            "userId" to userId,
            "payerAccountId" to payerAccountId
        )
        return restClient.post()
            .uri("/api/internal/payments/{orderId}/authorize", orderId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
    }
}
