package com.joinlivora.backend.token;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TokenPackageRepository extends JpaRepository<TokenPackage, UUID> {
    Optional<TokenPackage> findByActiveTrueAndId(UUID id);
    java.util.List<TokenPackage> findAllByActiveTrue();
}
