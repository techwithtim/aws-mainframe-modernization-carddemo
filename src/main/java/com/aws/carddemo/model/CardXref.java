package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * Card Cross-Reference Entity
 * 
 * Represents the bidirectional navigation table between cards, customers, and accounts.
 * This entity replaces the VSAM Alternate Index (AIX) CXACAIX pattern from the mainframe
 * implementation, enabling efficient multi-directional lookups:
 * - Card to Account (via primary key)
 * - Account to Cards (via idx_xref_account B-tree index)
 * - Customer to Cards (via idx_xref_customer B-tree index)
 * 
 * <p>Migrated from COBOL copybook: app/cpy/CVACT03Y.cpy</p>
 * 
 * <p><strong>COBOL Structure (50-byte CARD-XREF-RECORD):</strong></p>
 * <pre>
 * 01 CARD-XREF-RECORD.
 *    05 XREF-CARD-NUM    PIC X(16).   → String cardNumber (primary key)
 *    05 XREF-CUST-ID     PIC 9(09).   → Long customerId (foreign key)
 *    05 XREF-ACCT-ID     PIC 9(11).   → Long accountId (foreign key)
 * </pre>
 * 
 * <p><strong>Data Integrity:</strong></p>
 * <ul>
 *   <li>Primary Key: cardNumber (16-digit card number)</li>
 *   <li>Foreign Key 1: customerId → customer(customer_id) with ON DELETE RESTRICT</li>
 *   <li>Foreign Key 2: accountId → account(account_id) with ON DELETE RESTRICT</li>
 *   <li>Index 1: idx_xref_account on account_id for account-to-cards lookup</li>
 *   <li>Index 2: idx_xref_customer on customer_id for customer-to-cards lookup</li>
 * </ul>
 * 
 * <p><strong>Navigation Pattern:</strong></p>
 * The @ManyToOne relationships are marked with insertable=false and updatable=false
 * because the foreign key values (customerId, accountId) are the source of truth.
 * The customer and account references provide read-only navigation for queries.
 * 
 * <p><strong>Performance Characteristics:</strong></p>
 * <ul>
 *   <li>Card-to-Account lookup: {@code <10ms} (primary key index)</li>
 *   <li>Account-to-Cards lookup: {@code <20ms} (B-tree index on account_id)</li>
 *   <li>Customer-to-Cards lookup: {@code <20ms} (B-tree index on customer_id)</li>
 * </ul>
 * 
 * <p><strong>PCI-DSS Compliance:</strong></p>
 * Card numbers are protected with @ToString.Exclude to prevent exposure in application logs.
 * For display purposes, use a masked representation (e.g., "************1234").
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0
 * @see BaseEntity
 * @see Customer
 * @see Account
 * @since 2024-01-01
 */
@Entity
@Table(
    name = "card_xref",
    indexes = {
        @Index(name = "idx_xref_account", columnList = "account_id"),
        @Index(name = "idx_xref_customer", columnList = "customer_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class CardXref extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Card Number (Primary Key)
     * 
     * 16-digit credit card number serving as the primary key for cross-reference lookups.
     * Corresponds to COBOL: XREF-CARD-NUM PIC X(16)
     * 
     * <p><strong>Validation:</strong></p>
     * <ul>
     *   <li>Required field (NOT NULL constraint)</li>
     *   <li>Exactly 16 digits (Pattern validation)</li>
     *   <li>Unique across all card cross-references</li>
     * </ul>
     * 
     * <p><strong>PCI-DSS Protection:</strong></p>
     * Excluded from toString() output to prevent card number exposure in logs.
     * Always use masked representation for display or logging purposes.
     * 
     * <p><strong>Database Mapping:</strong></p>
     * <ul>
     *   <li>Column: card_number VARCHAR(16) NOT NULL PRIMARY KEY</li>
     *   <li>Index: PRIMARY KEY index (automatic)</li>
     * </ul>
     */
    @Id
    @Column(name = "card_number", length = 16, nullable = false)
    @NotNull(message = "Card number is required")
    @Pattern(regexp = "\\d{16}", message = "Card number must be exactly 16 digits")
    @ToString.Exclude
    @EqualsAndHashCode.Include
    private String cardNumber;

    /**
     * Customer ID (Foreign Key)
     * 
     * Unique identifier linking this card cross-reference to a customer record.
     * Corresponds to COBOL: XREF-CUST-ID PIC 9(09)
     * 
     * <p><strong>Database Mapping:</strong></p>
     * <ul>
     *   <li>Column: customer_id BIGINT NOT NULL</li>
     *   <li>Foreign Key: fk_xref_customer REFERENCES customer(customer_id) ON DELETE RESTRICT</li>
     *   <li>Index: idx_xref_customer (B-tree) for customer-to-cards lookup</li>
     * </ul>
     * 
     * <p><strong>Business Rule:</strong></p>
     * ON DELETE RESTRICT prevents customer deletion if cards exist, ensuring referential integrity.
     */
    @Column(name = "customer_id", nullable = false)
    @NotNull(message = "Customer ID is required")
    private Long customerId;

    /**
     * Account ID (Foreign Key)
     * 
     * Unique identifier linking this card cross-reference to an account record.
     * Corresponds to COBOL: XREF-ACCT-ID PIC 9(11)
     * 
     * <p><strong>Database Mapping:</strong></p>
     * <ul>
     *   <li>Column: account_id BIGINT NOT NULL</li>
     *   <li>Foreign Key: fk_xref_account REFERENCES account(account_id) ON DELETE RESTRICT</li>
     *   <li>Index: idx_xref_account (B-tree) for account-to-cards lookup</li>
     * </ul>
     * 
     * <p><strong>Business Rule:</strong></p>
     * ON DELETE RESTRICT prevents account deletion if cards exist, ensuring referential integrity.
     */
    @Column(name = "account_id", nullable = false)
    @NotNull(message = "Account ID is required")
    private Long accountId;

    /**
     * Customer Navigation (Read-Only Relationship)
     * 
     * Provides lazy-loaded navigation from CardXref to Customer entity for read-only purposes.
     * Marked with insertable=false and updatable=false because customerId is the source of truth.
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>
     * CardXref xref = cardXrefRepository.findByCardNumber("1234567890123456");
     * Customer customer = xref.getCustomer(); // Lazy load customer
     * String name = customer.getFirstName() + " " + customer.getLastName();
     * </pre>
     * 
     * <p><strong>Performance Note:</strong></p>
     * Fetch type is LAZY to avoid N+1 query problems. Use JOIN FETCH in queries if customer
     * data is always needed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    /**
     * Account Navigation (Read-Only Relationship)
     * 
     * Provides lazy-loaded navigation from CardXref to Account entity for read-only purposes.
     * Marked with insertable=false and updatable=false because accountId is the source of truth.
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>
     * CardXref xref = cardXrefRepository.findByCardNumber("1234567890123456");
     * Account account = xref.getAccount(); // Lazy load account
     * BigDecimal balance = account.getCurrentBalance();
     * </pre>
     * 
     * <p><strong>Performance Note:</strong></p>
     * Fetch type is LAZY to avoid N+1 query problems. Use JOIN FETCH in queries if account
     * data is always needed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private Account account;
}
