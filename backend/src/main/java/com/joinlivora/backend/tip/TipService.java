package com.joinlivora.backend.tip;

import com.joinlivora.backend.tip.dto.CreatorTipDto;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

@Service("directTipService")
@RequiredArgsConstructor
@Slf4j
public class TipService {

    private final CreatorTipRepository tipRepository;
    private final com.joinlivora.backend.payout.CreatorEarningsService creatorEarningsService;

    @Transactional
    public DirectTip saveTip(DirectTip tip) {
        log.info("Persisting tip from user {} to creator {} for {} {}",
                tip.getUser() != null ? tip.getUser().getId() : "null",
                tip.getCreator() != null ? tip.getCreator().getId() : "null",
                tip.getAmount(), tip.getCurrency());
        return tipRepository.save(tip);
    }

    @Transactional(readOnly = true)
    public Optional<DirectTip> findById(UUID id) {
        return tipRepository.findById(id);
    }

    @Transactional
    public void updateStatus(UUID id, TipStatus status) {
        tipRepository.findById(id).ifPresent(tip -> {
            log.info("Updating tip {} status to {}", id, status);
            tip.setStatus(status);
            tipRepository.save(tip);
        });
    }

    @Transactional
    public void completeTip(UUID tipId) {
        tipRepository.findById(tipId).ifPresentOrElse(tip -> {
            if (tip.getStatus() == TipStatus.COMPLETED) {
                log.warn("Tip {} is already COMPLETED", tipId);
                return;
            }
            log.info("Completing tip {}", tipId);
            tip.setStatus(TipStatus.COMPLETED);
            tipRepository.save(tip);

            creatorEarningsService.recordDirectTipEarning(tip);
        }, () -> {
            log.error("Tip {} not found for completion", tipId);
            throw new com.joinlivora.backend.exception.ResourceNotFoundException("Tip not found: " + tipId);
        });
    }

    @Transactional(readOnly = true)
    public List<CreatorTipDto> getTipsForCreator(User creator) {
        log.info("Fetching completed tips for creator {}", creator.getId());
        return tipRepository.findAllByCreatorAndStatusOrderByCreatedAtDesc(creator, TipStatus.COMPLETED)
                .stream()
                .map(this::mapToCreatorDto)
                .collect(Collectors.toList());
    }

    private CreatorTipDto mapToCreatorDto(DirectTip tip) {
        return CreatorTipDto.builder()
                .id(tip.getId())
                .amount(tip.getAmount())
                .fromUserId(maskUserId(tip.getUser().getId()))
                .createdAt(tip.getCreatedAt())
                .build();
    }

    private String maskUserId(Long id) {
        if (id == null) return "u_***";
        String s = String.valueOf(id);
        if (s.length() <= 2) return "u_***";
        return "u_" + s.charAt(0) + "***" + s.charAt(s.length() - 1);
    }
}
