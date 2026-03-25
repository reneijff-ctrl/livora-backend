package com.joinlivora.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.payout.CreatorSettingsService;
import com.joinlivora.backend.payout.dto.CreatorSettingsDto;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class CreatorSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private CreatorSettingsService creatorSettingsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "creator@test.com", roles = "CREATOR")
    void getSettings_ShouldReturnSettings() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");
        when(userService.getByEmail("creator@test.com")).thenReturn(user);

        CreatorSettingsDto settings = CreatorSettingsDto.builder()
                .username("testcreator")
                .category("gaming")
                .active(true)
                .build();
        when(creatorSettingsService.getSettings(user)).thenReturn(settings);

        mockMvc.perform(get("/api/creator/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testcreator"))
                .andExpect(jsonPath("$.category").value("gaming"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(username = "creator@test.com", roles = "CREATOR")
    void updateSettings_AsCreator_ShouldReturnForbidden() throws Exception {
        CreatorSettingsDto settings = CreatorSettingsDto.builder()
                .active(false)
                .build();
        
        mockMvc.perform(put("/api/creator/settings")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(settings)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void updateSettings_AsAdmin_ShouldSucceed() throws Exception {
        User user = new User();
        user.setEmail("admin@test.com");
        when(userService.getByEmail("admin@test.com")).thenReturn(user);

        CreatorSettingsDto settings = CreatorSettingsDto.builder()
                .username("updated_username")
                .active(false)
                .build();
        
        when(creatorSettingsService.updateSettings(eq(user), any(CreatorSettingsDto.class))).thenReturn(settings);

        mockMvc.perform(put("/api/creator/settings")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(settings)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getSettings_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/creator/settings"))
                .andExpect(status().isForbidden());
    }
}








