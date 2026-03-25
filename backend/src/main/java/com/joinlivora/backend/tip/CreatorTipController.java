package com.joinlivora.backend.tip;

import com.joinlivora.backend.tip.dto.CreatorTipDto;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/creator/tips")
@RequiredArgsConstructor
@Slf4j
public class CreatorTipController {

    private final TipService tipService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('CREATOR')")
    public List<CreatorTipDto> getMyTips(Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        log.info("Creator {} fetching their tips", user.getEmail());
        return tipService.getTipsForCreator(user);
    }
}
