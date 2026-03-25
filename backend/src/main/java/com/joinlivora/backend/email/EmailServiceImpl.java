package com.joinlivora.backend.email;

import com.joinlivora.backend.user.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:noreply@joinlivora.com}")
    private String fromEmail;

    @Value("${livora.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    @Async
    public void sendRegistrationEmail(User user) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("user", user);
        variables.put("loginUrl", frontendUrl + "/login");
        if (user.getEmailVerificationToken() != null) {
            variables.put("verificationUrl", frontendUrl + "/verify-email?token=" + user.getEmailVerificationToken());
        }
        sendEmail(user.getEmail(), "Welcome to Livora!", "registration", variables);
    }

    @Override
    @Async
    public void sendVerificationEmail(User user) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("user", user);
        variables.put("verificationUrl", frontendUrl + "/verify-email?token=" + user.getEmailVerificationToken());
        sendEmail(user.getEmail(), "Verify your email", "registration", variables); // Reuse registration template for now or create new one
    }

    @Override
    @Async
    public void sendPasswordResetEmail(User user, String token) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("user", user);
        variables.put("resetUrl", frontendUrl + "/reset-password?token=" + token);
        sendEmail(user.getEmail(), "Password Reset Request", "password-reset", variables);
    }

    @Override
    @Async
    public void sendPayoutStatusEmail(User user, boolean enabled, String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("user", user);
        variables.put("enabled", enabled);
        variables.put("reason", reason);
        String subject = enabled ? "Payouts Enabled" : "Payouts Locked";
        sendEmail(user.getEmail(), subject, "payout-status-change", variables);
    }

    @Override
    @Async
    public void sendAccountBanEmail(User user, String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("user", user);
        variables.put("reason", reason);
        sendEmail(user.getEmail(), "Account Suspended", "account-ban", variables);
    }

    @Override
    @Async
    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            log.info("Sending email to {} with subject: {}", to, subject);
            
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, 
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, 
                    StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", to, e);
        }
    }
}
