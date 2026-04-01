package no.kodabank.gateway.web

import no.kodabank.gateway.application.MerchantService
import no.kodabank.gateway.domain.MerchantState
import no.kodabank.shared.domain.TenantId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Admin endpoints for merchant management.
 * These are not protected by the API key filter (intended for internal/admin use).
 */
@RestController
@RequestMapping("/api/v1/admin/merchants")
class MerchantAdminController(
    private val merchantService: MerchantService
) {

    @PostMapping
    fun registerMerchant(@RequestBody body: RegisterMerchantRequest): ResponseEntity<RegisterMerchantResponse> {
        val (merchant, rawApiKey) = merchantService.register(
            tenantId = TenantId(body.tenantId),
            merchantName = body.merchantName,
            callbackUrl = body.callbackUrl
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            RegisterMerchantResponse(merchant = merchant, apiKey = rawApiKey)
        )
    }

    @PostMapping("/{merchantId}/rotate-key")
    fun rotateApiKey(
        @PathVariable merchantId: String,
        @RequestParam tenantId: String
    ): RegisterMerchantResponse {
        val (merchant, rawApiKey) = merchantService.rotateApiKey(TenantId(tenantId), merchantId)
        return RegisterMerchantResponse(merchant = merchant, apiKey = rawApiKey)
    }

    @PostMapping("/{merchantId}/deactivate")
    fun deactivate(
        @PathVariable merchantId: String,
        @RequestParam tenantId: String,
        @RequestBody body: DeactivateRequest
    ): MerchantState {
        return merchantService.deactivate(TenantId(tenantId), merchantId, body.reason)
    }

    @GetMapping("/{merchantId}")
    fun getMerchant(
        @PathVariable merchantId: String,
        @RequestParam tenantId: String
    ): MerchantState {
        return merchantService.getMerchant(TenantId(tenantId), merchantId)
    }
}

data class RegisterMerchantRequest(
    val tenantId: String,
    val merchantName: String,
    val callbackUrl: String
)

data class RegisterMerchantResponse(
    val merchant: MerchantState,
    val apiKey: String
)

data class DeactivateRequest(
    val reason: String
)
