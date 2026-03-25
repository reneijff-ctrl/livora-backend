package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.model.RuleFraudSignal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface RuleFraudSignalRepository extends JpaRepository<RuleFraudSignal, UUID>, JpaSpecificationExecutor<RuleFraudSignal> {

    /**
     * Finds all fraud signals for a specific creator.
     *
     * @param userId the ID of the creator
     * @param pageable pagination and sorting information
     * @return a page of fraud signals
     */
    Page<RuleFraudSignal> findAllByUserId(Long userId, Pageable pageable);

    /**
     * Counts the number of fraud signals for a specific creator after a given timestamp.
     *
     * @param userId the ID of the creator
     * @param after the timestamp after which to count signals
     * @return the count of fraud signals
     */
    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    /**
     * Finds the top 10 most recent fraud signals for a specific creator.
     *
     * @param userId the ID of the creator
     * @return a list of the top 10 fraud signals ordered by creation date descending
     */
    List<RuleFraudSignal> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Counts the number of fraud signals for a specific creator with a specific risk level after a given timestamp.
     *
     * @param userId the ID of the creator
     * @param riskLevel the risk level to count
     * @param after the timestamp after which to count signals
     * @return the count of fraud signals
     */
    long countByUserIdAndRiskLevelAndCreatedAtAfter(Long userId, FraudDecisionLevel riskLevel, Instant after);

    /**
     * Checks if any unresolved fraud signals exist for a specific creator with a specific risk level.
     *
     * @param userId the ID of the creator
     * @param riskLevel the risk level to check
     * @return true if unresolved signals exist, false otherwise
     */
    boolean existsByUserIdAndRiskLevelAndResolvedFalse(Long userId, FraudDecisionLevel riskLevel);

    /**
     * Finds all fraud signals where the type contains a specific string.
     *
     * @param reason the string to search for in the type
     * @return a list of matching fraud signals
     */
    List<RuleFraudSignal> findAllByReasonContaining(String reason);

    /**
     * Counts the total number of unresolved fraud signals.
     *
     * @return the count of unresolved fraud signals
     */
    long countByResolvedFalse();

    /**
     * Counts unresolved fraud signals grouped by risk level.
     *
     * @return a map with risk levels as keys and their corresponding count of unresolved signals as values
     */
    default Map<RiskLevel, Long> countUnresolvedByRiskLevel() {
        List<Object[]> results = countUnresolvedByRiskLevelRaw();
        Map<RiskLevel, Long> map = new EnumMap<>(RiskLevel.class);
        for (Object[] result : results) {
            FraudDecisionLevel level = (FraudDecisionLevel) result[0];
            Long count = (Long) result[1];
            try {
                map.put(RiskLevel.valueOf(level.name()), count);
            } catch (IllegalArgumentException e) {
                // Ignore levels that don't match RiskLevel if any
            }
        }
        return map;
    }

    @Query("SELECT r.riskLevel, COUNT(r) FROM RuleFraudSignal r WHERE r.resolved = false GROUP BY r.riskLevel")
    List<Object[]> countUnresolvedByRiskLevelRaw();

    /**
     * Counts the total number of fraud signals of a specific type created after a given timestamp for a specific creator.
     *
     * @param creatorId the ID of the creator
     * @param type the type of fraud signal to count
     * @param after the timestamp after which to count signals
     * @return the count of fraud signals
     */
    long countByCreatorIdAndTypeAndCreatedAtAfter(Long creatorId, FraudSignalType type, Instant after);

    /**
     * Counts the total number of fraud signals of a specific type created after a given timestamp.
     *
     * @param type the type of fraud signal to count
     * @param after the timestamp after which to count signals
     * @return the count of fraud signals
     */
    long countByTypeAndCreatedAtAfter(FraudSignalType type, Instant after);
}
