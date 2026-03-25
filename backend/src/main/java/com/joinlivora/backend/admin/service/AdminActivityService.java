package com.joinlivora.backend.admin.service;

import com.joinlivora.backend.admin.dto.AdminActivityEventDTO;
import com.joinlivora.backend.creator.repository.CreatorApplicationRepository;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.report.repository.ReportRepository;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminActivityService {

    private final UserRepository userRepository;
    private final CreatorApplicationRepository creatorApplicationRepository;
    private final ReportRepository reportRepository;
    private final LivestreamSessionRepository livestreamSessionRepository;
    private final PaymentRepository paymentRepository;

    @Cacheable("adminActivity")
    public List<AdminActivityEventDTO> getRecentActivity() {
        try {
            List<AdminActivityEventDTO> events = new ArrayList<>();

            // 1. User registrations
            if (userRepository != null) {
                userRepository.findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .forEach(user -> {
                            if (user != null) {
                                events.add(AdminActivityEventDTO.builder()
                                        .id("USER-" + user.getId())
                                        .type("USER_REGISTRATION")
                                        .description("New user registered: " + (user.getUsername() != null ? user.getUsername() : "Unknown") + " (" + (user.getEmail() != null ? user.getEmail() : "No Email") + ")")
                                        .timestamp(user.getCreatedAt() != null ? user.getCreatedAt() : Instant.now())
                                        .build());
                            }
                        });
            }

            // 2. Creator applications
            if (creatorApplicationRepository != null) {
                creatorApplicationRepository.findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt")))
                        .forEach(app -> {
                            if (app != null) {
                                String username = (app.getUser() != null && app.getUser().getUsername() != null) ? app.getUser().getUsername() : "Unknown";
                                events.add(AdminActivityEventDTO.builder()
                                        .id("APP-" + app.getId())
                                        .type("CREATOR_APPLICATION")
                                        .description("Creator application submitted by: " + username)
                                        .timestamp(app.getSubmittedAt() != null ? 
                                                app.getSubmittedAt().atZone(ZoneId.systemDefault()).toInstant() : Instant.now())
                                        .build());
                            }
                        });
            }

            // 3. Reports
            if (reportRepository != null) {
                reportRepository.findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .forEach(report -> {
                            if (report != null) {
                                events.add(AdminActivityEventDTO.builder()
                                        .id("REPORT-" + report.getId())
                                        .type("REPORT_CREATED")
                                        .description("New report created for reason: " + (report.getReason() != null ? report.getReason() : "Unspecified"))
                                        .timestamp(report.getCreatedAt() != null ? report.getCreatedAt() : Instant.now())
                                        .build());
                            }
                        });
            }

            // 4. Streams started
            if (livestreamSessionRepository != null) {
                livestreamSessionRepository.findAllByStartedAtIsNotNull(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt")))
                        .forEach(session -> {
                            if (session != null) {
                                String username = (session.getCreator() != null && session.getCreator().getUsername() != null) ? session.getCreator().getUsername() : "Unknown";
                                events.add(AdminActivityEventDTO.builder()
                                        .id("STREAM-" + session.getId())
                                        .type("STREAM_STARTED")
                                        .description("Stream started by: " + username)
                                        .timestamp(session.getStartedAt() != null ? 
                                                session.getStartedAt().atZone(ZoneId.systemDefault()).toInstant() : Instant.now())
                                        .build());
                            }
                        });
            }

            // 5. Payments
            if (paymentRepository != null) {
                paymentRepository.findAllBySuccessTrue(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .forEach(payment -> {
                            if (payment != null) {
                                String username = (payment.getUser() != null && payment.getUser().getUsername() != null) ? payment.getUser().getUsername() : "Unknown";
                                events.add(AdminActivityEventDTO.builder()
                                        .id("PAYMENT-" + payment.getId())
                                        .type("PAYMENT_COMPLETED")
                                        .description("Payment of " + (payment.getAmount() != null ? payment.getAmount() : "0") + " " + (payment.getCurrency() != null ? payment.getCurrency() : "EUR") + " completed by: " + username)
                                        .timestamp(payment.getCreatedAt() != null ? payment.getCreatedAt() : Instant.now())
                                        .build());
                            }
                        });
            }

            return events.stream()
                    .filter(e -> e.getTimestamp() != null)
                    .sorted(Comparator.comparing(AdminActivityEventDTO::getTimestamp).reversed())
                    .limit(20)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // Return empty list on failure as per requirements
            return new ArrayList<>();
        }
    }
}
