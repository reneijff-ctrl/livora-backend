package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.TipGraph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service("tipGraphBuilderService")
@RequiredArgsConstructor
public class TipGraphBuilderService {

    private final TipRepository tipRepository;

    @Transactional(readOnly = true)
    public List<TipGraph> buildGraph(Instant since) {
        List<Object[]> results = tipRepository.aggregateTips(since);
        
        return results.stream().map(row -> TipGraph.builder()
                .tipperUserId(new UUID(0L, (Long) row[0]))
                .creatorUserId(new UUID(0L, (Long) row[1]))
                .totalAmount((BigDecimal) row[2])
                .tipCount((Long) row[3])
                .firstTipAt((Instant) row[4])
                .lastTipAt((Instant) row[5])
                .build()).toList();
    }
}
