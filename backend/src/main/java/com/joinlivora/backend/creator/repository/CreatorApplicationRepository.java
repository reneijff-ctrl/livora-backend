package com.joinlivora.backend.creator.repository;

import com.joinlivora.backend.creator.model.CreatorApplication;
import com.joinlivora.backend.creator.model.CreatorApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreatorApplicationRepository extends JpaRepository<CreatorApplication, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<CreatorApplication> findById(Long id);

    @EntityGraph(attributePaths = "user")
    Page<CreatorApplication> findAll(Pageable pageable);

    Optional<CreatorApplication> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    List<CreatorApplication> findByStatus(CreatorApplicationStatus status);

    @EntityGraph(attributePaths = "user")
    Page<CreatorApplication> findByStatus(CreatorApplicationStatus status, Pageable pageable);
    
    long countByStatus(CreatorApplicationStatus status);
}
