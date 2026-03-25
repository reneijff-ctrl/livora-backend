package com.joinlivora.backend.analytics.adaptive;

import com.joinlivora.backend.payout.CreatorEarning;
import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WhaleRiskModel {

    private final CreatorEarningRepository creatorEarningRepository;

    public double calculateRisk(User creator) {
        List<CreatorEarning> transactions = creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator);
        if (transactions.isEmpty()) {
            return 0.0;
        }

        double totalRevenue = transactions.stream()
                .mapToDouble(tx -> tx.getNetAmount().doubleValue())
                .sum();

        if (totalRevenue == 0) return 0.0;

        Map<String, Double> supporterTotals = new HashMap<>();
        for (CreatorEarning tx : transactions) {
            String name = tx.getUser() != null ? tx.getUser().getEmail() : "Anonymous";
            supporterTotals.merge(name, tx.getNetAmount().doubleValue(), Double::sum);
        }

        List<Double> sortedTotals = supporterTotals.values().stream()
                .sorted(Comparator.reverseOrder())
                .toList();

        double singleDominance = sortedTotals.get(0) / totalRevenue;
        double topThreeSum = sortedTotals.stream().limit(3).mapToDouble(Double::doubleValue).sum();
        double topThreeShare = topThreeSum / totalRevenue;

        return calculateWhaleRisk(topThreeShare, singleDominance);
    }

    public double calculateWhaleRisk(
            double topThreeShare,
            double singleDominance
    ) {
        return (topThreeShare * 0.6) + (singleDominance * 0.4);
    }
}
