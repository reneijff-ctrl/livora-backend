package com.joinlivora.backend.monetization;

import com.joinlivora.backend.auth.AuthService;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.service.TrustEvaluationService;
import com.joinlivora.backend.fraud.service.VelocityTrackerService;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.wallet.*;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.Role;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Disabled("Legacy streaming architecture")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TippingFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWalletRepository tokenBalanceRepository;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private LegacyCreatorProfileRepository creatorProfileRepository;

    @Autowired
    private AuthService authService;

    @MockBean
    private TrustEvaluationService trustEvaluationService;

    @MockBean
    private VelocityTrackerService velocityTrackerService;

    @Autowired
    private JwtService jwtService;

    private User viewer;
    private User creator;
    private Stream room;
    private String viewerToken;

    @BeforeEach
    void setUp() {
        viewer = new User();
        viewer.setEmail("viewer@test.com");
        viewer.setUsername("viewer");
        viewer.setPassword("password");
        viewer.setRole(Role.USER);
        viewer.setPayoutsEnabled(true);
        viewer.setCreatedAt(Instant.now());
        viewer = userRepository.save(viewer);

        UserWallet balance = new UserWallet();
        balance.setUserId(viewer);
        balance.setBalance(1000);
        balance.setReservedBalance(0);
        tokenBalanceRepository.save(balance);

        creator = new User();
        creator.setEmail("creator@test.com");
        creator.setUsername("creator");
        creator.setPassword("password");
        creator.setRole(Role.CREATOR);
        creator.setPayoutsEnabled(true);
        creator.setCreatedAt(Instant.now());
        creator = userRepository.save(creator);

        LegacyCreatorProfile profile = LegacyCreatorProfile.builder()
                .user(creator)
                .username("creator_one")
                .displayName("Creator One")
                .category("General")
                .build();
        creatorProfileRepository.save(profile);

        room = Stream.builder()
                .creator(creator)
                .title("Live Show")
                .isLive(true)
                .build();
        room = streamRepository.save(room);

        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        viewerToken = jwtService.generateAccessToken(viewer);
    }

    @Test
    void sendTokenTip_Success() throws Exception {
        mockMvc.perform(post("/api/tips/send")
                        .with(csrf())
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": \"" + room.getId() + "\", \"amount\": 100, \"message\": \"Great show!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(100)))
                .andExpect(jsonPath("$.currency", is("TOKEN")))
                .andExpect(jsonPath("$.senderEmail", is(viewer.getEmail())))
                .andExpect(jsonPath("$.viewerBalance", is(900)))
                .andExpect(jsonPath("$.creatorBalance", notNullValue()));
    }

    @Test
    void sendTokenTip_InsufficientBalance_ShouldFail() throws Exception {
        mockMvc.perform(post("/api/tips/send")
                        .with(csrf())
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": \"" + room.getId() + "\", \"amount\": 2000, \"message\": \"Too much!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendTokenTip_RoomNotFound_ShouldFail() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(post("/api/tips/send")
                        .with(csrf())
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": \"" + randomId + "\", \"amount\": 100}"))
                .andExpect(status().isNotFound());
    }
}









