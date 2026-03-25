package com.joinlivora.backend.payout.freeze;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.audit.export.AsyncExportService;
import com.joinlivora.backend.audit.export.ExportJob;
import com.joinlivora.backend.audit.export.ExportJobRepository;
import com.joinlivora.backend.payout.freeze.dto.CsvExportResult;
import com.joinlivora.backend.payout.freeze.dto.PayoutFreezeRequest;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class AdminPayoutFreezeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayoutFreezeService payoutFreezeService;

    @MockBean
    private PayoutFreezeAuditService auditService;

    @MockBean
    private AsyncExportService asyncExportService;

    @MockBean
    private ExportJobRepository exportJobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        User adminUser = new User("admin@test.com", "password", Role.ADMIN);
        adminUser.setId(100L);
        adminPrincipal = new UserPrincipal(adminUser);
    }

    @Test
    void freezeCreator_ShouldCallServiceAndReturnOk() throws Exception {
        PayoutFreezeRequest request = new PayoutFreezeRequest(1L, "Chargeback investigation");

        mockMvc.perform(post("/api/admin/payout-freeze/freeze")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payouts frozen for creator 1"));

        verify(payoutFreezeService).freezeCreator(eq(1L), eq("Chargeback investigation"), eq(100L));
    }

    @Test
    void unfreezeCreator_ShouldCallServiceAndReturnOk() throws Exception {
        mockMvc.perform(post("/api/admin/payout-freeze/unfreeze/1")
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payouts unfrozen for creator 1"));

        verify(payoutFreezeService).unfreezeCreator(eq(1L));
    }

    @Test
    @WithMockUser(roles = "USER")
    void freezeCreator_AsUser_ShouldReturnForbidden() throws Exception {
        PayoutFreezeRequest request = new PayoutFreezeRequest(1L, "Chargeback investigation");

        mockMvc.perform(post("/api/admin/payout-freeze/freeze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAuditLogs_ShouldReturnList() throws Exception {
        Long creatorId = 1L;
        PayoutFreezeAuditLog log1 = PayoutFreezeAuditLog.builder()
                .creatorId(creatorId)
                .action("FREEZE")
                .reason("Reason 1")
                .createdAt(java.time.Instant.now())
                .build();

        when(auditService.getAuditForCreator(creatorId)).thenReturn(java.util.List.of(log1));

        mockMvc.perform(get("/api/admin/payout-freeze/audit/" + creatorId)
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("FREEZE"))
                .andExpect(jsonPath("$[0].reason").value("Reason 1"));
    }

    @Test
    void getAllAuditLogs_ShouldReturnPaginatedResults() throws Exception {
        PayoutFreezeAuditLog log1 = PayoutFreezeAuditLog.builder()
                .creatorId(1L)
                .action("FREEZE")
                .reason("Reason 1")
                .createdAt(java.time.Instant.now())
                .build();

        Page<PayoutFreezeAuditLog> page = new PageImpl<>(
                java.util.List.of(log1),
                PageRequest.of(0, 20),
                1
        );

        when(auditService.getAllAudit(any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/payout-freeze/audit")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("FREEZE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void exportAuditLogs_ShouldReturnCsvFile() throws Exception {
        Long creatorId = 1L;
        String csvContent = "creator,action,reason,adminId,createdAt\n1,FREEZE,Reason,100,2024-02-21T17:33:00Z";

        when(auditService.generateCsvForCreator(eq(creatorId), eq(null), eq(null))).thenReturn(csvContent);

        mockMvc.perform(get("/api/admin/payout-freeze/audit/" + creatorId + "/export")
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=payout_freeze_audit_1.csv"))
                .andExpect(content().string(csvContent));
    }

    @Test
    void exportAuditLogs_WithDateRange_ShouldReturnCsvFile() throws Exception {
        Long creatorId = 1L;
        java.time.Instant from = java.time.Instant.parse("2024-02-01T00:00:00Z");
        java.time.Instant to = java.time.Instant.parse("2024-02-28T23:59:59Z");
        String csvContent = "creator,action,reason,adminId,createdAt\n1,FREEZE,Reason,100,2024-02-21T17:33:00Z";

        when(auditService.generateCsvForCreator(eq(creatorId), eq(from), eq(to))).thenReturn(csvContent);

        mockMvc.perform(get("/api/admin/payout-freeze/audit/" + creatorId + "/export")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(content().string(csvContent));

        verify(auditService).generateCsvForCreator(creatorId, from, to);
    }

    @Test
    void exportGlobalAuditLogs_ShouldReturnCsvFile() throws Exception {
        String csvContent = "creator,action,reason,adminId,createdAt\n1,FREEZE,Reason,100,2024-02-21T17:33:00Z";
        String hash = "hash123";
        CsvExportResult result = new CsvExportResult(csvContent, hash);

        when(auditService.generateSignedGlobalCsv(eq(null), eq(null))).thenReturn(result);

        mockMvc.perform(get("/api/admin/payout-freeze/audit/export")
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=payout_audit_global.csv"))
                .andExpect(header().string("X-File-SHA256", hash))
                .andExpect(content().string(csvContent));
    }

    @Test
    void exportGlobalAuditLogs_WithDateRange_ShouldReturnCsvFile() throws Exception {
        java.time.Instant from = java.time.Instant.parse("2024-02-01T00:00:00Z");
        java.time.Instant to = java.time.Instant.parse("2024-02-28T23:59:59Z");
        String csvContent = "creator,action,reason,adminId,createdAt\n1,FREEZE,Reason,100,2024-02-21T17:33:00Z";
        String hash = "hash123";
        CsvExportResult result = new CsvExportResult(csvContent, hash);

        when(auditService.generateSignedGlobalCsv(eq(from), eq(to))).thenReturn(result);

        mockMvc.perform(get("/api/admin/payout-freeze/audit/export")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=payout_audit_global.csv"))
                .andExpect(header().string("X-File-SHA256", hash))
                .andExpect(content().string(csvContent));

        verify(auditService).generateSignedGlobalCsv(from, to);
    }

    @Test
    void exportGlobalAuditLogsAsync_ShouldCreateJobAndReturnOk() throws Exception {
        java.time.Instant from = java.time.Instant.parse("2024-02-01T00:00:00Z");
        java.time.Instant to = java.time.Instant.parse("2024-02-28T23:59:59Z");

        ExportJob job = ExportJob.builder()
                .id(500L)
                .type("PAYOUT_AUDIT")
                .status("PENDING")
                .build();

        when(exportJobRepository.save(any(ExportJob.class))).thenReturn(job);

        mockMvc.perform(post("/api/admin/payout-freeze/audit/export/async")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(500))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(exportJobRepository).save(any(ExportJob.class));
        verify(asyncExportService).processGlobalAuditExport(eq(500L), eq(from), eq(to));
    }
}








