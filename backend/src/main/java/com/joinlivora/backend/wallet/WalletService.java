package com.joinlivora.backend.wallet;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class WalletService {
    // TODO: Consolidate all balance management into TokenWalletService and remove this class.

    private final UserWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public void addTokens(Long userId, long amount, WalletTransactionType type, String referenceId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        UserWallet wallet = getOrCreateWallet(userId);
        wallet.setBalance(wallet.getBalance() + amount);
        wallet.setUpdatedAt(Instant.now());
        walletRepository.save(wallet);

        createTransaction(wallet.getUserId(), amount, type, referenceId);
        log.info("WALLET: Added {} tokens to user {}. Type: {}, Ref: {}", amount, userId, type, referenceId);
    }

    @Transactional
    public void deductTokens(Long userId, long amount, WalletTransactionType type, String referenceId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        UserWallet wallet = getOrCreateWallet(userId);
        if (wallet.getAvailableBalance() < amount) {
            throw new InsufficientBalanceException("Insufficient token balance");
        }

        wallet.setBalance(wallet.getBalance() - amount);
        wallet.setUpdatedAt(Instant.now());
        walletRepository.save(wallet);

        createTransaction(wallet.getUserId(), -amount, type, referenceId);
        log.info("WALLET: Deducted {} tokens from user {}. Type: {}, Ref: {}", amount, userId, type, referenceId);
    }

    public long getBalance(Long userId) {
        return getOrCreateWallet(userId).getBalance();
    }

    public UserWallet getOrCreateWallet(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        return walletRepository.findByUserId(user)
                .orElseGet(() -> walletRepository.save(
                        UserWallet.builder()
                                .userId(user)
                                .balance(0)
                                .reservedBalance(0)
                                .updatedAt(Instant.now())
                                .build()
                ));
    }

    private void createTransaction(User user, long amount, WalletTransactionType type, String referenceId) {
        WalletTransaction tx = WalletTransaction.builder()
                .userId(user)
                .amount(amount)
                .type(type)
                .referenceId(referenceId)
                .build();
        
        if (type == WalletTransactionType.LIVESTREAM_ADMISSION) {
            tx.setReason("Unlock livestream access (creator=" + referenceId + ")");
        } else {
            tx.setReason(type.name());
        }
        
        transactionRepository.save(tx);
    }
}
