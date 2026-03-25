package com.joinlivora.backend.fraud.aspect;

import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component("fraudProtectionAspect")
@Slf4j
@RequiredArgsConstructor
public class FraudProtectionAspect {

    private final UserService userService;

    @Pointcut("execution(* com.joinlivora.backend.monetization.TipOrchestrationService.createTipIntent(..)) || " +
              "execution(* com.joinlivora.backend.monetization.TipOrchestrationService.sendTokenTip(..)) || " +
              "execution(* com.joinlivora.backend.monetization.SuperTipService.sendSuperTip(..)) || " +
              "execution(* com.joinlivora.backend.monetization.PPVPurchaseService.createPurchaseIntent(..)) || " +
              "execution(* com.joinlivora.backend.payout.PayoutService.executePayout(..))")
    public void sensitiveFinancialActions() {}

    @Before("sensitiveFinancialActions()")
    public void checkFraudRisk() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        String email = authentication.getName();
        User user;
        try {
            user = userService.getByEmail(email);
        } catch (RuntimeException e) {
            log.debug("FraudProtectionAspect: Could not evaluate risk for creator {}: {}", email, e.getMessage());
            return;
        }

        if (user.getFraudRiskLevel() == FraudRiskLevel.HIGH) {
            log.warn("SECURITY [fraud_protection]: Blocked sensitive action for high-risk creator: {}", email);
            throw new AccessDeniedException("Action blocked due to high fraud risk level. Please contact support.");
        }
    }
}
