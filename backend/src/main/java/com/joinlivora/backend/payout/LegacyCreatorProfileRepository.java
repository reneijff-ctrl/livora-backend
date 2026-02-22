package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegacyCreatorProfileRepository extends JpaRepository<LegacyCreatorProfile, UUID> {
    Optional<LegacyCreatorProfile> findByUser(User user);
    Optional<LegacyCreatorProfile> findByUsername(String username);
    List<LegacyCreatorProfile> findAllByActiveTrue();
}
