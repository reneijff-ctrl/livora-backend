package com.joinlivora.backend.payout;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payout.dto.AdminPayoutDetailDTO;
import com.joinlivora.backend.payout.dto.PayoutOverrideRequest;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPayoutServiceTest {

    @Mock
    private CreatorPayoutRepository payoutRepository;
    @Mock
    private CreatorEarningRepository earningRepository;
    @Mock
    private PayoutHoldRepository holdRepository;
    @Mock
    private StripePayoutService stripePayoutService;
    @Mock
    private AuditService auditService;
    @Mock
    private PayoutAuditService payoutAuditService;
    @Mock
    private PayoutAuditLogRepository auditLogRepository;
    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;

    @InjectMocks
    private AdminPayoutService adminPayoutService;

    private UUID payoutId;
    private UUID creatorId;
    private CreatorPayout payout;
    private User admin;

    @BeforeEach
    void setUp() {
        payoutId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        payout = CreatorPayout.builder()
                .id(payoutId)
                .creatorId(creatorId)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .status(PayoutStatus.FAILED)
                .build();

        admin = new User();
        admin.setId(999L);
        admin.setEmail("admin@test.com");
    }

    @Test
    void getPayoutDetails_Success_ShouldReturnFullDTO() {
        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));

        User user = new User();
        user.setEmail("creator@test.com");
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder().id(creatorId).user(user).build();
        when(creatorProfileRepository.findById(creatorId)).thenReturn(Optional.of(profile));

        CreatorEarning earning = CreatorEarning.builder()
                .id(UUID.randomUUID())
                .netAmount(new BigDecimal("100.00"))
                .currency("EUR")
                .sourceType(EarningSource.TIP)
                .payoutHold(PayoutHold.builder().id(UUID.randomUUID()).reason("Risk").status(PayoutHoldStatus.ACTIVE).build())
                .createdAt(Instant.now())
                .build();
        when(earningRepository.findAllByPayout(payout)).thenReturn(Collections.singletonList(earning));

        PayoutAuditLog log = PayoutAuditLog.builder()
                .id(UUID.randomUUID())
                .payoutId(payoutId)
                .actorType(PayoutActorType.SYSTEM)
                .action("CREATED")
                .newStatus(PayoutStatus.PENDING)
                .message("Initial request")
                .createdAt(Instant.now())
                .build();
        when(auditLogRepository.findAllByPayoutIdOrderByCreatedAtDesc(payoutId)).thenReturn(Collections.singletonList(log));

        AdminPayoutDetailDTO details = adminPayoutService.getPayoutDetails(payoutId);

        assertEquals(payoutId, details.getId());
        assertEquals("creator@test.com", details.getCreatorEmail());
        assertEquals(1, details.getEarnings().size());
        assertEquals(1, details.getHolds().size());
        assertEquals(1, details.getAuditLogs().size());
        assertEquals("CREATED", details.getAuditLogs().get(0).getAction());
        assertEquals("TIP", details.getEarnings().get(0).getSourceType());
    }

    @Test
    void overridePayout_ReleaseHold_ShouldUpdateHolds() {
        PayoutOverrideRequest request = PayoutOverrideRequest.builder()
                .releaseHold(true)
                .adminNote("Releasing hold")
                .build();

        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        
        PayoutHold activeHold = PayoutHold.builder()
                .id(UUID.randomUUID())
                .status(PayoutHoldStatus.ACTIVE)
                .build();
        when(holdRepository.findAllByUserIdOrderByCreatedAtDesc(creatorId))
                .thenReturn(Collections.singletonList(activeHold));

        adminPayoutService.overridePayout(payoutId, request, admin, "127.0.0.1", "agent");

        assertEquals(PayoutHoldStatus.RELEASED, activeHold.getStatus());
        verify(holdRepository).save(activeHold);
        verify(auditService).logEvent(any(), eq(AuditService.PAYOUT_OVERRIDE), anyString(), eq(payoutId), any(), anyString(), anyString());
    }

    @Test
    void overridePayout_RelockEarnings_ShouldLockEarnings() {
        PayoutOverrideRequest request = PayoutOverrideRequest.builder()
                .relockEarnings(true)
                .build();

        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        
        CreatorEarning earning = CreatorEarning.builder().locked(false).build();
        when(earningRepository.findAllByPayout(payout)).thenReturn(Collections.singletonList(earning));

        adminPayoutService.overridePayout(payoutId, request, admin, "127.0.0.1", "agent");

        assertTrue(earning.isLocked());
        verify(earningRepository).saveAll(anyList());
    }

    @Test
    void overridePayout_ForcePayout_ShouldUpdateStatusAndTrigger() {
        PayoutOverrideRequest request = PayoutOverrideRequest.builder()
                .forcePayout(true)
                .build();

        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        when(earningRepository.findAllByPayout(payout)).thenReturn(Collections.emptyList());

        adminPayoutService.overridePayout(payoutId, request, admin, "127.0.0.1", "agent");

        assertEquals(PayoutStatus.PENDING, payout.getStatus());
        verify(payoutRepository).save(payout);
        verify(stripePayoutService).triggerPayout(payoutId);
    }

    @Test
    void overridePayout_CompleteOverride_ShouldPerformAllActions() {
        PayoutOverrideRequest request = PayoutOverrideRequest.builder()
                .releaseHold(true)
                .relockEarnings(true)
                .forcePayout(true)
                .adminNote("Force it all")
                .build();

        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        
        PayoutHold activeHold = PayoutHold.builder().status(PayoutHoldStatus.ACTIVE).build();
        when(holdRepository.findAllByUserIdOrderByCreatedAtDesc(creatorId)).thenReturn(Collections.singletonList(activeHold));
        
        CreatorEarning earning = CreatorEarning.builder().locked(false).build();
        when(earningRepository.findAllByPayout(payout)).thenReturn(Collections.singletonList(earning));

        adminPayoutService.overridePayout(payoutId, request, admin, "127.0.0.1", "agent");

        assertEquals(PayoutHoldStatus.RELEASED, activeHold.getStatus());
        assertTrue(earning.isLocked());
        assertEquals(PayoutStatus.PENDING, payout.getStatus());
        
        verify(holdRepository).save(activeHold);
        verify(earningRepository, atLeastOnce()).saveAll(anyList());
        verify(payoutRepository).save(payout);
        verify(stripePayoutService).triggerPayout(payoutId);
        verify(auditService).logEvent(eq(new UUID(0L, admin.getId())), eq(AuditService.PAYOUT_OVERRIDE), eq("PAYOUT"), eq(payoutId), any(), eq("127.0.0.1"), eq("agent"));
    }
}









