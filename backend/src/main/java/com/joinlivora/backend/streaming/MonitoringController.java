package com.joinlivora.backend.streaming;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {

    private final MeterRegistry meterRegistry;

    @PostMapping("/playback-error")
    public ResponseEntity<Void> reportPlaybackError(@RequestBody PlaybackErrorRequest request) {
        log.warn("Monitoring: Playback error reported: streamId={}, error={}, detail={}", 
                request.getStreamId(), request.getErrorType(), request.getDetail());
        
        meterRegistry.counter("streaming.playback.errors", 
                "streamId", request.getStreamId(),
                "errorType", request.getErrorType()
        ).increment();
        
        return ResponseEntity.ok().build();
    }

    @Data
    public static class PlaybackErrorRequest {
        private String streamId;
        private String errorType;
        private String detail;
    }
}
