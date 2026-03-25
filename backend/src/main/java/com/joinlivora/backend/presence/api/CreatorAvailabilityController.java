package com.joinlivora.backend.presence.api;

import com.joinlivora.backend.presence.dto.CreatorAvailabilityResponse;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/public/creators")
@RequiredArgsConstructor
public class CreatorAvailabilityController {

    private final CreatorPresenceService creatorPresenceService;
    private final LiveViewerCounterService liveViewerCounterService;
    private final StreamRepository streamRepository;

    @GetMapping("/{creatorUserId}/availability")
    public ResponseEntity<CreatorAvailabilityResponse> getAvailability(@PathVariable Long creatorUserId) {
        var liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId);
        Stream activeStream = liveStreams.isEmpty() ? null : liveStreams.get(0);
        return ResponseEntity.ok(CreatorAvailabilityResponse.builder()
                .creatorUserId(creatorUserId)
                .availability(creatorPresenceService.getAvailability(creatorUserId))
                .isPaid(activeStream != null && activeStream.isPaid())
                .viewerCount(liveViewerCounterService.getViewerCount(creatorUserId))
                .admissionPrice(activeStream != null ? activeStream.getAdmissionPrice() : java.math.BigDecimal.ZERO)
                .build());
    }
}
