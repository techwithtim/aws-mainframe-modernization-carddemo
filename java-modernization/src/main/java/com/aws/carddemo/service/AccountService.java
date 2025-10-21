package com.aws.carddemo.service;

import com.aws.carddemo.dto.AccountDTO;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountDTO getAccountById(Long accountId) {
        log.info("Fetching account with ID: {}", accountId);
        Account account = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));
        return mapToDTO(account);
    }

    public List<AccountDTO> getAllAccounts() {
        log.info("Fetching all accounts");
        return accountRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<AccountDTO> getAccountsByStatus(String status) {
        log.info("Fetching accounts with status: {}", status);
        return accountRepository.findByActiveStatus(status).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public AccountDTO createAccount(AccountDTO accountDTO) {
        log.info("Creating new account with ID: {}", accountDTO.getAccountId());
        if (accountRepository.existsByAccountId(accountDTO.getAccountId())) {
            throw new IllegalArgumentException("Account already exists with ID: " + accountDTO.getAccountId());
        }
        Account account = mapToEntity(accountDTO);
        Account savedAccount = accountRepository.save(account);
        return mapToDTO(savedAccount);
    }

    public AccountDTO updateAccount(Long accountId, AccountDTO accountDTO) {
        log.info("Updating account with ID: {}", accountId);
        Account existingAccount = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        existingAccount.setActiveStatus(accountDTO.getActiveStatus());
        existingAccount.setCurrentBalance(accountDTO.getCurrentBalance());
        existingAccount.setCreditLimit(accountDTO.getCreditLimit());
        existingAccount.setCashCreditLimit(accountDTO.getCashCreditLimit());
        existingAccount.setOpenDate(accountDTO.getOpenDate());
        existingAccount.setExpirationDate(accountDTO.getExpirationDate());
        existingAccount.setReissueDate(accountDTO.getReissueDate());
        existingAccount.setCurrentCycleCredit(accountDTO.getCurrentCycleCredit());
        existingAccount.setCurrentCycleDebit(accountDTO.getCurrentCycleDebit());
        existingAccount.setAddressZip(accountDTO.getAddressZip());
        existingAccount.setGroupId(accountDTO.getGroupId());

        Account updatedAccount = accountRepository.save(existingAccount);
        return mapToDTO(updatedAccount);
    }

    public void deleteAccount(Long accountId) {
        log.info("Deleting account with ID: {}", accountId);
        if (!accountRepository.existsByAccountId(accountId)) {
            throw new ResourceNotFoundException("Account not found with ID: " + accountId);
        }
        accountRepository.deleteById(accountId);
    }

    private AccountDTO mapToDTO(Account account) {
        AccountDTO dto = new AccountDTO();
        dto.setAccountId(account.getAccountId());
        dto.setActiveStatus(account.getActiveStatus());
        dto.setCurrentBalance(account.getCurrentBalance());
        dto.setCreditLimit(account.getCreditLimit());
        dto.setCashCreditLimit(account.getCashCreditLimit());
        dto.setOpenDate(account.getOpenDate());
        dto.setExpirationDate(account.getExpirationDate());
        dto.setReissueDate(account.getReissueDate());
        dto.setCurrentCycleCredit(account.getCurrentCycleCredit());
        dto.setCurrentCycleDebit(account.getCurrentCycleDebit());
        dto.setAddressZip(account.getAddressZip());
        dto.setGroupId(account.getGroupId());
        return dto;
    }

    private Account mapToEntity(AccountDTO dto) {
        Account account = new Account();
        account.setAccountId(dto.getAccountId());
        account.setActiveStatus(dto.getActiveStatus());
        account.setCurrentBalance(dto.getCurrentBalance());
        account.setCreditLimit(dto.getCreditLimit());
        account.setCashCreditLimit(dto.getCashCreditLimit());
        account.setOpenDate(dto.getOpenDate());
        account.setExpirationDate(dto.getExpirationDate());
        account.setReissueDate(dto.getReissueDate());
        account.setCurrentCycleCredit(dto.getCurrentCycleCredit());
        account.setCurrentCycleDebit(dto.getCurrentCycleDebit());
        account.setAddressZip(dto.getAddressZip());
        account.setGroupId(dto.getGroupId());
        return account;
    }
}
