package com.journey.api_rest_v1.config;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Problem-detail error responses (RFC 9457) for the whole API. Same as Spring's default, except that invalid
 * request bodies say what is wrong: {@code detail} lists the failed rules ("Email is required; Password must be
 * at least 8 characters") and {@code errors} maps each field to its message, so forms can show them.
 */
@RestControllerAdvice
public class ValidationErrorHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, ValidationErrorHandler::message,
                        (first, second) -> first, java.util.LinkedHashMap::new));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                errors.isEmpty() ? "Invalid request" : String.join("; ", errors.values()));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Custom messages ("Email is required") as written; default ones get the field name ("email must be ..."). */
    private static String message(FieldError error) {
        String message = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
        boolean custom = !message.isEmpty() && Character.isUpperCase(message.charAt(0));
        return custom ? message : error.getField().replace('_', ' ') + " " + message;
    }
}
