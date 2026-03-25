package com.joinlivora.backend.payout;

import com.joinlivora.backend.content.ContentRepository;
import com.joinlivora.backend.creator.dto.CreatorDashboardStatisticsDTO;
import com.joinlivora.backend.creator.repository.CreatorPostRepository;
import com.joinlivora.backend.payout.dto.CreatorDashboardDto;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorDashboardServiceTest {

    @Mock
    private CreatorEarningRepository earningRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private CreatorPostRepository creatorPostRepository;

    @InjectMocks
    private CreatorDashboardService creatorDashboardService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setRole(Role.CREATOR);
    }

    @Test
    void getDashboard_AsCreator_ShouldReturnAggregatedData() {
        when(earningRepository.sumTotalNetEarningsByCreator(creator)).thenReturn(new BigDecimal("100.50"));
        
        CreatorEarning earning = new CreatorEarning();
        earning.setNetAmount(new BigDecimal("50.25"));
        when(earningRepository.findAvailableEarningsByCreator(creator)).thenReturn(List.of(earning));
        
        when(streamRepository.countByCreatorIdAndIsLiveTrue(creator.getId())).thenReturn(2L);
        when(contentRepository.countByCreator(creator)).thenReturn(10L);
        when(earningRepository.countByCreatorAndSource(creator, EarningSource.SUBSCRIPTION)).thenReturn(5L);

        CreatorDashboardDto result = creatorDashboardService.getDashboard(creator);

        assertNotNull(result);
        assertEquals(new BigDecimal("100.50"), result.getTotalEarnings());
        assertEquals(new BigDecimal("50.25"), result.getAvailableBalance());
        assertEquals(2L, result.getActiveStreams());
        assertEquals(10L, result.getContentCount());
        assertEquals(5L, result.getTotalSubscribers());
    }

    @Test
    void getDashboard_AsUser_ShouldThrowAccessDeniedException() {
        User regularUser = new User();
        regularUser.setRole(Role.USER);

        assertThrows(AccessDeniedException.class, () -> creatorDashboardService.getDashboard(regularUser));
    }

    @Test
    void getDashboard_WithNullEarnings_ShouldReturnZero() {
        when(earningRepository.sumTotalNetEarningsByCreator(creator)).thenReturn(null);
        when(earningRepository.findAvailableEarningsByCreator(creator)).thenReturn(List.of());

        CreatorDashboardDto result = creatorDashboardService.getDashboard(creator);

        assertEquals(BigDecimal.ZERO, result.getTotalEarnings());
        assertEquals(BigDecimal.ZERO, result.getAvailableBalance());
    }

    @Test
    void getStatistics_ShouldReturnCorrectCounts() {
        when(creatorPostRepository.countByCreator(creator)).thenReturn(15L);
        when(earningRepository.countByCreatorAndSource(creator, EarningSource.TIP)).thenReturn(42L);

        CreatorDashboardStatisticsDTO result = creatorDashboardService.getStatistics(creator);

        assertNotNull(result);
        assertEquals(15L, result.getPostsCount());
        assertEquals(42L, result.getTipsCount());
        assertEquals(0L, result.getSubscribersCount()); // Subscribers not implemented yet
    }
}








