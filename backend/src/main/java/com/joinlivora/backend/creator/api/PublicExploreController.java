package com.joinlivora.backend.creator.api;

import com.joinlivora.backend.creator.dto.CreatorProfileDTO;
import com.joinlivora.backend.creator.dto.ExploreCreatorDto;
import com.joinlivora.backend.creator.dto.PublicCreatorProfileDto;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.creator.service.CreatorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/creators")
@RequiredArgsConstructor
public class PublicExploreController {

    private final CreatorProfileService creatorProfileService;
    private final CreatorSearchService creatorSearchService;

    @GetMapping
    public ResponseEntity<Page<ExploreCreatorDto>> getExploreCreators(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(creatorSearchService.getExploreCreatorsList(pageable));
    }

    @GetMapping("/top")
    public ResponseEntity<Page<CreatorProfileDTO>> getTopCreators(
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(creatorSearchService.getCreators(
                null, null, country, null, "all", "followers",
                null, null, null, null, null, null, pageable));
    }

    @GetMapping("/live")
    public ResponseEntity<Page<CreatorProfileDTO>> getLiveCreators(
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(creatorSearchService.getCreators(
                null, null, country, true, "all", "viewers",
                null, null, null, null, null, null, pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PublicCreatorProfileDto> getPublicCreatorProfile(@PathVariable Long userId) {
        return creatorProfileService.getPublicProfileById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
