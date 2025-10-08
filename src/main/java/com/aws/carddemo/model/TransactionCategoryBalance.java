package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing account-level balance segmentation by transaction category.
 * <p>
 * Migrated from: app/cpy/CVTRA01Y.cpy (TRAN-CAT-BAL-RECORD, 50-byte COBOL structure)
 * <p>
 * This entity tracks outstanding balances for each account segmented by transaction type
 * and transaction category, enabling sophisticated financial operations including:
 * <ul>
 *   <li><b>Tiered Interest Calculations:</b> Different APRs applied per category
 *       (e.g., 16.99% for purchases, 24.99% for cash advances, 12.99% for balance transfers)</li>
 *   <li><b>Credit Limit Enforcement:</b> Separate limits for different transaction types
 *       (e.g., $10,000 purchase limit vs $2,000 cash advance limit)</li>
 *   <li><b>Rewards Program Eligibility:</b> Category-specific spending thresholds
 *       (e.g., 5% cash back on grocery purchases over $500/month)</li>
 * </ul>
 * <p>
 * <b>Composite Primary Key Structure:</b>
 * <p>
 * This entity uses JPA @IdClass pattern with three key fields that together uniquely
 * identify a transaction category balance record:
 * <ol>
 *   <li>accountId - The account owning the balance</li>
 *   <li>transactionTypeCode - The type of transaction (purchase, payment, etc.)</li>
 *   <li>transactionCategoryCode - The specific category (groceries, gas, etc.)</li>
 * </ol>
 * <p>
 * The composite key maps to the COBOL TRAN-CAT-KEY structure:
 * <pre>
 * 05  TRAN-CAT-KEY.
 *     10 TRANCAT-ACCT-ID        PIC 9(11).    → accountId (Long)
 *     10 TRANCAT-TYPE-CD        PIC X(02).    → transactionTypeCode (String, 2 chars)
 *     10 TRANCAT-CD             PIC 9(04).    → transactionCategoryCode (String, 4 chars)
 * 05  TRAN-CAT-BAL              PIC S9(09)V99. → categoryBalance (BigDecimal)
 * </pre>
 * <p>
 * <b>Financial Data Precision:</b>
 * <p>
 * The categoryBalance field uses BigDecimal with NUMERIC(11,2) precision, preserving
 * exact decimal arithmetic required for financial calculations. This mapping from COBOL
 * PIC S9(09)V99 (packed decimal COMP-3) ensures no floating-point rounding errors occur
 * in balance calculations, maintaining cent-level accuracy for all monetary operations.
 * <p>
 * <b>Spring Batch Integration:</b>
 * <p>
 * This entity is central to the InterestCalculationJobConfig batch job (migrated from
 * CBACT04C.cbl). The batch processor:
 * <ol>
 *   <li>Joins account, disclosure_group, and transaction_category_balance tables</li>
 *   <li>Computes monthly interest: categoryBalance × interestRate / 1200</li>
 *   <li>Uses HALF_UP rounding mode for banker's rounding compliance</li>
 *   <li>Updates account balances with computed interest charges</li>
 * </ol>
 * <p>
 * <b>Referential Integrity:</b>
 * <p>
 * Foreign key constraints ensure data consistency:
 * <ul>
 *   <li><b>fk_catbal_account:</b> FOREIGN KEY (account_id) REFERENCES account(account_id)
 *       ON DELETE CASCADE - Child records automatically removed when parent account deleted</li>
 *   <li><b>fk_catbal_type:</b> FOREIGN KEY (transaction_type_code) REFERENCES
 *       transaction_type(type_code) - Validates transaction type exists</li>
 *   <li><b>fk_catbal_category:</b> FOREIGN KEY (transaction_type_code, transaction_category_code)
 *       REFERENCES transaction_category(transaction_type_code, category_code) -
 *       Composite foreign key validates category belongs to specified type</li>
 * </ul>
 * <p>
 * <b>Concurrency Control:</b>
 * <p>
 * Inherits @Version field from BaseEntity for optimistic locking, preventing concurrent
 * balance update conflicts. This is critical for transaction posting operations where
 * multiple batch jobs or REST API calls may attempt to modify the same category balance
 * simultaneously. Conflicts trigger OptimisticLockException, requiring application-level
 * retry with refreshed entity state.
 * <p>
 * <b>Audit Trail:</b>
 * <p>
 * The lastUpdated timestamp (with @UpdateTimestamp annotation) automatically tracks when
 * the category balance was last modified, complementing the inherited createdAt and
 * updatedAt fields from BaseEntity. This provides comprehensive audit trail for balance
 * modifications, essential for financial reconciliation and compliance reporting.
 * <p>
 * <b>Performance Optimization:</b>
 * <p>
 * Index on account_id (idx_catbal_account) enables efficient account-level category
 * balance queries, supporting REST API endpoints that retrieve all category balances
 * for a given account. The @ManyToOne relationship with Account uses LAZY fetch to
 * avoid unnecessary eager loading of related entities, reducing query overhead.
 * <p>
 * <b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 6.2.2.2: Transaction Category Balance - Composite key entity enabling
 *       account balance segmentation by category for tiered interest calculations with
 *       different APRs per category</li>
 *   <li>Section 0.8.3: Data Type Mapping - COBOL PIC S9(09)V99 → BigDecimal
 *       @Column(precision=11, scale=2) with DEFAULT 0.00 for category balance tracking</li>
 *   <li>Section 0.1.1 Primary Goal #4: Batch Processing Refactoring - InterestCalculationJobConfig
 *       uses TransactionCategoryBalance to compute monthly interest charges per account/category
 *       combination</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>
 * // Create composite key for lookup
 * TransactionCategoryBalanceId id = new TransactionCategoryBalanceId(1L, "01", "0001");
 * 
 * // Find existing balance
 * Optional&lt;TransactionCategoryBalance&gt; balance = repository.findById(id);
 * 
 * // Create new balance record
 * TransactionCategoryBalance newBalance = TransactionCategoryBalance.builder()
 *     .accountId(1L)
 *     .transactionTypeCode("01")
 *     .transactionCategoryCode("0001")
 *     .categoryBalance(new BigDecimal("1500.00"))
 *     .build();
 * repository.save(newBalance);
 * 
 * // Update existing balance (optimistic locking protects concurrent updates)
 * TransactionCategoryBalance existing = repository.findById(id).orElseThrow();
 * existing.setCategoryBalance(existing.getCategoryBalance().add(new BigDecimal("250.00")));
 * repository.save(existing); // @Version automatically incremented, lastUpdated auto-set
 * </pre>
 *
 * @see TransactionCategoryBalanceId
 * @see BaseEntity for audit fields and version control
 * @see Account for parent account entity
 */
@Entity
@Table(
    name = "TRANSACTION_CATEGORY_BALANCE",
    indexes = {
        @Index(name = "idx_catbal_account", columnList = "account_id")
    }
)
@IdClass(TransactionCategoryBalanceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"account"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class TransactionCategoryBalance extends BaseEntity implements Serializable {

    /**
     * Serial version UID for Serializable interface compliance.
     * Required for distributed caching and session serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Account identifier - first component of composite primary key.
     * <p>
     * Migrated from: TRANCAT-ACCT-ID PIC 9(11) in CVTRA01Y.cpy
     * <p>
     * Represents the 11-digit account number that owns this transaction category balance.
     * Together with transactionTypeCode and transactionCategoryCode, forms the composite
     * primary key for this entity using @IdClass(TransactionCategoryBalanceId.class) pattern.
     * <p>
     * This field participates in the foreign key relationship with the ACCOUNT table
     * (fk_catbal_account), ensuring that only valid accounts can have category balances.
     * When an account is deleted, all associated category balance records are automatically
     * removed via ON DELETE CASCADE constraint.
     * <p>
     * <b>JPA Note:</b> Annotated with @Id because it's part of the composite key. The
     * @IdClass annotation on the entity class tells JPA to use TransactionCategoryBalanceId
     * for composite key identity. This field must match the accountId field in
     * TransactionCategoryBalanceId class exactly (same name and type).
     * <p>
     * <b>Validation:</b> Must be not null and must reference an existing account_id
     * in the ACCOUNT table. Validated by database foreign key constraint.
     */
    @Id
    @Column(name = "account_id", nullable = false)
    @NotNull(message = "Account ID is required")
    @EqualsAndHashCode.Include
    private Long accountId;

    /**
     * Transaction type code - second component of composite primary key.
     * <p>
     * Migrated from: TRANCAT-TYPE-CD PIC X(02) in CVTRA01Y.cpy
     * <p>
     * Two-character code identifying the transaction type (e.g., "01" for purchases,
     * "02" for payments, "03" for cash advances, "04" for balance transfers). Together
     * with accountId and transactionCategoryCode, forms the composite primary key.
     * <p>
     * This field participates in two foreign key relationships:
     * <ul>
     *   <li><b>fk_catbal_type:</b> References transaction_type(type_code) to ensure
     *       the type code is valid</li>
     *   <li><b>fk_catbal_category:</b> Part of composite foreign key referencing
     *       transaction_category(transaction_type_code, category_code) to ensure the
     *       category belongs to the specified transaction type</li>
     * </ul>
     * <p>
     * Valid values are defined in the TRANSACTION_TYPE reference table loaded during
     * Flyway migration V3__seed_reference_data.sql from app/data/ASCII/trantype.txt.
     * <p>
     * <b>JPA Note:</b> Annotated with @Id as part of composite key. Must match the
     * transactionTypeCode field in TransactionCategoryBalanceId class.
     * <p>
     * <b>Validation:</b> Must be not null, exactly 2 characters, and must exist in
     * the TRANSACTION_TYPE table. Validated by database foreign key constraint.
     */
    @Id
    @Column(name = "transaction_type_code", nullable = false, length = 2)
    @NotNull(message = "Transaction type code is required")
    @EqualsAndHashCode.Include
    private String transactionTypeCode;

    /**
     * Transaction category code - third component of composite primary key.
     * <p>
     * Migrated from: TRANCAT-CD PIC 9(04) in CVTRA01Y.cpy
     * <p>
     * Four-character code identifying the transaction category (e.g., "0001" for groceries,
     * "0002" for gas, "0003" for restaurants). Together with accountId and transactionTypeCode,
     * forms the composite primary key enabling fine-grained balance tracking.
     * <p>
     * This field participates in the composite foreign key relationship fk_catbal_category
     * which references transaction_category(transaction_type_code, category_code), ensuring
     * that the category code exists and belongs to the specified transaction type.
     * <p>
     * Valid values are defined in the TRANSACTION_CATEGORY reference table loaded during
     * Flyway migration V3__seed_reference_data.sql from app/data/ASCII/trancatg.txt.
     * Each category belongs to a specific transaction type, establishing a hierarchical
     * classification system (e.g., purchase type has categories: groceries, gas, dining).
     * <p>
     * <b>JPA Note:</b> Annotated with @Id as part of composite key. Must match the
     * transactionCategoryCode field in TransactionCategoryBalanceId class. Stored as
     * VARCHAR(4) to preserve leading zeros (e.g., "0001" not "1").
     * <p>
     * <b>Validation:</b> Must be not null, exactly 4 characters, and must exist in the
     * TRANSACTION_CATEGORY table for the specified transaction type. Validated by
     * database composite foreign key constraint.
     */
    @Id
    @Column(name = "transaction_category_code", nullable = false, length = 4)
    @NotNull(message = "Transaction category code is required")
    @EqualsAndHashCode.Include
    private String transactionCategoryCode;

    /**
     * Outstanding balance for this account/type/category combination.
     * <p>
     * Migrated from: TRAN-CAT-BAL PIC S9(09)V99 in CVTRA01Y.cpy
     * <p>
     * Tracks the current balance for a specific transaction category within an account.
     * This balance represents the sum of all transactions of the specified type and category
     * that have been posted to the account and have not yet been paid off.
     * <p>
     * <b>Data Type Precision:</b> Uses BigDecimal with NUMERIC(11,2) precision to preserve
     * exact decimal arithmetic. The COBOL source used PIC S9(09)V99 COMP-3 (packed decimal)
     * which stores 9 integer digits and 2 fractional digits with sign. The Java BigDecimal
     * mapping ensures identical precision with no floating-point rounding errors.
     * <p>
     * <b>Database Default:</b> PostgreSQL schema includes DEFAULT 0.00 for new records,
     * ensuring category balances start at zero when first created.
     * <p>
     * <b>Financial Business Rules:</b>
     * <ul>
     *   <li><b>Interest Calculation:</b> Spring Batch InterestCalculationJobConfig (migrated
     *       from CBACT04C.cbl) uses this balance to compute monthly interest charges:
     *       <code>monthlyInterest = categoryBalance × (interestRate / 1200)</code> with
     *       RoundingMode.HALF_UP for banker's rounding compliance</li>
     *   <li><b>Credit Limit Enforcement:</b> Transaction posting logic validates that
     *       categoryBalance does not exceed category-specific credit limits (e.g., purchase
     *       category limited to $10,000, cash advance category limited to $2,000)</li>
     *   <li><b>Rewards Eligibility:</b> Rewards programs may require minimum category
     *       balances (e.g., 5% cash back on grocery purchases requires $500+ monthly balance)</li>
     * </ul>
     * <p>
     * <b>Concurrency:</b> Updates to this field are protected by optimistic locking via
     * @Version field inherited from BaseEntity. Transaction posting operations that modify
     * category balances must handle OptimisticLockException and retry with refreshed entity.
     * <p>
     * <b>Validation:</b> Constrained to 9 integer digits and 2 fractional digits via
     * @Digits annotation. This prevents overflow and ensures data integrity matches the
     * original COBOL PIC clause constraints.
     * <p>
     * <b>Audit Trail:</b> All modifications automatically update the lastUpdated timestamp
     * via @UpdateTimestamp annotation, providing audit trail for balance changes.
     */
    @Column(name = "category_balance", nullable = false, precision = 11, scale = 2)
    @NotNull(message = "Category balance is required")
    @Digits(integer = 9, fraction = 2, message = "Category balance must have at most 9 integer digits and 2 fractional digits")
    @Builder.Default
    private BigDecimal categoryBalance = BigDecimal.ZERO;

    /**
     * Timestamp of last balance modification, automatically updated on entity changes.
     * <p>
     * This field provides additional audit trail granularity beyond the inherited updatedAt
     * field from BaseEntity. While updatedAt tracks any entity modification (including
     * relationship changes), lastUpdated specifically tracks when the categoryBalance field
     * was modified, which is critical for financial reconciliation.
     * <p>
     * <b>Automatic Update:</b> The @UpdateTimestamp annotation from Hibernate ensures this
     * field is automatically set to the current timestamp whenever the entity is modified
     * and saved. This occurs during the @PreUpdate lifecycle callback, before the UPDATE
     * SQL statement is executed.
     * <p>
     * <b>Database Schema:</b> PostgreSQL column defined as:
     * <pre>
     * last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
     * </pre>
     * The database trigger provides redundant timestamp update for non-JPA modifications,
     * ensuring consistency across all modification paths.
     * <p>
     * <b>Business Value:</b> Enables temporal queries and audit reports:
     * <ul>
     *   <li>Identify stale category balances (not updated in X days)</li>
     *   <li>Track balance modification frequency patterns</li>
     *   <li>Reconcile balance changes with transaction posting timestamps</li>
     *   <li>Detect anomalous balance update patterns (e.g., high-frequency updates
     *       may indicate batch job issues or fraudulent activity)</li>
     * </ul>
     * <p>
     * <b>Migration Note:</b> The COBOL TRAN-CAT-BAL-RECORD structure did not include an
     * explicit last-updated timestamp. This is a modernization enhancement that improves
     * auditability and debugging capabilities beyond the legacy system.
     * <p>
     * <b>JPA Lifecycle:</b> Timestamp lifecycle:
     * <ul>
     *   <li><b>Insert:</b> Set to current timestamp on initial persist</li>
     *   <li><b>Update:</b> Refreshed to current timestamp on every update</li>
     *   <li><b>Query:</b> Retrieved as LocalDateTime for temporal comparison</li>
     * </ul>
     */
    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    /**
     * Parent account entity - provides read-only navigation from category balance to account.
     * <p>
     * This @ManyToOne relationship enables navigation from a transaction category balance
     * record to its owning account entity. Multiple category balance records belong to a
     * single account (one per transaction type/category combination).
     * <p>
     * <b>Fetch Strategy:</b> Uses LAZY fetch to avoid N+1 query problems. The account entity
     * is only loaded from the database when explicitly accessed via getAccount(). For bulk
     * operations that don't need account details, this prevents unnecessary join queries.
     * <p>
     * <b>Read-Only Navigation:</b> The insertable=false and updatable=false attributes make
     * this relationship read-only. The accountId field is the actual foreign key that
     * establishes the relationship; this navigation property is a convenience for accessing
     * the full account entity without requiring a separate repository query.
     * <p>
     * <b>Database Foreign Key:</b> Backed by fk_catbal_account constraint:
     * <pre>
     * FOREIGN KEY (account_id) REFERENCES account(account_id) ON DELETE CASCADE
     * </pre>
     * The CASCADE delete ensures that when an account is deleted, all associated category
     * balance records are automatically removed, preventing orphaned records.
     * <p>
     * <b>Circular Reference Prevention:</b> Excluded from @ToString via @ToString(exclude = {"account"})
     * annotation on the class level to prevent stack overflow when logging entity state.
     * Account entity has @OneToMany back-reference to category balances, creating a
     * bidirectional relationship that could cause infinite recursion in toString() calls.
     * <p>
     * <b>Legacy Mapping:</b> In the COBOL system, this relationship was implicit. Batch
     * programs like CBACT04C.cbl (interest calculation) performed explicit file reads:
     * <pre>
     * READ ACCTFILE WITH KEY = TRANCAT-ACCT-ID
     * </pre>
     * The JPA @ManyToOne relationship replaces these explicit file reads with object
     * navigation, providing cleaner object-oriented code.
     * <p>
     * <b>Usage Example:</b>
     * <pre>
     * TransactionCategoryBalance balance = repository.findById(id).orElseThrow();
     * Account account = balance.getAccount();  // LAZY load triggered here
     * String accountNumber = account.getAccountNumber();
     * </pre>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", 
                insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_catbal_account"))
    private Account account;
}
