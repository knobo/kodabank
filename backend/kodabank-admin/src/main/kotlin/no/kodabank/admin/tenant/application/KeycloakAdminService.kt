package no.kodabank.admin.tenant.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Creates Keycloak client scopes for new tenants and adds them as optional scopes
 * to all relevant clients so tenant_id is injected into JWT tokens.
 */
@Service
class KeycloakAdminService(
    @Value("\${keycloak.url}") private val keycloakUrl: String,
    @Value("\${keycloak.realm}") private val realm: String
) {
    private val log = LoggerFactory.getLogger(KeycloakAdminService::class.java)
    private val restClient = RestClient.create()

    companion object {
        /** Clients that should receive the new tenant scope as an optional scope. */
        private val TARGET_CLIENT_IDS = listOf(
            "kodabank-nettbank",
            "kodabank-admin-ui",
            "kodabank-bff",
            "kodabank-core",
            "kodabank-clearing",
            "kodabank-admin-service"
        )
    }

    /**
     * Creates a `tenant:{tenantId}` client scope in Keycloak with a hardcoded claim mapper
     * that puts the tenantId into the `tenant_id` JWT claim, then adds the scope as optional
     * to all relevant clients.
     */
    fun createTenantScope(tenantId: String) {
        val token = obtainAdminToken()
        val scopeName = "tenant:$tenantId"

        val scopeId = createClientScope(token, scopeName, tenantId)
        log.info("Created Keycloak client scope '{}' with id {}", scopeName, scopeId)

        addScopeToClients(token, scopeId)
        log.info("Added scope '{}' as optional to {} clients", scopeName, TARGET_CLIENT_IDS.size)
    }

    /**
     * Obtains an admin access token from Keycloak's master realm using the admin password grant.
     */
    private fun obtainAdminToken(): String {
        val response = restClient.post()
            .uri("$keycloakUrl/realms/master/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("grant_type=password&client_id=admin-cli&username=admin&password=admin")
            .retrieve()
            .body(Map::class.java)
            ?: throw RuntimeException("Failed to obtain Keycloak admin token")

        return response["access_token"] as String
    }

    /**
     * Creates a client scope with a hardcoded claim mapper that injects tenant_id into tokens.
     * Returns the UUID of the newly created scope.
     */
    private fun createClientScope(token: String, scopeName: String, tenantId: String): String {
        val scopePayload = mapOf(
            "name" to scopeName,
            "description" to "Tenant scope for $tenantId",
            "protocol" to "openid-connect",
            "attributes" to mapOf(
                "include.in.token.scope" to "true",
                "display.on.consent.screen" to "false"
            ),
            "protocolMappers" to listOf(
                mapOf(
                    "name" to "tenant_id-$tenantId",
                    "protocol" to "openid-connect",
                    "protocolMapper" to "oidc-hardcoded-claim-mapper",
                    "consentRequired" to false,
                    "config" to mapOf(
                        "claim.name" to "tenant_id",
                        "claim.value" to tenantId,
                        "jsonType.label" to "String",
                        "id.token.claim" to "true",
                        "access.token.claim" to "true",
                        "userinfo.token.claim" to "true"
                    )
                )
            )
        )

        restClient.post()
            .uri("$keycloakUrl/admin/realms/$realm/client-scopes")
            .headers { it.setBearerAuth(token) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(scopePayload)
            .retrieve()
            .toBodilessEntity()

        // Keycloak returns the new scope's ID in the Location header, but to be safe
        // we look it up by name.
        return findScopeIdByName(token, scopeName)
    }

    /**
     * Finds a client scope UUID by its name.
     */
    private fun findScopeIdByName(token: String, scopeName: String): String {
        @Suppress("UNCHECKED_CAST")
        val scopes = restClient.get()
            .uri("$keycloakUrl/admin/realms/$realm/client-scopes")
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .body(List::class.java) as List<Map<String, Any>>

        return scopes.first { it["name"] == scopeName }["id"] as String
    }

    /**
     * Adds the scope as an optional client scope to all target clients.
     */
    private fun addScopeToClients(token: String, scopeId: String) {
        val clientUuids = findClientUuids(token)

        for (clientId in TARGET_CLIENT_IDS) {
            val clientUuid = clientUuids[clientId]
            if (clientUuid == null) {
                log.warn("Client '{}' not found in Keycloak realm '{}', skipping", clientId, realm)
                continue
            }

            restClient.put()
                .uri("$keycloakUrl/admin/realms/$realm/clients/$clientUuid/optional-client-scopes/$scopeId")
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .toBodilessEntity()

            log.debug("Added scope to client '{}'", clientId)
        }
    }

    /**
     * Lists all clients in the realm and returns a map of clientId -> UUID.
     */
    private fun findClientUuids(token: String): Map<String, String> {
        @Suppress("UNCHECKED_CAST")
        val clients = restClient.get()
            .uri("$keycloakUrl/admin/realms/$realm/clients")
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .body(List::class.java) as List<Map<String, Any>>

        return clients
            .filter { it["clientId"] in TARGET_CLIENT_IDS }
            .associate { it["clientId"] as String to it["id"] as String }
    }
}
