package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.CreatorEarningsSummary;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorEarningsServiceSummaryTest {

    @Mock
    private PayoutCreatorEarningsRepository payoutCreatorEarningsRepository;

    @Mock
    private PayoutRepository payoutRepository;

    @Mock
    private CreatorEarningRepository creatorEarningRepository;

    @InjectMocks
    private CreatorEarningsService creatorEarningsService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setEmail("creator@test.com");
    }

    @Test
    void getEarningsSummary_WhenEarningsExist_ShouldReturnSummary() {
        BigDecimal total = new BigDecimal("100.00");
        BigDecimal pending = new BigDecimal("30.00");
        BigDecimal available = new BigDecimal("70.00");

        com.joinlivora.backend.payout.CreatorEarnings earnings = com.joinlivora.backend.payout.CreatorEarnings.builder()
                .creator(creator)
                .totalEarned(total)
                .pendingBalance(pending)
                .availableBalance(available)
                .build();

        when(payoutCreatorEarningsRepository.findByCreator(creator)).thenReturn(java.util.Optional.of(earnings));
        when(payoutRepository.findAllByUserOrderByCreatedAtDesc(creator)).thenReturn(java.util.Collections.emptyList());
        when(creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).thenReturn(java.util.Collections.emptyList());

        CreatorEarningsSummary summary = creatorEarningsService.getEarningsSummary(creator);

        assertEquals(total, summary.getTotalEarned());
        assertEquals(pending, summary.getPendingBalance());
        assertEquals(available, summary.getAvailableBalance());
        assertEquals(BigDecimal.ZERO, summary.getMonthEarnings());
        org.junit.jupiter.api.Assertions.assertNull(summary.getLastPayoutDate());
    }

    @Test
    void getEarningsSummary_WhenNoEarningsExist_ShouldReturnZeros() {
        when(payoutCreatorEarningsRepository.findByCreator(creator)).thenReturn(java.util.Optional.empty());
        when(payoutRepository.findAllByUserOrderByCreatedAtDesc(creator)).thenReturn(java.util.Collections.emptyList());
        when(creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).thenReturn(java.util.Collections.emptyList());

        CreatorEarningsSummary summary = creatorEarningsService.getEarningsSummary(creator);

        assertEquals(BigDecimal.ZERO, summary.getTotalEarned());
        assertEquals(BigDecimal.ZERO, summary.getPendingBalance());
        assertEquals(BigDecimal.ZERO, summary.getAvailableBalance());
        assertEquals(BigDecimal.ZERO, summary.getMonthEarnings());
        org.junit.jupiter.api.Assertions.assertNull(summary.getLastPayoutDate());
    }
}








