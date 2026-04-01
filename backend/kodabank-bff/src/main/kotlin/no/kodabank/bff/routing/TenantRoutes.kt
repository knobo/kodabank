package no.kodabank.bff.routing

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.kodabank.bff.auth.SessionManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient

@RestController
@RequestMapping("/api/v1/tenants")
class TenantRoutes(
    private val readModelQueries: ReadModelQueries,
    private val sessionManager: SessionManager,
    private val jdbc: JdbcTemplate,
    @Value("\${kodabank.admin-url}") private val adminUrl: String,
    @Value("\${kodabank.frontend-url}") private val frontendUrl: String
) {

    private val restClient = RestClient.create()

    @GetMapping
    fun listTenants(): ResponseEntity<List<TenantSummaryResponse>> {
        val tenants = readModelQueries.listTenants().map { it.toSummaryResponse() }
        return ResponseEntity.ok(tenants)
    }

    @GetMapping("/{id}/branding")
    fun getTenantBranding(@PathVariable id: String): ResponseEntity<*> {
        val branding = readModelQueries.getTenantBranding(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "not_found", "message" to "Tenant not found: $id"))
        return ResponseEntity.ok(branding.toBrandingResponse())
    }

    @PostMapping("/register")
    fun registerBank(
        @RequestBody body: Map<String, Any>,
        request: jakarta.servlet.http.HttpServletRequest
    ): ResponseEntity<*> {
        val accessToken = request.getAttribute("kodabank.accessToken") as? String
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "not_authenticated", "message" to "Login required to create a bank"))

        return try {
            val response = restClient.post()
                .uri("$adminUrl/api/tenants/register")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $accessToken")
                .body(body)
                .retrieve()
                .toEntity(Map::class.java)
            val tenantState = response.body ?: emptyMap<String, Any>()
            val tenantId = tenantState["tenantId"] as? String ?: ""
            val bankName = tenantState["bankName"] as? String ?: ""
            val result = mapOf("id" to tenantId, "bankName" to bankName, "url" to "/api/v1/$tenantId")
            ResponseEntity.status(response.statusCode).body(result)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(mapOf("error" to "admin_error", "message" to (e.message ?: "Failed to register bank")))
        }
    }

    @GetMapping("/{id}/admin/settings")
    fun getAdminSettings(
        @PathVariable id: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = sessionManager.getSession(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()
        val settings = readModelQueries.getTenantAdminSettings(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "not_found"))
        if (settings.ownerUserId != null && settings.ownerUserId != session.sub) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "not_owner"))
        }
        return ResponseEntity.ok(mapOf(
            "tenantId" to settings.tenantId,
            "bankName" to settings.bankName,
            "bankCode" to settings.bankCode,
            "currency" to settings.currency,
            "primaryColor" to (settings.primaryColor ?: ""),
            "tagline" to (settings.tagline ?: ""),
            "logoUrl" to (settings.logoUrl ?: ""),
            "status" to settings.status,
            "urlAlias" to (settings.urlAlias ?: ""),
            "accessPolicyType" to (settings.accessPolicyType ?: ""),
            "transferPolicyType" to (settings.transferPolicyType ?: "")
        ))
    }

    @PatchMapping("/{id}/admin/settings")
    fun updateAdminSettings(
        @PathVariable id: String,
        @RequestBody body: Map<String, Any>,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = sessionManager.getSession(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()
        val settings = readModelQueries.getTenantAdminSettings(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "not_found"))
        if (settings.ownerUserId != null && settings.ownerUserId != session.sub) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "not_owner"))
        }

        val urlAlias = (body["urlAlias"] as? String)?.trim()?.lowercase()
            ?.replace(Regex("[^a-z0-9-]"), "-")
            ?.take(50)

        if (urlAlias != null) {
            jdbc.update("UPDATE rm_tenants SET url_alias = ? WHERE tenant_id = ?",
                urlAlias.ifBlank { null }, id)
        }

        return ResponseEntity.ok(mapOf("updated" to true, "urlAlias" to (urlAlias ?: "")))
    }
}

@RestController
class BankAliasRedirect(
    private val readModelQueries: ReadModelQueries,
    @Value("\${kodabank.frontend-url}") private val frontendUrl: String
) {
    @GetMapping("/go/{alias}")
    fun redirect(@PathVariable alias: String, response: HttpServletResponse) {
        val tenant = readModelQueries.getTenantByAlias(alias)
        if (tenant != null) {
            response.sendRedirect("$frontendUrl/${tenant.tenantId}/login")
        } else {
            response.sendRedirect("$frontendUrl?error=bank_not_found")
        }
    }
}

data class TenantSummaryResponse(
    val id: String,
    val name: String,
    val bankCode: String,
    val country: String,
    val currency: String,
    val primaryColor: String?,
    val logoUrl: String?,
    val tagline: String?
)

data class TenantBrandingResponse(
    val id: String,
    val bankName: String,
    val bankCode: String,
    val ibanPrefix: String,
    val country: String,
    val currency: String,
    val primaryColor: String?,
    val secondaryColor: String?,
    val logoUrl: String?,
    val tagline: String?
)

private fun TenantBrandingView.toSummaryResponse() = TenantSummaryResponse(
    id = tenantId,
    name = bankName,
    bankCode = bankCode,
    country = country,
    currency = currency,
    primaryColor = primaryColor,
    logoUrl = logoUrl,
    tagline = tagline
)

private fun TenantBrandingView.toBrandingResponse() = TenantBrandingResponse(
    id = tenantId,
    bankName = bankName,
    bankCode = bankCode,
    ibanPrefix = ibanPrefix,
    country = country,
    currency = currency,
    primaryColor = primaryColor,
    secondaryColor = secondaryColor,
    logoUrl = logoUrl,
    tagline = tagline
)
