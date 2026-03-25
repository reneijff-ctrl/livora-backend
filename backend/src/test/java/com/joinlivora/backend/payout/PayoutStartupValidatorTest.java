package com.joinlivora.backend.payout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutStartupValidatorTest {

    @Mock
    private CreatorPayoutRepository payoutRepository;

    @Mock
    private CreatorEarningRepository earningRepository;

    @Mock
    private PayoutRequestRepository payoutRequestRepository;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PayoutStartupValidator validator;

    @Mock
    private ApplicationReadyEvent event;

    @Test
    void validatePayoutState_ShouldLogCorrectCounts() {
        // Arrange
        when(payoutRepository.countByStatusAndCreatedAtBefore(eq(PayoutStatus.PROCESSING), any(Instant.class)))
                .thenReturn(5L);
        when(payoutRequestRepository.countByStatusAndCreatedAtBefore(eq(PayoutRequestStatus.PENDING), any(Instant.class)))
                .thenReturn(3L);
        when(earningRepository.countByLockedTrueAndPayoutIsNullAndPayoutRequestIsNull())
                .thenReturn(10L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(0);

        // Act
        validator.validatePayoutState();

        // Assert
        verify(payoutRepository).countByStatusAndCreatedAtBefore(eq(PayoutStatus.PROCESSING), any(Instant.class));
        verify(payoutRequestRepository).countByStatusAndCreatedAtBefore(eq(PayoutRequestStatus.PENDING), any(Instant.class));
        verify(earningRepository).countByLockedTrueAndPayoutIsNullAndPayoutRequestIsNull();
    }

    @Test
    void validatePayoutState_ZeroCounts_ShouldStillWork() {
        // Arrange
        when(payoutRepository.countByStatusAndCreatedAtBefore(eq(PayoutStatus.PROCESSING), any(Instant.class)))
                .thenReturn(0L);
        when(payoutRequestRepository.countByStatusAndCreatedAtBefore(eq(PayoutRequestStatus.PENDING), any(Instant.class)))
                .thenReturn(0L);
        when(earningRepository.countByLockedTrueAndPayoutIsNullAndPayoutRequestIsNull())
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(0);

        // Act
        validator.validatePayoutState();

        // Assert
        verify(payoutRepository).countByStatusAndCreatedAtBefore(eq(PayoutStatus.PROCESSING), any(Instant.class));
        verify(payoutRequestRepository).countByStatusAndCreatedAtBefore(eq(PayoutRequestStatus.PENDING), any(Instant.class));
        verify(earningRepository).countByLockedTrueAndPayoutIsNullAndPayoutRequestIsNull();
    }

    @Test
    void validatePayoutState_InconsistentChargebacks_ShouldLogError() {
        // Arrange
        when(payoutRepository.countByStatusAndCreatedAtBefore(any(), any())).thenReturn(0L);
        when(payoutRequestRepository.countByStatusAndCreatedAtBefore(any(), any())).thenReturn(0L);
        when(earningRepository.countByLockedTrueAndPayoutIsNullAndPayoutRequestIsNull()).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(2);

        // Act
        validator.validatePayoutState();

        // Assert
        verify(jdbcTemplate).queryForObject(contains("chargebacks"), eq(Integer.class));
    }
}








