package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.CollusionOverrideRequest;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/collusion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCollusionController {

    private final CreatorCollusionRecordRepository recordRepository;
    private final CreatorTrustService trustService;
    private final UserService userService;

    @GetMapping("/creators")
    public ResponseEntity<Page<CreatorCollusionRecord>> getAllRecords(
            @PageableDefault(size = 20, sort = "evaluatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(recordRepository.findAll(pageable));
    }

    @GetMapping("/{creatorId}")
    public ResponseEntity<Page<CreatorCollusionRecord>> getRecordsByCreatorId(
            @PathVariable UUID creatorId,
            @PageableDefault(size = 20, sort = "evaluatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(recordRepository.findAllByCreatorIdOrderByEvaluatedAtDesc(creatorId, pageable));
    }

    @PostMapping("/override/{creatorId}")
    public ResponseEntity<Void> overrideCollusion(
            @PathVariable UUID creatorId,
            @RequestBody CollusionOverrideRequest request,
            @AuthenticationPrincipal UserDetails adminDetails
    ) {
        User creator = userService.getById(creatorId.getLeastSignificantBits());
        User admin = userService.getByEmail(adminDetails.getUsername());
        
        trustService.override(creator, request.getScore(), request.getReason(), admin);
        
        return ResponseEntity.noContent().build();
    }
}
