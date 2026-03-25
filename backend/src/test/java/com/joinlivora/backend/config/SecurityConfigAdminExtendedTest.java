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
public class SecurityConfigAdminExtendedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void whenAdminRole_accessSubscriptions_thenOk() throws Exception {
        User user = new User();
        user.setEmail("admin@test.com");
        user.setRole(Role.ADMIN);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/admin/subscriptions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void whenUserRole_accessSubscriptions_thenForbidden() throws Exception {
        User user = new User();
        user.setEmail("creator@test.com");
        user.setRole(Role.USER);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/admin/subscriptions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAdminRole_accessPayouts_thenOk() throws Exception {
        User user = new User();
        user.setEmail("admin@test.com");
        user.setRole(Role.ADMIN);
        String token = jwtService.generateAccessToken(user);

        // Accessing /api/admin/payouts (one of the AdminPayoutControllers)
        mockMvc.perform(get("/api/admin/payouts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void whenAdminRole_accessLegacyPayouts_thenOk() throws Exception {
        User user = new User();
        user.setEmail("admin@test.com");
        user.setRole(Role.ADMIN);
        String token = jwtService.generateAccessToken(user);

        // Accessing /admin/payouts (the other AdminPayoutController)
        mockMvc.perform(get("/admin/payouts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void whenAdminRole_accessInternal_thenOk() throws Exception {
        User user = new User();
        user.setEmail("admin@test.com");
        user.setRole(Role.ADMIN);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/internal/chargebacks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}








