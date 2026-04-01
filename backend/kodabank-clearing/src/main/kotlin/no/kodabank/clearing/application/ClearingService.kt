package no.kodabank.clearing.application

import com.fasterxml.jackson.databind.ObjectMapper
import no.kodabank.clearing.domain.*
import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class ClearingService(
    private val store: KodaStoreClient,
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val BATCH_CATEGORY = "ClearingBatch"
        private const val SETTLEMENT_CATEGORY = "Settlement"
    }

    private data class TransferPolicyRow(
        val tenantId: String,
        val policyType: String,
        val whitelist: List<String>,
        val domainCode: String?
    )

    private fun loadTransferPolicy(tenantId: String): TransferPolicyRow? {
        val rows = jdbc.queryForList(
            "SELECT tenant_id, policy_type, whitelist, domain_code FROM rm_transfer_policies WHERE tenant_id = ?",
            tenantId
        )
        if (rows.isEmpty()) return null
        val row = rows.first()
        val whitelistJson = row["whitelist"]?.toString() ?: "[]"
        @Suppress("UNCHECKED_CAST")
        val whitelist = try {
            objectMapper.readValue(whitelistJson, List::class.java) as List<String>
        } catch (_: Exception) {
            emptyList<String>()
        }
        return TransferPolicyRow(
            tenantId = row["tenant_id"] as String,
            policyType = (row["policy_type"] as? String) ?: "OPEN",
            whitelist = whitelist,
            domainCode = row["domain_code"] as? String
        )
    }

    /**
     * Check transfer policies for both debtor and creditor banks.
     * Throws [TransferPolicyRejectedException] if either bank's policy rejects the transfer.
     */
    private fun checkTransferPolicy(debtorTenant: String, creditorTenant: String) {
        val debtorPolicy = loadTransferPolicy(debtorTenant)
        val creditorPolicy = loadTransferPolicy(creditorTenant)

        // Check debtor (sending bank) outbound policy
        if (debtorPolicy != null) {
            when (debtorPolicy.policyType) {
                "CLOSED" -> throw TransferPolicyRejectedException(
                    "Debtor bank '$debtorTenant' has CLOSED transfer policy; outbound transfers are not allowed"
                )
                "WHITELIST" -> {
                    if (creditorTenant !in debtorPolicy.whitelist) {
                        throw TransferPolicyRejectedException(
                            "Debtor bank '$debtorTenant' does not have creditor bank '$creditorTenant' in its transfer whitelist"
                        )
                    }
                }
                "DOMAIN_CODE" -> {
                    if (debtorPolicy.domainCode == null ||
                        creditorPolicy?.domainCode == null ||
                        debtorPolicy.domainCode != creditorPolicy.domainCode
                    ) {
                        throw TransferPolicyRejectedException(
                            "Debtor bank '$debtorTenant' and creditor bank '$creditorTenant' do not share the same domain code"
                        )
                    }
                }
                // OPEN -> allow
            }
        }

        // Check creditor (receiving bank) inbound policy
        if (creditorPolicy != null) {
            when (creditorPolicy.policyType) {
                "CLOSED" -> throw TransferPolicyRejectedException(
                    "Creditor bank '$creditorTenant' has CLOSED transfer policy; inbound transfers are not allowed"
                )
                "WHITELIST" -> {
                    if (debtorTenant !in creditorPolicy.whitelist) {
                        throw TransferPolicyRejectedException(
                            "Creditor bank '$creditorTenant' does not have debtor bank '$debtorTenant' in its transfer whitelist"
                        )
                    }
                }
                "DOMAIN_CODE" -> {
                    if (creditorPolicy.domainCode == null ||
                        debtorPolicy?.domainCode == null ||
                        creditorPolicy.domainCode != debtorPolicy.domainCode
                    ) {
                        throw TransferPolicyRejectedException(
                            "Creditor bank '$creditorTenant' and debtor bank '$debtorTenant' do not share the same domain code"
                        )
                    }
                }
                // OPEN -> allow
            }
        }
    }

    /**
     * Get or create current open clearing batch.
     * For the demo, we use a simple daily batch ID.
     */
    fun getOrCreateBatch(): ClearingBatchState {
        val batchId = "batch-${java.time.LocalDate.now()}"
        val streamId = "$BATCH_CATEGORY-$batchId"
        val stream = store.readStream(streamId)

        if (stream.events.isEmpty()) {
            val event = ClearingBatchCreated(batchId = batchId, createdAt = Instant.now().toString())
            store.append(streamId, null, listOf(batchEventToRequest(event, null)))
            return loadBatch(batchId)
        }
        return rebuildBatch(stream.events)
    }

    fun addClearingItem(
        debtorTenant: String,
        debtorIban: String,
        creditorTenant: String,
        creditorIban: String,
        creditorName: String,
        amount: BigDecimal,
        currency: String,
        reference: String?,
        paymentExecutionId: String,
        correlationId: String?
    ): ClearingBatchState {
        val batch = getOrCreateBatch()
        val itemId = UUID.randomUUID().toString()

        // Check transfer policies before adding the item
        try {
            checkTransferPolicy(debtorTenant, creditorTenant)
        } catch (e: TransferPolicyRejectedException) {
            val rejectedEvent = ClearingItemRejected(
                batchId = batch.batchId,
                itemId = itemId,
                debtorTenant = debtorTenant,
                creditorTenant = creditorTenant,
                reason = e.message ?: "Transfer policy rejected",
                rejectedAt = Instant.now()
            )
            val streamId = "$BATCH_CATEGORY-${batch.batchId}"
            store.append(streamId, batch.version, listOf(batchEventToRequest(rejectedEvent, correlationId)))
            throw e
        }

        val event = ClearingItemAdded(
            batchId = batch.batchId,
            itemId = itemId,
            debtorTenant = debtorTenant,
            debtorIban = debtorIban,
            creditorTenant = creditorTenant,
            creditorIban = creditorIban,
            creditorName = creditorName,
            amount = amount,
            currency = currency,
            reference = reference,
            paymentExecutionId = paymentExecutionId,
            correlationId = correlationId
        )
        val streamId = "$BATCH_CATEGORY-${batch.batchId}"
        store.append(streamId, batch.version, listOf(batchEventToRequest(event, correlationId)))
        return loadBatch(batch.batchId)
    }

    /**
     * Process a clearing batch: settle all unsettled items.
     * For the demo, settlement is instant.
     */
    fun processBatch(batchId: String): ClearingBatchState {
        val batch = loadBatch(batchId)
        require(!batch.processed) { "Batch already processed" }

        val streamId = "$BATCH_CATEGORY-$batchId"
        val totalAmount = batch.items.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }

        // Mark batch as processed
        val processedEvent = ClearingBatchProcessed(
            batchId = batchId,
            processedAt = Instant.now().toString(),
            totalItems = batch.items.size,
            totalAmount = totalAmount
        )
        store.append(streamId, batch.version, listOf(batchEventToRequest(processedEvent, null)))

        // Settle each item
        var currentBatch = loadBatch(batchId)
        for (item in currentBatch.items.filter { !it.settled }) {
            val settlementId = UUID.randomUUID().toString()
            settleItem(settlementId, batchId, item)

            val settledEvent = ClearingItemSettled(
                batchId = batchId,
                itemId = item.itemId,
                settlementId = settlementId
            )
            currentBatch = loadBatch(batchId)
            store.append(streamId, currentBatch.version, listOf(batchEventToRequest(settledEvent, item.correlationId)))
            currentBatch = loadBatch(batchId)
        }

        return loadBatch(batchId)
    }

    private fun settleItem(settlementId: String, batchId: String, item: ClearingItem) {
        val streamId = "$SETTLEMENT_CATEGORY-$settlementId"

        val events = listOf(
            settlementEventToRequest(SettlementInitiated(
                settlementId = settlementId,
                clearingBatchId = batchId,
                clearingItemId = item.itemId,
                debtorTenant = item.debtorTenant,
                creditorTenant = item.creditorTenant,
                amount = item.amount,
                currency = item.currency,
                creditorIban = item.creditorIban,
                creditorName = item.creditorName,
                reference = item.reference,
                correlationId = item.correlationId
            ), item.correlationId),
            settlementEventToRequest(DebtorBankDebited(
                settlementId = settlementId,
                tenantId = item.debtorTenant,
                amount = item.amount
            ), item.correlationId),
            settlementEventToRequest(CreditorBankCredited(
                settlementId = settlementId,
                tenantId = item.creditorTenant,
                amount = item.amount
            ), item.correlationId),
            settlementEventToRequest(CreditorCustomerCredited(
                settlementId = settlementId,
                creditorIban = item.creditorIban,
                amount = item.amount
            ), item.correlationId),
            settlementEventToRequest(SettlementCompleted(
                settlementId = settlementId,
                completedAt = Instant.now().toString()
            ), item.correlationId)
        )

        store.append(streamId, null, events)
    }

    fun getBatch(batchId: String): ClearingBatchState = loadBatch(batchId)

    private fun loadBatch(batchId: String): ClearingBatchState {
        val streamId = "$BATCH_CATEGORY-$batchId"
        val stream = store.readStream(streamId)
        if (stream.events.isEmpty()) throw BatchNotFoundException(batchId)
        return rebuildBatch(stream.events)
    }

    private fun rebuildBatch(events: List<RecordedEvent>): ClearingBatchState {
        val parsed = events.map { parseBatchEvent(it) }
        return ClearingBatchState.rebuild(parsed)
    }

    private fun batchEventToRequest(event: ClearingBatchEvent, correlationId: String?): NewEventRequest {
        val metadata = buildMap<String, Any?> {
            put("sourceService", "kodabank-clearing")
            correlationId?.let { put("correlationId", it) }
        }
        val (eventType, payload) = when (event) {
            is ClearingBatchCreated -> "ClearingBatchCreated" to mapOf<String, Any?>(
                "batchId" to event.batchId, "createdAt" to event.createdAt
            )
            is ClearingItemAdded -> "ClearingItemAdded" to mapOf<String, Any?>(
                "batchId" to event.batchId, "itemId" to event.itemId,
                "debtorTenant" to event.debtorTenant, "debtorIban" to event.debtorIban,
                "creditorTenant" to event.creditorTenant, "creditorIban" to event.creditorIban,
                "creditorName" to event.creditorName, "amount" to event.amount,
                "currency" to event.currency, "reference" to event.reference,
                "paymentExecutionId" to event.paymentExecutionId, "correlationId" to event.correlationId
            )
            is ClearingBatchProcessed -> "ClearingBatchProcessed" to mapOf<String, Any?>(
                "batchId" to event.batchId, "processedAt" to event.processedAt,
                "totalItems" to event.totalItems, "totalAmount" to event.totalAmount
            )
            is ClearingItemSettled -> "ClearingItemSettled" to mapOf<String, Any?>(
                "batchId" to event.batchId, "itemId" to event.itemId,
                "settlementId" to event.settlementId
            )
            is ClearingItemRejected -> "ClearingItemRejected" to mapOf<String, Any?>(
                "batchId" to event.batchId, "itemId" to event.itemId,
                "debtorTenant" to event.debtorTenant, "creditorTenant" to event.creditorTenant,
                "reason" to event.reason, "rejectedAt" to event.rejectedAt.toString()
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    private fun settlementEventToRequest(event: SettlementEvent, correlationId: String?): NewEventRequest {
        val metadata = buildMap<String, Any?> {
            put("sourceService", "kodabank-clearing")
            correlationId?.let { put("correlationId", it) }
        }
        val (eventType, payload) = when (event) {
            is SettlementInitiated -> "SettlementInitiated" to mapOf<String, Any?>(
                "settlementId" to event.settlementId, "clearingBatchId" to event.clearingBatchId,
                "clearingItemId" to event.clearingItemId, "debtorTenant" to event.debtorTenant,
                "creditorTenant" to event.creditorTenant, "amount" to event.amount,
                "currency" to event.currency, "creditorIban" to event.creditorIban,
                "creditorName" to event.creditorName, "reference" to event.reference,
                "correlationId" to event.correlationId
            )
            is DebtorBankDebited -> "DebtorBankDebited" to mapOf<String, Any?>(
                "settlementId" to event.settlementId, "tenantId" to event.tenantId,
                "amount" to event.amount
            )
            is CreditorBankCredited -> "CreditorBankCredited" to mapOf<String, Any?>(
                "settlementId" to event.settlementId, "tenantId" to event.tenantId,
                "amount" to event.amount
            )
            is CreditorCustomerCredited -> "CreditorCustomerCredited" to mapOf<String, Any?>(
                "settlementId" to event.settlementId, "creditorIban" to event.creditorIban,
                "amount" to event.amount
            )
            is SettlementCompleted -> "SettlementCompleted" to mapOf<String, Any?>(
                "settlementId" to event.settlementId, "completedAt" to event.completedAt
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    private fun parseBatchEvent(recorded: RecordedEvent): Pair<ClearingBatchEvent, Int> {
        val p = recorded.payload
        val event: ClearingBatchEvent = when (recorded.eventType) {
            "ClearingBatchCreated" -> ClearingBatchCreated(p["batchId"] as String, p["createdAt"] as String)
            "ClearingItemAdded" -> ClearingItemAdded(
                batchId = p["batchId"] as String, itemId = p["itemId"] as String,
                debtorTenant = p["debtorTenant"] as String, debtorIban = p["debtorIban"] as String,
                creditorTenant = p["creditorTenant"] as String, creditorIban = p["creditorIban"] as String,
                creditorName = p["creditorName"] as String, amount = BigDecimal(p["amount"].toString()),
                currency = p["currency"] as String, reference = p["reference"] as? String,
                paymentExecutionId = p["paymentExecutionId"] as String, correlationId = p["correlationId"] as? String
            )
            "ClearingBatchProcessed" -> ClearingBatchProcessed(
                batchId = p["batchId"] as String, processedAt = p["processedAt"] as String,
                totalItems = (p["totalItems"] as Number).toInt(), totalAmount = BigDecimal(p["totalAmount"].toString())
            )
            "ClearingItemSettled" -> ClearingItemSettled(
                batchId = p["batchId"] as String, itemId = p["itemId"] as String,
                settlementId = p["settlementId"] as String
            )
            "ClearingItemRejected" -> ClearingItemRejected(
                batchId = p["batchId"] as String, itemId = p["itemId"] as String,
                debtorTenant = p["debtorTenant"] as String, creditorTenant = p["creditorTenant"] as String,
                reason = p["reason"] as String, rejectedAt = Instant.parse(p["rejectedAt"] as String)
            )
            else -> throw IllegalArgumentException("Unknown event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }
}

class BatchNotFoundException(batchId: String) : RuntimeException("Batch not found: $batchId")

class TransferPolicyRejectedException(message: String) : RuntimeException(message)
