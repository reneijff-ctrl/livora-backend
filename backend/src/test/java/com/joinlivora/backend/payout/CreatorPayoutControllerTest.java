package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutEligibilityResponseDTO;
import com.joinlivora.backend.payout.dto.PayoutRequestResponseDTO;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorPayoutControllerTest {

    @Mock
    private PayoutService payoutService;
    @Mock
    private CreatorPayoutService creatorPayoutService;
    @Mock
    private PayoutRequestService payoutRequestService;
    @Mock
    private UserService userService;
    @Mock
    private LegacyCreatorStripeAccountRepository creatorStripeAccountRepository;
    @Mock
    private StripeConnectService stripeConnectService;
    @Mock
    private CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;

    @InjectMocks
    private CreatorPayoutController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void checkEligibility_ShouldReturnEligibility() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("creator@test.com");

        User user = new User();
        user.setEmail("creator@test.com");

        PayoutEligibilityResponseDTO expectedResponse = PayoutEligibilityResponseDTO.builder()
                .eligible(true)
                .reasons(List.of())
                .build();

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(payoutRequestService.checkEligibility(user)).thenReturn(expectedResponse);

        ResponseEntity<PayoutEligibilityResponseDTO> response = controller.checkEligibility(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
    }

    @Test
    void getPayouts_ShouldReturnPayoutRequests() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("creator@test.com");

        User user = new User();
        user.setEmail("creator@test.com");

        PayoutRequest request = new PayoutRequest();
        PayoutRequestResponseDTO responseDTO = PayoutRequestResponseDTO.builder()
                .amount(new java.math.BigDecimal("100.00"))
                .status(PayoutRequestStatus.PENDING)
                .build();

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(payoutRequestService.getPayoutRequestsByUser(user)).thenReturn(List.of(request));
        when(payoutRequestService.mapToResponseDTO(request)).thenReturn(responseDTO);

        ResponseEntity<List<PayoutRequestResponseDTO>> response = controller.getPayouts(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(responseDTO, response.getBody().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onboard_ShouldReturnUrl() throws Exception {
        java.security.Principal principal = mock(java.security.Principal.class);
        when(principal.getName()).thenReturn("creator@test.com");

        User user = new User();
        user.setEmail("creator@test.com");

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(stripeConnectService.createOrGetStripeAccount(user)).thenReturn("acct_123");
        when(stripeConnectService.generateOnboardingLink("acct_123", "http://localhost:3000/creator/stripe/success", "http://localhost:3000/creator/stripe/retry"))
                .thenReturn("https://stripe.com/onboard/123");

        ResponseEntity<Map<String, String>> response = (ResponseEntity<Map<String, String>>) controller.onboard(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://stripe.com/onboard/123", response.getBody().get("onboardingUrl"));
    }

    @Test
    void getStripeAccountStatus_Found_ShouldReturnAccount() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("creator@test.com");

        User user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");

        LegacyCreatorStripeAccount account = new LegacyCreatorStripeAccount();
        account.setCreatorId(1L);

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(creatorStripeAccountRepository.findByCreatorId(1L)).thenReturn(Optional.of(account));

        ResponseEntity<?> response = controller.getStripeAccountStatus(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(account, response.getBody());
    }

    @Test
    void requestPayout_ShouldReturnPayoutRequest() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("creator@test.com");

        User user = new User();
        user.setEmail("creator@test.com");

        PayoutRequest payoutRequest = new PayoutRequest();
        payoutRequest.setAmount(new java.math.BigDecimal("100.00"));
        
        PayoutRequestResponseDTO responseDTO = PayoutRequestResponseDTO.builder()
                .amount(new java.math.BigDecimal("100.00"))
                .build();

        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(payoutRequestService.createPayoutRequest(user)).thenReturn(payoutRequest);
        when(payoutRequestService.mapToResponseDTO(payoutRequest)).thenReturn(responseDTO);

        ResponseEntity<PayoutRequestResponseDTO> response = controller.requestPayout(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(responseDTO, response.getBody());
    }
}








