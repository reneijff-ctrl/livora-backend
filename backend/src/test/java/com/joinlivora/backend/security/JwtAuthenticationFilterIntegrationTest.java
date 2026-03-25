package com.joinlivora.backend.security;

import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthenticationFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRiskStateRepository userRiskStateRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        userRiskStateRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user.setEmail("test" + suffix + "@example.com");
        user.setUsername("test" + suffix);
        user.setPassword("password");
        user.setRole(Role.USER);
        user = userRepository.save(user);

        token = jwtService.generateAccessToken(user);
    }

    @Test
    void whenUserIsBlocked_thenReturns403AndErrorCode() throws Exception {
        Long userId = user.getId();
        UserRiskState blockedState = UserRiskState.builder()
                .userId(userId)
                .currentRisk(FraudDecisionLevel.HIGH)
                .blockedUntil(Instant.now().plusSeconds(3600))
                .build();
        userRiskStateRepository.save(blockedState);

        mockMvc.perform(get("/api/tokens/balance")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_TEMP_BLOCKED"))
                .andExpect(jsonPath("$.message").value("Your account is temporarily blocked due to suspicious activity."));
    }

    @Test
    void whenUserIsNotBlocked_thenProceedsSuccessfully() throws Exception {
        mockMvc.perform(get("/api/tokens/balance")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void whenRequestingPublicContent_thenBypassesAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/content/public")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void whenRequestingPublicContentSubPath_thenBypassesAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/content/public/any-subpath")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void whenUserBlockExpired_thenProceedsSuccessfully() throws Exception {
        Long userId = user.getId();
        UserRiskState expiredBlockState = UserRiskState.builder()
                .userId(userId)
                .currentRisk(FraudDecisionLevel.HIGH)
                .blockedUntil(Instant.now().minusSeconds(60))
                .build();
        userRiskStateRepository.save(expiredBlockState);

        mockMvc.perform(get("/api/tokens/balance")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}








