package com.joinlivora.backend.payout;

import com.joinlivora.backend.creator.model.StripeOnboardingStatus;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.service.AccountLinkService;
import com.stripe.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeConnectServiceTest {

    @Mock
    private StripeClient stripeClient;

    @Mock
    private LegacyCreatorStripeAccountRepository repository;

    @Mock
    private AccountService accountService;

    @Mock
    private AccountLinkService accountLinkService;

    @InjectMocks
    private StripeConnectService stripeConnectService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
    }

    @Test
    void createOrGetStripeAccount_NewAccount_ShouldCreateAndSave() throws Exception {
        when(repository.findByCreatorId(1L)).thenReturn(Optional.empty());
        when(stripeClient.accounts()).thenReturn(accountService);
        
        Account mockAccount = mock(Account.class);
        when(mockAccount.getId()).thenReturn("acct_123");
        when(accountService.create(any(com.stripe.param.AccountCreateParams.class))).thenReturn(mockAccount);

        String accountId = stripeConnectService.createOrGetStripeAccount(creator);

        assertEquals("acct_123", accountId);
        verify(repository).save(argThat(account -> 
            account.getStripeAccountId().equals("acct_123") && 
            account.getOnboardingStatus() == StripeOnboardingStatus.PENDING
        ));
    }

    @Test
    void createOrGetStripeAccount_ExistingAccount_ShouldReturnExisting() throws Exception {
        LegacyCreatorStripeAccount existing = LegacyCreatorStripeAccount.builder()
                .stripeAccountId("acct_existing")
                .build();
        when(repository.findByCreatorId(1L)).thenReturn(Optional.of(existing));

        String accountId = stripeConnectService.createOrGetStripeAccount(creator);

        assertEquals("acct_existing", accountId);
        verify(stripeClient, never()).accounts();
        verify(repository, never()).save(any());
    }

    @Test
    void generateOnboardingLink_ShouldReturnUrl() throws Exception {
        when(stripeClient.accountLinks()).thenReturn(accountLinkService);
        
        AccountLink mockAccountLink = mock(AccountLink.class);
        when(mockAccountLink.getUrl()).thenReturn("https://stripe.com/onboard/123");
        when(accountLinkService.create(any(com.stripe.param.AccountLinkCreateParams.class))).thenReturn(mockAccountLink);

        String url = stripeConnectService.generateOnboardingLink("acct_123", "http://return", "http://refresh");

        assertEquals("https://stripe.com/onboard/123", url);
    }

    @Test
    void updateAccountStatus_ShouldUpdateAndSave() {
        LegacyCreatorStripeAccount account = LegacyCreatorStripeAccount.builder()
                .stripeAccountId("acct_123")
                .chargesEnabled(false)
                .payoutsEnabled(false)
                .onboardingCompleted(false)
                .onboardingStatus(StripeOnboardingStatus.PENDING)
                .build();
        
        when(repository.findByStripeAccountId("acct_123")).thenReturn(Optional.of(account));

        stripeConnectService.updateAccountStatus("acct_123", true, true, true);

        verify(repository).save(account);
        assertEquals(true, account.isChargesEnabled());
        assertEquals(true, account.isPayoutsEnabled());
        assertEquals(true, account.isOnboardingCompleted());
        assertEquals(StripeOnboardingStatus.VERIFIED, account.getOnboardingStatus());
    }

    @Test
    void refreshOnboardingStatus_NotStarted_ShouldReturnNotStarted() throws Exception {
        when(repository.findByCreatorId(1L)).thenReturn(Optional.empty());

        StripeOnboardingStatus status = stripeConnectService.refreshOnboardingStatus(creator);

        assertEquals(StripeOnboardingStatus.NOT_STARTED, status);
    }

    @Test
    void refreshOnboardingStatus_AlreadyVerified_ShouldReturnVerified() throws Exception {
        LegacyCreatorStripeAccount account = LegacyCreatorStripeAccount.builder()
                .onboardingStatus(StripeOnboardingStatus.VERIFIED)
                .build();
        when(repository.findByCreatorId(1L)).thenReturn(Optional.of(account));

        StripeOnboardingStatus status = stripeConnectService.refreshOnboardingStatus(creator);

        assertEquals(StripeOnboardingStatus.VERIFIED, status);
        verify(stripeClient, never()).accounts();
    }

    @Test
    void refreshOnboardingStatus_Pending_ShouldRetrieveFromStripe() throws Exception {
        LegacyCreatorStripeAccount account = LegacyCreatorStripeAccount.builder()
                .stripeAccountId("acct_123")
                .onboardingStatus(StripeOnboardingStatus.PENDING)
                .onboardingCompleted(false)
                .build();
        when(repository.findByCreatorId(1L)).thenReturn(Optional.of(account));
        when(stripeClient.accounts()).thenReturn(accountService);

        Account stripeAccount = mock(Account.class);
        when(stripeAccount.getDetailsSubmitted()).thenReturn(true);
        when(accountService.retrieve("acct_123")).thenReturn(stripeAccount);

        StripeOnboardingStatus status = stripeConnectService.refreshOnboardingStatus(creator);

        assertEquals(StripeOnboardingStatus.VERIFIED, status);
        assertEquals(true, account.isOnboardingCompleted());
        assertEquals(StripeOnboardingStatus.VERIFIED, account.getOnboardingStatus());
        verify(repository).save(account);
    }
}








