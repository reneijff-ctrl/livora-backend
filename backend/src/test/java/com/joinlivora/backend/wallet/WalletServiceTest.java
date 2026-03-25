package com.joinlivora.backend.wallet;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private UserWalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WalletService walletService;

    private User user;
    private Long userId = 1L;
    private UserWallet wallet;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);
        user.setEmail("test@test.com");

        wallet = UserWallet.builder()
                .userId(user)
                .balance(100)
                .reservedBalance(0)
                .build();
    }

    @Test
    void addTokens_ShouldIncreaseBalanceAndRecordTransaction() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(user)).thenReturn(Optional.of(wallet));

        walletService.addTokens(userId, 50, WalletTransactionType.PURCHASE, "ref123");

        assertEquals(150, wallet.getBalance());
        verify(walletRepository).save(wallet);
        verify(transactionRepository).save(argThat(tx -> 
            tx.getUserId().equals(user) && 
            tx.getAmount() == 50 && 
            tx.getType() == WalletTransactionType.PURCHASE &&
            "ref123".equals(tx.getReferenceId())
        ));
    }

    @Test
    void deductTokens_Success_ShouldDecreaseBalanceAndRecordTransaction() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(user)).thenReturn(Optional.of(wallet));

        walletService.deductTokens(userId, 30, WalletTransactionType.TIP, "ref456");

        assertEquals(70, wallet.getBalance());
        verify(walletRepository).save(wallet);
        verify(transactionRepository).save(argThat(tx -> 
            tx.getUserId().equals(user) && 
            tx.getAmount() == -30 && 
            tx.getType() == WalletTransactionType.TIP &&
            "TIP".equals(tx.getReason())
        ));
    }

    @Test
    void deductTokens_liveStreamAdmission_ShouldHaveDetailedReason() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(user)).thenReturn(Optional.of(wallet));

        String creatorId = "999";
        walletService.deductTokens(userId, 20, WalletTransactionType.LIVESTREAM_ADMISSION, creatorId);

        assertEquals(80, wallet.getBalance());
        verify(transactionRepository).save(argThat(tx -> 
            tx.getType() == WalletTransactionType.LIVESTREAM_ADMISSION &&
            ("Unlock Stream access (creator=" + creatorId + ")").equals(tx.getReason())
        ));
    }

    @Test
    void deductTokens_InsufficientBalance_ShouldThrowException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(user)).thenReturn(Optional.of(wallet));

        assertThrows(InsufficientBalanceException.class, () -> 
            walletService.deductTokens(userId, 150, WalletTransactionType.CHAT, "ref789")
        );
        
        verify(walletRepository, never()).save(any());
    }

    @Test
    void getBalance_ShouldReturnCurrentBalance() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(user)).thenReturn(Optional.of(wallet));

        long balance = walletService.getBalance(userId);

        assertEquals(100, balance);
    }

    @Test
    void addTokens_InvalidAmount_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> 
            walletService.addTokens(userId, -10, WalletTransactionType.PURCHASE, "ref")
        );
    }
}








