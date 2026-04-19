package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.repository.ChargebackCaseRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargebackCorrelationServiceTest {

    @Mock
    private ChargebackCaseRepository chargebackCaseRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private ChargebackCorrelationService correlationService;

    private ChargebackCase chargeback;
    private UUID chargebackId;

    @BeforeEach
    void setUp() {
        chargebackId = UUID.randomUUID();
        chargeback = new ChargebackCase();
        chargeback.setId(chargebackId);
        chargeback.setDeviceFingerprint("fp-123");
        chargeback.setIpAddress("192.168.1.1");
        chargeback.setPaymentMethodFingerprint("pm-456");
        chargeback.setTransactionId(UUID.randomUUID());
    }

    @Test
    void analyze_NoCorrelations_ShouldReturnZero() {
        when(chargebackCaseRepository.findAllByDeviceFingerprint("fp-123")).thenReturn(Collections.singletonList(chargeback));
        when(chargebackCaseRepository.findAllByIpAddress("192.168.1.1")).thenReturn(Collections.singletonList(chargeback));
        when(chargebackCaseRepository.findAllByPaymentMethodFingerprint("pm-456")).thenReturn(Collections.singletonList(chargeback));
        when(paymentRepository.findById(chargeback.getTransactionId())).thenReturn(Optional.empty());

        int score = correlationService.analyze(chargeback);
        assertEquals(0, score);
    }

    @Test
    void analyze_AllCorrelations_ShouldReturnMaxScore() {
        ChargebackCase other = new ChargebackCase();
        other.setId(UUID.randomUUID());

        when(chargebackCaseRepository.findAllByDeviceFingerprint("fp-123")).thenReturn(List.of(chargeback, other));
        when(chargebackCaseRepository.findAllByIpAddress("192.168.1.1")).thenReturn(List.of(chargeback, other));
        when(chargebackCaseRepository.findAllByPaymentMethodFingerprint("pm-456")).thenReturn(List.of(chargeback, other));

        User creator = new User();
        creator.setId(100L);
        Payment payment = new Payment();
        payment.setCreator(creator);
        when(paymentRepository.findById(chargeback.getTransactionId())).thenReturn(Optional.of(payment));
        when(chargebackCaseRepository.findAllByCreatorId(100L)).thenReturn(List.of(chargeback, other));

        int score = correlationService.analyze(chargeback);
        // 30 (device) + 20 (ip) + 40 (payment method) + 10 (creator) = 100
        assertEquals(100, score);
    }

    @Test
    void analyze_PartialCorrelations_ShouldSumCorrectly() {
        ChargebackCase other = new ChargebackCase();
        other.setId(UUID.randomUUID());

        // Only device and IP correlate
        when(chargebackCaseRepository.findAllByDeviceFingerprint("fp-123")).thenReturn(List.of(chargeback, other));
        when(chargebackCaseRepository.findAllByIpAddress("192.168.1.1")).thenReturn(List.of(chargeback, other));
        when(chargebackCaseRepository.findAllByPaymentMethodFingerprint("pm-456")).thenReturn(Collections.singletonList(chargeback));
        when(paymentRepository.findById(chargeback.getTransactionId())).thenReturn(Optional.empty());

        int score = correlationService.analyze(chargeback);
        // 30 (device) + 20 (ip) = 50
        assertEquals(50, score);
    }
}
