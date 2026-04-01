package no.kodabank.clearing.domain

import java.math.BigDecimal

data class ClearingItem(
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
    val correlationId: String?,
    val settled: Boolean = false,
    val settlementId: String? = null
)

data class ClearingBatchState(
    val batchId: String,
    val items: List<ClearingItem>,
    val processed: Boolean,
    val processedAt: String?,
    val version: Int
) {
    companion object {
        val EMPTY = ClearingBatchState("", emptyList(), false, null, 0)

        fun evolve(state: ClearingBatchState, event: ClearingBatchEvent, version: Int): ClearingBatchState =
            when (event) {
                is ClearingBatchCreated -> state.copy(
                    batchId = event.batchId,
                    version = version
                )
                is ClearingItemAdded -> state.copy(
                    items = state.items + ClearingItem(
                        itemId = event.itemId,
                        debtorTenant = event.debtorTenant,
                        debtorIban = event.debtorIban,
                        creditorTenant = event.creditorTenant,
                        creditorIban = event.creditorIban,
                        creditorName = event.creditorName,
                        amount = event.amount,
                        currency = event.currency,
                        reference = event.reference,
                        paymentExecutionId = event.paymentExecutionId,
                        correlationId = event.correlationId
                    ),
                    version = version
                )
                is ClearingBatchProcessed -> state.copy(
                    processed = true,
                    processedAt = event.processedAt,
                    version = version
                )
                is ClearingItemSettled -> state.copy(
                    items = state.items.map { item ->
                        if (item.itemId == event.itemId)
                            item.copy(settled = true, settlementId = event.settlementId)
                        else item
                    },
                    version = version
                )
                is ClearingItemRejected -> state.copy(
                    items = state.items.filter { it.itemId != event.itemId },
                    version = version
                )
            }

        fun rebuild(events: List<Pair<ClearingBatchEvent, Int>>): ClearingBatchState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
