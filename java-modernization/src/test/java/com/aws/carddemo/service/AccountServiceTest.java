package com.aws.carddemo.service;

import com.aws.carddemo.dto.AccountDTO;
import com.aws.carddemo.exception.ResourceNotFoundException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;
    private AccountDTO testAccountDTO;

    @BeforeEach
    void setUp() {
        testAccount = new Account();
        testAccount.setAccountId(12345678901L);
        testAccount.setActiveStatus("Y");
        testAccount.setCurrentBalance(new BigDecimal("5000.00"));
        testAccount.setCreditLimit(new BigDecimal("10000.00"));
        testAccount.setCashCreditLimit(new BigDecimal("2000.00"));
        testAccount.setOpenDate(LocalDate.of(2020, 1, 1));
        testAccount.setExpirationDate(LocalDate.of(2025, 1, 1));
        testAccount.setAddressZip("12345");
        testAccount.setGroupId("GRP001");

        testAccountDTO = new AccountDTO();
        testAccountDTO.setAccountId(12345678901L);
        testAccountDTO.setActiveStatus("Y");
        testAccountDTO.setCurrentBalance(new BigDecimal("5000.00"));
        testAccountDTO.setCreditLimit(new BigDecimal("10000.00"));
        testAccountDTO.setCashCreditLimit(new BigDecimal("2000.00"));
        testAccountDTO.setOpenDate(LocalDate.of(2020, 1, 1));
        testAccountDTO.setExpirationDate(LocalDate.of(2025, 1, 1));
        testAccountDTO.setAddressZip("12345");
        testAccountDTO.setGroupId("GRP001");
    }

    @Test
    void getAccountById_Success() {
        when(accountRepository.findByAccountId(anyLong())).thenReturn(Optional.of(testAccount));

        AccountDTO result = accountService.getAccountById(12345678901L);

        assertNotNull(result);
        assertEquals(testAccount.getAccountId(), result.getAccountId());
        assertEquals(testAccount.getActiveStatus(), result.getActiveStatus());
        verify(accountRepository, times(1)).findByAccountId(12345678901L);
    }

    @Test
    void getAccountById_NotFound() {
        when(accountRepository.findByAccountId(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            accountService.getAccountById(12345678901L);
        });

        verify(accountRepository, times(1)).findByAccountId(12345678901L);
    }

    @Test
    void getAllAccounts_Success() {
        List<Account> accounts = Arrays.asList(testAccount);
        when(accountRepository.findAll()).thenReturn(accounts);

        List<AccountDTO> result = accountService.getAllAccounts();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(accountRepository, times(1)).findAll();
    }

    @Test
    void createAccount_Success() {
        when(accountRepository.existsByAccountId(anyLong())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        AccountDTO result = accountService.createAccount(testAccountDTO);

        assertNotNull(result);
        assertEquals(testAccountDTO.getAccountId(), result.getAccountId());
        verify(accountRepository, times(1)).existsByAccountId(testAccountDTO.getAccountId());
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void createAccount_AlreadyExists() {
        when(accountRepository.existsByAccountId(anyLong())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> {
            accountService.createAccount(testAccountDTO);
        });

        verify(accountRepository, times(1)).existsByAccountId(testAccountDTO.getAccountId());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void updateAccount_Success() {
        when(accountRepository.findByAccountId(anyLong())).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        AccountDTO result = accountService.updateAccount(12345678901L, testAccountDTO);

        assertNotNull(result);
        verify(accountRepository, times(1)).findByAccountId(12345678901L);
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void deleteAccount_Success() {
        when(accountRepository.existsByAccountId(anyLong())).thenReturn(true);
        doNothing().when(accountRepository).deleteById(anyLong());

        accountService.deleteAccount(12345678901L);

        verify(accountRepository, times(1)).existsByAccountId(12345678901L);
        verify(accountRepository, times(1)).deleteById(12345678901L);
    }

    @Test
    void deleteAccount_NotFound() {
        when(accountRepository.existsByAccountId(anyLong())).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> {
            accountService.deleteAccount(12345678901L);
        });

        verify(accountRepository, times(1)).existsByAccountId(12345678901L);
        verify(accountRepository, never()).deleteById(anyLong());
    }
}
