package com.joinlivora.backend.config;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityConfigAdminTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void whenAnonymous_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenUserRole_thenForbidden() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");
        user.setRole(Role.USER);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenCreatorRole_thenForbidden() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");
        user.setRole(Role.CREATOR);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAdminRole_thenOk() throws Exception {
        User user = new User();
        user.setEmail("admin@test.com");
        user.setRole(Role.ADMIN);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}








