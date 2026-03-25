package com.joinlivora.backend.creator.api;

import com.joinlivora.backend.creator.follow.entity.CreatorFollow;
import com.joinlivora.backend.creator.follow.repository.CreatorFollowRepository;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicCreatorProfileByIdTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private StreamRepository StreamRepository;

    @Autowired
    private CreatorFollowRepository creatorFollowRepository;

    @MockBean
    private com.joinlivora.backend.creator.service.OnlineStatusService onlineStatusService;

    private User activeCreator;
    private CreatorProfile activeProfile;
    private User follower;

    @BeforeEach
    void setUp() {
        StreamRepository.deleteAll();
        creatorFollowRepository.deleteAll();
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        activeCreator = TestUserFactory.createCreator("creator@test.com");
        activeCreator = userRepository.save(activeCreator);

        activeProfile = creatorProfileRepository.save(CreatorProfile.builder()
                .user(activeCreator)
                .displayName("Active Creator")
                .bio("I am active")
                .avatarUrl("avatar.jpg")
                .bannerUrl("banner.jpg")
                .status(ProfileStatus.ACTIVE)
                .build());

        follower = TestUserFactory.createViewer("follower@test.com");
        follower = userRepository.save(follower);
        
        creatorFollowRepository.save(CreatorFollow.builder()
                .follower(follower)
                .creator(activeCreator)
                .build());
                
        StreamRepository.save(Stream.builder()
                .creator(activeCreator)
                .isLive(true)
                .build());

        when(onlineStatusService.isOnline(activeCreator.getId())).thenReturn(true);
    }

    @Test
    void getPublicProfile_Success() throws Exception {
        mockMvc.perform(get("/api/public/creators/" + activeCreator.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value(activeProfile.getId()))
                .andExpect(jsonPath("$.displayName").value("Active Creator"))
                .andExpect(jsonPath("$.bio").value("I am active"))
                .andExpect(jsonPath("$.profileImageUrl").value("avatar.jpg"))
                .andExpect(jsonPath("$.bannerImageUrl").value("banner.jpg"))
                .andExpect(jsonPath("$.totalFollowers").value(1))
                .andExpect(jsonPath("$.isOnline").value(true));
    }

    @Test
    void getPublicProfile_AutoCreate_Success() throws Exception {
        User creatorWithoutProfile = TestUserFactory.createCreator("noprofile@test.com");
        creatorWithoutProfile = userRepository.save(creatorWithoutProfile);
        
        mockMvc.perform(get("/api/public/creators/" + creatorWithoutProfile.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("noprofile"));
        
        // Verify it was actually created in DB
        assertTrue(creatorProfileRepository.findByUserId(creatorWithoutProfile.getId()).isPresent());
    }

    @Test
    void getPublicProfile_NotFound_ForNonExistentId() throws Exception {
        mockMvc.perform(get("/api/public/creators/99999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfile_NotFound_ForNonCreatorRole() throws Exception {
        User user = TestUserFactory.createViewer("user@test.com");
        user = userRepository.save(user);
        
        CreatorProfile profile = creatorProfileRepository.save(CreatorProfile.builder()
                .user(user)
                .status(ProfileStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/api/public/creators/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfile_NotFound_ForInactiveStatus() throws Exception {
        User inactive = TestUserFactory.createCreator("inactive@test.com");
        inactive = userRepository.save(inactive);
        
        CreatorProfile profile = creatorProfileRepository.save(CreatorProfile.builder()
                .user(inactive)
                .status(ProfileStatus.SUSPENDED)
                .build());

        mockMvc.perform(get("/api/public/creators/" + inactive.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfile_ShadowbannedActive_IsAccessible() throws Exception {
        User shadowbanned = TestUserFactory.createCreator("shadow@test.com");
        shadowbanned.setShadowbanned(true);
        shadowbanned = userRepository.save(shadowbanned);
        
        CreatorProfile profile = creatorProfileRepository.save(CreatorProfile.builder()
                .user(shadowbanned)
                .status(ProfileStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/api/public/creators/" + shadowbanned.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}








