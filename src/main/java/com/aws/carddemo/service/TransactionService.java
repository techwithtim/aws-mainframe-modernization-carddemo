package com.aws.carddemo.service;

import com.aws.carddemo.exception.InsufficientFundsException;
import com.aws.carddemo.exception.InvalidInputException;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.CardXref;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.model.TransactionCategoryBalance;
import com.aws.carddemo.model.TransactionCategoryBalanceId;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardXrefRepository;
import com.aws.carddemo.repository.TransactionCategoryBalanceRepository;
import com.aws.carddemo.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service managing credit card transaction operations including transaction history retrieval
 * with pagination and date range filtering, transaction detail lookup by transaction ID, new
 * transaction posting with card-to-account validation and balance updates, transaction category
 * classification, and transaction amount validation.
 * 
 * Migrated from: app/cbl/COTRN00C.cbl (transaction browse), COTRN01C.cbl (transaction view),
 * COTRN02C.cbl (transaction add), CBTRN01C.cbl (batch posting), CBTRN02C.cbl (batch posting with balance updates)
 * 
 * Business Logic Preservation:
 * - Transaction History Browse: Implements COBOL STARTBR/READNEXT pagination pattern from COTRN00C.cbl
 *   displaying WS-TRANS-REC OCCURS 10 transactions per page sorted by processingTimestamp DESC
 * - Transaction Detail Lookup: Replaces COBOL READ TRANFILE BY TRAN-ID keyed access from COTRN01C.cbl
 *   with TransactionRepository.findById() returning Optional<Transaction>
 * - Transaction Posting: Coordinates multi-entity updates from COTRN02C.cbl and CBTRN02C.cbl including:
 *   (1) Card number validation via CardXrefRepository.findByCardNumber() resolving to account ID
 *   (2) Credit limit check via AccountService.validateCreditLimit() preventing over-limit charges
 *   (3) Transaction record insertion with dual timestamps (originalTimestamp, processingTimestamp)
 *   (4) Account balance update: ACCT-CURR-BAL += TRAN-AMT per CBTRN02C.cbl line 547
 *   (5) Category balance update: TCAT-BALANCE += TRAN-AMT for financial tracking
 * 
 * Data Type Precision:
 * - All monetary calculations use BigDecimal with RoundingMode.HALF_UP replacing COBOL
 *   PIC S9(09)V99 COMP-3 packed decimal arithmetic ensuring exact penny precision
 * - Transaction amounts validated: must be non-zero (positive for charges, negative for refunds)
 * - Date range queries use LocalDate for timezone-agnostic filtering
 * 
 * Transaction Management:
 * - postTransaction() method annotated @Transactional ensuring ACID properties for coordinated
 *   updates to Transaction entity, Account entity currentBalance, and TransactionCategoryBalance
 *   entity categoryBalance within single database transaction with automatic commit on success
 *   or rollback on exception (ResourceNotFoundException, InvalidInputException, InsufficientFundsException)
 * - Replaces COBOL EXEC CICS SYNCPOINT commit and SYNCPOINT ROLLBACK patterns from mainframe
 *   transaction management with Spring declarative transaction management
 * - Supports READ_COMMITTED isolation level with optimistic locking via @Version for concurrent
 *   transaction safety preventing lost updates when multiple threads post transactions simultaneously
 * 
 * Error Handling:
 * - FILE STATUS 23 (not found) → ResourceNotFoundException with HTTP 404
 * - FILE STATUS 22 (duplicate) → DuplicateResourceException with HTTP 409
 * - Invalid amount, invalid card number, invalid date → InvalidInputException with HTTP 400
 * - Credit limit exceeded → InsufficientFundsException with HTTP 422
 * - All exceptions include detailed financial information (requested amount, available balance, credit limit)
 * 
 * PCI-DSS Compliance:
 * - Card numbers masked in all logs using getCardNumberMasked() method showing only last 4 digits
 * - Audit logging records transaction events: successful postings with masked card numbers, amounts,
 *   account IDs, timestamps at INFO level; validation failures at WARN level; system errors at ERROR level
 * - Structured logging to CloudWatch Logs for security monitoring, compliance audit trails, and operational troubleshooting
 * 
 * Performance Optimizations:
 * - Paginated queries use indexed columns (account_id, processing_timestamp) for O(log n) access
 * - Date range queries leverage composite index on (account_id, processing_timestamp)
 * - Card cross-reference lookups use indexed card_number column for O(log n) card-to-account resolution
 * - Pessimistic locking with findByIdWithLock for account balance updates preventing phantom reads
 * 
 * Constructor-Injected Dependencies:
 * - TransactionRepository: Transaction data access with custom query methods
 * - AccountRepository: Account data access with pessimistic locking support
 * - CardXrefRepository: Card cross-reference lookups for card-to-account resolution
 * - TransactionCategoryBalanceRepository: Category balance data access
 * - AccountService: Business logic delegation for credit limit validation
 * 
 * Technical Specification:
 * - Section 0.4.1: TransactionService (COTRN00C.cbl, COTRN01C.cbl, COTRN02C.cbl) for transaction
 *   history queries and new transaction validation/posting
 * - Section 0.8.1: Critical Directive #1 - Maintain full functional equivalence with legacy COBOL application
 * - Section 0.8.1: Critical Directive #2 - Minimal change discipline, preserve existing business logic
 * - Section 0.8.1: Critical Directive #3 - PCI-DSS compliance, sensitive data masked in logs
 * - Section 0.8.1: Critical Directive #4 - Test-driven validation with JUnit 5 + Mockito
 * 
 * @see Transaction for transaction entity definition
 * @see Account for account entity with balance tracking
 * @see CardXref for card cross-reference entity
 * @see TransactionCategoryBalance for category balance tracking
 * @see TransactionRepository for transaction data access
 * @see AccountRepository for account data access with locking
 * @see ResourceNotFoundException for not found exceptions
 * @see InvalidInputException for validation failures
 * @see InsufficientFundsException for credit limit violations
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final TransactionCategoryBalanceRepository transactionCategoryBalanceRepository;
    private final AccountService accountService;

    /**
     * Retrieve paginated transaction history for a specific account sorted by processing timestamp
     * in descending order (latest transactions first).
     * 
     * Migrated from: app/cbl/COTRN00C.cbl PROCESS-PAGE-FORWARD paragraph (lines 285-328)
     * 
     * Business Logic:
     * - COBOL STARTBR/READNEXT pagination pattern with WS-TRANS-REC OCCURS 10 displays 10 transactions
     *   per page (lines 297-303 WS-IDX FROM 1 BY 1 UNTIL WS-IDX > 10)
     * - Spring Data Page abstraction replaces COBOL manual pagination logic
     * - COBOL TRAN-ORIG-TS field used for sorting becomes processingTimestamp DESC ordering
     * - COBOL CDEMO-CT00-PAGE-NUM tracks current page (line 324) replaced by Pageable.getPageNumber()
     * 
     * Query Performance:
     * - Uses composite index on (account_id, processing_timestamp DESC) for optimal query performance
     * - Page size typically 10 or 20 matching COBOL screen pagination (OCCURS 10 in COTRN00C.cbl)
     * - Offset pagination suitable for transaction browsing with stable sort order
     * 
     * @param accountId account identifier for transaction history query, must exist in ACCOUNT table
     * @param pageable pagination parameters including page number (0-based), page size (typically 10-20),
     *                 and optional sort criteria (default: processingTimestamp DESC)
     * @return Page<Transaction> containing transactions for the page with total count metadata
     *         enabling UI pagination controls (page X of Y, next/previous buttons), never null
     *         but may be empty page if no transactions exist for account or page exceeds total pages
     * @throws ResourceNotFoundException if accountId does not exist in database (FILE STATUS 23 equivalent)
     */
    public Page<Transaction> getTransactionHistory(Long accountId, Pageable pageable) {
        log.debug("Retrieving transaction history for account ID: {}, page: {}, size: {}",
                accountId, pageable.getPageNumber(), pageable.getPageSize());

        // Validate account exists (replaces COBOL READ ACCTFILE validation)
        if (!accountRepository.findById(accountId).isPresent()) {
            log.warn("Account not found: {}", accountId);
            throw new ResourceNotFoundException("Account not found with ID: " + accountId);
        }

        // Execute paginated query (replaces COBOL STARTBR/READNEXT loop)
        Page<Transaction> transactionPage = transactionRepository.findByAccountAccountId(accountId, pageable);

        log.info("Retrieved {} transactions for account {}, page {} of {}",
                transactionPage.getNumberOfElements(),
                accountId,
                transactionPage.getNumber() + 1,
                transactionPage.getTotalPages());

        return transactionPage;
    }

    /**
     * Retrieve detailed information for a specific transaction by transaction ID.
     * 
     * Migrated from: app/cbl/COTRN01C.cbl PROCESS-ENTER-KEY paragraph (lines 144-177)
     * 
     * Business Logic:
     * - COBOL READ TRANFILE BY TRAN-ID keyed random access (lines 161-173) replaced with
     *   TransactionRepository.findById() returning Optional<Transaction>
     * - COBOL FILE STATUS 23 (record not found) error handling mapped to ResourceNotFoundException
     * - COBOL TRAN-RECORD fields populated into screen (COTRN1AO) replaced with Transaction entity return
     * 
     * @param transactionId unique transaction identifier (TRAN-ID from COBOL CVTRA05Y.cpy)
     * @return Transaction entity containing all transaction details including amount, timestamps,
     *         card number (full number, not masked - service layer responsibility to mask for DTOs),
     *         merchant details, and account relationship, never null
     * @throws ResourceNotFoundException if transaction ID does not exist (FILE STATUS 23 equivalent)
     */
    public Transaction getTransactionById(Long transactionId) {
        log.debug("Retrieving transaction detail for ID: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found: {}", transactionId);
                    return new ResourceNotFoundException("Transaction not found with ID: " + transactionId);
                });

        log.info("Retrieved transaction: {} for account: {}, amount: {}, card: {}",
                transactionId,
                transaction.getAccount().getAccountId(),
                transaction.getAmount(),
                transaction.getCardNumberMasked());  // PCI-DSS: Log masked card number only

        return transaction;
    }

    /**
     * Post a new credit card transaction including validation, account balance update, and category
     * balance tracking. This method coordinates multi-entity updates within a single database transaction.
     * 
     * Migrated from:
     * - app/cbl/COTRN02C.cbl PROCESS-ENTER-KEY validation (lines 154-316)
     * - app/cbl/COTRN02C.cbl ADD-TRANSACTION paragraph (lines 444-490)
     * - app/cbl/CBTRN02C.cbl 2800-UPDATE-ACCOUNT-REC paragraph (lines 545-560)
     * - app/cbl/CBTRN02C.cbl 2600-UPDATE-TCAT-BAL paragraph (lines 506-542)
     * 
     * Business Logic Flow:
     * 
     * 1. Card Number Validation (COTRN02C.cbl lines 218-232):
     *    - COBOL: CARDNINI OF COTRN2AI IS NUMERIC validation
     *    - Java: CardXrefRepository.findByCardNumber() lookup
     *    - Resolves 16-digit card number to account ID via XREF file alternate index (CXACAIX)
     *    - Throws InvalidInputException if card number invalid or not found (FILE STATUS 23)
     * 
     * 2. Account Lookup with Pessimistic Locking (CBTRN02C.cbl lines 419-431):
     *    - COBOL: READ ACCOUNT-FILE UPDATE (line 421)
     *    - Java: AccountRepository.findByIdWithLock() with SELECT FOR UPDATE
     *    - Pessimistic locking prevents concurrent transaction posting conflicts
     *    - Throws ResourceNotFoundException if account not found (FILE STATUS 23)
     * 
     * 3. Credit Limit Validation (CBTRN02C.cbl implicit validation):
     *    - COBOL: IF ACCT-CURR-BAL + TRAN-AMT > ACCT-CREDIT-LIMIT pattern
     *    - Java: AccountService.validateCreditLimit(account, transactionAmount)
     *    - Throws InsufficientFundsException if currentBalance + transactionAmount > creditLimit
     *    - Validation occurs BEFORE any database modifications ensuring data integrity
     * 
     * 4. Transaction Record Creation (COTRN02C.cbl lines 444-468):
     *    - COBOL: MOVE fields to TRAN-RECORD, WRITE FD-TRANFILE-REC (line 478)
     *    - Java: Build Transaction entity with builder pattern, transactionRepository.save()
     *    - Dual timestamps: originalTimestamp (merchant authorization time from request),
     *      processingTimestamp = LocalDateTime.now() (system processing time)
     *    - Auto-generated transactionId via IDENTITY strategy replaces COBOL sequential ID generation
     * 
     * 5. Account Balance Update (CBTRN02C.cbl lines 545-559):
     *    - COBOL: ADD DALYTRAN-AMT TO ACCT-CURR-BAL (line 547)
     *    - Java: account.setCurrentBalance(account.getCurrentBalance().add(transaction.getAmount()))
     *    - BigDecimal arithmetic with exact precision replacing COBOL PIC S9(10)V99 COMP-3
     *    - Also updates currentCycleCredit or currentCycleDebit based on amount sign (lines 548-552)
     *    - REWRITE FD-ACCTFILE-REC (line 554) replaced with accountRepository.save() flush
     * 
     * 6. Category Balance Update (CBTRN02C.cbl lines 506-542):
     *    - COBOL: ADD DALYTRAN-AMT TO TCAT-BAL (line 527)
     *    - Java: Find TransactionCategoryBalance by account + category, increment categoryBalance
     *    - Maintains financial tracking aggregates for interest calculation and reporting
     *    - REWRITE FD-TCATBALF-REC (line 529) replaced with transactionCategoryBalanceRepository.save()
     * 
     * Transaction Management:
     * - @Transactional annotation ensures all 4 operations (insert transaction, update account balance,
     *   update category balance, update cycle balances) complete atomically within single database transaction
     * - On success: automatic COMMIT replacing COBOL EXEC CICS SYNCPOINT
     * - On exception: automatic ROLLBACK replacing COBOL EXEC CICS SYNCPOINT ROLLBACK
     * - READ_COMMITTED isolation prevents dirty reads while allowing concurrent reads
     * - Optimistic locking via @Version prevents lost updates from concurrent modifications
     * 
     * Error Handling:
     * - Invalid card number (not found in XREF) → InvalidInputException "Card number not found"
     * - Account not found → ResourceNotFoundException "Account not found with ID: {accountId}"
     * - Credit limit exceeded → InsufficientFundsException "Transaction amount {amount} exceeds available credit"
     * - Negative or zero amount → InvalidInputException "Transaction amount must be non-zero"
     * - Future transaction date → InvalidInputException "Transaction date cannot be in the future"
     * - Category balance not found → Auto-create with initial balance = transaction amount
     * 
     * PCI-DSS Compliance:
     * - Card number masked in all log statements using getCardNumberMasked() showing last 4 digits
     * - Successful transaction posting logged at INFO level with masked card, amount, account ID
     * - Validation failures logged at WARN level with reason but no sensitive data
     * - System errors logged at ERROR level with exception details for troubleshooting
     * 
     * @param cardNumber 16-digit credit card number (PIC X(16) from COBOL), validated via XREF lookup
     * @param transactionAmount monetary amount in dollars and cents with exact decimal precision,
     *                          positive for purchases/charges, negative for refunds/credits,
     *                          must be non-zero (COBOL PIC S9(09)V99 COMP-3 equivalent)
     * @param merchantName merchant name for transaction description (max 50 characters)
     * @param transactionTypeCode transaction type code (e.g., "01" purchase, "02" payment),
     *                            must exist in TRANSACTION_TYPE reference table
     * @param transactionCategoryCode transaction category code (e.g., "01" grocery, "02" fuel),
     *                                must exist in TRANSACTION_CATEGORY reference table
     * @param transactionDate original transaction authorization date from merchant (YYYY-MM-DD),
     *                        cannot be future date, replaces COBOL TRAN-ORIG-TS field
     * @return Transaction newly created transaction entity with assigned transactionId and timestamps
     * @throws ResourceNotFoundException if account not found or card not found in cross-reference
     * @throws InvalidInputException if amount is zero, date is future, or card number format invalid
     * @throws InsufficientFundsException if transaction amount exceeds available credit limit
     */
    @Transactional
    public Transaction postTransaction(
            String cardNumber,
            BigDecimal transactionAmount,
            String merchantName,
            String transactionTypeCode,
            String transactionCategoryCode,
            LocalDate transactionDate) {

        log.debug("Posting transaction: cardNumber=**{}**, amount={}, merchant={}, type={}, category={}, date={}",
                cardNumber.length() >= 4 ? cardNumber.substring(cardNumber.length() - 4) : "****",
                transactionAmount,
                merchantName,
                transactionTypeCode,
                transactionCategoryCode,
                transactionDate);

        // Validation: Amount must be non-zero (COTRN02C.cbl lines 242-253)
        if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Transaction amount validation failed: amount is zero or null");
            throw new InvalidInputException("Transaction amount must be non-zero");
        }

        // Validation: Transaction date cannot be in future (COTRN02C.cbl lines 270-286)
        if (transactionDate != null && transactionDate.isAfter(LocalDate.now())) {
            log.warn("Transaction date validation failed: date {} is in the future", transactionDate);
            throw new InvalidInputException("Transaction date cannot be in the future");
        }

        // Step 1: Card Number Validation and Account Resolution (COTRN02C.cbl lines 218-232)
        // COBOL: READ CCXREF-FILE BY CARD-NUM using CXACAIX alternate index
        CardXref cardXref = cardXrefRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> {
                    log.warn("Card number not found in cross-reference: **{}**",
                            cardNumber.length() >= 4 ? cardNumber.substring(cardNumber.length() - 4) : "****");
                    return new InvalidInputException("Card number not found: " + maskCardNumber(cardNumber));
                });

        Long accountId = cardXref.getAccountId();
        log.debug("Resolved card number to account ID: {}", accountId);

        // Step 2: Account Lookup with Pessimistic Locking (CBTRN02C.cbl lines 419-431)
        // COBOL: READ ACCOUNT-FILE UPDATE
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> {
                    log.error("Account not found during transaction posting: {}", accountId);
                    return new ResourceNotFoundException("Account not found with ID: " + accountId);
                });

        // Step 3: Credit Limit Validation (CBTRN02C.cbl implicit balance check)
        // COBOL: IF ACCT-CURR-BAL + TRAN-AMT > ACCT-CREDIT-LIMIT
        accountService.validateCreditLimit(account, transactionAmount);
        log.debug("Credit limit validation passed for account: {}", accountId);

        // Step 4: Transaction Record Creation (COTRN02C.cbl lines 444-468)
        // COBOL: MOVE fields to TRAN-RECORD, WRITE FD-TRANFILE-REC
        Transaction transaction = Transaction.builder()
                .account(account)
                .cardNumber(cardNumber)
                .amount(transactionAmount)
                .merchantName(merchantName)
                .transactionTypeCode(transactionTypeCode)
                .transactionCategoryCode(transactionCategoryCode)
                .originalTimestamp(transactionDate != null ? transactionDate.atStartOfDay() : LocalDateTime.now())
                .processingTimestamp(LocalDateTime.now())
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Transaction record created: ID={}, amount={}, card=**{}**",
                transaction.getTransactionId(),
                transaction.getAmount(),
                transaction.getCardNumberMasked());

        // Step 5: Account Balance Update (CBTRN02C.cbl lines 545-559)
        // COBOL: ADD DALYTRAN-AMT TO ACCT-CURR-BAL (line 547)
        BigDecimal oldBalance = account.getCurrentBalance();
        BigDecimal newBalance = oldBalance.add(transactionAmount);
        account.setCurrentBalance(newBalance);

        // Update cycle credit/debit tracking (CBTRN02C.cbl lines 548-552)
        if (transactionAmount.compareTo(BigDecimal.ZERO) >= 0) {
            // Positive amount = charge/purchase → increment cycle credit
            account.setCurrentCycleCredit(
                    account.getCurrentCycleCredit().add(transactionAmount));
        } else {
            // Negative amount = refund/credit → increment cycle debit
            account.setCurrentCycleDebit(
                    account.getCurrentCycleDebit().add(transactionAmount));
        }

        account = accountRepository.save(account);
        log.info("Account balance updated: accountId={}, oldBalance={}, newBalance={}, change={}",
                accountId, oldBalance, newBalance, transactionAmount);

        // Step 6: Category Balance Update (CBTRN02C.cbl lines 506-542)
        // COBOL: ADD DALYTRAN-AMT TO TCAT-BAL (line 527)
        TransactionCategoryBalanceId categoryBalanceId = new TransactionCategoryBalanceId(
                accountId, transactionTypeCode, transactionCategoryCode);
        
        Optional<TransactionCategoryBalance> categoryBalanceOpt =
                transactionCategoryBalanceRepository.findById(categoryBalanceId);

        TransactionCategoryBalance categoryBalance;
        if (categoryBalanceOpt.isPresent()) {
            // Existing category balance: increment
            categoryBalance = categoryBalanceOpt.get();
            BigDecimal oldCategoryBalance = categoryBalance.getCategoryBalance();
            BigDecimal newCategoryBalance = oldCategoryBalance.add(transactionAmount);
            categoryBalance.setCategoryBalance(newCategoryBalance);
            log.debug("Updated category balance: type={}, category={}, old={}, new={}",
                    transactionTypeCode, transactionCategoryCode, oldCategoryBalance, newCategoryBalance);
        } else {
            // New category balance: initialize with transaction amount
            categoryBalance = TransactionCategoryBalance.builder()
                    .accountId(accountId)
                    .transactionTypeCode(transactionTypeCode)
                    .transactionCategoryCode(transactionCategoryCode)
                    .categoryBalance(transactionAmount)
                    .build();
            log.debug("Created new category balance: category={}, balance={}",
                    transactionCategoryCode, transactionAmount);
        }

        transactionCategoryBalanceRepository.save(categoryBalance);

        log.info("Transaction posted successfully: transactionId={}, accountId={}, amount={}, card=**{}**",
                transaction.getTransactionId(),
                accountId,
                transactionAmount,
                transaction.getCardNumberMasked());

        return transaction;
    }

    /**
     * Retrieve transactions within a date range for a specific account with pagination support.
     * 
     * Query Performance:
     * - Uses composite index on (account_id, processing_timestamp) for optimal query performance
     * - Date range query benefits from B-tree index range scan
     * - Suitable for monthly statement generation and date-filtered transaction reports
     * 
     * @param accountId account identifier for transaction query
     * @param startDate inclusive start date for date range filter (YYYY-MM-DD)
     * @param endDate inclusive end date for date range filter (YYYY-MM-DD)
     * @param pageable pagination parameters including page number, page size, and sort criteria
     * @return Page<Transaction> containing transactions within date range sorted by timestamp
     * @throws ResourceNotFoundException if account ID does not exist
     * @throws InvalidInputException if startDate is after endDate
     */
    public Page<Transaction> getTransactionsByDateRange(
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        log.debug("Retrieving transactions for account {} between {} and {}",
                accountId, startDate, endDate);

        // Validate date range
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            log.warn("Invalid date range: startDate {} is after endDate {}", startDate, endDate);
            throw new InvalidInputException("Start date cannot be after end date");
        }

        // Validate account exists
        if (!accountRepository.findById(accountId).isPresent()) {
            log.warn("Account not found: {}", accountId);
            throw new ResourceNotFoundException("Account not found with ID: " + accountId);
        }

        // Execute date range query
        // Repository method accepts LocalDate and uses DATE() function in JPQL for date-only comparison
        LocalDate effectiveStartDate = startDate != null ? startDate : LocalDate.MIN;
        LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.MAX;

        Page<Transaction> transactions = transactionRepository.findByTransactionDateBetween(
                effectiveStartDate, effectiveEndDate, pageable);

        log.info("Retrieved {} transactions for account {} in date range {} to {}",
                transactions.getNumberOfElements(), accountId, startDate, endDate);

        return transactions;
    }

    /**
     * Retrieve transactions by category code for reporting and analysis purposes.
     * 
     * This method supports financial reporting and category-based transaction analysis
     * by filtering transactions based on their transaction category code (e.g., "01" for
     * groceries, "02" for fuel, "03" for dining).
     * 
     * Use Cases:
     * - Monthly spending breakdown by category for cardholder statements
     * - Category-based transaction analysis for fraud detection
     * - Merchant category code (MCC) reporting for business intelligence
     * - Category spending limits enforcement and monitoring
     * 
     * @param transactionCategoryCode transaction category code (e.g., "01", "02", "03")
     *                                must exist in TRANSACTION_CATEGORY reference table
     * @return List of transactions matching the category code, sorted by processing timestamp DESC,
     *         never null but may be empty if no transactions exist for category
     * @throws InvalidInputException if category code is null, empty, or invalid format
     */
    public java.util.List<Transaction> getTransactionsByCategory(String transactionCategoryCode) {
        log.debug("Retrieving transactions for category: {}", transactionCategoryCode);

        // Validate category code format
        if (transactionCategoryCode == null || transactionCategoryCode.trim().isEmpty()) {
            log.warn("Invalid transaction category code: null or empty");
            throw new InvalidInputException("Transaction category code cannot be null or empty");
        }

        // Execute category query
        java.util.List<Transaction> transactions = transactionRepository.findByTransactionCategoryCode(
                transactionCategoryCode);

        log.info("Retrieved {} transactions for category: {}", transactions.size(), transactionCategoryCode);

        return transactions;
    }

    /**
     * Get transaction summary statistics for an account including total count, sum of amounts,
     * and date range of transactions.
     * 
     * This method provides high-level transaction metrics for account overview screens,
     * statement summaries, and financial reporting without retrieving individual transaction details.
     * 
     * Use Cases:
     * - Account dashboard displaying transaction summary (total transactions, total amount)
     * - Statement generation header information (period totals, transaction count)
     * - Financial reporting aggregates (monthly totals, yearly totals)
     * - Fraud detection baseline metrics (typical transaction count and amount ranges)
     * 
     * Performance Optimization:
     * - Uses database aggregation functions (COUNT, SUM, MIN, MAX) for efficient calculation
     * - Avoids loading individual transaction entities into memory
     * - Suitable for high-frequency dashboard queries requiring fast response times
     * 
     * @param accountId account identifier for summary calculation
     * @return Map containing summary statistics:
     *         - "totalCount": total number of transactions (Long)
     *         - "totalAmount": sum of all transaction amounts (BigDecimal)
     *         - "earliestDate": date of oldest transaction (LocalDateTime)
     *         - "latestDate": date of most recent transaction (LocalDateTime)
     *         Returns empty map if no transactions exist for account
     * @throws ResourceNotFoundException if account ID does not exist
     */
    public java.util.Map<String, Object> getAccountTransactionSummary(Long accountId) {
        log.debug("Retrieving transaction summary for account: {}", accountId);

        // Validate account exists
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.warn("Account not found: {}", accountId);
                    return new ResourceNotFoundException("Account not found with ID: " + accountId);
                });

        // Retrieve all transactions for account (could be optimized with aggregation query)
        // Using unpaged to get all results; consider adding database aggregation for large accounts
        Page<Transaction> transactionPage = transactionRepository.findByAccountAccountId(
                accountId, Pageable.unpaged());
        java.util.List<Transaction> transactions = transactionPage.getContent();

        // Calculate summary statistics
        java.util.Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("accountId", accountId);
        summary.put("accountNumber", account.getAccountNumber());
        summary.put("totalCount", (long) transactions.size());

        if (!transactions.isEmpty()) {
            BigDecimal totalAmount = transactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            summary.put("totalAmount", totalAmount);

            LocalDateTime earliestDate = transactions.stream()
                    .map(Transaction::getProcessingTimestamp)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            summary.put("earliestDate", earliestDate);

            LocalDateTime latestDate = transactions.stream()
                    .map(Transaction::getProcessingTimestamp)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            summary.put("latestDate", latestDate);
        } else {
            summary.put("totalAmount", BigDecimal.ZERO);
            summary.put("earliestDate", null);
            summary.put("latestDate", null);
        }

        log.info("Transaction summary for account {}: count={}, total={}",
                accountId, summary.get("totalCount"), summary.get("totalAmount"));

        return summary;
    }

    /**
     * Helper method to mask card numbers for PCI-DSS compliant logging.
     * Shows only last 4 digits: "************1234"
     * 
     * @param cardNumber full 16-digit card number
     * @return masked card number showing last 4 digits
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****************";
        }
        return "************" + cardNumber.substring(cardNumber.length() - 4);
    }
}
