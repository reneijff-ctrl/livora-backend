package com.joinlivora.backend.token;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.wallet.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service("tokenWalletService")
@RequiredArgsConstructor
@Slf4j
public class TokenWalletService {

    private final UserWalletRepository tokenBalanceRepository;
    private final WalletTransactionRepository tokenTransactionRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Get the available balance for a creator (total balance - reserved balance).
     */
    public long getAvailableBalance(Long userId) {
        return getOrCreateBalance(userId, false).getAvailableBalance();
    }

    /**
     * Get the total balance for a creator (including reserved tokens).
     */
    public long getTotalBalance(Long userId) {
        return getOrCreateBalance(userId, false).getBalance();
    }

    /**
     * Reserve tokens for a pending transaction.
     * Prevents the reserved amount from being used in other transactions.
     */
    @Transactional
    @Retryable(
            retryFor = {DeadlockLoserDataAccessException.class, PessimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public void reserveTokens(Long userId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Reservation amount must be positive");
        }

        UserWallet balance = getOrCreateBalance(userId, true);
        if (balance.getAvailableBalance() < amount) {
            throw new InsufficientBalanceException("Insufficient token balance");
        }

        balance.setReservedBalance(balance.getReservedBalance() + amount);
        tokenBalanceRepository.save(balance);
        log.info("TOKEN: Reserved {} tokens for creator {}", amount, userId);
    }

    /**
     * Cancel a previously made token reservation.
     */
    @Transactional
    @Retryable(
            retryFor = {DeadlockLoserDataAccessException.class, PessimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public void cancelReservation(Long userId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Cancellation amount must be positive");
        }

        UserWallet balance = getOrCreateBalance(userId, true);
        if (balance.getReservedBalance() < amount) {
            throw new IllegalArgumentException("Cannot cancel more than reserved amount");
        }

        balance.setReservedBalance(balance.getReservedBalance() - amount);
        tokenBalanceRepository.save(balance);
        log.info("TOKEN: Cancelled reservation of {} tokens for creator {}", amount, userId);
    }

    /**
     * Deduct tokens directly from the creator's balance without a prior reservation.
     * Throws InsufficientBalanceException if the available balance is too low.
     */
    @Transactional
    @Retryable(
            retryFor = {DeadlockLoserDataAccessException.class, PessimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public void deductTokens(Long userId, long amount, WalletTransactionType type, String reference) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        UserWallet balance = getOrCreateBalance(userId, true);
        if (balance.getAvailableBalance() < amount) {
            throw new InsufficientBalanceException("Insufficient token balance");
        }

        balance.setBalance(balance.getBalance() - amount);
        balance.setUpdatedAt(Instant.now());
        tokenBalanceRepository.save(balance);

        User user = balance.getUserId();
        tokenTransactionRepository.save(WalletTransaction.builder()
                .userId(user)
                .amount(-amount)
                .type(type)
                .reason(deriveReason(type, reference))
                .referenceId(reference)
                .build());

        log.info("TOKEN: Deducted {} tokens from creator {} for {}. Ref: {}", amount, user.getEmail(), type, reference);
        
        sendWalletUpdate(user, balance.getBalance(), -amount, type);
    }

    /**
     * Add tokens directly to the user's balance.
     */
    @Transactional
    @Retryable(
            retryFor = {DeadlockLoserDataAccessException.class, PessimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public void addTokens(Long userId, long amount, WalletTransactionType type, String reference) {
        log.info("WEBHOOK_DEBUG: addTokens ENTERED userId={}, amount={}, type={}, ref={}", userId, amount, type, reference);
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        UserWallet balance = getOrCreateBalance(userId, true);
        log.info("WEBHOOK_DEBUG: addTokens wallet found, currentBalance={}, adding={}", balance.getBalance(), amount);
        balance.setBalance(balance.getBalance() + amount);
        balance.setUpdatedAt(Instant.now());
        tokenBalanceRepository.save(balance);

        User user = balance.getUserId();
        tokenTransactionRepository.save(WalletTransaction.builder()
                .userId(user)
                .amount(amount)
                .type(type)
                .reason(deriveReason(type, reference))
                .referenceId(reference)
                .build());

        log.info("TOKEN: Added {} tokens to user {}. Type: {}, Ref: {}", amount, user.getEmail(), type, reference);
        
        sendWalletUpdate(user, balance.getBalance(), amount, type);
    }

    /**
     * Commit a previously made reservation, converting it into a final deduction.
     */
    @Transactional
    @Retryable(
            retryFor = {DeadlockLoserDataAccessException.class, PessimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public void commitReservation(Long userId, long amount, WalletTransactionType type, String reference) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Commit amount must be positive");
        }

        UserWallet balance = getOrCreateBalance(userId, true);
        if (balance.getReservedBalance() < amount) {
            throw new IllegalArgumentException("Insufficient reserved tokens to commit");
        }

        // Deduct from total balance AND release reservation
        balance.setBalance(balance.getBalance() - amount);
        balance.setReservedBalance(balance.getReservedBalance() - amount);
        balance.setUpdatedAt(Instant.now());
        tokenBalanceRepository.save(balance);

        User user = balance.getUserId();
        tokenTransactionRepository.save(WalletTransaction.builder()
                .userId(user)
                .amount(-amount)
                .type(type)
                .reason(deriveReason(type, reference))
                .referenceId(reference)
                .build());

        log.info("TOKEN: Committed reservation of {} tokens from creator {} for {}. Ref: {}", amount, user.getEmail(), type, reference);
        
        sendWalletUpdate(user, balance.getBalance(), -amount, type);
    }

    private void sendWalletUpdate(User user, long newBalance, long delta, WalletTransactionType type) {
        Map<String, Object> payload = Map.of(
            "type", "WALLET_UPDATE",
            "balance", newBalance,
            "delta", delta,
            "transactionType", type.name()
        );
        
        messagingTemplate.convertAndSendToUser(
            user.getId().toString(),
            "/queue/wallet",
            payload
        );
    }

    private String deriveReason(WalletTransactionType type, String referenceId) {
        if (type == WalletTransactionType.LIVESTREAM_ADMISSION) {
            return "Unlock livestream access (creator=" + referenceId + ")";
        }
        return type.name();
    }

    public UserWallet getOrCreateWallet(Long userId) {
        return getOrCreateBalance(userId, false);
    }

    private UserWallet getOrCreateBalance(Long userId, boolean lock) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Optional<UserWallet> existing = lock ?
                tokenBalanceRepository.findByUserIdWithLock(user) : 
                tokenBalanceRepository.findByUserId(user);

        return existing.orElseGet(() -> tokenBalanceRepository.save(
                        UserWallet.builder()
                                .userId(user)
                                .balance(0)
                                .reservedBalance(0)
                                .updatedAt(Instant.now())
                                .build()
                ));
    }
}
