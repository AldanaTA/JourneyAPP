package com.journey.api_rest_v1.service;

/**
 * Published when a reset link should be emailed. Handled after the transaction commits, so the email is only
 * sent once the token is saved.
 *
 * @param email       Where to send the link.
 * @param displayName How to greet the user.
 * @param rawToken    The token for the link (never stored; only its hash is).
 * @param minutesValid How long the link works.
 */
public record PasswordResetRequestedEvent(String email, String displayName, String rawToken, long minutesValid) {

    /** Keeps the raw token out of logs. */
    @Override
    public String toString() {
        return "PasswordResetRequestedEvent[email=" + email + "]";
    }
}
