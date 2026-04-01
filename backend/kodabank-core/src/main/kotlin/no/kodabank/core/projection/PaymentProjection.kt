package no.kodabank.core.projection

import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.RecordedEvent
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PaymentProjection(
    jdbc: JdbcTemplate,
    store: KodaStoreClient
) : ProjectionWorker(jdbc, store) {

    private val log = LoggerFactory.getLogger(javaClass)

    override val projectionName = "payment-projection"
    override val categories = listOf("PaymentInitiation")

    @Scheduled(fixedDelay = 500)
    fun run() = poll()

    override fun handleEvent(event: RecordedEvent) {
        val p = event.payload
        when (event.eventType) {
            "PaymentRequested" -> {
                val tenantId = extractTenantId(event.streamId)
                jdbc.update(
                    """INSERT INTO rm_payments
                       (payment_id, tenant_id, debtor_account_id, debtor_iban, creditor_iban,
                        creditor_name, amount, currency, payment_type, status, reference, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'INTERNAL', 'REQUESTED', ?, ?)
                       ON CONFLICT (payment_id) DO NOTHING""",
                    p["paymentId"] as String,
                    tenantId,
                    p["debtorAccountId"] as String,
                    p["debtorIban"] as String,
                    p["creditorIban"] as String,
                    p["creditorName"] as String,
                    toBigDecimal(p["amount"]),
                    p["currency"] as? String ?: "NOK",
                    p["reference"] as? String,
                    toTimestamp(event.createdAt)
                )
                log.info("Projected PaymentRequested for payment {}", p["paymentId"])
            }

            "PaymentValidated" -> {
                jdbc.update(
                    "UPDATE rm_payments SET payment_type = ?, status = 'VALIDATED' WHERE payment_id = ?",
                    p["paymentType"] as String,
                    p["paymentId"] as String
                )
                log.info("Projected PaymentValidated for payment {}", p["paymentId"])
            }

            "PaymentAccepted" -> {
                jdbc.update(
                    "UPDATE rm_payments SET status = 'ACCEPTED' WHERE payment_id = ?",
                    p["paymentId"] as String
                )
                log.info("Projected PaymentAccepted for payment {}", p["paymentId"])
            }

            "PaymentRejected" -> {
                jdbc.update(
                    "UPDATE rm_payments SET status = 'REJECTED' WHERE payment_id = ?",
                    p["paymentId"] as String
                )
                log.info("Projected PaymentRejected for payment {}", p["paymentId"])
            }

            "PaymentExecutionCompleted" -> {
                val completedAt = p["completedAt"]?.let {
                    try { Instant.parse(it as String) } catch (_: Exception) { Instant.now() }
                } ?: Instant.now()
                jdbc.update(
                    "UPDATE rm_payments SET status = 'COMPLETED', completed_at = ? WHERE payment_id = ?",
                    toTimestamp(completedAt),
                    p["paymentId"] as? String
                )
                log.info("Projected PaymentExecutionCompleted for payment {}", p["paymentId"])
            }

            else -> log.debug("Ignoring event type {} in PaymentProjection", event.eventType)
        }
    }
}
