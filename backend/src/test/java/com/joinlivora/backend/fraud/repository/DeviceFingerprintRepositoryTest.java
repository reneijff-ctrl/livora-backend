package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.DeviceFingerprint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class DeviceFingerprintRepositoryTest {

    @Autowired
    private DeviceFingerprintRepository repository;

    @Test
    void testSaveAndFind() {
        Long userId = 1L;
        String hash = "test-hash-123";

        DeviceFingerprint fingerprint = DeviceFingerprint.builder()
                .userId(userId)
                .fingerprintHash(hash)
                .userAgent("Mozilla/5.0")
                .ipAddress("127.0.0.1")
                .trusted(false)
                .build();

        DeviceFingerprint saved = repository.save(fingerprint);
        assertNotNull(saved.getId());
        assertNotNull(saved.getFirstSeen());
        assertNotNull(saved.getLastSeen());

        Optional<DeviceFingerprint> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(userId, found.get().getUserId());
        assertEquals(hash, found.get().getFingerprintHash());
        assertFalse(found.get().isTrusted());
    }

    @Test
    void testFindByUserIdAndFingerprintHash() {
        Long userId = 2L;
        String hash = "hash-456";

        DeviceFingerprint fingerprint = DeviceFingerprint.builder()
                .userId(userId)
                .fingerprintHash(hash)
                .build();
        repository.save(fingerprint);

        Optional<DeviceFingerprint> found = repository.findByUserIdAndFingerprintHash(userId, hash);
        assertTrue(found.isPresent());
    }

    @Test
    void testFindAllByUserId() {
        Long userId = 3L;
        repository.save(DeviceFingerprint.builder().userId(userId).fingerprintHash("h1").build());
        repository.save(DeviceFingerprint.builder().userId(userId).fingerprintHash("h2").build());
        repository.save(DeviceFingerprint.builder().userId(4L).fingerprintHash("h3").build());

        List<DeviceFingerprint> list = repository.findAllByUserId(userId);
        assertEquals(2, list.size());
    }

    @Test
    void testUniqueConstraint() {
        Long userId = 5L;
        String hash = "unique-hash";

        repository.saveAndFlush(DeviceFingerprint.builder().userId(userId).fingerprintHash(hash).build());

        DeviceFingerprint duplicate = DeviceFingerprint.builder()
                .userId(userId)
                .fingerprintHash(hash)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(duplicate);
        });
    }

    @Test
    void testUpdateTriggersOnUpdate() throws InterruptedException {
        DeviceFingerprint fingerprint = DeviceFingerprint.builder()
                .userId(6L)
                .fingerprintHash("update-hash")
                .build();

        fingerprint = repository.saveAndFlush(fingerprint);
        Instant firstLastSeen = fingerprint.getLastSeen();

        Thread.sleep(10); // Ensure time passes

        fingerprint.setTrusted(true);
        DeviceFingerprint updated = repository.saveAndFlush(fingerprint);

        assertTrue(updated.getLastSeen().isAfter(firstLastSeen));
    }
}








