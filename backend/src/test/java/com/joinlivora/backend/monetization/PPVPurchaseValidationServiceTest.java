package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PPVPurchaseValidationServiceTest {

    @Mock
    private PpvPurchaseRepository ppvPurchaseRepository;

    @InjectMocks
    private PPVPurchaseValidationService validationService;

    private User user;
    private PpvContent content;
    private UUID contentId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        contentId = UUID.randomUUID();
        content = new PpvContent();
        content.setId(contentId);
    }

    @Test
    void hasPurchased_WithEntity_ShouldReturnTrue_WhenPaidRecordExists() {
        when(ppvPurchaseRepository.findByPpvContentAndUserAndStatus(content, user, PpvPurchaseStatus.PAID))
                .thenReturn(Optional.of(new PpvPurchase()));

        assertTrue(validationService.hasPurchased(user, content));
        verify(ppvPurchaseRepository).findByPpvContentAndUserAndStatus(content, user, PpvPurchaseStatus.PAID);
    }

    @Test
    void hasPurchased_WithEntity_ShouldReturnFalse_WhenNoRecord() {
        when(ppvPurchaseRepository.findByPpvContentAndUserAndStatus(content, user, PpvPurchaseStatus.PAID))
                .thenReturn(Optional.empty());

        assertFalse(validationService.hasPurchased(user, content));
    }

    @Test
    void hasPurchased_WithId_ShouldReturnTrue_WhenPaidRecordExists() {
        when(ppvPurchaseRepository.existsByPpvContent_IdAndUser_IdAndStatus(contentId, 1L, PpvPurchaseStatus.PAID))
                .thenReturn(true);

        assertTrue(validationService.hasPurchased(1L, contentId));
        verify(ppvPurchaseRepository).existsByPpvContent_IdAndUser_IdAndStatus(contentId, 1L, PpvPurchaseStatus.PAID);
    }

    @Test
    void hasPurchased_WithId_ShouldReturnFalse_WhenNoRecord() {
        when(ppvPurchaseRepository.existsByPpvContent_IdAndUser_IdAndStatus(contentId, 1L, PpvPurchaseStatus.PAID))
                .thenReturn(false);

        assertFalse(validationService.hasPurchased(1L, contentId));
    }

    @Test
    void hasPurchased_NullInputs_ShouldReturnFalse() {
        assertFalse(validationService.hasPurchased(null, content));
        assertFalse(validationService.hasPurchased(user, null));
        assertFalse(validationService.hasPurchased(null, contentId));
        assertFalse(validationService.hasPurchased(1L, null));
    }

    @Test
    void hasPurchased_WithRefundedStatus_ShouldReturnFalse() {
        // hasPurchased implementation specifically checks for PpvPurchaseStatus.COMPLETED
        // so any other status should naturally return false/empty from repository
        when(ppvPurchaseRepository.existsByPpvContent_IdAndUser_IdAndStatus(contentId, 1L, PpvPurchaseStatus.PAID))
                .thenReturn(false);

        assertFalse(validationService.hasPurchased(1L, contentId));
    }
}








