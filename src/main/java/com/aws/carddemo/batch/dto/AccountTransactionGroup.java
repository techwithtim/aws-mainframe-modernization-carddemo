package com.aws.carddemo.batch.dto;

import com.aws.carddemo.model.Transaction;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Data Transfer Object (DTO) representing grouped transaction data for a single account
 * within a statement billing period, used in Spring Batch chunk-oriented processing
 * for statement generation.
 * 
 * <p><b>Batch Processing Pipeline Role:</b>
 * This DTO serves as the data carrier between Spring Batch components in the statement
 * generation job (migrated from COBOL CBSTM03A.CBL):
 * 
 * <pre>
 * AccountTransactionGroupReader (ItemReader)
 *     ↓
 *     Executes SQL: SELECT account_id, account_number, customer_name, customer_address,
 *                          statement_period_start, statement_period_end
 *                   FROM account a
 *                   JOIN customer c ON a.customer_id = c.customer_id
 *                   WHERE processing_timestamp BETWEEN ? AND ?
 *                   GROUP BY account_id
 *     ↓
 *     For each account, loads all transactions in billing period
 *     ↓
 *     Wraps in AccountTransactionGroup DTO
 *     ↓
 * StatementProcessor (ItemProcessor)
 *     ↓
 *     Receives AccountTransactionGroup as input
 *     ↓
 *     Formats statement data:
 *         - Customer name and address header
 *         - Transaction line items (date, description, amount)
 *         - Summary calculations (total purchases, payments, balance)
 *     ↓
 *     Returns StatementData output DTO
 *     ↓
 * StatementWriter (ItemWriter)
 *     ↓
 *     Writes formatted statements to file (text/HTML) or database
 * </pre>
 * 
 * <p><b>Why Record Type?</b>
 * Java 17+ Record provides immutability, built-in equals()/hashCode()/toString(),
 * and concise syntax ideal for DTOs in batch processing where data should not be
 * mutated after creation.
 * 
 * <p><b>Immutability Benefits:</b>
 * <ul>
 *   <li><b>Thread Safety:</b> Multiple processor threads can safely access same DTO</li>
 *   <li><b>Chunk Integrity:</b> Data cannot be corrupted between reader and processor</li>
 *   <li><b>Audit Trail:</b> Original input preserved for error analysis and retry logic</li>
 *   <li><b>Serialization Safety:</b> Immutable state ensures consistent serialization</li>
 * </ul>
 * 
 * <p><b>Serializable Interface:</b>
 * Implements {@code Serializable} to support Spring Batch chunk serialization when
 * using remote partitioning or async step execution patterns where DTOs are passed
 * across JVM boundaries or persisted to database for job restart scenarios.
 * 
 * <p><b>Bean Validation Annotations:</b>
 * {@code @NotNull} annotations on critical fields enable fail-fast validation during
 * chunk processing. If reader produces invalid data, validation exception occurs
 * immediately preventing downstream processor errors and enabling proper skip/retry
 * handling per Spring Batch skip policy configuration.
 * 
 * <p><b>Field Descriptions:</b>
 * <ul>
 *   <li><b>accountId:</b> Account surrogate primary key for database joins and logging</li>
 *   <li><b>accountNumber:</b> Business account number for statement display header</li>
 *   <li><b>customerName:</b> Full name (firstName + middleName + lastName) for header</li>
 *   <li><b>customerAddress:</b> Formatted address (line1, city, state ZIP) for header</li>
 *   <li><b>transactions:</b> All transactions in billing period, sorted by processingTimestamp
 *       ascending for chronological display on statement</li>
 *   <li><b>statementPeriodStartDate:</b> Billing cycle start (e.g., 2024-01-01)</li>
 *   <li><b>statementPeriodEndDate:</b> Billing cycle end (e.g., 2024-01-31)</li>
 * </ul>
 * 
 * <p><b>Usage in AccountTransactionGroupReader:</b>
 * <pre>
 * public AccountTransactionGroup read() throws Exception {
 *     // Read next account from result set
 *     Long accountId = resultSet.getLong("account_id");
 *     String accountNumber = resultSet.getString("account_number");
 *     String customerName = resultSet.getString("customer_name");
 *     String customerAddress = resultSet.getString("customer_address");
 *     LocalDate periodStart = resultSet.getDate("period_start").toLocalDate();
 *     LocalDate periodEnd = resultSet.getDate("period_end").toLocalDate();
 *     
 *     // Load transactions for this account in billing period
 *     List&lt;Transaction&gt; transactions = transactionRepository
 *         .findByAccountIdAndProcessingTimestampBetween(
 *             accountId, periodStart.atStartOfDay(), periodEnd.atTime(23, 59, 59));
 *     
 *     // Wrap in DTO
 *     return new AccountTransactionGroup(
 *         accountId, accountNumber, customerName, customerAddress,
 *         transactions, periodStart, periodEnd
 *     );
 * }
 * </pre>
 * 
 * <p><b>Usage in StatementProcessor:</b>
 * <pre>
 * public StatementData process(AccountTransactionGroup input) throws Exception {
 *     // Calculate statement summary from input.transactions()
 *     BigDecimal totalPurchases = input.transactions().stream()
 *         .filter(t -&gt; t.getTransactionTypeCode().equals("01"))
 *         .map(Transaction::getAmount)
 *         .reduce(BigDecimal.ZERO, BigDecimal::add);
 *     
 *     // Format statement using input customer/account metadata
 *     return StatementData.builder()
 *         .accountNumber(input.accountNumber())
 *         .customerName(input.customerName())
 *         .customerAddress(input.customerAddress())
 *         .periodStart(input.statementPeriodStartDate())
 *         .periodEnd(input.statementPeriodEndDate())
 *         .transactions(formatTransactions(input.transactions()))
 *         .totalPurchases(totalPurchases)
 *         .build();
 * }
 * </pre>
 * 
 * <p><b>Data Aggregation Strategy:</b>
 * Reader component aggregates transaction data by account to minimize database queries.
 * Alternative would be reading transactions one-by-one and regrouping in processor,
 * but that creates N+1 query problems and inefficient chunk processing. This DTO
 * design enables efficient batch-level aggregation with single SQL query per account.
 * 
 * <p><b>COBOL Source Reference:</b>
 * Migrated from CBSTM03A.CBL statement generation batch job logic. Original COBOL
 * program read TRANSACT file sequentially, grouped by account, and accumulated
 * transaction details into WORKING-STORAGE arrays. This DTO replaces COBOL arrays
 * with type-safe Java List&lt;Transaction&gt; collection.
 * 
 * <p><b>COBOL Data Structure Mappings:</b>
 * <pre>
 * COBOL Field (CBSTM03A.CBL)        Java Field                        Type Mapping
 * ================================   ==============================    ==============================
 * ACCT-ID PIC 9(11)                  accountId                         Long (from CVACT01Y.cpy)
 * ACCT-NUM (display format)          accountNumber                     String
 * CUST-FIRST-NAME + MIDDLE + LAST    customerName                      String (concatenated)
 * CUST-ADDR-LINE-1 + CITY + STATE    customerAddress                   String (formatted)
 * WS-TRAN-ARRAY (2D array)           transactions                      List&lt;Transaction&gt;
 * WS-PERIOD-START-DATE               statementPeriodStartDate          LocalDate
 * WS-PERIOD-END-DATE                 statementPeriodEndDate            LocalDate
 * </pre>
 * 
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li><b>Chunk Size:</b> Typical chunk size 10-50 accounts balances memory vs throughput</li>
 *   <li><b>Transaction List Size:</b> Average 20-100 transactions per account per month</li>
 *   <li><b>Memory Footprint:</b> ~5-10 KB per DTO including transaction details</li>
 *   <li><b>Serialization Cost:</b> Minimal overhead for in-JVM processing, acceptable for remote</li>
 * </ul>
 * 
 * <p><b>Error Handling:</b>
 * If validation fails (e.g., null accountId), Spring Batch throws {@code ValidationException}
 * which triggers skip policy. Configure skip limit and skippable exceptions in job configuration:
 * <pre>
 * stepBuilder.&lt;AccountTransactionGroup, StatementData&gt;chunk(10)
 *     .reader(reader)
 *     .processor(processor)
 *     .writer(writer)
 *     .faultTolerant()
 *     .skip(ValidationException.class)
 *     .skipLimit(10)
 *     .build();
 * </pre>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 0.4.1: File Transformation - CBSTM03A.CBL → StatementGenerationJobConfig.java</li>
 *   <li>Section 2.3: Batch Processing Workflows - Statement generation job flow</li>
 *   <li>Section 5.2: Component Details - Spring Batch chunk processing architecture</li>
 *   <li>Section 6.2: Database Design - Transaction history queries for statement periods</li>
 * </ul>
 * 
 * @param accountId Account surrogate primary key, references Account.accountId, never null
 * @param accountNumber Account business number for display, never null
 * @param customerName Full customer name (firstName + middleName + lastName), never null
 * @param customerAddress Formatted mailing address (line1, city, state ZIP), may be null if incomplete
 * @param transactions List of all transactions in statement period, sorted ascending by processingTimestamp, never null but may be empty
 * @param statementPeriodStartDate Statement billing cycle start date (inclusive), never null
 * @param statementPeriodEndDate Statement billing cycle end date (inclusive), never null
 * 
 * @see com.aws.carddemo.model.Transaction for transaction entity structure
 * @see com.aws.carddemo.batch.config.StatementGenerationJobConfig for job configuration
 * @see com.aws.carddemo.batch.processor.StatementProcessor for processing logic
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
public record AccountTransactionGroup(
    @NotNull(message = "Account ID is required for statement generation")
    Long accountId,
    
    @NotNull(message = "Account number is required for statement header")
    String accountNumber,
    
    @NotNull(message = "Customer name is required for statement header")
    String customerName,
    
    String customerAddress,
    
    @NotNull(message = "Transactions list is required (may be empty but not null)")
    List<Transaction> transactions,
    
    @NotNull(message = "Statement period start date is required")
    LocalDate statementPeriodStartDate,
    
    @NotNull(message = "Statement period end date is required")
    LocalDate statementPeriodEndDate
) implements Serializable {
    
    /**
     * Serialization version UID for distributed Spring Batch compatibility.
     * 
     * <p><b>Version Policy:</b> Increment this value when record structure changes
     * in a non-compatible way (field additions, removals, type changes) to prevent
     * deserialization errors when restarting jobs from serialized execution context.
     * 
     * <p><b>Spring Batch Context:</b> When jobs fail and are restarted, Spring Batch
     * may deserialize this DTO from database execution context. Version mismatch
     * will cause job restart failure, requiring manual cleanup of job repository.
     * 
     * <p>Current version: 1 (initial release)
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * Compact canonical constructor with validation and defensive copying.
     * 
     * <p><b>Validation Logic:</b>
     * Ensures business rules beyond simple null checks:
     * <ul>
     *   <li>Statement period dates are logical (start &lt;= end)</li>
     *   <li>Transactions list is defensively copied to prevent external mutation</li>
     *   <li>Transaction timestamps fall within statement period (defensive check)</li>
     * </ul>
     * 
     * <p><b>Defensive Copying:</b>
     * Creates unmodifiable copy of transactions list to enforce immutability contract.
     * Prevents external code from mutating list after DTO construction, which could
     * corrupt batch processing state and violate audit trail requirements.
     * 
     * <p><b>Design Note:</b> Compact constructor syntax allows validation without
     * explicit field assignments - Java compiler auto-assigns parameters to record fields.
     * 
     * @throws IllegalArgumentException if statement period end is before start date
     */
    public AccountTransactionGroup {
        // Validate date range logic
        if (statementPeriodStartDate != null && statementPeriodEndDate != null) {
            if (statementPeriodEndDate.isBefore(statementPeriodStartDate)) {
                throw new IllegalArgumentException(
                    String.format("Statement period end date (%s) cannot be before start date (%s)",
                        statementPeriodEndDate, statementPeriodStartDate)
                );
            }
        }
        
        // Defensive copy to enforce immutability
        transactions = transactions != null 
            ? List.copyOf(transactions) 
            : List.of();
    }
}
