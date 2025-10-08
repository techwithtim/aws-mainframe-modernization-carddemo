package com.aws.carddemo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing credit card account master data.
 * 
 * <p><b>Legacy Mapping:</b> Migrated from COBOL copybook {@code app/cpy/CVACT01Y.cpy}
 * (ACCOUNT-RECORD structure, 300-byte fixed-length record with 17 fields).
 * 
 * <p><b>Business Purpose:</b> The Account entity represents a credit card account
 * with complete financial information including current balance, credit limits,
 * transaction history, and associated cards. Each account belongs to one customer
 * and can have multiple physical cards and transactions.
 * 
 * <p><b>Financial Data Precision:</b> All monetary fields use {@code BigDecimal}
 * with precision=12 and scale=2 to maintain exact decimal precision required for
 * financial calculations per COBOL PIC S9(10)V99 COMP-3 packed decimal mapping.
 * This prevents floating-point rounding errors critical for banking applications.
 * 
 * <p><b>Database Schema:</b> Maps to {@code ACCOUNT} table with indexes and constraints.
 * 
 * @see BaseEntity
 * @see Customer
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Entity
@Table(
    name = "account",
    indexes = {
        @Index(name = "idx_account_number", columnList = "account_number", unique = true),
        @Index(name = "idx_account_customer", columnList = "customer_id"),
        @Index(name = "idx_account_group", columnList = "group_id"),
        @Index(name = "idx_account_status", columnList = "active_status")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Account extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique account identifier, auto-generated primary key.
     * 
     * <p><b>Note:</b> This is the synthetic surrogate primary key used for database 
     * relationships. The business/natural key from COBOL is stored in {@code acctId}.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /**
     * Account business key (11-digit numeric ID from COBOL system).
     * 
     * <p><b>Legacy Mapping:</b> ACCT-ID PIC 9(11) from CVACT01Y.cpy.
     * 
     * <p>This is the business identifier from the legacy COBOL/VSAM system,
     * stored as a zero-padded 11-character string to preserve leading zeros.
     * 
     * <p><b>Example:</b> "00000000001" for account 1, "00012345678" for account 12345678
     * 
     * <p><b>Validation:</b>
     * <ul>
     *   <li>{@code @NotBlank}: Required field, cannot be null or empty</li>
     *   <li>{@code @Pattern}: Must be exactly 11 numeric digits (0-9)</li>
     *   <li>{@code @Column(unique=true)}: Unique constraint across all accounts</li>
     * </ul>
     * 
     * <p><b>Database Schema:</b> acct_id VARCHAR(11) UNIQUE NOT NULL
     * 
     * <p><b>Note:</b> This is the business/natural key that uniquely identifies
     * the account in business operations and corresponds to COBOL ACCT-ID field.
     */
    @NotBlank(message = "Account business ID is required")
    @Pattern(regexp = "\\d{11}", message = "Account ID must be exactly 11 numeric digits")
    @Column(name = "acct_id", length = 11, unique = true, nullable = false)
    private String acctId;

    /**
     * Account number (11 digits, unique identifier for customer-facing operations).
     * 
     * <p><b>Data Type:</b> Stored as VARCHAR to preserve leading zeros.
     * <p><b>Business Rule:</b> Must be exactly 11 digits for validation.
     * 
     * <p><b>Note:</b> This may be the same as {@code acctId} or a formatted version
     * for display purposes. Keeping for backward compatibility with existing code.
     */
    @NotBlank(message = "Account number is required")
    @Size(min = 11, max = 11, message = "Account number must be exactly 11 digits")
    @Pattern(regexp = "\\d{11}", message = "Account number must contain only digits")
    @Column(name = "account_number", length = 11, unique = true, nullable = false)
    private String accountNumber;

    /**
     * Reference to the customer who owns this account.
     * 
     * <p><b>Relationship:</b> Many-to-One with Customer (lazy fetch).
     * <p><b>Foreign Key:</b> customer_id with RESTRICT constraint.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @NotNull(message = "Customer is required")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Customer customer;

    /**
     * Account active status ("Y" = active, "N" = inactive).
     * 
     * <p><b>Legacy Mapping:</b> ACCT-ACTIVE-STATUS PIC X(01) from CVACT01Y.cpy.
     */
    @Pattern(regexp = "[YN]", message = "Active status must be 'Y' or 'N'")
    @Size(min = 1, max = 1, message = "Active status must be exactly 1 character")
    @Column(name = "active_status", length = 1)
    private String activeStatus;

    /**
     * Current account balance (precision: 12 digits, scale: 2 decimal places).
     * 
     * <p><b>Legacy Mapping:</b> ACCT-CURR-BAL PIC S9(10)V99 from CVACT01Y.cpy.
     * <p><b>Business Rule:</b> Must be >= 0 (non-negative balance).
     */
    @NotNull(message = "Current balance is required")
    @Digits(integer = 10, fraction = 2, message = "Current balance must have at most 10 integer digits and 2 fraction digits")
    @Min(value = 0, message = "Current balance cannot be negative")
    @Column(name = "current_balance", precision = 12, scale = 2, nullable = false)
    private BigDecimal currentBalance;

    /**
     * Maximum credit limit for purchases.
     * 
     * <p><b>Legacy Mapping:</b> ACCT-CREDIT-LIMIT PIC S9(10)V99 from CVACT01Y.cpy.
     */
    @NotNull(message = "Credit limit is required")
    @Digits(integer = 10, fraction = 2, message = "Credit limit must have at most 10 integer digits and 2 fraction digits")
    @Min(value = 0, message = "Credit limit cannot be negative")
    @Column(name = "credit_limit", precision = 12, scale = 2, nullable = false)
    private BigDecimal creditLimit;

    /**
     * Maximum cash advance credit limit.
     * 
     * <p><b>Legacy Mapping:</b> ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 from CVACT01Y.cpy.
     */
    @Digits(integer = 10, fraction = 2, message = "Cash credit limit must have at most 10 integer digits and 2 fraction digits")
    @Min(value = 0, message = "Cash credit limit cannot be negative")
    @Column(name = "cash_credit_limit", precision = 12, scale = 2)
    private BigDecimal cashCreditLimit;

    /**
     * Total credits in current billing cycle.
     * 
     * <p><b>Legacy Mapping:</b> ACCT-CURR-CYC-CREDIT PIC S9(10)V99 from CVACT01Y.cpy.
     */
    @Digits(integer = 10, fraction = 2, message = "Current cycle credit must have at most 10 integer digits and 2 fraction digits")
    @Column(name = "current_cycle_credit", precision = 12, scale = 2)
    private BigDecimal currentCycleCredit;

    /**
     * Total debits in current billing cycle.
     * 
     * <p><b>Legacy Mapping:</b> ACCT-CURR-CYC-DEBIT PIC S9(10)V99 from CVACT01Y.cpy.
     */
    @Digits(integer = 10, fraction = 2, message = "Current cycle debit must have at most 10 integer digits and 2 fraction digits")
    @Column(name = "current_cycle_debit", precision = 12, scale = 2)
    private BigDecimal currentCycleDebit;

    /**
     * Account opening date.
     * 
     * <p><b>Legacy Mapping:</b> ACCT-OPEN-DATE PIC X(10) from CVACT01Y.cpy.
     * <p><b>Business Rule:</b> Must be in the past or present.
     */
    @NotNull(message = "Open date is required")
    @PastOrPresent(message = "Open date must be in the past or present")
    @Column(name = "open_date", nullable = false)
    private LocalDate openDate;

    /**
     * Account expiration date.
     * 
     * <p><b>Legacy Mapping:</b> ACCT-EXPIRAION-DATE PIC X(10) from CVACT01Y.cpy.
     * <p><b>Business Rule:</b> Must be in the future and after open date.
     */
    @NotNull(message = "Expiration date is required")
    @Future(message = "Expiration date must be in the future")
    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    /**
     * Account reissue date (optional).
     * 
     * <p><b>Legacy Mapping:</b> ACCT-REISSUE-DATE PIC X(10) from CVACT01Y.cpy.
     */
    @Column(name = "reissue_date")
    private LocalDate reissueDate;

    /**
     * Account billing address ZIP code.
     * 
     * <p><b>Legacy Mapping:</b> ACCT-ADDR-ZIP PIC X(10) from CVACT01Y.cpy.
     */
    @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "ZIP code must be 5 digits or 9 digits with hyphen (12345 or 12345-6789)")
    @Size(max = 10, message = "ZIP code cannot exceed 10 characters")
    @Column(name = "address_zip", length = 10)
    private String addressZip;

    /**
     * Disclosure group ID for interest rate tier.
     * 
     * <p><b>Legacy Mapping:</b> ACCT-GROUP-ID PIC X(10) from CVACT01Y.cpy.
     */
    @Size(max = 10, message = "Group ID cannot exceed 10 characters")
    @Column(name = "group_id", length = 10)
    private String groupId;

    /**
     * Collection of physical cards associated with this account.
     * 
     * <p><b>Relationship:</b> One-to-Many bidirectional with Card.
     */
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Card> cards = new ArrayList<>();

    /**
     * Collection of transactions posted to this account.
     * 
     * <p><b>Relationship:</b> One-to-Many bidirectional with Transaction.
     */
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Transaction> transactions = new ArrayList<>();

    /**
     * Helper method to add a card to the account's card collection.
     * Maintains bidirectional relationship integrity.
     * 
     * @param card the card to add
     */
    public void addCard(Card card) {
        if (card != null) {
            cards.add(card);
            card.setAccount(this);
        }
    }

    /**
     * Helper method to remove a card from the account's card collection.
     * Maintains bidirectional relationship integrity.
     * 
     * @param card the card to remove
     */
    public void removeCard(Card card) {
        if (card != null) {
            cards.remove(card);
            card.setAccount(null);
        }
    }

    /**
     * Helper method to add a transaction to the account's transaction collection.
     * Maintains bidirectional relationship integrity.
     * 
     * @param transaction the transaction to add
     */
    public void addTransaction(Transaction transaction) {
        if (transaction != null) {
            transactions.add(transaction);
            transaction.setAccount(this);
        }
    }

    /**
     * Helper method to remove a transaction from the account's transaction collection.
     * Maintains bidirectional relationship integrity.
     * 
     * @param transaction the transaction to remove
     */
    public void removeTransaction(Transaction transaction) {
        if (transaction != null) {
            transactions.remove(transaction);
            transaction.setAccount(null);
        }
    }
}
