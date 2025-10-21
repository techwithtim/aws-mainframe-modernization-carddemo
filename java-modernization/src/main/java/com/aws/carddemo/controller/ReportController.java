package com.aws.carddemo.controller;

import com.aws.carddemo.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/accounts/summary")
    public ResponseEntity<Map<String, Object>> getAccountSummaryReport() {
        Map<String, Object> report = reportService.generateAccountSummaryReport();
        return ResponseEntity.ok(report);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> getTransactionReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        Map<String, Object> report = reportService.generateTransactionReport(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/cards/utilization")
    public ResponseEntity<Map<String, Object>> getCardUtilizationReport() {
        Map<String, Object> report = reportService.generateCardUtilizationReport();
        return ResponseEntity.ok(report);
    }

    @GetMapping("/statements/{accountId}")
    public ResponseEntity<Map<String, Object>> getMonthlyStatement(@PathVariable Long accountId) {
        Map<String, Object> statement = reportService.generateMonthlyStatementData(accountId);
        return ResponseEntity.ok(statement);
    }
}
