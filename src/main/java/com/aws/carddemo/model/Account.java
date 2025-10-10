package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing credit card account master data.
 * Migrated from: app/cpy/CVACT01Y.cpy (ACCOUNT-RECORD, 300-byte COBOL structure)
 * 
 * This entity maintains financial account information including balances, credit limits,
 * and account lifecycle dates. It serves as the central entity linking customers to their
 * cards and transaction history.
 * 
 * Key Design Decisions:
 * - accountId: Synthetic surrogate primary key (Long) for JPA performance
 * - accountNumber: Business natural key (String) preserving COBOL ACCT-ID with leading zeros
 * - BigDecimal: All monetary fields use NUMERIC(12,2) precision per COBOL PIC S9(10)V99 COMP-3
 * - LocalDate: Timezone-agnostic date representation for account lifecycle management
 * - Bidirectional relationships: @ManyToOne to Customer, @OneToMany to Card/Transaction
 * 
 * PCI-DSS Compliance:
 * - Financial data stored with exact decimal precision
 * - Audit trail via BaseEntity (createdAt, updatedAt)
 * - Optimistic locking via @Version preventing concurrent modification conflicts
 * 
 * @see BaseEntity for audit fields and version control
 * @see Customer for parent demographic entity
 * @see Card for associated payment cards
 * @see Transaction for transaction history
 */
@Entity
@Table(
    name = "ACCOUNT",
    indexes = {
        @Index(name = "idx_account_number", columnList = "account_number", unique = true),
        @Index(name = "idx_account_customer", columnList = "customer_id"),
        @Index(name = "idx_account_group", columnList = "group_id"),
        @Index(name = "idx_account_status", columnList = "active_status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_number", columnNames = {"account_number"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"customer", "cards", "transactions"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Account extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Synthetic surrogate primary key.
     * Generated automatically by database IDENTITY strategy.
     * 
     * COBOL Mapping: N/A (new field for JPA requirements)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id", nullable = false)
    @EqualsAndHashCode.Include
    private Long accountId;

    /**
     * Business account number - the original COBOL ACCT-ID.
     * Stored as VARCHAR to preserve leading zeros (e.g., "00012345678").
     * 
     * COBOL Mapping: ACCT-ID PIC 9(11)
     * Validation: Exactly 11 digits, unique across all accounts
     * 
     * NOTE: NOT included in equals()/hashCode() - JPA entities with surrogate keys
     * should use only the @Id field (accountId) for equality. This prevents
     * Hibernate session cache issues and conforms to JPA entity equality contract.
     */
    @Column(name = "account_number", nullable = false, length = 11, unique = true)
    @NotNull(message = "Account number is required")
    @Size(min = 11, max = 11, message = "Account number must be exactly 11 digits")
    @Pattern(regexp = "\\d{11}", message = "Account number must contain only digits")
    private String accountNumber;

    /**
     * Parent customer owning this account.
     * LAZY fetch to avoid N+1 query problems.
     * Foreign key constraint with RESTRICT prevents orphaned accounts.
     * 
     * COBOL Mapping: Implicit relationship through customer file lookups
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "customer_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_account_customer")
    )
    @NotNull(message = "Customer is required")
    private Customer customer;

    /**
     * Account active status indicator.
     * 'Y' = Active, 'N' = Inactive/Closed
     * 
     * COBOL Mapping: ACCT-ACTIVE-STATUS PIC X(01)
     * Database: CHECK constraint enforces valid values
     */
    @Column(name = "active_status", nullable = false, length = 1)
    @NotNull(message = "Active status is required")
    @Pattern(regexp = "[YN]", message = "Active status must be 'Y' or 'N'")
    private String activeStatus;

    /**
     * Current account balance.
     * Precision: 12 digits total, 2 decimal places (handles up to 9,999,999,999.99)
     * 
     * COBOL Mapping: ACCT-CURR-BAL PIC S9(10)V99
     * Database: CHECK constraint ensures non-negative balance
     */
    @Column(name = "current_balance", nullable = false, precision = 12, scale = 2)
    @NotNull(message = "Current balance is required")
    @Digits(integer = 10, fraction = 2, message = "Current balance must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", inclusive = true, message = "Current balance cannot be negative")
    private BigDecimal currentBalance;

    /**
     * Maximum credit limit for purchases.
     * 
     * COBOL Mapping: ACCT-CREDIT-LIMIT PIC S9(10)V99
     */
    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    @NotNull(message = "Credit limit is required")
    @Digits(integer = 10, fraction = 2, message = "Credit limit must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", inclusive = true, message = "Credit limit cannot be negative")
    private BigDecimal creditLimit;

    /**
     * Maximum cash advance limit.
     * Typically lower than regular credit limit.
     * 
     * COBOL Mapping: ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99
     */
    @Column(name = "cash_credit_limit", nullable = false, precision = 12, scale = 2)
    @NotNull(message = "Cash credit limit is required")
    @Digits(integer = 10, fraction = 2, message = "Cash credit limit must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", inclusive = true, message = "Cash credit limit cannot be negative")
    private BigDecimal cashCreditLimit;

    /**
     * Total credits posted in current billing cycle.
     * 
     * COBOL Mapping: ACCT-CURR-CYC-CREDIT PIC S9(10)V99
     */
    @Column(name = "current_cycle_credit", nullable = false, precision = 12, scale = 2)
    @NotNull(message = "Current cycle credit is required")
    @Digits(integer = 10, fraction = 2, message = "Current cycle credit must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", inclusive = true, message = "Current cycle credit cannot be negative")
    private BigDecimal currentCycleCredit;

    /**
     * Total debits posted in current billing cycle.
     * 
     * COBOL Mapping: ACCT-CURR-CYC-DEBIT PIC S9(10)V99
     */
    @Column(name = "current_cycle_debit", nullable = false, precision = 12, scale = 2)
    @NotNull(message = "Current cycle debit is required")
    @Digits(integer = 10, fraction = 2, message = "Current cycle debit must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", inclusive = true, message = "Current cycle debit cannot be negative")
    private BigDecimal currentCycleDebit;

    /**
     * Total interest paid year-to-date.
     * Accumulated monthly by interest calculation batch job (CBACT04C.cbl migration).
     * Reset to zero at beginning of calendar year.
     * 
     * <p><b>Modernization Enhancement:</b> This field represents a new feature for the
     * modernized application. The original COBOL CBACT04C.cbl program maintained a
     * working storage variable (WS-TOTAL-INT) for year-to-date interest accumulation
     * during batch processing, but this value was not persisted to the VSAM ACCTFILE.
     * The 178-byte FILLER in CVACT01Y.cpy copybook provided reserved space for future
     * enhancements. In the modernized system, we persist this value to the database
     * for improved financial reporting, audit capabilities, and regulatory compliance.
     * 
     * <p><b>Business Rules:</b>
     * - Accumulates monthly interest charges from InterestProcessor batch component
     * - Updated by AccountWriter after interest calculation completes
     * - Supports 1099-INT tax reporting at year end
     * - Provides transparency for customer financial statements
     * 
     * <p><b>Database Schema:</b> This field requires a corresponding database column
     * added via Flyway migration script with default value 0.00 for existing accounts.
     * 
     * COBOL Mapping: N/A (new field - utilizes reserved FILLER space in CVACT01Y.cpy)
     * Batch Integration: InterestProcessor → InterestTransaction → AccountWriter
     */
    @Column(name = "interest_paid_ytd", nullable = false, precision = 12, scale = 2)
    @NotNull(message = "Interest paid year-to-date is required")
    @Digits(integer = 10, fraction = 2, message = "Interest paid YTD must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", inclusive = true, message = "Interest paid YTD cannot be negative")
    @Builder.Default
    private BigDecimal interestPaidYtd = BigDecimal.ZERO;

    /**
     * Date account was opened.
     * Must be in the past or today.
     * 
     * COBOL Mapping: ACCT-OPEN-DATE PIC X(10) format YYYY-MM-DD
     */
    @Column(name = "open_date", nullable = false)
    @NotNull(message = "Open date is required")
    @PastOrPresent(message = "Open date must be in the past or today")
    private LocalDate openDate;

    /**
     * Date account expires.
     * Must be in the future relative to open date.
     * 
     * COBOL Mapping: ACCT-EXPIRAION-DATE PIC X(10) format YYYY-MM-DD
     * Note: Preserving COBOL typo "EXPIRAION" in documentation
     * Database: CHECK constraint enforces expiration_date > open_date
     */
    @Column(name = "expiration_date", nullable = false)
    @NotNull(message = "Expiration date is required")
    @Future(message = "Expiration date must be in the future")
    private LocalDate expirationDate;

    /**
     * Date card was reissued (e.g., after lost/stolen report).
     * Optional field - null if card never reissued.
     * 
     * COBOL Mapping: ACCT-REISSUE-DATE PIC X(10) format YYYY-MM-DD
     */
    @Column(name = "reissue_date")
    private LocalDate reissueDate;

    /**
     * ZIP code associated with account billing address.
     * Format: 5 digits or 5+4 (e.g., "12345" or "12345-6789")
     * 
     * COBOL Mapping: ACCT-ADDR-ZIP PIC X(10)
     */
    @Column(name = "address_zip", length = 10)
    @Pattern(
        regexp = "\\d{5}(-\\d{4})?",
        message = "ZIP code must be 5 digits or 5+4 format (e.g., 12345 or 12345-6789)"
    )
    private String addressZip;

    /**
     * Disclosure group ID for interest rate assignment.
     * Links to DISCLOSURE_GROUP table for APR calculations.
     * 
     * COBOL Mapping: ACCT-GROUP-ID PIC X(10)
     */
    @Column(name = "group_id", length = 10)
    @Size(max = 10, message = "Group ID cannot exceed 10 characters")
    private String groupId;

    /**
     * Collection of cards associated with this account.
     * Bidirectional relationship - mapped by "account" in Card entity.
     * 
     * <p><b>NO CASCADE DELETE:</b> Business rule requires accounts with active cards
     * cannot be deleted (ON DELETE RESTRICT constraint at database level). Application
     * must explicitly deactivate or remove all cards before account closure. This prevents
     * accidental data loss and enforces proper account lifecycle management per COBOL
     * business rules.
     * 
     * <p><b>NO ORPHAN REMOVAL:</b> Disabled to prevent automatic deletion of cards when
     * parent account is deleted. Cards must be explicitly deleted through Card repository
     * or service layer, maintaining audit trail and business process compliance.
     * 
     * <p><b>Cascade Operations:</b> PERSIST, MERGE, REFRESH only - these support
     * convenience operations (saving account with new cards, refreshing relationships)
     * without compromising referential integrity enforcement.
     * 
     * COBOL Mapping: One-to-many relationship through CARDXREF file
     */
    @OneToMany(
        mappedBy = "account",
        cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH},
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Card> cards = new ArrayList<>();

    /**
     * Collection of transactions posted to this account.
     * Bidirectional relationship - mapped by "account" in Transaction entity.
     * Cascade ALL operations for transaction lifecycle management.
     * 
     * COBOL Mapping: One-to-many relationship through TRANSACT file
     */
    @OneToMany(
        mappedBy = "account",
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    /**
     * Helper method to add a card to this account.
     * Maintains bidirectional relationship consistency.
     * 
     * @param card Card to add to this account
     */
    public void addCard(Card card) {
        cards.add(card);
        card.setAccount(this);
    }

    /**
     * Helper method to remove a card from this account.
     * Maintains bidirectional relationship consistency.
     * 
     * @param card Card to remove from this account
     */
    public void removeCard(Card card) {
        cards.remove(card);
        card.setAccount(null);
    }

    /**
     * Helper method to add a transaction to this account.
     * Maintains bidirectional relationship consistency.
     * 
     * @param transaction Transaction to add to this account
     */
    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        transaction.setAccount(this);
    }

    /**
     * Helper method to remove a transaction from this account.
     * Maintains bidirectional relationship consistency.
     * 
     * @param transaction Transaction to remove from this account
     */
    public void removeTransaction(Transaction transaction) {
        transactions.remove(transaction);
        transaction.setAccount(null);
    }

    /**
     * Business logic method to check if account has available credit.
     * 
     * @param amount Amount to check against available credit
     * @return true if sufficient credit available, false otherwise
     */
    public boolean hasAvailableCredit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        BigDecimal availableCredit = creditLimit.subtract(currentBalance);
        return availableCredit.compareTo(amount) >= 0;
    }

    /**
     * Business logic method to calculate available credit.
     * 
     * @return Available credit (credit limit minus current balance)
     */
    public BigDecimal getAvailableCredit() {
        return creditLimit.subtract(currentBalance);
    }

    /**
     * Business logic method to check if account is active.
     * 
     * @return true if active_status is 'Y', false otherwise
     */
    public boolean isActive() {
        return "Y".equals(activeStatus);
    }

    /**
     * Business logic method to check if account is expired.
     * 
     * @return true if expiration date has passed, false otherwise
     */
    public boolean isExpired() {
        return expirationDate != null && LocalDate.now().isAfter(expirationDate);
    }
}
