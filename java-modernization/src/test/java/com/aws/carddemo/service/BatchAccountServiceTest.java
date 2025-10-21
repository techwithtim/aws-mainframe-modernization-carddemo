package com.aws.carddemo.service;

import com.aws.carddemo.model.Account;
import com.aws.carddemo.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchAccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private BatchAccountService batchAccountService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = new Account();
        testAccount.setAccountId(1L);
        testAccount.setAccountNumber("12345678901");
        testAccount.setCurrentBalance(new BigDecimal("1000.00"));
        testAccount.setCreditLimit(new BigDecimal("5000.00"));
        testAccount.setActiveStatus("Y");
        testAccount.setCurrentCycleCredit(BigDecimal.ZERO);
        testAccount.setCurrentCycleDebit(BigDecimal.ZERO);
    }

    @Test
    void testProcessAllAccounts() {
        List<Account> accounts = Arrays.asList(testAccount);
        when(accountRepository.findAll()).thenReturn(accounts);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        batchAccountService.processAllAccounts();

        verify(accountRepository, times(1)).findAll();
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void testCalculateInterest() {
        List<Account> accounts = Arrays.asList(testAccount);
        when(accountRepository.findAll()).thenReturn(accounts);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        batchAccountService.calculateInterest();

        verify(accountRepository, times(1)).findAll();
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void testResetCycleCounters() {
        List<Account> accounts = Arrays.asList(testAccount);
        when(accountRepository.findAll()).thenReturn(accounts);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        batchAccountService.resetCycleCounters();

        verify(accountRepository, times(1)).findAll();
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void testCloseExpiredAccounts() {
        testAccount.setExpirationDate(LocalDate.now().minusDays(1));
        List<Account> accounts = Arrays.asList(testAccount);
        when(accountRepository.findAll()).thenReturn(accounts);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        batchAccountService.closeExpiredAccounts();

        verify(accountRepository, times(1)).findAll();
        verify(accountRepository, times(1)).save(any(Account.class));
    }
}
