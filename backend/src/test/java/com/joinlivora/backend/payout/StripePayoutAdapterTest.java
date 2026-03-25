package com.joinlivora.backend.payout;

import com.joinlivora.backend.exception.PayoutRestrictedException;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.param.AccountUpdateParams;
import com.stripe.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripePayoutAdapterTest {

    @Mock
    private StripeClient stripeClient;

    @Mock
    private AccountService accountService;

    @Mock
    private PayoutHoldService payoutHoldService;

    @InjectMocks
    private StripePayoutAdapter adapter;

    private final String STRIPE_ACCOUNT_ID = "acct_123";
    private final UUID SUBJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(stripeClient.accounts()).thenReturn(accountService);
    }

    @Test
    void enforceHold_ShouldUpdateStripeAccount() throws StripeException {
        int holdDays = 7;
        
        adapter.enforceHold(STRIPE_ACCOUNT_ID, holdDays);

        ArgumentCaptor<AccountUpdateParams> captor = ArgumentCaptor.forClass(AccountUpdateParams.class);
        verify(accountService).update(eq(STRIPE_ACCOUNT_ID), captor.capture());

        AccountUpdateParams params = captor.getValue();
        assertNotNull(params);
    }

    @Test
    void validateNoActiveHold_NoPolicies_ShouldSucceed() {
        when(payoutHoldService.getPayoutHoldStatus(SUBJECT_ID))
                .thenReturn(com.joinlivora.backend.payout.dto.PayoutHoldStatusDTO.builder()
                        .holdLevel(HoldLevel.NONE)
                        .build());

        assertDoesNotThrow(() -> adapter.validateNoActiveHold(SUBJECT_ID, RiskSubjectType.CREATOR));
    }

    @Test
    void validateNoActiveHold_ActivePolicy_ShouldThrowException() {
        when(payoutHoldService.getPayoutHoldStatus(SUBJECT_ID))
                .thenReturn(com.joinlivora.backend.payout.dto.PayoutHoldStatusDTO.builder()
                        .holdLevel(HoldLevel.MEDIUM)
                        .unlockDate(Instant.now().plus(1, ChronoUnit.DAYS))
                        .reason("Suspicious activity")
                        .build());

        PayoutRestrictedException ex = assertThrows(PayoutRestrictedException.class, 
                () -> adapter.validateNoActiveHold(SUBJECT_ID, RiskSubjectType.CREATOR));
        
        assertTrue(ex.getMessage().contains("Suspicious activity"));
    }
}










