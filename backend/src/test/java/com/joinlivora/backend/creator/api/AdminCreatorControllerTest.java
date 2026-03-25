package com.joinlivora.backend.creator.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.creator.dto.AdminCreatorResponse;
import com.joinlivora.backend.creator.dto.AdminCreatorStripeStatusResponse;
import com.joinlivora.backend.creator.dto.UpdateCreatorStatusRequest;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCreatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorProfileService creatorProfileService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCreators_ShouldReturnData() throws Exception {
        AdminCreatorResponse response = AdminCreatorResponse.builder()
                .userId(1L)
                .email("creator@example.com")
                .displayName("Creator Name")
                .status(ProfileStatus.PENDING)
                .build();

        PageImpl<AdminCreatorResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
        when(creatorProfileService.getAdminCreators(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("creator@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCreators_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/admin/creators"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCreatorsStripeStatus_ShouldReturnData() throws Exception {
        AdminCreatorStripeStatusResponse response = AdminCreatorStripeStatusResponse.builder()
                .userId(2L)
                .email("creator2@example.com")
                .stripeAccountId("acct_123")
                .payoutsEnabled(true)
                .stripeOnboardingComplete(true)
                .build();

        PageImpl<AdminCreatorStripeStatusResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
        when(creatorProfileService.getAdminCreatorsStripeStatus(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/creators/stripe-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("creator2@example.com"))
                .andExpect(jsonPath("$.content[0].stripeAccountId").value("acct_123"))
                .andExpect(jsonPath("$.content[0].payoutsEnabled").value(true))
                .andExpect(jsonPath("$.content[0].stripeOnboardingComplete").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCreatorsStripeStatus_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/admin/creators/stripe-status"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_ShouldCallService() throws Exception {
        UpdateCreatorStatusRequest request = new UpdateCreatorStatusRequest();
        request.setStatus(ProfileStatus.ACTIVE);

        mockMvc.perform(post("/api/admin/creators/1/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(creatorProfileService).updateCreatorStatus(eq(1L), eq(ProfileStatus.ACTIVE));
    }
}








