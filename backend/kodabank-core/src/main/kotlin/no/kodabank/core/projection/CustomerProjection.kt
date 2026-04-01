package no.kodabank.core.projection

import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.RecordedEvent
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CustomerProjection(
    jdbc: JdbcTemplate,
    store: KodaStoreClient
) : ProjectionWorker(jdbc, store) {

    private val log = LoggerFactory.getLogger(javaClass)

    override val projectionName = "customer-projection"
    override val categories = listOf("Party")

    @Scheduled(fixedDelay = 500)
    fun run() = poll()

    override fun handleEvent(event: RecordedEvent) {
        val p = event.payload
        when (event.eventType) {
            "PartyRegistered" -> {
                val tenantId = extractTenantId(event.streamId)
                jdbc.update(
                    """INSERT INTO rm_customers
                       (party_id, tenant_id, first_name, last_name, email, phone, status, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                       ON CONFLICT (party_id) DO NOTHING""",
                    p["partyId"] as String,
                    tenantId,
                    p["firstName"] as String,
                    p["lastName"] as String,
                    p["email"] as? String,
                    p["phone"] as? String,
                    toTimestamp(event.createdAt)
                )
                log.info("Projected PartyRegistered for party {}", p["partyId"])
            }

            "PartyContactUpdated" -> {
                jdbc.update(
                    """UPDATE rm_customers SET
                       email = COALESCE(?, email),
                       phone = COALESCE(?, phone)
                       WHERE party_id = ?""",
                    p["email"] as? String,
                    p["phone"] as? String,
                    p["partyId"] as String
                )
                log.info("Projected PartyContactUpdated for party {}", p["partyId"])
            }

            "PartyStatusChanged" -> {
                jdbc.update(
                    "UPDATE rm_customers SET status = ? WHERE party_id = ?",
                    p["newStatus"] as String,
                    p["partyId"] as String
                )
                log.info("Projected PartyStatusChanged for party {}", p["partyId"])
            }

            else -> log.debug("Ignoring event type {} in CustomerProjection", event.eventType)
        }
    }
}
