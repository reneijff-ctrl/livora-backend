package com.joinlivora.backend.email;

import com.joinlivora.backend.email.event.PasswordResetRequestedEvent;
import com.joinlivora.backend.email.event.PayoutStatusChangedEvent;
import com.joinlivora.backend.email.event.UserRegisteredEvent;
import com.joinlivora.backend.email.event.UserStatusChangedEvent;
import com.joinlivora.backend.email.event.EmailVerificationRequestedEvent;
import com.joinlivora.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventListener {

    private final EmailService emailService;

    @EventListener
    @Async
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Handling UserRegisteredEvent for {}", event.getUser().getEmail());
        emailService.sendRegistrationEmail(event.getUser());
    }

    @EventListener
    @Async
    public void handleEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        log.info("Handling EmailVerificationRequestedEvent for {}", event.getUser().getEmail());
        emailService.sendVerificationEmail(event.getUser());
    }

    @EventListener
    @Async
    public void handlePasswordResetRequested(PasswordResetRequestedEvent event) {
        log.info("Handling PasswordResetRequestedEvent for {}", event.getUser().getEmail());
        emailService.sendPasswordResetEmail(event.getUser(), event.getToken());
    }

    @EventListener
    @Async
    public void handlePayoutStatusChanged(PayoutStatusChangedEvent event) {
        log.info("Handling PayoutStatusChangedEvent for {}", event.getUser().getEmail());
        emailService.sendPayoutStatusEmail(event.getUser(), event.isEnabled(), event.getReason());
    }

    @EventListener
    @Async
    public void handleUserStatusChanged(UserStatusChangedEvent event) {
        log.info("Handling UserStatusChangedEvent for {}", event.getUser().getEmail());
        // Only send email for negative status changes like SUSPENDED or TERMINATED
        if (event.getUser().getStatus() == UserStatus.SUSPENDED || event.getUser().getStatus() == UserStatus.TERMINATED) {
            emailService.sendAccountBanEmail(event.getUser(), event.getReason());
        }
    }
}
