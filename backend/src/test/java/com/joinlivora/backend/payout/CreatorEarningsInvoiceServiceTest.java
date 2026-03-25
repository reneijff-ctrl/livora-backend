package com.joinlivora.backend.payout;

import com.joinlivora.backend.config.PlatformCompanyProperties;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorEarningsInvoiceServiceTest {

    @Mock
    private CreatorEarningRepository earningRepository;

    @Mock
    private CreatorEarningsInvoiceRepository invoiceRepository;

    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;

    @Mock
    private PlatformCompanyProperties companyProperties;

    private CreatorEarningsInvoiceService invoiceService;

    private User creator;
    private YearMonth month;
    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        invoiceService = new CreatorEarningsInvoiceService(earningRepository, invoiceRepository, creatorProfileRepository, companyProperties);
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@example.com");

        month = YearMonth.of(2025, 12);
        start = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        end = month.atEndOfMonth().atTime(23, 59, 59, 999999999).toInstant(ZoneOffset.UTC);
    }

    @Test
    void testGenerateMonthlyInvoices() {
        // Given
        when(earningRepository.findCreatorsWithUninvoicedEarnings(any(), any())).thenReturn(List.of(creator));
        when(earningRepository.findCurrenciesByCreatorWithUninvoicedEarnings(eq(creator), any(), any())).thenReturn(List.of("EUR"));
        
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder()
                .displayName("Creator Name")
                .user(creator)
                .build();
        when(creatorProfileRepository.findByUser(creator)).thenReturn(Optional.of(profile));
        
        when(companyProperties.getName()).thenReturn("Livora Platform");
        when(companyProperties.getAddress()).thenReturn("Platform Address");
        when(companyProperties.getEmail()).thenReturn("billing@joinlivora.com");
        when(companyProperties.getVatNumber()).thenReturn("VAT123");

        CreatorEarning earning = CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("100.00"))
                .platformFee(new BigDecimal("30.00"))
                .netAmount(new BigDecimal("70.00"))
                .currency("EUR")
                .build();
        
        when(earningRepository.findUninvoicedEarningsByCreatorCurrencyAndPeriod(eq(creator), eq("EUR"), any(), any()))
                .thenReturn(List.of(earning));
        
        when(invoiceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        invoiceService.generateMonthlyInvoices(month);

        // Then
        ArgumentCaptor<CreatorEarningsInvoice> invoiceCaptor = ArgumentCaptor.forClass(CreatorEarningsInvoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        
        CreatorEarningsInvoice savedInvoice = invoiceCaptor.getValue();
        assertEquals(creator, savedInvoice.getCreator());
        assertEquals("EUR", savedInvoice.getCurrency());
        assertEquals(new BigDecimal("100.00"), savedInvoice.getGrossEarnings());
        assertEquals(new BigDecimal("30.00"), savedInvoice.getPlatformFee());
        assertEquals(new BigDecimal("70.00"), savedInvoice.getNetEarnings());
        assertTrue(savedInvoice.getInvoiceNumber().contains("202512"));
        assertEquals("Creator Name", savedInvoice.getCreatorName());
        assertEquals("creator@example.com", savedInvoice.getCreatorEmail());
        assertEquals("Livora Platform", savedInvoice.getSellerName());
        assertEquals("Platform Address", savedInvoice.getSellerAddress());
        assertEquals("billing@joinlivora.com", savedInvoice.getSellerEmail());
        assertEquals("VAT123", savedInvoice.getSellerVatNumber());
        
        verify(earningRepository, times(1)).save(earning);
        assertTrue(earning.isLocked());
        assertEquals(savedInvoice, earning.getInvoice());
    }

    @Test
    void testNoCreators() {
        // Given
        lenient().when(earningRepository.findCreatorsWithUninvoicedEarnings(any(), any())).thenReturn(Collections.emptyList());

        // When
        invoiceService.generateMonthlyInvoices(month);

        // Then
        verify(invoiceRepository, never()).save(any());
    }
}








