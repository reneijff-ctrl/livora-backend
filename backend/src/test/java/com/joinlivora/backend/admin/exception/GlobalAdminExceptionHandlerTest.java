package com.joinlivora.backend.admin.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalAdminExceptionHandlerTest {

    private GlobalAdminExceptionHandler handler;
    private HandlerMethod handlerMethod;
    private MethodParameter methodParameter;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        handler = new GlobalAdminExceptionHandler();
        handlerMethod = mock(HandlerMethod.class);
        methodParameter = mock(MethodParameter.class);
        request = mock(HttpServletRequest.class);
        
        Method dummyMethod = this.getClass().getDeclaredMethod("setUp");
        when(handlerMethod.getMethod()).thenReturn(dummyMethod);
        when(handlerMethod.getReturnType()).thenReturn(methodParameter);
        when(handlerMethod.getBeanType()).thenReturn((Class) this.getClass());
        when(request.getRequestURI()).thenReturn("/api/admin/metrics");
    }

    @Test
    void handleAdminException_WhenReturnTypeIsList_ShouldReturnEmptyList() {
        when(methodParameter.getParameterType()).thenReturn((Class) List.class);
        
        ResponseEntity<Object> response = handler.handleAdminException(new RuntimeException("Error"), handlerMethod, request);
        
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof List);
        assertTrue(((List<?>) response.getBody()).isEmpty());
    }

    @Test
    void handleAdminException_WhenReturnTypeIsPage_ShouldReturnEmptyPage() {
        when(methodParameter.getParameterType()).thenReturn((Class) Page.class);
        
        ResponseEntity<Object> response = handler.handleAdminException(new IllegalStateException("Invalid"), handlerMethod, request);
        
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Page);
        assertTrue(((Page<?>) response.getBody()).isEmpty());
    }

    @Test
    void handleAdminException_WhenReturnTypeIsDtoWrappedInResponseEntity_ShouldReturnSafeMap() {
        when(methodParameter.getParameterType()).thenReturn((Class) ResponseEntity.class);
        MethodParameter nestedParam = mock(MethodParameter.class);
        when(methodParameter.nested()).thenReturn(nestedParam);
        when(nestedParam.getNestedParameterType()).thenReturn((Class) Object.class);
        
        ResponseEntity<Object> response = handler.handleAdminException(new NullPointerException("NPE"), handlerMethod, request);
        
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(true, body.get("success"));
        assertEquals(true, body.get("isFallback"));
        assertEquals("Service temporarily degraded. Returning safe defaults.", body.get("message"));
    }

    @Test
    void handleAdminException_WhenRuntimeException_ShouldLogAndReturn200() {
        when(methodParameter.getParameterType()).thenReturn((Class) String.class);
        
        ResponseEntity<Object> response = handler.handleAdminException(new RuntimeException("Crash"), handlerMethod, request);
        
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
    }

    @Test
    void handleAdminException_WhenNotAdminPath_ShouldReturnNull() {
        when(request.getRequestURI()).thenReturn("/api/public/data");
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class); // Not an admin controller name
        
        ResponseEntity<Object> response = handler.handleAdminException(new RuntimeException("Error"), handlerMethod, request);
        
        assertNull(response, "Should return null for non-admin paths to allow other handlers to take over");
    }
}








