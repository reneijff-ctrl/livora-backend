package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.Chargeback;
import com.joinlivora.backend.fraud.ChargebackStatus;
import com.joinlivora.backend.fraud.repository.ChargebackRepository;
import com.joinlivora.backend.payout.PayoutHoldService;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargebackServiceTest {

    @Mock
    private ChargebackRepository chargebackRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PayoutHoldService payoutHoldService;
    @Mock
    private com.joinlivora.backend.payment.ChargebackRepository paymentChargebackRepository;

    @InjectMocks
    private ChargebackService chargebackService;

    private User user;
    private String stripeChargeId = "ch_123";

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setPayoutsEnabled(true);
        user.setFraudRiskLevel(FraudRiskLevel.LOW);
        user.setTrustScore(100);
        user.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void handleChargebackOpened_FirstChargeback_ShouldSetMediumRisk() {
        com.joinlivora.backend.payment.Chargeback paymentCb = new com.joinlivora.backend.payment.Chargeback();
        paymentCb.setUserId(new UUID(0L, 1L));
        
        when(paymentChargebackRepository.findByStripeChargeId(stripeChargeId)).thenReturn(Optional.of(paymentCb));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        // Mock repository to return the chargeback that will be saved
        List<Chargeback> chargebacks = new ArrayList<>();
        chargebacks.add(Chargeback.builder().status(ChargebackStatus.OPEN).build());
        when(chargebackRepository.findByUser_Id(1L)).thenReturn(chargebacks);

        chargebackService.handleChargebackOpened(stripeChargeId, new BigDecimal("50.00"), "Fraudulent");

        verify(chargebackRepository).save(any(Chargeback.class));
        assertFalse(user.isPayoutsEnabled());
        assertEquals(FraudRiskLevel.MEDIUM, user.getFraudRiskLevel());
        verify(userRepository).save(user);
        verify(payoutHoldService).createHold(eq(user), eq(RiskLevel.MEDIUM), isNull(), contains(stripeChargeId));
    }

    @Test
    void handleChargebackClosed_SecondLost_ShouldSetHighRisk() {
        Chargeback cb1 = Chargeback.builder().stripeChargeId("ch_old").status(ChargebackStatus.LOST).build();
        Chargeback cb2 = Chargeback.builder().stripeChargeId(stripeChargeId).user(user).status(ChargebackStatus.OPEN).build();
        
        when(chargebackRepository.findByStripeChargeId(stripeChargeId)).thenReturn(Optional.of(cb2));
        
        List<Chargeback> chargebacks = new ArrayList<>();
        chargebacks.add(cb1);
        chargebacks.add(cb2); // This one will be updated to LOST
        when(chargebackRepository.findByUser_Id(1L)).thenReturn(chargebacks);

        chargebackService.handleChargebackClosed(stripeChargeId, false);

        assertEquals(ChargebackStatus.LOST, cb2.getStatus());
        assertEquals(FraudRiskLevel.HIGH, user.getFraudRiskLevel());
        assertFalse(user.isPayoutsEnabled());
        verify(userRepository).save(user);
    }

    @Test
    void handleChargebackClosed_ThirdLost_ShouldFreezePayouts() {
        Chargeback cb1 = Chargeback.builder().status(ChargebackStatus.LOST).build();
        Chargeback cb2 = Chargeback.builder().status(ChargebackStatus.LOST).build();
        Chargeback cb3 = Chargeback.builder().stripeChargeId(stripeChargeId).user(user).status(ChargebackStatus.OPEN).build();
        
        when(chargebackRepository.findByStripeChargeId(stripeChargeId)).thenReturn(Optional.of(cb3));
        
        List<Chargeback> chargebacks = List.of(cb1, cb2, cb3);
        when(chargebackRepository.findByUser_Id(1L)).thenReturn(chargebacks);

        chargebackService.handleChargebackClosed(stripeChargeId, false);

        assertEquals(ChargebackStatus.LOST, cb3.getStatus());
        assertEquals(FraudRiskLevel.HIGH, user.getFraudRiskLevel());
        assertEquals(UserStatus.PAYOUTS_FROZEN, user.getStatus());
        assertFalse(user.isPayoutsEnabled());
        verify(userRepository).save(user);
    }

    @Test
    void handleChargebackOpened_UserNotFound_ShouldReturnEarly() {
        when(chargebackRepository.findByStripeChargeId(stripeChargeId)).thenReturn(Optional.empty());
        when(paymentChargebackRepository.findByStripeChargeId(stripeChargeId)).thenReturn(Optional.empty());

        chargebackService.handleChargebackOpened(stripeChargeId, new BigDecimal("50.00"), "Fraudulent");

        verify(chargebackRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void handleChargebackClosed_Won_ShouldRestoreTrust() {
        Chargeback cb = Chargeback.builder()
                .stripeChargeId(stripeChargeId)
                .user(user)
                .status(ChargebackStatus.OPEN)
                .build();
        
        when(chargebackRepository.findByStripeChargeId(stripeChargeId)).thenReturn(Optional.of(cb));
        when(chargebackRepository.findByUser_Id(1L)).thenReturn(List.of(cb));
        user.setTrustScore(80);

        chargebackService.handleChargebackClosed(stripeChargeId, true);

        assertEquals(ChargebackStatus.WON, cb.getStatus());
        assertEquals(100, user.getTrustScore());
        verify(userRepository).save(user);
    }

    @Test
    void getAllChargebacks_ShouldReturnMappedPage() {
        Chargeback cb = Chargeback.builder()
                .user(user)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reason("fraudulent")
                .status(ChargebackStatus.OPEN)
                .createdAt(Instant.now())
                .build();
        
        Pageable pageable = PageRequest.of(0, 20);
        Page<Chargeback> page = new PageImpl<>(List.of(cb));
        
        when(chargebackRepository.findAll(pageable)).thenReturn(page);

        var result = chargebackService.getAllChargebacks(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("test@test.com", result.getContent().get(0).getUserEmail());
        assertEquals(new BigDecimal("100.00"), result.getContent().get(0).getAmount());
        assertEquals(FraudRiskLevel.LOW, result.getContent().get(0).getFraudRisk());
    }
}








