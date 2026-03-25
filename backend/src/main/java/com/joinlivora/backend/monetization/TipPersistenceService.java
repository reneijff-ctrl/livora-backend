package com.joinlivora.backend.monetization;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.token.TipRecord;
import com.joinlivora.backend.token.TipRecordRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TipPersistenceService {

    private final TipRepository tipRepository;
    private final TipRecordRepository tipRecordRepository;
    private final StreamRepository streamRepository;
    private final AuditService auditService;
    private final UserService userService;

    public Optional<User> findUserById(Long userId) {
        try {
            return Optional.ofNullable(userService.getById(userId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Tip> findByClientRequestId(String clientRequestId) {
        return tipRepository.findByClientRequestId(clientRequestId);
    }

    public Optional<Tip> findByStripePaymentIntentId(String paymentIntentId) {
        return tipRepository.findByStripePaymentIntentId(paymentIntentId);
    }

    @Transactional
    public Tip saveTip(Tip tip) {
        return tipRepository.save(tip);
    }

    @Transactional
    public TipRecord saveTipRecord(TipRecord tipRecord) {
        return tipRecordRepository.save(tipRecord);
    }

    public Optional<Stream> findStreamById(UUID roomId) {
        return streamRepository.findById(roomId)
                .or(() -> streamRepository.findByMediasoupRoomId(roomId));
    }

    public void logAudit(Tip tip, String ipAddress, String userAgent, Map<String, Object> extraMetadata) {
        auditService.logEvent(
                tip.getSenderUserId() != null ? new UUID(0L, tip.getSenderUserId().getId()) : null,
                AuditService.TIP_CREATED,
                "TIP",
                tip.getId(),
                extraMetadata,
                ipAddress,
                userAgent
        );
    }

    public void logAudit(TipRecord tipRecord, String ipAddress, String userAgent) {
        auditService.logEvent(
                tipRecord.getViewer() != null ? new UUID(0L, tipRecord.getViewer().getId()) : null,
                AuditService.TIP_CREATED,
                "TIP_RECORD",
                tipRecord.getId(),
                Map.of("amount", tipRecord.getAmount(), "creator", tipRecord.getCreator().getId()),
                ipAddress,
                userAgent
        );
    }
}
