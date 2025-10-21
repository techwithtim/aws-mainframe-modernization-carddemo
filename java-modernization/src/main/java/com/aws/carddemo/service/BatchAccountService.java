package com.aws.carddemo.service;

import com.aws.carddemo.model.Account;
import com.aws.carddemo.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class BatchAccountService {

    private static final Logger log = LoggerFactory.getLogger(BatchAccountService.class);

    @Autowired
    private AccountRepository accountRepository;

    public void processAllAccounts() {
        log.info("Starting batch account processing");
        List<Account> accounts = accountRepository.findAll();
        
        for (Account account : accounts) {
            processAccount(account);
        }
        
        log.info("Completed batch account processing. Processed {} accounts", accounts.size());
    }

    private void processAccount(Account account) {
        log.debug("Processing account: {}", account.getAccountId());
        
        if (account.getCurrentCycleCredit() == null) {
            account.setCurrentCycleCredit(BigDecimal.ZERO);
        }
        if (account.getCurrentCycleDebit() == null) {
            account.setCurrentCycleDebit(BigDecimal.ZERO);
        }
        
        accountRepository.save(account);
    }

    public void calculateInterest() {
        log.info("Starting interest calculation");
        List<Account> accounts = accountRepository.findAll();
        
        for (Account account : accounts) {
            if ("Y".equals(account.getActiveStatus()) && 
                account.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0) {
                
                BigDecimal interestRate = new BigDecimal("0.0199");
                BigDecimal monthlyRate = interestRate.divide(new BigDecimal("12"), 4, BigDecimal.ROUND_HALF_UP);
                BigDecimal interest = account.getCurrentBalance().multiply(monthlyRate);
                
                account.setCurrentBalance(account.getCurrentBalance().add(interest));
                accountRepository.save(account);
                
                log.debug("Applied interest {} to account {}", interest, account.getAccountId());
            }
        }
        
        log.info("Completed interest calculation for {} accounts", accounts.size());
    }

    public void resetCycleCounters() {
        log.info("Resetting cycle counters");
        List<Account> accounts = accountRepository.findAll();
        
        for (Account account : accounts) {
            account.setCurrentCycleCredit(BigDecimal.ZERO);
            account.setCurrentCycleDebit(BigDecimal.ZERO);
            accountRepository.save(account);
        }
        
        log.info("Reset cycle counters for {} accounts", accounts.size());
    }

    public void closeExpiredAccounts() {
        log.info("Closing expired accounts");
        List<Account> accounts = accountRepository.findAll();
        LocalDate today = LocalDate.now();
        int closedCount = 0;
        
        for (Account account : accounts) {
            if (account.getExpirationDate() != null && 
                account.getExpirationDate().isBefore(today) &&
                "Y".equals(account.getActiveStatus())) {
                
                account.setActiveStatus("N");
                accountRepository.save(account);
                closedCount++;
                
                log.debug("Closed expired account: {}", account.getAccountId());
            }
        }
        
        log.info("Closed {} expired accounts", closedCount);
    }
}
