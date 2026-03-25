package com.joinlivora.backend.fraud.repository;
import com.joinlivora.backend.fraud.model.UserRiskState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserRiskStateRepository extends JpaRepository<UserRiskState, Long> {
    List<UserRiskState> findAllByBlockedUntilBefore(Instant timestamp);
    long countByPaymentLockedTrue();
}
