package com.journey.api_rest_v1.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.config.AuthProperties;
import com.journey.api_rest_v1.dto.auth.AuthResponseDto;
import com.journey.api_rest_v1.dto.auth.ChangePasswordRequest;
import com.journey.api_rest_v1.dto.auth.ForgotPasswordRequest;
import com.journey.api_rest_v1.dto.auth.LoginRequest;
import com.journey.api_rest_v1.dto.auth.RegisterRequest;
import com.journey.api_rest_v1.dto.auth.ResetPasswordRequest;
import com.journey.api_rest_v1.dto.auth.UserDto;
import com.journey.api_rest_v1.service.AuthService;
import com.journey.api_rest_v1.service.AuthService.ClientInfo;
import com.journey.api_rest_v1.service.AuthService.Session;
import com.journey.api_rest_v1.service.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Sign-up, sign-in, and sessions under {@code /api/v1/auth}.
 *
 * <p>Sign-up, sign-in, and refresh return an access JWT in the body and set the refresh token as an HttpOnly,
 * SameSite=Strict cookie limited to {@code /api/v1/auth}. The browser sends it to refresh and logout only.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "journey_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final CurrentUser currentUser;
    private final AuthProperties properties;

    /** Wires the auth service. */
    public AuthController(AuthService authService, CurrentUser currentUser, AuthProperties properties) {
        this.authService = authService;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    /** Creates an account with a username and password and signs it in. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return withSession(HttpStatus.CREATED, authService.register(request, client(http)));
    }

    /** Signs in with a username (or email) and password. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withSession(HttpStatus.OK, authService.login(request, client(http)));
    }

    /** Exchanges the refresh cookie for a new access token (and a new refresh cookie). */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest http) {
        return withSession(HttpStatus.OK, authService.refresh(refreshToken, client(http)));
    }

    /** Ends the session and clears the refresh cookie. Always succeeds. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    /** Returns the signed-in user. */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me() {
        return ResponseEntity.ok(authService.getUser(currentUser.requireId()));
    }

    /**
     * Changes the signed-in user's password. Signs out every other session and returns a fresh one for this
     * device (new access token and refresh cookie).
     */
    @PostMapping("/password")
    public ResponseEntity<AuthResponseDto> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest http) {
        return withSession(HttpStatus.OK, authService.changePassword(currentUser.requireId(), request, client(http)));
    }

    /**
     * Emails a password reset link if an account uses that email. Always returns 202 Accepted, so the
     * response never reveals whether an account exists.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest http) {
        authService.requestPasswordReset(request, client(http));
        return ResponseEntity.accepted().build();
    }

    /** Sets a new password with the token from a reset email; every session is signed out. */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponseDto> withSession(HttpStatus status, Session session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(session.refreshToken(), session.refreshTokenLifetime()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(session.response());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private static ClientInfo client(HttpServletRequest http) {
        return new ClientInfo(http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
    }
}
