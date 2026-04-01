package no.kodabank.clearing.domain

import java.math.BigDecimal
import java.time.Instant

// -- Clearing Batch Events (shared, no tenant prefix) --

sealed interface ClearingBatchEvent {
    val batchId: String
}

data class ClearingBatchCreated(
    override val batchId: String,
    val createdAt: String
) : ClearingBatchEvent

data class ClearingItemAdded(
    override val batchId: String,
    val itemId: String,
    val debtorTenant: String,
    val debtorIban: String,
    val creditorTenant: String,
    val creditorIban: String,
    val creditorName: String,
    val amount: BigDecimal,
    val currency: String,
    val reference: String?,
    val paymentExecutionId: String,
    val correlationId: String?
) : ClearingBatchEvent

data class ClearingBatchProcessed(
    override val batchId: String,
    val processedAt: String,
    val totalItems: Int,
    val totalAmount: BigDecimal
) : ClearingBatchEvent

data class ClearingItemSettled(
    override val batchId: String,
    val itemId: String,
    val settlementId: String
) : ClearingBatchEvent

data class ClearingItemRejected(
    override val batchId: String,
    val itemId: String,
    val debtorTenant: String,
    val creditorTenant: String,
    val reason: String,
    val rejectedAt: Instant
) : ClearingBatchEvent

// -- Settlement Events (shared) --

sealed interface SettlementEvent {
    val settlementId: String
}

data class SettlementInitiated(
    override val settlementId: String,
    val clearingBatchId: String,
    val clearingItemId: String,
    val debtorTenant: String,
    val creditorTenant: String,
    val amount: BigDecimal,
    val currency: String,
    val creditorIban: String,
    val creditorName: String,
    val reference: String?,
    val correlationId: String?
) : SettlementEvent

data class DebtorBankDebited(
    override val settlementId: String,
    val tenantId: String,
    val amount: BigDecimal
) : SettlementEvent

data class CreditorBankCredited(
    override val settlementId: String,
    val tenantId: String,
    val amount: BigDecimal
) : SettlementEvent

data class CreditorCustomerCredited(
    override val settlementId: String,
    val creditorIban: String,
    val amount: BigDecimal
) : SettlementEvent

data class SettlementCompleted(
    override val settlementId: String,
    val completedAt: String
) : SettlementEvent
