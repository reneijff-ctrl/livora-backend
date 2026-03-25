package com.joinlivora.backend.stripe.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.payout.LegacyCreatorStripeAccount;
import com.joinlivora.backend.payout.StripeConnectService;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StripeCheckoutServiceTest {

    private StripeCheckoutService stripeCheckoutService;
    private MockedStatic<Session> mockedSession;
    private CreatorRepository creatorRepository;
    private StripeConnectService stripeConnectService;

    @BeforeEach
    void setUp() {
        creatorRepository = mock(CreatorRepository.class);
        stripeConnectService = mock(StripeConnectService.class);
        stripeCheckoutService = new StripeCheckoutService(
                "http://localhost:5173/payment/success",
                "http://localhost:5173/payment/cancel",
                creatorRepository,
                stripeConnectService
        );
        ReflectionTestUtils.setField(stripeCheckoutService, "stripeEnabled", true);
        ReflectionTestUtils.setField(stripeCheckoutService, "currency", "eur");
        ReflectionTestUtils.setField(stripeCheckoutService, "platformFeePercentage", 30);
        Stripe.apiKey = "sk_test_key";
        mockedSession = mockStatic(Session.class);
    }

    @AfterEach
    void tearDown() {
        mockedSession.close();
    }

    @Test
    void createCheckoutSession_ShouldReturnUrl() throws StripeException {
        // Arrange
        Long creatorId = 1L;
        Long userId = 2L;
        Long amountCents = 1000L;
        Session session = mock(Session.class);
        when(session.getUrl()).thenReturn("http://stripe-session-url");
        mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(session);

        // Act
        String url = stripeCheckoutService.createCheckoutSession(creatorId, "John Doe", userId, amountCents);

        // Assert
        assertEquals("http://stripe-session-url", url);
        assertEquals("sk_test_key", Stripe.apiKey);

        ArgumentCaptor<SessionCreateParams> captor = ArgumentCaptor.forClass(SessionCreateParams.class);
        mockedSession.verify(() -> Session.create(captor.capture()));
        
        SessionCreateParams params = captor.getValue();
        assertEquals("eur", params.getCurrency());
        assertEquals(SessionCreateParams.Mode.PAYMENT, params.getMode());
        assertEquals("1", params.getMetadata().get("creator"));
        assertEquals("2", params.getMetadata().get("userId"));
        assertEquals("TIP", params.getMetadata().get("type"));
        assertEquals("http://localhost:5173/payment/success?session_id={CHECKOUT_SESSION_ID}", params.getSuccessUrl());
        assertEquals("http://localhost:5173/payment/cancel?creatorId=1", params.getCancelUrl());
        
        assertEquals(1, params.getLineItems().size());
        assertEquals("Tip for John Doe", params.getLineItems().get(0).getPriceData().getProductData().getName());
        assertEquals(1000L, params.getLineItems().get(0).getPriceData().getUnitAmount());
    }

    @Test
    void createCheckoutSession_WithInvalidAmount_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
            stripeCheckoutService.createCheckoutSession(1L, "John", 2L, 99L)
        );
    }

    @Test
    void createCheckoutSession_ShouldUseDestinationCharges_WhenCreatorHasStripeAccount() throws StripeException {
        // Arrange
        Long creatorId = 1L;
        Long userId = 2L;
        Long amountCents = 1000L;
        
        User creatorUser = mock(User.class);
        when(creatorUser.getId()).thenReturn(10L);
        Creator creator = mock(Creator.class);
        when(creator.getUser()).thenReturn(creatorUser);
        when(creatorRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        
        LegacyCreatorStripeAccount stripeAccount = mock(LegacyCreatorStripeAccount.class);
        when(stripeAccount.getStripeAccountId()).thenReturn("acct_123");
        when(stripeAccount.isChargesEnabled()).thenReturn(true);
        when(stripeConnectService.getAccountByCreatorId(10L)).thenReturn(Optional.of(stripeAccount));

        Session session = mock(Session.class);
        when(session.getUrl()).thenReturn("http://stripe-session-url");
        mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(session);

        // Act
        String url = stripeCheckoutService.createCheckoutSession(creatorId, "John Doe", userId, amountCents);

        // Assert
        assertEquals("http://stripe-session-url", url);
        ArgumentCaptor<SessionCreateParams> captor = ArgumentCaptor.forClass(SessionCreateParams.class);
        mockedSession.verify(() -> Session.create(captor.capture()));
        
        SessionCreateParams params = captor.getValue();
        assertNotNull(params.getPaymentIntentData());
        assertEquals(300L, params.getPaymentIntentData().getApplicationFeeAmount());
        assertEquals("acct_123", params.getPaymentIntentData().getTransferData().getDestination());
    }

    @Test
    void createCheckoutSession_WithMissingCreatorId_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
            stripeCheckoutService.createCheckoutSession(null, "John", 2L, 1000L)
        );
    }

    @Test
    void constructor_WithInvalidUrls_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
            new StripeCheckoutService("relative/url", "http://ok.com", creatorRepository, stripeConnectService)
        );
        assertThrows(IllegalArgumentException.class, () ->
            new StripeCheckoutService("http://ok.com", "relative/url", creatorRepository, stripeConnectService)
        );
    }

    @Test
    void createCheckoutSession_WhenStripeFails_ShouldThrowException() throws StripeException {
        // Arrange
        mockedSession.when(() -> Session.create(any(SessionCreateParams.class)))
                .thenThrow(new StripeException("Error", "request-id", "code", 400) {});

        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
            stripeCheckoutService.createCheckoutSession(1L, "John", 2L, 1000L)
        );
    }
}








