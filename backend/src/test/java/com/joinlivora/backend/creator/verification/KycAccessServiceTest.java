package com.joinlivora.backend.creator.verification;

import com.joinlivora.backend.common.exception.KycNotApprovedException;
import com.joinlivora.backend.creator.model.CreatorVerification;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycAccessServiceTest {

    @Mock
    private CreatorVerificationRepository creatorVerificationRepository;

    @InjectMocks
    private KycAccessService kycAccessService;

    private final Long creatorId = 1L;

    @Test
    void assertCreatorCanReceivePayout_WhenApproved_ShouldNotThrow() {
        // Given
        CreatorVerification verification = CreatorVerification.builder()
                .status(VerificationStatus.APPROVED)
                .build();
        when(creatorVerificationRepository.findByCreator_User_Id(creatorId)).thenReturn(Optional.of(verification));

        // When & Then
        assertDoesNotThrow(() -> kycAccessService.assertCreatorCanReceivePayout(creatorId));
    }

    @Test
    void assertCreatorCanReceivePayout_WhenNotFound_ShouldThrowIllegalStateException() {
        // Given
        when(creatorVerificationRepository.findByCreator_User_Id(creatorId)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
                () -> kycAccessService.assertCreatorCanReceivePayout(creatorId));
        assertEquals("Creator verification not found", exception.getMessage());
    }

    @Test
    void assertCreatorCanReceivePayout_WhenNotApproved_ShouldThrowKycNotApprovedException() {
        // Given
        CreatorVerification verification = CreatorVerification.builder()
                .status(VerificationStatus.PENDING)
                .build();
        when(creatorVerificationRepository.findByCreator_User_Id(creatorId)).thenReturn(Optional.of(verification));

        // When & Then
        assertThrows(KycNotApprovedException.class, 
                () -> kycAccessService.assertCreatorCanReceivePayout(creatorId));
    }
    
    @Test
    void assertCreatorCanReceivePayout_WhenRejected_ShouldThrowKycNotApprovedException() {
        // Given
        CreatorVerification verification = CreatorVerification.builder()
                .status(VerificationStatus.REJECTED)
                .build();
        when(creatorVerificationRepository.findByCreator_User_Id(creatorId)).thenReturn(Optional.of(verification));

        // When & Then
        assertThrows(KycNotApprovedException.class, 
                () -> kycAccessService.assertCreatorCanReceivePayout(creatorId));
    }
}










