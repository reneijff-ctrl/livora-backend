package com.joinlivora.backend.controller;

import com.joinlivora.backend.security.JwtService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminLandingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void whenAdminRole_thenGetLanding() throws Exception {
        User user = new User();
        user.setEmail("admin@test.com");
        user.setRole(Role.ADMIN);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/admin")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Admin dashboard coming soon"));
    }

    @Test
    void whenUserRole_thenForbidden() throws Exception {
        User user = new User();
        user.setEmail("user@test.com");
        user.setRole(Role.USER);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/admin")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAnonymous_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/admin")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}








