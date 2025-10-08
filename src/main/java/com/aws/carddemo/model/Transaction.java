package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing posted credit card transaction records.
 * Migrated from: app/cpy/CVTRA05Y.cpy (TRAN-RECORD, 350-byte COBOL structure)
 * 
 * <p>This entity maintains a complete, immutable audit trail of all transaction activity
 * including merchant details, authorization timestamps, and financial amounts with exact
 * decimal precision required for financial calculations and regulatory compliance.
 * 
 * <p><b>Business Key Design:</b>
 * <ul>
 *   <li><b>Surrogate Key:</b> {@code transactionId} - BIGINT IDENTITY auto-generated primary key</li>
 *   <li><b>Business Key:</b> {@code transactionNumber} - VARCHAR(16) UNIQUE preserving COBOL TRAN-ID</li>
 * </ul>
 * 
 * <p><b>Transaction Processing Model:</b>
 * <ul>
 *   <li><b>Original Timestamp:</b> Merchant authorization time when transaction was initiated</li>
 *   <li><b>Processing Timestamp:</b> System processing time when transaction was posted to account</li>
 *   <li><b>Dual Timestamp Audit:</b> Enables reconciliation between merchant auth and posting batch</li>
 * </ul>
 * 
 * <p><b>Financial Precision:</b>
 * <ul>
 *   <li>Amount field uses {@code BigDecimal} with NUMERIC(11,2) database precision</li>
 *   <li>Mapped from COBOL PIC S9(09)V99 preserving exact decimal arithmetic</li>
 *   <li>Validation: {@code @DecimalMin("0.01")} ensures positive transactions only</li>
 *   <li>Database CHECK constraint {@code chk_positive_amount > 0} enforces data integrity</li>
 * </ul>
 * 
 * <p><b>PCI-DSS Compliance (Critical Security Requirement):</b>
 * <ul>
 *   <li>{@code cardNumber} field marked {@code @ToString.Exclude} preventing log exposure</li>
 *   <li>{@code getCardNumberMasked()} provides safe display format: "************1234"</li>
 *   <li>Sensitive data never appears in toString(), debug output, or exception traces</li>
 *   <li>Complies with Section 0.8.1 Critical Directive #3: PCI-DSS card masking</li>
 * </ul>
 * 
 * <p><b>Insert-Only Pattern:</b>
 * Transactions are immutable once posted - no updates or deletes allowed. This preserves
 * complete audit trail for financial reconciliation and regulatory compliance. Repository
 * layer provides only save() (insert) operations, not update or delete methods.
 * 
 * <p><b>Foreign Key Relationships:</b>
 * <ul>
 *   <li><b>Account:</b> {@code @ManyToOne LAZY} to parent Account entity (RESTRICT on delete)</li>
 *   <li><b>Type Code:</b> {@code transactionTypeCode} references transaction_type(type_code)</li>
 *   <li><b>Category Code:</b> {@code transactionCategoryCode} references transaction_category(category_code)</li>
 * </ul>
 * 
 * <p><b>Performance Indexes:</b>
 * <ul>
 *   <li><b>idx_transaction_account_date:</b> (account_id, processing_timestamp DESC) for pagination</li>
 *   <li><b>idx_transaction_card:</b> (card_number) for fraud detection queries</li>
 *   <li><b>idx_transaction_merchant:</b> (merchant_id) for merchant reconciliation</li>
 *   <li><b>uk_transaction_number:</b> UNIQUE constraint on business key transactionNumber</li>
 * </ul>
 * 
 * <p><b>COBOL Field Mappings:</b>
 * <pre>
 * COBOL Field (CVTRA05Y.cpy)     Java Field                    Type Mapping
 * ============================    =========================     ================================
 * TRAN-ID PIC X(16)               transactionNumber             VARCHAR(16) UNIQUE business key
 * TRAN-TYPE-CD PIC X(02)          transactionTypeCode           VARCHAR(2) FK to type table
 * TRAN-CAT-CD PIC 9(04)           transactionCategoryCode       VARCHAR(4) FK to category table
 * TRAN-SOURCE PIC X(10)           transactionSource             VARCHAR(10)
 * TRAN-DESC PIC X(100)            description                   VARCHAR(100)
 * TRAN-AMT PIC S9(09)V99          amount                        NUMERIC(11,2) BigDecimal
 * TRAN-MERCHANT-ID PIC 9(09)      merchantId                    VARCHAR(9)
 * TRAN-MERCHANT-NAME PIC X(50)    merchantName                  VARCHAR(50)
 * TRAN-MERCHANT-CITY PIC X(50)    merchantCity                  VARCHAR(50)
 * TRAN-MERCHANT-ZIP PIC X(10)     merchantZip                   VARCHAR(10) with ZIP regex
 * TRAN-CARD-NUM PIC X(16)         cardNumber                    VARCHAR(16) PCI-DSS masked
 * TRAN-ORIG-TS PIC X(26)          originalTimestamp             TIMESTAMP merchant auth time
 * TRAN-PROC-TS PIC X(26)          processingTimestamp           TIMESTAMP system processing time
 * </pre>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 6.2.2.1: Transaction History Table - Dual timestamps for audit trail</li>
 *   <li>Section 0.8.3: Data Type Mapping - COBOL PIC S9(09)V99 → BigDecimal precision</li>
 *   <li>Section 0.8.1: Critical Directive #3 - PCI-DSS compliance with card masking</li>
 *   <li>Section 0.4.1: File Transformation - CVTRA05Y.cpy → Transaction.java mapping</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b>
 * <pre>
 * Transaction txn = Transaction.builder()
 *     .transactionNumber("TXN20240101001234")
 *     .account(account)
 *     .transactionTypeCode("01")
 *     .transactionCategoryCode("1001")
 *     .amount(new BigDecimal("125.50"))
 *     .cardNumber("4111111111111234")
 *     .merchantName("ACME Store")
 *     .originalTimestamp(LocalDateTime.parse("2024-01-01T10:15:30"))
 *     .processingTimestamp(LocalDateTime.now())
 *     .build();
 * transactionRepository.save(txn);  // Insert only, no updates
 * 
 * // Safe display format (PCI-DSS compliant)
 * String maskedCard = txn.getCardNumberMasked();  // Returns "************1234"
 * </pre>
 * 
 * @see BaseEntity for audit fields (createdAt, updatedAt, version)
 * @see Account for parent account relationship
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Entity
@Table(
    name = "transaction",
    indexes = {
        @Index(name = "idx_transaction_account_date", columnList = "account_id, processing_timestamp"),
        @Index(name = "idx_transaction_card", columnList = "card_number"),
        @Index(name = "idx_transaction_merchant", columnList = "merchant_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_transaction_number", columnNames = {"transaction_number"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true, exclude = {"account", "cardNumber"})
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Transaction extends BaseEntity implements Serializable {

    /**
     * Serialization version UID for distributed cache compatibility.
     * Updated when class structure changes in a non-compatible way.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Synthetic surrogate primary key auto-generated by database.
     * 
     * <p><b>Generation Strategy:</b> IDENTITY strategy delegates sequence generation
     * to PostgreSQL SERIAL or BIGSERIAL column type, providing optimal performance
     * for high-volume transaction inserts.
     * 
     * <p><b>Equality Contract:</b> Marked {@code @EqualsAndHashCode.Include} to ensure
     * JPA entity equality is based solely on primary key, preventing Hibernate session
     * cache issues when entity is detached/reattached across transactions.
     * 
     * <p><b>COBOL Mapping:</b> N/A (new field for JPA surrogate key requirements)
     * 
     * @return the transaction surrogate primary key, null for transient entities
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id", nullable = false)
    @EqualsAndHashCode.Include
    private Long transactionId;

    /**
     * Business transaction number - the original COBOL TRAN-ID business key.
     * Unique identifier for transactions visible to external systems and reports.
     * 
     * <p><b>Format:</b> Typically 16-character alphanumeric string like "TXN20240101001234"
     * combining date prefix with sequence number for human readability.
     * 
     * <p><b>Uniqueness:</b> UNIQUE constraint {@code uk_transaction_number} enforces
     * business key uniqueness at database level, preventing duplicate transaction posting.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-ID PIC X(16)
     * 
     * @return the business transaction number, never null for persisted transactions
     */
    @Column(name = "transaction_number", nullable = false, length = 16, unique = true)
    @NotBlank(message = "Transaction number is required")
    @Size(min = 1, max = 16, message = "Transaction number must be between 1 and 16 characters")
    private String transactionNumber;

    /**
     * Parent account to which this transaction is posted.
     * 
     * <p><b>Fetch Strategy:</b> LAZY fetch to avoid N+1 query problems when loading
     * transaction lists. Use {@code JOIN FETCH account} in JPQL when account details
     * are needed immediately.
     * 
     * <p><b>Foreign Key:</b> {@code fk_transaction_account} references account(account_id)
     * with RESTRICT on delete - accounts with transactions cannot be deleted.
     * 
     * <p><b>Cascade:</b> No cascade operations - transaction lifecycle is independent
     * of account lifecycle (insert-only pattern).
     * 
     * <p><b>ToString Exclusion:</b> Marked {@code @ToString.Exclude} to prevent
     * recursive toString() calls and potential LazyInitializationException.
     * 
     * <p><b>COBOL Mapping:</b> Implicit relationship via ACCT-ID in batch processing
     * 
     * @return the parent account, never null for valid transactions
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transaction_account"))
    @NotNull(message = "Account is required")
    private Account account;

    /**
     * Transaction type code referencing the transaction_type reference table.
     * 
     * <p><b>Reference Data:</b> Foreign key to {@code transaction_type(type_code)}
     * defining transaction categories like "01" (Purchase), "02" (Cash Advance),
     * "03" (Payment), etc.
     * 
     * <p><b>Validation:</b> 2-character code validated by database foreign key
     * constraint {@code fk_transaction_type}.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-TYPE-CD PIC X(02)
     * 
     * @return the transaction type code, never null
     */
    @Column(name = "transaction_type_code", nullable = false, length = 2)
    @NotBlank(message = "Transaction type code is required")
    @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
    private String transactionTypeCode;

    /**
     * Transaction category code referencing the transaction_category reference table.
     * 
     * <p><b>Reference Data:</b> Foreign key to {@code transaction_category(category_code)}
     * defining detailed categories like "1001" (Groceries), "1002" (Gas Stations),
     * "2001" (Restaurants), etc.
     * 
     * <p><b>Reporting:</b> Category codes enable spending analysis and category balance
     * tracking for credit card statements.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-CAT-CD PIC 9(04)
     * 
     * @return the transaction category code, never null
     */
    @Column(name = "transaction_category_code", nullable = false, length = 4)
    @NotBlank(message = "Transaction category code is required")
    @Size(min = 4, max = 4, message = "Transaction category code must be exactly 4 characters")
    private String transactionCategoryCode;

    /**
     * Transaction source identifier indicating origination channel.
     * 
     * <p><b>Valid Values:</b> Typical sources include "ONLINE", "POS", "ATM", "MOBILE",
     * "PHONE", "MAIL", etc.
     * 
     * <p><b>Analytics:</b> Used for channel analysis and fraud detection patterns.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-SOURCE PIC X(10)
     * 
     * @return the transaction source, may be null for legacy data
     */
    @Column(name = "transaction_source", length = 10)
    @Size(max = 10, message = "Transaction source cannot exceed 10 characters")
    private String transactionSource;

    /**
     * Descriptive text providing transaction details for statement display.
     * 
     * <p><b>Format:</b> Human-readable description like "PURCHASE AT ACME STORE CITY ST"
     * combining merchant name, city, and state for customer statement clarity.
     * 
     * <p><b>Required Field:</b> Always populated for customer-facing statements and
     * transaction history displays.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-DESC PIC X(100)
     * 
     * @return the transaction description, never null
     */
    @Column(name = "description", nullable = false, length = 100)
    @NotBlank(message = "Transaction description is required")
    @Size(max = 100, message = "Transaction description cannot exceed 100 characters")
    private String description;

    /**
     * Transaction amount in US dollars with exact decimal precision.
     * 
     * <p><b>Financial Precision:</b>
     * <ul>
     *   <li><b>Java Type:</b> {@code BigDecimal} for exact decimal arithmetic without rounding errors</li>
     *   <li><b>Database Type:</b> NUMERIC(11,2) allowing 9 integer digits + 2 decimal places</li>
     *   <li><b>COBOL Mapping:</b> PIC S9(09)V99 COMP-3 packed decimal field</li>
     *   <li><b>Max Value:</b> 999,999,999.99 (sufficient for individual transaction amounts)</li>
     * </ul>
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Digits(integer=9, fraction=2)} enforces precision limits</li>
     *   <li>{@code @DecimalMin("0.01")} ensures positive amounts only</li>
     *   <li>Database CHECK constraint {@code chk_positive_amount > 0} provides data integrity</li>
     *   <li>Zero or negative amounts rejected at application and database levels</li>
     * </ul>
     * 
     * <p><b>Business Rules:</b>
     * <ul>
     *   <li>Purchase transactions: Positive amounts decrease available credit</li>
     *   <li>Payment transactions: Separate transaction type with opposite sign handling</li>
     *   <li>Refund transactions: Handled as separate type, not negative amounts</li>
     * </ul>
     * 
     * <p><b>Technical Note:</b> Always use {@code BigDecimal} for monetary calculations.
     * Never use {@code double} or {@code float} which accumulate rounding errors.
     * Example: {@code new BigDecimal("125.50")} preferred over {@code new BigDecimal(125.50)}
     * to avoid floating-point representation issues.
     * 
     * <p><b>Technical Specification:</b> Section 0.8.3 Data Type Mapping - COBOL
     * PIC S9(09)V99 → BigDecimal @Column(precision=11, scale=2)
     * 
     * @return the transaction amount, never null, always positive
     */
    @Column(name = "amount", nullable = false, precision = 11, scale = 2)
    @NotNull(message = "Transaction amount is required")
    @Digits(integer = 9, fraction = 2, message = "Amount must have at most 9 integer digits and 2 decimal places")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    /**
     * Merchant identifier for reconciliation and reporting.
     * 
     * <p><b>Format:</b> 9-digit numeric merchant ID assigned by card processor.
     * 
     * <p><b>Usage:</b> Enables merchant-level transaction aggregation for:
     * <ul>
     *   <li>Merchant reconciliation reports</li>
     *   <li>Fraud detection pattern analysis</li>
     *   <li>Merchant category analysis</li>
     * </ul>
     * 
     * <p><b>Index:</b> {@code idx_transaction_merchant} on merchant_id for fast lookups.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-MERCHANT-ID PIC 9(09)
     * 
     * @return the merchant identifier, may be null for non-merchant transactions
     */
    @Column(name = "merchant_id", length = 9)
    @Size(max = 9, message = "Merchant ID cannot exceed 9 characters")
    @Pattern(regexp = "\\d{0,9}", message = "Merchant ID must contain only digits")
    private String merchantId;

    /**
     * Merchant business name as reported by the card processor.
     * 
     * <p><b>Format:</b> Up to 50 characters typically in uppercase like "ACME STORE #123".
     * 
     * <p><b>Display:</b> Appears on customer statements and transaction history displays.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-MERCHANT-NAME PIC X(50)
     * 
     * @return the merchant name, may be null
     */
    @Column(name = "merchant_name", length = 50)
    @Size(max = 50, message = "Merchant name cannot exceed 50 characters")
    private String merchantName;

    /**
     * Merchant city location for transaction identification.
     * 
     * <p><b>Format:</b> City name up to 50 characters, typically uppercase.
     * 
     * <p><b>Fraud Detection:</b> City location enables geographic fraud pattern detection
     * when combined with card usage history.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-MERCHANT-CITY PIC X(50)
     * 
     * @return the merchant city, may be null
     */
    @Column(name = "merchant_city", length = 50)
    @Size(max = 50, message = "Merchant city cannot exceed 50 characters")
    private String merchantCity;

    /**
     * Merchant ZIP code (US format) for location analysis.
     * 
     * <p><b>Format:</b> US ZIP code in 5-digit (12345) or ZIP+4 (12345-6789) format.
     * 
     * <p><b>Validation:</b> {@code @Pattern} ensures valid US ZIP code format.
     * 
     * <p><b>Analytics:</b> Enables geographic spending analysis and fraud detection.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-MERCHANT-ZIP PIC X(10)
     * 
     * @return the merchant ZIP code, may be null
     */
    @Column(name = "merchant_zip", length = 10)
    @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "Merchant ZIP must be in format 12345 or 12345-6789")
    private String merchantZip;

    /**
     * Credit card number used for this transaction (PCI-DSS SENSITIVE DATA).
     * 
     * <p><b>CRITICAL SECURITY REQUIREMENT:</b> This field contains sensitive cardholder
     * data subject to PCI-DSS compliance requirements. Special handling is mandatory:
     * 
     * <p><b>PCI-DSS Compliance Measures:</b>
     * <ul>
     *   <li><b>{@code @ToString.Exclude}:</b> Prevents card number exposure in logs, debug
     *       output, exception stack traces, and any toString() invocations</li>
     *   <li><b>Masked Display:</b> Use {@code getCardNumberMasked()} for safe display,
     *       returning format "************1234" showing only last 4 digits</li>
     *   <li><b>Database Encryption:</b> Production databases must enable column-level or
     *       tablespace encryption for this field</li>
     *   <li><b>Access Control:</b> Limit application access to card numbers via service
     *       layer security annotations (@PreAuthorize)</li>
     * </ul>
     * 
     * <p><b>Format:</b> 16-digit numeric string like "4111111111111234" (no spaces/hyphens).
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @Pattern(regexp="\\d{16}")} enforces exact 16-digit format</li>
     *   <li>Additional Luhn algorithm validation recommended in service layer</li>
     * </ul>
     * 
     * <p><b>Index:</b> {@code idx_transaction_card} on card_number enables fraud detection
     * queries finding all transactions for a specific card.
     * 
     * <p><b>Usage Warning:</b> NEVER:
     * <ul>
     *   <li>Log the full card number to any logging system</li>
     *   <li>Display full card number in UI (use getCardNumberMasked() instead)</li>
     *   <li>Include in error messages or exception details</li>
     *   <li>Store in unencrypted cache or session state</li>
     *   <li>Transmit via unencrypted channels (HTTPS required)</li>
     * </ul>
     * 
     * <p><b>COBOL Mapping:</b> TRAN-CARD-NUM PIC X(16)
     * 
     * <p><b>Technical Specification:</b> Section 0.8.1 Critical Directive #3 - PCI-DSS
     * compliance with @ToString.Exclude and getCardNumberMasked() masking
     * 
     * @return the full card number, never null, SENSITIVE DATA
     * @see #getCardNumberMasked() for PCI-DSS compliant display format
     */
    @Column(name = "card_number", nullable = false, length = 16)
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "\\d{16}", message = "Card number must be exactly 16 digits")
    @ToString.Exclude
    private String cardNumber;

    /**
     * Original transaction timestamp - merchant authorization time.
     * 
     * <p><b>Business Meaning:</b> The date and time when the merchant obtained
     * authorization for this transaction from the card network. This is the
     * "merchant perspective" timestamp representing when the cardholder made
     * the purchase.
     * 
     * <p><b>Dual Timestamp Model:</b>
     * <ul>
     *   <li><b>originalTimestamp:</b> When merchant authorized the transaction</li>
     *   <li><b>processingTimestamp:</b> When our system posted the transaction</li>
     *   <li><b>Time Difference:</b> Typically 1-3 days for batch processing settlement</li>
     * </ul>
     * 
     * <p><b>Validation:</b> {@code @PastOrPresent} ensures authorization timestamp
     * cannot be a future date, preventing invalid backdated or forward-dated entries
     * that would violate audit trail integrity.
     * 
     * <p><b>Reconciliation:</b> Used to reconcile merchant authorization files with
     * posted transactions, matching on card number + amount + original timestamp.
     * 
     * <p><b>COBOL Mapping:</b> TRAN-ORIG-TS PIC X(26) formatted as ISO-8601 timestamp
     * 
     * <p><b>Technical Specification:</b> Section 6.2.2.1 - Dual timestamps for audit trail
     * 
     * @return the merchant authorization timestamp, never null, must be past or present
     */
    @Column(name = "original_timestamp", nullable = false)
    @NotNull(message = "Original timestamp is required")
    @PastOrPresent(message = "Original timestamp must be in the past or present")
    private LocalDateTime originalTimestamp;

    /**
     * Processing timestamp - system posting time.
     * 
     * <p><b>Business Meaning:</b> The date and time when this transaction was posted
     * to the account by our batch processing system. This is the "system perspective"
     * timestamp representing when the transaction officially affected the account balance.
     * 
     * <p><b>Automatic Population:</b> Database column includes {@code DEFAULT CURRENT_TIMESTAMP}
     * ensuring automatic timestamp assignment during INSERT operations, even for batch
     * SQL inserts bypassing JPA layer.
     * 
     * <p><b>Batch Processing:</b> For daily transaction posting jobs (CBTRN01C.cbl logic),
     * all transactions in a batch receive the same processingTimestamp representing the
     * batch run time, enabling batch-level reconciliation and audit trails.
     * 
     * <p><b>Statement Generation:</b> Statements are generated based on processingTimestamp,
     * not originalTimestamp, ensuring consistent cut-off dates for billing cycles.
     * 
     * <p><b>Sorting/Pagination:</b> Transaction history queries sort by processingTimestamp
     * DESC to show most recently posted transactions first, matching COBOL batch processing
     * sequence where latest batch appears at top of transaction file.
     * 
     * <p><b>Index:</b> {@code idx_transaction_account_date} on (account_id, processing_timestamp)
     * enables efficient pagination: {@code findByAccountId(id, PageRequest.of(0, 20, 
     * Sort.by("processingTimestamp").descending()))}
     * 
     * <p><b>COBOL Mapping:</b> TRAN-PROC-TS PIC X(26) formatted as ISO-8601 timestamp
     * 
     * <p><b>Technical Specification:</b> Section 6.2.2.1 - System processing time for
     * batch reconciliation and statement generation
     * 
     * @return the system processing timestamp, never null
     */
    @Column(name = "processing_timestamp", nullable = false)
    @NotNull(message = "Processing timestamp is required")
    private LocalDateTime processingTimestamp;

    /**
     * Returns PCI-DSS compliant masked card number for safe display.
     * 
     * <p><b>PCI-DSS Compliance:</b> This method provides the ONLY safe way to display
     * card numbers in application UIs, logs, and customer-facing interfaces. The masking
     * format complies with PCI-DSS requirement 3.3: "Mask PAN when displayed (the first
     * six and last four digits are the maximum number of digits to be displayed)."
     * 
     * <p><b>Masking Format:</b>
     * <ul>
     *   <li><b>Input:</b> Full 16-digit card number "4111111111111234"</li>
     *   <li><b>Output:</b> Masked format "************1234" (12 asterisks + last 4 digits)</li>
     *   <li><b>Null Handling:</b> Returns "****************" if cardNumber is null</li>
     * </ul>
     * 
     * <p><b>Non-Persistent Field:</b> Marked {@code @Transient} indicating this is a
     * calculated field not stored in the database. The database contains only the full
     * card number in the {@code card_number} column.
     * 
     * <p><b>Usage Examples:</b>
     * <pre>
     * // REST API response DTO should use masked version
     * response.setCardNumber(transaction.getCardNumberMasked());
     * 
     * // Logging (safe to log)
     * logger.info("Transaction posted for card: {}", transaction.getCardNumberMasked());
     * 
     * // UI display
     * &lt;div&gt;Card: {{ transaction.cardNumberMasked }}&lt;/div&gt;
     * 
     * // NEVER do this (exposes full card number)
     * logger.info("Card: {}", transaction.getCardNumber());  // VIOLATION!
     * </pre>
     * 
     * <p><b>Implementation Note:</b> Uses {@code substring()} for efficient string slicing.
     * Alternative implementations could use {@code String.format("*".repeat(12) + "%s", 
     * cardNumber.substring(12))} but substring is more performant and clearer.
     * 
     * <p><b>Security Warning:</b> This method is NOT a substitute for proper access
     * control. Service layer methods returning card numbers should still enforce
     * role-based security (e.g., {@code @PreAuthorize("hasRole('ADMIN')")}) before
     * allowing ANY access to card data, even masked.
     * 
     * <p><b>Technical Specification:</b> Section 0.8.1 Critical Directive #3 - PCI-DSS
     * compliance requires getCardNumberMasked() method returning "************1234" format
     * 
     * @return masked card number in format "************1234", safe for display and logging
     * @see #cardNumber for full (sensitive) card number field
     */
    @Transient
    public String getCardNumberMasked() {
        if (cardNumber == null || cardNumber.length() < 16) {
            return "****************";
        }
        return "************" + cardNumber.substring(12);
    }
}
