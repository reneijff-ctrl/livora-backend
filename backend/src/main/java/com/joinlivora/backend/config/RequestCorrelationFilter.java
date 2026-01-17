package com.joinlivora.backend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter implements Filter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final String MDC_ENDPOINT_KEY = "endpoint";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest) {
            String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }

            MDC.put(MDC_REQUEST_ID_KEY, requestId);
            MDC.put(MDC_ENDPOINT_KEY, httpRequest.getRequestURI());

            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader(REQUEST_ID_HEADER, requestId);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
