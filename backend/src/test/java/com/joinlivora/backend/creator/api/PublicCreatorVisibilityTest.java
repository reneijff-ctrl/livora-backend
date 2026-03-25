package com.joinlivora.backend.creator.api;

import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicCreatorVisibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    private CreatorProfile shadowbannedProfile;
    private CreatorProfile inactiveProfile;
    private CreatorProfile regularProfile;
    private CreatorProfile activeProfile;

    @BeforeEach
    void setup() {
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();

        User activeCreator = createUser("active@test.com", Role.CREATOR, false);
        activeProfile = createProfile(activeCreator, "active_creator", ProfileStatus.ACTIVE);

        User shadowbannedCreator = createUser("shadow@test.com", Role.CREATOR, true);
        shadowbannedProfile = createProfile(shadowbannedCreator, "shadow_creator", ProfileStatus.ACTIVE);

        User inactiveCreator = createUser("inactive@test.com", Role.CREATOR, false);
        inactiveProfile = createProfile(inactiveCreator, "inactive_creator", ProfileStatus.SUSPENDED);

        User regularUser = createUser("user@test.com", Role.USER, false);
        // Even if they somehow have a profile, it shouldn't show
        regularProfile = createProfile(regularUser, "regular_user", ProfileStatus.ACTIVE);
    }

    private User createUser(String email, Role role, boolean shadowbanned) {
        User user = new User(email, "password", role);
        user.setUsername(email.split("@")[0]);
        user.setShadowbanned(shadowbanned);
        return userRepository.save(user);
    }

    private CreatorProfile createProfile(User user, String username, ProfileStatus status) {
        CreatorProfile profile = new CreatorProfile();
        profile.setUser(user);
        profile.setUsername(username);
        profile.setDisplayName(username);
        profile.setStatus(status);
        return creatorProfileRepository.save(profile);
    }

    @Test
    void getCreator_ActiveCreator_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/creators/" + activeProfile.getUser().getId()))
                .andExpect(status().isOk());
    }

    @Test
    void getCreator_Shadowbanned_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/creators/" + shadowbannedProfile.getUser().getId()))
                .andExpect(status().isNotFound());
        
        mockMvc.perform(get("/api/creators/shadow_creator"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCreator_Inactive_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/creators/" + inactiveProfile.getUser().getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCreator_NonCreator_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/creators/" + regularProfile.getUser().getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfile_Shadowbanned_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/creators/profile/" + shadowbannedProfile.getUser().getId()))
                .andExpect(status().isNotFound());
        
        mockMvc.perform(get("/api/creators/profile/shadow_creator"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfile_Inactive_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/creators/profile/" + inactiveProfile.getUser().getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProfileByUsername_Shadowbanned_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/creators/username/shadow_creator"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProfileByUsername_Inactive_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/creators/username/inactive_creator"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfileById_Shadowbanned_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/public/creators/" + shadowbannedProfile.getUser().getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfileById_Inactive_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/public/creators/" + inactiveProfile.getUser().getId()))
                .andExpect(status().isNotFound());
    }
}








