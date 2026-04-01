package no.kodabank.core.projection

import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.RecordedEvent
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class AccountProjection(
    jdbc: JdbcTemplate,
    store: KodaStoreClient
) : ProjectionWorker(jdbc, store) {

    private val log = LoggerFactory.getLogger(javaClass)

    override val projectionName = "account-projection"
    override val categories = listOf("CurrentAccount", "SavingsAccount")

    @Scheduled(fixedDelay = 500)
    fun run() = poll()

    override fun handleEvent(event: RecordedEvent) {
        val p = event.payload
        when (event.eventType) {
            "CurrentAccountOpened" -> {
                val tenantId = extractTenantId(event.streamId)
                jdbc.update(
                    """INSERT INTO rm_accounts
                       (account_id, tenant_id, party_id, iban, account_name, account_type,
                        product_id, balance, currency, status, created_at)
                       VALUES (?, ?, ?, ?, ?, 'CURRENT', ?, 0, ?, 'ACTIVE', ?)
                       ON CONFLICT (account_id) DO NOTHING""",
                    p["accountId"] as String,
                    tenantId,
                    p["partyId"] as String,
                    p["iban"] as String,
                    p["accountName"] as String,
                    p["productId"] as? String,
                    p["currency"] as? String ?: "NOK",
                    toTimestamp(event.createdAt)
                )
                log.info("Projected CurrentAccountOpened for account {}", p["accountId"])
            }

            "SavingsAccountOpened" -> {
                val tenantId = extractTenantId(event.streamId)
                jdbc.update(
                    """INSERT INTO rm_accounts
                       (account_id, tenant_id, party_id, iban, account_name, account_type,
                        product_id, balance, currency, status, created_at)
                       VALUES (?, ?, ?, ?, ?, 'SAVINGS', ?, 0, ?, 'ACTIVE', ?)
                       ON CONFLICT (account_id) DO NOTHING""",
                    p["accountId"] as String,
                    tenantId,
                    p["partyId"] as String,
                    p["iban"] as String,
                    p["accountName"] as String,
                    p["productId"] as? String,
                    p["currency"] as? String ?: "NOK",
                    toTimestamp(event.createdAt)
                )
                log.info("Projected SavingsAccountOpened for account {}", p["accountId"])
            }

            "FundsDeposited" -> {
                val accountId = p["accountId"] as String
                val balanceAfter = toBigDecimal(p["balanceAfter"])
                jdbc.update(
                    "UPDATE rm_accounts SET balance = ? WHERE account_id = ?",
                    balanceAfter, accountId
                )
                insertTransaction(event, accountId, "FundsDeposited")
                log.info("Projected FundsDeposited for account {}", accountId)
            }

            "FundsWithdrawn" -> {
                val accountId = p["accountId"] as String
                val balanceAfter = toBigDecimal(p["balanceAfter"])
                jdbc.update(
                    "UPDATE rm_accounts SET balance = ? WHERE account_id = ?",
                    balanceAfter, accountId
                )
                insertTransaction(event, accountId, "FundsWithdrawn")
                log.info("Projected FundsWithdrawn for account {}", accountId)
            }

            "SavingsDeposited" -> {
                val accountId = p["accountId"] as String
                val balanceAfter = toBigDecimal(p["balanceAfter"])
                jdbc.update(
                    "UPDATE rm_accounts SET balance = ? WHERE account_id = ?",
                    balanceAfter, accountId
                )
                insertTransaction(event, accountId, "SavingsDeposited")
                log.info("Projected SavingsDeposited for account {}", accountId)
            }

            "SavingsWithdrawn" -> {
                val accountId = p["accountId"] as String
                val balanceAfter = toBigDecimal(p["balanceAfter"])
                jdbc.update(
                    "UPDATE rm_accounts SET balance = ? WHERE account_id = ?",
                    balanceAfter, accountId
                )
                insertTransaction(event, accountId, "SavingsWithdrawn")
                log.info("Projected SavingsWithdrawn for account {}", accountId)
            }

            else -> log.debug("Ignoring event type {} in AccountProjection", event.eventType)
        }
    }

    private fun insertTransaction(event: RecordedEvent, accountId: String, eventType: String) {
        val p = event.payload
        val tenantId = extractTenantId(event.streamId)
        val txnId = "txn-${event.globalOffset}"
        jdbc.update(
            """INSERT INTO rm_transactions
               (transaction_id, tenant_id, account_id, event_type, amount, balance_after,
                currency, counterparty_name, counterparty_iban, reference, remittance_info, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT (transaction_id) DO NOTHING""",
            txnId,
            tenantId,
            accountId,
            eventType,
            toBigDecimal(p["amount"]),
            toBigDecimal(p["balanceAfter"]),
            p["currency"] as? String ?: "NOK",
            p["counterpartyName"] as? String,
            p["counterpartyIban"] as? String,
            p["reference"] as? String,
            p["remittanceInfo"] as? String,
            toTimestamp(event.createdAt)
        )
    }
}
