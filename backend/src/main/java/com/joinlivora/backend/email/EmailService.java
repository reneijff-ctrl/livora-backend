package com.joinlivora.backend.email;

import com.joinlivora.backend.user.User;
import java.util.Map;

public interface EmailService {
    void sendRegistrationEmail(User user);
    void sendVerificationEmail(User user);
    void sendPasswordResetEmail(User user, String token);
    void sendPayoutStatusEmail(User user, boolean enabled, String reason);
    void sendAccountBanEmail(User user, String reason);
    void sendEmail(String to, String subject, String templateName, Map<String, Object> variables);
}
