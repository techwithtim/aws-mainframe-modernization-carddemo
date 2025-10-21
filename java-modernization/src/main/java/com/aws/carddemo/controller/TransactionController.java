package com.aws.carddemo.controller;

import com.aws.carddemo.dto.TransactionDTO;
import com.aws.carddemo.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable String id) {
        log.info("GET /api/v1/transactions/{}", id);
        TransactionDTO transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }

    @GetMapping
    public ResponseEntity<List<TransactionDTO>> getTransactions(
            @RequestParam(required = false) String cardNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("GET /api/v1/transactions with cardNumber: {}, startDate: {}, endDate: {}", 
                cardNumber, startDate, endDate);
        
        List<TransactionDTO> transactions;
        if (cardNumber != null && startDate != null && endDate != null) {
            transactions = transactionService.getTransactionsByCardNumberAndDateRange(cardNumber, startDate, endDate);
        } else if (cardNumber != null) {
            transactions = transactionService.getTransactionsByCardNumber(cardNumber);
        } else {
            transactions = transactionService.getAllTransactions();
        }
        return ResponseEntity.ok(transactions);
    }

    @PostMapping
    public ResponseEntity<TransactionDTO> createTransaction(@Valid @RequestBody TransactionDTO transactionDTO) {
        log.info("POST /api/v1/transactions");
        TransactionDTO createdTransaction = transactionService.createTransaction(transactionDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTransaction);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionDTO> updateTransaction(
            @PathVariable String id,
            @Valid @RequestBody TransactionDTO transactionDTO) {
        log.info("PUT /api/v1/transactions/{}", id);
        TransactionDTO updatedTransaction = transactionService.updateTransaction(id, transactionDTO);
        return ResponseEntity.ok(updatedTransaction);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable String id) {
        log.info("DELETE /api/v1/transactions/{}", id);
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
}
