package com.joinlivora.backend.fraud;

import com.joinlivora.backend.fraud.repository.ChargebackRepository;
import com.joinlivora.backend.fraud.repository.FraudFlagRepository;
import com.joinlivora.backend.fraud.service.FraudScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudScoreServiceTest {

    @Mock
    private ChargebackRepository chargebackRepository;

    @Mock
    private FraudFlagRepository fraudFlagRepository;

    @InjectMocks
    private FraudScoreService fraudScoreService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = new UUID(0L, 123L);
    }

    @Test
    void calculateFraudScore_ShouldReturnZero_WhenNoFlagsOrChargebacks() {
        when(chargebackRepository.findByUser_Id(123L)).thenReturn(List.of());
        when(fraudFlagRepository.findByUserId(userId)).thenReturn(List.of());

        int score = fraudScoreService.calculateFraudScore(userId);

        assertThat(score).isEqualTo(0);
    }

    @Test
    void calculateFraudScore_ShouldAccumulatePoints_Correctly() {
        // OPEN chargeback: +40
        Chargeback openCb = Chargeback.builder().status(ChargebackStatus.OPEN).build();
        // LOST chargeback: +20
        Chargeback lostCb = Chargeback.builder().status(ChargebackStatus.LOST).build();
        // WON chargeback: +0 (not specified in requirements, assuming 0)
        Chargeback wonCb = Chargeback.builder().status(ChargebackStatus.WON).build();

        // MANUAL flag: +10
        FraudFlag manualFlag = FraudFlag.builder().source(FraudFlagSource.MANUAL).build();
        // SYSTEM flag: +0 (not specified in requirements, assuming 0)
        FraudFlag systemFlag = FraudFlag.builder().source(FraudFlagSource.SYSTEM).build();

        when(chargebackRepository.findByUser_Id(123L)).thenReturn(List.of(openCb, lostCb, wonCb));
        when(fraudFlagRepository.findByUserId(userId)).thenReturn(List.of(manualFlag, systemFlag));

        int score = fraudScoreService.calculateFraudScore(userId);

        // 40 (OPEN) + 20 (LOST) + 10 (MANUAL) = 70
        assertThat(score).isEqualTo(70);
    }

    @Test
    void calculateFraudScore_ShouldCapScoreAt100() {
        Chargeback openCb1 = Chargeback.builder().status(ChargebackStatus.OPEN).build();
        Chargeback openCb2 = Chargeback.builder().status(ChargebackStatus.OPEN).build();
        Chargeback openCb3 = Chargeback.builder().status(ChargebackStatus.OPEN).build();

        when(chargebackRepository.findByUser_Id(123L)).thenReturn(List.of(openCb1, openCb2, openCb3));
        when(fraudFlagRepository.findByUserId(userId)).thenReturn(List.of());

        int score = fraudScoreService.calculateFraudScore(userId);

        // 40 * 3 = 120 -> capped at 100
        assertThat(score).isEqualTo(100);
    }
}








