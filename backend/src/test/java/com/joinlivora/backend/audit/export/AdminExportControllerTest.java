package com.joinlivora.backend.audit.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
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
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class AdminExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExportJobRepository exportJobRepository;

    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setup() {
        User adminUser = new User("admin@test.com", "password", Role.ADMIN);
        adminUser.setId(1L);
        adminPrincipal = new UserPrincipal(adminUser);
    }

    @Test
    void getJobStatus_ShouldReturnJob() throws Exception {
        Long jobId = 1L;
        Instant now = Instant.now();
        ExportJob job = ExportJob.builder()
                .id(jobId)
                .status("COMPLETED")
                .createdAt(now)
                .completedAt(now.plusSeconds(60))
                .errorMessage(null)
                .build();

        when(exportJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/admin/export/" + jobId)
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.completedAt").exists())
                .andExpect(jsonPath("$.errorMessage").isEmpty());
    }

    @Test
    void getJobStatus_NotFound_ShouldReturn404() throws Exception {
        Long jobId = 999L;
        when(exportJobRepository.findById(jobId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/export/" + jobId)
                        .with(user(adminPrincipal)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getJobStatus_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/export/1"))
                .andExpect(status().isForbidden());
    }
}








