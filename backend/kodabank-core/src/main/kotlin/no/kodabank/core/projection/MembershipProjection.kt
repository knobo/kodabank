package no.kodabank.core.projection

import no.kodabank.shared.client.KodaStoreClient
import no.kodabank.shared.client.RecordedEvent
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MembershipProjection(
    jdbc: JdbcTemplate,
    store: KodaStoreClient
) : ProjectionWorker(jdbc, store) {

    private val log = LoggerFactory.getLogger(javaClass)

    override val projectionName = "membership-projection"
    override val categories = listOf("BankMembership")

    @Scheduled(fixedDelay = 500)
    fun run() = poll()

    override fun handleEvent(event: RecordedEvent) {
        val p = event.payload
        when (event.eventType) {
            "MembershipRequested" -> {
                jdbc.update(
                    """INSERT INTO rm_memberships
                       (tenant_id, user_id, display_name, email, status, requested_at)
                       VALUES (?, ?, ?, ?, 'PENDING', ?)
                       ON CONFLICT (tenant_id, user_id) DO NOTHING""",
                    p["tenantId"] as String,
                    p["userId"] as String,
                    p["displayName"] as String,
                    p["email"] as? String,
                    toTimestamp(event.createdAt)
                )
                log.info("Projected MembershipRequested for user {} in tenant {}", p["userId"], p["tenantId"])
            }

            "MembershipApproved" -> {
                jdbc.update(
                    """UPDATE rm_memberships SET
                       status = 'APPROVED',
                       party_id = ?,
                       resolved_at = ?
                       WHERE tenant_id = ? AND user_id = ?""",
                    p["partyId"] as String,
                    toTimestamp(event.createdAt),
                    p["tenantId"] as String,
                    p["userId"] as String
                )
                log.info("Projected MembershipApproved for user {} in tenant {}", p["userId"], p["tenantId"])
            }

            "MembershipRejected" -> {
                jdbc.update(
                    """UPDATE rm_memberships SET
                       status = 'REJECTED',
                       resolved_at = ?
                       WHERE tenant_id = ? AND user_id = ?""",
                    toTimestamp(event.createdAt),
                    p["tenantId"] as String,
                    p["userId"] as String
                )
                log.info("Projected MembershipRejected for user {} in tenant {}", p["userId"], p["tenantId"])
            }

            "MembershipRevoked" -> {
                jdbc.update(
                    """UPDATE rm_memberships SET
                       status = 'REVOKED',
                       resolved_at = ?
                       WHERE tenant_id = ? AND user_id = ?""",
                    toTimestamp(event.createdAt),
                    p["tenantId"] as String,
                    p["userId"] as String
                )
                log.info("Projected MembershipRevoked for user {} in tenant {}", p["userId"], p["tenantId"])
            }

            else -> log.debug("Ignoring event type {} in MembershipProjection", event.eventType)
        }
    }
}
