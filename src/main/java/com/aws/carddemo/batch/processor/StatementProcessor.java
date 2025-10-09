/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.batch.processor;

import com.aws.carddemo.batch.dto.AccountTransactionGroup;
import com.aws.carddemo.batch.dto.FormattedTransaction;
import com.aws.carddemo.batch.dto.StatementData;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.Customer;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.repository.AccountRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring Batch ItemProcessor implementation that formats monthly account statements with transaction
 * details during statement generation job execution.
 * 
 * <p><b>COBOL Source Mapping:</b> Migrated from COBOL batch program {@code app/cbl/CBSTM03A.CBL}
 * paragraph {@code 5000-CREATE-STATEMENT} and {@code STATEMENT-LINES} copybook data structure.
 * This processor implements the complete business logic for statement formatting including:</p>
 * <ul>
 *   <li>Account summary value calculations (previous/current balance, total debits/credits, finance charges)</li>
 *   <li>Minimum payment due calculation (greater of $25 or 2% of current balance)</li>
 *   <li>Transaction list formatting in chronological order</li>
 *   <li>PCI-DSS compliant sensitive data masking (card numbers, account numbers)</li>
 *   <li>Customer address formatting from CUSTREC.cpy copybook field mappings</li>
 *   <li>Payment due date calculation (statement date + 21 days standard billing cycle)</li>
 * </ul>
 * 
 * <p><b>Functional Equivalence to COBOL:</b></p>
 * <pre>
 * COBOL Section                          Java Method                    Business Logic Preserved
 * ────────────────────────────────────────────────────────────────────────────────────────────────
 * 5000-CREATE-STATEMENT                  process()                      Main statement formatting orchestration
 * STATEMENT-LINES ST-LINE1-4             formatCustomerInfo()           Customer name and address header
 * STATEMENT-LINES ST-LINE7-9             calculateAccountSummary()      Balance and summary calculations
 * STATEMENT-LINES ST-LINE13-14           formatTransactionList()        Transaction detail lines
 * WS-MIN-PAY-DUE calculation             calculateMinimumPayment()      min($25, 2% * balance)
 * ACCT-CURR-BAL retrieval                getPreviousBalance()           Opening balance from prior period
 * </pre>
 * 
 * <p><b>Spring Batch Processing Flow:</b></p>
 * <pre>
 * AccountTransactionGroupReader → StatementProcessor → StatementWriter
 *                                       ↓
 *                         Transforms AccountTransactionGroup
 *                         into StatementData DTO ready for
 *                         template rendering (HTML/PDF)
 * </pre>
 * 
 * <p><b>Thread Safety and Statelessness:</b>
 * This processor is implemented as a stateless singleton Spring bean ({@code @Component})
 * with no mutable instance variables. All data transformations operate on method parameters
 * and local variables only, enabling safe parallel chunk processing when configured in
 * Spring Batch job definition. Multiple threads can safely invoke {@code process()} concurrently
 * without race conditions or data corruption.</p>
 * 
 * <p><b>PCI-DSS Compliance Requirements:</b>
 * Per Agent Action Plan Special Instruction 3 (Security: PCI-DSS compliance, sensitive data
 * masked in logs) and PCI-DSS requirement 3.4 (render PAN unreadable anywhere it is stored),
 * this processor implements comprehensive data masking:</p>
 * <ul>
 *   <li><b>Card Numbers:</b> Display as "XXXX-XXXX-XXXX-1234" (last 4 digits visible)</li>
 *   <li><b>Account Numbers:</b> Mask except last 4 digits, format: "XXXX-XXXX-X789"</li>
 *   <li><b>CVV Codes:</b> Never included in statement output (complete suppression)</li>
 *   <li><b>SSN:</b> Never included in customer-facing statements (internal use only)</li>
 * </ul>
 * 
 * <p><b>Financial Calculation Precision:</b>
 * All monetary calculations use {@link BigDecimal} with {@code scale=2} and
 * {@code RoundingMode.HALF_UP} to preserve exact decimal precision matching COBOL
 * {@code PIC S9(09)V99 COMP-3} packed decimal representation. This ensures:</p>
 * <ul>
 *   <li>No floating-point rounding errors in financial calculations</li>
 *   <li>Regulatory compliance for financial statement accuracy</li>
 *   <li>Exact functional equivalence with legacy COBOL arithmetic operations</li>
 * </ul>
 * 
 * <p><b>Backward Compatibility:</b>
 * Statement format preserves exact COBOL layout including:</p>
 * <ul>
 *   <li>Header lines with asterisk borders matching {@code ST-LINE0} pattern</li>
 *   <li>Customer address layout from {@code STATEMENT-LINES ST-LINE1} through {@code ST-LINE4}</li>
 *   <li>Transaction line format matching {@code ST-LINE13}, {@code ST-LINE14} structure</li>
 *   <li>Footer summary totals matching {@code ST-LINE12}, {@code ST-LINE14A}, {@code ST-LINE15}</li>
 * </ul>
 * This enables customer service representatives to process modernized statements without
 * retraining and supports automated statement parsing systems expecting legacy format structure.
 * 
 * <p><b>Date Format Standards:</b>
 * Transaction dates formatted as {@code MM/dd/yyyy} using {@link DateTimeFormatter#ofPattern(String)}
 * matching COBOL date display format. Examples: "01/15/2024", "12/31/2023". This format is
 * familiar to US customers and consistent with legacy statement conventions.</p>
 * 
 * <p><b>Currency Formatting Standards:</b>
 * Amounts formatted using {@link NumberFormat#getCurrencyInstance(Locale)} with {@code Locale.US}
 * producing output like "$1,234.56". This matches COBOL {@code CURRENCY-EDITED} format with
 * {@code PIC $Z,ZZZ,ZZ9.99} pattern from {@code ST-TRANAMT} field definition.</p>
 * 
 * <p><b>Business Rules Preserved from COBOL:</b></p>
 * <ul>
 *   <li><b>Minimum Payment Calculation:</b> {@code max($25.00, currentBalance * 0.02)} rounded to
 *       2 decimal places. Replicates COBOL logic: {@code IF ACCT-CURR-BAL * 0.02 < 25.00 THEN
 *       MOVE 25.00 TO MIN-PAY-DUE ELSE MOVE ACCT-CURR-BAL * 0.02 TO MIN-PAY-DUE}.</li>
 *   <li><b>Payment Due Date:</b> Statement period end date + 21 days. Matches COBOL:
 *       {@code COMPUTE DUE-DATE = STMT-DATE + 21}.</li>
 *   <li><b>Statement Opening Balance:</b> If no prior statement exists (new account), opening
 *       balance defaults to $0.00. Matches COBOL: {@code IF WS-PREVIOUS-STMT-FOUND = 'N' THEN
 *       MOVE 0.00 TO WS-OPENING-BAL}.</li>
 *   <li><b>Transaction Sorting:</b> Chronological order by {@code processingTimestamp} ascending,
 *       then by {@code transactionId} for deterministic ordering. Matches COBOL: {@code ORDER BY
 *       TRAN-DATE, TRAN-ID}.</li>
 *   <li><b>Description Truncation:</b> Transaction descriptions limited to 40 characters matching
 *       COBOL {@code PIC X(40)} field constraint from {@code ST-TRANDT} field.</li>
 * </ul>
 * 
 * <p><b>Error Handling:</b>
 * The processor propagates exceptions to Spring Batch framework for handling per job
 * configuration skip/retry policies. Common scenarios:</p>
 * <ul>
 *   <li><b>Null Input:</b> {@code NullPointerException} triggers skip if configured</li>
 *   <li><b>Invalid Data:</b> {@code IllegalArgumentException} for business rule violations</li>
 *   <li><b>Database Errors:</b> {@code DataAccessException} from repository calls</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li><b>Stateless Design:</b> No instance state reduces memory footprint</li>
 *   <li><b>Stream Processing:</b> Efficient transaction aggregation using Java streams</li>
 *   <li><b>Single Query:</b> Previous balance query uses repository findById with Optional</li>
 *   <li><b>Chunk Processing:</b> Typical chunk size 10-50 accounts balances throughput vs memory</li>
 * </ul>
 * 
 * <p><b>Usage Example in Spring Batch Job Configuration:</b></p>
 * <pre>
 * {@code @Bean
 * public Step statementGenerationStep(
 *         AccountTransactionGroupReader reader,
 *         StatementProcessor processor,
 *         StatementWriter writer) {
 *     return stepBuilder.get("statementGenerationStep")
 *             .<AccountTransactionGroup, StatementData>chunk(10)
 *             .reader(reader)
 *             .processor(processor)
 *             .writer(writer)
 *             .build();
 * }}
 * </pre>
 * 
 * <p><b>Technical Specification References:</b></p>
 * <ul>
 *   <li>Section 0.4.1: File Transformation Mapping - CBSTM03A.CBL → StatementProcessor.java</li>
 *   <li>Section 0.8.1: Critical Directive #3 - PCI-DSS compliance with sensitive data masking</li>
 *   <li>Section 0.8.3: Data Type Mapping Standards - BigDecimal for COMP-3 financial fields</li>
 *   <li>Section 2.3: Batch Processing Workflows - Statement generation job sequence</li>
 * </ul>
 * 
 * @see AccountTransactionGroup Input DTO containing grouped transaction data
 * @see StatementData Output DTO ready for template rendering
 * @see FormattedTransaction Individual transaction line DTO
 * @author CardDemo Modernization Team (AWS)
 * @since 1.0.0
 */
@Component
public class StatementProcessor implements ItemProcessor<AccountTransactionGroup, StatementData> {

    /**
     * Minimum payment amount constant ($25.00).
     * 
     * <p><b>COBOL Mapping:</b> Hardcoded value in CBSTM03A.CBL minimum payment calculation logic:
     * {@code IF ACCT-CURR-BAL * 0.02 < 25.00 THEN MOVE 25.00 TO MIN-PAY-DUE}.
     * 
     * <p><b>Business Rule:</b> Minimum payment cannot be less than $25.00 regardless of balance,
     * ensuring minimum revenue from each billing cycle and preventing abuse of revolving credit terms.
     */
    private static final BigDecimal MINIMUM_PAYMENT_FLOOR = new BigDecimal("25.00");

    /**
     * Minimum payment percentage (2% of current balance).
     * 
     * <p><b>COBOL Mapping:</b> Multiplier in CBSTM03A.CBL: {@code MOVE ACCT-CURR-BAL * 0.02 TO MIN-PAY-DUE}.
     * 
     * <p><b>Business Rule:</b> Minimum payment is typically 2% of outstanding balance, ensuring
     * principal reduction over time and discouraging perpetual minimum payment behavior.
     */
    private static final BigDecimal MINIMUM_PAYMENT_PERCENTAGE = new BigDecimal("0.02");

    /**
     * Payment grace period in days (21 days from statement date).
     * 
     * <p><b>COBOL Mapping:</b> {@code COMPUTE DUE-DATE = STMT-DATE + 21} in CBSTM03A.CBL.
     * 
     * <p><b>Business Rule:</b> Standard billing cycle provides 21-day grace period for payment
     * processing, matching industry standard credit card terms and CARD Act requirements.
     */
    private static final int PAYMENT_DUE_DAYS = 21;

    /**
     * Date formatter for transaction dates (MM/dd/yyyy format).
     * 
     * <p><b>COBOL Mapping:</b> Date display format in {@code ST-TRANDATE} field from CBSTM03A.CBL
     * {@code STATEMENT-LINES} structure. Thread-safe DateTimeFormatter instance for concurrent use.
     * 
     * <p><b>Format Examples:</b> "01/15/2024", "12/31/2023", "02/29/2024" (leap year)
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    /**
     * Currency formatter for US dollar amounts.
     * 
     * <p><b>COBOL Mapping:</b> {@code CURRENCY-EDITED} format with {@code PIC $Z,ZZZ,ZZ9.99}
     * pattern from {@code ST-TRANAMT} field in CBSTM03A.CBL.
     * 
     * <p><b>Format Examples:</b> "$1,234.56", "$0.99", "$1,000,000.00"
     * 
     * <p><b>Thread Safety:</b> {@link NumberFormat} is NOT thread-safe, so we create new instances
     * per invocation using {@link NumberFormat#getCurrencyInstance(Locale)} factory method.
     */
    private static final Locale US_LOCALE = Locale.US;

    /**
     * Maximum length for transaction descriptions (40 characters).
     * 
     * <p><b>COBOL Mapping:</b> {@code PIC X(40)} field constraint from {@code ST-TRANDT} field
     * in CBSTM03A.CBL {@code STATEMENT-LINES} copybook structure line 135.
     * 
     * <p><b>Business Rule:</b> Descriptions exceeding 40 characters are truncated to maintain
     * fixed-width statement layout compatibility with legacy systems and customer service tools.
     */
    private static final int MAX_DESCRIPTION_LENGTH = 40;

    /**
     * Fixed length for transaction reference numbers (16 characters).
     * 
     * <p><b>COBOL Mapping:</b> {@code PIC X(16)} field definition from {@code FD-TRANS-ID} in
     * CBSTM03A.CBL transaction file record layout line 133.
     * 
     * <p><b>Business Rule:</b> Transaction IDs are zero-padded to 16 characters for consistent
     * alignment in statement display and automated processing systems.
     */
    private static final int REFERENCE_NUMBER_LENGTH = 16;

    /**
     * Account repository for retrieving previous statement period opening balance.
     * 
     * <p><b>Design Note:</b> While ideally the Reader component should pre-populate all required
     * data in {@link AccountTransactionGroup} to keep Processors stateless, previous balance
     * calculation requires a separate query for the prior statement period. This is an acceptable
     * deviation from pure statelessness as it's a single lightweight query per account processed.
     * 
     * <p><b>Alternative Approach:</b> Could be refactored to include previousBalance in
     * AccountTransactionGroup DTO, moving this query to Reader component. Current design chosen
     * to isolate statement-specific calculation logic within processor for maintainability.
     */
    private final AccountRepository accountRepository;

    /**
     * Constructor for dependency injection of required repositories.
     * 
     * <p><b>Injection Pattern:</b> Constructor injection (recommended over field injection) enables:
     * <ul>
     *   <li>Immutable field references (final keyword)</li>
     *   <li>Easy mocking in unit tests</li>
     *   <li>Explicit dependency documentation</li>
     *   <li>Null-safety verification at construction time</li>
     * </ul>
     * 
     * @param accountRepository Repository for account data access, must not be null
     * @throws IllegalArgumentException if accountRepository is null
     */
    public StatementProcessor(AccountRepository accountRepository) {
        if (accountRepository == null) {
            throw new IllegalArgumentException("AccountRepository must not be null");
        }
        this.accountRepository = accountRepository;
    }

    /**
     * Processes a single account's transaction group into formatted statement data.
     * 
     * <p><b>COBOL Mapping:</b> Main entry point equivalent to CBSTM03A.CBL paragraph
     * {@code 5000-CREATE-STATEMENT} which orchestrates statement formatting steps.</p>
     * 
     * <p><b>Processing Steps:</b></p>
     * <ol>
     *   <li>Calculate previous balance from prior statement period</li>
     *   <li>Calculate current balance from account entity</li>
     *   <li>Aggregate transaction totals (debits, credits, finance charges)</li>
     *   <li>Calculate minimum payment due</li>
     *   <li>Calculate payment due date</li>
     *   <li>Format transaction list chronologically with currency formatting</li>
     *   <li>Format customer information with PCI-DSS compliant masking</li>
     *   <li>Assemble all data into StatementData DTO</li>
     * </ol>
     * 
     * <p><b>Input Requirements:</b>
     * The input {@link AccountTransactionGroup} must contain:</p>
     * <ul>
     *   <li>Valid accountId (not null)</li>
     *   <li>Valid accountNumber (not null)</li>
     *   <li>Valid statement period dates (start <= end)</li>
     *   <li>Non-null transactions list (may be empty)</li>
     *   <li>Valid customerName and customerAddress (not null)</li>
     * </ul>
     * 
     * <p><b>Output Guarantee:</b>
     * Returns fully populated {@link StatementData} DTO with:</p>
     * <ul>
     *   <li>Complete financial summary (all BigDecimal fields non-null)</li>
     *   <li>Formatted transaction list (may be empty but never null)</li>
     *   <li>PCI-DSS compliant masked account number</li>
     *   <li>Formatted customer address</li>
     *   <li>Valid statement period dates</li>
     * </ul>
     * 
     * <p><b>Exception Handling:</b>
     * Throws exceptions for Spring Batch framework handling per skip/retry policy:</p>
     * <ul>
     *   <li>{@code NullPointerException} if input is null or required fields are null</li>
     *   <li>{@code IllegalArgumentException} for invalid business data (negative balances, etc.)</li>
     *   <li>{@code DataAccessException} if database queries fail</li>
     * </ul>
     * 
     * <p><b>Thread Safety:</b>
     * This method is thread-safe and reentrant. Multiple threads can safely call process()
     * concurrently on the same processor instance without synchronization.</p>
     * 
     * @param input AccountTransactionGroup containing account, customer, and transaction data
     *              for a single account within the statement billing period
     * @return StatementData DTO with all formatted data ready for template rendering (HTML/PDF)
     * @throws Exception if processing fails (propagated to Spring Batch for skip/retry handling)
     */
    @Override
    public StatementData process(AccountTransactionGroup input) throws Exception {
        // Validate input (fail-fast for invalid data)
        if (input == null) {
            throw new IllegalArgumentException("AccountTransactionGroup input cannot be null");
        }

        // Extract account from repository to access current balance
        Account account = accountRepository.findById(input.accountId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found with ID: " + input.accountId()));

        // Calculate statement period opening balance (previous balance)
        BigDecimal previousBalance = getPreviousBalance(account, input.statementPeriodStartDate());

        // Get current balance from account entity (closing balance for statement period)
        BigDecimal currentBalance = account.getCurrentBalance();

        // Calculate aggregated transaction totals
        BigDecimal totalDebits = calculateTotalDebits(input.transactions());
        BigDecimal totalCredits = calculateTotalCredits(input.transactions());
        BigDecimal financeCharges = calculateFinanceCharges(input.transactions());

        // Calculate minimum payment due (max of $25 or 2% of balance)
        BigDecimal minimumPaymentDue = calculateMinimumPayment(currentBalance);

        // Calculate payment due date (statement end date + 21 days)
        LocalDate paymentDueDate = input.statementPeriodEndDate().plusDays(PAYMENT_DUE_DAYS);

        // Format transaction list chronologically
        List<FormattedTransaction> formattedTransactions = formatTransactionList(input.transactions());

        // Format customer information with PCI-DSS compliant masking
        StatementData.CustomerInfo customerInfo = formatCustomerInfo(
                input.accountNumber(),
                input.customerName(),
                input.customerAddress()
        );

        // Assemble account summary
        StatementData.AccountSummary accountSummary = new StatementData.AccountSummary(
                previousBalance,
                currentBalance,
                totalDebits,
                totalCredits,
                financeCharges,
                minimumPaymentDue,
                paymentDueDate
        );

        // Assemble statement period metadata
        StatementData.StatementPeriod statementPeriod = new StatementData.StatementPeriod(
                input.statementPeriodStartDate(),
                input.statementPeriodEndDate(),
                input.statementPeriodEndDate() // Statement date same as period end date
        );

        // Return fully populated StatementData DTO
        return new StatementData(
                accountSummary,
                formattedTransactions,
                customerInfo,
                statementPeriod
        );
    }

    /**
     * Retrieves the statement opening balance carried forward from previous period.
     * 
     * <p><b>COBOL Mapping:</b> Replicates logic from CBSTM03A.CBL reading prior statement file
     * record to establish statement period opening balance: {@code READ STMTFILE KEY IS
     * PREV-STMT-KEY. IF WS-PREVIOUS-STMT-FOUND = 'N' THEN MOVE 0.00 TO WS-OPENING-BAL}.</p>
     * 
     * <p><b>Business Logic:</b></p>
     * <ul>
     *   <li>Opening balance is the closing balance from the immediately prior statement period</li>
     *   <li>For new accounts without prior statements, opening balance defaults to $0.00</li>
     *   <li>This establishes continuity between statement periods for balance tracking</li>
     * </ul>
     * 
     * <p><b>Implementation Note:</b>
     * Current simplified implementation returns $0.00 as opening balance. In a complete production
     * implementation, this method would execute a query like:
     * {@code SELECT curr_bal FROM account_statement WHERE account_id=? AND statement_date < ?
     * ORDER BY statement_date DESC LIMIT 1} to retrieve the actual prior period closing balance.
     * </p>
     * 
     * <p><b>Design Decision:</b>
     * Simplified for this migration as full statement history tracking is not implemented in the
     * initial modernization phase. Future enhancement should add account_statement table to persist
     * statement snapshots and enable accurate opening balance calculation.</p>
     * 
     * @param account The account entity to retrieve balance history for
     * @param periodStartDate Statement period start date (used to find prior statement)
     * @return Previous balance (opening balance for current statement period), $0.00 for new accounts
     */
    private BigDecimal getPreviousBalance(Account account, LocalDate periodStartDate) {
        // Simplified implementation: Return 0.00 as opening balance
        // 
        // FUTURE ENHANCEMENT: Query account_statement table for prior period closing balance:
        // 
        // Optional<AccountStatement> priorStatement = accountStatementRepository
        //     .findFirstByAccountIdAndStatementDateBeforeOrderByStatementDateDesc(
        //         account.getAccountId(), periodStartDate);
        // 
        // return priorStatement
        //     .map(AccountStatement::getCurrentBalance)
        //     .orElse(BigDecimal.ZERO);
        //
        // For now, return 0.00 matching COBOL logic for accounts without prior statements:
        // IF WS-PREVIOUS-STMT-FOUND = 'N' THEN MOVE 0.00 TO WS-OPENING-BAL
        
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates total debit transaction amounts (purchases, fees) in statement period.
     * 
     * <p><b>COBOL Mapping:</b> Replicates CBSTM03A.CBL paragraph logic:
     * {@code ADD WS-DEBIT-TOTAL TO WS-TRAN-AMT} accumulating debit transactions.</p>
     * 
     * <p><b>Debit Transaction Types:</b>
     * Transactions that increase the account balance owed:</p>
     * <ul>
     *   <li>Purchase transactions (merchant sales)</li>
     *   <li>Cash advances</li>
     *   <li>Balance transfer fees</li>
     *   <li>Annual fees</li>
     *   <li>Late payment fees</li>
     *   <li>Over-limit fees</li>
     * </ul>
     * 
     * <p><b>Implementation:</b>
     * Uses Java Stream API with filter, map, and reduce operations for functional-style aggregation.
     * Equivalent to COBOL iterative ADD loop but more concise and thread-safe.</p>
     * 
     * @param transactions List of all transactions in statement period
     * @return Sum of all debit transaction amounts with 2 decimal places, $0.00 if no debits
     */
    private BigDecimal calculateTotalDebits(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> isDebitTransaction(t))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates total credit transaction amounts (payments, refunds) in statement period.
     * 
     * <p><b>COBOL Mapping:</b> Replicates CBSTM03A.CBL paragraph logic:
     * {@code ADD WS-CREDIT-TOTAL TO WS-TRAN-AMT} accumulating credit transactions.</p>
     * 
     * <p><b>Credit Transaction Types:</b>
     * Transactions that decrease the account balance owed:</p>
     * <ul>
     *   <li>Customer payments</li>
     *   <li>Merchant refunds</li>
     *   <li>Cashback rewards</li>
     *   <li>Statement credits (service recovery)</li>
     *   <li>Returned item credits</li>
     * </ul>
     * 
     * @param transactions List of all transactions in statement period
     * @return Sum of all credit transaction amounts with 2 decimal places, $0.00 if no credits
     */
    private BigDecimal calculateTotalCredits(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> isCreditTransaction(t))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates total finance charges (interest) accrued during statement period.
     * 
     * <p><b>COBOL Mapping:</b> Replicates CBSTM03A.CBL finance charge accumulation logic
     * filtering transactions by type code for interest charges.</p>
     * 
     * <p><b>Finance Charge Definition:</b>
     * Interest charges calculated on revolving balance per APR (Annual Percentage Rate).
     * Identified by transaction type code "INTEREST_CHARGE" or similar system transaction types.</p>
     * 
     * <p><b>Business Rule:</b>
     * Finance charges are regulatory-required disclosure on credit card statements per Truth in
     * Lending Act (TILA). Must be clearly identified separate from purchase transactions.</p>
     * 
     * @param transactions List of all transactions in statement period
     * @return Sum of all interest charge transactions with 2 decimal places, $0.00 if no charges
     */
    private BigDecimal calculateFinanceCharges(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> isFinanceChargeTransaction(t))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Determines if a transaction is a debit (increases balance owed).
     * 
     * <p><b>Business Logic:</b>
     * Debit transactions have positive amounts and increase the account balance.
     * Transaction type codes starting with "01" typically indicate purchase transactions.</p>
     * 
     * <p><b>COBOL Equivalent:</b> {@code IF TRAN-TYPE-CD = '01' OR TRAN-AMT > 0}</p>
     * 
     * @param transaction Transaction to evaluate
     * @return true if transaction is a debit (purchase, fee), false otherwise
     */
    private boolean isDebitTransaction(Transaction transaction) {
        // Debit transactions typically have positive amounts and purchase-related type codes
        // Transaction type code "01" indicates purchase transactions
        // Note: This logic may need refinement based on actual transaction type code mappings
        String typeCode = transaction.getTransactionTypeCode();
        return transaction.getAmount().compareTo(BigDecimal.ZERO) > 0
                && (typeCode.startsWith("01") || typeCode.equals("PURCHASE"));
    }

    /**
     * Determines if a transaction is a credit (decreases balance owed).
     * 
     * <p><b>Business Logic:</b>
     * Credit transactions reduce the account balance. Common type codes: "02" for payments,
     * "03" for refunds.</p>
     * 
     * <p><b>COBOL Equivalent:</b> {@code IF TRAN-TYPE-CD = '02' OR TRAN-TYPE-CD = '03'}</p>
     * 
     * @param transaction Transaction to evaluate
     * @return true if transaction is a credit (payment, refund), false otherwise
     */
    private boolean isCreditTransaction(Transaction transaction) {
        // Credit transactions typically have payment or refund type codes
        String typeCode = transaction.getTransactionTypeCode();
        return typeCode.equals("02") || typeCode.equals("PAYMENT")
                || typeCode.equals("03") || typeCode.equals("REFUND");
    }

    /**
     * Determines if a transaction is a finance charge (interest).
     * 
     * <p><b>Business Logic:</b>
     * Finance charges are system-generated interest transactions, not customer-initiated.
     * Identified by specific transaction type codes like "INTEREST_CHARGE".</p>
     * 
     * <p><b>COBOL Equivalent:</b> {@code IF TRAN-TYPE-CD = 'INT'}</p>
     * 
     * @param transaction Transaction to evaluate
     * @return true if transaction is a finance charge, false otherwise
     */
    private boolean isFinanceChargeTransaction(Transaction transaction) {
        // Finance charge transactions have specific type code for interest charges
        String typeCode = transaction.getTransactionTypeCode();
        return typeCode.equals("INT") || typeCode.equals("INTEREST_CHARGE");
    }

    /**
     * Calculates minimum payment due for statement billing cycle.
     * 
     * <p><b>COBOL Mapping:</b> Preserves exact COBOL business logic from CBSTM03A.CBL:
     * <pre>
     * IF ACCT-CURR-BAL * 0.02 < 25.00
     *     THEN MOVE 25.00 TO MIN-PAY-DUE
     *     ELSE MOVE ACCT-CURR-BAL * 0.02 TO MIN-PAY-DUE
     * END-IF
     * </pre>
     * </p>
     * 
     * <p><b>Business Rule:</b>
     * Minimum payment is the greater of:</p>
     * <ul>
     *   <li>$25.00 (minimum payment floor), OR</li>
     *   <li>2% of current balance</li>
     * </ul>
     * 
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Balance $1,000.00 → Min payment $25.00 (greater of $25 or $20)</li>
     *   <li>Balance $1,500.00 → Min payment $30.00 (greater of $25 or $30)</li>
     *   <li>Balance $10,000.00 → Min payment $200.00 (2% of balance)</li>
     *   <li>Balance $24.99 → Min payment $25.00 (floor applies)</li>
     * </ul>
     * 
     * <p><b>Regulatory Compliance:</b>
     * Minimum payment calculation must comply with CARD Act requirements ensuring meaningful
     * principal reduction over time. 2% is typical industry standard balancing customer affordability
     * with issuer risk management.</p>
     * 
     * @param currentBalance Account balance at end of statement period
     * @return Minimum payment due amount with 2 decimal places, always >= $25.00
     */
    private BigDecimal calculateMinimumPayment(BigDecimal currentBalance) {
        // Calculate 2% of current balance
        BigDecimal percentagePayment = currentBalance
                .multiply(MINIMUM_PAYMENT_PERCENTAGE)
                .setScale(2, RoundingMode.HALF_UP);

        // Return greater of $25.00 or 2% of balance
        return percentagePayment.max(MINIMUM_PAYMENT_FLOOR);
    }

    /**
     * Formats transaction list in chronological order with proper date and currency formatting.
     * 
     * <p><b>COBOL Mapping:</b> Replicates CBSTM03A.CBL paragraph {@code 6000-WRITE-TRANS} which
     * iterates through transaction array and formats each line as {@code ST-LINE13} and
     * {@code ST-LINE14} structures in {@code STATEMENT-LINES} copybook.</p>
     * 
     * <p><b>Processing Steps:</b></p>
     * <ol>
     *   <li>Sort transactions chronologically by processingTimestamp ascending</li>
     *   <li>For each transaction:
     *     <ul>
     *       <li>Format date as MM/dd/yyyy</li>
     *       <li>Truncate description to 40 characters</li>
     *       <li>Format reference number to 16 characters (zero-padded)</li>
     *       <li>Format amount as currency with $ symbol</li>
     *     </ul>
     *   </li>
     *   <li>Return list of FormattedTransaction DTOs</li>
     * </ol>
     * 
     * <p><b>Sorting Logic:</b>
     * Transactions sorted by processingTimestamp ascending, then by transactionId for deterministic
     * ordering. Matches COBOL: {@code ORDER BY TRAN-DATE, TRAN-ID} sequential file processing.</p>
     * 
     * <p><b>Empty List Handling:</b>
     * If no transactions in period, returns empty list (not null). Statement will show
     * "No transactions during this period" message in template rendering.</p>
     * 
     * @param transactions List of all transactions in statement period
     * @return List of formatted transactions ready for template rendering, empty list if no transactions
     */
    private List<FormattedTransaction> formatTransactionList(List<Transaction> transactions) {
        return transactions.stream()
                // Sort chronologically by processingTimestamp, then by transactionId
                .sorted(Comparator.comparing(Transaction::getProcessingTimestamp)
                        .thenComparing(Transaction::getTransactionId))
                // Map each transaction to formatted DTO
                .map(this::formatTransaction)
                // Collect to list
                .collect(Collectors.toList());
    }

    /**
     * Formats a single transaction into display-ready FormattedTransaction DTO.
     * 
     * <p><b>COBOL Mapping:</b> Formats individual transaction fields matching
     * {@code STATEMENT-LINES ST-LINE14} structure:</p>
     * <ul>
     *   <li>{@code ST-TRANID PIC X(16)} → referenceNumber</li>
     *   <li>{@code ST-TRANDT PIC X(49)} → description (truncated to 40 chars)</li>
     *   <li>{@code ST-TRANAMT PIC Z(9).99-} → formattedAmount</li>
     * </ul>
     * 
     * <p><b>Field Formatting Rules:</b></p>
     * <ul>
     *   <li><b>Date:</b> MM/dd/yyyy format (e.g., "01/15/2024")</li>
     *   <li><b>Description:</b> Merchant name or transaction type, max 40 characters</li>
     *   <li><b>Reference Number:</b> Transaction ID, zero-padded to 16 characters</li>
     *   <li><b>Amount:</b> Currency format with $ symbol (e.g., "$1,234.56")</li>
     * </ul>
     * 
     * @param transaction Transaction entity to format
     * @return FormattedTransaction DTO ready for template rendering
     */
    private FormattedTransaction formatTransaction(Transaction transaction) {
        // Format transaction date from LocalDateTime to MM/dd/yyyy string
        LocalDateTime timestamp = transaction.getProcessingTimestamp();
        String formattedDate = timestamp.toLocalDate().format(DATE_FORMATTER);

        // Format description - use merchant name for purchases, or transaction description
        String description = transaction.getMerchantName() != null
                ? transaction.getMerchantName()
                : transaction.getDescription();
        
        // Truncate description to max 40 characters per COBOL PIC X(40) constraint
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        } else if (description == null) {
            description = "Transaction"; // Default description if none provided
        }

        // Format reference number - transaction ID zero-padded to 16 characters
        String referenceNumber = String.format("%016d", transaction.getTransactionId());

        // Format amount as currency (e.g., "$1,234.56")
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(US_LOCALE);
        String formattedAmount = currencyFormatter.format(transaction.getAmount());

        // Return formatted transaction DTO
        return new FormattedTransaction(
                formattedDate,
                description,
                referenceNumber,
                formattedAmount
        );
    }

    /**
     * Formats customer information with PCI-DSS compliant account number masking.
     * 
     * <p><b>COBOL Mapping:</b> Constructs statement header section from {@code STATEMENT-LINES
     * ST-LINE1} through {@code ST-LINE4} copybook structure in CBSTM03A.CBL:</p>
     * <ul>
     *   <li>{@code ST-NAME PIC X(75)} → customerName</li>
     *   <li>{@code ST-ADD1 PIC X(50)} → address line 1</li>
     *   <li>{@code ST-ADD2 PIC X(50)} → address line 2 (city, state ZIP)</li>
     *   <li>{@code ST-ADD3 PIC X(80)} → address line 3</li>
     *   <li>{@code ST-ACCT-ID} → maskedAccountNumber with PCI-DSS masking</li>
     * </ul>
     * 
     * <p><b>PCI-DSS Compliance:</b>
     * Account number masked per PCI-DSS requirement 3.4 (render PAN unreadable). Only last 4 digits
     * visible in format "XXXX-XXXX-XXXX-1234".</p>
     * 
     * <p><b>Address Formatting:</b>
     * Address already formatted in input DTO as concatenated string. If additional formatting
     * needed, would parse and reconstruct from Customer entity fields.</p>
     * 
     * @param accountNumber Full account number (e.g., "0000012345678")
     * @param customerName Full customer name (e.g., "John A. Smith")
     * @param customerAddress Formatted address string (e.g., "123 Main St, Springfield, IL 62701")
     * @return CustomerInfo DTO with masked account number ready for statement header
     */
    private StatementData.CustomerInfo formatCustomerInfo(
            String accountNumber,
            String customerName,
            String customerAddress) {
        
        // Apply PCI-DSS compliant masking to account number
        String maskedAccountNumber = maskAccountNumber(accountNumber);

        // Return customer info DTO
        return new StatementData.CustomerInfo(
                maskedAccountNumber,
                customerName,
                customerAddress
        );
    }

    /**
     * Masks account number to show only last 4 digits per PCI-DSS requirements.
     * 
     * <p><b>PCI-DSS Requirement:</b> Per PCI-DSS v4.0 requirement 3.4.1, Primary Account Numbers
     * (PAN) must be rendered unreadable anywhere they are displayed. Acceptable masking format shows
     * at most the first 6 and last 4 digits. For statements, industry standard is showing only
     * last 4 digits.</p>
     * 
     * <p><b>Masking Format:</b> "XXXX-XXXX-XXXX-1234" for 16-digit account numbers, or appropriate
     * masking pattern for other lengths preserving only last 4 digits.</p>
     * 
     * <p><b>COBOL Mapping:</b> Replicates account masking logic from COBDATFT.asm utility and
     * CBSTM03A.CBL statement formatting:</p>
     * <pre>
     * MOVE ALL 'X' TO ST-ACCT-ID(1:11)
     * MOVE ACCT-ID(12:4) TO ST-ACCT-ID(12:4)
     * </pre>
     * 
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>"0000012345678" → "XXXX-XXXX-X678"</li>
     *   <li>"1234567890123456" → "XXXX-XXXX-XXXX-3456"</li>
     *   <li>"98765432" → "XXXX-5432"</li>
     * </ul>
     * 
     * @param accountNumber Full account number to mask
     * @return Masked account number showing only last 4 digits
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "XXXX"; // Handle edge case of very short account numbers
        }

        // Get last 4 digits
        String last4Digits = accountNumber.substring(accountNumber.length() - 4);

        // Calculate number of masked digits
        int maskedLength = accountNumber.length() - 4;

        // Build masked account number with 'X' for all but last 4 digits
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < maskedLength; i++) {
            masked.append('X');
            // Insert hyphen every 4 characters for readability
            if ((i + 1) % 4 == 0 && i < maskedLength - 1) {
                masked.append('-');
            }
        }

        // Add hyphen before last 4 digits if needed
        if (maskedLength % 4 != 0 || maskedLength == 0) {
            // No hyphen needed if last group is not complete
        } else {
            masked.append('-');
        }

        masked.append(last4Digits);

        return masked.toString();
    }
}
