package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorBalanceServiceTest {

    @Mock
    private CreatorEarningRepository earningRepository;

    @InjectMocks
    private CreatorBalanceService balanceService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
    }

    @Test
    void getAvailableBalance_NoHolds_Included() {
        CreatorEarning e1 = CreatorEarning.builder()
                .currency("USD")
                .netAmount(new BigDecimal("100.00"))
                .locked(false)
                .payoutHold(null)
                .build();

        when(earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).thenReturn(Collections.singletonList(e1));

        Map<String, BigDecimal> balance = balanceService.getAvailableBalance(creator);

        assertEquals(new BigDecimal("100.00"), balance.get("USD"));
    }

    @Test
    void getAvailableBalance_ActiveHold_Excluded() {
        PayoutHold hold = PayoutHold.builder()
                .status(PayoutHoldStatus.ACTIVE)
                .build();
        
        CreatorEarning e1 = CreatorEarning.builder()
                .currency("USD")
                .netAmount(new BigDecimal("100.00"))
                .locked(false)
                .payoutHold(hold)
                .build();

        when(earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).thenReturn(Collections.singletonList(e1));

        Map<String, BigDecimal> balance = balanceService.getAvailableBalance(creator);

        assertTrue(balance.isEmpty() || balance.get("USD") == null);
    }

    @Test
    void getAvailableBalance_ReleasedHold_Included() {
        PayoutHold hold = PayoutHold.builder()
                .status(PayoutHoldStatus.RELEASED)
                .build();
        
        CreatorEarning e1 = CreatorEarning.builder()
                .currency("USD")
                .netAmount(new BigDecimal("100.00"))
                .locked(false)
                .payoutHold(hold)
                .build();

        when(earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).thenReturn(Collections.singletonList(e1));

        Map<String, BigDecimal> balance = balanceService.getAvailableBalance(creator);

        assertEquals(new BigDecimal("100.00"), balance.get("USD"));
    }

    @Test
    void getAvailableBalance_CancelledHold_Excluded() {
        PayoutHold hold = PayoutHold.builder()
                .status(PayoutHoldStatus.CANCELLED)
                .build();
        
        CreatorEarning e1 = CreatorEarning.builder()
                .currency("USD")
                .netAmount(new BigDecimal("100.00"))
                .locked(false)
                .payoutHold(hold)
                .build();

        when(earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).thenReturn(Collections.singletonList(e1));

        Map<String, BigDecimal> balance = balanceService.getAvailableBalance(creator);

        assertTrue(balance.isEmpty() || balance.get("USD") == null);
    }

    @Test
    void getAvailableBalance_Locked_Excluded() {
        CreatorEarning e1 = CreatorEarning.builder()
                .currency("USD")
                .netAmount(new BigDecimal("100.00"))
                .locked(true)
                .payoutHold(null)
                .build();

        when(earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).thenReturn(Collections.singletonList(e1));

        Map<String, BigDecimal> balance = balanceService.getAvailableBalance(creator);

        assertTrue(balance.isEmpty());
    }

    @Test
    void getAvailableBalance_GroupedByCurrency() {
        CreatorEarning e1 = CreatorEarning.builder()
                .currency("USD")
                .netAmount(new BigDecimal("100.00"))
                .locked(false)
                .build();
        CreatorEarning e2 = CreatorEarning.builder()
                .currency("USD")
                .netAmount(new BigDecimal("50.00"))
                .locked(false)
                .build();
        CreatorEarning e3 = CreatorEarning.builder()
                .currency("EUR")
                .netAmount(new BigDecimal("30.00"))
                .locked(false)
                .build();

        when(earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)).thenReturn(Arrays.asList(e1, e2, e3));

        Map<String, BigDecimal> balance = balanceService.getAvailableBalance(creator);

        assertEquals(new BigDecimal("150.00"), balance.get("USD"));
        assertEquals(new BigDecimal("30.00"), balance.get("EUR"));
    }
}








