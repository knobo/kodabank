package no.kodabank.core.membership.domain

import java.time.Instant

sealed interface MembershipEvent {
    val tenantId: String
    val userId: String
}

data class MembershipRequested(
    override val tenantId: String,
    override val userId: String,
    val displayName: String,
    val email: String,
    val message: String?,
    val requestedAt: Instant
) : MembershipEvent

data class MembershipApproved(
    override val tenantId: String,
    override val userId: String,
    val approvedBy: String,
    val partyId: String,
    val approvedAt: Instant
) : MembershipEvent

data class MembershipRejected(
    override val tenantId: String,
    override val userId: String,
    val rejectedBy: String,
    val reason: String,
    val rejectedAt: Instant
) : MembershipEvent

data class MembershipRevoked(
    override val tenantId: String,
    override val userId: String,
    val revokedBy: String,
    val reason: String,
    val revokedAt: Instant
) : MembershipEvent
