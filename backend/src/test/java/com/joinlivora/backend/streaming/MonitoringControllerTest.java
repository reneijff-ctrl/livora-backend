package com.joinlivora.backend.streaming;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MonitoringController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
}, excludeFilters = {
    @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = com.joinlivora.backend.security.JwtAuthenticationFilter.class),
    @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = com.joinlivora.backend.security.RateLimitingFilter.class)
})
class MonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeterRegistry meterRegistry;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @Test
    void reportPlaybackError_ShouldIncrementCounter() throws Exception {
        Counter counter = mock(Counter.class);
        when(meterRegistry.counter(eq("liveStreaming.playback.errors"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(counter);

        mockMvc.perform(post("/api/auth/monitoring/playback-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liveStreamId\": \"liveStream-1\", \"errorType\": \"manifestLoadError\", \"detail\": \"FATAL\"}"))
                .andExpect(status().isOk());

        verify(meterRegistry).counter(
                eq("liveStreaming.playback.errors"),
                eq("liveStreamId"), eq("liveStream-1"),
                eq("errorType"), eq("manifestLoadError")
        );
        verify(counter).increment();
    }
}








