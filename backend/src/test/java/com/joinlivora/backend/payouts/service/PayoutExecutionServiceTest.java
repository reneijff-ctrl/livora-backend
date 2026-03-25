package com.joinlivora.backend.payouts.service;

import com.joinlivora.backend.payout.CreatorPayout;
import com.joinlivora.backend.payout.PayoutService;
import com.joinlivora.backend.payouts.exception.PayoutFrozenException;
import com.joinlivora.backend.payouts.model.CreatorAccount;
import com.joinlivora.backend.payouts.repository.CreatorAccountRepository;
import com.joinlivora.backend.aml.service.AMLRulesEngine;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutExecutionServiceTest {

    @Mock
    private CreatorAccountRepository creatorAccountRepository;

    @Mock
    private PayoutService payoutService;

    @Mock
    private AMLRulesEngine amlRulesEngine;

    @Mock
    private UserRepository userRepository;
    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @InjectMocks
    private PayoutExecutionService payoutExecutionService;

    private UUID creatorId;
    private User user;
    private BigDecimal amount;
    private String currency;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        user = new User();
        user.setId(creatorId.getLeastSignificantBits());
        amount = new BigDecimal("100.00");
        currency = "EUR";

        lenient().when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));
    }

    @Test
    void executePayout_WhenFrozen_ShouldThrowException() throws Exception {
        CreatorAccount account = CreatorAccount.builder()
                .creatorId(creatorId)
                .payoutFrozen(true)
                .build();

        when(creatorAccountRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(account));

        assertThrows(PayoutFrozenException.class, () ->
                payoutExecutionService.executePayout(creatorId, amount, currency));

        verify(amlRulesEngine).evaluateRules(user, amount);
        verify(payoutService, never()).executePayout(any(), any(), any());
    }

    @Test
    void executePayout_WhenNotFrozen_ShouldDelegateToPayoutService() throws Exception {
        CreatorAccount account = CreatorAccount.builder()
                .creatorId(creatorId)
                .payoutFrozen(false)
                .build();

        CreatorPayout expectedPayout = mock(CreatorPayout.class);

        when(creatorAccountRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(account));
        when(payoutService.executePayout(creatorId, amount, currency)).thenReturn(expectedPayout);

        CreatorPayout result = payoutExecutionService.executePayout(creatorId, amount, currency);

        assertEquals(expectedPayout, result);
        verify(amlRulesEngine).evaluateRules(user, amount);
        verify(payoutService).executePayout(creatorId, amount, currency);
    }

    @Test
    void executePayout_WhenAccountNotFound_ShouldDelegateToPayoutService() throws Exception {
        CreatorPayout expectedPayout = mock(CreatorPayout.class);

        when(creatorAccountRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());
        when(payoutService.executePayout(creatorId, amount, currency)).thenReturn(expectedPayout);

        CreatorPayout result = payoutExecutionService.executePayout(creatorId, amount, currency);

        assertEquals(expectedPayout, result);
        verify(amlRulesEngine).evaluateRules(user, amount);
        verify(payoutService).executePayout(creatorId, amount, currency);
    }
}








