package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.CreatorEarningsOverviewDTO;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreatorEarningsOverviewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorEarningRepository earningRepository;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = TestUserFactory.createCreator("creator@example.com");
        userRepository.save(creator);

        // Add some earnings
        earningRepository.save(CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("10.00"))
                .platformFee(new BigDecimal("3.00"))
                .netAmount(new BigDecimal("7.00"))
                .currency("EUR")
                .sourceType(EarningSource.TIP)
                .locked(false)
                .createdAt(Instant.now())
                .build());

        earningRepository.save(CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("20.00"))
                .platformFee(new BigDecimal("6.00"))
                .netAmount(new BigDecimal("14.00"))
                .currency("EUR")
                .sourceType(EarningSource.SUBSCRIPTION)
                .locked(true)
                .createdAt(Instant.now().minusSeconds(3600))
                .build());
    }

    @Test
    @WithMockUser(username = "creator@example.com", roles = "CREATOR")
    void getEarningsOverview_ShouldReturnCorrectData() throws Exception {
        mockMvc.perform(get("/api/creator/earnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarnings").value(21.0))
                .andExpect(jsonPath("$.availableBalance").value(7.0))
                .andExpect(jsonPath("$.pendingBalance").value(14.0))
                .andExpect(jsonPath("$.lastEarnings.length()").value(2));
    }
}








