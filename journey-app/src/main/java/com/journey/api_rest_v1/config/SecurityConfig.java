package com.journey.api_rest_v1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what.
 *
 * <ul>
 *   <li>Signing up, in, out, and refreshing: anyone.</li>
 *   <li>Reading the compendium, rules, and images (GET, and the POST filter-by-category searches): anyone.</li>
 *   <li>Creating, editing, or deleting compendium content: admins only (JWT scope {@code admin}).</li>
 *   <li>Everything else (the current user, and later characters): any signed-in user.</li>
 * </ul>
 *
 * <p>Requests are stateless: no server session or CSRF token; access JWTs come in the
 * {@code Authorization: Bearer} header. The refresh token travels in an HttpOnly, SameSite=Strict cookie
 * scoped to {@code /api/v1/auth}, so page scripts cannot read it and other sites cannot send it.</p>
 */
@Configuration
public class SecurityConfig {

    /** BCrypt cost used for new password hashes (matches the seeded admin hash). */
    private static final int BCRYPT_STRENGTH = 12;

    /** Official content that everyone may read and only admins may change. */
    private static final String[] CONTENT = {
        "/api/v1/traits/**", "/api/v1/powers/**", "/api/v1/jobs/**", "/api/v1/races/**",
        "/api/v1/items/**", "/api/v1/creatures/**", "/api/v1/status-conditions/**",
        "/api/v1/rules/**", "/api/v1/assets/**",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/health/**", "/actuator/info",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                        "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, CONTENT).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/*/filter-by-category").permitAll()
                .requestMatchers(CONTENT).hasAuthority("SCOPE_admin")
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
