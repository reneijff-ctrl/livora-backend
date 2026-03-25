package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.payout.dto.PayoutHoldOverrideRequest;
import com.joinlivora.backend.payout.dto.PayoutHoldReleaseRequest;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutHoldAdminServiceTest {

    @Mock
    private PayoutHoldPolicyRepository holdPolicyRepository;
    @Mock
    private StripePayoutAdapter stripePayoutAdapter;
    @Mock
    private StripeAccountRepository stripeAccountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorEarningsService creatorEarningsService;
    @Mock
    private PayoutHoldAuditService holdAuditService;

    @InjectMocks
    private PayoutHoldAdminService service;

    private User admin;
    private User creator;
    private UUID creatorSubjectId;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setEmail("admin@test.com");

        creator = new User();
        creator.setId(2L);
        creator.setEmail("creator@test.com");

        creatorSubjectId = new UUID(0L, 2L);
    }

    @Test
    void overrideHold_ShouldSavePolicyAndUpdateStripe() throws Exception {
        PayoutHoldOverrideRequest request = PayoutHoldOverrideRequest.builder()
                .subjectId(creatorSubjectId)
                .subjectType(RiskSubjectType.CREATOR)
                .holdLevel(HoldLevel.LONG)
                .holdDays(30)
                .reason("suspicious")
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(creator));
        StripeAccount stripeAccount = StripeAccount.builder()
                .stripeAccountId("acct_123")
                .build();
        when(stripeAccountRepository.findByUser(creator)).thenReturn(Optional.of(stripeAccount));

        service.overrideHold(request, admin);

        ArgumentCaptor<PayoutHoldPolicy> policyCaptor = ArgumentCaptor.forClass(PayoutHoldPolicy.class);
        verify(holdPolicyRepository).save(policyCaptor.capture());
        
        PayoutHoldPolicy saved = policyCaptor.getValue();
        assertEquals(creatorSubjectId, saved.getSubjectId());
        assertEquals(HoldLevel.LONG, saved.getHoldLevel());
        assertEquals(30, saved.getHoldDays());
        assertTrue(saved.getReason().contains("suspicious"));
        assertTrue(saved.getReason().contains("admin@test.com"));
        assertNotNull(saved.getExpiresAt());

        verify(stripePayoutAdapter).enforceHold("acct_123", 30);
    }

    @Test
    void releaseHold_ShouldExpirePoliciesAndUnlockEarnings() throws Exception {
        PayoutHoldReleaseRequest request = PayoutHoldReleaseRequest.builder()
                .subjectId(creatorSubjectId)
                .subjectType(RiskSubjectType.CREATOR)
                .reason("cleared")
                .build();

        PayoutHoldPolicy activePolicy = PayoutHoldPolicy.builder()
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        
        when(holdPolicyRepository.findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(creatorSubjectId, RiskSubjectType.CREATOR))
                .thenReturn(List.of(activePolicy));
        
        when(userRepository.findById(2L)).thenReturn(Optional.of(creator));
        StripeAccount stripeAccount = StripeAccount.builder()
                .stripeAccountId("acct_123")
                .build();
        when(stripeAccountRepository.findByUser(creator)).thenReturn(Optional.of(stripeAccount));

        service.releaseHold(request, admin);

        verify(holdPolicyRepository).save(activePolicy);
        assertFalse(activePolicy.getExpiresAt().isAfter(Instant.now()));
        
        verify(stripePayoutAdapter).enforceHold("acct_123", 2);
        verify(creatorEarningsService).unlockExpiredEarnings();
    }
}








