package no.kodabank.core.membership.domain

import java.time.Instant

enum class MembershipStatus { PENDING, APPROVED, REJECTED, REVOKED }

data class MembershipState(
    val tenantId: String,
    val userId: String,
    val displayName: String,
    val email: String,
    val message: String?,
    val status: MembershipStatus,
    val partyId: String?,
    val approvedBy: String?,
    val rejectedBy: String?,
    val revokedBy: String?,
    val reason: String?,
    val requestedAt: Instant?,
    val resolvedAt: Instant?,
    val version: Int
) {
    companion object {
        val EMPTY = MembershipState(
            tenantId = "",
            userId = "",
            displayName = "",
            email = "",
            message = null,
            status = MembershipStatus.PENDING,
            partyId = null,
            approvedBy = null,
            rejectedBy = null,
            revokedBy = null,
            reason = null,
            requestedAt = null,
            resolvedAt = null,
            version = 0
        )

        fun evolve(state: MembershipState, event: MembershipEvent, version: Int): MembershipState =
            when (event) {
                is MembershipRequested -> state.copy(
                    tenantId = event.tenantId,
                    userId = event.userId,
                    displayName = event.displayName,
                    email = event.email,
                    message = event.message,
                    status = MembershipStatus.PENDING,
                    requestedAt = event.requestedAt,
                    version = version
                )
                is MembershipApproved -> state.copy(
                    approvedBy = event.approvedBy,
                    partyId = event.partyId,
                    status = MembershipStatus.APPROVED,
                    resolvedAt = event.approvedAt,
                    version = version
                )
                is MembershipRejected -> state.copy(
                    rejectedBy = event.rejectedBy,
                    reason = event.reason,
                    status = MembershipStatus.REJECTED,
                    resolvedAt = event.rejectedAt,
                    version = version
                )
                is MembershipRevoked -> state.copy(
                    revokedBy = event.revokedBy,
                    reason = event.reason,
                    status = MembershipStatus.REVOKED,
                    resolvedAt = event.revokedAt,
                    version = version
                )
            }

        fun rebuild(events: List<Pair<MembershipEvent, Int>>): MembershipState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
