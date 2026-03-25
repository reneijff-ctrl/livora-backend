package com.joinlivora.backend.payout;

import com.joinlivora.backend.content.ContentRepository;
import com.joinlivora.backend.creator.dto.CreatorDashboardStatisticsDTO;
import com.joinlivora.backend.creator.repository.CreatorPostRepository;
import com.joinlivora.backend.payout.dto.CreatorDashboardDto;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CreatorDashboardService {

    private final CreatorEarningRepository earningRepository;
    private final ContentRepository contentRepository;
    private final StreamRepository streamRepository;
    private final CreatorPostRepository creatorPostRepository;

    @Transactional(readOnly = true)
    public CreatorDashboardStatisticsDTO getStatistics(User creator) {
        long postsCount = creatorPostRepository.countByCreator(creator);
        long tipsCount = earningRepository.countByCreatorAndSource(creator, EarningSource.TIP);
        // Subscribers not yet implemented in a dedicated table, returning 0 as per requirements
        long subscribersCount = 0;

        return CreatorDashboardStatisticsDTO.builder()
                .postsCount(postsCount)
                .tipsCount(tipsCount)
                .subscribersCount(subscribersCount)
                .build();
    }

    @Transactional(readOnly = true)
    public CreatorDashboardDto getDashboard(User creator) {
        if (creator.getRole() != Role.CREATOR && creator.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("User is not a creator");
        }

        BigDecimal totalEarnings = earningRepository.sumTotalNetEarningsByCreator(creator);
        if (totalEarnings == null) {
            totalEarnings = BigDecimal.ZERO;
        }

        // Calculate available balance by summing available earnings
        BigDecimal availableBalance = earningRepository.findAvailableEarningsByCreator(creator)
                .stream()
                .map(CreatorEarning::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeStreams = streamRepository.countByCreatorIdAndIsLiveTrue(creator.getId());
        long contentCount = contentRepository.countByCreator(creator);
        
        // For totalSubscribers, using a proxy: number of subscription earnings
        long totalSubscribers = earningRepository.countByCreatorAndSource(creator, EarningSource.SUBSCRIPTION);

        return CreatorDashboardDto.builder()
                .totalEarnings(totalEarnings)
                .availableBalance(availableBalance)
                .activeStreams(activeStreams)
                .contentCount(contentCount)
                .totalSubscribers(totalSubscribers)
                .build();
    }
}
