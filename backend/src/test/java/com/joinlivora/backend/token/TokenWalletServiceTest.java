package com.joinlivora.backend.token;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.wallet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenWalletServiceTest {

    @Mock
    private UserWalletRepository tokenBalanceRepository;

    @Mock
    private WalletTransactionRepository tokenTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private TokenWalletService tokenWalletService;

    private User user;
    private Long userId = 1L;
    private UserWallet balance;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");

        balance = UserWallet.builder()
                .userId(user)
                .balance(100)
                .reservedBalance(0)
                .build();
    }

    @Test
    void getAvailableBalance_ShouldReturnCorrectBalance() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenBalanceRepository.findByUserId(user)).thenReturn(Optional.of(balance));

        long available = tokenWalletService.getAvailableBalance(userId);

        assertEquals(100, available);
    }

    @Test
    void getTotalBalance_ShouldReturnCorrectBalance() {
        balance.setReservedBalance(30);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenBalanceRepository.findByUserId(user)).thenReturn(Optional.of(balance));

        long total = tokenWalletService.getTotalBalance(userId);

        assertEquals(100, total);
    }

    @Test
    void reserveTokens_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenBalanceRepository.findByUserIdWithLock(user)).thenReturn(Optional.of(balance));

        tokenWalletService.reserveTokens(userId, 40);

        assertEquals(40, balance.getReservedBalance());
        assertEquals(60, balance.getAvailableBalance());
        verify(tokenBalanceRepository).save(balance);
    }

    @Test
    void reserveTokens_InsufficientBalance_ShouldThrowException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenBalanceRepository.findByUserIdWithLock(user)).thenReturn(Optional.of(balance));

        assertThrows(InsufficientBalanceException.class, () -> 
                tokenWalletService.reserveTokens(userId, 110));
        
        verify(tokenBalanceRepository, never()).save(any());
    }

    @Test
    void cancelReservation_Success() {
        balance.setReservedBalance(50);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenBalanceRepository.findByUserIdWithLock(user)).thenReturn(Optional.of(balance));

        tokenWalletService.cancelReservation(userId, 30);

        assertEquals(20, balance.getReservedBalance());
        verify(tokenBalanceRepository).save(balance);
    }

    @Test
    void deductTokens_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenBalanceRepository.findByUserIdWithLock(user)).thenReturn(Optional.of(balance));

        tokenWalletService.deductTokens(userId, 50, WalletTransactionType.CHAT, "Ref 123");

        assertEquals(50, balance.getBalance());
        verify(tokenBalanceRepository).save(balance);
        verify(tokenTransactionRepository).save(any(WalletTransaction.class));
        verify(messagingTemplate).convertAndSendToUser(eq(user.getId().toString()), eq("/queue/wallet"), anyMap());
    }

    @Test
    void deductTokens_InsufficientBalance_ShouldThrowException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenBalanceRepository.findByUserIdWithLock(user)).thenReturn(Optional.of(balance));

        assertThrows(InsufficientBalanceException.class, () -> 
                tokenWalletService.deductTokens(userId, 150, WalletTransactionType.CHAT, "Ref 123"));
    }

    @Test
    void commitReservation_Success() {
        balance.setReservedBalance(40);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenBalanceRepository.findByUserIdWithLock(user)).thenReturn(Optional.of(balance));

        tokenWalletService.commitReservation(userId, 40, WalletTransactionType.TIP, "Ref 456");

        assertEquals(60, balance.getBalance());
        assertEquals(0, balance.getReservedBalance());
        verify(tokenBalanceRepository).save(balance);
        verify(tokenTransactionRepository).save(any(WalletTransaction.class));
        verify(messagingTemplate).convertAndSendToUser(eq(user.getId().toString()), eq("/queue/wallet"), anyMap());
    }

    @Test
    void addTokens_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenBalanceRepository.findByUserIdWithLock(user)).thenReturn(Optional.of(balance));

        tokenWalletService.addTokens(userId, 50, WalletTransactionType.PURCHASE, "Ref 789");

        assertEquals(150, balance.getBalance());
        verify(tokenBalanceRepository).save(balance);
        verify(tokenTransactionRepository).save(any(WalletTransaction.class));
        verify(messagingTemplate).convertAndSendToUser(eq(user.getId().toString()), eq("/queue/wallet"), anyMap());
    }

    @Test
    void reserveTokens_UserNotFound_ShouldThrowException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
                tokenWalletService.reserveTokens(userId, 10));
    }
}








