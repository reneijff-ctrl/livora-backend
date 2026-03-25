package com.joinlivora.backend.payment;

import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvoiceController.class)
@Import(SecurityConfig.class)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private InvoicePdfService invoicePdfService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @BeforeEach
    void setup() throws jakarta.servlet.ServletException, java.io.IOException {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(funnelTrackingFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "creator@example.com", roles = "USER")
    void downloadInvoice_Owner_ShouldReturnPdf() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        User user = new User();
        user.setId(1L);
        user.setEmail("creator@example.com");

        Invoice invoice = Invoice.builder()
                .userId(user)
                .invoiceNumber("INV-001")
                .grossAmount(new BigDecimal("10.00"))
                .currency("eur")
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(userService.getByEmail("creator@example.com")).thenReturn(user);
        when(invoicePdfService.generateInvoicePdf(any())).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/invoices/" + invoiceId + "/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-INV-001.pdf"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void downloadInvoice_Admin_ShouldReturnPdf() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");

        User admin = new User();
        admin.setId(2L);
        admin.setEmail("admin@example.com");

        Invoice invoice = Invoice.builder()
                .userId(owner)
                .invoiceNumber("INV-001")
                .grossAmount(new BigDecimal("10.00"))
                .currency("eur")
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(userService.getByEmail("admin@example.com")).thenReturn(admin);
        when(invoicePdfService.generateInvoicePdf(any())).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/invoices/" + invoiceId + "/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    @WithMockUser(username = "other@example.com", roles = "USER")
    void downloadInvoice_NotOwner_ShouldReturnForbidden() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");

        User other = new User();
        other.setId(2L);
        other.setEmail("other@example.com");

        Invoice invoice = Invoice.builder()
                .userId(owner)
                .invoiceNumber("INV-001")
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(userService.getByEmail("other@example.com")).thenReturn(other);

        mockMvc.perform(get("/api/invoices/" + invoiceId + "/download"))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadInvoice_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/invoices/" + UUID.randomUUID() + "/download"))
                .andExpect(status().isUnauthorized());
    }
}








