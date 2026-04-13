package com.joinlivora.backend.auth.totp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TotpBackupCodeRepository extends JpaRepository<TotpBackupCode, UUID> {

    List<TotpBackupCode> findAllByUserIdAndUsedFalse(Long userId);

    void deleteAllByUserId(Long userId);
}
