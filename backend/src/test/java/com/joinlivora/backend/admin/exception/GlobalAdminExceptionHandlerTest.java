package com.joinlivora.backend.admin.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;

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
    void handleAdminReadOnlyException_WhenReturnTypeIsList_ShouldReturnEmptyList() {
        when(methodParameter.getParameterType()).thenReturn((Class) List.class);

        ResponseEntity<Object> response = handler.handleAdminReadOnlyException(
                new NullPointerException("NPE"), handlerMethod, request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof List);
        assertTrue(((List<?>) response.getBody()).isEmpty());
    }

    @Test
    void handleAdminReadOnlyException_WhenReturnTypeIsPage_ShouldReturnEmptyPage() {
        when(methodParameter.getParameterType()).thenReturn((Class) Page.class);

        ResponseEntity<Object> response = handler.handleAdminReadOnlyException(
                new IllegalStateException("Invalid state"), handlerMethod, request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Page);
        assertTrue(((Page<?>) response.getBody()).isEmpty());
    }

    @Test
    void handleAdminReadOnlyException_WhenReturnTypeIsDto_ShouldReturnNull() {
        when(methodParameter.getParameterType()).thenReturn((Class) String.class);

        ResponseEntity<Object> response = handler.handleAdminReadOnlyException(
                new NullPointerException("NPE"), handlerMethod, request);

        assertNull(response, "Should return null for DTO return types to allow GlobalExceptionHandler to handle it");
    }

    @Test
    void handleAdminReadOnlyException_WhenReturnTypeIsResponseEntity_AndNestedTypeIsUnknown_ShouldReturnNull() {
        when(methodParameter.getParameterType()).thenReturn((Class) ResponseEntity.class);
        MethodParameter nestedParam = mock(MethodParameter.class);
        when(methodParameter.nested()).thenReturn(nestedParam);
        when(nestedParam.getNestedParameterType()).thenReturn((Class) Object.class);

        ResponseEntity<Object> response = handler.handleAdminReadOnlyException(
                new IllegalStateException("Error"), handlerMethod, request);

        assertNull(response, "Should return null for ResponseEntity<Object> so GlobalExceptionHandler returns proper 500");
    }
}
