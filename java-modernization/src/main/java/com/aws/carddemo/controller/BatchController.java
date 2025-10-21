package com.aws.carddemo.controller;

import com.aws.carddemo.service.BatchAccountService;
import com.aws.carddemo.service.BatchTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/batch")
public class BatchController {

    @Autowired
    private BatchAccountService batchAccountService;

    @Autowired
    private BatchTransactionService batchTransactionService;

    @PostMapping("/accounts/process")
    public ResponseEntity<Map<String, String>> processAccounts() {
        batchAccountService.processAllAccounts();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Account batch processing completed successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/accounts/calculate-interest")
    public ResponseEntity<Map<String, String>> calculateInterest() {
        batchAccountService.calculateInterest();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Interest calculation completed successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/accounts/reset-cycles")
    public ResponseEntity<Map<String, String>> resetCycleCounters() {
        batchAccountService.resetCycleCounters();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Cycle counters reset successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/accounts/close-expired")
    public ResponseEntity<Map<String, String>> closeExpiredAccounts() {
        batchAccountService.closeExpiredAccounts();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Expired accounts closed successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transactions/post-daily")
    public ResponseEntity<Map<String, String>> postDailyTransactions() {
        batchTransactionService.postDailyTransactions();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Daily transactions posted successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transactions/purge")
    public ResponseEntity<Map<String, String>> purgeOldTransactions(@RequestParam(defaultValue = "365") int daysToKeep) {
        batchTransactionService.purgeOldTransactions(daysToKeep);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Old transactions purged successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/summary")
    public ResponseEntity<Map<String, String>> generateTransactionSummary() {
        batchTransactionService.generateTransactionSummary();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Transaction summary generated successfully");
        return ResponseEntity.ok(response);
    }
}
