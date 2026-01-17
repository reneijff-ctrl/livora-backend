package com.joinlivora.backend.exception;

import com.stripe.exception.StripeException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        String requestId = MDC.get("requestId");
        log.warn("SECURITY [auth_failure]: Login failure detected - Bad credentials for request: {}", requestId);
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password", request);
    }

    @ExceptionHandler(org.springframework.security.authentication.LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedException(org.springframework.security.authentication.LockedException ex, HttpServletRequest request) {
        log.warn("SECURITY [security_incident]: Access attempt on locked account for request: {}", MDC.get("requestId"));
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Account Locked", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to access this resource", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", message, request);
    }

    @ExceptionHandler(StripeException.class)
    public ResponseEntity<ErrorResponse> handleStripeException(StripeException ex, HttpServletRequest request) {
        log.error("SECURITY [payment_failure]: Stripe API error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Payment provider error", request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        if (ex.getMessage() != null && ex.getMessage().contains("User not found")) {
            log.warn("User not found: {} [RequestID: {}]", ex.getMessage(), MDC.get("requestId"));
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", "User not found", request);
        }
        log.error("Runtime error [RequestID: {}]", MDC.get("requestId"), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An internal error occurred", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred [RequestID: {}]", MDC.get("requestId"), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred. Please contact support with Request ID: " + MDC.get("requestId"), request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String error, String message, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .requestId(MDC.get("requestId"))
                .build();
        return new ResponseEntity<>(response, status);
    }
}
