package com.joinlivora.backend.payment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Service for calculating VAT based on EU rules.
 */
@Service("vatCalculationService")
public class VatCalculationService {

    private static final Set<String> EU_COUNTRIES = Set.of(
            "AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR",
            "GR", "HR", "HU", "IE", "IT", "LT", "LU", "LV", "MT", "NL", "PL",
            "PT", "RO", "SE", "SI", "SK"
    );

    private static final Map<String, BigDecimal> VAT_RATES;

    static {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("AT", new BigDecimal("0.20"));
        rates.put("BE", new BigDecimal("0.21"));
        rates.put("BG", new BigDecimal("0.20"));
        rates.put("CY", new BigDecimal("0.19"));
        rates.put("CZ", new BigDecimal("0.21"));
        rates.put("DE", new BigDecimal("0.19"));
        rates.put("DK", new BigDecimal("0.25"));
        rates.put("EE", new BigDecimal("0.22"));
        rates.put("ES", new BigDecimal("0.21"));
        rates.put("FI", new BigDecimal("0.255"));
        rates.put("FR", new BigDecimal("0.20"));
        rates.put("GR", new BigDecimal("0.24"));
        rates.put("HR", new BigDecimal("0.25"));
        rates.put("HU", new BigDecimal("0.27"));
        rates.put("IE", new BigDecimal("0.23"));
        rates.put("IT", new BigDecimal("0.22"));
        rates.put("LT", new BigDecimal("0.21"));
        rates.put("LU", new BigDecimal("0.17"));
        rates.put("LV", new BigDecimal("0.21"));
        rates.put("MT", new BigDecimal("0.18"));
        rates.put("NL", new BigDecimal("0.21"));
        rates.put("PL", new BigDecimal("0.23"));
        rates.put("PT", new BigDecimal("0.23"));
        rates.put("RO", new BigDecimal("0.19"));
        rates.put("SE", new BigDecimal("0.25"));
        rates.put("SI", new BigDecimal("0.22"));
        rates.put("SK", new BigDecimal("0.20"));
        VAT_RATES = Collections.unmodifiableMap(rates);
    }

    private static final BigDecimal DEFAULT_EU_VAT_RATE = new BigDecimal("0.21");

    /**
     * Calculates VAT for a given net amount and country.
     *
     * @param netAmount    The net amount before VAT.
     * @param countryCode  ISO 3166-1 alpha-2 country code.
     * @param isVatExempt Whether the customer is exempt from VAT (e.g. B2B reverse charge).
     * @return VatResult containing the calculated amounts.
     */
    public VatResult calculateVat(BigDecimal netAmount, String countryCode, boolean isVatExempt) {
        if (netAmount == null) {
            throw new IllegalArgumentException("Net amount cannot be null");
        }

        String normalizedCountry = countryCode != null ? countryCode.toUpperCase() : "";

        if (isVatExempt || !EU_COUNTRIES.contains(normalizedCountry)) {
            return new VatResult(
                    netAmount,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    netAmount.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO
            );
        }

        BigDecimal vatRate = VAT_RATES.getOrDefault(normalizedCountry, DEFAULT_EU_VAT_RATE);
        BigDecimal vatAmount = netAmount.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossAmount = netAmount.add(vatAmount).setScale(2, RoundingMode.HALF_UP);

        return new VatResult(netAmount, vatAmount, grossAmount, vatRate);
    }

    /**
     * Calculates VAT from a gross amount (amount including VAT).
     *
     * @param grossAmount  The total amount including VAT.
     * @param countryCode  ISO 3166-1 alpha-2 country code.
     * @param isVatExempt Whether the customer is exempt from VAT.
     * @return VatResult containing the calculated amounts.
     */
    public VatResult calculateVatFromGross(BigDecimal grossAmount, String countryCode, boolean isVatExempt) {
        if (grossAmount == null) {
            throw new IllegalArgumentException("Gross amount cannot be null");
        }

        String normalizedCountry = countryCode != null ? countryCode.toUpperCase() : "";

        if (isVatExempt || !EU_COUNTRIES.contains(normalizedCountry)) {
            return new VatResult(
                    grossAmount.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    grossAmount.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO
            );
        }

        BigDecimal vatRate = VAT_RATES.getOrDefault(normalizedCountry, DEFAULT_EU_VAT_RATE);
        // net = gross / (1 + rate)
        BigDecimal netAmount = grossAmount.divide(BigDecimal.ONE.add(vatRate), 2, RoundingMode.HALF_UP);
        BigDecimal vatAmount = grossAmount.subtract(netAmount).setScale(2, RoundingMode.HALF_UP);

        return new VatResult(netAmount, vatAmount, grossAmount.setScale(2, RoundingMode.HALF_UP), vatRate);
    }

    public record VatResult(
            BigDecimal netAmount,
            BigDecimal vatAmount,
            BigDecimal grossAmount,
            BigDecimal vatRate
    ) {
    }
}
