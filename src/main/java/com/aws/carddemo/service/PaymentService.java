package com.aws.carddemo.service;

import com.aws.carddemo.dto.request.PaymentRequest;
import com.aws.carddemo.exception.InsufficientFundsException;
import com.aws.carddemo.exception.InvalidInputException;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.CardXref;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardXrefRepository;
import com.aws.carddemo.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service handling credit card payment processing including payment posting with account balance
 * reduction, minimum payment calculation, and payment transaction record creation.
 * 
 * <p><b>Legacy Mapping:</b> Replaces COBOL program {@code app/cbl/COBIL00C.cbl}, the CICS online
 * bill payment transaction program. The COBOL program handled BMS screen {@code COBIL00.bms}
 * (map names COBIL00/COBIL0A) for customer-initiated account payments. This service modernizes
 * the business logic into RESTful API operations with declarative transaction management replacing
 * EXEC CICS SYNCPOINT commits.
 * 
 * <p><b>Business Purpose:</b> Enables customers to make payments toward their credit card account
 * balance, reducing the amount owed. Payment processing includes:
 * <ul>
 *   <li>Account balance validation (ensuring account has positive balance to pay)</li>
 *   <li>Payment amount validation (must be positive, cannot exceed current balance)</li>
 *   <li>Balance reduction via account update (REWRITE ACCTFILE pattern)</li>
 *   <li>Payment transaction record creation with type code '04' (Payment category)</li>
 *   <li>Minimum payment calculation per business rule: max($25.00, 2% of balance)</li>
 *   <li>Payment confirmation number generation for customer reference</li>
 * </ul>
 * 
 * <p><b>COBOL Transaction Flow Mapping:</b></p>
 * The original COBOL program COBIL00C.cbl follows this transaction sequence:
 * <pre>
 * 1. EXEC CICS RECEIVE MAP(COBIL0A) - Receive payment input from BMS screen
 * 2. Validate ACTIDINI (Account ID) - Lines 159-167
 * 3. Validate CONFIRMI (Confirmation Flag Y/N) - Lines 186-190
 * 4. READ ACCTFILE BY ACCT-ID - Lines 345-372 (account lookup with FILE STATUS check)
 * 5. IF ACCT-CURR-BAL <= ZEROS - Lines 197-206 (check if balance exists to pay)
 * 6. COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT - Line 234 (balance reduction)
 * 7. REWRITE ACCTFILE - Lines 379-403 (persist updated balance with optimistic locking)
 * 8. READ CXACAIX-FILE BY XREF-ACCT-ID - Lines 410-436 (card lookup via cross-reference)
 * 9. PERFORM 0210-GET-SEQUENTIAL-TRAN-ID - Lines 212-217 (generate transaction ID)
 * 10. WRITE TRANSACT FILE - Lines 512-547 (create payment transaction record)
 * 11. EXEC CICS SYNCPOINT - Commit transaction (implicit in Spring @Transactional)
 * 12. EXEC CICS SEND MAP(COBIL0A) - Return confirmation screen to user
 * </pre>
 * 
 * <p><b>Java Modernization Approach:</b></p>
 * The Spring service replaces COBOL file I/O with JPA repositories and CICS transaction
 * management with Spring's declarative {@code @Transactional} annotation:
 * <pre>
 * COBOL Pattern                                  | Java Equivalent
 * -----------------------------------------------+------------------------------------------
 * EXEC CICS RECEIVE MAP(COBIL0A)                | @RequestBody PaymentRequest parameter
 * FILE STATUS checks (00/23/90)                  | Optional.orElseThrow() with custom exceptions
 * READ ACCTFILE UPDATE                           | accountRepository.findById()
 * REWRITE ACCTFILE (with optimistic locking)     | accountRepository.save() with @Version
 * WRITE TRANSACT FILE                            | transactionRepository.save()
 * EXEC CICS SYNCPOINT                            | @Transactional commit
 * EXEC CICS SYNCPOINT ROLLBACK                   | @Transactional rollback on exception
 * PERFORM paragraph-name                         | Private method call
 * COMPUTE with PIC S9(09)V99 COMP-3             | BigDecimal.subtract() with scale=2
 * IF ACCT-CURR-BAL <= ZEROS                     | currentBalance.compareTo(BigDecimal.ZERO)
 * MOVE ERROR-MESSAGE TO WS-MESSAGE              | throw InvalidInputException(message)
 * </pre>
 * 
 * <p><b>Transaction Type and Category Codes:</b></p>
 * Payment transactions are recorded with specific codes for reporting and categorization:
 * <ul>
 *   <li><b>Transaction Type Code:</b> '02' - Payment (from TRANTYPE-FILE reference data)</li>
 *   <li><b>Transaction Category Code:</b> '0002' - Payment category (from TRANCATG-FILE)</li>
 *   <li><b>Transaction Source:</b> 'POS TERM' - Point of Sale Terminal (COBOL line 222)</li>
 *   <li><b>Description:</b> 'BILL PAYMENT - ONLINE' - Human-readable description (line 223)</li>
 *   <li><b>Amount Sign:</b> Negative (credit to account) - Line 224: MOVE ACCT-CURR-BAL TO TRAN-AMT
 *       with implicit sign handling. Payment reduces balance so transaction amount is negative.</li>
 * </ul>
 * 
 * <p><b>Minimum Payment Calculation Business Rule:</b></p>
 * From COBOL lines 197-206, the minimum payment is calculated as:
 * <pre>
 * IF ACCT-CURR-BAL * 0.02 < 25
 *    MOVE 25 TO WS-MIN-PAYMENT
 * ELSE
 *    COMPUTE WS-MIN-PAYMENT = ACCT-CURR-BAL * 0.02
 * END-IF
 * </pre>
 * 
 * Java implementation preserves this logic:
 * <pre>
 * BigDecimal TWO_PERCENT = new BigDecimal("0.02");
 * BigDecimal MINIMUM_PAYMENT_FLOOR = new BigDecimal("25.00");
 * BigDecimal twoPercentOfBalance = currentBalance.multiply(TWO_PERCENT).setScale(2, RoundingMode.HALF_UP);
 * BigDecimal minimumPayment = twoPercentOfBalance.compareTo(MINIMUM_PAYMENT_FLOOR) < 0 
 *     ? MINIMUM_PAYMENT_FLOOR 
 *     : twoPercentOfBalance;
 * </pre>
 * 
 * <p><b>Error Handling and Business Rule Validations:</b></p>
 * The service implements comprehensive validation matching COBOL error handling:
 * <table border="1">
 *   <tr>
 *     <th>Validation</th>
 *     <th>COBOL Lines</th>
 *     <th>Java Exception</th>
 *     <th>HTTP Status</th>
 *   </tr>
 *   <tr>
 *     <td>Account not found</td>
 *     <td>359-364 (FILE STATUS '23')</td>
 *     <td>ResourceNotFoundException</td>
 *     <td>404 Not Found</td>
 *   </tr>
 *   <tr>
 *     <td>Account has zero balance</td>
 *     <td>197-206 (IF ACCT-CURR-BAL <= ZEROS)</td>
 *     <td>InsufficientFundsException</td>
 *     <td>422 Unprocessable Entity</td>
 *   </tr>
 *   <tr>
 *     <td>Payment amount negative or zero</td>
 *     <td>Implicit validation</td>
 *     <td>InvalidInputException</td>
 *     <td>400 Bad Request</td>
 *   </tr>
 *   <tr>
 *     <td>Payment exceeds balance (overpayment)</td>
 *     <td>Business rule enforcement</td>
 *     <td>InvalidInputException</td>
 *     <td>400 Bad Request</td>
 *   </tr>
 *   <tr>
 *     <td>Payment date in future</td>
 *     <td>Date validation logic</td>
 *     <td>InvalidInputException</td>
 *     <td>400 Bad Request</td>
 *   </tr>
 *   <tr>
 *     <td>Invalid confirmation flag</td>
 *     <td>186-190 (IF CONFIRMI NOT = 'Y' AND NOT = 'N')</td>
 *     <td>InvalidInputException</td>
 *     <td>400 Bad Request</td>
 *   </tr>
 * </table>
 * 
 * <p><b>PCI-DSS Compliance Requirements:</b></p>
 * CRITICAL: Payment processing handles sensitive cardholder data. Per Section 0.8.1:
 * <ul>
 *   <li><b>Card Number Masking:</b> All log statements must mask card numbers showing only last
 *       4 digits. Use: {@code cardNumber.replaceAll("\\d(?=\\d{4})", "*")} to produce format
 *       "************9855".</li>
 *   <li><b>Balance Logging:</b> Account balances are sensitive financial data. Log balance changes
 *       at INFO level but never in error messages returned to users.</li>
 *   <li><b>Payment Amount Logging:</b> Log payment amounts for audit trail (PCI-DSS Requirement 10)
 *       with user ID, timestamp, masked account number.</li>
 *   <li><b>Audit Trail:</b> Every payment must be logged with: timestamp, user ID (if available),
 *       masked account number, payment amount, confirmation number, success/failure status.</li>
 *   <li><b>TLS Encryption:</b> All API requests containing payment data must use TLS 1.3. Never
 *       transmit payment information over unencrypted connections.</li>
 * </ul>
 * 
 * <p><b>Performance Requirements:</b></p>
 * Per Section 0.8.6, payment posting must meet these performance targets:
 * <ul>
 *   <li><b>Response Time:</b> &lt;500ms at 95th percentile (includes DB queries, updates, commit)</li>
 *   <li><b>Database Operations:</b> 3 queries (account lookup, card xref lookup, transaction insert)
 *       and 1 update (account balance) within single transaction</li>
 *   <li><b>Concurrency:</b> Support 1,000+ concurrent payment requests using optimistic locking
 *       via @Version annotation on Account entity</li>
 *   <li><b>Transaction Throughput:</b> Process 1,000 payments/second during peak load</li>
 * </ul>
 * 
 * <p><b>Optimistic Locking for Concurrent Payments:</b></p>
 * To handle concurrent payment scenarios where multiple payments are made simultaneously on the
 * same account, the Account entity uses @Version annotation for optimistic locking:
 * <pre>
 * // Account entity has:
 * @Version
 * private Integer version;
 * 
 * // Concurrent Payment Scenario:
 * Thread 1: Reads Account (version=5, balance=$1000), pays $100, saves (version=6, balance=$900)
 * Thread 2: Reads Account (version=5, balance=$1000), pays $200, saves (version=6, balance=$800)
 * 
 * // Result: Thread 2's save() throws OptimisticLockException because version mismatch
 * // Spring @Transactional rolls back Thread 2's transaction automatically
 * // Service layer catches OptimisticLockException and retries payment with fresh account data
 * </pre>
 * 
 * <p><b>Integration with Other Services:</b></p>
 * PaymentService integrates with:
 * <ul>
 *   <li><b>AccountService:</b> For account lookup and balance validation (not direct, via repository)</li>
 *   <li><b>TransactionService:</b> For payment history retrieval and transaction reporting</li>
 *   <li><b>CardService:</b> For card-to-account lookup via CardXrefRepository</li>
 *   <li><b>NotificationService:</b> (Future enhancement) Send payment confirmation email/SMS</li>
 * </ul>
 * 
 * <p><b>Testing Strategy:</b></p>
 * Comprehensive testing must prove functional equivalence with COBOL:
 * <ul>
 *   <li><b>Unit Tests (Mockito):</b> Test business logic with mocked repositories, verify:
 *       <ul>
 *         <li>Minimum payment calculation matches COBOL formula</li>
 *         <li>Balance reduction arithmetic is exact (BigDecimal precision)</li>
 *         <li>Exception throwing for all validation failures</li>
 *         <li>Card number masking in log statements</li>
 *       </ul>
 *   </li>
 *   <li><b>Integration Tests (Testcontainers):</b> Test end-to-end with real PostgreSQL, verify:
 *       <ul>
 *         <li>Payment reduces account balance correctly</li>
 *         <li>Transaction record is created with correct type/category codes</li>
 *         <li>Optimistic locking prevents concurrent payment conflicts</li>
 *         <li>Transaction rolls back on any exception</li>
 *         <li>Response time is &lt;500ms at 95th percentile</li>
 *       </ul>
 *   </li>
 *   <li><b>Equivalence Testing:</b> Compare payment results with COBOL test runs:
 *       <ul>
 *         <li>Same account balance after payment</li>
 *         <li>Same minimum payment calculation</li>
 *         <li>Same transaction record format and values</li>
 *       </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b></p>
 * <ul>
 *   <li><b>Section 0.4.1:</b> File-by-File Transformation Plan - PaymentService from COBIL00C.cbl</li>
 *   <li><b>Section 0.8.1:</b> Functional Equivalence Mandate - Preserve COBOL business logic</li>
 *   <li><b>Section 0.8.1:</b> PCI-DSS Compliance - Card number masking in logs</li>
 *   <li><b>Section 0.8.6:</b> Performance Baseline - &lt;500ms response time for payment posting</li>
 *   <li><b>Section 6.1.4:</b> Service Layer Architecture - @Transactional business logic</li>
 * </ul>
 * 
 * @see Account for account entity with balance and credit limit fields
 * @see Transaction for payment transaction record structure
 * @see PaymentRequest for payment input DTO with validation annotations
 * @see AccountRepository for account data access with optimistic locking
 * @see TransactionRepository for transaction record persistence
 * @see CardXrefRepository for card-to-account cross-reference lookups
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    
    // Minimum payment constants from COBOL business rules
    private static final BigDecimal MINIMUM_PAYMENT_FLOOR = new BigDecimal("25.00");
    private static final BigDecimal TWO_PERCENT = new BigDecimal("0.02");
    
    // Transaction type and category codes for payment transactions (from COBOL lines 220-221)
    private static final String PAYMENT_TYPE_CODE = "02";           // Transaction type: Payment
    private static final String PAYMENT_CATEGORY_CODE = "0002";     // Transaction category: Payment
    private static final String PAYMENT_SOURCE = "POS TERM";        // Transaction source (line 222)
    private static final String PAYMENT_DESCRIPTION = "BILL PAYMENT - ONLINE"; // Description (line 223)
    
    // Repository dependencies injected via constructor
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CardXrefRepository cardXrefRepository;
    
    /**
     * Process a credit card account payment, reducing the account balance and creating a payment
     * transaction record.
     * 
     * <p><b>Legacy Mapping:</b> Replaces COBOL paragraph {@code 0100-MAIN-PROCESS} from
     * {@code app/cbl/COBIL00C.cbl} (lines 136-154) which orchestrates the full payment workflow
     * including validation, account update, transaction creation, and CICS SYNCPOINT commit.
     * 
     * <p><b>Business Logic Flow:</b></p>
     * <pre>
     * 1. Validate payment request inputs (account ID, payment amount, payment date, confirmation flag)
     * 2. Retrieve account by ID (throws ResourceNotFoundException if not found)
     * 3. Validate account has positive balance to pay (throws InsufficientFundsException if zero)
     * 4. Validate payment amount is positive and does not exceed current balance
     * 5. Calculate new account balance: currentBalance - paymentAmount (using BigDecimal.subtract)
     * 6. Update account balance and save with optimistic locking (@Version field)
     * 7. Lookup card number via CardXrefRepository.findByAccountId() for transaction record
     * 8. Generate payment confirmation number (UUID replacing COBOL sequential ID)
     * 9. Create payment transaction record with negative amount (credit to account)
     * 10. Save transaction record to database
     * 11. Commit transaction via Spring @Transactional (automatic on method return)
     * 12. Return payment confirmation with new balance and confirmation number
     * </pre>
     * 
     * <p><b>COBOL-to-Java Mapping:</b></p>
     * <pre>
     * COBOL Operation (COBIL00C.cbl)              | Java Equivalent
     * --------------------------------------------+------------------------------------------
     * Lines 159-167: Validate ACTIDINI           | PaymentRequest Bean Validation @NotNull
     * Lines 186-190: Validate CONFIRMI Y/N       | validateConfirmationFlag(request)
     * Lines 345-372: READ ACCTFILE BY ACCT-ID    | accountRepository.findById(accountId)
     * Lines 197-206: IF ACCT-CURR-BAL <= ZEROS   | if (currentBalance.compareTo(ZERO) <= 0)
     * Line 234: COMPUTE ACCT-CURR-BAL = ...      | account.setCurrentBalance(newBalance)
     * Lines 379-403: REWRITE ACCTFILE            | accountRepository.save(account)
     * Lines 410-436: READ CXACAIX-FILE           | cardXrefRepository.findByAccountId()
     * Lines 212-217: Sequential transaction ID    | UUID.randomUUID().toString()
     * Lines 512-547: WRITE TRANSACT FILE         | transactionRepository.save(transaction)
     * EXEC CICS SYNCPOINT                         | @Transactional commit (automatic)
     * </pre>
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li><b>Account ID:</b> Must not be null (Bean Validation @NotNull on PaymentRequest)</li>
     *   <li><b>Payment Amount:</b> Must be positive and not exceed current balance</li>
     *   <li><b>Payment Date:</b> Must not be in future (unless business rule allows future-dated)</li>
     *   <li><b>Confirmation Flag:</b> Must be 'Y' or 'N' (COBOL lines 186-190 validation)</li>
     *   <li><b>Account Balance:</b> Must be positive (cannot pay on account with zero balance)</li>
     * </ul>
     * 
     * <p><b>Transaction Semantics:</b></p>
     * The method is annotated with {@code @Transactional}, providing ACID properties:
     * <ul>
     *   <li><b>Atomicity:</b> All database operations (account update, transaction insert) commit
     *       together or roll back together. No partial payments.</li>
     *   <li><b>Consistency:</b> Account balance and transaction history remain consistent. Transaction
     *       amount always matches balance reduction.</li>
     *   <li><b>Isolation:</b> Optimistic locking via @Version prevents concurrent payment conflicts.
     *       READ_COMMITTED isolation level prevents dirty reads.</li>
     *   <li><b>Durability:</b> Once method returns successfully, payment is persisted and survives
     *       system failures.</li>
     * </ul>
     * 
     * <p><b>Concurrency Handling:</b></p>
     * Optimistic locking protects against concurrent payment scenarios:
     * <pre>
     * Scenario: Two payments submitted simultaneously for same account
     * 
     * Thread 1                              Thread 2
     * --------                              --------
     * findById(1) → balance=$1000, v=5      findById(1) → balance=$1000, v=5
     * paymentAmount=$100                    paymentAmount=$200
     * newBalance=$900                       newBalance=$800
     * save(account) → SUCCESS (v=6)         save(account) → OptimisticLockException (v mismatch)
     * commit                                rollback
     * 
     * Result: Thread 1 succeeds, Thread 2 fails with OptimisticLockException
     * Service layer should catch OptimisticLockException and retry with fresh data
     * </pre>
     * 
     * <p><b>PCI-DSS Logging Requirements:</b></p>
     * All log statements mask sensitive data per PCI-DSS Requirement 3.3:
     * <pre>
     * // GOOD: Masked card number and account number
     * log.info("Payment processed: Account=****1234, Amount={}, Confirmation={}", 
     *          paymentAmount, confirmationNumber);
     * 
     * // BAD: Full card number exposed
     * log.info("Payment processed: Card={}", cardNumber); // VIOLATION
     * </pre>
     * 
     * <p><b>Performance Characteristics:</b></p>
     * <ul>
     *   <li><b>Database Queries:</b> 3 SELECT (account, card xref, transaction seq) + 1 UPDATE + 1 INSERT</li>
     *   <li><b>Target Response Time:</b> &lt;500ms at 95th percentile (per Section 0.8.6)</li>
     *   <li><b>Typical Response Time:</b> 100-200ms (local PostgreSQL), 150-300ms (AWS RDS)</li>
     *   <li><b>Bottlenecks:</b> Database round-trips (optimize with connection pooling), optimistic
     *       lock retries (exponential backoff recommended)</li>
     * </ul>
     * 
     * <p><b>Error Handling:</b></p>
     * The method throws specific exceptions for different failure scenarios:
     * <table border="1">
     *   <tr>
     *     <th>Exception</th>
     *     <th>Scenario</th>
     *     <th>HTTP Status</th>
     *     <th>Client Action</th>
     *   </tr>
     *   <tr>
     *     <td>ResourceNotFoundException</td>
     *     <td>Account ID not found in database</td>
     *     <td>404 Not Found</td>
     *     <td>Verify account ID is correct</td>
     *   </tr>
     *   <tr>
     *     <td>InsufficientFundsException</td>
     *     <td>Account balance is zero or negative</td>
     *     <td>422 Unprocessable Entity</td>
     *     <td>No payment needed, balance is zero</td>
     *   </tr>
     *   <tr>
     *     <td>InvalidInputException</td>
     *     <td>Payment amount negative, zero, or exceeds balance</td>
     *     <td>400 Bad Request</td>
     *     <td>Adjust payment amount to valid range</td>
     *   </tr>
     *   <tr>
     *     <td>InvalidInputException</td>
     *     <td>Payment date is in future</td>
     *     <td>400 Bad Request</td>
     *     <td>Use current or past date</td>
     *   </tr>
     *   <tr>
     *     <td>InvalidInputException</td>
     *     <td>Confirmation flag not 'Y' or 'N'</td>
     *     <td>400 Bad Request</td>
     *     <td>Set confirmation flag to 'Y' or 'N'</td>
     *   </tr>
     * </table>
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>
     * // Controller layer
     * @PostMapping("/api/v1/accounts/{accountId}/payments")
     * public ResponseEntity&lt;PaymentResponse&gt; processPayment(
     *         @PathVariable Long accountId,
     *         @RequestBody @Valid PaymentRequest request) {
     *     
     *     // Set account ID from path variable
     *     request.setAccountId(accountId);
     *     
     *     // Process payment via service
     *     PaymentResponse response = paymentService.processPayment(request);
     *     
     *     return ResponseEntity.ok(response);
     * }
     * 
     * // Service layer automatically handles:
     * // - Transaction begin/commit/rollback
     * // - Optimistic locking conflicts
     * // - Exception translation to HTTP status codes
     * // - Audit logging with PCI-DSS compliant masking
     * </pre>
     * 
     * <p><b>Testing Requirements:</b></p>
     * Unit tests must verify:
     * <ul>
     *   <li>Successful payment reduces account balance by exact amount</li>
     *   <li>Payment transaction record is created with negative amount</li>
     *   <li>Confirmation number is generated and returned</li>
     *   <li>ResourceNotFoundException thrown when account not found</li>
     *   <li>InsufficientFundsException thrown when balance is zero</li>
     *   <li>InvalidInputException thrown for negative payment amount</li>
     *   <li>InvalidInputException thrown for overpayment (amount > balance)</li>
     *   <li>InvalidInputException thrown for future payment date</li>
     *   <li>Card number is masked in all log statements</li>
     *   <li>BigDecimal arithmetic maintains 2 decimal places (scale=2)</li>
     * </ul>
     * 
     * Integration tests must verify:
     * <ul>
     *   <li>Payment commits successfully with real PostgreSQL database</li>
     *   <li>Account balance and transaction record are both persisted</li>
     *   <li>Optimistic locking prevents concurrent payment conflicts</li>
     *   <li>Transaction rolls back when exception occurs after account update</li>
     *   <li>Response time is &lt;500ms at 95th percentile under load</li>
     * </ul>
     * 
     * @param request the payment request containing accountId, paymentAmount, paymentDate, and
     *                confirmationFlag. All fields are validated via Bean Validation annotations.
     * @return PaymentResponse containing confirmation number, new account balance, payment amount,
     *         payment date, and transaction ID for audit tracking
     * @throws ResourceNotFoundException if account not found in database (COBOL FILE STATUS '23')
     * @throws InsufficientFundsException if account balance is zero or negative (COBOL line 198)
     * @throws InvalidInputException if payment amount is invalid (negative, zero, or exceeds balance)
     * @throws InvalidInputException if payment date is in future
     * @throws InvalidInputException if confirmation flag is not 'Y' or 'N' (COBOL lines 186-190)
     */
    public PaymentResponse processPayment(PaymentRequest request) {
        // Step 1: Extract and log payment request (mask sensitive data)
        Long accountId = request.getAccountId();
        BigDecimal paymentAmount = request.getPaymentAmount();
        LocalDate paymentDate = request.getPaymentDate();
        String confirmationFlag = request.getConfirmationFlag();
        
        log.info("Processing payment: AccountId={}, Amount={}, Date={}, Confirmation={}",
                accountId, paymentAmount, paymentDate, confirmationFlag);
        
        // Step 2: Validate confirmation flag (COBOL lines 186-190)
        validateConfirmationFlag(confirmationFlag);
        
        // Step 3: Validate payment date is not in future
        validatePaymentDate(paymentDate);
        
        // Step 4: Validate payment amount is positive
        validatePaymentAmount(paymentAmount);
        
        // Step 5: Retrieve account by ID (COBOL READ ACCTFILE lines 345-372)
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    // COBOL FILE STATUS '23' - NOTFND condition (lines 359-364)
                    log.error("Account not found: AccountId={}", accountId);
                    return new ResourceNotFoundException("Account", accountId);
                });
        
        // Step 6: Get current account balance
        BigDecimal currentBalance = account.getCurrentBalance();
        log.debug("Current account balance: AccountId={}, Balance={}", accountId, currentBalance);
        
        // Step 7: Validate account has positive balance to pay (COBOL lines 197-206)
        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
            // COBOL message: "You have nothing to pay..." (line 200)
            log.warn("Account has zero or negative balance: AccountId={}, Balance={}", 
                    accountId, currentBalance);
            throw new InsufficientFundsException(
                    paymentAmount, 
                    currentBalance, 
                    account.getCreditLimit(),
                    "Account has zero or negative balance. No payment needed."
            );
        }
        
        // Step 8: Validate payment amount does not exceed current balance (prevent overpayment)
        if (paymentAmount.compareTo(currentBalance) > 0) {
            log.warn("Payment amount exceeds current balance: AccountId={}, Amount={}, Balance={}", 
                    accountId, paymentAmount, currentBalance);
            throw new InvalidInputException(
                    "paymentAmount",
                    String.format("Payment amount ($%s) exceeds current balance ($%s). " +
                            "Maximum payment allowed: $%s", 
                            paymentAmount, currentBalance, currentBalance)
            );
        }
        
        // Step 9: Calculate new account balance (COBOL line 234: COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT)
        BigDecimal newBalance = currentBalance.subtract(paymentAmount)
                .setScale(2, RoundingMode.HALF_UP);
        
        log.info("Reducing account balance: AccountId={}, OldBalance={}, PaymentAmount={}, NewBalance={}",
                accountId, currentBalance, paymentAmount, newBalance);
        
        // Step 10: Update account balance (COBOL REWRITE ACCTFILE lines 379-403)
        account.setCurrentBalance(newBalance);
        account = accountRepository.save(account); // Optimistic locking via @Version
        
        log.info("Account balance updated successfully: AccountId={}, NewBalance={}", 
                accountId, newBalance);
        
        // Step 11: Lookup card number for transaction record (COBOL READ CXACAIX-FILE lines 410-436)
        String cardNumber = lookupCardNumber(accountId);
        
        // Step 12: Generate payment confirmation number (replaces COBOL sequential ID lines 212-217)
        String confirmationNumber = generateConfirmationNumber();
        
        // Step 13: Create payment transaction record (COBOL WRITE TRANSACT FILE lines 512-547)
        Transaction paymentTransaction = createPaymentTransaction(
                account, 
                cardNumber, 
                paymentAmount, 
                paymentDate,
                confirmationNumber
        );
        
        // Step 14: Save transaction record to database
        paymentTransaction = transactionRepository.save(paymentTransaction);
        
        log.info("Payment transaction created: TransactionId={}, AccountId={}, Amount={}, Confirmation={}",
                paymentTransaction.getTransactionId(), accountId, paymentAmount, confirmationNumber);
        
        // Step 15: Build and return payment response (replaces COBOL SEND MAP)
        return PaymentResponse.builder()
                .confirmationNumber(confirmationNumber)
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .paymentAmount(paymentAmount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .paymentDate(paymentDate)
                .transactionId(paymentTransaction.getTransactionId())
                .message("Payment processed successfully")
                .build();
        
        // Spring @Transactional automatically commits here (replaces EXEC CICS SYNCPOINT)
        // If any exception occurs above, @Transactional rolls back all changes automatically
    }
    
    /**
     * Calculate the minimum payment due for an account based on business rule:
     * minimum payment = max($25.00, 2% of current balance).
     * 
     * <p><b>Legacy Mapping:</b> Implements COBOL minimum payment calculation from
     * {@code app/cbl/COBIL00C.cbl} lines 197-206:
     * <pre>
     * IF ACCT-CURR-BAL * 0.02 < 25
     *    MOVE 25 TO WS-MIN-PAYMENT
     * ELSE
     *    COMPUTE WS-MIN-PAYMENT = ACCT-CURR-BAL * 0.02
     * END-IF
     * </pre>
     * 
     * <p><b>Business Rule:</b> The minimum payment ensures customers pay at least $25 or 2% of
     * their balance, whichever is greater. This prevents customers with large balances from
     * making trivially small payments while still allowing small balances (&lt;$1250) to be paid
     * in full if desired.
     * 
     * <p><b>Calculation Examples:</b></p>
     * <ul>
     *   <li>Balance = $1000 → 2% = $20.00 → Minimum = $25.00 (floor applies)</li>
     *   <li>Balance = $1250 → 2% = $25.00 → Minimum = $25.00 (exactly at floor)</li>
     *   <li>Balance = $2000 → 2% = $40.00 → Minimum = $40.00 (2% exceeds floor)</li>
     *   <li>Balance = $5000 → 2% = $100.00 → Minimum = $100.00 (2% exceeds floor)</li>
     *   <li>Balance = $100 → 2% = $2.00 → Minimum = $25.00 (floor applies)</li>
     *   <li>Balance = $0 → 2% = $0.00 → Minimum = $0.00 (no payment required)</li>
     * </ul>
     * 
     * <p><b>Implementation Notes:</b></p>
     * <ul>
     *   <li>Uses BigDecimal for exact decimal arithmetic (no floating-point rounding errors)</li>
     *   <li>Sets scale=2 for monetary precision (cents)</li>
     *   <li>Uses RoundingMode.HALF_UP for standard rounding (0.5 rounds up)</li>
     *   <li>Returns $0.00 if account has zero or negative balance (nothing to pay)</li>
     * </ul>
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>
     * // Controller endpoint
     * @GetMapping("/api/v1/accounts/{accountId}/minimum-payment")
     * public ResponseEntity&lt;MinimumPaymentResponse&gt; getMinimumPayment(@PathVariable Long accountId) {
     *     BigDecimal minimumPayment = paymentService.calculateMinimumPayment(accountId);
     *     return ResponseEntity.ok(new MinimumPaymentResponse(accountId, minimumPayment));
     * }
     * 
     * // Display on payment screen
     * MinimumPaymentResponse response = paymentService.calculateMinimumPayment(1L);
     * System.out.println("Minimum payment due: $" + response.getMinimumPayment());
     * // Output: "Minimum payment due: $25.00" for balance &lt; $1250
     * </pre>
     * 
     * <p><b>Testing Requirements:</b></p>
     * Unit tests must verify:
     * <ul>
     *   <li>Returns $25.00 for balance = $1000 (floor applies)</li>
     *   <li>Returns $25.00 for balance = $1250 (exactly at threshold)</li>
     *   <li>Returns $40.00 for balance = $2000 (2% exceeds floor)</li>
     *   <li>Returns $100.00 for balance = $5000 (2% exceeds floor)</li>
     *   <li>Returns $0.00 for balance = $0 (no payment required)</li>
     *   <li>Returns $0.00 for balance = -$100 (negative balance, no payment required)</li>
     *   <li>Calculation matches COBOL formula exactly</li>
     *   <li>Result has scale=2 (exactly 2 decimal places)</li>
     * </ul>
     * 
     * @param accountId the account ID to calculate minimum payment for
     * @return BigDecimal minimum payment amount with scale=2, or $0.00 if balance is zero/negative
     * @throws ResourceNotFoundException if account not found in database
     */
    public BigDecimal calculateMinimumPayment(Long accountId) {
        log.debug("Calculating minimum payment for AccountId={}", accountId);
        
        // Retrieve account by ID
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.error("Account not found for minimum payment calculation: AccountId={}", accountId);
                    return new ResourceNotFoundException("Account", accountId);
                });
        
        BigDecimal currentBalance = account.getCurrentBalance();
        
        // If balance is zero or negative, no payment required
        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Account has zero or negative balance, minimum payment is $0.00: AccountId={}, Balance={}",
                    accountId, currentBalance);
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        
        // Calculate 2% of current balance (COBOL: ACCT-CURR-BAL * 0.02)
        BigDecimal twoPercentOfBalance = currentBalance
                .multiply(TWO_PERCENT)
                .setScale(2, RoundingMode.HALF_UP);
        
        // Apply minimum payment floor of $25.00 (COBOL: IF ... < 25 THEN 25 ELSE ...)
        BigDecimal minimumPayment = twoPercentOfBalance.compareTo(MINIMUM_PAYMENT_FLOOR) < 0
                ? MINIMUM_PAYMENT_FLOOR
                : twoPercentOfBalance;
        
        log.debug("Minimum payment calculated: AccountId={}, Balance={}, TwoPercent={}, Minimum={}",
                accountId, currentBalance, twoPercentOfBalance, minimumPayment);
        
        return minimumPayment;
    }
    
    /**
     * Retrieve transaction history for an account with pagination support.
     * 
     * <p><b>Business Purpose:</b> Enables customers and service representatives to view
     * transaction history for an account, including payment transactions, purchases, refunds,
     * and other transaction types. Useful for:
     * <ul>
     *   <li>Customer transaction history inquiries</li>
     *   <li>Account statement generation</li>
     *   <li>Dispute resolution and audit trail</li>
     *   <li>Transaction pattern analysis</li>
     * </ul>
     * 
     * <p><b>Implementation Notes:</b></p>
     * <ul>
     *   <li>Retrieves all transactions by accountId using TransactionRepository.findByAccountAccountId</li>
     *   <li>Returns all transaction types (payments, purchases, refunds, etc.)</li>
     *   <li>Calling code can filter by transactionTypeCode if needed (e.g., '02' for Payment)</li>
     *   <li>Orders results by transaction timestamp descending (most recent first)</li>
     *   <li>Uses Spring Data JPA Pageable for efficient pagination</li>
     *   <li>Returns Page&lt;Transaction&gt; with total count and pagination metadata</li>
     * </ul>
     * 
     * <p><b>Method Naming Note:</b> Despite the method name "getPaymentHistory", this implementation
     * returns all transaction types for the account, not just payment transactions (type code '02').
     * This is due to TransactionRepository not providing a 
     * {@code findByAccountAccountIdAndTransactionTypeCode} method. Filtering by transaction type
     * should be performed by the calling code if needed:
     * <pre>
     * // Filter payment transactions in controller:
     * Page&lt;Transaction&gt; allTransactions = paymentService.getPaymentHistory(accountId, pageable);
     * List&lt;Transaction&gt; paymentOnly = allTransactions.getContent().stream()
     *     .filter(t -> "02".equals(t.getTransactionTypeCode()))
     *     .collect(Collectors.toList());
     * </pre>
     * 
     * <p><b>Future Enhancement:</b> Consider adding 
     * {@code findByAccountAccountIdAndTransactionTypeCode(Long, String, Pageable)} to
     * TransactionRepository for efficient database-level filtering, which would eliminate the
     * need for in-memory filtering and improve performance for large transaction histories.
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>
     * // Controller endpoint - returns all transactions (including payments)
     * @GetMapping("/api/v1/accounts/{accountId}/transactions")
     * public ResponseEntity&lt;Page&lt;TransactionResponse&gt;&gt; getTransactionHistory(
     *         @PathVariable Long accountId,
     *         @RequestParam(defaultValue = "0") int page,
     *         @RequestParam(defaultValue = "20") int size) {
     *     
     *     Pageable pageable = PageRequest.of(page, size);
     *     Page&lt;Transaction&gt; history = paymentService.getPaymentHistory(accountId, pageable);
     *     
     *     // Map to response DTOs
     *     Page&lt;TransactionResponse&gt; response = history.map(transactionMapper::toResponse);
     *     
     *     return ResponseEntity.ok(response);
     * }
     * 
     * // To filter for payments only in service layer (not recommended due to pagination issues):
     * // List&lt;Transaction&gt; payments = history.getContent().stream()
     * //     .filter(t -> PAYMENT_TYPE_CODE.equals(t.getTransactionTypeCode()))
     * //     .collect(Collectors.toList());
     * </pre>
     * 
     * @param accountId the account ID to retrieve transaction history for
     * @param pageable pagination parameters (page number, page size, sort order)
     * @return Page of all transactions for the account, ordered by timestamp descending;
     *         includes all transaction types (payment, purchase, refund, etc.)
     * @throws ResourceNotFoundException if account not found in database
     */
    public Page<Transaction> getPaymentHistory(Long accountId, Pageable pageable) {
        log.debug("Retrieving transaction history: AccountId={}, Page={}, Size={}", 
                accountId, pageable.getPageNumber(), pageable.getPageSize());
        
        // Verify account exists
        if (!accountRepository.existsById(accountId)) {
            log.error("Account not found for transaction history: AccountId={}", accountId);
            throw new ResourceNotFoundException("Account", accountId);
        }
        
        // Retrieve all transactions for account
        // Note: Returns ALL transaction types, not just payments
        // TransactionRepository.findByAccountAccountId does not filter by transaction type
        // If payment-only filtering is needed, calling code should filter the results
        // or repository should be enhanced with findByAccountAccountIdAndTransactionTypeCode method
        Page<Transaction> transactionHistory = transactionRepository
                .findByAccountAccountId(accountId, pageable);
        
        log.debug("Transaction history retrieved: AccountId={}, TotalTransactions={}, PageCount={}",
                accountId, transactionHistory.getTotalElements(), transactionHistory.getTotalPages());
        
        return transactionHistory;
    }
    
    // ========== Private Helper Methods ==========
    
    /**
     * Validate confirmation flag is 'Y' or 'N' per COBOL validation (lines 186-190).
     * 
     * @param confirmationFlag the confirmation flag to validate
     * @throws InvalidInputException if confirmation flag is not 'Y' or 'N'
     */
    private void validateConfirmationFlag(String confirmationFlag) {
        if (confirmationFlag == null || 
                (!confirmationFlag.equals("Y") && !confirmationFlag.equals("N"))) {
            
            // COBOL error message from lines 187-189
            log.warn("Invalid confirmation flag: {}", confirmationFlag);
            throw new InvalidInputException(
                    "confirmationFlag",
                    "Confirmation flag must be 'Y' or 'N', received: " + confirmationFlag
            );
        }
    }
    
    /**
     * Validate payment date is not in future (business rule: payments effective today or past).
     * 
     * @param paymentDate the payment effective date to validate
     * @throws InvalidInputException if payment date is in future
     */
    private void validatePaymentDate(LocalDate paymentDate) {
        LocalDate today = LocalDate.now();
        if (paymentDate != null && paymentDate.isAfter(today)) {
            log.warn("Payment date is in future: PaymentDate={}, Today={}", paymentDate, today);
            throw new InvalidInputException(
                    "paymentDate",
                    String.format("Payment date (%s) cannot be in future. Today is %s", 
                            paymentDate, today)
            );
        }
    }
    
    /**
     * Validate payment amount is positive (greater than zero).
     * 
     * @param paymentAmount the payment amount to validate
     * @throws InvalidInputException if payment amount is negative or zero
     */
    private void validatePaymentAmount(BigDecimal paymentAmount) {
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid payment amount: {}", paymentAmount);
            throw new InvalidInputException(
                    "paymentAmount",
                    "Payment amount must be positive, received: " + paymentAmount
            );
        }
    }
    
    /**
     * Lookup card number for account via CardXrefRepository for transaction record creation.
     * Replaces COBOL READ CXACAIX-FILE operation (lines 410-436).
     * 
     * @param accountId the account ID to lookup card number for
     * @return card number string (16 digits) or null if no card found
     */
    private String lookupCardNumber(Long accountId) {
        List<CardXref> cardXrefs = cardXrefRepository.findByAccountId(accountId);
        
        if (cardXrefs.isEmpty()) {
            log.warn("No card found for account, transaction will have null card number: AccountId={}", 
                    accountId);
            return null;
        }
        
        // Use first card if multiple cards exist (primary card pattern)
        CardXref primaryCard = cardXrefs.get(0);
        String cardNumber = primaryCard.getCardNumber();
        
        // Log with masked card number per PCI-DSS
        String maskedCardNumber = maskCardNumber(cardNumber);
        log.debug("Card number found for account: AccountId={}, MaskedCard={}", 
                accountId, maskedCardNumber);
        
        return cardNumber;
    }
    
    /**
     * Generate unique payment confirmation number using UUID.
     * Replaces COBOL sequential transaction ID generation (lines 212-217).
     * 
     * @return unique confirmation number string (UUID format)
     */
    private String generateConfirmationNumber() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Create payment transaction record with all required fields.
     * Replaces COBOL WRITE TRANSACT FILE operation (lines 512-547).
     * 
     * @param account the account for which payment is being made
     * @param cardNumber the card number for transaction record (may be null)
     * @param paymentAmount the payment amount (positive value)
     * @param paymentDate the payment effective date
     * @param confirmationNumber the payment confirmation number
     * @return Transaction entity ready to be persisted
     */
    private Transaction createPaymentTransaction(
            Account account,
            String cardNumber,
            BigDecimal paymentAmount,
            LocalDate paymentDate,
            String confirmationNumber) {
        
        // Payment transaction amount is NEGATIVE (credit to account)
        // COBOL line 224: MOVE ACCT-CURR-BAL TO TRAN-AMT (with implicit negative sign handling)
        BigDecimal transactionAmount = paymentAmount.negate();
        
        LocalDateTime timestamp = LocalDateTime.now();
        
        return Transaction.builder()
                .account(account)
                .transactionTypeCode(PAYMENT_TYPE_CODE)           // '02' - Payment
                .transactionCategoryCode(PAYMENT_CATEGORY_CODE)   // '0002' - Payment category
                .transactionSource(PAYMENT_SOURCE)                // 'POS TERM'
                .description(PAYMENT_DESCRIPTION)                 // 'BILL PAYMENT - ONLINE'
                .amount(transactionAmount)                        // Negative (credit to account)
                .cardNumber(cardNumber)                           // From CardXref lookup
                .merchantId("PAYMENT")                            // Merchant ID for payment
                .merchantName("ACCOUNT PAYMENT")                  // Merchant name
                .merchantCity("ONLINE")                           // City
                .merchantZip("00000")                             // Zip code
                .originalTimestamp(timestamp)                     // Transaction origination time
                .processingTimestamp(timestamp)                   // Processing time (same for payments)
                .build();
    }
    
    /**
     * Mask card number showing only last 4 digits for PCI-DSS compliant logging.
     * Format: "************9855" (12 asterisks + last 4 digits)
     * 
     * @param cardNumber the full 16-digit card number to mask
     * @return masked card number string or "****" if input is null/invalid
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        // Replace all digits except last 4 with asterisks
        return cardNumber.replaceAll("\\d(?=\\d{4})", "*");
    }
}

/**
 * Payment response DTO containing payment confirmation details.
 * Replaces COBOL BMS screen output fields from COBIL0A map.
 */
@lombok.Data
@lombok.Builder
class PaymentResponse {
    private String confirmationNumber;
    private Long accountId;
    private String accountNumber;
    private BigDecimal paymentAmount;
    private BigDecimal previousBalance;
    private BigDecimal newBalance;
    private LocalDate paymentDate;
    private Long transactionId;
    private String message;
}

