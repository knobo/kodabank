package no.kodabank.bff.routing

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Component
class CoreServiceClient(
    @Value("\${kodabank.core-url}") private val coreUrl: String
) {
    private val restClient: RestClient = RestClient.builder()
        .baseUrl(coreUrl)
        .build()

    // -- Accounts --

    fun getAccount(tenantId: String, accountId: String): JsonNode? {
        return restClient.get()
            .uri("/api/internal/accounts/{accountId}?tenantId={tenantId}", accountId, tenantId)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode::class.java)
    }

    fun openCurrentAccount(
        tenantId: String,
        partyId: String,
        iban: String,
        currency: String,
        productId: String,
        accountName: String,
        correlationId: String? = null
    ): JsonNode? {
        val body = mapOf(
            "tenantId" to tenantId,
            "partyId" to partyId,
            "iban" to iban,
            "currency" to currency,
            "productId" to productId,
            "accountName" to accountName,
            "correlationId" to correlationId
        )
        return restClient.post()
            .uri("/api/internal/accounts/current")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
    }

    fun deposit(
        tenantId: String,
        accountId: String,
        amount: BigDecimal,
        reference: String,
        counterpartyName: String? = null,
        counterpartyIban: String? = null,
        remittanceInfo: String? = null,
        correlationId: String? = null
    ): JsonNode? {
        val body = mapOf(
            "tenantId" to tenantId,
            "amount" to amount,
            "reference" to reference,
            "counterpartyName" to counterpartyName,
            "counterpartyIban" to counterpartyIban,
            "remittanceInfo" to remittanceInfo,
            "correlationId" to correlationId
        )
        return restClient.post()
            .uri("/api/internal/accounts/{accountId}/deposit", accountId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
    }

    fun withdraw(
        tenantId: String,
        accountId: String,
        amount: BigDecimal,
        reference: String,
        counterpartyName: String? = null,
        counterpartyIban: String? = null,
        remittanceInfo: String? = null,
        correlationId: String? = null
    ): JsonNode? {
        val body = mapOf(
            "tenantId" to tenantId,
            "amount" to amount,
            "reference" to reference,
            "counterpartyName" to counterpartyName,
            "counterpartyIban" to counterpartyIban,
            "remittanceInfo" to remittanceInfo,
            "correlationId" to correlationId
        )
        return restClient.post()
            .uri("/api/internal/accounts/{accountId}/withdraw", accountId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
    }

    // -- Payments --

    fun initiatePayment(
        tenantId: String,
        debtorAccountId: String,
        debtorIban: String,
        creditorIban: String,
        creditorName: String,
        amount: BigDecimal,
        currency: String = "NOK",
        reference: String? = null,
        remittanceInfo: String? = null,
        correlationId: String? = null
    ): JsonNode? {
        val body = mapOf(
            "tenantId" to tenantId,
            "debtorAccountId" to debtorAccountId,
            "debtorIban" to debtorIban,
            "creditorIban" to creditorIban,
            "creditorName" to creditorName,
            "amount" to amount,
            "currency" to currency,
            "reference" to reference,
            "remittanceInfo" to remittanceInfo,
            "correlationId" to correlationId
        )
        return restClient.post()
            .uri("/api/internal/payments/initiate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
    }

    fun getPayment(tenantId: String, paymentId: String): JsonNode? {
        return restClient.get()
            .uri("/api/internal/payments/{paymentId}?tenantId={tenantId}", paymentId, tenantId)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode::class.java)
    }

    // -- Parties --

    fun getParty(tenantId: String, partyId: String): JsonNode? {
        return restClient.get()
            .uri("/api/internal/parties/{partyId}?tenantId={tenantId}", partyId, tenantId)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode::class.java)
    }
}
