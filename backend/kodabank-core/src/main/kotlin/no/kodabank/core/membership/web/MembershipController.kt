package no.kodabank.core.membership.web

import no.kodabank.core.membership.application.MembershipNotFoundException
import no.kodabank.core.membership.application.MembershipService
import no.kodabank.core.membership.domain.MembershipState
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/internal/memberships")
class MembershipController(
    private val membershipService: MembershipService,
    private val jdbc: JdbcTemplate
) {

    @PostMapping
    fun requestMembership(@RequestBody req: RequestMembershipRequest): ResponseEntity<MembershipState> {
        val state = membershipService.requestMembership(
            tenantId = req.tenantId,
            userId = req.userId,
            displayName = req.displayName,
            email = req.email,
            message = req.message
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(state)
    }

    @PostMapping("/{tenantId}/{userId}/approve")
    fun approveMembership(
        @PathVariable tenantId: String,
        @PathVariable userId: String,
        @RequestBody req: ApproveMembershipRequest
    ): MembershipState {
        return membershipService.approveMembership(
            tenantId = tenantId,
            userId = userId,
            approvedBy = req.approvedBy
        )
    }

    @PostMapping("/{tenantId}/{userId}/reject")
    fun rejectMembership(
        @PathVariable tenantId: String,
        @PathVariable userId: String,
        @RequestBody req: RejectMembershipRequest
    ): MembershipState {
        return membershipService.rejectMembership(
            tenantId = tenantId,
            userId = userId,
            rejectedBy = req.rejectedBy,
            reason = req.reason
        )
    }

    @GetMapping("/{tenantId}/{userId}")
    fun getMembership(
        @PathVariable tenantId: String,
        @PathVariable userId: String
    ): MembershipState {
        return membershipService.getMembership(tenantId, userId)
    }

    @GetMapping("/{tenantId}")
    fun listMemberships(
        @PathVariable tenantId: String
    ): List<MembershipReadModel> {
        return jdbc.query(
            """SELECT tenant_id, user_id, display_name, email, party_id, status, requested_at, resolved_at
               FROM rm_memberships WHERE tenant_id = ? ORDER BY requested_at DESC""",
            { rs, _ ->
                MembershipReadModel(
                    tenantId = rs.getString("tenant_id"),
                    userId = rs.getString("user_id"),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    partyId = rs.getString("party_id"),
                    status = rs.getString("status"),
                    requestedAt = rs.getString("requested_at"),
                    resolvedAt = rs.getString("resolved_at")
                )
            },
            tenantId
        )
    }

    @ExceptionHandler(MembershipNotFoundException::class)
    fun handleNotFound(e: MembershipNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("code" to "MEMBERSHIP_NOT_FOUND", "message" to (e.message ?: "")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("code" to "BAD_REQUEST", "message" to (e.message ?: "")))
}

data class RequestMembershipRequest(
    val tenantId: String,
    val userId: String,
    val displayName: String,
    val email: String,
    val message: String? = null
)

data class ApproveMembershipRequest(
    val approvedBy: String
)

data class RejectMembershipRequest(
    val rejectedBy: String,
    val reason: String
)

data class MembershipReadModel(
    val tenantId: String,
    val userId: String,
    val displayName: String?,
    val email: String?,
    val partyId: String?,
    val status: String,
    val requestedAt: String?,
    val resolvedAt: String?
)
