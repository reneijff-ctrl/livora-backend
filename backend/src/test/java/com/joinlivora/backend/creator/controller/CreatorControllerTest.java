package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.creator.dto.*;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import com.joinlivora.backend.streaming.StreamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CreatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreatorProfileService creatorProfileService;

    @MockBean
    private com.joinlivora.backend.presence.service.CreatorPresenceService creatorPresenceService;

    @MockBean
    private LegacyCreatorProfileRepository legacyCreatorProfileRepository;

    @MockBean
    private StreamRepository StreamRepository;

    @MockBean
    private com.joinlivora.backend.creator.service.OnlineStatusService onlineStatusService;

    @Test
    void getOnlineCreators_ShouldReturnList() throws Exception {
        HomepageCreatorDto dto = HomepageCreatorDto.builder()
                .creatorId(1L)
                .displayName("Online Creator")
                .isOnline(true)
                .isLive(true)
                .build();

        when(creatorProfileService.getPublicCreatorsForHomepage()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/creators/online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].creatorId").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Online Creator"))
                .andExpect(jsonPath("$[0].isOnline").value(true))
                .andExpect(jsonPath("$[0].isLive").value(true));
    }

    @Test
    void getCreatorByUserId_ShouldReturnCreatorDTO() throws Exception {
        PublicCreatorInfoResponse dto = PublicCreatorInfoResponse.builder()
                .creatorId(123L)
                .username("jane_creator")
                .displayName("Jane Creator")
                .bio("Creator bio")
                .avatarUrl("http://img/avatar.jpg")
                .followerCount(10)
                .postCount(5)
                .build();
        when(creatorProfileService.getPublicCreatorInfo("123")).thenReturn(java.util.Optional.of(dto));

        mockMvc.perform(get("/api/creators/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value(123))
                .andExpect(jsonPath("$.displayName").value("Jane Creator"))
                .andExpect(jsonPath("$.bio").value("Creator bio"))
                .andExpect(jsonPath("$.avatarUrl").value("http://img/avatar.jpg"))
                .andExpect(jsonPath("$.followerCount").value(10))
                .andExpect(jsonPath("$.postCount").value(5));
    }

    @Test
    void getCreatorByUserId_NotFound_ShouldReturn404() throws Exception {
        when(creatorProfileService.getPublicCreatorInfo("999")).thenReturn(java.util.Optional.empty());
        mockMvc.perform(get("/api/creators/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicCreators_ShouldReturnList() throws Exception {
        CreatorProfileDTO creator = CreatorProfileDTO.builder()
                .creatorId(1L)
                .username("creator1")
                .displayName("Creator One")
                .avatarUrl("http://avatar.com/1")
                .bio("Bio 1")
                .build();

        when(creatorProfileService.getCreators(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(creator)));

        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].creatorId").value(1))
                .andExpect(jsonPath("$[0].username").value("creator1"))
                .andExpect(jsonPath("$[0].displayName").value("Creator One"))
                .andExpect(jsonPath("$[0].bio").value("Bio 1"));
    }

    @Test
    void getCreators_WithFilters_ShouldReturnFilteredList() throws Exception {
        CreatorProfileDTO creator = CreatorProfileDTO.builder()
                .creatorId(1L)
                .username("filtered")
                .gender("female")
                .location("USA")
                .build();

        when(creatorProfileService.getCreators(eq("women"), any(), eq("USA"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(creator)));

        mockMvc.perform(get("/api/creators")
                        .param("category", "women")
                        .param("country", "USA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("filtered"));
    }

    @Test
    void getPublicCreators_ShouldReturnPaginatedList() throws Exception {
        ExploreCreatorDto creator = ExploreCreatorDto.builder()
                .creatorId(10L)
                .displayName("Home Creator")
                .profileImageUrl("http://avatar.com/home")
                .shortBio("Short bio")
                .isOnline(true)
                .build();

        org.springframework.data.domain.Page<ExploreCreatorDto> page = new org.springframework.data.domain.PageImpl<>(
                List.of(creator), 
                org.springframework.data.domain.PageRequest.of(0, 12), 
                1
        );

        when(creatorProfileService.getExploreCreatorsList(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/creators/public")
                        .param("page", "0")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].creatorId").value(10))
                .andExpect(jsonPath("$.content[0].displayName").value("Home Creator"))
                .andExpect(jsonPath("$.content[0].profileImageUrl").value("http://avatar.com/home"))
                .andExpect(jsonPath("$.content[0].shortBio").value("Short bio"))
                .andExpect(jsonPath("$.content[0].isOnline").value(true));
    }

    @Test
    void getPublicCreatorByUsername_ShouldReturnProfile() throws Exception {
        PublicCreatorInfoResponse response = PublicCreatorInfoResponse.builder()
                .creatorId(123L)
                .username("testcreator")
                .displayName("Test Creator")
                .bio("Test Bio")
                .isOwner(false)
                .build();

        when(creatorProfileService.getPublicCreatorInfo("testcreator")).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/creators/testcreator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value(123))
                .andExpect(jsonPath("$.username").value("testcreator"))
                .andExpect(jsonPath("$.displayName").value("Test Creator"))
                .andExpect(jsonPath("$.bio").value("Test Bio"));
    }


    @Test
    void getPublicCreatorProfile_ByGeneratedUsername_ShouldReturnProfile() throws Exception {
        PublicCreatorProfileResponse response = PublicCreatorProfileResponse.builder()
                .creatorId(123L)
                .username("user_123")
                .displayName("Creator OneTwoThree")
                .bio("I am a creator")
                .isOwner(false)
                .build();

        when(creatorProfileService.getPublicProfile("user_123")).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/creators/profile/user_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value(123))
                .andExpect(jsonPath("$.username").value("user_123"))
                .andExpect(jsonPath("$.displayName").value("Creator OneTwoThree"))
                .andExpect(jsonPath("$.bio").value("I am a creator"))
                .andExpect(jsonPath("$.isOwner").value(false));
    }

    @Test
    void getPublicCreatorProfile_ByUsername_ShouldReturnProfile() throws Exception {
        PublicCreatorProfileResponse response = PublicCreatorProfileResponse.builder()
                .creatorId(456L)
                .username("johndoe")
                .displayName("John Doe")
                .bio("Hello world")
                .build();

        when(creatorProfileService.getPublicProfile("johndoe")).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/creators/profile/johndoe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value(456))
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.displayName").value("John Doe"))
                .andExpect(jsonPath("$.bio").value("Hello world"));
    }

    @Test
    void getPublicCreatorProfile_NotFound_ShouldReturn404() throws Exception {
        when(creatorProfileService.getPublicProfile("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/creators/profile/nonexistent"))
                .andExpect(status().isNotFound());
    }
}








