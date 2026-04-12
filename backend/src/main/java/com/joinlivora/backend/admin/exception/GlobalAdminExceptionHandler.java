package com.joinlivora.backend.admin.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.core.MethodParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Fallback handler for admin read-only endpoints only.
 *
 * Scope: restricted to endpoints that return List or Page (dashboard stats, queues).
 * Mutation endpoints (approve/reject/suspend/etc.) are NOT intercepted here —
 * they fall through to GlobalExceptionHandler and return proper HTTP 4xx/5xx.
 *
 * Does NOT carry @Order(HIGHEST_PRECEDENCE) — GlobalExceptionHandler takes priority
 * for all typed exceptions (BusinessException, ResourceNotFoundException, etc.).
 */
@Slf4j
@ControllerAdvice(basePackages = {
        "com.joinlivora.backend.admin",
        "com.joinlivora.backend.streaming",
        "com.joinlivora.backend.audit",
        "com.joinlivora.backend.analytics",
        "com.joinlivora.backend.chargeback"
})
public class GlobalAdminExceptionHandler {

    /**
     * Safe fallback ONLY for read-only list/page endpoints.
     * Returns an empty collection so the dashboard renders instead of crashing.
     * For any other return type (DTO, void, ResponseEntity<DTO>) this method
     * returns null, signalling Spring to try the next handler (GlobalExceptionHandler).
     */
    @ExceptionHandler({NullPointerException.class, IllegalStateException.class})
    public ResponseEntity<Object> handleAdminReadOnlyException(
            Exception ex, HandlerMethod handlerMethod, HttpServletRequest request) {

        MethodParameter returnParam = handlerMethod.getReturnType();
        Class<?> returnType = returnParam.getParameterType();

        // Unwrap ResponseEntity<T> to inspect T
        if (ResponseEntity.class.isAssignableFrom(returnType)) {
            try {
                returnType = returnParam.nested().getNestedParameterType();
            } catch (Exception e) {
                // Cannot determine nested type — do not intercept, let it propagate
                return null;
            }
        }

        // Safe fallback: empty list for list-returning read endpoints
        if (List.class.isAssignableFrom(returnType)) {
            log.error("ADMIN_READ_ERROR [{}] returning empty list: {}",
                    handlerMethod.getMethod().getName(), ex.getMessage(), ex);
            return ResponseEntity.ok(new ArrayList<>());
        }

        // Safe fallback: empty page for paginated read endpoints
        if (Page.class.isAssignableFrom(returnType)) {
            log.error("ADMIN_READ_ERROR [{}] returning empty page: {}",
                    handlerMethod.getMethod().getName(), ex.getMessage(), ex);
            return ResponseEntity.ok(Page.empty());
        }

        // All other return types (DTOs, void, mutation endpoints) — do NOT intercept.
        // Fall through to GlobalExceptionHandler → returns proper HTTP 500.
        return null;
    }
}
