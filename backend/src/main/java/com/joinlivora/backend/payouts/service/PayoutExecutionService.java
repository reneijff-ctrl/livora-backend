package com.joinlivora.backend.payouts.service;

import com.joinlivora.backend.payout.CreatorPayout;
import com.joinlivora.backend.payout.PayoutService;
import com.joinlivora.backend.payouts.exception.PayoutFrozenException;
import com.joinlivora.backend.payouts.repository.CreatorAccountRepository;
import com.joinlivora.backend.aml.service.AMLRulesEngine;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutExecutionService {

    private final CreatorAccountRepository creatorAccountRepository;
    private final PayoutService payoutService;
    private final AMLRulesEngine amlRulesEngine;
    private final UserRepository userRepository;

    @Transactional
    public CreatorPayout executePayout(UUID creatorId, BigDecimal amount, String currency) throws Exception {
        log.info("Starting payout execution for creator: {}", creatorId);

        // TODO: Implement transfer logic for internal balances.
        // If funds were held on the platform (e.g. for Token-based tips), 
        // we need to perform a Stripe Transfer from the platform account to the connected account
        // before triggering the payout.
        
        // Re-evaluate AML risk before payout
        User user = userRepository.findById(creatorId.getLeastSignificantBits())
                .orElseThrow(() -> new RuntimeException("Creator creator not found: " + creatorId));
        amlRulesEngine.evaluateRules(user, amount);

        creatorAccountRepository.findByCreatorId(creatorId).ifPresent(account -> {
            if (account.isPayoutFrozen()) {
                log.warn("Payout blocked for creator {}: Payouts are frozen. Reason: {}", creatorId, account.getFreezeReason());
                throw new PayoutFrozenException("Payouts are frozen for this account");
            }
        });

        return payoutService.executePayout(creatorId, amount, currency);
    }
}
