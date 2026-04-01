package no.kodabank.admin.tenant.domain

import java.math.BigDecimal

sealed interface ProductEvent {
    val tenantId: String
    val productId: String
}

data class ProductDefined(
    override val tenantId: String,
    override val productId: String,
    val name: String,
    val type: String,
    val fees: Map<String, Any?> = emptyMap(),
    val features: Map<String, Any?> = emptyMap(),
    val interestRate: BigDecimal? = null
) : ProductEvent

data class ProductUpdated(
    override val tenantId: String,
    override val productId: String,
    val name: String? = null,
    val fees: Map<String, Any?>? = null,
    val features: Map<String, Any?>? = null,
    val interestRate: BigDecimal? = null
) : ProductEvent

data class ProductRetired(
    override val tenantId: String,
    override val productId: String
) : ProductEvent
