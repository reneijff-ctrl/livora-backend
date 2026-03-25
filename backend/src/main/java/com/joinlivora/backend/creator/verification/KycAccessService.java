package com.joinlivora.backend.creator.verification;

import com.joinlivora.backend.common.exception.KycNotApprovedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycAccessService {

    private final CreatorVerificationRepository creatorVerificationRepository;

    public void assertCreatorCanReceivePayout(Long creatorId) {

        var verification = creatorVerificationRepository
                .findByCreator_User_Id(creatorId)
                .orElseThrow(() -> new IllegalStateException("Creator verification not found"));

        if (verification.getStatus() != VerificationStatus.APPROVED) {
            throw new KycNotApprovedException();
        }
    }
}
