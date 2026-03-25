package com.joinlivora.backend.creator.monetization;

import com.joinlivora.backend.creator.model.CreatorMonetization;
import com.joinlivora.backend.creator.model.CreatorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreatorMonetizationRepository extends JpaRepository<CreatorMonetization, Long> {
    Optional<CreatorMonetization> findByCreator(CreatorProfile creator);
}
