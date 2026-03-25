package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.dto.ChatPpvAccessResponse;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatAccessController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
class ChatAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatRoomService chatRoomService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.joinlivora.backend.security.JwtService jwtService;

    @MockBean
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;

    @MockBean
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @MockBean
    private com.joinlivora.backend.security.LoginFailureHandler loginFailureHandler;

    @Test
    void getPpvAccess_ShouldReturnAccessInfo() throws Exception {
        UUID roomId = UUID.randomUUID();
        Long userId = 123L;
        String email = "test@test.com";
        UUID ppvId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);

        User user = new User();
        user.setId(userId);
        user.setEmail(email);

        ChatPpvAccessResponse response = ChatPpvAccessResponse.builder()
                .hasAccess(true)
                .expiresAt(expiresAt)
                .ppvContentId(ppvId)
                .build();

        when(userService.getByEmail(email)).thenReturn(user);
        when(chatRoomService.checkPpvAccess(roomId, userId)).thenReturn(response);

        mockMvc.perform(get("/api/chat/ppv-access/" + roomId)
                        .principal(() -> email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAccess").value(true))
                .andExpect(jsonPath("$.ppvContentId").value(ppvId.toString()))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void getPpvAccess_NoAccess_ShouldReturnFalse() throws Exception {
        UUID roomId = UUID.randomUUID();
        Long userId = 123L;
        String email = "test@test.com";
        UUID ppvId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setEmail(email);

        ChatPpvAccessResponse response = ChatPpvAccessResponse.builder()
                .hasAccess(false)
                .ppvContentId(ppvId)
                .build();

        when(userService.getByEmail(email)).thenReturn(user);
        when(chatRoomService.checkPpvAccess(roomId, userId)).thenReturn(response);

        mockMvc.perform(get("/api/chat/ppv-access/" + roomId)
                        .principal(() -> email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAccess").value(false))
                .andExpect(jsonPath("$.ppvContentId").value(ppvId.toString()))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }
}









