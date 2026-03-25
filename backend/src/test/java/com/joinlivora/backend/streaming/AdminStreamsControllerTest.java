package com.joinlivora.backend.streaming;

import com.joinlivora.backend.admin.dto.AdminStreamDTO;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminStreamsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StreamService LiveStreamService;

    @MockBean
    private StreamRepository StreamRepository;

    @MockBean
    private LiveViewerCounterService viewerCounterService;

    @MockBean
    private UserService userService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private BadgeService badgeService;

    @MockBean
    private BadgeRepository badgeRepository;

    private Stream activeStream;

    @BeforeEach
    void setUp() {
        User creator = new User();
        creator.setId(1L);
        creator.setUsername("test_creator");
        creator.setEmail("creator@test.com");

        activeStream = Stream.builder()
                .id(UUID.randomUUID())
                .creator(creator)
                .isLive(true)
                .title("Test Stream Title")
                .startedAt(Instant.now().minusSeconds(3600))
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getActiveStreams_ShouldReturnList() throws Exception {
        when(StreamRepository.findActiveStreamsWithUser()).thenReturn(List.of(activeStream));
        when(viewerCounterService.getViewerCount(1L)).thenReturn(100L);

        mockMvc.perform(get("/api/admin/streams"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].creatorUsername").value("test_creator"))
                .andExpect(jsonPath("$[0].title").value("Test Stream Title"))
                .andExpect(jsonPath("$[0].viewerCount").value(100))
                .andExpect(jsonPath("$[0].durationSeconds").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3600)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getActiveStreams_WhenEmpty_ShouldReturnEmptyList() throws Exception {
        when(StreamRepository.findActiveStreamsWithUser()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/streams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getActiveStreams_WhenNull_ShouldReturnEmptyList() throws Exception {
        when(StreamRepository.findActiveStreamsWithUser()).thenReturn(null);

        mockMvc.perform(get("/api/admin/streams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getActiveStreams_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/streams"))
                .andExpect(status().isForbidden());
    }
}








