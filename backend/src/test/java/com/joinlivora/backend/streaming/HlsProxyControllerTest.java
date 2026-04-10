package com.joinlivora.backend.streaming;

import com.joinlivora.backend.config.MetricsService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HlsProxyController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
}, excludeFilters = {
    @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.joinlivora.backend.security.JwtAuthenticationFilter.class),
    @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.joinlivora.backend.security.RateLimitingFilter.class)
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

    @MockBean
    private MetricsService metricsService;

    @TempDir
    Path tempDir;

    private UUID streamId;
    private User user;
    private Stream liveStream;

    @BeforeEach
    void setUp() {
        streamId = UUID.randomUUID();
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");
        liveStream = Stream.builder()
                .id(streamId)
                .creator(user)
                .build();

        // Point controller at the temp directory
        ReflectionTestUtils.setField(hlsProxyController, "hlsDirectory", tempDir.toString());
        ReflectionTestUtils.setField(hlsProxyController, "recordingsDirectory",
                tempDir.resolve("recordings").toString());

        // Mock counters (called inside getPlaylist / getSegment)
        io.micrometer.core.instrument.Counter mockCounter =
                org.mockito.Mockito.mock(io.micrometer.core.instrument.Counter.class);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);
        // MetricsService CDN counters — prevent NPE when increment() is called
        when(metricsService.getHlsFallbackServed()).thenReturn(mockCounter);

        // HlsProxyController injects com.joinlivora.backend.livestream.service.LiveStreamService
        // which is registered as @MockBean liveStreamServiceV2 in this test class.
        // Default: stream is active; validateViewerAccess defaults to false (Mockito default for boolean).
        when(liveStreamServiceV2.isStreamActive(any())).thenReturn(true);
    }

    // ── Playlist tests ────────────────────────────────────────────────────────

    @Test
    void getPlaylist_Success_ShouldReturnFileWithCorrectHeaders() throws Exception {
        // Create HLS file under tempDir/{streamId}/master.m3u8
        Path streamDir = tempDir.resolve(streamId.toString());
        Files.createDirectories(streamDir);
        Files.writeString(streamDir.resolve("master.m3u8"), "#EXTM3U");

        when(streamRepository.findByIdWithCreator(streamId)).thenReturn(Optional.of(liveStream));
        when(userService.resolveUserFromSubject("creator@test.com")).thenReturn(Optional.of(user));
        when(liveStreamServiceV2.validateViewerAccess(liveStream, user)).thenReturn(true);

        mockMvc.perform(get("/api/hls/" + streamId + "/master.m3u8")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.apple.mpegurl"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(content().string("#EXTM3U"));
    }

    @Test
    void getPlaylist_AccessDenied_ShouldReturnForbidden() throws Exception {
        when(streamRepository.findByIdWithCreator(streamId)).thenReturn(Optional.of(liveStream));
        when(userService.resolveUserFromSubject("creator@test.com")).thenReturn(Optional.of(user));
        // liveStreamServiceV2.validateViewerAccess returns false by default (see setUp)

        mockMvc.perform(get("/api/hls/" + streamId + "/master.m3u8")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPlaylist_StreamNotFound_ShouldReturnForbidden() throws Exception {
        when(streamRepository.findByIdWithCreator(streamId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/hls/" + streamId + "/master.m3u8")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPlaylist_FileNotFound_ShouldReturnNotFound() throws Exception {
        when(streamRepository.findByIdWithCreator(streamId)).thenReturn(Optional.of(liveStream));
        when(userService.resolveUserFromSubject("creator@test.com")).thenReturn(Optional.of(user));
        when(liveStreamServiceV2.validateViewerAccess(liveStream, user)).thenReturn(true);

        // No file created — should 404
        mockMvc.perform(get("/api/hls/" + streamId + "/missing.m3u8")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isNotFound());
    }

    // ── Segment tests ─────────────────────────────────────────────────────────

    @Test
    void getSegment_Success_ShouldReturnSegmentWithCacheHeader() throws Exception {
        Path streamDir = tempDir.resolve(streamId.toString());
        Files.createDirectories(streamDir);
        Files.write(streamDir.resolve("720p_0001.ts"), new byte[]{0x47, 0x00, 0x11, 0x22});

        when(streamRepository.findByIdWithCreator(streamId)).thenReturn(Optional.of(liveStream));
        when(userService.resolveUserFromSubject("creator@test.com")).thenReturn(Optional.of(user));
        when(liveStreamServiceV2.validateViewerAccess(liveStream, user)).thenReturn(true);

        mockMvc.perform(get("/api/hls/" + streamId + "/720p_0001.ts")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/mp2t"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(content().bytes(new byte[]{0x47, 0x00, 0x11, 0x22}));
    }

    @Test
    void getSegment_AccessDenied_ShouldReturnForbidden() throws Exception {
        when(streamRepository.findByIdWithCreator(streamId)).thenReturn(Optional.of(liveStream));
        when(userService.resolveUserFromSubject("creator@test.com")).thenReturn(Optional.of(user));
        // liveStreamServiceV2.validateViewerAccess returns false by default (see setUp)

        mockMvc.perform(get("/api/hls/" + streamId + "/720p_0001.ts")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSegment_StreamNotLive_ShouldReturnForbidden() throws Exception {
        when(streamRepository.findByIdWithCreator(streamId)).thenReturn(Optional.of(liveStream));
        when(liveStreamService.isStreamActive(user.getId())).thenReturn(false);
        when(liveStreamServiceV2.isStreamActive(user.getId())).thenReturn(false);

        mockMvc.perform(get("/api/hls/" + streamId + "/720p_0001.ts")
                        .principal(() -> "creator@test.com"))
                .andExpect(status().isForbidden());
    }
}
