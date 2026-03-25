package com.joinlivora.backend.controller;

import com.joinlivora.backend.admin.dto.UserAdminResponseDTO;
import com.joinlivora.backend.admin.service.AdminService;
import com.joinlivora.backend.analytics.FunnelTrackingFilter;
import com.joinlivora.backend.config.SecurityConfig;
import com.joinlivora.backend.monetization.PpvPurchaseRepository;
import com.joinlivora.backend.monetization.TipRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.security.AuditLogoutHandler;
import com.joinlivora.backend.security.JwtAuthenticationFilter;
import com.joinlivora.backend.security.RateLimitingFilter;
import com.joinlivora.backend.user.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorEarningRepository creatorEarningRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private UserSubscriptionRepository subscriptionRepository;

    @MockBean
    private TipRepository tipRepository;

    @MockBean
    private PpvPurchaseRepository ppvPurchaseRepository;

    @MockBean
    private AdminService adminService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RateLimitingFilter rateLimitingFilter;

    @MockBean
    private FunnelTrackingFilter funnelTrackingFilter;

    @MockBean
    private AuditLogoutHandler auditLogoutHandler;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() throws Exception {
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
    @WithMockUser(roles = "ADMIN")
    void getUsers_AsAdmin_ShouldReturnData() throws Exception {
        UserAdminResponseDTO dto = UserAdminResponseDTO.builder()
                .id(1L)
                .email("admin@test.com")
                .build();
        when(adminService.getUsers(any(), any())).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("admin@test.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUserStatus_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/status")
                        .with(csrf())
                        .param("status", "SUSPENDED"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shadowbanUser_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/shadowban")
                        .with(csrf())
                        .param("shadowbanned", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void togglePayouts_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/payouts")
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void forceLogout_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/logout")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getUsers_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }
}








