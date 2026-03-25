package com.joinlivora.backend.payout.freeze;

import com.joinlivora.backend.payout.freeze.dto.CsvExportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutFreezeAuditServiceTest {

    @Mock
    private PayoutFreezeAuditRepository auditRepository;

    @InjectMocks
    private PayoutFreezeAuditService auditService;

    @Test
    void getAuditForCreator_ShouldCallRepository() {
        Long creatorId = 123L;
        List<PayoutFreezeAuditLog> expectedLogs = List.of(
                PayoutFreezeAuditLog.builder().creatorId(creatorId).action("FREEZE").build()
        );

        when(auditRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId)).thenReturn(expectedLogs);

        List<PayoutFreezeAuditLog> result = auditService.getAuditForCreator(creatorId);

        assertThat(result).isEqualTo(expectedLogs);
        verify(auditRepository).findByCreatorIdOrderByCreatedAtDesc(creatorId);
    }

    @Test
    void getAllAudit_ShouldCallRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PayoutFreezeAuditLog> expectedPage = new PageImpl<>(List.of(
                PayoutFreezeAuditLog.builder().creatorId(123L).action("FREEZE").build()
        ));

        when(auditRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(expectedPage);

        Page<PayoutFreezeAuditLog> result = auditService.getAllAudit(pageable);

        assertThat(result).isEqualTo(expectedPage);
        verify(auditRepository).findAllByOrderByCreatedAtDesc(pageable);
    }

    @Test
    void generateCsvForCreator_ShouldReturnValidCsv() {
        Long creatorId = 123L;
        Instant now = Instant.parse("2024-02-21T17:33:00Z");
        PayoutFreezeAuditLog log1 = PayoutFreezeAuditLog.builder()
                .creatorId(creatorId)
                .action("FREEZE")
                .reason("Fraud suspected, check logs")
                .adminId(1L)
                .createdAt(now)
                .build();
        PayoutFreezeAuditLog log2 = PayoutFreezeAuditLog.builder()
                .creatorId(creatorId)
                .action("UNFREEZE")
                .reason("Manual unfreeze \"fixed\"")
                .adminId(2L)
                .createdAt(now.plusSeconds(3600))
                .build();

        when(auditRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId)).thenReturn(List.of(log2, log1));

        String csv = auditService.generateCsvForCreator(creatorId);

        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("creator,action,reason,adminId,createdAt");
        
        // Log 2 (UNFREEZE) is first because of fetch order
        assertThat(lines[1]).isEqualTo("123,UNFREEZE,\"Manual unfreeze \"\"fixed\"\"\",2,2024-02-21T18:33:00Z");
        
        // Log 1 (FREEZE)
        assertThat(lines[2]).isEqualTo("123,FREEZE,\"Fraud suspected, check logs\",1,2024-02-21T17:33:00Z");
    }

    @Test
    void generateCsvForCreator_WithDateRange_ShouldCallRepositoryWithFiltering() {
        Long creatorId = 123L;
        Instant from = Instant.parse("2024-02-01T00:00:00Z");
        Instant to = Instant.parse("2024-02-28T23:59:59Z");
        PayoutFreezeAuditLog log = PayoutFreezeAuditLog.builder()
                .creatorId(creatorId)
                .action("FREEZE")
                .reason("Test")
                .createdAt(from.plusSeconds(100))
                .build();

        when(auditRepository.findByCreatorIdAndCreatedAtBetweenOrderByCreatedAtDesc(creatorId, from, to))
                .thenReturn(List.of(log));

        String csv = auditService.generateCsvForCreator(creatorId, from, to);

        assertThat(csv).contains("FREEZE,Test");
        verify(auditRepository).findByCreatorIdAndCreatedAtBetweenOrderByCreatedAtDesc(creatorId, from, to);
    }

    @Test
    void generateGlobalCsv_NoDateRange_ShouldFetchAll() {
        PayoutFreezeAuditLog log = PayoutFreezeAuditLog.builder()
                .creatorId(1L)
                .action("FREEZE")
                .reason("Reason")
                .createdAt(Instant.now())
                .build();

        when(auditRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(log));

        String csv = auditService.generateGlobalCsv(null, null);

        assertThat(csv).contains("1,FREEZE,Reason");
        verify(auditRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void generateGlobalCsv_WithDateRange_ShouldFetchFiltered() {
        Instant from = Instant.parse("2024-02-01T00:00:00Z");
        Instant to = Instant.parse("2024-02-28T23:59:59Z");
        PayoutFreezeAuditLog log = PayoutFreezeAuditLog.builder()
                .creatorId(2L)
                .action("UNFREEZE")
                .reason("Global test")
                .createdAt(from.plusSeconds(100))
                .build();

        when(auditRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)).thenReturn(List.of(log));

        String csv = auditService.generateGlobalCsv(from, to);

        assertThat(csv).contains("2,UNFREEZE,Global test");
        verify(auditRepository).findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }

    @Test
    void generateSignedGlobalCsv_ShouldReturnResultWithHash() {
        Instant from = Instant.parse("2024-02-01T00:00:00Z");
        Instant to = Instant.parse("2024-02-28T23:59:59Z");
        PayoutFreezeAuditLog log = PayoutFreezeAuditLog.builder()
                .creatorId(2L)
                .action("UNFREEZE")
                .reason("Global test")
                .createdAt(from.plusSeconds(100))
                .build();

        when(auditRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)).thenReturn(List.of(log));

        CsvExportResult result = auditService.generateSignedGlobalCsv(from, to);

        assertThat(result.getCsv()).contains("2,UNFREEZE,Global test");
        assertThat(result.getSha256Hash()).isNotBlank();
        verify(auditRepository).findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }
}








