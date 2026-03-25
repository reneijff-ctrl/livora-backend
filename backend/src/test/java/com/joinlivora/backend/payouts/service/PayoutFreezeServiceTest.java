package com.joinlivora.backend.payouts.service;

import com.joinlivora.backend.payouts.model.CreatorAccount;
import com.joinlivora.backend.payouts.model.PayoutFreezeHistory;
import com.joinlivora.backend.payouts.repository.CreatorAccountRepository;
import com.joinlivora.backend.payouts.repository.PayoutFreezeHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutFreezeServiceTest {

    @Mock
    private CreatorAccountRepository creatorAccountRepository;

    @Mock
    private PayoutFreezeHistoryRepository historyRepository;

    @Mock
    private com.joinlivora.backend.user.UserRepository userRepository;

    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @InjectMocks
    private PayoutFreezeService payoutFreezeService;

    private UUID creatorId;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
    }

    @Test
    void freezeCreator_ShouldUpdateAccountAndSaveHistory() {
        String reason = "Suspicious activity";
        String triggeredBy = "SYSTEM";
        
        when(creatorAccountRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());

        payoutFreezeService.freezeCreator(creatorId, reason, triggeredBy);

        ArgumentCaptor<CreatorAccount> accountCaptor = ArgumentCaptor.forClass(CreatorAccount.class);
        verify(creatorAccountRepository).save(accountCaptor.capture());
        CreatorAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.isPayoutFrozen()).isTrue();
        assertThat(savedAccount.getFreezeReason()).isEqualTo(reason);
        assertThat(savedAccount.getFrozenAt()).isNotNull();

        ArgumentCaptor<PayoutFreezeHistory> historyCaptor = ArgumentCaptor.forClass(PayoutFreezeHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        PayoutFreezeHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getCreatorId()).isEqualTo(creatorId);
        assertThat(savedHistory.getReason()).contains(reason);
        assertThat(savedHistory.getTriggeredBy()).isEqualTo(triggeredBy);
    }

    @Test
    void unfreezeCreator_ShouldUpdateAccount() {
        CreatorAccount existingAccount = CreatorAccount.builder()
                .creatorId(creatorId)
                .payoutFrozen(true)
                .freezeReason("Previous type")
                .build();
        
        when(creatorAccountRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(existingAccount));

        payoutFreezeService.unfreezeCreator(creatorId, "ADMIN");

        verify(creatorAccountRepository).save(existingAccount);
        assertThat(existingAccount.isPayoutFrozen()).isFalse();
        assertThat(existingAccount.getFreezeReason()).isNull();
        assertThat(existingAccount.getFrozenAt()).isNull();
        
        verify(historyRepository, never()).save(any());
    }

    @Test
    void unfreezeCreator_WhenAccountNotFound_ShouldDoNothing() {
        when(creatorAccountRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());

        payoutFreezeService.unfreezeCreator(creatorId, "ADMIN");

        verify(creatorAccountRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }
}








