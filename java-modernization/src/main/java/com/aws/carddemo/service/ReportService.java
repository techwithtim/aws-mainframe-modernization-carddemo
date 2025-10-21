package com.aws.carddemo.service;

import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.Card;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardRepository;
import com.aws.carddemo.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public Map<String, Object> generateAccountSummaryReport() {
        log.info("Generating account summary report");
        
        List<Account> accounts = accountRepository.findAll();
        
        int totalAccounts = accounts.size();
        int activeAccounts = 0;
        int inactiveAccounts = 0;
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalCreditLimit = BigDecimal.ZERO;
        
        for (Account account : accounts) {
            if ("Y".equals(account.getActiveStatus())) {
                activeAccounts++;
            } else {
                inactiveAccounts++;
            }
            totalBalance = totalBalance.add(account.getCurrentBalance());
            totalCreditLimit = totalCreditLimit.add(account.getCreditLimit());
        }
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportName", "Account Summary Report");
        report.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        report.put("totalAccounts", totalAccounts);
        report.put("activeAccounts", activeAccounts);
        report.put("inactiveAccounts", inactiveAccounts);
        report.put("totalBalance", totalBalance);
        report.put("totalCreditLimit", totalCreditLimit);
        report.put("averageBalance", totalAccounts > 0 ? totalBalance.divide(new BigDecimal(totalAccounts), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO);
        
        log.info("Account summary report generated: {} total accounts", totalAccounts);
        return report;
    }

    public Map<String, Object> generateTransactionReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating transaction report from {} to {}", startDate, endDate);
        
        List<Transaction> allTransactions = transactionRepository.findAll();
        
        int totalTransactions = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<String, Integer> transactionsByType = new HashMap<>();
        Map<Integer, Integer> transactionsByCategory = new HashMap<>();
        
        for (Transaction transaction : allTransactions) {
            if (transaction.getProcessTimestamp() != null &&
                !transaction.getProcessTimestamp().isBefore(startDate) &&
                !transaction.getProcessTimestamp().isAfter(endDate)) {
                
                totalTransactions++;
                totalAmount = totalAmount.add(transaction.getAmount());
                
                String typeCode = transaction.getTransactionTypeCode();
                transactionsByType.put(typeCode, transactionsByType.getOrDefault(typeCode, 0) + 1);
                
                Integer categoryCode = transaction.getTransactionCategoryCode();
                transactionsByCategory.put(categoryCode, transactionsByCategory.getOrDefault(categoryCode, 0) + 1);
            }
        }
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportName", "Transaction Report");
        report.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        report.put("startDate", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        report.put("endDate", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        report.put("totalTransactions", totalTransactions);
        report.put("totalAmount", totalAmount);
        report.put("averageAmount", totalTransactions > 0 ? totalAmount.divide(new BigDecimal(totalTransactions), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO);
        report.put("transactionsByType", transactionsByType);
        report.put("transactionsByCategory", transactionsByCategory);
        
        log.info("Transaction report generated: {} transactions", totalTransactions);
        return report;
    }

    public Map<String, Object> generateCardUtilizationReport() {
        log.info("Generating card utilization report");
        
        List<Card> cards = cardRepository.findAll();
        List<Account> accounts = accountRepository.findAll();
        
        int totalCards = cards.size();
        int activeCards = 0;
        int inactiveCards = 0;
        
        for (Card card : cards) {
            if ("Y".equals(card.getActiveStatus())) {
                activeCards++;
            } else {
                inactiveCards++;
            }
        }
        
        BigDecimal totalUtilization = BigDecimal.ZERO;
        int accountsWithBalance = 0;
        
        for (Account account : accounts) {
            if (account.getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal utilization = account.getCurrentBalance()
                    .divide(account.getCreditLimit(), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
                totalUtilization = totalUtilization.add(utilization);
                accountsWithBalance++;
            }
        }
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportName", "Card Utilization Report");
        report.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        report.put("totalCards", totalCards);
        report.put("activeCards", activeCards);
        report.put("inactiveCards", inactiveCards);
        report.put("averageUtilization", accountsWithBalance > 0 ? 
            totalUtilization.divide(new BigDecimal(accountsWithBalance), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO);
        
        log.info("Card utilization report generated: {} cards", totalCards);
        return report;
    }

    public Map<String, Object> generateMonthlyStatementData(Long accountId) {
        log.info("Generating monthly statement for account {}", accountId);
        
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
        
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = LocalDateTime.now();
        
        List<Transaction> allTransactions = transactionRepository.findByCardNumber(
            cardRepository.findByAccountId(accountId).stream()
                .findFirst()
                .map(Card::getCardNumber)
                .orElse("")
        );
        
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        int transactionCount = 0;
        
        for (Transaction transaction : allTransactions) {
            if (transaction.getProcessTimestamp() != null &&
                !transaction.getProcessTimestamp().isBefore(startOfMonth) &&
                !transaction.getProcessTimestamp().isAfter(endOfMonth)) {
                
                transactionCount++;
                if ("01".equals(transaction.getTransactionTypeCode())) {
                    totalDebits = totalDebits.add(transaction.getAmount());
                } else if ("02".equals(transaction.getTransactionTypeCode())) {
                    totalCredits = totalCredits.add(transaction.getAmount());
                }
            }
        }
        
        Map<String, Object> statement = new HashMap<>();
        statement.put("statementName", "Monthly Statement");
        statement.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        statement.put("accountId", accountId);
        statement.put("statementPeriod", startOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE) + " to " + endOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE));
        statement.put("previousBalance", account.getCurrentBalance().subtract(totalDebits).add(totalCredits));
        statement.put("totalDebits", totalDebits);
        statement.put("totalCredits", totalCredits);
        statement.put("currentBalance", account.getCurrentBalance());
        statement.put("creditLimit", account.getCreditLimit());
        statement.put("availableCredit", account.getCreditLimit().subtract(account.getCurrentBalance()));
        statement.put("transactionCount", transactionCount);
        
        log.info("Monthly statement generated for account {}", accountId);
        return statement;
    }
}
