package com.journey.api_rest_v1.security;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.journey.api_rest_v1.security.RateLimiter.Decision;
import com.journey.api_rest_v1.security.RateLimiter.Rule;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limits requests per client IP. Runs before Spring Security, so floods are refused before any token is
 * checked or password hashed. Sensitive auth endpoints get tight limits; everything else under /api shares a
 * generous one that normal use never reaches. Refused requests get 429 with a Retry-After header.
 *
 * <p>The client IP is the connection's address. Behind a reverse proxy, set
 * {@code server.forward-headers-strategy=native} (and make sure the proxy overwrites X-Forwarded-For), or every
 * user shares the proxy's address.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** A rule for one endpoint (method + exact path). */
    private record EndpointRule(HttpMethod method, String path, Rule rule) {
        boolean matches(HttpServletRequest request) {
            // A path ending in "/**" matches everything under it (e.g. join codes).
            return method.matches(request.getMethod()) && (path.endsWith("/**")
                    ? request.getRequestURI().startsWith(path.substring(0, path.length() - 2))
                    : path.equals(request.getRequestURI()));
        }
    }

    static final Rule GENERAL = new Rule("api", 600, Duration.ofMinutes(1));

    private static final List<EndpointRule> ENDPOINT_RULES = List.of(
        new EndpointRule(HttpMethod.POST, "/api/v1/auth/login", new Rule("login", 10, Duration.ofMinutes(1))),
        new EndpointRule(HttpMethod.POST, "/api/v1/auth/register", new Rule("register", 5, Duration.ofHours(1))),
        new EndpointRule(HttpMethod.POST, "/api/v1/auth/password/forgot", new Rule("forgot", 5, Duration.ofMinutes(15))),
        new EndpointRule(HttpMethod.POST, "/api/v1/auth/password/reset", new Rule("reset", 10, Duration.ofMinutes(15))),
        new EndpointRule(HttpMethod.POST, "/api/v1/auth/password", new Rule("change-password", 10, Duration.ofMinutes(15))),
        new EndpointRule(HttpMethod.POST, "/api/v1/auth/refresh", new Rule("refresh", 60, Duration.ofMinutes(1))),
        // Join links: slows down guessing codes.
        new EndpointRule(HttpMethod.GET, "/api/v1/campaigns/join/**", new Rule("join-preview", 30, Duration.ofMinutes(1))),
        new EndpointRule(HttpMethod.POST, "/api/v1/campaigns/join/**", new Rule("join", 10, Duration.ofMinutes(1))));

    private final RateLimiter limiter;
    private final boolean enabled;

    /** Creates the filter; {@code journey.rate-limit.enabled=false} turns it off (e.g. for load tests). */
    public RateLimitFilter(RateLimiter limiter, @Value("${journey.rate-limit.enabled:true}") boolean enabled) {
        this.limiter = limiter;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        for (EndpointRule endpoint : ENDPOINT_RULES) {
            if (endpoint.matches(request)) {
                Decision decision = limiter.tryConsume(endpoint.rule(), ip);
                if (!decision.allowed()) {
                    reject(response, request, decision);
                    return;
                }
                break;
            }
        }
        Decision general = limiter.tryConsume(GENERAL, ip);
        if (!general.allowed()) {
            reject(response, request, general);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Writes a 429 problem-detail response, like the API's other errors. */
    static void reject(HttpServletResponse response, HttpServletRequest request, Decision decision) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Too many attempts. Please wait %d seconds and try again.","instance":"%s"}"""
                .formatted(decision.retryAfterSeconds(), request.getRequestURI().replace("\"", "")));
    }
}
