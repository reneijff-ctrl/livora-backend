package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPayoutControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PayoutRepository payoutRepository;

    @Mock
    private StripeAccountRepository stripeAccountRepository;

    @Mock
    private UserService userService;

    @Mock
    private PayoutService payoutService;

    @Mock
    private com.joinlivora.backend.payouts.service.PayoutExecutionService payoutExecutionService;

    @Mock
    private AuditService auditService;

    @Mock
    private AdminPayoutService adminPayoutService;

    @Mock
    private PayoutRequestService payoutRequestService;

    @InjectMocks
    private AdminPayoutController controller;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter jacksonMessageConverter = new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        jacksonMessageConverter.setObjectMapper(mapper);

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(jacksonMessageConverter)
                .setCustomArgumentResolvers(
                        new org.springframework.data.web.PageableHandlerMethodArgumentResolver(),
                        new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(UserDetails.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        UserDetails userDetails = mock(UserDetails.class);
                        lenient().when(userDetails.getUsername()).thenReturn("admin@test.com");
                        return userDetails;
                    }
                })
                .build();

        User admin = new User();
        admin.setId(999L);
        admin.setEmail("admin@test.com");
        lenient().when(userService.getByEmail(any())).thenReturn(admin);
    }

    @Test
    void executeManualPayout_Success_ShouldReturnPayout() throws Exception {
        UUID creatorId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        CreatorPayout payout = CreatorPayout.builder()
                .id(UUID.randomUUID())
                .creatorId(creatorId)
                .amount(amount)
                .currency("EUR")
                .status(PayoutStatus.COMPLETED)
                .build();

        when(payoutService.calculateAvailablePayout(creatorId)).thenReturn(amount);
        when(payoutExecutionService.executePayout(eq(creatorId), eq(amount), eq("EUR"))).thenReturn(payout);

        mockMvc.perform(post("/api/admin/payouts/" + creatorId + "/execute")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(payoutExecutionService).executePayout(creatorId, amount, "EUR");
    }

    @Test
    void executeManualPayout_NoFunds_ShouldReturnBadRequest() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(payoutService.calculateAvailablePayout(creatorId)).thenReturn(BigDecimal.ZERO);

        mockMvc.perform(post("/api/admin/payouts/" + creatorId + "/execute")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No funds available for payout"));
    }

    @Test
    void executeManualPayout_ServiceError_ShouldReturnInternalServerError() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(payoutService.calculateAvailablePayout(creatorId)).thenReturn(new BigDecimal("50.00"));
        when(payoutService.executePayout(any(), any(), any())).thenThrow(new RuntimeException("Stripe error"));

        mockMvc.perform(post("/api/admin/payouts/" + creatorId + "/execute")
                        .with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(containsString("Payout execution failed")));
    }

    @Test
    void retryPayout_Success_ShouldReturnOk() throws Exception {
        UUID payoutId = UUID.randomUUID();
        Payout payout = Payout.builder()
                .id(payoutId)
                .status(PayoutStatus.FAILED)
                .eurAmount(new BigDecimal("50.00"))
                .build();

        when(payoutRepository.findById(payoutId)).thenReturn(java.util.Optional.of(payout));

        mockMvc.perform(post("/api/admin/payouts/" + payoutId + "/retry")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payout reset to PENDING for retry"));

        verify(payoutRepository).save(argThat(p -> p.getStatus() == PayoutStatus.PENDING));
    }

    @Test
    void retryPayout_NotFailed_ShouldReturnBadRequest() throws Exception {
        UUID payoutId = UUID.randomUUID();
        Payout payout = Payout.builder()
                .id(payoutId)
                .status(PayoutStatus.COMPLETED)
                .build();

        when(payoutRepository.findById(payoutId)).thenReturn(java.util.Optional.of(payout));

        mockMvc.perform(post("/api/admin/payouts/" + payoutId + "/retry")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only failed payouts can be retried"));
    }

    @Test
    void overridePayout_Success_ShouldReturnOk() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {
                    "releaseHold": true,
                    "forcePayout": true,
                    "relockEarnings": true,
                    "adminNote": "Test note"
                }
                """;

        mockMvc.perform(post("/api/admin/payouts/" + id + "/override")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payout override successful"));

        verify(adminPayoutService).overridePayout(eq(id), any(), any(), any(), any());
    }

    @Test
    void suspendPayouts_Success_ShouldReturnOk() throws Exception {
        String stripeAccountId = "acct_123";
        StripeAccount account = StripeAccount.builder()
                .stripeAccountId(stripeAccountId)
                .payoutsEnabled(true)
                .build();

        when(stripeAccountRepository.findByStripeAccountId(stripeAccountId)).thenReturn(java.util.Optional.of(account));

        mockMvc.perform(post("/api/admin/payouts/accounts/" + stripeAccountId + "/suspend")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payouts suspended"));

        verify(stripeAccountRepository).save(argThat(a -> !a.isPayoutsEnabled()));
    }

    @Test
    void getPayoutRequests_ShouldReturnList() throws Exception {
        com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO dto = com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO.builder()
                .amount(new BigDecimal("100.00"))
                .status(PayoutRequestStatus.PENDING)
                .build();

        // Since we map in the controller, we should mock the behavior of mapping or the service call
        when(payoutRequestService.getPayoutRequestsByStatus(any())).thenReturn(java.util.List.of(new PayoutRequest()));
        when(payoutRequestService.mapToResponseDTO(any())).thenReturn(dto);

        mockMvc.perform(get("/api/admin/payouts/requests")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(100.00))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getPayoutRequest_ShouldReturnDetail() throws Exception {
        UUID id = UUID.randomUUID();
        com.joinlivora.backend.payout.dto.PayoutRequestAdminDetailDTO dto = com.joinlivora.backend.payout.dto.PayoutRequestAdminDetailDTO.builder()
                .id(id)
                .creatorEmail("creator@test.com")
                .amount(new BigDecimal("100.00"))
                .fraudScore(10)
                .trustScore(90)
                .stripeReady(true)
                .payoutHolds(java.util.List.of())
                .build();

        when(payoutRequestService.getPayoutRequestAdminDetail(id)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/payouts/requests/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.creatorEmail").value("creator@test.com"))
                .andExpect(jsonPath("$.fraudScore").value(10))
                .andExpect(jsonPath("$.trustScore").value(90))
                .andExpect(jsonPath("$.stripeReady").value(true));
    }

    @Test
    void approvePayoutRequest_ShouldReturnOk() throws Exception {
        UUID id = UUID.randomUUID();
        com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO dto = com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO.builder()
                .id(id)
                .status(PayoutRequestStatus.APPROVED)
                .build();

        when(payoutRequestService.approvePayoutRequest(id)).thenReturn(new PayoutRequest());
        when(payoutRequestService.mapToResponseDTO(any())).thenReturn(dto);

        mockMvc.perform(post("/api/admin/payouts/requests/" + id + "/approve")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectPayoutRequest_ShouldReturnOk() throws Exception {
        UUID id = UUID.randomUUID();
        com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO dto = com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO.builder()
                .id(id)
                .status(PayoutRequestStatus.REJECTED)
                .rejectionReason("Ineligible")
                .build();

        when(payoutRequestService.rejectPayoutRequest(eq(id), anyString())).thenReturn(new PayoutRequest());
        when(payoutRequestService.mapToResponseDTO(any())).thenReturn(dto);

        mockMvc.perform(post("/api/admin/payouts/requests/" + id + "/reject")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Ineligible\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Ineligible"));
    }

    @Test
    void getPayouts_ShouldReturnPage() throws Exception {
        UUID payoutId = UUID.randomUUID();
        User user = new User();
        user.setEmail("user@test.com");
        Payout payout = Payout.builder()
                .id(payoutId)
                .user(user)
                .eurAmount(new BigDecimal("100.00"))
                .status(PayoutStatus.COMPLETED)
                .createdAt(java.time.Instant.now())
                .build();

        org.springframework.data.domain.Page<Payout> page = new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(payout),
                org.springframework.data.domain.PageRequest.of(0, 10),
                1
        );
        when(payoutRepository.findAllWithUser(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/payouts")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(payoutId.toString()))
                .andExpect(jsonPath("$.content[0].userEmail").value("user@test.com"))
                .andExpect(jsonPath("$.content[0].amount").value(100.00))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }
}








