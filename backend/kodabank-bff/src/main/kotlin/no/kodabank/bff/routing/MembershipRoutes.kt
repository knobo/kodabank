package no.kodabank.bff.routing

import jakarta.servlet.http.HttpServletRequest
import no.kodabank.bff.auth.SessionManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

@RestController
@RequestMapping("/api/v1/{tenant}")
class MembershipRoutes(
    private val sessionManager: SessionManager,
    @Value("\${kodabank.core-url}") private val coreUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.builder().baseUrl(coreUrl).build()

    /** Public: request membership (no session required for joining a bank) */
    @PostMapping("/memberships")
    fun requestMembership(
        @PathVariable tenant: String,
        @RequestBody body: Map<String, Any>,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = sessionManager.getSession(request)
        val userId = session?.sub?.takeIf { it.isNotBlank() }
            ?: (body["userId"] as? String)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "not_authenticated"))

        val payload = mapOf(
            "tenantId" to tenant,
            "userId" to userId,
            "displayName" to (body["displayName"] as? String ?: ""),
            "email" to (body["email"] as? String ?: ""),
            "message" to (body["message"] as? String)
        )

        return try {
            val result = restClient.post()
                .uri("/api/internal/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(Map::class.java)
            ResponseEntity.status(result.statusCode).body(toMembershipResponse(result.body))
        } catch (e: HttpClientErrorException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    /** Get current user's membership status */
    @GetMapping("/membership")
    fun getMyMembership(
        @PathVariable tenant: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = sessionManager.getSession(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()
        val sub = session.sub.takeIf { it.isNotBlank() }
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "no_user_id"))

        return try {
            val result = restClient.get()
                .uri("/api/internal/memberships/{tenant}/{userId}", tenant, sub)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(Map::class.java)
            ResponseEntity.ok(toMembershipResponse(result.body))
        } catch (e: HttpClientErrorException.NotFound) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "not_found"))
        } catch (e: HttpClientErrorException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    /** Admin: list all memberships */
    @GetMapping("/admin/memberships")
    fun listMemberships(
        @PathVariable tenant: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        sessionManager.getSession(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        return try {
            val result = restClient.get()
                .uri("/api/internal/memberships/{tenant}", tenant)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(List::class.java)
            val memberships = (result.body as? List<*>)?.mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                (item as? Map<String, Any?>)?.let { toMembershipResponse(it) }
            } ?: emptyList<Any>()
            ResponseEntity.ok(memberships)
        } catch (e: HttpClientErrorException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    /** Admin: approve a membership */
    @PostMapping("/admin/memberships/{userId}/approve")
    fun approveMembership(
        @PathVariable tenant: String,
        @PathVariable userId: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = sessionManager.getSession(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        val payload = mapOf("approvedBy" to (session.sub.takeIf { it.isNotBlank() } ?: session.username))
        return try {
            val result = restClient.post()
                .uri("/api/internal/memberships/{tenant}/{userId}/approve", tenant, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(Map::class.java)
            ResponseEntity.ok(toMembershipResponse(result.body))
        } catch (e: HttpClientErrorException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    /** Admin: reject a membership */
    @PostMapping("/admin/memberships/{userId}/reject")
    fun rejectMembership(
        @PathVariable tenant: String,
        @PathVariable userId: String,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val session = sessionManager.getSession(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        val payload = mapOf(
            "rejectedBy" to (session.sub.takeIf { it.isNotBlank() } ?: session.username),
            "reason" to ""
        )
        return try {
            val result = restClient.post()
                .uri("/api/internal/memberships/{tenant}/{userId}/reject", tenant, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(Map::class.java)
            ResponseEntity.ok(toMembershipResponse(result.body))
        } catch (e: HttpClientErrorException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun toMembershipResponse(body: Map<*, *>?): Map<String, Any?> {
        if (body == null) return emptyMap()
        return mapOf(
            "userId" to body["userId"],
            "displayName" to body["displayName"],
            "email" to body["email"],
            "status" to body["status"],
            "message" to body["message"],
            "createdAt" to (body["requestedAt"] ?: body["createdAt"] ?: "")
        )
    }
}
