package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/creator/ppv")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR') or hasRole('ADMIN')")
public class CreatorPpvController {

    private final PpvService ppvService;
    private final UserService userService;

    @GetMapping("/mine")
    public ResponseEntity<?> getMyContent(@AuthenticationPrincipal UserDetails userDetails) {
        User creator = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ppvService.getCreatorPpvContent(creator));
    }

    @PostMapping
    public ResponseEntity<?> createContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PpvContent content
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ppvService.createContent(creator, content));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestBody PpvContent content
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ppvService.updateContent(creator, id, content));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id
    ) {
        User creator = userService.getByEmail(userDetails.getUsername());
        ppvService.deleteContent(creator, id);
        return ResponseEntity.ok().build();
    }
}
