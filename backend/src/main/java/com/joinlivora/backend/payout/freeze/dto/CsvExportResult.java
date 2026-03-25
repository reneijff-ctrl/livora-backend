package com.joinlivora.backend.payout.freeze.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CsvExportResult {
    private final String csv;
    private final String sha256Hash;
}
