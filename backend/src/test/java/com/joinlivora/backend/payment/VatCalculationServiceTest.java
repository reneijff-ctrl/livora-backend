package com.joinlivora.backend.payment;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class VatCalculationServiceTest {

    private final VatCalculationService service = new VatCalculationService();

    @Test
    void calculateVat_EUCountry_ShouldApplyCorrectRate() {
        // Germany (DE) has 19% VAT
        BigDecimal netAmount = new BigDecimal("100.00");
        VatCalculationService.VatResult result = service.calculateVat(netAmount, "DE", false);

        assertEquals(new BigDecimal("19.00").setScale(2, RoundingMode.HALF_UP), result.vatAmount());
        assertEquals(new BigDecimal("119.00").setScale(2, RoundingMode.HALF_UP), result.grossAmount());
        assertEquals(new BigDecimal("0.19"), result.vatRate());
    }

    @Test
    void calculateVat_EUCountryWithVatExempt_ShouldApplyZeroVat() {
        BigDecimal netAmount = new BigDecimal("100.00");
        VatCalculationService.VatResult result = service.calculateVat(netAmount, "DE", true);

        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), result.vatAmount());
        assertEquals(new BigDecimal("100.00").setScale(2, RoundingMode.HALF_UP), result.grossAmount());
        assertEquals(BigDecimal.ZERO, result.vatRate());
    }

    @Test
    void calculateVat_NonEUCountry_ShouldApplyZeroVat() {
        // USA (US) is not in EU
        BigDecimal netAmount = new BigDecimal("100.00");
        VatCalculationService.VatResult result = service.calculateVat(netAmount, "US", false);

        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), result.vatAmount());
        assertEquals(new BigDecimal("100.00").setScale(2, RoundingMode.HALF_UP), result.grossAmount());
        assertEquals(BigDecimal.ZERO, result.vatRate());
    }

    @Test
    void calculateVat_DifferentEURates_ShouldApplyCorrectRate() {
        // Hungary (HU) has 27% VAT
        BigDecimal netAmount = new BigDecimal("100.00");
        VatCalculationService.VatResult resultHU = service.calculateVat(netAmount, "HU", false);
        assertEquals(new BigDecimal("27.00").setScale(2, RoundingMode.HALF_UP), resultHU.vatAmount());

        // Luxembourg (LU) has 17% VAT
        VatCalculationService.VatResult resultLU = service.calculateVat(netAmount, "LU", false);
        assertEquals(new BigDecimal("17.00").setScale(2, RoundingMode.HALF_UP), resultLU.vatAmount());
    }

    @Test
    void calculateVat_Rounding_ShouldWorkCorrectly() {
        // Finland (FI) has 25.5% VAT
        BigDecimal netAmount = new BigDecimal("10.00");
        VatCalculationService.VatResult result = service.calculateVat(netAmount, "FI", false);

        // 10.00 * 0.255 = 2.55
        assertEquals(new BigDecimal("2.55").setScale(2, RoundingMode.HALF_UP), result.vatAmount());
        assertEquals(new BigDecimal("12.55").setScale(2, RoundingMode.HALF_UP), result.grossAmount());
    }

    @Test
    void calculateVat_NullCountry_ShouldApplyZeroVat() {
        BigDecimal netAmount = new BigDecimal("100.00");
        VatCalculationService.VatResult result = service.calculateVat(netAmount, null, false);

        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), result.vatAmount());
        assertEquals(BigDecimal.ZERO, result.vatRate());
    }

    @Test
    void calculateVat_NullAmount_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> service.calculateVat(null, "DE", false));
    }

    @Test
    void calculateVatFromGross_EUCountry_ShouldCalculateBackwards() {
        // Germany (DE) has 19% VAT. Gross 119 -> Net 100, VAT 19
        BigDecimal grossAmount = new BigDecimal("119.00");
        VatCalculationService.VatResult result = service.calculateVatFromGross(grossAmount, "DE", false);

        assertEquals(new BigDecimal("100.00").setScale(2, RoundingMode.HALF_UP), result.netAmount());
        assertEquals(new BigDecimal("19.00").setScale(2, RoundingMode.HALF_UP), result.vatAmount());
        assertEquals(new BigDecimal("0.19"), result.vatRate());
    }

    @Test
    void calculateVatFromGross_NonEUCountry_ShouldApplyZeroVat() {
        BigDecimal grossAmount = new BigDecimal("100.00");
        VatCalculationService.VatResult result = service.calculateVatFromGross(grossAmount, "US", false);

        assertEquals(new BigDecimal("100.00").setScale(2, RoundingMode.HALF_UP), result.netAmount());
        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), result.vatAmount());
        assertEquals(BigDecimal.ZERO, result.vatRate());
    }
}








