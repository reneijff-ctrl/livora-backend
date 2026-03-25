package com.joinlivora.backend.creator.controller;

import com.joinlivora.backend.content.AccessLevel;
import com.joinlivora.backend.content.Content;
import com.joinlivora.backend.content.ContentService;
import com.joinlivora.backend.content.ContentType;
import com.joinlivora.backend.content.dto.UpdateContentRequest;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CreatorControllerContentTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContentService contentService;

    @MockBean
    private UserService userService;

    private User creator;
    private UUID contentId;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
        creator.setRole(Role.CREATOR);

        contentId = UUID.randomUUID();
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldGetCreatorContent() throws Exception {
        when(userService.getByEmail("creator@test.com")).thenReturn(creator);
        
        mockMvc.perform(get("/api/creators/content"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldDeleteContent() throws Exception {
        when(userService.getByEmail("creator@test.com")).thenReturn(creator);

        mockMvc.perform(delete("/api/creators/content/" + contentId))
                .andExpect(status().isNoContent());

        verify(contentService).deleteContent(eq(contentId), eq(1L));
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = {"CREATOR"})
    void shouldUpdateContent() throws Exception {
        when(userService.getByEmail("creator@test.com")).thenReturn(creator);

        Content updatedContent = Content.builder()
                .id(contentId)
                .title("Updated Title")
                .description("Updated Description")
                .accessLevel(AccessLevel.PREMIUM)
                .type(ContentType.PHOTO)
                .creator(creator)
                .unlockPriceTokens(200)
                .build();

        when(contentService.updateContent(eq(contentId), eq(1L), any(UpdateContentRequest.class)))
                .thenReturn(updatedContent);

        String requestJson = "{\"title\":\"Updated Title\", \"description\":\"Updated Description\", \"accessLevel\":\"PREMIUM\", \"unlockPriceTokens\":200}";

        mockMvc.perform(put("/api/creators/content/" + contentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.unlockPriceTokens").value(200));
    }
}








