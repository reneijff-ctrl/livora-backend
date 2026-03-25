package com.joinlivora.backend.streaming;

import com.joinlivora.backend.admin.dto.StreamRiskStatusDTO;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.streaming.service.StreamRiskMonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminStreamRiskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StreamRiskMonitorService streamRiskMonitorService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllStreamRisks_ShouldReturnList() throws Exception {
        UUID streamId = UUID.randomUUID();
        StreamRiskStatusDTO risk = StreamRiskStatusDTO.builder()
                .streamId(streamId)
                .creatorId(123L)
                .creatorUsername("test_creator")
                .viewerCount(100)
                .riskLevel(RiskLevel.LOW)
                .riskScore(10)
                .build();

        when(streamRiskMonitorService.getAllStreamRisks()).thenReturn(List.of(risk));

        mockMvc.perform(get("/api/admin/streams/risk"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].streamId").value(streamId.toString()))
                .andExpect(jsonPath("$[0].creatorUsername").value("test_creator"))
                .andExpect(jsonPath("$[0].riskLevel").value("LOW"))
                .andExpect(jsonPath("$[0].riskScore").value(10));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllStreamRisks_AsUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/streams/risk"))
                .andExpect(status().isForbidden());
    }
}
