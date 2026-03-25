package com.joinlivora.backend.abuse;

import com.joinlivora.backend.abuse.model.AbuseEventType;
import com.joinlivora.backend.abuse.repository.AbuseEventRepository;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.fraud.model.FraudRiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbuseDetectionServiceTest {

    @Mock
    private AbuseEventRepository abuseEventRepository;

    @Mock
    private FraudScoringService fraudRiskService;

    @Mock
    private com.joinlivora.backend.monetization.TipRepository tipRepository;

    @Mock
    private com.joinlivora.backend.analytics.AnalyticsEventRepository analyticsEventRepository;

    @InjectMocks
    private AbuseDetectionService abuseDetectionService;

    private UUID userId;
    private String ipAddress;

    @BeforeEach
    void setUp() {
        userId = new UUID(0L, 12345L);
        ipAddress = "192.168.1.1";
    }

    @Test
    void trackEvent_ShouldSaveEvent() {
        abuseDetectionService.trackEvent(userId, ipAddress, AbuseEventType.RAPID_TIPPING, "Test description");

        verify(abuseEventRepository).save(any());
    }

    @Test
    void trackEvent_WhenThresholdExceeded_ShouldEscalateAndSoftBlock() {
        // Given
        when(abuseEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(userId), eq(AbuseEventType.RAPID_TIPPING), any()))
                .thenReturn(10L); // Limit is 10

        // When
        abuseDetectionService.trackEvent(userId, ipAddress, AbuseEventType.RAPID_TIPPING, "Rapid tipping detected");

        // Then
        verify(fraudRiskService).recordDecision(eq(userId), isNull(), isNull(), argThat(result -> 
                result.level() == FraudRiskLevel.HIGH && 
                result.reasons().get(0).contains("RAPID_TIPPING")
        ));
        
        assertTrue(abuseDetectionService.isSoftBlocked(userId, ipAddress));
    }

    @Test
    void trackEvent_WhenThresholdNotExceeded_ShouldNotEscalate() {
        // Given
        when(abuseEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(userId), eq(AbuseEventType.RAPID_TIPPING), any()))
                .thenReturn(5L); // Limit is 10

        // When
        abuseDetectionService.trackEvent(userId, ipAddress, AbuseEventType.RAPID_TIPPING, "Some tipping");

        // Then
        verify(fraudRiskService, never()).recordDecision(any(), any(), any(), any());
        assertFalse(abuseDetectionService.isSoftBlocked(userId, ipAddress));
    }

    @Test
    void isSoftBlocked_WhenUserBlocked_ShouldReturnTrue() {
        // Given
        when(abuseEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(userId), any(), any()))
                .thenReturn(100L);
        abuseDetectionService.trackEvent(userId, ipAddress, AbuseEventType.MESSAGE_SPAM, "Spam");

        // Then
        assertTrue(abuseDetectionService.isSoftBlocked(userId, null));
        assertTrue(abuseDetectionService.isSoftBlocked(null, ipAddress));
        assertTrue(abuseDetectionService.isSoftBlocked(userId, ipAddress));
    }

    @Test
    void trackEvent_IpOnly_ShouldSoftBlockIp() {
        // Given
        when(abuseEventRepository.countByIpAddressAndEventTypeAndCreatedAtAfter(eq(ipAddress), eq(AbuseEventType.LOGIN_BRUTE_FORCE), any()))
                .thenReturn(10L);

        // When
        abuseDetectionService.trackEvent(null, ipAddress, AbuseEventType.LOGIN_BRUTE_FORCE, "Brute force");

        // Then
        verify(fraudRiskService, never()).recordDecision(any(), any(), any(), any());
        assertTrue(abuseDetectionService.isSoftBlocked(null, ipAddress));
    }

    @Test
    void checkRapidTipping_WhenExceeded_ShouldRecordEventAndPoints() {
        // Given
        when(tipRepository.countBySenderUserId_IdAndCreatedAtAfter(eq(userId.getLeastSignificantBits()), any()))
                .thenReturn(6L);

        // When
        abuseDetectionService.checkRapidTipping(userId, ipAddress);

        // Then
        verify(abuseEventRepository).save(argThat(event -> 
                event.getUserId().equals(userId) && 
                event.getEventType() == AbuseEventType.RAPID_TIPPING &&
                event.getDescription().contains("More than 5 tips")
        ));
        
        verify(fraudRiskService).recordDecision(eq(userId), isNull(), isNull(), argThat(result -> 
                result.score() == 20 &&
                result.reasons().get(0).contains("RAPID_TIPPING")
        ));
    }

    @Test
    void checkRapidTipping_WhenNotExceeded_ShouldDoNothing() {
        // Given
        when(tipRepository.countBySenderUserId_IdAndCreatedAtAfter(eq(userId.getLeastSignificantBits()), any()))
                .thenReturn(3L);

        // When
        abuseDetectionService.checkRapidTipping(userId, ipAddress);

        // Then
        verifyNoInteractions(abuseEventRepository);
        verify(fraudRiskService, never()).recordDecision(any(), any(), any(), any());
    }

    @Test
    void checkMessageSpam_WhenExceeded_ShouldRecordEventAndPoints() {
        // Given
        when(analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(userId.getLeastSignificantBits()), eq(com.joinlivora.backend.analytics.AnalyticsEventType.CHAT_MESSAGE_SENT), any()))
                .thenReturn(11L);

        // When
        abuseDetectionService.checkMessageSpam(userId, ipAddress);

        // Then
        verify(abuseEventRepository).save(argThat(event -> 
                event.getUserId().equals(userId) && 
                event.getEventType() == AbuseEventType.MESSAGE_SPAM &&
                event.getDescription().contains("More than 10 messages")
        ));
        
        verify(fraudRiskService).recordDecision(eq(userId), isNull(), isNull(), argThat(result -> 
                result.score() == 10 &&
                result.reasons().get(0).contains("MESSAGE_SPAM")
        ));
    }

    @Test
    void checkMessageSpam_WhenNotExceeded_ShouldDoNothing() {
        // Given
        when(analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(userId.getLeastSignificantBits()), any(), any()))
                .thenReturn(5L);

        // When
        abuseDetectionService.checkMessageSpam(userId, ipAddress);

        // Then
        verifyNoInteractions(abuseEventRepository);
        verify(fraudRiskService, never()).recordDecision(any(), any(), any(), any());
    }

    @Test
    void checkLoginBruteForce_WhenExceeded_ShouldRecordEventAndPoints() {
        // Given
        when(analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(userId.getLeastSignificantBits()), eq(com.joinlivora.backend.analytics.AnalyticsEventType.USER_LOGIN_FAILED), any()))
                .thenReturn(5L);

        // When
        abuseDetectionService.checkLoginBruteForce(userId, ipAddress);

        // Then
        verify(abuseEventRepository).save(argThat(event ->
                event.getUserId().equals(userId) &&
                event.getEventType() == AbuseEventType.LOGIN_BRUTE_FORCE &&
                event.getDescription().contains("5 or more failed logins")
        ));

        verify(fraudRiskService).recordDecision(eq(userId), isNull(), isNull(), argThat(result ->
                result.score() == 40 &&
                result.level() == FraudRiskLevel.MEDIUM &&
                result.reasons().get(0).contains("LOGIN_BRUTE_FORCE")
        ));
    }

    @Test
    void checkLoginBruteForce_WhenNotExceeded_ShouldDoNothing() {
        // Given
        when(analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(userId.getLeastSignificantBits()), any(), any()))
                .thenReturn(4L);

        // When
        abuseDetectionService.checkLoginBruteForce(userId, ipAddress);

        // Then
        verifyNoInteractions(abuseEventRepository);
        verify(fraudRiskService, never()).recordDecision(any(), any(), any(), any());
    }

    @Test
    void checkRapidTipping_WithRandomUUID_ShouldThrowException() {
        UUID randomId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> 
            abuseDetectionService.checkRapidTipping(randomId, ipAddress)
        );
    }
}








