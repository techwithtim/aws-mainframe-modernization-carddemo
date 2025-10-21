package com.aws.carddemo.service;

import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.Card;
import com.aws.carddemo.model.CardXref;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardRepository;
import com.aws.carddemo.repository.CardXrefRepository;
import com.aws.carddemo.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BatchTransactionService {

    private static final Logger log = LoggerFactory.getLogger(BatchTransactionService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardXrefRepository cardXrefRepository;

    public void postDailyTransactions() {
        log.info("Starting daily transaction posting");
        
        List<Transaction> transactions = transactionRepository.findAll();
        int processedCount = 0;
        int errorCount = 0;
        
        for (Transaction transaction : transactions) {
            try {
                if (validateAndPostTransaction(transaction)) {
                    processedCount++;
                } else {
                    errorCount++;
                }
            } catch (Exception e) {
                log.error("Error processing transaction {}: {}", transaction.getTransactionId(), e.getMessage());
                errorCount++;
            }
        }
        
        log.info("Daily transaction posting completed. Processed: {}, Errors: {}", processedCount, errorCount);
    }

    private boolean validateAndPostTransaction(Transaction transaction) {
        Optional<CardXref> xrefOpt = cardXrefRepository.findByCardNum(transaction.getCardNumber());
        if (xrefOpt.isEmpty()) {
            log.warn("Card {} not found in cross-reference", transaction.getCardNumber());
            return false;
        }

        CardXref xref = xrefOpt.get();
        Optional<Account> accountOpt = accountRepository.findById(xref.getAcctId());
        if (accountOpt.isEmpty()) {
            log.warn("Account {} not found", xref.getAcctId());
            return false;
        }

        Account account = accountOpt.get();
        
        if (!"Y".equals(account.getActiveStatus())) {
            log.warn("Account {} is not active", account.getAccountId());
            return false;
        }

        BigDecimal amount = transaction.getAmount();
        if ("01".equals(transaction.getTransactionTypeCode())) {
            BigDecimal newBalance = account.getCurrentBalance().add(amount);
            if (newBalance.compareTo(account.getCreditLimit()) > 0) {
                log.warn("Transaction would exceed credit limit for account {}", account.getAccountId());
                return false;
            }
            account.setCurrentBalance(newBalance);
            account.setCurrentCycleDebit(account.getCurrentCycleDebit().add(amount));
        } else if ("02".equals(transaction.getTransactionTypeCode())) {
            account.setCurrentBalance(account.getCurrentBalance().subtract(amount));
            account.setCurrentCycleCredit(account.getCurrentCycleCredit().add(amount));
        }

        accountRepository.save(account);
        log.debug("Posted transaction {} to account {}", transaction.getTransactionId(), account.getAccountId());
        return true;
    }

    public void purgeOldTransactions(int daysToKeep) {
        log.info("Purging transactions older than {} days", daysToKeep);
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        List<Transaction> allTransactions = transactionRepository.findAll();
        int purgedCount = 0;
        
        for (Transaction transaction : allTransactions) {
            if (transaction.getProcessTimestamp() != null && 
                transaction.getProcessTimestamp().isBefore(cutoffDate)) {
                transactionRepository.delete(transaction);
                purgedCount++;
            }
        }
        
        log.info("Purged {} old transactions", purgedCount);
    }

    public void generateTransactionSummary() {
        log.info("Generating transaction summary");
        
        List<Transaction> transactions = transactionRepository.findAll();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        int debitCount = 0;
        int creditCount = 0;
        
        for (Transaction transaction : transactions) {
            if ("01".equals(transaction.getTransactionTypeCode())) {
                totalDebits = totalDebits.add(transaction.getAmount());
                debitCount++;
            } else if ("02".equals(transaction.getTransactionTypeCode())) {
                totalCredits = totalCredits.add(transaction.getAmount());
                creditCount++;
            }
        }
        
        log.info("Transaction Summary - Debits: {} ({}), Credits: {} ({})", 
                 debitCount, totalDebits, creditCount, totalCredits);
    }
}
