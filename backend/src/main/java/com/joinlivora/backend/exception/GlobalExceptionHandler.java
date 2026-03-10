package com.joinlivora.backend.exception;

import com.joinlivora.backend.common.exception.KycNotApprovedException;
import com.joinlivora.backend.fraud.exception.HighFraudRiskException;
import com.stripe.exception.StripeException;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.common.exception.PayoutBlockedException;
import com.joinlivora.backend.payouts.exception.PayoutFrozenException;
import com.joinlivora.backend.security.LoginFailureHandler;
import com.joinlivora.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.catalina.connector.ClientAbortException;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AuditService auditService;
    private final LoginFailureHandler loginFailureHandler;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        String requestId = MDC.get("requestId");
        log.warn("SECURITY [auth_failure]: Login failure detected - Bad credentials for request: {}", requestId);
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password", request, "INVALID_CREDENTIALS", ex);
    }

    @ExceptionHandler(org.springframework.security.authentication.LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedException(org.springframework.security.authentication.LockedException ex, HttpServletRequest request) {
        log.warn("SECURITY [security_incident]: Access attempt on locked account for request: {}", MDC.get("requestId"));
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Account Locked", ex.getMessage(), request, "ACCOUNT_LOCKED", ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request, "UNAUTHORIZED", ex);
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermissionDeniedException(PermissionDeniedException ex, HttpServletRequest request) {
        log.warn("Permission denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request, "FORBIDDEN", ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        if (ex instanceof ChatAccessException cae) {
            log.warn("Chat access denied: {} [Code: {}]", cae.getMessage(), cae.getErrorCode());
            ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.FORBIDDEN.value())
                    .error("Forbidden")
                    .message(cae.getMessage())
                    .path(request.getRequestURI())
                    .requestId(MDC.get("requestId"))
                    .errorCode(cae.getErrorCode().name())
                    .roomId(cae.getRoomId())
                    .ppvContentId(cae.getPpvContentId())
                    .requiredPrice(cae.getRequiredPrice());
            
            if (request.getRequestURI().contains("/api/admin/dashboard/metrics") && !"prod".equals(activeProfile)) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                cae.printStackTrace(pw);
                builder.stackTrace(sw.toString());
            }
            
            return new ResponseEntity<>(builder.build(), HttpStatus.FORBIDDEN);
        }
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to access this resource", request, "FORBIDDEN", ex);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalanceException(InsufficientBalanceException ex, HttpServletRequest request) {
        log.warn("Insufficient balance: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Insufficient Balance", ex.getMessage(), request, "INSUFFICIENT_BALANCE", ex);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Business Error", ex.getMessage(), request, "BUSINESS_ERROR", ex);
    }

    @ExceptionHandler(PayoutRestrictedException.class)
    public ResponseEntity<ErrorResponse> handlePayoutRestrictedException(PayoutRestrictedException ex, HttpServletRequest request) {
        log.warn("SECURITY [payout_restriction]: Payout restricted: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Payout Restricted", ex.getMessage(), request, "PAYOUT_RESTRICTED", ex);
    }

    @ExceptionHandler(PayoutFrozenException.class)
    public ResponseEntity<ErrorResponse> handlePayoutFrozenException(PayoutFrozenException ex, HttpServletRequest request) {
        log.warn("SECURITY [payout_freeze]: Payout attempt while frozen: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Payout Frozen", ex.getMessage(), request, "PAYOUT_FROZEN", ex);
    }

    @ExceptionHandler(PaymentLockedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentLockedException(PaymentLockedException ex, HttpServletRequest request) {
        log.warn("SECURITY [fraud_protection]: Payment attempt from locked creator: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Payment Locked", ex.getMessage(), request, "PAYMENT_LOCKED", ex);
    }

    @ExceptionHandler(KycNotApprovedException.class)
    public ResponseEntity<?> handleKycBlocked(KycNotApprovedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "KYC_NOT_APPROVED",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(PayoutBlockedException.class)
    public ResponseEntity<?> handlePayoutBlocked(PayoutBlockedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "PAYOUT_BLOCKED",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(UserRestrictedException.class)
    public ResponseEntity<ErrorResponse> handleUserRestrictedException(UserRestrictedException ex, HttpServletRequest request) {
        log.warn("SECURITY [abuse_prevention]: Restricted creator attempt: {} [Level: {}]", ex.getMessage(), ex.getLevel());
        
        HttpStatus status = (ex.getLevel() == com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION 
                || ex.getLevel() == com.joinlivora.backend.abuse.model.RestrictionLevel.FRAUD_LOCK)
                ? HttpStatus.FORBIDDEN 
                : HttpStatus.TOO_MANY_REQUESTS;
        
        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .requestId(MDC.get("requestId"))
                .errorCode("USER_RESTRICTED_" + ex.getLevel())
                .expiresAt(ex.getExpiresAt());
        
        if (request.getRequestURI().contains("/api/admin/dashboard/metrics") && !"prod".equals(activeProfile)) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            builder.stackTrace(sw.toString());
        }
        
        return new ResponseEntity<>(builder.build(), status);
    }

    @ExceptionHandler(HighFraudRiskException.class)
    public ResponseEntity<ErrorResponse> handleHighFraudRiskException(HighFraudRiskException ex, HttpServletRequest request) {
        log.warn("SECURITY [fraud_protection]: Transaction blocked due to high fraud risk. Score: {}, Reasons: {}", ex.getScore(), ex.getReasons());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request, "HIGH_FRAUD_RISK", ex);
    }

    @ExceptionHandler(TrustChallengeException.class)
    public ResponseEntity<ErrorResponse> handleTrustChallengeException(TrustChallengeException ex, HttpServletRequest request) {
        log.warn("SECURITY [trust_evaluation]: Trust challenge required for creator at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Trust Challenge Required", ex.getMessage(), request, "TRUST_CHALLENGE_REQUIRED", ex);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request, "NOT_FOUND", ex);
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFoundException(jakarta.persistence.EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Entity not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request, "NOT_FOUND", ex);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Illegal State", ex.getMessage(), request, "ILLEGAL_STATE", ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request, "INVALID_ARGUMENT", ex);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not supported: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", ex.getMessage(), request, "METHOD_NOT_ALLOWED", ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", message, request, "VALIDATION_ERROR", ex);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(jakarta.validation.ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request, "VALIDATION_ERROR", ex);
    }

    @ExceptionHandler(StripeException.class)
    public ResponseEntity<ErrorResponse> handleStripeException(StripeException ex, HttpServletRequest request) {
        log.error("SECURITY [payment_failure]: Stripe API error: {} [Type: {}, Status: {}]", 
                ex.getMessage(), ex.getClass().getSimpleName(), ex.getStatusCode());
        
        String message = "Payment provider error";
        String errorCode = "PAYMENT_PROVIDER_ERROR";
        HttpStatus status = HttpStatus.BAD_GATEWAY;

        if (ex.getStatusCode() != null && ex.getStatusCode() == 404) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", "Payment resource not found", request, "PAYMENT_RESOURCE_NOT_FOUND", ex);
        }

        if (ex instanceof com.stripe.exception.CardException) {
            message = "Your card was declined.";
            errorCode = "CARD_DECLINED";
            status = HttpStatus.BAD_REQUEST;
        } else if (ex instanceof com.stripe.exception.RateLimitException) {
            message = "Too many requests to the payment provider. Please try again later.";
            errorCode = "PAYMENT_RATE_LIMIT";
            status = HttpStatus.TOO_MANY_REQUESTS;
        } else if (ex instanceof com.stripe.exception.InvalidRequestException) {
            message = "Invalid request sent to payment provider.";
            errorCode = "INVALID_PAYMENT_REQUEST";
            status = HttpStatus.BAD_REQUEST;
        } else if (ex instanceof com.stripe.exception.AuthenticationException) {
            message = "Internal payment authentication error.";
            errorCode = "PAYMENT_AUTHENTICATION_ERROR";
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return buildErrorResponse(status, "Payment Error", message, request, errorCode, ex);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found (static): {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request, "NOT_FOUND", ex);
    }

    @ExceptionHandler(ClientAbortException.class)
    public ResponseEntity<ErrorResponse> handleClientAbortException(ClientAbortException ex) {
        log.debug("Client aborted the connection: {}", ex.getMessage());
        return null;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        if (ex.getCause() instanceof StripeException stripeException) {
            return handleStripeException(stripeException, request);
        }
        
        if (ex.getMessage() != null && ex.getMessage().contains("User not found")) {
            log.warn("User not found: {} [RequestID: {}]", ex.getMessage(), MDC.get("requestId"));
            loginFailureHandler.onLoginFailure((String) null, "user_not_found", request);
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", "User not found", request, "NOT_FOUND", ex);
        }
        log.error("Runtime error [RequestID: {}]", MDC.get("requestId"), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An internal error occurred", request, "INTERNAL_ERROR", ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred [RequestID: {}]", MDC.get("requestId"), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred. Please contact support with Request ID: " + MDC.get("requestId"), request, "UNEXPECTED_ERROR", ex);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String error, String message, HttpServletRequest request) {
        return buildErrorResponse(status, error, message, request, null, null);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String error, String message, HttpServletRequest request, String errorCode) {
        return buildErrorResponse(status, error, message, request, errorCode, null);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String error, String message, HttpServletRequest request, String errorCode, Throwable ex) {
        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .requestId(MDC.get("requestId"))
                .errorCode(errorCode);

        if (ex != null && request.getRequestURI().contains("/api/admin/dashboard/metrics") && !"prod".equals(activeProfile)) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            builder.stackTrace(sw.toString());
        }

        return new ResponseEntity<>(builder.build(), status);
    }
}
