package no.kodabank.bff.config

import no.kodabank.bff.auth.SessionManager
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * BFF-specific security configuration.
 *
 * The BFF acts as a session gateway: auth endpoints (BankID login/logout) are public,
 * tenant and branding endpoints are public, and all other routes require a valid
 * BFF-managed session cookie.
 *
 * This overrides the shared SecurityConfig which is designed for backend resource servers.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val sessionManager: SessionManager
) {

    @Bean
    @Primary
    fun bffSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Health and info endpoints
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    // Auth endpoints (login, logout, session check)
                    .requestMatchers("/api/v1/*/auth/**").permitAll()
                    // OIDC callback and platform login (tenant-less paths)
                    .requestMatchers("/api/v1/auth/callback").permitAll()
                    .requestMatchers("/api/v1/auth/login").permitAll()
                    .requestMatchers("/api/v1/auth/me").permitAll()
                    .requestMatchers("/api/v1/auth/my-banks").permitAll()
                    .requestMatchers("/go/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/*/memberships").permitAll()
                    // Tenant and branding endpoints (public read, but register requires auth)
                    .requestMatchers("/api/v1/*/branding/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/tenants").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/tenants/*").permitAll()
                    // Preflight requests
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // All other requests require a valid session
                    .anyRequest().authenticated()
            }
            // Disable the default OAuth2 resource server JWT validation for the BFF;
            // instead we use our own session-based filter
            .addFilterBefore(BffSessionFilter(sessionManager), UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf(
            "http://localhost:3000",
            "http://localhost:3001",
            "http://localhost:3100",
            "http://localhost:3101",
            "http://localhost:3200",
            "http://localhost:5173"
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}

/**
 * Filter that validates BFF session cookies on protected routes.
 * Public routes are handled by permitAll() in the security chain and skip this filter.
 */
class BffSessionFilter(
    private val sessionManager: SessionManager
) : OncePerRequestFilter() {

    private val publicPathPatterns = listOf(
        Regex("^/actuator/(health|info)$"),
        Regex("^/api/v1/[^/]+/auth/.*$"),
        Regex("^/api/v1/auth/callback$"),
        Regex("^/api/v1/auth/login$"),
        Regex("^/api/v1/auth/me$"),
        Regex("^/api/v1/auth/my-banks$"),
        Regex("^/go/.*$"),
        Regex("^/api/v1/[^/]+/memberships$"),
        Regex("^/api/v1/[^/]+/branding(/.*)?$"),
        Regex("^/api/v1/tenants/?$")
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI

        // Let public paths pass through without session validation
        if (request.method == "OPTIONS" || publicPathPatterns.any { it.matches(path) }) {
            filterChain.doFilter(request, response)
            return
        }

        val session = sessionManager.getSession(request)
        if (session == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error":"no_session","message":"Authentication required"}""")
            return
        }

        // Store session data as request attributes for downstream use
        request.setAttribute("kodabank.session", session)
        request.setAttribute("kodabank.accessToken", session.accessToken)
        request.setAttribute("kodabank.tenantId", session.tenantId)
        request.setAttribute("kodabank.partyId", session.partyId)

        // Set Spring Security authentication so that .anyRequest().authenticated() passes
        val authentication = UsernamePasswordAuthenticationToken(
            session.username,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(request, response)
    }
}
