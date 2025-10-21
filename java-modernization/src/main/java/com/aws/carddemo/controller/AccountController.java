package com.aws.carddemo.controller;

import com.aws.carddemo.dto.AccountDTO;
import com.aws.carddemo.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{id}")
    public ResponseEntity<AccountDTO> getAccountById(@PathVariable Long id) {
        log.info("GET /api/v1/accounts/{}", id);
        AccountDTO account = accountService.getAccountById(id);
        return ResponseEntity.ok(account);
    }

    @GetMapping
    public ResponseEntity<List<AccountDTO>> getAllAccounts(
            @RequestParam(required = false) String status) {
        log.info("GET /api/v1/accounts with status: {}", status);
        List<AccountDTO> accounts;
        if (status != null && !status.isEmpty()) {
            accounts = accountService.getAccountsByStatus(status);
        } else {
            accounts = accountService.getAllAccounts();
        }
        return ResponseEntity.ok(accounts);
    }

    @PostMapping
    public ResponseEntity<AccountDTO> createAccount(@Valid @RequestBody AccountDTO accountDTO) {
        log.info("POST /api/v1/accounts");
        AccountDTO createdAccount = accountService.createAccount(accountDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAccount);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountDTO> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountDTO accountDTO) {
        log.info("PUT /api/v1/accounts/{}", id);
        AccountDTO updatedAccount = accountService.updateAccount(id, accountDTO);
        return ResponseEntity.ok(updatedAccount);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        log.info("DELETE /api/v1/accounts/{}", id);
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }
}
