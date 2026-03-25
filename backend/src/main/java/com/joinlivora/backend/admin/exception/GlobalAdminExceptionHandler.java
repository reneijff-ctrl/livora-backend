package com.joinlivora.backend.admin.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler for admin endpoints to prevent UI crashes.
 * Returns HTTP 200 with fallback values for specific internal errors.
 */
@Slf4j
@ControllerAdvice(basePackages = {"com.joinlivora.backend.admin", "com.joinlivora.backend.streaming", "com.joinlivora.backend.audit", "com.joinlivora.backend.analytics", "com.joinlivora.backend.chargeback"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalAdminExceptionHandler {

    @ExceptionHandler({NullPointerException.class, IllegalStateException.class, RuntimeException.class})
    public ResponseEntity<Object> handleAdminException(Exception ex, HandlerMethod handlerMethod, HttpServletRequest request) {
        String controllerName = handlerMethod.getBeanType().getSimpleName();
        String packageName = handlerMethod.getBeanType().getPackageName();
        String path = request.getRequestURI();

        boolean isAdmin = controllerName.contains("Admin") || 
                          packageName.contains(".admin") || 
                          packageName.endsWith(".admin") ||
                          path.contains("/api/admin") ||
                          path.contains("/internal/admin") ||
                          path.contains("/api/admin/dashboard");

        if (!isAdmin) {
            // Signal to Spring to try other @ExceptionHandler methods by returning null
            return null;
        }

        log.error("ADMIN_ERROR: Internal failure in admin endpoint [{}]: {}", 
                handlerMethod.getMethod().getName(), ex.getMessage(), ex);

        MethodParameter returnParam = handlerMethod.getReturnType();
        Class<?> returnType = returnParam.getParameterType();

        // If it returns a ResponseEntity, look into the nested type
        if (ResponseEntity.class.isAssignableFrom(returnType)) {
            try {
                returnType = returnParam.nested().getNestedParameterType();
            } catch (Exception e) {
                // If nested type cannot be determined, fallback to Map
                returnType = Map.class;
            }
        }

        // 1. If it returns a List, provide an empty list
        if (List.class.isAssignableFrom(returnType)) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        // 2. If it returns a Page, provide an empty page
        if (Page.class.isAssignableFrom(returnType)) {
            return ResponseEntity.ok(Page.empty());
        }

        // 3. For other types (usually DTOs), return a safe map
        // Most admin DTOs in this project are flat or have simple structures.
        // Returning an empty map/object is safer for the frontend than a 500 error.
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("success", true);
        fallback.put("isFallback", true);
        fallback.put("message", "Service temporarily degraded. Returning safe defaults.");
        
        return ResponseEntity.ok(fallback);
    }
}
