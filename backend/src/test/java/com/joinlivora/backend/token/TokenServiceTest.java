package com.joinlivora.backend.token;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.wallet.*;
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
class TokenServiceTest {

    @Mock
    private UserWalletRepository tokenBalanceRepository;
    @Mock
    private WalletTransactionRepository tokenTransactionRepository;
    @Mock
    private TokenPackageRepository tokenPackageRepository;
    @Mock
    private TipRecordRepository tipRecordRepository;
    @Mock
    private CreatorEarningsRepository creatorEarningsRepository;
    @Mock
    private StreamRepository StreamRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenWalletService tokenWalletService;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;

    @InjectMocks
    private TokenService tokenService;

    private User user;
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);
        user.setEmail("test@test.com");
    }

    @Test
    void creditTokens_Success_ShouldProcess() {
        tokenService.creditTokens(user, 50, "ref1");

        verify(tokenWalletService).addTokens(eq(userId), eq(50L), eq(WalletTransactionType.PURCHASE), eq("ref1"));
    }

    @Test
    void spendTokens_Success_ShouldDeductTokens() {
        tokenService.spendTokens(userId, 50, WalletTransactionType.CHAT, "ref123");

        verify(tokenWalletService).deductTokens(eq(userId), eq(50L), eq(WalletTransactionType.CHAT), eq("ref123"));
    }

    @Test
    void spendTokens_InsufficientBalance_ShouldThrowException() {
        doThrow(new com.joinlivora.backend.exception.InsufficientBalanceException("Insufficient token balance"))
                .when(tokenWalletService).deductTokens(anyLong(), anyLong(), any(), anyString());

        com.joinlivora.backend.exception.InsufficientBalanceException exception = assertThrows(com.joinlivora.backend.exception.InsufficientBalanceException.class, 
            () -> tokenService.spendTokens(userId, 50, WalletTransactionType.CHAT, "ref123"));
        
        assertEquals("Insufficient token balance", exception.getMessage());
    }

    @Test
    void spendTokens_UserNotFound_ShouldThrowException() {
        doThrow(new com.joinlivora.backend.exception.ResourceNotFoundException("User not found: " + userId))
                .when(tokenWalletService).deductTokens(anyLong(), anyLong(), any(), anyString());

        com.joinlivora.backend.exception.ResourceNotFoundException exception = assertThrows(com.joinlivora.backend.exception.ResourceNotFoundException.class, 
            () -> tokenService.spendTokens(userId, 50, WalletTransactionType.CHAT, "ref123"));
        
        assertTrue(exception.getMessage().contains("User not found"));
    }

    @Test
    void spendTokens_InvalidAmount_ShouldThrowException() {
        doThrow(new IllegalArgumentException("Amount must be positive"))
                .when(tokenWalletService).deductTokens(anyLong(), anyLong(), any(), anyString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> tokenService.spendTokens(userId, -10, WalletTransactionType.CHAT, "ref123"));
        
        assertEquals("Amount must be positive", exception.getMessage());
    }
}










