package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.EarningsBreakdownDTO;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorBalanceServiceBreakdownTest {

    @Mock
    private CreatorEarningRepository earningRepository;

    @InjectMocks
    private CreatorBalanceService balanceService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
    }

    @Test
    void getEarningsBreakdown_ShouldCategorizeCorrectly() {
        // 1. Available (not locked)
        CreatorEarning e1 = CreatorEarning.builder()
                .netAmount(new BigDecimal("10.00"))
                .currency("EUR")
                .locked(false)
                .build();

        // 2. Locked by PayoutHold
        PayoutHold hold = PayoutHold.builder().status(PayoutHoldStatus.ACTIVE).build();
        CreatorEarning e2 = CreatorEarning.builder()
                .netAmount(new BigDecimal("20.00"))
                .currency("EUR")
                .locked(true)
                .payoutHold(hold)
                .build();

        // 3. Locked by FraudHold (Policy)
        PayoutHoldPolicy policy = PayoutHoldPolicy.builder().build();
        CreatorEarning e3 = CreatorEarning.builder()
                .netAmount(new BigDecimal("30.00"))
                .currency("EUR")
                .locked(true)
                .holdPolicy(policy)
                .build();

        // 4. Manual Admin Lock (locked but no hold records)
        CreatorEarning e4 = CreatorEarning.builder()
                .netAmount(new BigDecimal("40.00"))
                .currency("EUR")
                .locked(true)
                .build();

        // 5. Token conversion (Available)
        CreatorEarning e5 = CreatorEarning.builder()
                .netAmount(new BigDecimal("1000")) // 10 EUR
                .currency("TOKEN")
                .locked(false)
                .build();

        // 6. Locked by Payout (not one of the 3 categories, but in lockedEarnings)
        CreatorPayout payout = CreatorPayout.builder().id(UUID.randomUUID()).build();
        CreatorEarning e6 = CreatorEarning.builder()
                .netAmount(new BigDecimal("50.00"))
                .currency("EUR")
                .locked(true)
                .payout(payout)
                .build();

        List<CreatorEarning> earnings = Arrays.asList(e1, e2, e3, e4, e5, e6);
        when(earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).thenReturn(earnings);

        EarningsBreakdownDTO result = balanceService.getEarningsBreakdown(creator);

        // Total: 10 + 20 + 30 + 40 + 10 + 50 = 160.00
        assertEquals(6, result.getTotalEarnings().getCount());
        assertEquals(0, new BigDecimal("160.00").compareTo(result.getTotalEarnings().getSum()));

        // Available: e1 (10) + e5 (10) = 20.00
        assertEquals(2, result.getAvailableEarnings().getCount());
        assertEquals(0, new BigDecimal("20.00").compareTo(result.getAvailableEarnings().getSum()));

        // Locked: e2 (20) + e3 (30) + e4 (40) + e6 (50) = 140.00
        assertEquals(4, result.getLockedEarnings().getCount());
        assertEquals(0, new BigDecimal("140.00").compareTo(result.getLockedEarnings().getSum()));

        // LockedBy breakdown
        // PayoutHold: e2 (20)
        assertEquals(1, result.getLockedBy().getPayoutHold().getCount());
        assertEquals(0, new BigDecimal("20.00").compareTo(result.getLockedBy().getPayoutHold().getSum()));

        // FraudHold: e3 (30)
        assertEquals(1, result.getLockedBy().getFraudHold().getCount());
        assertEquals(0, new BigDecimal("30.00").compareTo(result.getLockedBy().getFraudHold().getSum()));

        // ManualAdminLock: e4 (40)
        assertEquals(1, result.getLockedBy().getManualAdminLock().getCount());
        assertEquals(0, new BigDecimal("40.00").compareTo(result.getLockedBy().getManualAdminLock().getSum()));
        
        // PayoutRequested: e6 (50)
        assertEquals(1, result.getLockedBy().getPayoutRequested().getCount());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getLockedBy().getPayoutRequested().getSum()));
    }
}








