package com.joinlivora.backend.creator.api;

import com.joinlivora.backend.creator.dto.ExploreCreatorDto;
import com.joinlivora.backend.creator.dto.PublicCreatorProfileDto;
import com.joinlivora.backend.creator.service.CreatorProfileService;
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

    @GetMapping
    public ResponseEntity<Page<ExploreCreatorDto>> getExploreCreators(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(creatorProfileService.getExploreCreatorsList(pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PublicCreatorProfileDto> getPublicCreatorProfile(@PathVariable Long userId) {
        return creatorProfileService.getPublicProfileById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
