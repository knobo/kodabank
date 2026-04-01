package no.kodabank.core.membership.domain

import no.kodabank.shared.client.NewEventRequest
import no.kodabank.shared.client.RecordedEvent
import java.time.Instant

object MembershipEventMapper {

    fun toRequest(event: MembershipEvent, metadata: Map<String, Any?> = emptyMap()): NewEventRequest {
        val (eventType, payload) = when (event) {
            is MembershipRequested -> "MembershipRequested" to mapOf<String, Any?>(
                "tenantId" to event.tenantId,
                "userId" to event.userId,
                "displayName" to event.displayName,
                "email" to event.email,
                "message" to event.message,
                "requestedAt" to event.requestedAt.toString()
            )
            is MembershipApproved -> "MembershipApproved" to mapOf<String, Any?>(
                "tenantId" to event.tenantId,
                "userId" to event.userId,
                "approvedBy" to event.approvedBy,
                "partyId" to event.partyId,
                "approvedAt" to event.approvedAt.toString()
            )
            is MembershipRejected -> "MembershipRejected" to mapOf<String, Any?>(
                "tenantId" to event.tenantId,
                "userId" to event.userId,
                "rejectedBy" to event.rejectedBy,
                "reason" to event.reason,
                "rejectedAt" to event.rejectedAt.toString()
            )
            is MembershipRevoked -> "MembershipRevoked" to mapOf<String, Any?>(
                "tenantId" to event.tenantId,
                "userId" to event.userId,
                "revokedBy" to event.revokedBy,
                "reason" to event.reason,
                "revokedAt" to event.revokedAt.toString()
            )
        }
        return NewEventRequest(eventType, payload, metadata)
    }

    fun fromRecorded(recorded: RecordedEvent): Pair<MembershipEvent, Int> {
        val p = recorded.payload
        val event: MembershipEvent = when (recorded.eventType) {
            "MembershipRequested" -> MembershipRequested(
                tenantId = p["tenantId"] as String,
                userId = p["userId"] as String,
                displayName = p["displayName"] as String,
                email = p["email"] as String,
                message = p["message"] as? String,
                requestedAt = Instant.parse(p["requestedAt"] as String)
            )
            "MembershipApproved" -> MembershipApproved(
                tenantId = p["tenantId"] as String,
                userId = p["userId"] as String,
                approvedBy = p["approvedBy"] as String,
                partyId = p["partyId"] as String,
                approvedAt = Instant.parse(p["approvedAt"] as String)
            )
            "MembershipRejected" -> MembershipRejected(
                tenantId = p["tenantId"] as String,
                userId = p["userId"] as String,
                rejectedBy = p["rejectedBy"] as String,
                reason = p["reason"] as String,
                rejectedAt = Instant.parse(p["rejectedAt"] as String)
            )
            "MembershipRevoked" -> MembershipRevoked(
                tenantId = p["tenantId"] as String,
                userId = p["userId"] as String,
                revokedBy = p["revokedBy"] as String,
                reason = p["reason"] as String,
                revokedAt = Instant.parse(p["revokedAt"] as String)
            )
            else -> throw IllegalArgumentException("Unknown membership event type: ${recorded.eventType}")
        }
        return event to recorded.streamVersion
    }
}
