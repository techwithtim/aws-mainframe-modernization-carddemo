package com.aws.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * JPA entity representing card cross-reference bidirectional navigation table.
 * 
 * <p><b>Legacy Mapping:</b> Migrated from COBOL copybook {@code app/cpy/CVACT03Y.cpy}
 * (CARD-XREF-RECORD structure, 50-byte fixed-length record with 3 key fields + 14-byte filler).
 * 
 * <p><b>Business Purpose:</b> The CardXref entity serves as a junction/cross-reference table
 * enabling bidirectional navigation between cards, accounts, and customers. This replaces the
 * VSAM Alternate Index (AIX) pattern CXACAIX from the mainframe implementation, providing
 * efficient multi-directional lookups through B-tree indexes automatically maintained by
 * PostgreSQL without manual BLDINDEX operations.
 * 
 * <p><b>COBOL Structure Mapping (50-byte CARD-XREF-RECORD):</b></p>
 * <pre>
 * 01 CARD-XREF-RECORD.                              | Java Equivalent
 *    05  XREF-CARD-NUM    PIC X(16).               → String cardNumber (@Id)
 *    05  XREF-CUST-ID     PIC 9(09).               → Long customerId (@Column)
 *    05  XREF-ACCT-ID     PIC 9(11).               → Long accountId (@Column)
 *    05  FILLER           PIC X(14).               → (Not mapped - unused padding)
 * </pre>
 * 
 * <p><b>Data Integrity and Foreign Key Constraints:</b></p>
 * The database schema enforces referential integrity through foreign key constraints defined
 * in V1__create_tables.sql migration script:
 * <ul>
 *   <li><b>Primary Key:</b> {@code card_number} VARCHAR(16) - Unique 16-digit card number</li>
 *   <li><b>Foreign Key 1:</b> {@code fk_xref_customer} FOREIGN KEY (customer_id)
 *       REFERENCES customer(customer_id) ON DELETE RESTRICT - Prevents orphaned cards</li>
 *   <li><b>Foreign Key 2:</b> {@code fk_xref_account} FOREIGN KEY (account_id)
 *       REFERENCES account(account_id) ON DELETE RESTRICT - Prevents orphaned cards</li>
 *   <li><b>Index 1:</b> {@code idx_xref_account} B-tree index on account_id column for
 *       account-to-cards lookup queries via cardXrefRepository.findByAccountId()</li>
 *   <li><b>Index 2:</b> {@code idx_xref_customer} B-tree index on customer_id column for
 *       customer-to-cards lookup queries via cardXrefRepository.findByCustomerId()</li>
 * </ul>
 * 
 * <p><b>Navigation Relationships:</b></p>
 * The entity provides two {@code @ManyToOne} relationships for read-only navigation to parent
 * entities. Both relationships are marked with {@code insertable=false} and {@code updatable=false}
 * because the foreign key columns (customerId, accountId) are the authoritative source of truth.
 * The entity relationships (customer, account) provide convenient navigation for queries but
 * do NOT control persistence - applications must set customerId and accountId directly.
 * <ul>
 *   <li><b>customer:</b> {@code @ManyToOne(LAZY)} navigation to Customer entity. Allows
 *       loading customer demographics when processing card cross-references. Use JOIN FETCH
 *       in repository queries if customer data is always needed to avoid N+1 queries.</li>
 *   <li><b>account:</b> {@code @ManyToOne(LAZY)} navigation to Account entity. Allows
 *       loading account balances and status when processing card cross-references. Use JOIN
 *       FETCH in repository queries if account data is always needed to avoid N+1 queries.</li>
 * </ul>
 * 
 * <p><b>Bidirectional Lookup Patterns:</b></p>
 * The cross-reference table enables three critical lookup patterns that replace VSAM AIX:
 * <ol>
 *   <li><b>Card-to-Account (Primary Key Lookup):</b>
 *       {@code cardXrefRepository.findById("4556737586899855")} →
 *       Performance: {@code <10ms} using primary key index. Use case: Transaction authorization
 *       needs to find account for a given card number.</li>
 *   <li><b>Account-to-Cards (Secondary Index Lookup):</b>
 *       {@code cardXrefRepository.findByAccountId(1L)} →
 *       Performance: {@code <20ms} using idx_xref_account B-tree index. Use case: Account
 *       statement generation needs to list all cards associated with account.</li>
 *   <li><b>Customer-to-Cards (Secondary Index Lookup):</b>
 *       {@code cardXrefRepository.findByCustomerId(1L)} →
 *       Performance: {@code <20ms} using idx_xref_customer B-tree index. Use case: Customer
 *       service representative needs to see all cards held by a customer.</li>
 * </ol>
 * 
 * <p><b>PCI-DSS Compliance Requirements:</b></p>
 * This entity contains sensitive cardholder data (CHD) that must be protected per PCI-DSS
 * requirements as specified in Section 0.8.1 (Critical Directive #3):
 * <ul>
 *   <li><b>Card Number Masking:</b> The {@code cardNumber} field is annotated with
 *       {@code @ToString.Exclude} to prevent full card number exposure in application logs,
 *       debug output, and exception stack traces. For display purposes, use a masking utility
 *       that shows only last 4 digits (e.g., "************9855").</li>
 *   <li><b>Audit Trail:</b> Extends {@link BaseEntity} to inherit automatic {@code createdAt}
 *       and {@code updatedAt} timestamps for PCI-DSS compliance reporting (Requirement 10:
 *       Track and monitor all access to network resources and cardholder data).</li>
 *   <li><b>Optimistic Locking:</b> Inherits {@code version} field from BaseEntity for
 *       concurrent modification prevention, ensuring ACID compliance during card reassignments
 *       or account updates.</li>
 * </ul>
 * 
 * <p><b>Data Validation:</b></p>
 * Comprehensive Bean Validation (JSR-380) constraints ensure data integrity before persistence:
 * <ul>
 *   <li><b>cardNumber:</b> {@code @NotNull} (required field),
 *       {@code @Pattern(regexp="\\d{16}")} (exactly 16 numeric digits per ISO/IEC 7812 format)</li>
 *   <li><b>customerId:</b> {@code @NotNull} (required foreign key reference)</li>
 *   <li><b>accountId:</b> {@code @NotNull} (required foreign key reference)</li>
 * </ul>
 * 
 * <p><b>Database Schema (PostgreSQL):</b></p>
 * <pre>
 * CREATE TABLE card_xref (
 *   card_number VARCHAR(16) PRIMARY KEY NOT NULL,
 *   customer_id BIGINT NOT NULL,
 *   account_id BIGINT NOT NULL,
 *   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 *   version INTEGER NOT NULL DEFAULT 0,
 *   CONSTRAINT fk_xref_customer FOREIGN KEY (customer_id)
 *     REFERENCES customer(customer_id) ON DELETE RESTRICT,
 *   CONSTRAINT fk_xref_account FOREIGN KEY (account_id)
 *     REFERENCES account(account_id) ON DELETE RESTRICT
 * );
 * 
 * CREATE INDEX idx_xref_account ON card_xref(account_id);
 * CREATE INDEX idx_xref_customer ON card_xref(customer_id);
 * </pre>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>
 * // Create new card cross-reference (e.g., issuing new card to customer)
 * CardXref xref = CardXref.builder()
 *     .cardNumber("4556737586899855")
 *     .customerId(1L)
 *     .accountId(1L)
 *     .build();
 * cardXrefRepository.save(xref);
 * 
 * // Lookup account by card number (transaction authorization scenario)
 * Optional&lt;CardXref&gt; xrefOpt = cardXrefRepository.findById("4556737586899855");
 * if (xrefOpt.isPresent()) {
 *     Long accountId = xrefOpt.get().getAccountId();
 *     // Proceed with transaction authorization against account
 * }
 * 
 * // Find all cards for an account (statement generation scenario)
 * List&lt;CardXref&gt; accountCards = cardXrefRepository.findByAccountId(1L);
 * accountCards.forEach(xref -&gt; {
 *     String maskedCard = maskCardNumber(xref.getCardNumber());
 *     System.out.println("Card: " + maskedCard);
 * });
 * 
 * // Find all cards for a customer (customer service scenario)
 * List&lt;CardXref&gt; customerCards = cardXrefRepository.findByCustomerId(1L);
 * customerCards.forEach(xref -&gt; {
 *     Account account = xref.getAccount(); // Lazy load account if needed
 *     System.out.println("Account: " + account.getAccountNumber());
 * });
 * </pre>
 * 
 * <p><b>Performance Characteristics:</b></p>
 * <ul>
 *   <li><b>Primary Key Lookup:</b> {@code <10ms} - findById(cardNumber) uses primary key index</li>
 *   <li><b>Account Index Lookup:</b> {@code <20ms} - findByAccountId() uses idx_xref_account</li>
 *   <li><b>Customer Index Lookup:</b> {@code <20ms} - findByCustomerId() uses idx_xref_customer</li>
 *   <li><b>Index Maintenance:</b> Automatic by PostgreSQL (no manual BLDINDEX required)</li>
 *   <li><b>Concurrent Access:</b> Optimistic locking via @Version prevents lost updates</li>
 * </ul>
 * 
 * <p><b>Migration Notes:</b></p>
 * The COBOL copybook defined a 50-byte fixed-length record with 14-byte FILLER at the end.
 * This filler space was unused padding required by mainframe block size alignment and is NOT
 * mapped to any Java field. The PostgreSQL table uses variable-length VARCHAR for card_number
 * and fixed-size BIGINT for foreign keys, eliminating the need for padding bytes.
 * 
 * <p><b>Technical Specification References:</b></p>
 * <ul>
 *   <li><b>Section 6.2.2.1:</b> Card Cross-Reference Table - Bidirectional navigation enabling
 *       card-to-account, account-to-cards, customer-to-cards lookups via B-tree indexes
 *       replacing VSAM AIX pattern</li>
 *   <li><b>Section 0.1.1:</b> Primary Goal #2 - Data Layer Modernization with automatic index
 *       maintenance by PostgreSQL eliminating VSAM DEFINE AIX and BLDINDEX manual operations</li>
 *   <li><b>Section 0.8.1:</b> Critical Directive #3 - PCI-DSS compliance with
 *       {@code @ToString.Exclude} on cardNumber preventing exposure in application logs</li>
 * </ul>
 * 
 * @see BaseEntity for audit fields (createdAt, updatedAt) and optimistic locking (version)
 * @see Customer for customer demographics and master record
 * @see Account for account balances and financial data
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @since 1.0.0
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

    /**
     * Serialization version UID for compatibility across distributed systems.
     * Updated when class structure changes in a non-compatible way.
     * 
     * <p>Inherited from {@link Serializable} interface to support:
     * <ul>
     *   <li>Distributed cache storage (Redis, Hazelcast) for performance optimization</li>
     *   <li>JPA detached entity serialization for transfer across service boundaries</li>
     *   <li>Session replication in clustered application server environments</li>
     *   <li>Remote method invocation (RMI) compatibility if needed</li>
     * </ul>
     */
    private static final long serialVersionUID = 1L;

    /**
     * Card Number (Primary Key).
     * 
     * <p><b>COBOL Mapping:</b> XREF-CARD-NUM PIC X(16) - 16-character alphanumeric field
     * representing a credit card number in ISO/IEC 7812 format.
     * 
     * <p><b>Business Rule:</b> The card number serves as both the business key and database
     * primary key for card cross-reference lookups. This design choice reflects the mainframe
     * VSAM KSDS pattern where XREF-CARD-NUM was the primary key field. Each card number can
     * appear only once in the cross-reference table, enforcing one-to-one mapping between
     * card number and account/customer combination.
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li><b>Required:</b> {@code @NotNull} - Card number is mandatory for all cross-references.
     *       Service layer must ensure card number is provided before persistence.</li>
     *   <li><b>Format:</b> {@code @Pattern(regexp="\\d{16}")} - Must be exactly 16 numeric digits.
     *       This validation enforces standard credit card number format (Visa, Mastercard, etc.)
     *       per ISO/IEC 7812 specification. Luhn algorithm validation should be performed at
     *       service layer for additional verification.</li>
     *   <li><b>Uniqueness:</b> Primary key constraint ensures no duplicate card numbers in table.
     *       Attempting to insert duplicate card number will throw {@code DataIntegrityViolationException}.</li>
     * </ul>
     * 
     * <p><b>PCI-DSS Protection:</b></p>
     * CRITICAL: Card numbers are sensitive Cardholder Data (CHD) under PCI-DSS Requirement 3.
     * This field is annotated with {@code @ToString.Exclude} to prevent full card number exposure
     * in application logs, debug output, stack traces, and monitoring systems. Logging full card
     * numbers violates PCI-DSS 3.3 (mask PAN when displayed) and 3.4 (render PAN unreadable).
     * 
     * <p><b>Best Practices for Card Number Handling:</b></p>
     * <ul>
     *   <li><b>Display Masking:</b> Always use masked representation for UI display (e.g.,
     *       "************9855" showing only last 4 digits). Implement utility method:
     *       {@code String masked = cardNumber.replaceAll("\\d(?=\\d{4})", "*");}</li>
     *   <li><b>Logging:</b> Never log full card number. If logging is required for debugging,
     *       use transaction ID or masked card number instead.</li>
     *   <li><b>Transmission:</b> Use TLS 1.3 for all API requests/responses containing card data.
     *       Consider tokenization for long-term storage if PCI-DSS scope reduction is desired.</li>
     *   <li><b>Storage:</b> Database encryption at rest should be enabled (AWS RDS encryption).
     *       Application-level encryption is NOT implemented to allow SQL queries on card_number
     *       field (required for primary key lookups).</li>
     * </ul>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li><b>Column:</b> {@code card_number} VARCHAR(16) PRIMARY KEY NOT NULL</li>
     *   <li><b>Index:</b> Primary key constraint creates clustered index (automatic)</li>
     *   <li><b>Storage:</b> Variable-length character field (16 bytes + 1-byte length header)</li>
     * </ul>
     * 
     * <p><b>Performance Notes:</b></p>
     * Primary key lookups via {@code cardXrefRepository.findById(cardNumber)} are extremely fast
     * ({@code <10ms}) due to primary key index. PostgreSQL uses B-tree index structure for
     * efficient O(log n) lookup complexity. For batch operations, consider using
     * {@code findAllById(Collection<String> cardNumbers)} to reduce round-trips.
     * 
     * @see Pattern for validation regex details
     * @see NotNull for required field enforcement
     */
    @Id
    @Column(name = "card_number", length = 16, nullable = false)
    @NotNull(message = "Card number is required")
    @Pattern(regexp = "\\d{16}", message = "Card number must be exactly 16 digits")
    @ToString.Exclude
    @EqualsAndHashCode.Include
    private String cardNumber;

    /**
     * Customer ID (Foreign Key).
     * 
     * <p><b>COBOL Mapping:</b> XREF-CUST-ID PIC 9(09) - 9-digit numeric field representing
     * the customer identifier linking this card to a customer record.
     * 
     * <p><b>Business Rule:</b> Every card cross-reference must be associated with exactly one
     * customer (the cardholder). This foreign key links to {@code customer.customer_id},
     * establishing the relationship between card and customer demographics. The ON DELETE RESTRICT
     * constraint prevents customer deletion if associated cards exist, ensuring referential
     * integrity and preventing orphaned cards.
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li><b>Required:</b> {@code @NotNull} - Customer ID is mandatory. Service layer must
     *       validate that referenced customer exists before creating cross-reference.</li>
     *   <li><b>Referential Integrity:</b> Foreign key constraint {@code fk_xref_customer}
     *       ensures customer_id value exists in customer table. Attempting to insert non-existent
     *       customer ID will throw {@code DataIntegrityViolationException}.</li>
     *   <li><b>Delete Restriction:</b> ON DELETE RESTRICT prevents customer deletion if cards
     *       exist. Business process must deactivate/remove all cards before customer deletion.</li>
     * </ul>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li><b>Column:</b> {@code customer_id} BIGINT NOT NULL</li>
     *   <li><b>Foreign Key:</b> {@code fk_xref_customer} FOREIGN KEY (customer_id)
     *       REFERENCES customer(customer_id) ON DELETE RESTRICT</li>
     *   <li><b>Index:</b> {@code idx_xref_customer} B-tree index on customer_id for efficient
     *       customer-to-cards lookup queries</li>
     * </ul>
     * 
     * <p><b>Index Performance:</b></p>
     * The {@code idx_xref_customer} B-tree index enables fast customer-to-cards lookups via
     * {@code cardXrefRepository.findByCustomerId(customerId)}. Performance is {@code <20ms}
     * for typical customer with 1-10 cards. For customers with many cards (e.g., corporate
     * accounts with 100+ cards), consider pagination to avoid loading large result sets.
     * 
     * <p><b>Usage Pattern:</b></p>
     * <ul>
     *   <li><b>Create:</b> Set customerId explicitly when creating new card cross-reference:
     *       {@code xref.setCustomerId(1L);}</li>
     *   <li><b>Update:</b> Customer ID should generally NOT be updated after creation (violates
     *       business invariant). If card is reassigned to different customer, delete old
     *       cross-reference and create new one.</li>
     *   <li><b>Query:</b> Use {@code cardXrefRepository.findByCustomerId(1L)} to find all
     *       cards for a customer (e.g., customer service lookup scenarios).</li>
     * </ul>
     * 
     * <p><b>Relationship to @ManyToOne customer Field:</b></p>
     * This is the authoritative foreign key column. The {@code @ManyToOne customer} relationship
     * field provides read-only navigation convenience but does NOT control persistence. Always
     * set {@code customerId} directly; setting {@code customer} entity will be ignored due to
     * {@code insertable=false} and {@code updatable=false} on the @JoinColumn.
     * 
     * @see NotNull for required field enforcement
     * @see Customer for customer demographics entity
     */
    @Column(name = "customer_id", nullable = false)
    @NotNull(message = "Customer ID is required")
    private Long customerId;

    /**
     * Account ID (Foreign Key).
     * 
     * <p><b>COBOL Mapping:</b> XREF-ACCT-ID PIC 9(11) - 11-digit numeric field representing
     * the account identifier linking this card to an account record.
     * 
     * <p><b>Business Rule:</b> Every card cross-reference must be associated with exactly one
     * account (the credit card account). This foreign key links to {@code account.account_id},
     * establishing the relationship between card and account financial data. The ON DELETE RESTRICT
     * constraint prevents account deletion if associated cards exist, ensuring referential
     * integrity and preventing orphaned cards.
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li><b>Required:</b> {@code @NotNull} - Account ID is mandatory. Service layer must
     *       validate that referenced account exists before creating cross-reference.</li>
     *   <li><b>Referential Integrity:</b> Foreign key constraint {@code fk_xref_account}
     *       ensures account_id value exists in account table. Attempting to insert non-existent
     *       account ID will throw {@code DataIntegrityViolationException}.</li>
     *   <li><b>Delete Restriction:</b> ON DELETE RESTRICT prevents account deletion if cards
     *       exist. Business process must deactivate/remove all cards before account closure.</li>
     * </ul>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li><b>Column:</b> {@code account_id} BIGINT NOT NULL</li>
     *   <li><b>Foreign Key:</b> {@code fk_xref_account} FOREIGN KEY (account_id)
     *       REFERENCES account(account_id) ON DELETE RESTRICT</li>
     *   <li><b>Index:</b> {@code idx_xref_account} B-tree index on account_id for efficient
     *       account-to-cards lookup queries</li>
     * </ul>
     * 
     * <p><b>Index Performance:</b></p>
     * The {@code idx_xref_account} B-tree index enables fast account-to-cards lookups via
     * {@code cardXrefRepository.findByAccountId(accountId)}. Performance is {@code <20ms}
     * for typical account with 1-5 cards. This lookup pattern is critical for:
     * <ul>
     *   <li>Statement generation (listing all cards on account statement)</li>
     *   <li>Account closure workflows (identifying all cards to deactivate)</li>
     *   <li>Fraud detection (checking all cards on compromised account)</li>
     * </ul>
     * 
     * <p><b>Usage Pattern:</b></p>
     * <ul>
     *   <li><b>Create:</b> Set accountId explicitly when creating new card cross-reference:
     *       {@code xref.setAccountId(1L);}</li>
     *   <li><b>Update:</b> Account ID should generally NOT be updated after creation (violates
     *       business invariant). If card is reassigned to different account, delete old
     *       cross-reference and create new one.</li>
     *   <li><b>Query:</b> Use {@code cardXrefRepository.findByAccountId(1L)} to find all
     *       cards for an account (e.g., statement generation scenarios).</li>
     * </ul>
     * 
     * <p><b>Relationship to @ManyToOne account Field:</b></p>
     * This is the authoritative foreign key column. The {@code @ManyToOne account} relationship
     * field provides read-only navigation convenience but does NOT control persistence. Always
     * set {@code accountId} directly; setting {@code account} entity will be ignored due to
     * {@code insertable=false} and {@code updatable=false} on the @JoinColumn.
     * 
     * @see NotNull for required field enforcement
     * @see Account for account financial data entity
     */
    @Column(name = "account_id", nullable = false)
    @NotNull(message = "Account ID is required")
    private Long accountId;

    /**
     * Customer Navigation (Read-Only Relationship).
     * 
     * <p><b>Purpose:</b> Provides lazy-loaded navigation from CardXref to Customer entity for
     * read-only query convenience. This relationship allows loading customer demographics when
     * processing card cross-references without requiring manual JOIN queries.
     * 
     * <p><b>Fetch Strategy:</b> {@code FetchType.LAZY} - Customer entity is NOT loaded automatically
     * when CardXref is retrieved. Customer data is only loaded when {@code getCustomer()} is
     * explicitly called and JPA session is still open. This lazy loading strategy prevents N+1
     * query problems and reduces memory footprint when customer data is not needed.
     * 
     * <p><b>Read-Only Semantics:</b> The {@code @JoinColumn} annotation includes
     * {@code insertable=false} and {@code updatable=false} attributes, making this relationship
     * read-only. The {@code customerId} field is the authoritative source of truth for the
     * foreign key value. Setting {@code customer} entity via {@code setCustomer()} will NOT
     * update the database - applications must set {@code customerId} directly.
     * 
     * <p><b>Usage Examples:</b></p>
     * <pre>
     * // Scenario 1: Load customer demographics during card lookup
     * CardXref xref = cardXrefRepository.findById("4556737586899855").orElseThrow();
     * Customer customer = xref.getCustomer(); // Lazy load customer
     * String fullName = customer.getFirstName() + " " + customer.getLastName();
     * System.out.println("Cardholder: " + fullName);
     * 
     * // Scenario 2: Avoid N+1 queries with JOIN FETCH
     * List&lt;CardXref&gt; xrefs = cardXrefRepository.findByAccountIdWithCustomer(accountId);
     * // Repository method uses: @Query("SELECT x FROM CardXref x JOIN FETCH x.customer WHERE x.accountId = :accountId")
     * xrefs.forEach(xref -&gt; {
     *     Customer customer = xref.getCustomer(); // Already loaded, no additional query
     *     System.out.println(customer.getFirstName());
     * });
     * 
     * // Scenario 3: Check if customer is loaded (avoid LazyInitializationException)
     * if (Hibernate.isInitialized(xref.getCustomer())) {
     *     String name = xref.getCustomer().getFirstName();
     * } else {
     *     // Customer not loaded, either use customerId or load explicitly
     *     Long customerId = xref.getCustomerId();
     * }
     * </pre>
     * 
     * <p><b>LazyInitializationException Prevention:</b></p>
     * Attempting to access {@code getCustomer()} after JPA session is closed will throw
     * {@code LazyInitializationException}. To prevent this:
     * <ul>
     *   <li><b>Option 1:</b> Use {@code @Transactional} on service methods to keep session open</li>
     *   <li><b>Option 2:</b> Use JOIN FETCH in repository query to eagerly load customer</li>
     *   <li><b>Option 3:</b> Use {@code customerId} instead of navigating to customer entity</li>
     *   <li><b>Option 4:</b> Configure Open Session In View (OSIV) pattern (not recommended
     *       for production due to performance implications)</li>
     * </ul>
     * 
     * <p><b>Performance Considerations:</b></p>
     * <ul>
     *   <li><b>Lazy Loading:</b> Individual getCustomer() call triggers SELECT query (~10ms)</li>
     *   <li><b>N+1 Problem:</b> Iterating 100 CardXref entities and calling getCustomer() on
     *       each = 1 query to load xrefs + 100 queries to load customers = 101 queries total</li>
     *   <li><b>Solution:</b> Use JOIN FETCH in repository query to load all data in single query:
     *       {@code @Query("SELECT x FROM CardXref x JOIN FETCH x.customer WHERE ...")}</li>
     * </ul>
     * 
     * <p><b>Why insertable=false and updatable=false?</b></p>
     * JPA requires that when two fields map to the same database column (customerId and
     * customer.customerId), one must be marked read-only to avoid ambiguity during persistence.
     * We chose to make the relationship read-only and the foreign key field (customerId)
     * writable because:
     * <ul>
     *   <li>Foreign key values are simpler to set (just Long ID vs full Customer entity)</li>
     *   <li>Avoids confusion about whether to set customerId or customer for persistence</li>
     *   <li>Provides clear semantics: customerId controls database, customer provides navigation</li>
     * </ul>
     * 
     * @see FetchType#LAZY for lazy loading details
     * @see JoinColumn for foreign key mapping configuration
     * @see Customer for customer demographics entity structure
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false)
    @ToString.Exclude
    private Customer customer;

    /**
     * Account Navigation (Read-Only Relationship).
     * 
     * <p><b>Purpose:</b> Provides lazy-loaded navigation from CardXref to Account entity for
     * read-only query convenience. This relationship allows loading account financial data
     * (balances, credit limit, status) when processing card cross-references without requiring
     * manual JOIN queries.
     * 
     * <p><b>Fetch Strategy:</b> {@code FetchType.LAZY} - Account entity is NOT loaded automatically
     * when CardXref is retrieved. Account data is only loaded when {@code getAccount()} is
     * explicitly called and JPA session is still open. This lazy loading strategy prevents N+1
     * query problems and reduces memory footprint when account data is not needed.
     * 
     * <p><b>Read-Only Semantics:</b> The {@code @JoinColumn} annotation includes
     * {@code insertable=false} and {@code updatable=false} attributes, making this relationship
     * read-only. The {@code accountId} field is the authoritative source of truth for the
     * foreign key value. Setting {@code account} entity via {@code setAccount()} will NOT
     * update the database - applications must set {@code accountId} directly.
     * 
     * <p><b>Usage Examples:</b></p>
     * <pre>
     * // Scenario 1: Load account balance during transaction authorization
     * CardXref xref = cardXrefRepository.findById("4556737586899855").orElseThrow();
     * Account account = xref.getAccount(); // Lazy load account
     * BigDecimal balance = account.getCurrentBalance();
     * if (balance.compareTo(transactionAmount) &gt;= 0) {
     *     // Authorize transaction
     * }
     * 
     * // Scenario 2: Find all cards for account with eager loading
     * List&lt;CardXref&gt; xrefs = cardXrefRepository.findByAccountIdWithAccount(accountId);
     * // Repository method uses: @Query("SELECT x FROM CardXref x JOIN FETCH x.account WHERE x.accountId = :accountId")
     * xrefs.forEach(xref -&gt; {
     *     Account account = xref.getAccount(); // Already loaded, no additional query
     *     System.out.println("Card: " + xref.getCardNumber() + ", Balance: " + account.getCurrentBalance());
     * });
     * 
     * // Scenario 3: Check account status before processing
     * CardXref xref = cardXrefRepository.findById(cardNumber).orElseThrow();
     * if (xref.getAccount().getActiveStatus().equals("Y")) {
     *     // Process transaction on active account
     * } else {
     *     throw new AccountInactiveException("Account is not active");
     * }
     * </pre>
     * 
     * <p><b>LazyInitializationException Prevention:</b></p>
     * Attempting to access {@code getAccount()} after JPA session is closed will throw
     * {@code LazyInitializationException}. To prevent this:
     * <ul>
     *   <li><b>Option 1:</b> Use {@code @Transactional} on service methods to keep session open</li>
     *   <li><b>Option 2:</b> Use JOIN FETCH in repository query to eagerly load account:
     *       {@code @Query("SELECT x FROM CardXref x JOIN FETCH x.account WHERE x.cardNumber = :cardNumber")}</li>
     *   <li><b>Option 3:</b> Use {@code accountId} instead of navigating to account entity
     *       and load account separately if needed</li>
     *   <li><b>Option 4:</b> Use Entity Graph to specify which relationships to fetch</li>
     * </ul>
     * 
     * <p><b>Performance Considerations:</b></p>
     * <ul>
     *   <li><b>Lazy Loading:</b> Individual getAccount() call triggers SELECT query (~10ms)</li>
     *   <li><b>N+1 Problem:</b> Loading 100 CardXref entities and calling getAccount() on each
     *       results in 101 database queries (1 for xrefs + 100 for accounts). This severely
     *       impacts performance.</li>
     *   <li><b>Solution:</b> Use JOIN FETCH to load all data in 1 query:
     *       {@code @Query("SELECT x FROM CardXref x JOIN FETCH x.account WHERE x.customerId = :customerId")}</li>
     *   <li><b>Batch Fetch Size:</b> Configure {@code @BatchSize(size=10)} on Account entity
     *       to reduce N+1 to N/10 queries (fallback if JOIN FETCH not possible)</li>
     * </ul>
     * 
     * <p><b>Why insertable=false and updatable=false?</b></p>
     * JPA requires that when two fields map to the same database column (accountId and
     * account.accountId), one must be marked read-only to avoid ambiguity during persistence.
     * We chose to make the relationship read-only and the foreign key field (accountId)
     * writable because:
     * <ul>
     *   <li>Foreign key values are simpler to set (just Long ID vs full Account entity)</li>
     *   <li>Avoids confusion about whether to set accountId or account for persistence</li>
     *   <li>Provides clear semantics: accountId controls database, account provides navigation</li>
     *   <li>Reduces risk of accidentally triggering cascading operations on Account entity</li>
     * </ul>
     * 
     * <p><b>Transaction Authorization Pattern:</b></p>
     * During credit card transaction authorization, the typical flow is:
     * <ol>
     *   <li>Receive card number from POS terminal/online gateway</li>
     *   <li>Lookup CardXref by card number (primary key lookup, {@code <10ms})</li>
     *   <li>Lazy load Account entity to check balance/status ({@code ~10ms})</li>
     *   <li>Verify sufficient funds and account is active</li>
     *   <li>Authorize transaction and update account balance</li>
     * </ol>
     * Total lookup time: {@code <20ms} for critical authorization path.
     * 
     * @see FetchType#LAZY for lazy loading details
     * @see JoinColumn for foreign key mapping configuration
     * @see Account for account financial data entity structure
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @ToString.Exclude
    private Account account;
}

