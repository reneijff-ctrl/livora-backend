package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.payout.CreatorEarning;
import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.payout.CreatorEarnings;
import com.joinlivora.backend.payout.EarningSource;
import com.joinlivora.backend.payout.PayoutCreatorEarningsRepository;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
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

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatorControllerMeEarningsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PayoutCreatorEarningsRepository balanceRepository;

    @Autowired
    private CreatorEarningRepository historyRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.creator.service.OnlineStatusService onlineStatusService;

    private User creator;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        balanceRepository.deleteAll();
        userRepository.deleteAll();

        creator = TestUserFactory.createCreator("creator-earnings@test.com");
        creator = userRepository.save(creator);

        // Setup balance
        CreatorEarnings balance = CreatorEarnings.builder()
                .creator(creator)
                .availableBalance(new BigDecimal("100.50"))
                .pendingBalance(new BigDecimal("20.00"))
                .totalEarned(new BigDecimal("120.50"))
                .build();
        balanceRepository.save(balance);

        // Setup some earnings history for fees
        CreatorEarning earning1 = CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("10.00"))
                .platformFee(new BigDecimal("3.00"))
                .netAmount(new BigDecimal("7.00"))
                .currency("EUR")
                .sourceType(EarningSource.TIP)
                .createdAt(Instant.now())
                .build();
        
        CreatorEarning earning2 = CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("50.00"))
                .platformFee(new BigDecimal("15.00"))
                .netAmount(new BigDecimal("35.00"))
                .currency("EUR")
                .sourceType(EarningSource.SUBSCRIPTION)
                .createdAt(Instant.now())
                .build();

        historyRepository.save(earning1);
        historyRepository.save(earning2);
    }

    @Test
    void getMyEarnings_AsCreator_ShouldReturnEarningsData() throws Exception {
        String token = jwtService.generateAccessToken(creator);

        mockMvc.perform(get("/api/creators/me/earnings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(100.50))
                .andExpect(jsonPath("$.totalEarned").value(120.50))
                .andExpect(jsonPath("$.pendingBalance").value(20.00))
                .andExpect(jsonPath("$.totalFees").value(18.00));
    }

    @Test
    void getMyEarnings_AsUser_ShouldReturn403() throws Exception {
        User user = TestUserFactory.createViewer("user@test.com");
        user = userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/creators/me/earnings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyEarnings_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/creators/me/earnings")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}








