package com.joinlivora.backend.creator.verification;

import com.joinlivora.backend.creator.model.CreatorVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreatorVerificationRepository extends JpaRepository<CreatorVerification, Long> {
    Optional<CreatorVerification> findByCreatorId(Long creatorId);
    Optional<CreatorVerification> findByCreator_User_Id(Long userId);
    List<CreatorVerification> findByStatus(VerificationStatus status);
    long countByStatus(VerificationStatus status);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"creator", "creator.user"})
    Page<CreatorVerification> findAll(Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"creator", "creator.user"})
    Page<CreatorVerification> findByStatus(VerificationStatus status, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT v.status, COUNT(v) FROM CreatorVerification v GROUP BY v.status")
    List<Object[]> countByStatuses();
}
