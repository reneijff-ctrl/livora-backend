package com.joinlivora.backend.tip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private CreatorTipRepository tipRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private User viewer;
    private User creatorUser;
    private CreatorProfile creatorProfile;

    @BeforeEach
    void setUp() {
        tipRepository.deleteAll();
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        viewer = TestUserFactory.createViewer("viewer@test.com");
        viewer = userRepository.save(viewer);

        creatorUser = TestUserFactory.createCreator("creator@test.com");
        creatorUser = userRepository.save(creatorUser);

        creatorProfile = CreatorProfile.builder()
                .user(creatorUser)
                .displayName("Test Creator")
                .build();
        creatorProfile = creatorProfileRepository.save(creatorProfile);
    }

    @Test
    void createTip_ShouldPersistTipAndReturnOk() throws Exception {
        String token = jwtService.generateAccessToken(viewer);
        Map<String, Object> payload = Map.of(
                "creator", creatorUser.getId(),
                "amount", 10.00
        );

        mockMvc.perform(post("/api/tips")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        var tips = tipRepository.findAll();
        assertEquals(1, tips.size());
        DirectTip tip = tips.get(0);
        assertEquals(viewer.getId(), tip.getUser().getId());
        assertEquals(creatorUser.getId(), tip.getCreator().getId());
        assertEquals(new BigDecimal("10.00"), tip.getAmount().setScale(2));
        assertEquals(TipStatus.PENDING, tip.getStatus());
    }

    @Test
    void createTip_InvalidAmount_ShouldReturnBadRequest() throws Exception {
        String token = jwtService.generateAccessToken(viewer);
        Map<String, Object> payload = Map.of(
                "creator", creatorUser.getId(),
                "amount", -5.00
        );

        mockMvc.perform(post("/api/tips")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTip_CreatorNotFound_ShouldReturnBadRequest() throws Exception {
        String token = jwtService.generateAccessToken(viewer);
        Map<String, Object> payload = Map.of(
                "creator", 99999L,
                "amount", 10.00
        );

        mockMvc.perform(post("/api/tips")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }
}








