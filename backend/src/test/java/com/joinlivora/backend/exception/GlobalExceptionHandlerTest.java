package com.joinlivora.backend.exception;

import com.joinlivora.backend.common.exception.KycNotApprovedException;
import com.joinlivora.backend.common.exception.PayoutBlockedException;
import com.joinlivora.backend.payouts.exception.PayoutFrozenException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import org.apache.catalina.connector.ClientAbortException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final com.joinlivora.backend.audit.service.AuditService auditService = org.mockito.Mockito.mock(com.joinlivora.backend.audit.service.AuditService.class);
    private final com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler = org.mockito.Mockito.mock(com.joinlivora.backend.security.LoginFailureHandler.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(auditService, loginFailureHandler);

    @Test
    void handlePayoutFrozenException_ShouldReturnForbidden() {
        PayoutFrozenException ex = new PayoutFrozenException("Payout is frozen due to suspicious activity");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/payouts");
        MDC.put("requestId", "test-req-creator");

        ResponseEntity<ErrorResponse> response = handler.handlePayoutFrozenException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Payout Frozen");
        assertThat(response.getBody().getMessage()).isEqualTo("Payout is frozen due to suspicious activity");
        assertThat(response.getBody().getErrorCode()).isEqualTo("PAYOUT_FROZEN");
        assertThat(response.getBody().getRequestId()).isEqualTo("test-req-creator");
        
        MDC.clear();
    }

    @Test
    void handleUserRestrictedException_ShouldReturnTooManyRequestsWithExpiresAt() {
        java.time.Instant expiresAt = java.time.Instant.now().plusSeconds(300);
        UserRestrictedException ex = new UserRestrictedException(com.joinlivora.backend.abuse.model.RestrictionLevel.CHAT_MUTE, "Your chat is restricted", expiresAt);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/liveStream/123/chat");
        MDC.put("requestId", "restricted-req-creator");

        ResponseEntity<ErrorResponse> response = handler.handleUserRestrictedException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Too Many Requests");
        assertThat(response.getBody().getMessage()).isEqualTo("Your chat is restricted");
        assertThat(response.getBody().getErrorCode()).isEqualTo("USER_RESTRICTED_CHAT_MUTE");
        assertThat(response.getBody().getExpiresAt()).isEqualTo(expiresAt);
        
        MDC.clear();
    }

    @Test
    void handleUserRestrictedException_WhenTempSuspension_ShouldReturnForbidden() {
        java.time.Instant expiresAt = java.time.Instant.now().plusSeconds(3600);
        UserRestrictedException ex = new UserRestrictedException(com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION, "Your account is suspended", expiresAt);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        MDC.put("requestId", "suspended-login-creator");

        ResponseEntity<ErrorResponse> response = handler.handleUserRestrictedException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Forbidden");
        assertThat(response.getBody().getErrorCode()).isEqualTo("USER_RESTRICTED_TEMP_SUSPENSION");
        assertThat(response.getBody().getExpiresAt()).isEqualTo(expiresAt);

        MDC.clear();
    }

    @Test
    void handleUserRestrictedException_WhenFraudLock_ShouldReturnForbidden() {
        java.time.Instant expiresAt = java.time.Instant.now().plus(java.time.Duration.ofDays(7));
        UserRestrictedException ex = new UserRestrictedException(com.joinlivora.backend.abuse.model.RestrictionLevel.FRAUD_LOCK, "Tipping disabled pending review", expiresAt);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/monetization/tip");
        MDC.put("requestId", "fraud-lock-creator");

        ResponseEntity<ErrorResponse> response = handler.handleUserRestrictedException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Forbidden");
        assertThat(response.getBody().getErrorCode()).isEqualTo("USER_RESTRICTED_FRAUD_LOCK");
        assertThat(response.getBody().getExpiresAt()).isEqualTo(expiresAt);

        MDC.clear();
    }

    @Test
    void handleAuthenticationException_ShouldReturnErrorCode() {
        AuthenticationException ex = new AuthenticationException("Unauthorized access") {};
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void handleAccessDeniedException_ShouldReturnErrorCode() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden access");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDeniedException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void handleResourceNotFoundException_ShouldReturnErrorCode() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Not found");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFoundException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void handleEntityNotFoundException_ShouldReturnErrorCode() {
        jakarta.persistence.EntityNotFoundException ex = new jakarta.persistence.EntityNotFoundException("Entity not found");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<ErrorResponse> response = handler.handleEntityNotFoundException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void handleValidationException_ShouldReturnErrorCode() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "Invalid value");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void handleConstraintViolationException_ShouldReturnErrorCode() {
        jakarta.validation.ConstraintViolationException ex = new jakarta.validation.ConstraintViolationException("Invalid input", java.util.Collections.emptySet());
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void handleIllegalArgumentException_ShouldReturnBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid parameter");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid parameter");
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void handleIllegalStateException_ShouldReturnBadRequest() {
        IllegalStateException ex = new IllegalStateException("Invalid state");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid state");
        assertThat(response.getBody().getErrorCode()).isEqualTo("ILLEGAL_STATE");
    }

    @Test
    void handleRuntimeException_ShouldReturnInternalError() {
        RuntimeException ex = new RuntimeException("Generic error");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getStackTrace()).isNull();
    }

    @Test
    void handleRuntimeException_WhenAdminMetricsPath_ShouldIncludeStackTrace() {
        RuntimeException ex = new RuntimeException("Database connection failed");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/admin/dashboard/metrics");

        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStackTrace()).isNotNull();
        assertThat(response.getBody().getStackTrace()).contains("java.lang.RuntimeException: Database connection failed");
        assertThat(response.getBody().getStackTrace()).contains("at com.joinlivora.backend.exception.GlobalExceptionHandlerTest");
    }

    @Test
    void handleRuntimeException_WhenAdminMetricsPathAndProdProfile_ShouldExcludeStackTrace() {
        ReflectionTestUtils.setField(handler, "activeProfile", "prod");
        
        RuntimeException ex = new RuntimeException("Database connection failed");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/admin/dashboard/metrics");

        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStackTrace()).isNull();
        
        // Reset after test
        ReflectionTestUtils.setField(handler, "activeProfile", null);
    }

    @Test
    void handleRuntimeException_WhenContainsAdminMetricsPath_ShouldIncludeStackTrace() {
        RuntimeException ex = new RuntimeException("Something failed");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/admin/dashboard/metrics/v2");

        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStackTrace()).isNotNull();
    }

    @Test
    void handleKycBlocked_ShouldReturnForbiddenWithSimpleMap() {
        KycNotApprovedException ex = new KycNotApprovedException();

        ResponseEntity<?> response = handler.handleKycBlocked(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(java.util.Map.class);
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo("KYC_NOT_APPROVED");
        assertThat(body.get("message")).isEqualTo("Creator KYC not approved. Payout blocked.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlePayoutBlocked_ShouldReturnForbiddenWithSimpleMap() {
        PayoutBlockedException ex = new PayoutBlockedException("Payout is blocked");

        ResponseEntity<?> response = handler.handlePayoutBlocked(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(java.util.Map.class);
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo("PAYOUT_BLOCKED");
        assertThat(body.get("message")).isEqualTo("Payout is blocked");
    }

    @Test
    void handleClientAbortException_ShouldReturnNull() {
        ClientAbortException ex = new ClientAbortException("Broken pipe");
        ResponseEntity<ErrorResponse> response = handler.handleClientAbortException(ex);
        assertThat(response).isNull();
    }
}










