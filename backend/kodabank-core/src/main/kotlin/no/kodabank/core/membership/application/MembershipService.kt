package no.kodabank.core.membership.application

import no.kodabank.core.account.application.CurrentAccountService
import no.kodabank.core.membership.domain.*
import no.kodabank.core.party.application.PartyService
import no.kodabank.shared.client.TenantAwareClient
import no.kodabank.shared.domain.Iban
import no.kodabank.shared.domain.TenantId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class MembershipService(
    private val store: TenantAwareClient,
    private val jdbc: JdbcTemplate,
    private val partyService: PartyService,
    private val currentAccountService: CurrentAccountService
) {
    companion object {
        private const val CATEGORY = "BankMembership"
    }

    fun requestMembership(
        tenantId: String,
        userId: String,
        displayName: String,
        email: String,
        message: String? = null,
        correlationId: String? = null
    ): MembershipState {
        val tid = TenantId(tenantId)
        val entityId = userId

        // Check if membership already exists
        val existing = store.readStream(CATEGORY, tid, entityId)
        require(existing.events.isEmpty()) { "Membership already requested for user $userId in tenant $tenantId" }

        val now = Instant.now()
        val requestedEvent = MembershipRequested(
            tenantId = tenantId,
            userId = userId,
            displayName = displayName,
            email = email,
            message = message,
            requestedAt = now
        )

        val metadata = buildMetadata(correlationId, tid)
        val events = mutableListOf(MembershipEventMapper.toRequest(requestedEvent, metadata))

        // Look up the bank's access policy
        val accessPolicy = lookupAccessPolicy(tenantId)

        if (accessPolicy == "AUTO_APPROVE") {
            // Create party and default account
            val partyId = UUID.randomUUID().toString()
            val approvedEvent = MembershipApproved(
                tenantId = tenantId,
                userId = userId,
                approvedBy = "SYSTEM",
                partyId = partyId,
                approvedAt = now
            )
            events.add(MembershipEventMapper.toRequest(approvedEvent, metadata))
        }

        store.append(CATEGORY, tid, entityId, null, events)

        val state = loadMembership(tid, entityId)

        // If auto-approved, create the party and default current account
        if (accessPolicy == "AUTO_APPROVE" && state.status == MembershipStatus.APPROVED) {
            createPartyAndDefaultAccount(tid, state, correlationId)
        }

        return loadMembership(tid, entityId)
    }

    fun approveMembership(
        tenantId: String,
        userId: String,
        approvedBy: String,
        correlationId: String? = null
    ): MembershipState {
        val tid = TenantId(tenantId)
        val entityId = userId
        val current = loadMembership(tid, entityId)
        require(current.status == MembershipStatus.PENDING) {
            "Cannot approve membership with status ${current.status}"
        }

        val partyId = UUID.randomUUID().toString()
        val event = MembershipApproved(
            tenantId = tenantId,
            userId = userId,
            approvedBy = approvedBy,
            partyId = partyId,
            approvedAt = Instant.now()
        )
        val metadata = buildMetadata(correlationId, tid)
        store.append(CATEGORY, tid, entityId, current.version, listOf(MembershipEventMapper.toRequest(event, metadata)))

        val state = loadMembership(tid, entityId)
        createPartyAndDefaultAccount(tid, state, correlationId)

        return loadMembership(tid, entityId)
    }

    fun rejectMembership(
        tenantId: String,
        userId: String,
        rejectedBy: String,
        reason: String,
        correlationId: String? = null
    ): MembershipState {
        val tid = TenantId(tenantId)
        val entityId = userId
        val current = loadMembership(tid, entityId)
        require(current.status == MembershipStatus.PENDING) {
            "Cannot reject membership with status ${current.status}"
        }

        val event = MembershipRejected(
            tenantId = tenantId,
            userId = userId,
            rejectedBy = rejectedBy,
            reason = reason,
            rejectedAt = Instant.now()
        )
        val metadata = buildMetadata(correlationId, tid)
        store.append(CATEGORY, tid, entityId, current.version, listOf(MembershipEventMapper.toRequest(event, metadata)))
        return loadMembership(tid, entityId)
    }

    fun revokeMembership(
        tenantId: String,
        userId: String,
        revokedBy: String,
        reason: String,
        correlationId: String? = null
    ): MembershipState {
        val tid = TenantId(tenantId)
        val entityId = userId
        val current = loadMembership(tid, entityId)
        require(current.status == MembershipStatus.APPROVED) {
            "Cannot revoke membership with status ${current.status}"
        }

        val event = MembershipRevoked(
            tenantId = tenantId,
            userId = userId,
            revokedBy = revokedBy,
            reason = reason,
            revokedAt = Instant.now()
        )
        val metadata = buildMetadata(correlationId, tid)
        store.append(CATEGORY, tid, entityId, current.version, listOf(MembershipEventMapper.toRequest(event, metadata)))
        return loadMembership(tid, entityId)
    }

    fun getMembership(tenantId: String, userId: String): MembershipState {
        val tid = TenantId(tenantId)
        val entityId = userId
        return loadMembership(tid, entityId)
    }

    private fun loadMembership(tenantId: TenantId, entityId: String): MembershipState {
        val stream = store.readStream(CATEGORY, tenantId, entityId)
        if (stream.events.isEmpty()) {
            throw MembershipNotFoundException(entityId)
        }
        val events = stream.events.map { MembershipEventMapper.fromRecorded(it) }
        return MembershipState.rebuild(events)
    }

    private fun lookupAccessPolicy(tenantId: String): String {
        val policies = jdbc.queryForList(
            "SELECT access_policy_type FROM rm_tenants WHERE tenant_id = ?",
            String::class.java,
            tenantId
        )
        return policies.firstOrNull() ?: "MANUAL"
    }

    private fun createPartyAndDefaultAccount(
        tenantId: TenantId,
        membership: MembershipState,
        correlationId: String?
    ) {
        // Create a Party for the new member
        val partyState = partyService.registerParty(
            tenantId = tenantId,
            nationalId = membership.userId, // userId serves as the identity reference
            firstName = membership.displayName.substringBefore(" ", membership.displayName),
            lastName = membership.displayName.substringAfter(" ", ""),
            email = membership.email,
            correlationId = correlationId
        )

        // Look up bank code for IBAN generation
        val bankCodes = jdbc.queryForList(
            "SELECT bank_code FROM rm_tenants WHERE tenant_id = ?",
            String::class.java,
            tenantId.value
        )
        val bankCode = bankCodes.firstOrNull() ?: "0000"

        // Generate IBAN and open default current account
        val accountSequence = (System.currentTimeMillis() % 1_000_000).toInt()
        val iban = Iban.generate(bankCode, accountSequence)

        currentAccountService.openAccount(
            tenantId = tenantId,
            partyId = partyState.partyId,
            iban = iban.value,
            currency = "NOK",
            productId = "default-current",
            accountName = "Brukskonto",
            correlationId = correlationId
        )
    }

    private fun buildMetadata(correlationId: String?, tenantId: TenantId): Map<String, Any?> =
        buildMap {
            put("tenantId", tenantId.value)
            put("sourceService", "kodabank-core")
            correlationId?.let { put("correlationId", it) }
        }
}

class MembershipNotFoundException(entityId: String) :
    RuntimeException("Membership not found: $entityId")
