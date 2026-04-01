package no.kodabank.bff.routing

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Component
class ClearingServiceClient(
    @Value("\${kodabank.clearing-url}") private val clearingUrl: String
) {
    private val restClient: RestClient = RestClient.builder()
        .baseUrl(clearingUrl)
        .build()

    fun addClearingItem(
        debtorTenant: String,
        debtorIban: String,
        creditorTenant: String,
        creditorIban: String,
        creditorName: String,
        amount: BigDecimal,
        currency: String = "NOK",
        reference: String? = null,
        paymentExecutionId: String,
        correlationId: String? = null
    ): JsonNode? {
        val body = mapOf(
            "debtorTenant" to debtorTenant,
            "debtorIban" to debtorIban,
            "creditorTenant" to creditorTenant,
            "creditorIban" to creditorIban,
            "creditorName" to creditorName,
            "amount" to amount,
            "currency" to currency,
            "reference" to reference,
            "paymentExecutionId" to paymentExecutionId,
            "correlationId" to correlationId
        )
        return restClient.post()
            .uri("/api/clearing/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
    }

    fun processBatch(batchId: String): JsonNode? {
        return restClient.post()
            .uri("/api/clearing/batches/{batchId}/process", batchId)
            .retrieve()
            .body(JsonNode::class.java)
    }

    fun getBatch(batchId: String): JsonNode? {
        return restClient.get()
            .uri("/api/clearing/batches/{batchId}", batchId)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode::class.java)
    }
}
