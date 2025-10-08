package com.aws.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * JPA entity class representing APR (Annual Percentage Rate) configuration reference data
 * for account groups and transaction categories with composite primary key.
 * 
 * <p>Migrated from: app/cpy/CVTRA02Y.cpy (DIS-GROUP-RECORD structure)</p>
 * 
 * <p>This entity stores interest rate configurations used by the Spring Batch interest 
 * calculation job to compute monthly interest charges per disclosure group and category 
 * combination. Each record defines the APR for a specific combination of account group, 
 * transaction type, and transaction category.</p>
 * 
 * <p><b>Business Purpose:</b></p>
 * <ul>
 *   <li>Configures interest rates (APRs) for different account groups and transaction types</li>
 *   <li>Enables tiered interest rate structures (e.g., 16.99% purchase, 24.99% cash advance)</li>
 *   <li>Used by CBACT04C.cbl → InterestCalculationService for monthly interest computation</li>
 *   <li>Supports promotional rate configurations (e.g., 0% balance transfers for 12 months)</li>
 * </ul>
 * 
 * <p><b>COBOL Structure Mapping (50-byte record):</b></p>
 * <pre>
 * 01  DIS-GROUP-RECORD.
 *     05  DIS-GROUP-KEY.
 *        10 DIS-ACCT-GROUP-ID    PIC X(10)     → String accountGroupId
 *        10 DIS-TRAN-TYPE-CD     PIC X(02)     → String transactionTypeCode
 *        10 DIS-TRAN-CAT-CD      PIC 9(04)     → String transactionCategoryCode
 *     05  DIS-INT-RATE           PIC S9(04)V99 → BigDecimal interestRate
 *     05  FILLER                 PIC X(28)     → (not migrated - padding)
 * </pre>
 * 
 * <p><b>Composite Primary Key Pattern:</b></p>
 * <ul>
 *   <li>Uses @IdClass(DisclosureGroupId.class) for JPA composite key support</li>
 *   <li>Three @Id fields: accountGroupId, transactionTypeCode, transactionCategoryCode</li>
 *   <li>Enables repository queries: findById(new DisclosureGroupId("GROUP01", "01", "0001"))</li>
 *   <li>Database constraint: PRIMARY KEY (account_group_id, transaction_type_code, transaction_category_code)</li>
 * </ul>
 * 
 * <p><b>Data Type Mapping - Financial Precision (CRITICAL):</b></p>
 * <ul>
 *   <li>COBOL PIC S9(04)V99 → Java BigDecimal: Preserves exact decimal precision for APR calculations</li>
 *   <li>Database NUMERIC(6,2): Stores rates with 4 integer digits and 2 decimal places (e.g., 24.99%)</li>
 *   <li>Validation range: 0.00% to 99.99% enforced via @DecimalMin/@DecimalMax</li>
 *   <li>WHY BigDecimal: Avoids floating-point rounding errors in financial interest calculations</li>
 * </ul>
 * 
 * <p><b>Reference Data Examples:</b></p>
 * <pre>
 * Account Group | Type Code | Category Code | APR     | Description
 * --------------|-----------|---------------|---------|---------------------------
 * GROUP01       | 01        | 0001          | 16.99%  | Standard Purchase Rate
 * GROUP01       | 02        | 0100          | 24.99%  | Standard Cash Advance Rate
 * GROUP01       | 03        | 0500          | 12.99%  | Promotional Balance Transfer
 * GROUP02       | 01        | 0001          | 14.99%  | Premium Purchase Rate
 * GROUP02       | 02        | 0100          | 21.99%  | Premium Cash Advance Rate
 * </pre>
 * 
 * <p><b>Foreign Key Relationships:</b></p>
 * <ul>
 *   <li>@ManyToOne to TransactionType: Validates transaction type code exists</li>
 *   <li>@ManyToOne to TransactionCategory: Validates transaction category code exists</li>
 *   <li>Composite foreign key to TransactionCategory: (transactionTypeCode, transactionCategoryCode)</li>
 *   <li>LAZY fetch strategy: Relationships loaded only when explicitly accessed</li>
 *   <li>Read-only relationships: insertable=false, updatable=false (keys managed by @Id)</li>
 * </ul>
 * 
 * <p><b>Database Indexes:</b></p>
 * <ul>
 *   <li>Primary Key Index: (account_group_id, transaction_type_code, transaction_category_code)</li>
 *   <li>idx_disclosure_group: B-tree index on account_group_id for account-based APR lookups</li>
 *   <li>fk_disclosure_type: Foreign key index on transaction_type_code</li>
 *   <li>fk_disclosure_category: Composite foreign key index on (transaction_type_code, transaction_category_code)</li>
 * </ul>
 * 
 * <p><b>Usage in Interest Calculation (CBACT04C.cbl logic):</b></p>
 * <pre>
 * // Monthly interest calculation batch job
 * DisclosureGroupId key = new DisclosureGroupId(
 *     account.getAccountGroupId(),  // e.g., "GROUP01"
 *     transaction.getTransactionTypeCode(),  // e.g., "01" (Purchase)
 *     transaction.getTransactionCategoryCode()  // e.g., "0001" (Retail)
 * );
 * DisclosureGroup disclosureGroup = disclosureGroupRepository.findById(key)
 *     .orElseThrow(() -> new ResourceNotFoundException("APR configuration not found"));
 * BigDecimal monthlyInterestRate = disclosureGroup.getInterestRate()
 *     .divide(new BigDecimal("100"))  // Convert percentage to decimal (16.99% → 0.1699)
 *     .divide(new BigDecimal("12"), 6, RoundingMode.HALF_UP);  // Annual to monthly rate
 * BigDecimal interestCharge = transactionBalance.multiply(monthlyInterestRate)
 *     .setScale(2, RoundingMode.HALF_UP);  // Round to 2 decimal places (cents)
 * </pre>
 * 
 * <p><b>PCI-DSS Compliance:</b></p>
 * <ul>
 *   <li>No sensitive cardholder data: Only APR configuration (reference data)</li>
 *   <li>Safe to log: Interest rates are not PCI-sensitive information</li>
 *   <li>No masking required: All fields can appear in logs and audit trails</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Cacheable at repository layer: Frequently accessed reference data</li>
 *   <li>Indexed composite key: Efficient lookups during interest calculation batch jobs</li>
 *   <li>LAZY relationship fetch: Avoids unnecessary joins when only APR rate is needed</li>
 *   <li>Expected data volume: ~50-100 records (2-3 account groups × 7 transaction types × 3-5 categories)</li>
 * </ul>
 * 
 * <p><b>Data Seeding:</b></p>
 * <ul>
 *   <li>Loaded via Flyway migration: V3__seed_reference_data.sql</li>
 *   <li>Source data: app/data/ASCII/discgrp.txt (51 records from COBOL system)</li>
 *   <li>Production updates: Managed via database scripts, not application code</li>
 * </ul>
 * 
 * @see DisclosureGroupId
 * @see TransactionType
 * @see TransactionCategory
 * @see com.aws.carddemo.service.InterestCalculationService
 * @see com.aws.carddemo.repository.DisclosureGroupRepository
 * @author CardDemo Modernization Team
 * @version 1.0.0
 */
@Entity
@Table(
    name = "disclosure_group",
    indexes = {
        @Index(name = "idx_disclosure_group", columnList = "account_group_id")
    }
)
@IdClass(DisclosureGroupId.class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"transactionType", "transactionCategory"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisclosureGroup implements Serializable {

    /**
     * Serial version UID for Serializable interface.
     * 
     * <p>Required for:</p>
     * <ul>
     *   <li>Distributed cache compatibility (Redis session storage)</li>
     *   <li>JPA detached state management</li>
     *   <li>Spring Batch chunk serialization during interest calculation jobs</li>
     * </ul>
     */
    private static final long serialVersionUID = 1L;

    /**
     * Account group identifier (composite primary key component 1 of 3).
     * 
     * <p>Migrated from: DIS-ACCT-GROUP-ID PIC X(10) in CVTRA02Y.cpy</p>
     * 
     * <p>Categorizes accounts into different groups for interest rate determination.
     * Typically correlates with account types or customer segments:</p>
     * <ul>
     *   <li>"GROUP01" / "STANDARD": Standard credit card accounts (higher APRs)</li>
     *   <li>"GROUP02" / "PREMIUM": Premium/rewards accounts (lower APRs as benefit)</li>
     *   <li>"GROUP03" / "SECURED": Secured credit card accounts (lowest APRs)</li>
     * </ul>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: account_group_id VARCHAR(10) PRIMARY KEY (part of composite)</li>
     *   <li>Index: idx_disclosure_group on this column for efficient lookups</li>
     *   <li>Constraint: NOT NULL (part of primary key)</li>
     * </ul>
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li>Cannot be blank (required field)</li>
     *   <li>Maximum length: 10 characters (matches COBOL PIC X(10))</li>
     *   <li>Typically uppercase alphanumeric (e.g., "GROUP01", "PREMIUM")</li>
     * </ul>
     * 
     * @see #transactionTypeCode
     * @see #transactionCategoryCode
     */
    @Id
    @Column(name = "account_group_id", length = 10, nullable = false)
    @NotBlank(message = "Account group ID cannot be blank")
    @Size(max = 10, message = "Account group ID cannot exceed 10 characters")
    @EqualsAndHashCode.Include
    private String accountGroupId;

    /**
     * Transaction type code (composite primary key component 2 of 3).
     * 
     * <p>Migrated from: DIS-TRAN-TYPE-CD PIC X(02) in CVTRA02Y.cpy</p>
     * 
     * <p>Identifies the type of transaction for interest rate calculation:</p>
     * <ul>
     *   <li>"01": Purchase transactions (typically lower APRs, e.g., 16.99%)</li>
     *   <li>"02": Cash Advance (highest APRs, e.g., 24.99%)</li>
     *   <li>"03": Balance Transfer (promotional rates, e.g., 0.00%-12.99%)</li>
     *   <li>"04": Payment (no interest charged)</li>
     *   <li>"05": Refund (no interest charged)</li>
     *   <li>"06": Fee (no interest charged)</li>
     *   <li>"07": Interest Charge (no additional interest on interest)</li>
     * </ul>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: transaction_type_code VARCHAR(2) PRIMARY KEY (part of composite)</li>
     *   <li>Foreign Key: fk_disclosure_type → transaction_type(type_code)</li>
     *   <li>Constraint: NOT NULL (part of primary key)</li>
     * </ul>
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li>Cannot be blank (required field)</li>
     *   <li>Maximum length: 2 characters (matches COBOL PIC X(02))</li>
     *   <li>Format: Two-digit numeric string (e.g., "01", "02")</li>
     *   <li>Must exist in transaction_type table (enforced by foreign key)</li>
     * </ul>
     * 
     * @see TransactionType
     * @see #transactionType
     */
    @Id
    @Column(name = "transaction_type_code", length = 2, nullable = false)
    @NotBlank(message = "Transaction type code cannot be blank")
    @Size(max = 2, message = "Transaction type code cannot exceed 2 characters")
    @EqualsAndHashCode.Include
    private String transactionTypeCode;

    /**
     * Transaction category code (composite primary key component 3 of 3).
     * 
     * <p>Migrated from: DIS-TRAN-CAT-CD PIC 9(04) in CVTRA02Y.cpy</p>
     * 
     * <p>Provides granular categorization within a transaction type for specific interest rates.
     * Stored as String to preserve leading zeros (e.g., "0001", "0100", not integers 1 or 100).</p>
     * 
     * <p>Example category codes:</p>
     * <ul>
     *   <li>"0001": Retail purchases (standard APR)</li>
     *   <li>"0002": Grocery purchases (possibly lower APR)</li>
     *   <li>"0003": Gas station purchases (possibly promotional APR)</li>
     *   <li>"0100": ATM cash advance (standard cash advance APR)</li>
     *   <li>"0101": Bank teller cash advance (possibly higher APR)</li>
     *   <li>"0500": Balance transfer from competitor (promotional 0% APR)</li>
     * </ul>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: transaction_category_code VARCHAR(4) PRIMARY KEY (part of composite)</li>
     *   <li>Composite Foreign Key: fk_disclosure_category → transaction_category(transaction_type_code, category_code)</li>
     *   <li>Constraint: NOT NULL (part of primary key)</li>
     * </ul>
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li>Cannot be blank (required field)</li>
     *   <li>Maximum length: 4 characters (matches COBOL PIC 9(04))</li>
     *   <li>Format: Four-digit numeric string with leading zeros (e.g., "0001", "0100")</li>
     *   <li>Must exist in transaction_category table (enforced by composite foreign key)</li>
     * </ul>
     * 
     * @see TransactionCategory
     * @see #transactionCategory
     */
    @Id
    @Column(name = "transaction_category_code", length = 4, nullable = false)
    @NotBlank(message = "Transaction category code cannot be blank")
    @Size(max = 4, message = "Transaction category code cannot exceed 4 characters")
    @EqualsAndHashCode.Include
    private String transactionCategoryCode;

    /**
     * Annual Percentage Rate (APR) for this disclosure group configuration.
     * 
     * <p>Migrated from: DIS-INT-RATE PIC S9(04)V99 in CVTRA02Y.cpy</p>
     * 
     * <p>Stored as BigDecimal to preserve exact decimal precision required for financial
     * interest calculations. This is the annual interest rate percentage (e.g., 16.99 represents 16.99%).</p>
     * 
     * <p><b>CRITICAL - Data Type Rationale:</b></p>
     * <ul>
     *   <li>COBOL PIC S9(04)V99: Signed packed decimal with 4 integer digits and 2 decimal places</li>
     *   <li>Java BigDecimal: Avoids floating-point rounding errors (float/double are PROHIBITED for money)</li>
     *   <li>Database NUMERIC(6,2): Fixed-point decimal with exact precision</li>
     *   <li>Example: 16.99% stored as BigDecimal("16.99"), NOT 0.1699 (which would be monthly rate)</li>
     * </ul>
     * 
     * <p><b>Valid Range:</b></p>
     * <ul>
     *   <li>Minimum: 0.00% (promotional offers, balance transfers)</li>
     *   <li>Maximum: 99.99% (regulatory cap, typically much lower in practice)</li>
     *   <li>Typical purchase APRs: 14.99% - 24.99%</li>
     *   <li>Typical cash advance APRs: 24.99% - 29.99%</li>
     *   <li>Typical balance transfer APRs: 0.00% - 12.99% (promotional period)</li>
     * </ul>
     * 
     * <p><b>Interest Calculation Formula (CBACT04C.cbl logic):</b></p>
     * <pre>
     * // Step 1: Convert annual percentage to monthly decimal rate
     * BigDecimal annualRatePercentage = disclosureGroup.getInterestRate();  // e.g., 16.99
     * BigDecimal annualRateDecimal = annualRatePercentage.divide(
     *     new BigDecimal("100"), 6, RoundingMode.HALF_UP);  // 16.99 → 0.169900
     * BigDecimal monthlyRateDecimal = annualRateDecimal.divide(
     *     new BigDecimal("12"), 6, RoundingMode.HALF_UP);  // 0.169900 → 0.014158
     * 
     * // Step 2: Calculate monthly interest charge
     * BigDecimal transactionBalance = new BigDecimal("1000.00");  // Example balance
     * BigDecimal monthlyInterest = transactionBalance
     *     .multiply(monthlyRateDecimal)  // 1000.00 × 0.014158 = 14.158333
     *     .setScale(2, RoundingMode.HALF_UP);  // Round to cents: $14.16
     * </pre>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: interest_rate NUMERIC(6,2) NOT NULL</li>
     *   <li>Constraint: CHECK (interest_rate >= 0.00 AND interest_rate <= 99.99)</li>
     *   <li>Precision: 6 total digits (4 before decimal, 2 after)</li>
     *   <li>Storage: Fixed-point decimal (no floating-point errors)</li>
     * </ul>
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li>@NotNull: Field is required (no null interest rates)</li>
     *   <li>@DecimalMin("0.00"): Minimum 0.00% (promotional offers)</li>
     *   <li>@DecimalMax("99.99"): Maximum 99.99% (regulatory cap)</li>
     *   <li>@Digits(integer=4, fraction=2): Exactly 4 integer digits and 2 decimal places</li>
     * </ul>
     * 
     * <p><b>Production Data Examples:</b></p>
     * <ul>
     *   <li>16.99: Standard purchase APR for GROUP01 accounts</li>
     *   <li>24.99: Cash advance APR for GROUP01 accounts</li>
     *   <li>12.99: Promotional balance transfer APR for GROUP01 accounts</li>
     *   <li>14.99: Premium purchase APR for GROUP02 accounts (2% lower as benefit)</li>
     *   <li>0.00: Special promotional APR for limited time offers</li>
     * </ul>
     * 
     * @see #accountGroupId
     * @see #transactionTypeCode
     * @see #transactionCategoryCode
     */
    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 2)
    @NotNull(message = "Interest rate cannot be null")
    @DecimalMin(value = "0.00", message = "Interest rate must be at least 0.00%")
    @DecimalMax(value = "99.99", message = "Interest rate cannot exceed 99.99%")
    @Digits(integer = 4, fraction = 2, message = "Interest rate must have at most 4 integer digits and 2 decimal places")
    private BigDecimal interestRate;

    /**
     * Many-to-one relationship to TransactionType entity (LAZY fetch).
     * 
     * <p>Provides read-only navigation from DisclosureGroup to TransactionType entity
     * via transaction_type_code foreign key. This relationship enables:</p>
     * <ul>
     *   <li>Validation that the transaction type code exists in the transaction_type table</li>
     *   <li>Access to transaction type description (e.g., "Purchase", "Cash Advance")</li>
     *   <li>Type-based APR configuration queries and reporting</li>
     * </ul>
     * 
     * <p><b>Relationship Configuration:</b></p>
     * <ul>
     *   <li>Foreign Key: fk_disclosure_type → transaction_type(type_code)</li>
     *   <li>Fetch Strategy: LAZY (relationship loaded only when explicitly accessed)</li>
     *   <li>Read-Only: insertable=false, updatable=false (key managed by @Id field)</li>
     *   <li>Cardinality: Many DisclosureGroups → One TransactionType</li>
     * </ul>
     * 
     * <p><b>Why Read-Only (insertable=false, updatable=false)?</b></p>
     * <ul>
     *   <li>transactionTypeCode is part of the composite primary key (@Id field)</li>
     *   <li>JPA manages composite key fields directly, not through relationships</li>
     *   <li>Prevents conflict: Cannot update the same column via both @Id and @JoinColumn</li>
     *   <li>Relationship is for navigation only, not for persisting the foreign key value</li>
     * </ul>
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>
     * DisclosureGroup disclosureGroup = disclosureGroupRepository.findById(key).orElseThrow();
     * // Access transaction type description (triggers LAZY load)
     * String typeDescription = disclosureGroup.getTransactionType().getTypeDescription();
     * // Result: "Purchase" or "Cash Advance"
     * </pre>
     * 
     * <p><b>Performance Considerations:</b></p>
     * <ul>
     *   <li>LAZY fetch: Relationship NOT loaded automatically with DisclosureGroup</li>
     *   <li>Explicit access triggers SELECT query to transaction_type table</li>
     *   <li>Use JOIN FETCH in repository queries if type description is frequently needed</li>
     *   <li>Excluded from toString() to avoid LazyInitializationException in logs</li>
     * </ul>
     * 
     * @see TransactionType
     * @see #transactionTypeCode
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "transaction_type_code",
        referencedColumnName = "type_code",
        insertable = false,
        updatable = false,
        foreignKey = @jakarta.persistence.ForeignKey(name = "fk_disclosure_type")
    )
    private TransactionType transactionType;

    /**
     * Many-to-one relationship to TransactionCategory entity (LAZY fetch, composite foreign key).
     * 
     * <p>Provides read-only navigation from DisclosureGroup to TransactionCategory entity
     * via composite foreign key (transaction_type_code, transaction_category_code).
     * This relationship enables:</p>
     * <ul>
     *   <li>Validation that the transaction category code exists in the transaction_category table</li>
     *   <li>Access to category description (e.g., "Retail Purchase", "ATM Cash Advance")</li>
     *   <li>Category-specific APR configuration and tiering (16.99% purchase, 24.99% cash advance)</li>
     *   <li>Hierarchical type → category → APR reporting and analysis</li>
     * </ul>
     * 
     * <p><b>Relationship Configuration:</b></p>
     * <ul>
     *   <li>Composite Foreign Key: fk_disclosure_category → transaction_category(transaction_type_code, category_code)</li>
     *   <li>Fetch Strategy: LAZY (relationship loaded only when explicitly accessed)</li>
     *   <li>Read-Only: insertable=false, updatable=false (keys managed by @Id fields)</li>
     *   <li>Cardinality: Many DisclosureGroups → One TransactionCategory</li>
     * </ul>
     * 
     * <p><b>Composite Foreign Key Mapping:</b></p>
     * <ul>
     *   <li>Column 1: transaction_type_code (part of DisclosureGroup composite PK)</li>
     *   <li>Column 2: transaction_category_code (part of DisclosureGroup composite PK)</li>
     *   <li>References: transaction_category(transaction_type_code, category_code)</li>
     *   <li>Ensures: Both type code and category code must exist in transaction_category table</li>
     * </ul>
     * 
     * <p><b>Why Read-Only (insertable=false, updatable=false)?</b></p>
     * <ul>
     *   <li>Both transactionTypeCode and transactionCategoryCode are @Id fields (composite PK)</li>
     *   <li>JPA manages composite key fields directly, not through relationships</li>
     *   <li>Prevents conflict: Cannot update the same columns via both @Id and @JoinColumns</li>
     *   <li>Relationship is for navigation only, not for persisting the foreign key values</li>
     * </ul>
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>
     * DisclosureGroup disclosureGroup = disclosureGroupRepository.findById(key).orElseThrow();
     * // Access category description (triggers LAZY load)
     * String categoryDescription = disclosureGroup.getTransactionCategory().getCategoryDescription();
     * // Result: "Retail Purchase" or "ATM Cash Advance"
     * 
     * // Build complete description for reporting
     * String fullDescription = String.format("%s - %s: %s%%",
     *     disclosureGroup.getTransactionType().getTypeDescription(),
     *     disclosureGroup.getTransactionCategory().getCategoryDescription(),
     *     disclosureGroup.getInterestRate()
     * );
     * // Result: "Purchase - Retail Purchase: 16.99%"
     * </pre>
     * 
     * <p><b>Performance Considerations:</b></p>
     * <ul>
     *   <li>LAZY fetch: Relationship NOT loaded automatically with DisclosureGroup</li>
     *   <li>Explicit access triggers SELECT query to transaction_category table</li>
     *   <li>Use JOIN FETCH in repository queries if category description is frequently needed</li>
     *   <li>Excluded from toString() to avoid LazyInitializationException in logs</li>
     *   <li>Consider DTOs with eager loading for reporting scenarios requiring full descriptions</li>
     * </ul>
     * 
     * @see TransactionCategory
     * @see #transactionTypeCode
     * @see #transactionCategoryCode
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(
            name = "transaction_type_code",
            referencedColumnName = "transaction_type_code",
            insertable = false,
            updatable = false
        ),
        @JoinColumn(
            name = "transaction_category_code",
            referencedColumnName = "category_code",
            insertable = false,
            updatable = false
        )
    })
    private TransactionCategory transactionCategory;

    /**
     * Returns the account group identifier for this disclosure group configuration.
     * 
     * <p>This is a composite primary key component (1 of 3). The account group ID
     * categorizes accounts into different groups for interest rate determination.</p>
     * 
     * @return the account group identifier (e.g., "GROUP01", "PREMIUM")
     * @see #accountGroupId
     */
    public String getAccountGroupId() {
        return accountGroupId;
    }

    /**
     * Sets the account group identifier for this disclosure group configuration.
     * 
     * <p>This is a composite primary key component (1 of 3). Once set as part of the
     * primary key, this value should not be modified (immutable after persistence).</p>
     * 
     * @param accountGroupId the account group identifier to set (max 10 characters)
     * @see #accountGroupId
     */
    public void setAccountGroupId(String accountGroupId) {
        this.accountGroupId = accountGroupId;
    }

    /**
     * Returns the transaction type code for this disclosure group configuration.
     * 
     * <p>This is a composite primary key component (2 of 3). The transaction type code
     * identifies the type of transaction for interest rate calculation.</p>
     * 
     * @return the transaction type code (e.g., "01" for Purchase, "02" for Cash Advance)
     * @see #transactionTypeCode
     */
    public String getTransactionTypeCode() {
        return transactionTypeCode;
    }

    /**
     * Sets the transaction type code for this disclosure group configuration.
     * 
     * <p>This is a composite primary key component (2 of 3). Once set as part of the
     * primary key, this value should not be modified (immutable after persistence).</p>
     * 
     * @param transactionTypeCode the transaction type code to set (2-digit format)
     * @see #transactionTypeCode
     */
    public void setTransactionTypeCode(String transactionTypeCode) {
        this.transactionTypeCode = transactionTypeCode;
    }

    /**
     * Returns the transaction category code for this disclosure group configuration.
     * 
     * <p>This is a composite primary key component (3 of 3). The category code provides
     * granular categorization within a transaction type for specific interest rates.</p>
     * 
     * @return the transaction category code (e.g., "0001" for Retail, "0100" for ATM)
     * @see #transactionCategoryCode
     */
    public String getTransactionCategoryCode() {
        return transactionCategoryCode;
    }

    /**
     * Sets the transaction category code for this disclosure group configuration.
     * 
     * <p>This is a composite primary key component (3 of 3). Once set as part of the
     * primary key, this value should not be modified (immutable after persistence).</p>
     * 
     * @param transactionCategoryCode the transaction category code to set (4-digit format)
     * @see #transactionCategoryCode
     */
    public void setTransactionCategoryCode(String transactionCategoryCode) {
        this.transactionCategoryCode = transactionCategoryCode;
    }

    /**
     * Returns the Annual Percentage Rate (APR) for this disclosure group configuration.
     * 
     * <p>The interest rate is stored as a percentage (e.g., 16.99 represents 16.99%),
     * NOT as a decimal (0.1699). Use BigDecimal arithmetic to convert to monthly rate
     * for interest calculation.</p>
     * 
     * @return the annual interest rate percentage (0.00 to 99.99)
     * @see #interestRate
     */
    public BigDecimal getInterestRate() {
        return interestRate;
    }

    /**
     * Sets the Annual Percentage Rate (APR) for this disclosure group configuration.
     * 
     * <p>The interest rate must be provided as a percentage (e.g., 16.99 for 16.99%),
     * NOT as a decimal (0.1699). Must be between 0.00% and 99.99%.</p>
     * 
     * @param interestRate the annual interest rate percentage to set
     * @throws jakarta.validation.ConstraintViolationException if rate is outside valid range
     * @see #interestRate
     */
    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    /**
     * Returns the associated TransactionType entity (LAZY fetch).
     * 
     * <p>Accessing this property triggers a database query if the relationship has not
     * been loaded. Use JOIN FETCH in repository queries if the type description is
     * frequently needed to avoid N+1 query problems.</p>
     * 
     * @return the associated TransactionType entity, or null if not loaded
     * @throws jakarta.persistence.EntityNotFoundException if the relationship is accessed
     *         but the referenced TransactionType no longer exists
     * @see #transactionType
     */
    public TransactionType getTransactionType() {
        return transactionType;
    }

    /**
     * Sets the associated TransactionType entity.
     * 
     * <p>Note: This relationship is read-only (insertable=false, updatable=false).
     * Setting this field does NOT persist the foreign key value. To establish the
     * relationship, set the transactionTypeCode field directly.</p>
     * 
     * @param transactionType the TransactionType entity to associate
     * @see #transactionType
     */
    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    /**
     * Returns the associated TransactionCategory entity (LAZY fetch).
     * 
     * <p>Accessing this property triggers a database query if the relationship has not
     * been loaded. Use JOIN FETCH in repository queries if the category description is
     * frequently needed to avoid N+1 query problems.</p>
     * 
     * @return the associated TransactionCategory entity, or null if not loaded
     * @throws jakarta.persistence.EntityNotFoundException if the relationship is accessed
     *         but the referenced TransactionCategory no longer exists
     * @see #transactionCategory
     */
    public TransactionCategory getTransactionCategory() {
        return transactionCategory;
    }

    /**
     * Sets the associated TransactionCategory entity.
     * 
     * <p>Note: This relationship is read-only (insertable=false, updatable=false).
     * Setting this field does NOT persist the foreign key values. To establish the
     * relationship, set the transactionTypeCode and transactionCategoryCode fields directly.</p>
     * 
     * @param transactionCategory the TransactionCategory entity to associate
     * @see #transactionCategory
     */
    public void setTransactionCategory(TransactionCategory transactionCategory) {
        this.transactionCategory = transactionCategory;
    }

    /**
     * Compares this DisclosureGroup entity with another object for equality.
     * 
     * <p>Two DisclosureGroup entities are considered equal if their composite primary keys
     * are equal (accountGroupId, transactionTypeCode, transactionCategoryCode). The
     * interestRate field and relationship fields are NOT included in equality comparison
     * to align with JPA entity identity semantics.</p>
     * 
     * <p>This method is automatically generated by Lombok @Data annotation with
     * @EqualsAndHashCode(onlyExplicitlyIncluded = true), using only fields marked with
     * @EqualsAndHashCode.Include (the three @Id fields).</p>
     * 
     * <p><b>JPA Entity Identity Rules:</b></p>
     * <ul>
     *   <li>Entity identity is based on primary key fields only</li>
     *   <li>Two entities with the same PK are the same entity instance (even if other fields differ)</li>
     *   <li>Relationship fields excluded to prevent LazyInitializationException during comparisons</li>
     * </ul>
     * 
     * @param o the object to compare with
     * @return true if the objects have equal composite primary keys, false otherwise
     * @see DisclosureGroupId#equals(Object)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisclosureGroup that = (DisclosureGroup) o;
        return java.util.Objects.equals(accountGroupId, that.accountGroupId) &&
               java.util.Objects.equals(transactionTypeCode, that.transactionTypeCode) &&
               java.util.Objects.equals(transactionCategoryCode, that.transactionCategoryCode);
    }

    /**
     * Generates a hash code for this DisclosureGroup entity.
     * 
     * <p>The hash code is computed from the three composite primary key fields
     * (accountGroupId, transactionTypeCode, transactionCategoryCode) using Objects.hash().
     * This ensures consistent hash code generation for entity identity management in JPA
     * and collection operations.</p>
     * 
     * <p>This method is automatically generated by Lombok @Data annotation with
     * @EqualsAndHashCode(onlyExplicitlyIncluded = true), using only fields marked with
     * @EqualsAndHashCode.Include (the three @Id fields).</p>
     * 
     * <p><b>Hash Code Consistency Requirements:</b></p>
     * <ul>
     *   <li>Hash code must remain constant for the lifetime of the entity instance</li>
     *   <li>Hash code computed from immutable primary key fields only</li>
     *   <li>Consistent with equals(): equal entities have equal hash codes</li>
     * </ul>
     * 
     * @return the hash code value for this entity's composite primary key
     * @see DisclosureGroupId#hashCode()
     */
    @Override
    public int hashCode() {
        return java.util.Objects.hash(accountGroupId, transactionTypeCode, transactionCategoryCode);
    }

    /**
     * Returns a string representation of this DisclosureGroup entity.
     * 
     * <p>The string includes the composite primary key fields (accountGroupId,
     * transactionTypeCode, transactionCategoryCode) and the interestRate field.
     * The transactionType and transactionCategory relationship fields are EXCLUDED
     * to avoid LazyInitializationException when toString() is called outside of an
     * active JPA session (e.g., in logs after transaction commit).</p>
     * 
     * <p>This method is automatically generated by Lombok @Data annotation with
     * @ToString(exclude = {"transactionType", "transactionCategory"}).</p>
     * 
     * <p><b>Example Output:</b></p>
     * <pre>
     * DisclosureGroup(accountGroupId=GROUP01, transactionTypeCode=01, 
     *     transactionCategoryCode=0001, interestRate=16.99)
     * </pre>
     * 
     * <p><b>Usage in Logging:</b></p>
     * <ul>
     *   <li>Safe to log: No PCI-sensitive data (APR configuration is reference data)</li>
     *   <li>Audit trail: Track APR configuration changes for compliance</li>
     *   <li>Debugging: Identify which APR configuration was applied to interest calculations</li>
     * </ul>
     * 
     * @return a string representation of this entity
     */
    @Override
    public String toString() {
        return "DisclosureGroup(" +
                "accountGroupId=" + accountGroupId +
                ", transactionTypeCode=" + transactionTypeCode +
                ", transactionCategoryCode=" + transactionCategoryCode +
                ", interestRate=" + interestRate +
                ")";
    }
}
