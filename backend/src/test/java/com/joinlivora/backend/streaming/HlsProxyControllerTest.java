package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HlsProxyController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
}, excludeFilters = {
    @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = com.joinlivora.backend.security.JwtAuthenticationFilter.class),
    @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = com.joinlivora.backend.security.RateLimitingFilter.class)
})
class HlsProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HlsProxyController hlsProxyController;

    @MockBean
    private StreamService streamService;

    @MockBean
    private LiveStreamService liveStreamService;

    @MockBean
    private StreamRepository streamRepository;

    @MockBean
    private UserService userService;

    @MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

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
    private com.joinlivora.backend.user.UserRepository userRepository;

    @MockBean
    private com.joinlivora.backend.livestream.service.LiveStreamService liveStreamServiceV2;

    @MockBean
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;

    @MockBean
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;

    @TempDir
    Path tempDir;

    private UUID liveStreamId;
    private String liveStreamKey = "sk_test";
    private User user;
    private Stream liveStream;

    @BeforeEach
    void setUp() {
        liveStreamId = UUID.randomUUID();
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");
        liveStream = Stream.builder()
                .id(liveStreamId)
                .streamKey(liveStreamKey)
                .creator(user)
                .build();

        ReflectionTestUtils.setField(hlsProxyController, "hlsDirectory", tempDir.toString());
        hlsProxyController.resetRateLimits();

        // Mock counter
        io.micrometer.core.instrument.Counter mockCounter = org.mockito.Mockito.mock(io.micrometer.core.instrument.Counter.class);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

        // Mock V2 active liveStream
        Stream activeV2 = new Stream();
        when(liveStreamServiceV2.getActiveStream(any())).thenReturn(activeV2);
    }

    @Test
    void getPlaylist_Success_ShouldReturnFile() throws Exception {
        Path liveStreamDir = tempDir.resolve(liveStreamKey);
        Files.createDirectories(liveStreamDir);
        Path playlistPath = liveStreamDir.resolve("index.m3u8");
        Files.writeString(playlistPath, "#EXTM3U");

        when(streamRepository.findByIdWithCreator(liveStreamId)).thenReturn(Optional.of(liveStream));
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(liveStreamService.validateViewerAccess(liveStream, user)).thenReturn(true);

        mockMvc.perform(get("/api/hls/" + liveStreamId + "/" + liveStreamKey + "/index.m3u8")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.apple.mpegurl"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().string("#EXTM3U"));
    }

    @Test
    void getPlaylist_AccessDenied_ShouldReturnForbidden() throws Exception {
        when(streamRepository.findByIdWithCreator(liveStreamId)).thenReturn(Optional.of(liveStream));
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(liveStreamService.validateViewerAccess(liveStream, user)).thenReturn(false);

        mockMvc.perform(get("/api/hls/" + liveStreamId + "/" + liveStreamKey + "/index.m3u8")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSegment_Success_ShouldReturnFile() throws Exception {
        Path liveStreamDir = tempDir.resolve(liveStreamKey);
        Files.createDirectories(liveStreamDir);
        Path segmentPath = liveStreamDir.resolve("segment1.ts");
        Files.write(segmentPath, new byte[]{0, 1, 2, 3});

        when(streamRepository.findByIdWithCreator(liveStreamId)).thenReturn(Optional.of(liveStream));
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(liveStreamService.validateViewerAccess(liveStream, user)).thenReturn(true);

        mockMvc.perform(get("/api/hls/" + liveStreamId + "/" + liveStreamKey + "/segment1.ts")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/mp2t"))
                .andExpect(header().string("Cache-Control", "max-age=3600"))
                .andExpect(content().bytes(new byte[]{0, 1, 2, 3}));
    }

    @Test
    void getVariantPlaylist_Success_ShouldReturnFile() throws Exception {
        Path liveStreamDir = tempDir.resolve(liveStreamKey);
        Files.createDirectories(liveStreamDir);
        Path playlistPath = liveStreamDir.resolve("index_1080p.m3u8");
        Files.writeString(playlistPath, "#EXTM3U_VARIANT");

        when(streamRepository.findByIdWithCreator(liveStreamId)).thenReturn(Optional.of(liveStream));
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(liveStreamService.validateViewerAccess(liveStream, user)).thenReturn(true);

        mockMvc.perform(get("/api/hls/" + liveStreamId + "/" + liveStreamKey + "/index_1080p.m3u8")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.apple.mpegurl"))
                .andExpect(content().string("#EXTM3U_VARIANT"));
    }

    @Test
    void getVariantSegment_Success_ShouldReturnFile() throws Exception {
        Path variantDir = tempDir.resolve(liveStreamKey).resolve("index_1080p");
        Files.createDirectories(variantDir);
        Path segmentPath = variantDir.resolve("segment1.ts");
        Files.write(segmentPath, new byte[]{4, 5, 6, 7});

        when(streamRepository.findByIdWithCreator(liveStreamId)).thenReturn(Optional.of(liveStream));
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(liveStreamService.validateViewerAccess(liveStream, user)).thenReturn(true);

        mockMvc.perform(get("/api/hls/" + liveStreamId + "/" + liveStreamKey + "/index_1080p/segment1.ts")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/mp2t"))
                .andExpect(content().bytes(new byte[]{4, 5, 6, 7}));
    }

    @Test
    void getSegment_RateLimitExceeded_ShouldReturn429() throws Exception {
        Path liveStreamDir = tempDir.resolve(liveStreamKey);
        Files.createDirectories(liveStreamDir);
        Path segmentPath = liveStreamDir.resolve("segment1.ts");
        Files.write(segmentPath, new byte[]{0});

        when(streamRepository.findByIdWithCreator(liveStreamId)).thenReturn(Optional.of(liveStream));
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(liveStreamService.validateViewerAccess(liveStream, user)).thenReturn(true);

        // Consume all tokens (120)
        for (int i = 0; i < 120; i++) {
            mockMvc.perform(get("/api/hls/" + liveStreamId + "/" + liveStreamKey + "/segment1.ts")
                            .principal(() -> "creator@test.com"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/hls/" + liveStreamId + "/" + liveStreamKey + "/segment1.ts")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void getPlaylist_FileNotFound_ShouldReturnNotFound() throws Exception {
        when(streamRepository.findByIdWithCreator(liveStreamId)).thenReturn(Optional.of(liveStream));
        when(userService.getByEmail("creator@test.com")).thenReturn(user);
        when(liveStreamService.validateViewerAccess(liveStream, user)).thenReturn(true);

        mockMvc.perform(get("/api/hls/" + liveStreamId + "/" + liveStreamKey + "/index.m3u8")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isNotFound());
    }
}








