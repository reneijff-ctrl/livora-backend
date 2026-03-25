package com.joinlivora.backend.payout;

import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.model.CreatorVerification;
import com.joinlivora.backend.creator.model.DocumentType;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class PayoutKycIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorVerificationRepository creatorVerificationRepository;

    @Autowired
    private CreatorRepository creatorRepository;

    @Autowired
    private LegacyCreatorProfileRepository creatorProfileRepository;

    @Test
    @WithMockUser(username = "creator@test.com", roles = "CREATOR")
    void requestPayout_WhenKycPending_ShouldReturnForbidden() throws Exception {
        // 1. Create creator
        User creator = TestUserFactory.createCreator("creator@test.com");
        creator.setUsername("creator_test");
        creator.setStatus(UserStatus.ACTIVE);
        creator.setPayoutsEnabled(true);
        creator = userRepository.save(creator);

        // Requirement: Creator profile must exist
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder()
                .user(creator)
                .username("creator")
                .displayName("Creator")
                .build();
        creatorProfileRepository.save(profile);

        Creator creatorEntity = Creator.builder()
                .user(creator)
                .active(true)
                .build();
        creatorEntity = creatorRepository.save(creatorEntity);

        // 2. Create CreatorVerification with status PENDING
        CreatorVerification verification = CreatorVerification.builder()
                .creator(creatorEntity)
                .legalFirstName("John")
                .legalLastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .country("US")
                .documentType(DocumentType.ID_CARD)
                .idDocumentUrl("http://example.com/front.jpg")
                .selfieDocumentUrl("http://example.com/selfie.jpg")
                .status(VerificationStatus.PENDING)
                .build();
        creatorVerificationRepository.save(verification);

        // 3. Attempt payout request via MockMvc POST
        // Note: Using plural /api/creator/payouts/request which corresponds to PayoutRequestService
        mockMvc.perform(post("/api/creator/payouts/request")
                        .contentType(MediaType.APPLICATION_JSON))
                // 4. Expect HTTP 403
                .andExpect(status().isForbidden())
                // 5. Expect JSON field "error" = "KYC_NOT_APPROVED"
                .andExpect(jsonPath("$.error").value("KYC_NOT_APPROVED"));
    }
}








