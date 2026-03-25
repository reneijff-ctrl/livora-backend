package com.joinlivora.backend.payout.freeze;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutFreezeServiceTest {

    @Mock
    private PayoutFreezeRepository payoutFreezeRepository;

    @Mock
    private PayoutFreezeAuditRepository auditRepository;

    @InjectMocks
    private PayoutFreezeService payoutFreezeService;

    @Test
    void freezeCreator_ShouldCreateNewActiveFreeze() {
        Long creatorId = 123L;
        String reason = "Suspicious activity";
        Long adminId = 1L;

        when(payoutFreezeRepository.findByCreatorIdAndActiveTrue(creatorId))
                .thenReturn(Optional.empty());

        payoutFreezeService.freezeCreator(creatorId, reason, adminId);

        verify(payoutFreezeRepository).save(argThat(freeze -> 
            freeze.getCreatorId().equals(creatorId) &&
            freeze.getReason().equals(reason) &&
            freeze.getCreatedByAdminId().equals(adminId) &&
            freeze.isActive()
        ));

        verify(auditRepository).save(argThat(audit -> 
            audit.getCreatorId().equals(creatorId) &&
            audit.getAction().equals("FREEZE") &&
            audit.getReason().equals(reason) &&
            audit.getAdminId().equals(adminId) &&
            audit.getCreatedAt() != null
        ));
    }

    @Test
    void freezeCreator_ShouldDeactivateExistingFreeze() {
        Long creatorId = 123L;
        PayoutFreeze existing = PayoutFreeze.builder()
                .creatorId(creatorId)
                .active(true)
                .build();

        when(payoutFreezeRepository.findByCreatorIdAndActiveTrue(creatorId))
                .thenReturn(Optional.of(existing));

        payoutFreezeService.freezeCreator(creatorId, "New reason", 1L);

        assertThat(existing.isActive()).isFalse();
        verify(payoutFreezeRepository, atLeastOnce()).save(existing);
        verify(payoutFreezeRepository).save(argThat(PayoutFreeze::isActive));
        
        verify(auditRepository).save(argThat(audit -> 
            audit.getCreatorId().equals(creatorId) &&
            audit.getAction().equals("FREEZE") &&
            audit.getAdminId().equals(1L)
        ));
    }

    @Test
    void unfreezeCreator_ShouldDeactivateActiveFreeze() {
        Long creatorId = 123L;
        PayoutFreeze existing = PayoutFreeze.builder()
                .creatorId(creatorId)
                .active(true)
                .build();

        when(payoutFreezeRepository.findByCreatorIdAndActiveTrue(creatorId))
                .thenReturn(Optional.of(existing));

        payoutFreezeService.unfreezeCreator(creatorId);

        assertThat(existing.isActive()).isFalse();
        verify(payoutFreezeRepository).save(existing);
        
        verify(auditRepository).save(argThat(audit -> 
            audit.getCreatorId().equals(creatorId) &&
            audit.getAction().equals("UNFREEZE") &&
            audit.getReason().equals("Manual unfreeze") &&
            audit.getAdminId().equals(1L) &&
            audit.getCreatedAt() != null
        ));
    }

    @Test
    void findActiveFreeze_ShouldReturnFromRepository() {
        Long creatorId = 123L;
        PayoutFreeze freeze = new PayoutFreeze();
        when(payoutFreezeRepository.findByCreatorIdAndActiveTrue(creatorId))
                .thenReturn(Optional.of(freeze));

        Optional<PayoutFreeze> result = payoutFreezeService.findActiveFreeze(creatorId);

        assertThat(result).isPresent().contains(freeze);
    }
}








