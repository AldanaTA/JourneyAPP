package com.journey.api_rest_v1.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A 429 Too Many Requests error that tells the client how long to wait (Retry-After header). */
public class TooManyRequestsException extends ResponseStatusException {

    private final long retryAfterSeconds;

    /** Creates the error with a message and the number of seconds to wait. */
    public TooManyRequestsException(String reason, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, reason);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return headers;
    }
}
