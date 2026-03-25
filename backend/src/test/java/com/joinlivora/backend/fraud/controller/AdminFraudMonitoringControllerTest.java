package com.joinlivora.backend.fraud.controller;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.fraud.dto.AdminFraudActionRequest;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RuleFraudSignal;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.service.EnforcementService;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminFraudMonitoringControllerTest {

    @Mock
    private RuleFraudSignalRepository fraudSignalRepository;

    @Mock
    private FraudDetectionService fraudDetectionService;

    @Mock
    private EnforcementService enforcementService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private UserDetails adminDetails;

    @InjectMocks
    private AdminFraudMonitoringController controller;

    @Test
    @SuppressWarnings("unchecked")
    void getSignals_ShouldReturnFilteredData() {
        UUID userId = UUID.randomUUID();
        RuleFraudSignal signal = RuleFraudSignal.builder()
                .id(UUID.randomUUID())
                .userId(userId.getLeastSignificantBits())
                .riskLevel(FraudDecisionLevel.HIGH)
                .reason("Fraudulent activity")
                .createdAt(Instant.now())
                .build();
        
        Page<RuleFraudSignal> page = new PageImpl<>(List.of(signal));
        when(fraudSignalRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        ResponseEntity<Page<RuleFraudSignal>> response = controller.getSignals(
                FraudDecisionLevel.HIGH,
                userId,
                Instant.now(),
                Instant.now(),
                PageRequest.of(0, 20)
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals(FraudDecisionLevel.HIGH, response.getBody().getContent().get(0).getRiskLevel());
    }

    @Test
    void approve_ShouldResolveSignal() {
        UUID signalId = UUID.randomUUID();
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        
        RuleFraudSignal signal = new RuleFraudSignal();
        signal.setId(signalId);
        signal.setUserId(2L);
        
        when(adminDetails.getUsername()).thenReturn("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);
        when(fraudSignalRepository.findById(signalId)).thenReturn(Optional.of(signal));
        
        AdminFraudActionRequest request = new AdminFraudActionRequest("Legitimate creator");
        
        ResponseEntity<Void> response = controller.approve(signalId, request, adminDetails, httpRequest);
        
        assertEquals(200, response.getStatusCode().value());
        assertTrue(signal.isResolved());
        assertEquals("Legitimate creator", signal.getActionReason());
        verify(fraudSignalRepository).save(signal);
        verify(auditService).logEvent(any(), eq("FRAUD_SIGNAL_APPROVED"), any(), eq(signalId), any(), any(), any());
    }

    @Test
    void blockUser_ShouldSuspendAndResolve() {
        UUID signalId = UUID.randomUUID();
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        
        RuleFraudSignal signal = new RuleFraudSignal();
        signal.setId(signalId);
        signal.setUserId(2L);
        
        when(adminDetails.getUsername()).thenReturn("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);
        when(fraudSignalRepository.findById(signalId)).thenReturn(Optional.of(signal));
        
        AdminFraudActionRequest request = new AdminFraudActionRequest("Confirmed fraud");
        
        ResponseEntity<Void> response = controller.blockUser(signalId, request, adminDetails, httpRequest);
        
        assertEquals(200, response.getStatusCode().value());
        assertTrue(signal.isResolved());
        verify(enforcementService).suspendAccount(eq(new UUID(0L, 2L)), eq("Confirmed fraud"), any(), any(), any(), eq("admin@test.com"), eq("ADMIN"), any(), any(), any());
        verify(fraudSignalRepository).save(signal);
    }

    @Test
    void removeRestrictions_ShouldUnblockAndResolve() {
        UUID signalId = UUID.randomUUID();
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        
        User user = new User();
        user.setId(2L);
        user.setStatus(UserStatus.SUSPENDED);
        
        RuleFraudSignal signal = new RuleFraudSignal();
        signal.setId(signalId);
        signal.setUserId(2L);
        
        when(adminDetails.getUsername()).thenReturn("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(admin);
        when(fraudSignalRepository.findById(signalId)).thenReturn(Optional.of(signal));
        when(userService.getById(2L)).thenReturn(user);
        
        AdminFraudActionRequest request = new AdminFraudActionRequest("Mistaken identity");
        
        ResponseEntity<Void> response = controller.removeRestrictions(signalId, request, adminDetails, httpRequest);
        
        assertEquals(200, response.getStatusCode().value());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.isPayoutsEnabled());
        verify(fraudDetectionService).unblockUser(user, admin);
        verify(userService).updateUser(user);
        verify(fraudSignalRepository).save(signal);
    }
}








