package no.kodabank.shared.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Shared security configuration for backend services using OAuth2 resource server (JWT).
 * Individual services can override this by providing their own SecurityFilterChain bean.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/internal/**").permitAll() // Service-to-service
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt {} }
            .build()
    }
}
