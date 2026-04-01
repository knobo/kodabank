package no.kodabank.bff.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseCookie
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

data class SessionData(
    val sessionId: String,
    val accessToken: String,
    val refreshToken: String?,
    val partyId: String,
    val tenantId: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val sub: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Component
class SessionManager(private val jdbc: JdbcTemplate) {

    companion object {
        const val SESSION_COOKIE_NAME = "KODABANK_SESSION"
        private const val SESSION_COOKIE_MAX_AGE = 28800L // 8 hours
        private const val SESSION_TTL_MS = SESSION_COOKIE_MAX_AGE * 1000
    }

    fun createSession(
        accessToken: String,
        refreshToken: String?,
        partyId: String,
        tenantId: String,
        username: String,
        firstName: String,
        lastName: String,
        sub: String = "",
        response: HttpServletResponse
    ): SessionData {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = SessionData(
            sessionId = sessionId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            partyId = partyId,
            tenantId = tenantId,
            username = username,
            firstName = firstName,
            lastName = lastName,
            sub = sub,
            createdAt = now
        )
        jdbc.update(
            """INSERT INTO bff_sessions
               (session_id, access_token, refresh_token, party_id, tenant_id, username, first_name, last_name, sub, created_at, expires_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT (session_id) DO UPDATE SET
               access_token = EXCLUDED.access_token,
               refresh_token = EXCLUDED.refresh_token,
               expires_at = EXCLUDED.expires_at""",
            sessionId, accessToken, refreshToken, partyId, tenantId, username, firstName, lastName,
            sub, now, now + SESSION_TTL_MS
        )
        setSessionCookie(sessionId, response)
        return session
    }

    fun getSession(request: HttpServletRequest): SessionData? {
        val sessionId = getSessionIdFromCookie(request) ?: return null
        val now = System.currentTimeMillis()
        return jdbc.query(
            "SELECT * FROM bff_sessions WHERE session_id = ? AND expires_at > ?",
            { rs, _ ->
                SessionData(
                    sessionId = rs.getString("session_id"),
                    accessToken = rs.getString("access_token"),
                    refreshToken = rs.getString("refresh_token"),
                    partyId = rs.getString("party_id"),
                    tenantId = rs.getString("tenant_id"),
                    username = rs.getString("username"),
                    firstName = rs.getString("first_name"),
                    lastName = rs.getString("last_name"),
                    sub = rs.getString("sub") ?: "",
                    createdAt = rs.getLong("created_at")
                )
            },
            sessionId, now
        ).firstOrNull()
    }

    fun getSessionById(sessionId: String): SessionData? {
        val now = System.currentTimeMillis()
        return jdbc.query(
            "SELECT * FROM bff_sessions WHERE session_id = ? AND expires_at > ?",
            { rs, _ ->
                SessionData(
                    sessionId = rs.getString("session_id"),
                    accessToken = rs.getString("access_token"),
                    refreshToken = rs.getString("refresh_token"),
                    partyId = rs.getString("party_id"),
                    tenantId = rs.getString("tenant_id"),
                    username = rs.getString("username"),
                    firstName = rs.getString("first_name"),
                    lastName = rs.getString("last_name"),
                    sub = rs.getString("sub") ?: "",
                    createdAt = rs.getLong("created_at")
                )
            },
            sessionId, now
        ).firstOrNull()
    }

    fun destroySession(request: HttpServletRequest, response: HttpServletResponse) {
        val sessionId = getSessionIdFromCookie(request)
        if (sessionId != null) {
            jdbc.update("DELETE FROM bff_sessions WHERE session_id = ?", sessionId)
        }
        clearSessionCookie(response)
    }

    private fun getSessionIdFromCookie(request: HttpServletRequest): String? {
        return request.cookies?.firstOrNull { it.name == SESSION_COOKIE_NAME }?.value
    }

    private fun setSessionCookie(sessionId: String, response: HttpServletResponse) {
        val cookie = ResponseCookie.from(SESSION_COOKIE_NAME, sessionId)
            .httpOnly(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(SESSION_COOKIE_MAX_AGE)
            .build()
        response.addHeader("Set-Cookie", cookie.toString())
    }

    private fun clearSessionCookie(response: HttpServletResponse) {
        val cookie = ResponseCookie.from(SESSION_COOKIE_NAME, "")
            .httpOnly(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build()
        response.addHeader("Set-Cookie", cookie.toString())
    }
}
