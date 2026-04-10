package com.joinlivora.backend.admin.service;

import com.joinlivora.backend.admin.dto.AdminAbuseEventDTO;
import com.joinlivora.backend.admin.dto.AdminRealtimeEventDTO;
import com.joinlivora.backend.creator.model.CreatorApplication;
import com.joinlivora.backend.livestream.event.StreamEndedEventV2;
import com.joinlivora.backend.livestream.event.StreamStartedEventV2;
import com.joinlivora.backend.moderation.dto.ModerationDecisionDTO;
import com.joinlivora.backend.payment.Payment;
import com.joinlivora.backend.report.model.Report;
import com.joinlivora.backend.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AdminRealtimeEventService {

    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    public AdminRealtimeEventService(ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider) {
        this.messagingTemplateProvider = messagingTemplateProvider;
    }

    public void broadcastUserRegistered(User user) {
        AdminRealtimeEventDTO event = AdminRealtimeEventDTO.builder()
                .type("USER_REGISTRATION")
                .eventType("USER_REGISTRATION")
                .message("New user registered: " + user.getUsername() + " (" + user.getEmail() + ")")
                .severity("INFO")
                .userId(user.getId())
                .timestamp(user.getCreatedAt() != null ? user.getCreatedAt() : Instant.now())
                .metadata(Map.of(
                        "userId", user.getId(),
                        "username", user.getUsername(),
                        "email", user.getEmail()
                ))
                .build();
        broadcast(event);
    }

    public void broadcastCreatorApplication(CreatorApplication application) {
        AdminRealtimeEventDTO event = AdminRealtimeEventDTO.builder()
                .type("CREATOR_APPLICATION")
                .eventType("CREATOR_APPLICATION")
                .message("Creator application submitted by: " + (application.getUser() != null ? application.getUser().getUsername() : "Unknown"))
                .severity("INFO")
                .userId(application.getUser() != null ? application.getUser().getId() : null)
                .timestamp(application.getSubmittedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                .metadata(Map.of(
                        "applicationId", application.getId(),
                        "userId", application.getUser() != null ? application.getUser().getId() : 0,
                        "username", application.getUser() != null ? application.getUser().getUsername() : "Unknown"
                ))
                .build();
        broadcast(event);
    }

    public void broadcastReportCreated(Report report) {
        AdminRealtimeEventDTO event = AdminRealtimeEventDTO.builder()
                .type("REPORT_CREATED")
                .eventType("REPORT_FILED")
                .message("New report filed for reason: " + report.getReason())
                .severity("WARNING")
                .userId(report.getReportedUserId())
                .timestamp(report.getCreatedAt())
                .metadata(Map.of(
                        "reportId", report.getId(),
                        "reportedUserId", report.getReportedUserId(),
                        "reason", report.getReason().name()
                ))
                .build();
        broadcast(event);
    }

    /**
     * Broadcasts the STREAM_STARTED admin event from a {@link StreamStartedEventV2} payload.
     */
    public void broadcastStreamStarted(StreamStartedEventV2 event, int viewerCount, int fraudRiskScore) {
        AdminRealtimeEventDTO dto = AdminRealtimeEventDTO.builder()
                .type("STREAM_STARTED")
                .eventType("STREAM_STARTED")
                .message("Stream started by creator " + event.getCreatorUserId())
                .severity("INFO")
                .streamId(event.getStreamId().toString())
                .timestamp(event.getStartedAt())
                .metadata(Map.of(
                        "streamId", event.getStreamId().toString(),
                        "creatorUserId", event.getCreatorUserId(),
                        "isPaid", event.isPaid(),
                        "admissionPrice", event.getAdmissionPrice() != null ? event.getAdmissionPrice().toPlainString() : "0",
                        "viewerCount", viewerCount,
                        "fraudRiskScore", fraudRiskScore,
                        "startedAt", event.getStartedAt().toString()
                ))
                .build();
        broadcast(dto);
    }

    /**
     * Broadcasts the STREAM_STOPPED admin event from a {@link StreamEndedEventV2} payload.
     */
    public void broadcastStreamStopped(StreamEndedEventV2 event, int viewerCount) {
        String reason = event.getReason() != null ? event.getReason() : "unknown";
        String message;
        switch (reason) {
            case "creator":
                message = "Creator ended the stream (creator " + event.getCreatorUserId() + ")";
                break;
            case "admin":
                message = "Stream stopped by admin (creator " + event.getCreatorUserId() + ")";
                break;
            case "disconnect":
                message = "Stream ended unexpectedly (creator " + event.getCreatorUserId() + ")";
                break;
            default:
                message = "Stream ended (creator " + event.getCreatorUserId() + ")";
                break;
        }

        AdminRealtimeEventDTO dto = AdminRealtimeEventDTO.builder()
                .type("STREAM_STOPPED")
                .eventType("STREAM_STOPPED")
                .message(message)
                .severity("admin".equals(reason) ? "WARNING" : "INFO")
                .streamId(event.getStreamId().toString())
                .timestamp(event.getEndedAt() != null ? event.getEndedAt() : Instant.now())
                .metadata(Map.of(
                        "streamId", event.getStreamId().toString(),
                        "creatorUserId", event.getCreatorUserId(),
                        "isPaid", event.isPaid(),
                        "viewerCount", viewerCount,
                        "reason", reason
                ))
                .build();
        broadcast(dto);
    }

    public void broadcastPaymentCompleted(Payment payment) {
        AdminRealtimeEventDTO event = AdminRealtimeEventDTO.builder()
                .type("PAYMENT_COMPLETED")
                .eventType("PAYMENT_COMPLETED")
                .message("Payment of " + payment.getAmount() + " " + payment.getCurrency() + " completed by: " + (payment.getUser() != null ? payment.getUser().getUsername() : "Unknown"))
                .severity("INFO")
                .userId(payment.getUser() != null ? payment.getUser().getId() : null)
                .timestamp(payment.getCreatedAt())
                .metadata(Map.of(
                        "paymentId", payment.getId(),
                        "userId", payment.getUser() != null ? payment.getUser().getId() : 0,
                        "amount", payment.getAmount(),
                        "currency", payment.getCurrency()
                ))
                .build();
        broadcast(event);
    }

    public void broadcastUserMuted(String creatorUsername, String mutedUsername, int duration) {
        AdminRealtimeEventDTO event = AdminRealtimeEventDTO.builder()
                .type("USER_MUTED")
                .eventType("USER_MUTED")
                .message("User muted by moderator")
                .severity("INFO")
                .timestamp(Instant.now())
                .metadata(Map.of(
                        "creatorUsername", creatorUsername,
                        "mutedUsername", mutedUsername,
                        "duration", duration
                ))
                .build();
        broadcast(event);
    }

    public void broadcastUserShadowMuted(String creatorUsername, String mutedUsername) {
        AdminRealtimeEventDTO event = AdminRealtimeEventDTO.builder()
                .type("USER_SHADOW_MUTED")
                .eventType("USER_SHADOW_MUTED")
                .message("User shadow muted by moderator")
                .severity("INFO")
                .timestamp(Instant.now())
                .metadata(Map.of(
                        "creatorUsername", creatorUsername,
                        "mutedUsername", mutedUsername
                ))
                .build();
        broadcast(event);
    }

    public void broadcastFraudSignal(String username, int score) {
        broadcastFraudSignal(username, "High fraud risk", score);
    }

    public void broadcastFraudSignal(String username, String signalType, int score) {
        AdminRealtimeEventDTO event = AdminRealtimeEventDTO.builder()
                .type("FRAUD_SIGNAL")
                .eventType("FRAUD_SIGNAL_DETECTED")
                .message("Fraud signal detected (" + signalType + ")")
                .severity("WARNING")
                .timestamp(Instant.now())
                .metadata(Map.of(
                        "username", username,
                        "score", score,
                        "signalType", signalType
                ))
                .build();
        broadcast(event);
    }

    public void publishViewerSpike(UUID streamId, int delta) {
        String severity = (delta >= 300) ? "CRITICAL" : "WARNING";
        AdminRealtimeEventDTO event = AdminRealtimeEventDTO.builder()
                .type("VIEWER_SPIKE_DETECTED")
                .eventType("VIEWER_SPIKE_DETECTED")
                .message("Viewer spike detected on stream " + streamId + " (+" + delta + " viewers in 30s)")
                .severity(severity)
                .streamId(streamId.toString())
                .timestamp(Instant.now())
                .metadata(Map.of(
                        "streamId", streamId,
                        "viewerIncrease", delta
                ))
                .build();
        broadcast(event);
    }

    public void publishAbuseEvent(
        String type,
        UUID streamId,
        String creatorUsername,
        String description
    ) {
        AdminAbuseEventDTO event =
            new AdminAbuseEventDTO(type, streamId, creatorUsername, description);

        messagingTemplateProvider.getObject().convertAndSend(
            "/exchange/amq.topic/admin.abuse",
            event
        );
    }

    public void publishModerationDecision(ModerationDecisionDTO decision) {
        messagingTemplateProvider.getObject().convertAndSend(
            "/exchange/amq.topic/admin.abuse",
            decision
        );
    }

    public void broadcastChatSpamDetected(UUID streamId, String username) {
        AdminRealtimeEventDTO event = AdminRealtimeEventDTO.builder()
                .type("CHAT_SPAM_DETECTED")
                .eventType("CHAT_SPAM_DETECTED")
                .message("Chat spam detected on stream " + streamId + " by " + username)
                .severity("WARNING")
                .streamId(streamId.toString())
                .timestamp(Instant.now())
                .metadata(Map.of(
                        "streamId", streamId,
                        "username", username
                ))
                .build();
        broadcast(event);
    }

    private void broadcast(AdminRealtimeEventDTO event) {
        log.info("Broadcasting admin event: {} - {}", event.getEventType() != null ? event.getEventType() : event.getType(), event.getMessage());
        messagingTemplateProvider.getObject().convertAndSend("/exchange/amq.topic/admin.events", event);
    }
}
