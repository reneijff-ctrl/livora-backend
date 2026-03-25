package com.joinlivora.backend.livestream.api;

import com.joinlivora.backend.livestream.dto.UnlockResponse;
import com.joinlivora.backend.livestream.service.UnlockLivestreamService;
import com.joinlivora.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/livestream")
@RequiredArgsConstructor
@Slf4j
public class UnlockLivestreamController {

    private final UnlockLivestreamService unlockService;

    @PostMapping("/{creatorUserId}/unlock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnlockResponse> unlockStream(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long creatorUserId
    ) {
        log.info("CONTROLLER: POST /api/livestream/{}/unlock - viewerUserId={}", 
                creatorUserId, principal != null ? principal.getUserId() : "null");
                
        Long viewerUserId = principal.getUserId();
        return ResponseEntity.ok(unlockService.unlockStream(creatorUserId, viewerUserId));
    }
}
