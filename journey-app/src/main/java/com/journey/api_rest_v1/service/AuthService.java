package com.journey.api_rest_v1.service;

import java.time.Duration;
import java.util.UUID;

import com.journey.api_rest_v1.dto.auth.AuthResponseDto;
import com.journey.api_rest_v1.dto.auth.ChangePasswordRequest;
import com.journey.api_rest_v1.dto.auth.ForgotPasswordRequest;
import com.journey.api_rest_v1.dto.auth.LoginRequest;
import com.journey.api_rest_v1.dto.auth.RegisterRequest;
import com.journey.api_rest_v1.dto.auth.ResetPasswordRequest;
import com.journey.api_rest_v1.dto.auth.UserDto;

/**
 * Sign-up, sign-in, and sessions. A session is a refresh token (kept by the client in an HttpOnly cookie)
 * that is exchanged for short-lived access JWTs.
 */
public interface AuthService {

    /** Who is signing in, recorded on the session for an "active sessions" list. */
    record ClientInfo(String userAgent, String ipAddress) {
    }

    /** A new or rotated session: the response body plus the raw refresh token for the cookie. */
    record Session(AuthResponseDto response, String refreshToken, Duration refreshTokenLifetime) {
    }

    /** Creates a user with a password identity and signs them in. */
    Session register(RegisterRequest request, ClientInfo client);

    /** Signs in with a username (or email) and password. */
    Session login(LoginRequest request, ClientInfo client);

    /** Exchanges a refresh token for a new access token and a replacement refresh token. */
    Session refresh(String refreshToken, ClientInfo client);

    /** Ends the session the refresh token belongs to (all its tokens stop working at once). */
    void logout(String refreshToken);

    /** Returns the given user. */
    UserDto getUser(UUID userId);

    /**
     * Changes the signed-in user's password after checking the current one. Every session of the user is
     * signed out (other devices too) and a new session is returned for this one.
     */
    Session changePassword(UUID userId, ChangePasswordRequest request, ClientInfo client);

    /**
     * Emails a reset link if an account with that email exists. Always completes the same way, so callers
     * cannot tell whether an account exists.
     */
    void requestPasswordReset(ForgotPasswordRequest request, ClientInfo client);

    /** Sets a new password with a token from a reset email and signs out every session of the user. */
    void resetPassword(ResetPasswordRequest request);
}
