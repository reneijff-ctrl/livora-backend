package com.joinlivora.backend.email;

import com.joinlivora.backend.user.User;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailServiceImpl emailService;

    @Mock
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@joinlivora.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void sendRegistrationEmail_shouldSendEmail() {
        User user = new User();
        user.setEmail("user@example.com");

        when(templateEngine.process(eq("registration"), any(Context.class))).thenReturn("<html>Registration Content</html>");

        emailService.sendRegistrationEmail(user);

        verify(mailSender).send(any(MimeMessage.class));
        verify(templateEngine).process(eq("registration"), any(Context.class));
    }

    @Test
    void sendPasswordResetEmail_shouldSendEmail() {
        User user = new User();
        user.setEmail("user@example.com");

        when(templateEngine.process(eq("password-reset"), any(Context.class))).thenReturn("<html>Reset Content</html>");

        emailService.sendPasswordResetEmail(user, "token123");

        verify(mailSender).send(any(MimeMessage.class));
        verify(templateEngine).process(eq("password-reset"), any(Context.class));
    }

    @Test
    void sendPayoutStatusEmail_shouldSendEmail() {
        User user = new User();
        user.setEmail("user@example.com");

        when(templateEngine.process(eq("payout-status-change"), any(Context.class))).thenReturn("<html>Payout Content</html>");

        emailService.sendPayoutStatusEmail(user, false, "Reason");

        verify(mailSender).send(any(MimeMessage.class));
        verify(templateEngine).process(eq("payout-status-change"), any(Context.class));
    }

    @Test
    void sendAccountBanEmail_shouldSendEmail() {
        User user = new User();
        user.setEmail("user@example.com");

        when(templateEngine.process(eq("account-ban"), any(Context.class))).thenReturn("<html>Ban Content</html>");

        emailService.sendAccountBanEmail(user, "Reason");

        verify(mailSender).send(any(MimeMessage.class));
        verify(templateEngine).process(eq("account-ban"), any(Context.class));
    }
}








