package com.joinlivora.backend.tip;

import com.joinlivora.backend.tip.dto.CreatorTipDto;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipServiceTest {

    @Mock
    private CreatorTipRepository tipRepository;

    @Mock
    private com.joinlivora.backend.payout.CreatorEarningsService creatorEarningsService;

    @InjectMocks
    private TipService tipService;

    @Test
    void saveTip_ShouldPersistTip() {
        User fromUser = new User();
        fromUser.setId(1L);
        User creator = new User();
        creator.setId(2L);

        DirectTip tip = DirectTip.builder()
                .user(fromUser)
                .creator(creator)
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .status(TipStatus.PENDING)
                .build();

        when(tipRepository.save(any(DirectTip.class))).thenReturn(tip);

        DirectTip savedTip = tipService.saveTip(tip);

        assertNotNull(savedTip);
        assertEquals(new BigDecimal("10.00"), savedTip.getAmount());
        verify(tipRepository, times(1)).save(tip);
    }

    @Test
    void updateStatus_ShouldUpdateStatus() {
        UUID id = UUID.randomUUID();
        DirectTip tip = new DirectTip();
        tip.setId(id);
        tip.setStatus(TipStatus.PENDING);

        when(tipRepository.findById(id)).thenReturn(Optional.of(tip));

        tipService.updateStatus(id, TipStatus.COMPLETED);

        assertEquals(TipStatus.COMPLETED, tip.getStatus());
        verify(tipRepository, times(1)).save(tip);
    }

    @Test
    void completeTip_ShouldUpdateStatusAndRecordEarning() {
        UUID id = UUID.randomUUID();
        DirectTip tip = DirectTip.builder()
                .id(id)
                .amount(new BigDecimal("20.00"))
                .currency("EUR")
                .status(TipStatus.PENDING)
                .build();

        when(tipRepository.findById(id)).thenReturn(Optional.of(tip));

        tipService.completeTip(id);

        assertEquals(TipStatus.COMPLETED, tip.getStatus());
        verify(tipRepository).save(tip);
        verify(creatorEarningsService).recordDirectTipEarning(tip);
    }

    @Test
    void getTipsForCreator_ShouldReturnMaskedTips() {
        User creator = new User();
        creator.setId(10L);

        User tipper = new User();
        tipper.setId(12345L);

        DirectTip tip = DirectTip.builder()
                .id(UUID.randomUUID())
                .user(tipper)
                .creator(creator)
                .amount(new BigDecimal("50.00"))
                .status(TipStatus.COMPLETED)
                .createdAt(Instant.now())
                .build();

        when(tipRepository.findAllByCreatorAndStatusOrderByCreatedAtDesc(creator, TipStatus.COMPLETED))
                .thenReturn(List.of(tip));

        List<CreatorTipDto> result = tipService.getTipsForCreator(creator);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("50.00"), result.get(0).getAmount());
        assertEquals("u_1***5", result.get(0).getFromUserId());
    }

    @Test
    void maskUserId_ShouldHandleShortIds() {
        User creator = new User();
        creator.setId(10L);

        User tipper = new User();
        tipper.setId(1L);

        DirectTip tip = DirectTip.builder()
                .user(tipper)
                .creator(creator)
                .status(TipStatus.COMPLETED)
                .build();

        when(tipRepository.findAllByCreatorAndStatusOrderByCreatedAtDesc(creator, TipStatus.COMPLETED))
                .thenReturn(List.of(tip));

        List<CreatorTipDto> result = tipService.getTipsForCreator(creator);
        assertEquals("u_***", result.get(0).getFromUserId());
    }
}








