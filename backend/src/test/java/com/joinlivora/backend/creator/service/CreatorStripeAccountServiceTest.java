package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.dto.CreatorStripeStatusResponse;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.CreatorStripeAccount;
import com.joinlivora.backend.creator.repository.CreatorStripeAccountRepository;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.param.AccountCreateParams;
import com.stripe.service.AccountService;
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
class CreatorStripeAccountServiceTest {

    @Mock
    private CreatorStripeAccountRepository repository;

    @Mock
    private StripeClient stripeClient;

    @Mock
    private AccountService accountService;

    @Mock
    private Account account;

    @InjectMocks
    private CreatorStripeAccountService service;

    private CreatorProfile creator;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("creator@example.com");
        creator = CreatorProfile.builder().id(1L).user(user).build();
    }

    @Test
    void createOrGetStripeAccount_NewAccount_ShouldCreateInStripeAndSave() throws StripeException {
        String stripeId = "acct_123";
        when(repository.findByCreator(creator)).thenReturn(Optional.empty());
        when(stripeClient.accounts()).thenReturn(accountService);
        when(accountService.create(any(AccountCreateParams.class))).thenReturn(account);
        when(account.getId()).thenReturn(stripeId);

        String result = service.createOrGetStripeAccount(creator);

        assertEquals(stripeId, result);
        verify(repository).save(any(CreatorStripeAccount.class));
        verify(accountService).create(any(AccountCreateParams.class));
    }

    @Test
    void createOrGetStripeAccount_ExistingAccount_ShouldReturnExisting() throws StripeException {
        String stripeId = "acct_existing";
        CreatorStripeAccount existing = CreatorStripeAccount.builder()
                .stripeAccountId(stripeId)
                .build();
        when(repository.findByCreator(creator)).thenReturn(Optional.of(existing));

        String result = service.createOrGetStripeAccount(creator);

        assertEquals(stripeId, result);
        verify(repository, never()).save(any());
        verify(stripeClient, never()).accounts();
    }

    @Test
    void createOrUpdateStripeAccount_NewAccount_ShouldSaveNew() {
        String stripeId = "acct_123";
        when(repository.findByCreator(creator)).thenReturn(Optional.empty());
        when(repository.save(any(CreatorStripeAccount.class))).thenAnswer(i -> i.getArguments()[0]);

        CreatorStripeAccount result = service.createOrUpdateStripeAccount(creator, stripeId, false);

        assertNotNull(result);
        assertEquals(creator, result.getCreator());
        assertEquals(stripeId, result.getStripeAccountId());
        assertFalse(result.isOnboardingCompleted());
        verify(repository).save(any(CreatorStripeAccount.class));
    }

    @Test
    void createOrUpdateStripeAccount_ExistingAccount_ShouldUpdate() {
        String oldStripeId = "acct_old";
        String newStripeId = "acct_new";
        CreatorStripeAccount existing = CreatorStripeAccount.builder()
                .creator(creator)
                .stripeAccountId(oldStripeId)
                .onboardingCompleted(false)
                .build();

        when(repository.findByCreator(creator)).thenReturn(Optional.of(existing));
        when(repository.save(any(CreatorStripeAccount.class))).thenAnswer(i -> i.getArguments()[0]);

        CreatorStripeAccount result = service.createOrUpdateStripeAccount(creator, newStripeId, true);

        assertEquals(newStripeId, result.getStripeAccountId());
        assertTrue(result.isOnboardingCompleted());
        verify(repository).save(existing);
    }

    @Test
    void updateAccountStatus_ShouldUpdateStatus() {
        String stripeId = "acct_123";
        CreatorStripeAccount existing = CreatorStripeAccount.builder()
                .stripeAccountId(stripeId)
                .onboardingCompleted(false)
                .build();

        when(repository.findByStripeAccountId(stripeId)).thenReturn(Optional.of(existing));

        service.updateAccountStatus(stripeId, true);

        assertTrue(existing.isOnboardingCompleted());
        verify(repository).save(existing);
    }

    @Test
    void getStripeStatus_NoAccount_ShouldReturnFalseValues() throws StripeException {
        when(repository.findByCreator(creator)).thenReturn(Optional.empty());

        CreatorStripeStatusResponse result = service.getStripeStatus(creator);

        assertFalse(result.isHasAccount());
        assertFalse(result.isOnboardingCompleted());
        assertFalse(result.isPayoutsEnabled());
    }

    @Test
    void getStripeStatus_ExistingAccount_ShouldFetchFromStripe() throws StripeException {
        String stripeId = "acct_123";
        CreatorStripeAccount existing = CreatorStripeAccount.builder()
                .stripeAccountId(stripeId)
                .onboardingCompleted(false)
                .build();

        when(repository.findByCreator(creator)).thenReturn(Optional.of(existing));
        when(stripeClient.accounts()).thenReturn(accountService);
        when(accountService.retrieve(stripeId)).thenReturn(account);
        when(account.getDetailsSubmitted()).thenReturn(true);
        when(account.getPayoutsEnabled()).thenReturn(true);

        CreatorStripeStatusResponse result = service.getStripeStatus(creator);

        assertTrue(result.isHasAccount());
        assertTrue(result.isOnboardingCompleted());
        assertTrue(result.isPayoutsEnabled());
        verify(repository).save(existing);
        assertTrue(existing.isOnboardingCompleted());
    }
}








