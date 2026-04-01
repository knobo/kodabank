package no.kodabank.admin.tenant.web

import no.kodabank.admin.tenant.application.BankDefinition
import no.kodabank.admin.tenant.application.DemoDataGenerator
import no.kodabank.admin.tenant.application.ProductDefinition
import no.kodabank.admin.tenant.application.TenantProvisioningService
import no.kodabank.admin.tenant.domain.*
import no.kodabank.shared.auth.TenantContext
import no.kodabank.shared.domain.TenantId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/tenants")
class TenantController(
    private val provisioningService: TenantProvisioningService,
    private val demoDataGenerator: DemoDataGenerator
) {

    @PostMapping
    fun createTenant(@RequestBody definition: BankDefinition): ResponseEntity<TenantState> {
        val state = provisioningService.createTenant(definition)
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    @PostMapping("/register")
    fun registerBank(@RequestBody request: BankRegistrationRequest): ResponseEntity<TenantState> {
        val ownerUserId = TenantContext.currentUserId()
        val branding = BrandingInfo(
            primaryColor = request.branding?.primaryColor ?: "#000000",
            secondaryColor = request.branding?.secondaryColor ?: "#FFFFFF",
            logo = request.branding?.logo ?: "",
            tagline = request.branding?.tagline ?: ""
        )
        val state = provisioningService.registerBank(
            ownerUserId = ownerUserId,
            bankName = request.bankName,
            currency = request.currency ?: "NOK",
            branding = branding,
            accessPolicy = request.accessPolicy ?: AccessPolicy(type = AccessPolicyType.AUTO_APPROVE),
            transferPolicy = request.transferPolicy ?: TransferPolicy(type = TransferPolicyType.OPEN)
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    @PostMapping("/import")
    fun importFromYaml(@RequestBody yamlBody: String): ResponseEntity<TenantState> {
        val definition = provisioningService.importFromYaml(yamlBody)
        val state = provisioningService.createTenant(definition)
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    @GetMapping
    fun listTenants(): ResponseEntity<List<TenantState>> {
        val tenants = provisioningService.listTenants()
        return ResponseEntity.ok(tenants)
    }

    @GetMapping("/public")
    fun listPublicTenants(): ResponseEntity<List<PublicTenantInfo>> {
        val tenants = provisioningService.listTenants()
            .filter { it.status == TenantStatus.ACTIVE }
            .map { PublicTenantInfo(
                id = it.tenantId,
                name = it.bankName,
                branding = it.branding
            ) }
        return ResponseEntity.ok(tenants)
    }

    @GetMapping("/{tenantId}")
    fun getTenant(@PathVariable tenantId: String): ResponseEntity<TenantState> {
        val state = provisioningService.loadTenant(TenantId(tenantId))
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(state)
    }

    @PutMapping("/{tenantId}/branding")
    fun updateBranding(
        @PathVariable tenantId: String,
        @RequestBody branding: BrandingUpdateRequest
    ): ResponseEntity<TenantState> {
        val event = BankBrandingUpdated(
            tenantId = tenantId,
            primaryColor = branding.primaryColor,
            secondaryColor = branding.secondaryColor,
            logo = branding.logo,
            tagline = branding.tagline
        )
        val state = provisioningService.updateBranding(TenantId(tenantId), event)
        return ResponseEntity.ok(state)
    }

    @PutMapping("/{tenantId}/transfer-policy")
    fun updateTransferPolicy(
        @PathVariable tenantId: String,
        @RequestBody request: TransferPolicyRequest
    ): ResponseEntity<TenantState> {
        val policy = TransferPolicy(
            type = request.type,
            whitelist = request.whitelist ?: emptyList(),
            domainCode = request.domainCode
        )
        val state = provisioningService.updateTransferPolicy(TenantId(tenantId), policy)
        return ResponseEntity.ok(state)
    }

    @PutMapping("/{tenantId}/access-policy")
    fun updateAccessPolicy(
        @PathVariable tenantId: String,
        @RequestBody request: AccessPolicyRequest
    ): ResponseEntity<TenantState> {
        val policy = AccessPolicy(
            type = request.type,
            webhookUrl = request.webhookUrl
        )
        val state = provisioningService.updateAccessPolicy(TenantId(tenantId), policy)
        return ResponseEntity.ok(state)
    }

    @PostMapping("/{tenantId}/products")
    fun addProduct(
        @PathVariable tenantId: String,
        @RequestBody product: ProductDefinition
    ): ResponseEntity<Void> {
        provisioningService.createProduct(TenantId(tenantId), product)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PostMapping("/{tenantId}/demo-data")
    fun generateDemoData(
        @PathVariable tenantId: String,
        @RequestBody(required = false) yamlBody: String?
    ): ResponseEntity<Map<String, Any>> {
        val definition = if (yamlBody != null) {
            provisioningService.importFromYaml(yamlBody)
        } else {
            // Load the tenant to verify it exists, then load from stored bank definition
            provisioningService.loadTenant(TenantId(tenantId))
                ?: return ResponseEntity.notFound().build()

            // Try to load the bank definition from file on classpath
            val yamlResource = javaClass.classLoader.getResourceAsStream("banks/$tenantId.yaml")
                ?: return ResponseEntity.badRequest().body(mapOf(
                    "status" to "error" as Any,
                    "message" to "No bank definition found for '$tenantId'. Provide YAML in request body." as Any
                ))
            provisioningService.importFromYaml(yamlResource.bufferedReader().readText())
        }

        val result = demoDataGenerator.generate(definition)
        return ResponseEntity.ok(mapOf(
            "status" to "completed" as Any,
            "tenantId" to tenantId as Any,
            "customersCreated" to result.customersCreated as Any,
            "accountsCreated" to result.accountsCreated as Any,
            "cardsCreated" to result.cardsCreated as Any,
            "transactionsGenerated" to result.transactionsGenerated as Any
        ))
    }
}

data class BrandingUpdateRequest(
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val logo: String? = null,
    val tagline: String? = null
)

data class BankRegistrationRequest(
    val bankName: String,
    val currency: String? = null,
    val branding: BrandingUpdateRequest? = null,
    val accessPolicy: AccessPolicy? = null,
    val transferPolicy: TransferPolicy? = null
)

data class TransferPolicyRequest(
    val type: TransferPolicyType,
    val whitelist: List<String>? = null,
    val domainCode: String? = null
)

data class AccessPolicyRequest(
    val type: AccessPolicyType,
    val webhookUrl: String? = null
)

data class PublicTenantInfo(
    val id: String,
    val name: String,
    val branding: BrandingInfo
)
