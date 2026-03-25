package com.joinlivora.backend.controller;

import com.joinlivora.backend.payout.CreatorBalanceService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.payout.dto.*;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/creator/earnings")
@RequiredArgsConstructor
@Slf4j
public class CreatorEarningsController {

    private final CreatorEarningsService creatorEarningsService;
    private final CreatorBalanceService creatorBalanceService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public CreatorEarningsOverviewDTO getEarnings(Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        log.info("AUDIT: User {} accessed earnings overview for creator ID {}", principal.getName(), user.getId());
        return creatorEarningsService.getEarningsOverview(user);
    }

    @GetMapping("/legacy")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public CreatorEarningsDTO getLegacyEarnings(Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        return creatorEarningsService.getAggregatedEarnings(user);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('CREATOR')")
    public CreatorEarningsSummary getEarningsSummary(Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        log.info("AUDIT: User {} accessed their earnings summary", principal.getName());
        return creatorEarningsService.getEarningsSummary(user);
    }

    @GetMapping("/balance")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorEarningsResponseDTO> getBalance(Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        log.info("AUDIT: User {} fetched their own CreatorEarnings balance", principal.getName());
        return creatorEarningsService.getBalance(user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/breakdown")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public EarningsBreakdownDTO getEarningsBreakdown(Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        log.info("AUDIT: User {} accessed earnings breakdown for creator ID {}", principal.getName(), user.getId());
        return creatorBalanceService.getEarningsBreakdown(user);
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public CreatorEarningsReportDTO getEarningsReport(Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        log.info("AUDIT: User {} accessed detailed earnings report for creator ID {}", principal.getName(), user.getId());
        return creatorEarningsService.getEarningsReport(user);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public List<CreatorEarningDto> getRecentTransactions(Principal principal) {
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));
        log.info("AUDIT: User {} accessed recent transactions for creator ID {}", principal.getName(), user.getId());
        return creatorEarningsService.getRecentTransactions(user, 20);
    }
}
