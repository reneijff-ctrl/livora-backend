package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.DeviceFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceFingerprintRepository extends JpaRepository<DeviceFingerprint, UUID> {
    
    List<DeviceFingerprint> findAllByUserId(Long userId);
    
    Optional<DeviceFingerprint> findByUserIdAndFingerprintHash(Long userId, String fingerprintHash);
    
    List<DeviceFingerprint> findAllByFingerprintHash(String fingerprintHash);
}
