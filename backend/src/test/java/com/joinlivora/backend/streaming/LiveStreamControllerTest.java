package com.joinlivora.backend.streaming;

import com.joinlivora.backend.monetization.dto.SuperTipResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.streaming.StreamCacheDTO;
import java.util.List;

@Disabled("Legacy streaming architecture")
@WebMvcTest(controllers = LiveStreamController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
}, excludeFilters = {
    @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = com.joinlivora.backend.security.JwtAuthenticationFilter.class),
    @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = com.joinlivora.backend.security.RateLimitingFilter.class)
})
class LiveStreamControllerTest {

    @TestConfiguration
    static class TestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                    return parameter.getParameterType().equals(UserPrincipal.class);
                }

                @Override
                public Object resolveArgument(org.springframework.core.MethodParameter parameter, org.springframework.web.method.support.ModelAndViewContainer mavContainer, org.springframework.web.context.request.NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                    return new UserPrincipal(1L, "creatorUserId@test.com", "password", Collections.emptyList());
                }
            });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        StreamCacheDTO activeV2 = StreamCacheDTO.builder().build();
        when(liveStreamServiceV2.getActiveStream(any())).thenReturn(activeV2);

        User creator = new User();
        creator.setId(1L);
        creator.setEmail("creatorUserId@test.com");
        when(userService.getById(1L)).thenReturn(creator);
    }

    @MockBean
    private StreamService streamService;

    @MockBean
    private LiveStreamService liveStreamService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.joinlivora.backend.creator.service.CreatorProfileService creatorProfileService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private StreamRepository streamRepository;

    @MockBean
    private com.joinlivora.backend.monetization.SuperTipHighlightTracker highlightTracker;

    @MockBean
    private com.joinlivora.backend.streaming.service.StreamModerationService liveStreamModerationService;

    @MockBean
    private com.joinlivora.backend.livestream.service.LiveStreamService liveStreamServiceV2;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @MockBean
    private com.joinlivora.backend.fraud.repository.UserRiskStateRepository userRiskStateRepository;

    @MockBean
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;

    @MockBean
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;

@Test
    void startStream_ShouldSucceed() throws Exception {
        User creator = new User();
        creator.setId(1L);
        creator.setEmail("creatorUserId@test.com");
        when(userService.getById(1L)).thenReturn(creator);
        when(streamService.startStream(any(), anyString(), anyString(), any(), anyBoolean(), any(), any(), anyBoolean())).thenReturn(new StreamRoom());

        mockMvc.perform(post("/api/stream/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"My Stream\", \"description\": \"Hello\", \"minChatTokens\": 10, \"isPaid\": false, \"recordingEnabled\": true}"))
                .andExpect(status().isOk());

        verify(streamService).startStream(argThat(u -> u.getId().equals(1L)), eq("My Stream"), eq("Hello"), eq(10L), eq(false), eq(null), eq(null), eq(true));
    }

    @Test
    void stopStream_ShouldSucceed() throws Exception {
        User creator = new User();
        creator.setId(1L);
        creator.setEmail("creatorUserId@test.com");
        when(userService.getById(1L)).thenReturn(creator);
        when(streamService.stopStream(any())).thenReturn(new StreamRoom());

        mockMvc.perform(post("/api/stream/stop"))
                .andExpect(status().isOk());

        verify(streamService).stopStream(argThat(u -> u.getId().equals(1L)));
    }

    @Test
    void getStreamStatus_ShouldReturnStatus() throws Exception {
        UUID liveStreamId = UUID.randomUUID();
        StreamRoom room = new StreamRoom();
        room.setId(liveStreamId);
        room.setLive(true);

        when(streamService.getRoom(liveStreamId)).thenReturn(room);

        mockMvc.perform(get("/api/stream/" + liveStreamId + ".status"))
                .andExpect(status().isOk());

        verify(streamService).getRoom(liveStreamId);
    }

    @Test
    void getActiveHighlight_ShouldReturnHighlight() throws Exception {
        UUID liveStreamId = UUID.randomUUID();
        SuperTipResponse highlight = SuperTipResponse.builder()
                .id(UUID.randomUUID())
                .senderEmail("creator@test.com")
                .highlightLevel(com.joinlivora.backend.monetization.HighlightLevel.ULTRA)
                .build();
        
        when(highlightTracker.getActiveHighlight(liveStreamId)).thenReturn(java.util.Optional.of(highlight));

        mockMvc.perform(get("/api/stream/" + liveStreamId + "/highlight"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderEmail").value("creator@test.com"))
                .andExpect(jsonPath("$.highlightLevel").value("ULTRA"));
    }

    @Test
    void getliveStreams_ShouldSucceed() throws Exception {
        when(streamService.getLiveStreams()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/stream/live"))
                .andExpect(status().isOk());

        verify(streamService).getLiveStreams();
    }

    @Test
    void getStreamByCreator_ShouldSucceed() throws Exception {
        User creator = new User();
        creator.setId(1L);
        when(creatorProfileService.getUserByProfileId(1L)).thenReturn(creator);
        when(streamService.getCreatorRoom(creator)).thenReturn(new StreamRoom());

        mockMvc.perform(get("/api/stream/1"))
                .andExpect(status().isOk());

        verify(streamService).getCreatorRoom(creator);
    }

    @Test
    void authenticateRTMP_Success_ShouldReturnOk() throws Exception {
        when(liveStreamService.verifyStreamKeyAndStart("sk_valid")).thenReturn(true);

        mockMvc.perform(post("/api/stream/auth")
                        .param("name", "sk_valid")
                        .param("addr", "127.0.0.1"))
                .andExpect(status().isOk());

        verify(liveStreamService).verifyStreamKeyAndStart("sk_valid");
    }

    @Test
    void authenticateRTMP_Failure_ShouldReturnForbidden() throws Exception {
        when(liveStreamService.verifyStreamKeyAndStart("sk_invalid")).thenReturn(false);

        mockMvc.perform(post("/api/stream/auth")
                        .param("name", "sk_invalid")
                        .param("addr", "127.0.0.1"))
                .andExpect(status().isForbidden());

        verify(liveStreamService).verifyStreamKeyAndStart("sk_invalid");
    }

    @Test
    void authenticateRTMPDone_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/stream/auth-done")
                        .param("name", "sk_test"))
                .andExpect(status().isOk());

        verify(liveStreamService).verifyStreamKeyAndStop("sk_test");
    }

    @Test
    void getVodStreams_ShouldSucceed() throws Exception {
        when(streamRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/stream/vod"))
                .andExpect(status().isOk());

        verify(streamRepository).findAll();
    }

    @Test
    void getHlsUrl_Success_ShouldReturnUrl() throws Exception {
        UUID liveStreamId = UUID.randomUUID();
        User creator = new User();
        creator.setId(2L);
        Stream liveStream = Stream.builder()
                .id(liveStreamId)
                .streamKey("test-key")
                .creator(creator)
                .build();
        User user = new User();
        user.setEmail("viewer@test.com");

        when(streamRepository.findById(liveStreamId)).thenReturn(Optional.of(liveStream));
        when(userService.getByEmail("viewer@test.com")).thenReturn(user);
        when(liveStreamService.validateViewerAccess(liveStream, user)).thenReturn(true);
        when(liveStreamServiceV2.getActiveStream(2L)).thenReturn(StreamCacheDTO.builder().build());

        mockMvc.perform(get("/api/stream/" + liveStreamId + "/hls")
                        .principal(() -> "viewer@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/hls/" + liveStreamId + "/test-key/index.m3u8"));

        verify(liveStreamService).validateViewerAccess(liveStream, user);
    }

    @Test
    void getHlsUrl_Denied_ShouldReturnForbidden() throws Exception {
        UUID liveStreamId = UUID.randomUUID();
        Stream liveStream = Stream.builder().id(liveStreamId).build();
        User user = new User();
        user.setEmail("viewer@test.com");

        when(streamRepository.findById(liveStreamId)).thenReturn(Optional.of(liveStream));
        when(userService.getByEmail("viewer@test.com")).thenReturn(user);
        when(liveStreamService.validateViewerAccess(liveStream, user)).thenReturn(false);

        mockMvc.perform(get("/api/stream/" + liveStreamId + "/hls")
                        .principal(() -> "viewer@test.com"))
                .andExpect(status().isForbidden());

        verify(liveStreamService).validateViewerAccess(liveStream, user);
    }
}








