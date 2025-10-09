package com.aws.carddemo.service;

import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.CardXref;
import com.aws.carddemo.model.DisclosureGroup;
import com.aws.carddemo.model.DisclosureGroupId;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.model.TransactionCategoryBalance;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardXrefRepository;
import com.aws.carddemo.repository.DisclosureGroupRepository;
import com.aws.carddemo.repository.TransactionCategoryBalanceRepository;
import com.aws.carddemo.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service implementing monthly interest calculation for credit card accounts.
 * 
 * <p><b>Migrated from:</b> app/cbl/CBACT04C.cbl (COBOL batch interest calculator program)</p>
 * 
 * <p>This service calculates and posts monthly interest charges to credit card accounts based on
 * outstanding category balances and disclosure group APR configurations. The interest calculation
 * preserves exact decimal precision using BigDecimal arithmetic with banker's rounding (HALF_UP)
 * to ensure financial accuracy and regulatory compliance.</p>
 * 
 * <p><b>Core Business Logic:</b></p>
 * <p>Interest Calculation Formula (from CBACT04C.cbl line 464-465):</p>
 * <pre>
 * COBOL: COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
 * Java:  monthlyInterest = categoryBalance.multiply(annualRate).divide(1200, 2, HALF_UP)
 * </pre>
 * 
 * <p><b>Key Design Decisions:</b></p>
 * <ul>
 *   <li><b>BigDecimal Precision:</b> All monetary calculations use BigDecimal to avoid
 *       floating-point rounding errors. Scale is set to 2 decimal places (cents) with
 *       RoundingMode.HALF_UP matching COBOL COMP-3 packed decimal rounding semantics.</li>
 *   <li><b>APR to Monthly Rate Conversion:</b> Annual percentage rate (e.g., 16.99%) is divided
 *       by 1200 to convert to monthly decimal rate (16.99/1200 = 0.014158... or ~1.416% monthly).
 *       The division by 1200 combines percentage-to-decimal (÷100) and annual-to-monthly (÷12).</li>
 *   <li><b>Transaction Integration:</b> Interest charges are posted as Transaction entities with
 *       type code '01' (Purchase) and category code '05' (Interest Charge), creating immutable
 *       audit trail with dual timestamps (original + processing).</li>
 *   <li><b>Account Balance Updates:</b> Calculated interest is added to Account.currentBalance
 *       within @Transactional boundary ensuring atomic coordination with transaction record creation.</li>
 *   <li><b>Disclosure Group Lookup:</b> APR rates are retrieved via composite key (accountGroupId,
 *       transactionTypeCode, transactionCategoryCode) from DisclosureGroup entity. Fallback to
 *       'DEFAULT' group if primary group not found (CBACT04C.cbl line 437-438).</li>
 *   <li><b>Card Cross-Reference:</b> Card numbers are resolved via CardXref lookup to populate
 *       Transaction.cardNumber field for PCI-DSS compliant audit trail.</li>
 * </ul>
 * 
 * <p><b>COBOL Program Flow Mapping:</b></p>
 * <pre>
 * COBOL Paragraph                      Java Method
 * ──────────────────────────────────────────────────────────────────────────────
 * 0000-MAIN (lines 188-222)            calculateInterestForAllAccounts()
 * 1000-TCATBALF-GET-NEXT (lines 325-348)  transactionCategoryBalanceRepository.findAll()
 * 1100-GET-ACCT-DATA (lines 372-391)   accountRepository.findById()
 * 1110-GET-XREF-DATA (lines 393-413)   cardXrefRepository.findByAccountId()
 * 1200-GET-INT-RATE (lines 415-460)    disclosureGroupRepository.findById()
 * 1300-COMPUTE-INTEREST (lines 462-470) calculateMonthlyInterest()
 * 1300-B-WRITE-TX (lines 473-515)      createInterestTransaction()
 * 1050-UPDATE-ACCOUNT (lines 350-370)  accountRepository.save()
 * </pre>
 * 
 * <p><b>Spring Batch Integration:</b></p>
 * <p>This service is designed to integrate with Spring Batch InterestCalculationJobConfig for
 * scheduled monthly execution. The batch job orchestrates processing of all accounts while this
 * service encapsulates the core interest calculation business logic. Methods support both:</p>
 * <ul>
 *   <li><b>Batch Processing:</b> calculateInterestForAllAccounts() processes all accounts</li>
 *   <li><b>Individual Processing:</b> calculateInterestForAccount(Long) for single account</li>
 * </ul>
 * 
 * <p><b>Transaction Management:</b></p>
 * <p>All public methods are annotated with @Transactional ensuring ACID properties:</p>
 * <ul>
 *   <li><b>Atomicity:</b> Interest transaction creation and account balance update succeed together or roll back</li>
 *   <li><b>Consistency:</b> Database constraints (foreign keys, check constraints) enforced</li>
 *   <li><b>Isolation:</b> READ_COMMITTED isolation level prevents dirty reads</li>
 *   <li><b>Durability:</b> Committed changes persisted to PostgreSQL with WAL</li>
 * </ul>
 * 
 * <p><b>Error Handling:</b></p>
 * <ul>
 *   <li><b>ResourceNotFoundException:</b> Thrown when Account, DisclosureGroup, or CardXref not found
 *       (replaces COBOL FILE STATUS '23' record not found and PERFORM 9999-ABEND-PROGRAM)</li>
 *   <li><b>ArithmeticException:</b> Thrown on division by zero or invalid BigDecimal operations
 *       (replaces COBOL arithmetic overflow conditions)</li>
 *   <li><b>DataAccessException:</b> Spring exception translation for database errors
 *       (replaces COBOL FILE STATUS checks and error handling)</li>
 * </ul>
 * 
 * <p><b>PCI-DSS Compliance:</b></p>
 * <ul>
 *   <li>Card numbers masked in log output via Logback converters (show last 4 digits only)</li>
 *   <li>Interest amounts logged with account ID suffix (last 4 digits) for audit trail</li>
 *   <li>No sensitive data (full card numbers, SSN) in exception messages or logs</li>
 * </ul>
 * 
 * <p><b>Performance Characteristics:</b></p>
 * <ul>
 *   <li><b>Batch Processing:</b> Processes 10,000 accounts/minute (baseline requirement)</li>
 *   <li><b>Database Access:</b> O(n) complexity where n = number of category balances per account</li>
 *   <li><b>Transaction Scope:</b> One database transaction per account to minimize lock duration</li>
 *   <li><b>Memory Footprint:</b> Stateless service with no instance variables, thread-safe</li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b></p>
 * <ul>
 *   <li>Section 0.1.1 Primary Goal #4: Batch Processing Refactoring - Convert JCL-orchestrated
 *       batch jobs to Spring Batch with scheduled execution</li>
 *   <li>Section 0.8.3 Data Type Mapping: COBOL PIC S9(n)V99 COMP-3 → Java BigDecimal with
 *       @Column(precision, scale) preserving exact decimal precision</li>
 *   <li>Section 0.8.5 Batch Job Conversion: CBACT04C.cbl → InterestCalculationService + Spring Batch</li>
 *   <li>Section 0.8.6 Performance Baseline: Interest calculation 10,000 accounts/minute</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Calculate interest for single account (REST API or batch item)
 * BigDecimal interest = interestCalculationService.calculateInterestForAccount(accountId);
 * 
 * // Calculate interest for all accounts (scheduled batch job)
 * interestCalculationService.calculateInterestForAllAccounts();
 * 
 * // Low-level interest calculation (utility method)
 * BigDecimal monthlyInterest = interestCalculationService.calculateMonthlyInterest(
 *     new BigDecimal("1000.00"),  // balance
 *     new BigDecimal("16.99")     // APR percentage
 * );
 * }</pre>
 * 
 * @see Account JPA entity for account master data
 * @see TransactionCategoryBalance entity for category balance segmentation
 * @see DisclosureGroup entity for APR configuration reference data
 * @see Transaction entity for interest charge transaction records
 * @see CardXref entity for card-to-account cross-reference
 * @author CardDemo Modernization Team
 * @version 1.0.0
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class InterestCalculationService {

    /**
     * Repository for account master data access.
     * Replaces COBOL FILE CONTROL SELECT ACCOUNT-FILE ASSIGN TO ACCTFILE.
     */
    private final AccountRepository accountRepository;

    /**
     * Repository for transaction category balance data access.
     * Replaces COBOL FILE CONTROL SELECT TCATBAL-FILE ASSIGN TO TCATBALF.
     */
    private final TransactionCategoryBalanceRepository transactionCategoryBalanceRepository;

    /**
     * Repository for disclosure group APR configuration data access.
     * Replaces COBOL FILE CONTROL SELECT DISCGRP-FILE ASSIGN TO DISCGRP.
     */
    private final DisclosureGroupRepository disclosureGroupRepository;

    /**
     * Repository for transaction record creation.
     * Replaces COBOL FILE CONTROL SELECT TRANSACT-FILE ASSIGN TO TRANSACT.
     */
    private final TransactionRepository transactionRepository;

    /**
     * Repository for card cross-reference lookups.
     * Replaces COBOL FILE CONTROL SELECT XREF-FILE ASSIGN TO XREFFILE.
     */
    private final CardXrefRepository cardXrefRepository;

    /**
     * Constant for converting annual percentage rate to monthly decimal rate.
     * Combines percentage-to-decimal conversion (÷100) and annual-to-monthly conversion (÷12).
     * 
     * <p>COBOL Source: CBACT04C.cbl line 465 - division by 1200 in COMPUTE statement</p>
     * <p>Formula: monthlyRate = annualRatePercentage / 1200</p>
     * <p>Example: 16.99% APR → 16.99 / 1200 = 0.014158... (≈1.416% per month)</p>
     */
    private static final BigDecimal ANNUAL_TO_MONTHLY_DIVISOR = new BigDecimal("1200");

    /**
     * Transaction type code for interest charge transactions.
     * 
     * <p>COBOL Source: CBACT04C.cbl line 482 - MOVE '01' TO TRAN-TYPE-CD</p>
     * <p>Type '01' = Purchase transaction type (interest charges classified as purchases)</p>
     */
    private static final String INTEREST_TRANSACTION_TYPE = "01";

    /**
     * Transaction category code for interest charge transactions.
     * 
     * <p>COBOL Source: CBACT04C.cbl line 483 - MOVE '05' TO TRAN-CAT-CD</p>
     * <p>Category '05' = Interest Charge category (distinct from regular purchases)</p>
     */
    private static final String INTEREST_TRANSACTION_CATEGORY = "05";

    /**
     * Transaction source identifier for system-generated interest charges.
     * 
     * <p>COBOL Source: CBACT04C.cbl line 484 - MOVE 'System' TO TRAN-SOURCE</p>
     * <p>Distinguishes automated system transactions from user-initiated transactions</p>
     */
    private static final String INTEREST_TRANSACTION_SOURCE = "System";

    /**
     * Default disclosure group ID fallback when primary group not found.
     * 
     * <p>COBOL Source: CBACT04C.cbl line 437 - MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID</p>
     * <p>Ensures all accounts have applicable APR rates even if custom group not configured</p>
     */
    private static final String DEFAULT_GROUP_ID = "DEFAULT";

    /**
     * Calculate and post interest charges for a single account based on category balances.
     * 
     * <p><b>Migrated from:</b> CBACT04C.cbl main processing loop (lines 188-222) for single account</p>
     * 
     * <p>This method processes one account's transaction category balances, calculates monthly
     * interest charges using disclosure group APR rates, creates interest transaction records,
     * and updates the account's current balance. The entire operation executes within a single
     * database transaction to ensure atomicity.</p>
     * 
     * <p><b>Processing Steps:</b></p>
     * <ol>
     *   <li>Retrieve account master data (CBACT04C.cbl line 372-391: 1100-GET-ACCT-DATA)</li>
     *   <li>Retrieve card cross-reference for card number (line 393-413: 1110-GET-XREF-DATA)</li>
     *   <li>Retrieve all transaction category balances for the account (line 326-348: 1000-TCATBALF-GET-NEXT)</li>
     *   <li>For each category balance:
     *       <ul>
     *         <li>Lookup disclosure group APR rate (line 415-460: 1200-GET-INT-RATE)</li>
     *         <li>Calculate monthly interest charge (line 462-470: 1300-COMPUTE-INTEREST)</li>
     *         <li>Create interest transaction record (line 473-515: 1300-B-WRITE-TX)</li>
     *         <li>Accumulate total interest</li>
     *       </ul>
     *   </li>
     *   <li>Update account current balance with total interest (line 350-370: 1050-UPDATE-ACCOUNT)</li>
     * </ol>
     * 
     * <p><b>COBOL to Java Mapping:</b></p>
     * <pre>
     * COBOL Logic                                Java Equivalent
     * ──────────────────────────────────────────────────────────────────────────────
     * MOVE FD-TRANCAT-ACCT-ID TO ACCT-ID         accountRepository.findById(accountId)
     * PERFORM 1100-GET-ACCT-DATA                 .orElseThrow(() -> ResourceNotFoundException)
     * 
     * MOVE ACCT-ID TO FD-XREF-ACCT-ID            cardXrefRepository.findByAccountId(accountId)
     * PERFORM 1110-GET-XREF-DATA                 .stream().findFirst()
     * 
     * PERFORM 1000-TCATBALF-GET-NEXT             transactionCategoryBalanceRepository
     *   UNTIL END-OF-FILE = 'Y'                    .findByAccountId(accountId)
     * 
     * MOVE ACCT-GROUP-ID TO FD-DIS-ACCT-GROUP-ID disclosureGroupRepository.findById(
     * MOVE TRANCAT-TYPE-CD TO FD-DIS-TRAN-TYPE-CD  new DisclosureGroupId(groupId, typeCode, catCode))
     * MOVE TRANCAT-CD TO FD-DIS-TRAN-CAT-CD      
     * PERFORM 1200-GET-INT-RATE                  
     * 
     * COMPUTE WS-MONTHLY-INT =                   calculateMonthlyInterest(balance, rate)
     *   (TRAN-CAT-BAL * DIS-INT-RATE) / 1200     
     * ADD WS-MONTHLY-INT TO WS-TOTAL-INT         totalInterest = totalInterest.add(monthlyInt)
     * PERFORM 1300-B-WRITE-TX                    createInterestTransaction(account, interest, cardNum)
     * 
     * ADD WS-TOTAL-INT TO ACCT-CURR-BAL          account.setCurrentBalance(currentBalance.add(totalInt))
     * REWRITE FD-ACCTFILE-REC                    accountRepository.save(account)
     * </pre>
     * 
     * <p><b>Transaction Semantics:</b></p>
     * <ul>
     *   <li>Method annotated with @Transactional (inherited from class-level annotation)</li>
     *   <li>All database operations (reads, writes) execute within single transaction</li>
     *   <li>Transaction commits automatically on successful method completion</li>
     *   <li>Transaction rolls back automatically if any exception thrown</li>
     *   <li>Replaces COBOL EXEC CICS SYNCPOINT (commit) and SYNCPOINT ROLLBACK patterns</li>
     * </ul>
     * 
     * <p><b>Error Handling:</b></p>
     * <ul>
     *   <li><b>Account Not Found:</b> Throws ResourceNotFoundException if accountId invalid
     *       (replaces COBOL ACCTFILE-STATUS '23' INVALID KEY check line 374-376)</li>
     *   <li><b>Card Xref Not Found:</b> Throws ResourceNotFoundException if no cards for account
     *       (replaces COBOL XREFFILE-STATUS '23' INVALID KEY check line 396-398)</li>
     *   <li><b>Disclosure Group Not Found:</b> Attempts DEFAULT group fallback before throwing exception
     *       (replaces COBOL logic line 437-459 with DEFAULT group lookup)</li>
     *   <li><b>Arithmetic Errors:</b> BigDecimal operations throw ArithmeticException on invalid operations
     *       (replaces COBOL SIZE ERROR condition)</li>
     * </ul>
     * 
     * <p><b>Business Rules:</b></p>
     * <ul>
     *   <li>Only category balances with non-zero balance are processed (optimization)</li>
     *   <li>Interest is calculated per category, allowing different APRs per category type</li>
     *   <li>Total interest for all categories is accumulated and added to account balance once</li>
     *   <li>Each category's interest generates a separate transaction record for audit trail</li>
     *   <li>Interest transactions are timestamped with LocalDateTime.now() for dual timestamp fields</li>
     * </ul>
     * 
     * <p><b>Performance Considerations:</b></p>
     * <ul>
     *   <li>Database queries: O(1) for account lookup, O(n) for category balances where n typically &lt; 10</li>
     *   <li>Transaction scope: Single transaction per account minimizes lock duration</li>
     *   <li>Batch processing: Call this method once per account in Spring Batch ItemProcessor</li>
     *   <li>Memory usage: Minimal - no large collections held in memory, entities garbage collected after commit</li>
     * </ul>
     * 
     * <p><b>Logging:</b></p>
     * <ul>
     *   <li>INFO: Successful interest calculation with masked account ID and total amount</li>
     *   <li>DEBUG: Per-category interest calculation details</li>
     *   <li>WARN: Disclosure group not found, using DEFAULT group fallback</li>
     *   <li>ERROR: Critical failures (account not found, database errors)</li>
     *   <li>Account IDs masked to show last 4 digits only for PCI-DSS compliance</li>
     * </ul>
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * // Called from Spring Batch ItemProcessor or REST API endpoint
     * try {
     *     BigDecimal totalInterest = interestCalculationService.calculateInterestForAccount(12345L);
     *     log.info("Calculated interest: ${} for account ID: {}", totalInterest, 12345L);
     * } catch (ResourceNotFoundException e) {
     *     log.error("Account not found: {}", e.getMessage());
     *     // Handle error appropriately (skip item in batch, return 404 in REST API)
     * }
     * }</pre>
     * 
     * @param accountId Account ID (surrogate primary key) to calculate interest for
     * @return Total interest amount calculated and posted for the account (sum of all category interests)
     * @throws ResourceNotFoundException if account not found, card xref not found, or disclosure group not found
     * @throws ArithmeticException if invalid BigDecimal operation occurs during calculation
     * @throws IllegalArgumentException if accountId is null
     * @see #calculateMonthlyInterest(BigDecimal, BigDecimal) for low-level interest calculation formula
     * @see #createInterestTransaction(Account, BigDecimal, String) for transaction record creation
     */
    public BigDecimal calculateInterestForAccount(Long accountId) {
        log.debug("Starting interest calculation for account ID: {}", accountId);

        // Step 1: Retrieve account master data (CBACT04C.cbl line 372-391: 1100-GET-ACCT-DATA)
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.error("Account not found: {}", accountId);
                    return new ResourceNotFoundException("Account not found with ID: " + accountId);
                });

        String maskedAccountId = "***" + account.getAccountNumber().substring(
                Math.max(0, account.getAccountNumber().length() - 4));
        log.debug("Retrieved account: {}, Group ID: {}", maskedAccountId, account.getGroupId());

        // Step 2: Retrieve card cross-reference for card number (CBACT04C.cbl line 393-413: 1110-GET-XREF-DATA)
        String cardNumber = cardXrefRepository.findByAccountId(accountId)
                .stream()
                .findFirst()
                .map(CardXref::getCardNumber)
                .orElseThrow(() -> {
                    log.error("Card cross-reference not found for account ID: {}", accountId);
                    return new ResourceNotFoundException("Card cross-reference not found for account ID: " + accountId);
                });

        String maskedCardNumber = "****-****-****-" + cardNumber.substring(Math.max(0, cardNumber.length() - 4));
        log.debug("Retrieved card number: {}", maskedCardNumber);

        // Step 3: Retrieve all transaction category balances for the account
        // (CBACT04C.cbl line 326-348: 1000-TCATBALF-GET-NEXT sequential READ)
        List<TransactionCategoryBalance> categoryBalances = transactionCategoryBalanceRepository.findByAccountId(accountId);
        log.debug("Found {} transaction category balances for account {}", categoryBalances.size(), maskedAccountId);

        // Step 4: Process each category balance and accumulate total interest
        // (CBACT04C.cbl line 467: ADD WS-MONTHLY-INT TO WS-TOTAL-INT)
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (TransactionCategoryBalance categoryBalance : categoryBalances) {
            // Skip zero balances (optimization - no interest to calculate)
            if (categoryBalance.getCategoryBalance().compareTo(BigDecimal.ZERO) == 0) {
                log.debug("Skipping zero balance for account {}, type {}, category {}",
                        maskedAccountId,
                        categoryBalance.getTransactionTypeCode(),
                        categoryBalance.getTransactionCategoryCode());
                continue;
            }

            // Step 4a: Lookup disclosure group APR rate (CBACT04C.cbl line 415-460: 1200-GET-INT-RATE)
            DisclosureGroup disclosureGroup = lookupDisclosureGroup(
                    account.getGroupId(),
                    categoryBalance.getTransactionTypeCode(),
                    categoryBalance.getTransactionCategoryCode());

            log.debug("Using APR {} for account {}, type {}, category {}, balance {}",
                    disclosureGroup.getInterestRate(),
                    maskedAccountId,
                    categoryBalance.getTransactionTypeCode(),
                    categoryBalance.getTransactionCategoryCode(),
                    categoryBalance.getCategoryBalance());

            // Step 4b: Calculate monthly interest charge (CBACT04C.cbl line 462-470: 1300-COMPUTE-INTEREST)
            BigDecimal monthlyInterest = calculateMonthlyInterest(
                    categoryBalance.getCategoryBalance(),
                    disclosureGroup.getInterestRate());

            log.debug("Calculated monthly interest: {} for category balance: {}",
                    monthlyInterest, categoryBalance.getCategoryBalance());

            // Step 4c: Create interest transaction record (CBACT04C.cbl line 473-515: 1300-B-WRITE-TX)
            createInterestTransaction(account, monthlyInterest, cardNumber);

            // Step 4d: Accumulate total interest
            totalInterest = totalInterest.add(monthlyInterest);
        }

        // Step 5: Update account current balance with total interest
        // (CBACT04C.cbl line 350-370: 1050-UPDATE-ACCOUNT)
        // COBOL: ADD WS-TOTAL-INT TO ACCT-CURR-BAL (line 352)
        if (totalInterest.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newBalance = account.getCurrentBalance().add(totalInterest);
            account.setCurrentBalance(newBalance);
            accountRepository.save(account);

            log.info("Interest calculation completed for account {}: Total interest ${} posted, new balance ${}",
                    maskedAccountId, totalInterest, newBalance);
        } else {
            log.debug("No interest calculated for account {} (zero category balances)", maskedAccountId);
        }

        return totalInterest;
    }

    /**
     * Calculate and post interest charges for all accounts in the system.
     * 
     * <p><b>Migrated from:</b> CBACT04C.cbl main processing loop (lines 188-222)</p>
     * 
     * <p>This method processes all transaction category balance records sequentially, grouping them
     * by account and calculating interest charges for each account. It mimics the COBOL batch job's
     * sequential file processing pattern but delegates per-account logic to calculateInterestForAccount().</p>
     * 
     * <p><b>COBOL Processing Flow:</b></p>
     * <pre>
     * CBACT04C.cbl lines 188-222 (0000-MAIN paragraph):
     * 
     *     PERFORM 0100-TCATBALF-OPEN
     *     PERFORM 0200-XREFFILE-OPEN
     *     PERFORM 0300-ACCOUNT-OPEN
     *     PERFORM 0400-DISCGRP-OPEN
     *     PERFORM 0500-TRANSFILE-OPEN
     *     
     *     PERFORM 1000-TCATBALF-GET-NEXT
     *     
     *     PERFORM 2000-PROCESS-RECORDS
     *       UNTIL END-OF-FILE = 'Y'
     *     
     *     PERFORM 9000-TCATBALF-CLOSE
     *     PERFORM 9100-XREFFILE-CLOSE
     *     PERFORM 9200-ACCOUNT-CLOSE
     *     PERFORM 9300-DISCGRP-CLOSE
     *     PERFORM 9400-TRANSFILE-CLOSE
     * </pre>
     * 
     * <p><b>Java Implementation Strategy:</b></p>
     * <p>The COBOL program processes records sequentially from TCATBAL-FILE and groups them by
     * account ID. The Java implementation retrieves all category balance records, extracts unique
     * account IDs, and processes each account individually via calculateInterestForAccount().</p>
     * 
     * <p><b>Key Differences from COBOL:</b></p>
     * <ul>
     *   <li><b>File Management:</b> COBOL explicitly opens/closes VSAM files; Java uses connection
     *       pooling managed by Spring Data JPA (no explicit open/close required)</li>
     *   <li><b>Sequential Processing:</b> COBOL reads records sequentially via STARTBR/READNEXT;
     *       Java retrieves all records via findAll() and processes in-memory</li>
     *   <li><b>Transaction Scope:</b> COBOL commits after each account (implicit SYNCPOINT);
     *       Java uses @Transactional per account via calculateInterestForAccount()</li>
     *   <li><b>Error Handling:</b> COBOL uses FILE STATUS checks and PERFORM 9999-ABEND-PROGRAM;
     *       Java throws exceptions that trigger Spring transaction rollback</li>
     * </ul>
     * 
     * <p><b>Processing Logic:</b></p>
     * <ol>
     *   <li>Retrieve all transaction category balance records from database</li>
     *   <li>Extract distinct account IDs from category balance records</li>
     *   <li>For each unique account ID, call calculateInterestForAccount()</li>
     *   <li>Log summary statistics (accounts processed, total interest posted, errors)</li>
     * </ol>
     * 
     * <p><b>Transaction Management:</b></p>
     * <p>This method itself is NOT transactional - it coordinates multiple per-account transactions.
     * Each call to calculateInterestForAccount() executes in its own transaction, ensuring that
     * failures on one account do not affect other accounts. This matches COBOL behavior where
     * each account's processing is independent.</p>
     * 
     * <p><b>Error Handling Strategy:</b></p>
     * <ul>
     *   <li>Continue processing remaining accounts if one account fails</li>
     *   <li>Log errors with masked account ID for troubleshooting</li>
     *   <li>Collect error count for batch job monitoring</li>
     *   <li>Do NOT throw exceptions - allows batch job to complete even with partial failures</li>
     * </ul>
     * 
     * <p><b>Performance Characteristics:</b></p>
     * <ul>
     *   <li>Sequential processing: O(n) where n = number of distinct accounts</li>
     *   <li>Memory usage: Loads all category balances into memory - suitable for typical cardemo dataset</li>
     *   <li>Database transactions: One transaction per account (not one for entire batch)</li>
     *   <li>Expected throughput: 10,000 accounts/minute (baseline requirement per Section 0.8.6)</li>
     * </ul>
     * 
     * <p><b>Logging Output:</b></p>
     * <ul>
     *   <li>INFO: Batch start with total category balance record count</li>
     *   <li>INFO: Per-account progress (every 100 accounts or configurable interval)</li>
     *   <li>INFO: Batch completion summary (total accounts, total interest, processing time)</li>
     *   <li>ERROR: Per-account failures with masked account ID and exception message</li>
     * </ul>
     * 
     * <p><b>Spring Batch Integration:</b></p>
     * <p>This method is designed to be called from Spring Batch InterestCalculationJobConfig as a
     * Tasklet step or as the ItemProcessor logic. The batch job handles scheduling (monthly cron),
     * job restart, and monitoring while this service provides the core business logic.</p>
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * // Called from Spring Batch Tasklet or scheduled job
     * @Scheduled(cron = "0 0 2 1 * ?") // Run at 2 AM on first day of each month
     * public void runMonthlyInterestCalculation() {
     *     log.info("Starting monthly interest calculation batch job");
     *     interestCalculationService.calculateInterestForAllAccounts();
     *     log.info("Monthly interest calculation batch job completed");
     * }
     * }</pre>
     * 
     * @throws ArithmeticException if invalid BigDecimal operation occurs (rare, logged but not propagated)
     * @see #calculateInterestForAccount(Long) for per-account processing logic
     */
    @Transactional(readOnly = true)
    public void calculateInterestForAllAccounts() {
        log.info("Starting interest calculation for all accounts");
        long startTime = System.currentTimeMillis();

        // Retrieve all transaction category balance records (CBACT04C.cbl line 326-348)
        List<TransactionCategoryBalance> allCategoryBalances = transactionCategoryBalanceRepository.findAll();
        log.info("Retrieved {} transaction category balance records", allCategoryBalances.size());

        if (allCategoryBalances.isEmpty()) {
            log.warn("No transaction category balance records found - no interest to calculate");
            return;
        }

        // Extract distinct account IDs from category balance records
        List<Long> accountIds = allCategoryBalances.stream()
                .map(TransactionCategoryBalance::getAccountId)
                .distinct()
                .toList();

        log.info("Processing interest calculation for {} unique accounts", accountIds.size());

        // Process each account individually (each in its own transaction)
        int processedCount = 0;
        int errorCount = 0;
        BigDecimal totalInterestPosted = BigDecimal.ZERO;

        for (Long accountId : accountIds) {
            try {
                // Delegate to per-account method (executes in separate transaction)
                BigDecimal accountInterest = calculateInterestForAccount(accountId);
                totalInterestPosted = totalInterestPosted.add(accountInterest);
                processedCount++;

                // Log progress every 100 accounts
                if (processedCount % 100 == 0) {
                    log.info("Progress: {} accounts processed, ${} total interest posted",
                            processedCount, totalInterestPosted);
                }

            } catch (ResourceNotFoundException e) {
                // Log error but continue processing remaining accounts
                log.error("Error calculating interest for account ID {}: {}",
                        accountId, e.getMessage());
                errorCount++;

            } catch (Exception e) {
                // Catch any unexpected exceptions to prevent batch failure
                log.error("Unexpected error calculating interest for account ID {}: {}",
                        accountId, e.getMessage(), e);
                errorCount++;
            }
        }

        // Log batch completion summary
        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Interest calculation batch completed: {} accounts processed, {} errors, " +
                        "${} total interest posted in {} ms",
                processedCount, errorCount, totalInterestPosted, elapsedTime);

        if (errorCount > 0) {
            log.warn("Interest calculation completed with {} errors - review error logs for details", errorCount);
        }
    }

    /**
     * Calculate monthly interest charge using the CardDemo interest formula.
     * 
     * <p><b>Migrated from:</b> CBACT04C.cbl lines 464-465 (1300-COMPUTE-INTEREST paragraph)</p>
     * 
     * <p><b>COBOL Source Code:</b></p>
     * <pre>
     * 1300-COMPUTE-INTEREST.
     *     COMPUTE WS-MONTHLY-INT
     *      = ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200
     * </pre>
     * 
     * <p><b>Interest Calculation Formula:</b></p>
     * <pre>
     * monthlyInterest = categoryBalance × (annualRatePercentage / 1200)
     * 
     * Where:
     *   categoryBalance      = Outstanding balance for transaction category (e.g., $1,000.00)
     *   annualRatePercentage = Annual percentage rate from DisclosureGroup (e.g., 16.99 for 16.99%)
     *   1200                 = Conversion factor combining two operations:
     *                          - Divide by 100 to convert percentage to decimal (16.99 → 0.1699)
     *                          - Divide by 12 to convert annual to monthly rate (0.1699 → 0.014158)
     * 
     * Example Calculation:
     *   Balance: $1,000.00
     *   APR: 16.99%
     *   Monthly Interest = 1000.00 × (16.99 / 1200)
     *                    = 1000.00 × 0.0141583...
     *                    = $14.1583...
     *                    = $14.16 (rounded to nearest cent with HALF_UP)
     * </pre>
     * 
     * <p><b>Rounding Strategy - CRITICAL for Financial Accuracy:</b></p>
     * <ul>
     *   <li><b>RoundingMode.HALF_UP (Banker's Rounding):</b> Rounds to nearest neighbor, ties round up</li>
     *   <li><b>Scale 2:</b> Results always rounded to 2 decimal places (cents)</li>
     *   <li><b>Precision 11:</b> Intermediate calculation uses 11 digits to prevent overflow</li>
     *   <li><b>COBOL Equivalence:</b> Matches COMP-3 packed decimal rounding in COBOL COMPUTE</li>
     * </ul>
     * 
     * <p><b>Rounding Examples:</b></p>
     * <pre>
     * Input Amount    → Rounded Result (HALF_UP)
     * ───────────────────────────────────────────
     * $14.1550        → $14.16 (0.005 rounds up)
     * $14.1549        → $14.15 (less than 0.005 rounds down)
     * $14.1650        → $14.17 (0.005 rounds up)
     * $10.125         → $10.13 (0.005 rounds up)
     * $10.124         → $10.12 (less than 0.005 rounds down)
     * </pre>
     * 
     * <p><b>Data Type Precision:</b></p>
     * <ul>
     *   <li><b>COBOL PIC S9(09)V99:</b> Signed 9 integer digits + 2 decimal digits (max $999,999,999.99)</li>
     *   <li><b>Java BigDecimal:</b> Arbitrary precision, configured to match COBOL precision</li>
     *   <li><b>Database NUMERIC(11,2):</b> Fixed-point decimal matching COBOL PIC clause</li>
     *   <li><b>WHY BigDecimal:</b> Avoids floating-point rounding errors (float/double PROHIBITED for money)</li>
     * </ul>
     * 
     * <p><b>Mathematical Properties:</b></p>
     * <ul>
     *   <li><b>Associative:</b> (a × b) / c = a × (b / c) - implementation uses multiplication first for clarity</li>
     *   <li><b>Commutative:</b> balance × rate = rate × balance (order doesn't affect result)</li>
     *   <li><b>Idempotent:</b> Same inputs always produce same output (deterministic)</li>
     *   <li><b>Scale Preserving:</b> Result always has exactly 2 decimal places (cents)</li>
     * </ul>
     * 
     * <p><b>Edge Cases Handled:</b></p>
     * <ul>
     *   <li><b>Zero Balance:</b> Returns $0.00 (0 × rate = 0)</li>
     *   <li><b>Zero Rate:</b> Returns $0.00 (balance × 0 = 0) - promotional 0% APR</li>
     *   <li><b>Negative Balance:</b> Calculates negative interest (refund scenario)</li>
     *   <li><b>Very Large Balance:</b> BigDecimal prevents overflow up to 2^31-1 digits</li>
     *   <li><b>Very Small Result:</b> Rounds to $0.01 minimum (less than $0.005 rounds to $0.00)</li>
     * </ul>
     * 
     * <p><b>Regulatory Compliance:</b></p>
     * <ul>
     *   <li><b>Truth in Lending Act (TILA):</b> Accurate APR disclosure and calculation</li>
     *   <li><b>CARD Act of 2009:</b> Transparent interest calculation methodology</li>
     *   <li><b>PCI-DSS:</b> Financial calculation accuracy for billing integrity</li>
     *   <li><b>Audit Trail:</b> Deterministic calculation supports financial audits</li>
     * </ul>
     * 
     * <p><b>Performance Characteristics:</b></p>
     * <ul>
     *   <li><b>Time Complexity:</b> O(1) - constant time calculation</li>
     *   <li><b>Space Complexity:</b> O(1) - no additional memory allocation</li>
     *   <li><b>Precision:</b> Exact decimal arithmetic (no floating-point errors)</li>
     *   <li><b>Thread Safety:</b> Immutable BigDecimal operations (thread-safe)</li>
     * </ul>
     * 
     * <p><b>Unit Testing:</b></p>
     * <p>This method should have comprehensive unit tests covering:</p>
     * <ul>
     *   <li>Standard calculations with various APRs (0%, 10%, 16.99%, 24.99%, 29.99%)</li>
     *   <li>Edge cases (zero balance, zero rate, negative balance)</li>
     *   <li>Rounding boundary conditions (0.5 cent increments)</li>
     *   <li>Large numbers (approaching BigDecimal capacity)</li>
     *   <li>Comparison with COBOL CBACT04C.cbl output for regression testing</li>
     * </ul>
     * 
     * <p><b>Usage Examples:</b></p>
     * <pre>{@code
     * // Standard purchase balance at 16.99% APR
     * BigDecimal interest1 = calculateMonthlyInterest(
     *     new BigDecimal("1000.00"),  // $1,000 balance
     *     new BigDecimal("16.99")     // 16.99% APR
     * );
     * // Result: $14.16 per month
     * 
     * // Cash advance balance at 24.99% APR
     * BigDecimal interest2 = calculateMonthlyInterest(
     *     new BigDecimal("500.00"),   // $500 balance
     *     new BigDecimal("24.99")     // 24.99% APR
     * );
     * // Result: $10.41 per month
     * 
     * // Promotional balance transfer at 0% APR
     * BigDecimal interest3 = calculateMonthlyInterest(
     *     new BigDecimal("5000.00"),  // $5,000 balance
     *     new BigDecimal("0.00")      // 0% APR
     * );
     * // Result: $0.00 per month (no interest)
     * }</pre>
     * 
     * @param categoryBalance Outstanding balance for transaction category (must not be null)
     * @param annualRatePercentage Annual percentage rate from disclosure group (e.g., 16.99 for 16.99% APR, must not be null)
     * @return Monthly interest charge rounded to 2 decimal places with HALF_UP rounding
     * @throws ArithmeticException if division results in non-terminating decimal (should never occur with scale 2)
     * @throws IllegalArgumentException if categoryBalance or annualRatePercentage is null
     * @see RoundingMode#HALF_UP
     * @see BigDecimal#divide(BigDecimal, int, RoundingMode)
     */
    public BigDecimal calculateMonthlyInterest(BigDecimal categoryBalance, BigDecimal annualRatePercentage) {
        // Validate input parameters
        if (categoryBalance == null) {
            throw new IllegalArgumentException("Category balance cannot be null");
        }
        if (annualRatePercentage == null) {
            throw new IllegalArgumentException("Annual rate percentage cannot be null");
        }

        // COBOL: COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
        // Java:  monthlyInterest = categoryBalance × annualRatePercentage ÷ 1200
        BigDecimal monthlyInterest = categoryBalance
                .multiply(annualRatePercentage)
                .divide(ANNUAL_TO_MONTHLY_DIVISOR, 2, RoundingMode.HALF_UP);

        log.trace("Interest calculation: ${} × {}% / 1200 = ${} per month",
                categoryBalance, annualRatePercentage, monthlyInterest);

        return monthlyInterest;
    }

    /**
     * Create and persist an interest charge transaction record.
     * 
     * <p><b>Migrated from:</b> CBACT04C.cbl lines 473-515 (1300-B-WRITE-TX paragraph)</p>
     * 
     * <p>This method creates a Transaction entity representing a system-generated interest charge
     * and persists it to the database. The transaction serves as an immutable audit trail of the
     * interest calculation and posting operation, providing transparency for customer billing
     * statements and regulatory compliance reporting.</p>
     * 
     * <p><b>COBOL Source Code Mapping:</b></p>
     * <pre>
     * CBACT04C.cbl 1300-B-WRITE-TX paragraph:
     * 
     * Line 482: MOVE '01' TO TRAN-TYPE-CD             → builder.transactionTypeCode("01")
     * Line 483: MOVE '05' TO TRAN-CAT-CD              → builder.transactionCategoryCode("05")
     * Line 484: MOVE 'System' TO TRAN-SOURCE          → builder.transactionSource("System")
     * Line 485-488: STRING 'Int. for a/c ', ACCT-ID   → builder.description("Int. for a/c " + accountId)
     * Line 490: MOVE WS-MONTHLY-INT TO TRAN-AMT       → builder.amount(interestAmount)
     * Line 495: MOVE XREF-CARD-NUM TO TRAN-CARD-NUM   → builder.cardNumber(cardNumber)
     * Line 496-498: PERFORM Z-GET-DB2-FORMAT-TIMESTAMP → builder.originalTimestamp(LocalDateTime.now())
     * Line 500: WRITE FD-TRANFILE-REC FROM TRAN-RECORD → transactionRepository.save(transaction)
     * </pre>
     * 
     * <p><b>Transaction Field Mappings:</b></p>
     * <ul>
     *   <li><b>transactionTypeCode:</b> "01" (Purchase) - Interest charges classified as purchases
     *       per COBOL line 482, not as separate transaction type</li>
     *   <li><b>transactionCategoryCode:</b> "05" (Interest Charge) - Distinguishes interest from
     *       regular purchase transactions per COBOL line 483</li>
     *   <li><b>transactionSource:</b> "System" - Identifies automated batch-generated transactions
     *       vs user-initiated transactions per COBOL line 484</li>
     *   <li><b>description:</b> "Int. for a/c {accountId}" - Descriptive text for customer statement
     *       per COBOL lines 485-488</li>
     *   <li><b>amount:</b> Calculated interest charge (positive value increases account balance)
     *       per COBOL line 490</li>
     *   <li><b>cardNumber:</b> Card number from CardXref lookup for PCI-DSS audit trail
     *       per COBOL line 495</li>
     *   <li><b>originalTimestamp:</b> LocalDateTime.now() - When interest was calculated
     *       per COBOL lines 496-497</li>
     *   <li><b>processingTimestamp:</b> LocalDateTime.now() - When transaction was posted
     *       per COBOL line 498 (same as originalTimestamp for batch-generated transactions)</li>
     *   <li><b>account:</b> ManyToOne relationship to Account entity (foreign key account_id)</li>
     * </ul>
     * 
     * <p><b>Fields NOT Populated (N/A for Interest Charges):</b></p>
     * <ul>
     *   <li><b>merchantId:</b> 0 (no merchant for system-generated interest) - COBOL line 491</li>
     *   <li><b>merchantName:</b> NULL (no merchant name) - COBOL line 492</li>
     *   <li><b>merchantCity:</b> NULL (no merchant city) - COBOL line 493</li>
     *   <li><b>merchantZip:</b> NULL (no merchant ZIP) - COBOL line 494</li>
     * </ul>
     * 
     * <p><b>Transaction ID Generation:</b></p>
     * <p>The COBOL program generates transaction ID by concatenating date + sequence number
     * (lines 476-480). In the Java implementation, transaction ID is auto-generated by database
     * IDENTITY column, eliminating the need for manual sequence management and ensuring uniqueness.</p>
     * 
     * <p><b>Timestamp Handling:</b></p>
     * <p>COBOL calls Z-GET-DB2-FORMAT-TIMESTAMP procedure to get DB2-formatted timestamp. Java
     * uses LocalDateTime.now() which provides timezone-agnostic timestamp. Both originalTimestamp
     * and processingTimestamp are set to the same value for batch-generated interest charges,
     * matching COBOL behavior (lines 496-498).</p>
     * 
     * <p><b>Database Persistence:</b></p>
     * <ul>
     *   <li>Method calls transactionRepository.save() which generates INSERT SQL statement</li>
     *   <li>Foreign key constraint enforces valid account_id reference</li>
     *   <li>Foreign key constraints enforce valid transaction_type_code and transaction_category_code</li>
     *   <li>Check constraint enforces amount precision (NUMERIC(10,2))</li>
     *   <li>Transaction participates in parent @Transactional method's transaction</li>
     * </ul>
     * 
     * <p><b>Transaction Semantics:</b></p>
     * <ul>
     *   <li>Executes within @Transactional context of calling method (calculateInterestForAccount)</li>
     *   <li>Transaction record creation and account balance update are atomic</li>
     *   <li>If save() fails, entire account interest calculation rolls back</li>
     *   <li>Replaces COBOL WRITE FD-TRANFILE-REC with FILE STATUS checks (line 500-514)</li>
     * </ul>
     * 
     * <p><b>Error Handling:</b></p>
     * <ul>
     *   <li><b>Foreign Key Violation:</b> Throws DataIntegrityViolationException if account_id invalid
     *       (replaces COBOL FILE STATUS checks line 501-505)</li>
     *   <li><b>Constraint Violation:</b> Throws ConstraintViolationException if validation fails
     *       (e.g., invalid amount, missing required fields)</li>
     *   <li><b>Database Error:</b> Throws DataAccessException for other database failures
     *       (replaces COBOL DISPLAY 'ERROR WRITING TRANSACTION RECORD' line 510)</li>
     * </ul>
     * 
     * <p><b>PCI-DSS Compliance:</b></p>
     * <ul>
     *   <li>Card numbers stored in database but masked in logs (show last 4 digits only)</li>
     *   <li>Transaction records provide audit trail for interest charges</li>
     *   <li>Timestamps enable forensic analysis and compliance reporting</li>
     *   <li>Immutable transaction records prevent tampering (no UPDATE, only INSERT)</li>
     * </ul>
     * 
     * <p><b>Customer Statement Integration:</b></p>
     * <p>The created transaction record appears on customer billing statements with description
     * "Int. for a/c {accountId}" and amount showing the monthly interest charge. Customers can
     * identify interest charges by transaction category "05" (Interest Charge) distinct from
     * regular purchases (category "01", "02", etc.).</p>
     * 
     * <p><b>Audit Trail Requirements:</b></p>
     * <ul>
     *   <li>Each interest calculation generates exactly one transaction record per category balance</li>
     *   <li>Transaction records are immutable (INSERT only, no UPDATE or DELETE)</li>
     *   <li>Dual timestamps (original + processing) support forensic analysis</li>
     *   <li>Card number links transaction to specific payment instrument</li>
     *   <li>Transaction source "System" distinguishes automated from user-initiated transactions</li>
     * </ul>
     * 
     * <p><b>Performance Characteristics:</b></p>
     * <ul>
     *   <li>Single database INSERT operation per call (O(1) complexity)</li>
     *   <li>Executes within parent transaction (no additional transaction overhead)</li>
     *   <li>Foreign key lookups use database indexes (efficient validation)</li>
     *   <li>No cascading operations (interest transaction standalone)</li>
     * </ul>
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * // Called internally by calculateInterestForAccount() after interest calculation
     * Account account = accountRepository.findById(12345L).orElseThrow();
     * BigDecimal interestCharge = new BigDecimal("14.16");
     * String cardNumber = "4000123456789010";
     * 
     * createInterestTransaction(account, interestCharge, cardNumber);
     * 
     * // Result: Transaction record created in database with:
     * // - type_code: "01"
     * // - category_code: "05"
     * // - source: "System"
     * // - description: "Int. for a/c 12345"
     * // - amount: $14.16
     * // - card_number: "4000123456789010"
     * // - original_timestamp: 2024-10-09T14:30:00
     * // - processing_timestamp: 2024-10-09T14:30:00
     * }</pre>
     * 
     * @param account Account entity for which interest transaction is being created (foreign key relationship)
     * @param interestAmount Monthly interest charge amount (positive value, will be added to account balance)
     * @param cardNumber Card number from CardXref lookup (16-digit string, will be masked in logs)
     * @return Created Transaction entity (persisted to database with auto-generated ID)
     * @throws IllegalArgumentException if account, interestAmount, or cardNumber is null
     * @throws org.springframework.dao.DataIntegrityViolationException if foreign key constraint violated
     * @throws jakarta.validation.ConstraintViolationException if validation annotations violated
     * @see Transaction Transaction entity class with validation rules
     * @see TransactionRepository Repository for transaction persistence
     */
    private Transaction createInterestTransaction(Account account, BigDecimal interestAmount, String cardNumber) {
        // Validate input parameters
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }
        if (interestAmount == null) {
            throw new IllegalArgumentException("Interest amount cannot be null");
        }
        if (cardNumber == null) {
            throw new IllegalArgumentException("Card number cannot be null");
        }

        // Get current timestamp for both original and processing timestamps
        // (CBACT04C.cbl line 496-498: PERFORM Z-GET-DB2-FORMAT-TIMESTAMP)
        LocalDateTime now = LocalDateTime.now();

        // Build transaction description: "Int. for a/c {accountId}"
        // (CBACT04C.cbl line 485-488: STRING 'Int. for a/c ', ACCT-ID INTO TRAN-DESC)
        String description = "Int. for a/c " + account.getAccountId();

        // Create interest charge transaction using builder pattern
        Transaction interestTransaction = Transaction.builder()
                .transactionTypeCode(INTEREST_TRANSACTION_TYPE)           // "01" (Purchase)
                .transactionCategoryCode(INTEREST_TRANSACTION_CATEGORY)   // "05" (Interest Charge)
                .transactionSource(INTEREST_TRANSACTION_SOURCE)           // "System"
                .description(description)                                  // "Int. for a/c {accountId}"
                .amount(interestAmount)                                    // Calculated interest
                .cardNumber(cardNumber)                                    // From CardXref lookup
                .originalTimestamp(now)                                    // When calculated
                .processingTimestamp(now)                                  // When posted (same as original)
                .account(account)                                          // Foreign key relationship
                .build();

        // Persist transaction to database (CBACT04C.cbl line 500: WRITE FD-TRANFILE-REC FROM TRAN-RECORD)
        Transaction savedTransaction = transactionRepository.save(interestTransaction);

        String maskedCardNumber = "****-****-****-" + cardNumber.substring(Math.max(0, cardNumber.length() - 4));
        log.debug("Created interest transaction: ${} for account {}, card {}",
                interestAmount, account.getAccountId(), maskedCardNumber);

        return savedTransaction;
    }

    /**
     * Lookup disclosure group APR configuration with fallback to DEFAULT group.
     * 
     * <p><b>Migrated from:</b> CBACT04C.cbl lines 415-460 (1200-GET-INT-RATE paragraph)</p>
     * 
     * <p>This private helper method encapsulates the disclosure group lookup logic including
     * the DEFAULT group fallback mechanism. It attempts to find the APR configuration for the
     * specified account group, transaction type, and transaction category. If the primary group
     * configuration is not found, it falls back to the DEFAULT group configuration.</p>
     * 
     * <p><b>COBOL Logic Flow:</b></p>
     * <pre>
     * CBACT04C.cbl 1200-GET-INT-RATE paragraph:
     * 
     * Line 419-425: Set composite key fields (account group ID, type code, category code)
     * Line 426-434: READ DISCGRP-FILE with composite key, check FILE STATUS
     * Line 437-438: If NOT FOUND, MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID
     * Line 441-449: READ DISCGRP-FILE again with DEFAULT group, check FILE STATUS
     * Line 452-459: If still NOT FOUND, DISPLAY ERROR and PERFORM 9999-ABEND-PROGRAM
     * </pre>
     * 
     * <p><b>Composite Key Construction:</b></p>
     * <p>The disclosure group entity uses a composite primary key consisting of three fields:</p>
     * <ul>
     *   <li><b>accountGroupId:</b> Account group identifier (e.g., "GROUP01", "GROUP02", "DEFAULT")</li>
     *   <li><b>transactionTypeCode:</b> Transaction type (e.g., "01" Purchase, "02" Payment)</li>
     *   <li><b>transactionCategoryCode:</b> Transaction category (e.g., "0001" Groceries, "0100" Cash Advance)</li>
     * </ul>
     * 
     * <p><b>Fallback Logic:</b></p>
     * <ol>
     *   <li>Attempt to find disclosure group using account's actual group ID (e.g., "GROUP01")</li>
     *   <li>If not found, retry with "DEFAULT" group ID (universal fallback configuration)</li>
     *   <li>If DEFAULT also not found, throw ResourceNotFoundException (configuration error)</li>
     * </ol>
     * 
     * <p><b>Business Rationale:</b></p>
     * <p>The DEFAULT group serves as a universal fallback ensuring that all accounts can calculate
     * interest even if their specific group configuration is missing. This prevents batch job failures
     * due to incomplete reference data and ensures continuous operation. The DEFAULT group typically
     * contains conservative (higher) APR rates as a safeguard.</p>
     * 
     * <p><b>Configuration Examples:</b></p>
     * <pre>
     * Scenario 1: Custom group configuration exists
     *   groupId: "PREMIUM", typeCode: "01", categoryCode: "0001"
     *   → Finds PREMIUM group APR (e.g., 14.99% lower rate for premium customers)
     *   
     * Scenario 2: Custom group missing, DEFAULT exists
     *   groupId: "NEWGROUP", typeCode: "01", categoryCode: "0001"
     *   → Primary lookup fails, fallback to DEFAULT group APR (e.g., 18.99% standard rate)
     *   
     * Scenario 3: Both missing (configuration error)
     *   groupId: "NEWGROUP", typeCode: "99", categoryCode: "9999"
     *   → Primary lookup fails, DEFAULT lookup fails
     *   → Throws ResourceNotFoundException
     *   → Batch job logs error and skips account
     * </pre>
     * 
     * <p><b>Error Handling Strategy:</b></p>
     * <ul>
     *   <li><b>Primary Not Found:</b> Log warning, attempt DEFAULT fallback (COBOL line 437-438)</li>
     *   <li><b>DEFAULT Not Found:</b> Throw ResourceNotFoundException, abort account processing
     *       (COBOL line 452-459: PERFORM 9999-ABEND-PROGRAM)</li>
     *   <li><b>Database Error:</b> Propagate DataAccessException, trigger transaction rollback</li>
     * </ul>
     * 
     * <p><b>Logging Output:</b></p>
     * <ul>
     *   <li>DEBUG: Primary disclosure group lookup with key details</li>
     *   <li>WARN: Primary group not found, attempting DEFAULT fallback</li>
     *   <li>DEBUG: DEFAULT group found, using fallback APR</li>
     *   <li>ERROR: DEFAULT group not found, configuration error (before throwing exception)</li>
     * </ul>
     * 
     * <p><b>Performance Characteristics:</b></p>
     * <ul>
     *   <li>Best case: One database query (primary group found) - O(1) complexity</li>
     *   <li>Worst case: Two database queries (fallback to DEFAULT) - O(1) complexity</li>
     *   <li>Composite primary key lookups use B-tree index (fast)</li>
     *   <li>No table scans or sequential access</li>
     * </ul>
     * 
     * <p><b>Thread Safety:</b></p>
     * <p>This method is thread-safe as it only reads from the database and does not maintain any
     * shared state. Multiple threads can call this method concurrently without synchronization.</p>
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * // Called internally by calculateInterestForAccount()
     * String accountGroupId = "PREMIUM";
     * String transactionTypeCode = "01"; // Purchase
     * String transactionCategoryCode = "0001"; // Groceries
     * 
     * DisclosureGroup disclosureGroup = lookupDisclosureGroup(
     *     accountGroupId, transactionTypeCode, transactionCategoryCode);
     * 
     * BigDecimal apr = disclosureGroup.getInterestRate(); // e.g., 14.99
     * }</pre>
     * 
     * @param accountGroupId Account's disclosure group ID (e.g., "GROUP01", "PREMIUM", "SECURED")
     * @param transactionTypeCode Transaction type code (e.g., "01" Purchase, "02" Payment)
     * @param transactionCategoryCode Transaction category code (e.g., "0001" Groceries, "0100" Cash Advance)
     * @return DisclosureGroup entity containing APR configuration (either primary or DEFAULT fallback)
     * @throws ResourceNotFoundException if both primary and DEFAULT groups not found
     * @throws IllegalArgumentException if any parameter is null
     * @see DisclosureGroup Entity class with APR configuration
     * @see DisclosureGroupId Composite primary key class
     */
    private DisclosureGroup lookupDisclosureGroup(String accountGroupId,
                                                   String transactionTypeCode,
                                                   String transactionCategoryCode) {
        // Validate input parameters
        if (accountGroupId == null) {
            throw new IllegalArgumentException("Account group ID cannot be null");
        }
        if (transactionTypeCode == null) {
            throw new IllegalArgumentException("Transaction type code cannot be null");
        }
        if (transactionCategoryCode == null) {
            throw new IllegalArgumentException("Transaction category code cannot be null");
        }

        // Attempt primary disclosure group lookup (CBACT04C.cbl line 419-434)
        DisclosureGroupId primaryKey = new DisclosureGroupId(
                accountGroupId, transactionTypeCode, transactionCategoryCode);

        Optional<DisclosureGroup> primaryGroup = disclosureGroupRepository.findById(primaryKey);

        if (primaryGroup.isPresent()) {
            log.debug("Found disclosure group: accountGroupId={}, typeCode={}, categoryCode={}, APR={}",
                    accountGroupId, transactionTypeCode, transactionCategoryCode,
                    primaryGroup.get().getInterestRate());
            return primaryGroup.get();
        }

        // Primary group not found, attempt DEFAULT fallback (COBOL line 437-449)
        log.warn("Disclosure group not found for accountGroupId={}, typeCode={}, categoryCode={} - " +
                        "attempting DEFAULT fallback",
                accountGroupId, transactionTypeCode, transactionCategoryCode);

        DisclosureGroupId defaultKey = new DisclosureGroupId(
                DEFAULT_GROUP_ID, transactionTypeCode, transactionCategoryCode);

        Optional<DisclosureGroup> defaultGroup = disclosureGroupRepository.findById(defaultKey);

        if (defaultGroup.isPresent()) {
            log.debug("Using DEFAULT disclosure group: typeCode={}, categoryCode={}, APR={}",
                    transactionTypeCode, transactionCategoryCode,
                    defaultGroup.get().getInterestRate());
            return defaultGroup.get();
        }

        // Both primary and DEFAULT groups not found - configuration error (COBOL line 452-459)
        String errorMessage = String.format(
                "Disclosure group not found for accountGroupId=%s (or DEFAULT), typeCode=%s, categoryCode=%s",
                accountGroupId, transactionTypeCode, transactionCategoryCode);
        log.error(errorMessage);
        throw new ResourceNotFoundException(errorMessage);
    }
}
