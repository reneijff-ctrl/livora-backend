package com.joinlivora.backend.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoreResult {
    private final String relativePath;
    private final String internalFilename;
    private final String absolutePath;
}
