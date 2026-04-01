package no.kodabank.admin.tenant.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

/**
 * Data classes matching the bank YAML definition structure for deserialization with Jackson YAML.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BankDefinition(
    val tenant: TenantDefinition,
    val products: List<ProductDefinition> = emptyList(),
    val demoData: DemoDataDefinition? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TenantDefinition(
    val id: String,
    val bankName: String,
    val bankCode: String,
    val country: String = "NO",
    val currency: String = "NOK",
    val branding: BrandingDefinition = BrandingDefinition()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BrandingDefinition(
    val primaryColor: String = "#000000",
    val secondaryColor: String = "#FFFFFF",
    val logo: String = "",
    val tagline: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProductDefinition(
    val id: String,
    val name: String,
    val type: String,
    val fees: Map<String, Any?> = emptyMap(),
    val features: Map<String, Any?> = emptyMap(),
    val interestRate: BigDecimal? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DemoDataDefinition(
    val customers: List<CustomerDefinition> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CustomerDefinition(
    val firstName: String,
    val lastName: String,
    val nationalId: String,
    val accounts: List<AccountDefinition> = emptyList(),
    val cards: List<CardDefinition> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountDefinition(
    val type: String,
    val product: String,
    val initialBalance: BigDecimal = BigDecimal.ZERO,
    val generateTransactions: Int = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CardDefinition(
    val type: String,
    val linkedAccount: Int = 0
)
