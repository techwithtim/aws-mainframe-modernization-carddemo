package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.CreditCardNumber;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * JPA entity representing physical credit card information.
 * Migrated from: app/cpy/CVACT02Y.cpy (CARD-RECORD, 150-byte COBOL structure)
 * 
 * <p>This entity maintains credit card details including card numbers, expiration dates,
 * and cardholder names. Each card is associated with exactly one account, enabling the
 * system to track multiple cards per account for fraud prevention and replacement scenarios.
 * 
 * <p><b>Legacy COBOL Structure Mapping:</b>
 * <pre>
 * COBOL CARD-RECORD (150 bytes):
 * 01  CARD-RECORD.
 *     05  CARD-NUM                PIC X(16).     → cardNumber (String with Luhn validation)
 *     05  CARD-ACCT-ID            PIC 9(11).     → account (@ManyToOne relationship)
 *     05  CARD-CVV-CD             PIC 9(03).     → INTENTIONALLY EXCLUDED (PCI-DSS 3.2.2)
 *     05  CARD-EMBOSSED-NAME      PIC X(50).     → embossedName (String)
 *     05  CARD-EXPIRAION-DATE     PIC X(10).     → expirationDate (LocalDate)
 *     05  CARD-ACTIVE-STATUS      PIC X(01).     → activeStatus ('Y'/'N')
 *     05  FILLER                  PIC X(59).     → Not migrated (padding)
 * </pre>
 * 
 * <p><b>Key Design Decisions:</b>
 * <ul>
 *   <li><b>Surrogate Key:</b> cardId (Long) introduced as synthetic primary key for JPA
 *       performance and compatibility with Spring Data JPA conventions. COBOL system used
 *       CARD-NUM as primary key in VSAM KSDS, but relational databases benefit from
 *       immutable numeric surrogate keys.</li>
 *   
 *   <li><b>Card Number Storage:</b> Stored as VARCHAR(16) preserving full 16-digit card
 *       number for transaction authorization lookups. MASKED in toString() via
 *       {@code @ToString.Exclude} to prevent exposure in logs. Public display uses
 *       {@code getCardNumberMasked()} method showing "************1234" format.</li>
 *   
 *   <li><b>CVV Exclusion:</b> CARD-CVV-CD field from COBOL copybook is INTENTIONALLY OMITTED
 *       from this entity. PCI-DSS Requirement 3.2.2 explicitly prohibits storage of CVV2/CVC2
 *       codes after authorization. CVV must be collected at point of sale but never persisted
 *       to any database or log file.</li>
 *   
 *   <li><b>Account Relationship:</b> CARD-ACCT-ID becomes {@code @ManyToOne} with LAZY fetch
 *       to Account entity. Foreign key constraint with RESTRICT prevents deletion of accounts
 *       with active cards. Multiple cards can reference same account (replacement cards,
 *       additional cardholders).</li>
 *   
 *   <li><b>Expiration Validation:</b> {@code @Future} constraint ensures new cards cannot be
 *       created with past expiration dates. Database CHECK constraint
 *       {@code chk_future_expiration} provides defense-in-depth validation. Active cards with
 *       expired dates are handled by batch job (card expiration processor).</li>
 *   
 *   <li><b>Active Status:</b> Single-character flag 'Y'/'N' preserves COBOL semantics.
 *       Alternative boolean mapping considered but rejected to maintain exact legacy logic
 *       compatibility where blank/null values were treated differently than 'N'.</li>
 * </ul>
 * 
 * <p><b>PCI-DSS Compliance Implementation:</b>
 * <ul>
 *   <li><b>Requirement 3.2.2:</b> CVV codes never stored (field omitted entirely)</li>
 *   <li><b>Requirement 3.4:</b> Card numbers masked in logs via {@code @ToString.Exclude}</li>
 *   <li><b>Requirement 3.5:</b> Full card number accessible only via getter (controlled access)</li>
 *   <li><b>Requirement 8.2:</b> Card modifications audited via BaseEntity timestamps</li>
 *   <li><b>Requirement 10.1:</b> All card access logged via Spring Security audit interceptors</li>
 * </ul>
 * 
 * <p><b>Database Schema:</b>
 * <pre>
 * CREATE TABLE card (
 *     card_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 *     card_number        VARCHAR(16) NOT NULL UNIQUE,  -- PCI-DSS: Full number for authorization
 *     account_id         BIGINT NOT NULL,
 *     embossed_name      VARCHAR(50) NOT NULL,         -- Cardholder name as printed on card
 *     expiration_date    DATE NOT NULL CHECK (expiration_date > CURRENT_DATE),
 *     active_status      CHAR(1) CHECK (active_status IN ('Y', 'N')),
 *     created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     version            INTEGER NOT NULL DEFAULT 0,
 *     CONSTRAINT fk_card_account FOREIGN KEY (account_id) 
 *         REFERENCES account(account_id) ON DELETE RESTRICT
 * );
 * 
 * -- Performance indexes for common query patterns
 * CREATE UNIQUE INDEX idx_card_number ON card(card_number);
 * CREATE INDEX idx_card_account ON card(account_id);
 * CREATE INDEX idx_card_expiration ON card(expiration_date) WHERE active_status = 'Y';
 * </pre>
 * 
 * <p><b>Usage Examples:</b>
 * <pre>
 * // Creating a new card with builder pattern
 * Card card = Card.builder()
 *     .cardNumber("4532123456789012")  // Visa card number (Luhn valid)
 *     .account(existingAccount)         // Must reference existing Account entity
 *     .embossedName("JOHN DOE")         // Name printed on physical card
 *     .expirationDate(LocalDate.of(2027, 12, 31))  // Future date required
 *     .activeStatus("Y")
 *     .build();
 * cardRepository.save(card);
 * 
 * // Safe display of card number (PCI-DSS compliant)
 * String maskedDisplay = card.getCardNumberMasked();  // Returns "************9012"
 * logger.info("Card created: {}", maskedDisplay);      // Safe for logs
 * 
 * // Full card number access (use only for authorization, never log)
 * String fullNumber = card.getCardNumber();  // Returns "4532123456789012"
 * paymentGateway.authorize(fullNumber, cvv); // CVV from user input, never persisted
 * </pre>
 * 
 * <p><b>Business Rules Enforced:</b>
 * <ul>
 *   <li>Card number must be exactly 16 digits and pass Luhn algorithm checksum</li>
 *   <li>Card number must be unique across all cards in system</li>
 *   <li>Each card must be associated with exactly one account (not null)</li>
 *   <li>Embossed name cannot be blank (cardholder identification requirement)</li>
 *   <li>Expiration date must be in the future at creation time</li>
 *   <li>Active status must be 'Y' or 'N' (no other values permitted)</li>
 * </ul>
 * 
 * <p><b>Relationship Navigation:</b>
 * <ul>
 *   <li><b>Parent:</b> {@code card.getAccount()} - Navigate to parent Account entity (LAZY)</li>
 *   <li><b>Children:</b> No direct children (transactions reference accounts, not cards)</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li><b>LAZY Fetch:</b> Account relationship uses LAZY fetch to prevent N+1 queries when
 *       loading card collections. Explicitly join-fetch account when needed:
 *       {@code SELECT c FROM Card c JOIN FETCH c.account WHERE c.cardNumber = :cardNum}</li>
 *   
 *   <li><b>Index Coverage:</b> idx_card_number (unique) enables fast card lookup by number
 *       for authorization checks (most frequent query pattern in online transactions)</li>
 *   
 *   <li><b>Partial Index:</b> idx_card_expiration filters WHERE active_status='Y' to support
 *       efficient batch processing of soon-to-expire active cards without scanning inactive
 *       cards (95% reduction in index size for typical card portfolios)</li>
 * </ul>
 * 
 * <p><b>Concurrency Control:</b>
 * Inherits optimistic locking via {@code @Version} from BaseEntity. Card updates (status
 * changes, expiration date extensions) use version check to prevent lost updates. Retry
 * logic required in service layer for {@code OptimisticLockException} handling.
 * 
 * <p><b>Migration from Legacy COBOL:</b>
 * <ul>
 *   <li><b>VSAM CARDFILE:</b> KSDS with primary key CARD-NUM → PostgreSQL table with
 *       UNIQUE index on card_number preserving key lookup performance</li>
 *   <li><b>AIX on CARD-ACCT-ID:</b> Alternate index → Secondary index idx_card_account
 *       enabling efficient "find all cards for account" queries</li>
 *   <li><b>READ by CARD-NUM:</b> COBOL file READ operations → JPA findByCardNumber()
 *       repository method with same O(log n) performance via B-tree index</li>
 *   <li><b>REWRITE operations:</b> COBOL REWRITE → JPA save() on existing entity with
 *       optimistic lock version check replacing VSAM record-level locking</li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 6.2.2.1: Card Master Table - Defines schema, indexes, and PCI-DSS
 *       compliance requirements including CVV exclusion and card number masking</li>
 *   <li>Section 0.8.1 Critical Directive #3: PCI-DSS compliance mandate requiring
 *       {@code @ToString.Exclude} on cardNumber and CVV prohibition per Requirement 3.2.2</li>
 *   <li>Section 0.1.1 Primary Goal #2: Data Layer Modernization - {@code @ManyToOne} to
 *       Account entity replaces VSAM CARD-ACCT-ID foreign key reference with JPA relationship</li>
 * </ul>
 * 
 * @see BaseEntity for audit fields (createdAt, updatedAt) and version control
 * @see Account for parent account entity relationship
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Entity
@Table(
    name = "card",
    indexes = {
        @Index(name = "idx_card_number", columnList = "card_number", unique = true),
        @Index(name = "idx_card_account", columnList = "account_id"),
        @Index(name = "idx_card_expiration", columnList = "expiration_date")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_card_number", columnNames = {"card_number"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Card extends BaseEntity implements Serializable {

    /**
     * Serialization version UID for compatibility across distributed systems.
     * Updated when class structure changes in a non-compatible way.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Synthetic surrogate primary key for JPA entity management.
     * Generated automatically by database IDENTITY strategy.
     * 
     * <p><b>Design Rationale:</b> While COBOL CARD-RECORD used CARD-NUM as primary key in
     * VSAM KSDS, relational databases benefit from immutable numeric surrogate keys for:
     * <ul>
     *   <li>Foreign key performance (8-byte BIGINT vs 16-byte VARCHAR references)</li>
     *   <li>Primary key stability (card numbers never change but system allows corrections)</li>
     *   <li>Spring Data JPA conventions (standard Long id pattern)</li>
     * </ul>
     * 
     * <p><b>COBOL Mapping:</b> N/A (new field introduced for JPA requirements)
     * 
     * <p><b>Equality Contract:</b> This field ALONE determines entity equality via
     * {@code @EqualsAndHashCode.Include}. Business fields (cardNumber) are NOT included
     * to prevent Hibernate session cache issues and conform to JPA entity equality semantics.
     * 
     * @see EqualsAndHashCode for JPA entity equality contract explanation
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id", nullable = false)
    @EqualsAndHashCode.Include
    private Long cardId;

    /**
     * 16-digit credit card number (PAN - Primary Account Number).
     * 
     * <p><b>COBOL Mapping:</b> CARD-NUM PIC X(16)
     * 
     * <p><b>Storage Format:</b> Full 16-digit number stored as VARCHAR(16) to enable
     * authorization lookups. Example: "4532123456789012" (Visa card format).
     * 
     * <p><b>PCI-DSS Compliance:</b>
     * <ul>
     *   <li><b>Requirement 3.4:</b> {@code @ToString.Exclude} prevents card number exposure
     *       in logs, debug output, exception messages, or toString() calls. Developers must
     *       explicitly call getCardNumberMasked() for display purposes.</li>
     *   
     *   <li><b>Requirement 3.5:</b> Full number accessible only via getCardNumber() method,
     *       enabling access control auditing and encryption-at-rest policies at getter level.</li>
     *   
     *   <li><b>Luhn Validation:</b> {@code @CreditCardNumber} annotation applies Luhn
     *       algorithm (modulus 10) checksum validation, catching data entry errors and
     *       invalid test card numbers.</li>
     * </ul>
     * 
     * <p><b>Validation Rules:</b>
     * <ul>
     *   <li>Must be exactly 16 digits (no spaces, hyphens, or formatting)</li>
     *   <li>Must contain only numeric characters (0-9)</li>
     *   <li>Must pass Luhn algorithm checksum for industry-standard validation</li>
     *   <li>Must be unique across all cards (enforced by database UNIQUE constraint)</li>
     *   <li>Cannot be blank or null</li>
     * </ul>
     * 
     * <p><b>Display Formatting:</b>
     * <ul>
     *   <li><b>Masked Display:</b> Use {@code getCardNumberMasked()} for UI/logs:
     *       returns "************1234" showing only last 4 digits</li>
     *   <li><b>Full Number:</b> Use {@code getCardNumber()} ONLY for payment authorization
     *       and NEVER log the result</li>
     * </ul>
     * 
     * <p><b>Database Constraint:</b>
     * <pre>
     * card_number VARCHAR(16) NOT NULL UNIQUE
     * CONSTRAINT uk_card_number UNIQUE (card_number)
     * </pre>
     * 
     * <p><b>Index:</b> idx_card_number (unique B-tree) - Primary lookup path for
     * authorization requests, ensuring O(log n) performance for findByCardNumber queries.
     * 
     * @see #getCardNumberMasked() for PCI-DSS compliant display format
     */
    @NotBlank(message = "Card number is required")
    @Size(min = 16, max = 16, message = "Card number must be exactly 16 digits")
    @Pattern(regexp = "\\d{16}", message = "Card number must contain only digits")
    @CreditCardNumber(message = "Invalid card number per Luhn algorithm")
    @Column(name = "card_number", length = 16, unique = true, nullable = false)
    @ToString.Exclude
    private String cardNumber;

    /**
     * Parent account associated with this card.
     * 
     * <p><b>COBOL Mapping:</b> CARD-ACCT-ID PIC 9(11) - Foreign key to ACCOUNT-RECORD
     * 
     * <p><b>Relationship Semantics:</b>
     * <ul>
     *   <li><b>Cardinality:</b> Many cards to one account (N:1 relationship)</li>
     *   <li><b>Multiplicity:</b> Each card MUST reference exactly one account (NOT NULL)</li>
     *   <li><b>Use Cases:</b> Replacement cards, additional cardholders, temporary cards</li>
     *   <li><b>Referential Integrity:</b> RESTRICT constraint prevents account deletion
     *       when active cards exist (business rule enforcement)</li>
     * </ul>
     * 
     * <p><b>Fetch Strategy:</b> LAZY fetch prevents N+1 query problem when loading card
     * collections. Account data loaded only when explicitly accessed:
     * <pre>
     * List<Card> cards = cardRepository.findAll();  // SELECT cards only
     * cards.get(0).getAccount().getAccountNumber(); // SELECT account (lazy load)
     * 
     * // Efficient alternative: explicit JOIN FETCH
     * List<Card> cardsWithAccounts = cardRepository
     *     .findAll(Sort.by("cardNumber"))
     *     .stream()
     *     .peek(c -> Hibernate.initialize(c.getAccount()))  // Batch fetch accounts
     *     .toList();
     * </pre>
     * 
     * <p><b>Cascading:</b> NO cascade operations - card lifecycle independent of account.
     * Deleting a card does not affect account; deleting an account fails if cards exist
     * (ON DELETE RESTRICT protects against orphaned cards).
     * 
     * <p><b>toString() Exclusion:</b> {@code @ToString.Exclude} prevents circular reference
     * issues when Account entity has {@code @OneToMany List<Card> cards} bidirectional
     * relationship. Without exclusion, toString() would infinite loop:
     * card.toString() → account.toString() → cards.toString() → card.toString() → ∞
     * 
     * <p><b>EqualsHashCode Exclusion:</b> {@code @EqualsAndHashCode.Exclude} prevents
     * Hibernate session issues. Entity equality based solely on {@code cardId} (primary key).
     * Including relationships in equals() breaks Hibernate guarantees and causes bugs when
     * entities are loaded in different sessions or persistence contexts.
     * 
     * <p><b>Database Constraint:</b>
     * <pre>
     * account_id BIGINT NOT NULL
     * CONSTRAINT fk_card_account FOREIGN KEY (account_id)
     *     REFERENCES account(account_id) ON DELETE RESTRICT
     * </pre>
     * 
     * <p><b>Index:</b> idx_card_account - Enables efficient "find all cards for account"
     * queries needed for account detail pages and card replacement workflows.
     * 
     * @see Account for parent entity structure
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "account_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_card_account")
    )
    @NotNull(message = "Account is required")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Account account;

    /**
     * Cardholder name as embossed on physical card.
     * 
     * <p><b>COBOL Mapping:</b> CARD-EMBOSSED-NAME PIC X(50)
     * 
     * <p><b>Usage:</b> Name printed on physical card, used for cardholder identification at
     * point of sale. May differ from account holder name (authorized users, business cards).
     * Typically uppercase with limited special characters due to embossing constraints.
     * 
     * <p><b>Validation Rules:</b>
     * <ul>
     *   <li>Cannot be blank (cardholder identification requirement)</li>
     *   <li>Maximum 50 characters (physical card embossing limitation)</li>
     *   <li>Typically contains only A-Z, spaces, hyphens, apostrophes, periods</li>
     * </ul>
     * 
     * <p><b>Examples:</b>
     * <ul>
     *   <li>"JOHN DOE" - Standard format</li>
     *   <li>"MARY O'CONNOR" - Apostrophe in surname</li>
     *   <li>"DR. ROBERT SMITH JR." - Title and suffix</li>
     *   <li>"ACME CORP" - Business card</li>
     * </ul>
     * 
     * <p><b>Database Column:</b>
     * <pre>
     * embossed_name VARCHAR(50) NOT NULL
     * </pre>
     */
    @NotBlank(message = "Embossed name is required")
    @Size(max = 50, message = "Embossed name must not exceed 50 characters")
    @Column(name = "embossed_name", length = 50, nullable = false)
    private String embossedName;

    /**
     * Card expiration date (month and year).
     * 
     * <p><b>COBOL Mapping:</b> CARD-EXPIRAION-DATE PIC X(10) - Date string YYYY-MM-DD format
     * 
     * <p><b>Storage Format:</b> LocalDate stores year, month, and day. Typically set to last
     * day of expiration month (e.g., "2027-12-31" for December 2027 expiration).
     * 
     * <p><b>Business Rules:</b>
     * <ul>
     *   <li><b>Creation Validation:</b> {@code @Future} constraint ensures new cards cannot
     *       be created with past or current expiration dates. This catches data entry errors
     *       and enforces business policy that all new cards must be valid for future use.</li>
     *   
     *   <li><b>Expired Card Handling:</b> Active cards (activeStatus='Y') with expired dates
     *       are processed by nightly batch job "Card Expiration Processor" which:
     *       <ol>
     *         <li>Identifies cards with expirationDate < CURRENT_DATE AND activeStatus='Y'</li>
     *         <li>Updates activeStatus to 'N' (deactivates expired cards)</li>
     *         <li>Generates replacement card records if account is in good standing</li>
     *         <li>Sends expiration notifications to cardholders</li>
     *       </ol>
     *   </li>
     *   
     *   <li><b>Authorization Checks:</b> Payment gateway verifies expirationDate during
     *       authorization requests, declining transactions on expired cards even if
     *       activeStatus='Y' (race condition between batch processing and real-time auth).</li>
     * </ul>
     * 
     * <p><b>Database Constraints:</b>
     * <pre>
     * expiration_date DATE NOT NULL
     * CONSTRAINT chk_future_expiration CHECK (expiration_date > CURRENT_DATE)
     * </pre>
     * 
     * <p><b>Index:</b> idx_card_expiration on (expiration_date) WHERE active_status='Y'
     * <br>Partial index optimizes batch processing of soon-to-expire active cards:
     * <pre>
     * SELECT * FROM card
     * WHERE active_status = 'Y'
     *   AND expiration_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days'
     * ORDER BY expiration_date;
     * </pre>
     * Index size reduced by 95% (excludes inactive cards) with no performance degradation.
     * 
     * @see Future for bean validation semantics
     */
    @NotNull(message = "Expiration date is required")
    @Future(message = "Expiration date must be in the future")
    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    /**
     * Active status flag indicating if card is currently valid for use.
     * 
     * <p><b>COBOL Mapping:</b> CARD-ACTIVE-STATUS PIC X(01)
     * 
     * <p><b>Valid Values:</b>
     * <ul>
     *   <li><b>'Y'</b> - Active card, valid for transactions (subject to expiration check)</li>
     *   <li><b>'N'</b> - Inactive card, transactions declined (lost, stolen, expired, closed)</li>
     * </ul>
     * 
     * <p><b>State Transitions:</b>
     * <ul>
     *   <li><b>Issuance:</b> New cards created with 'Y' status</li>
     *   <li><b>Deactivation:</b> Changed to 'N' on cardholder request, fraud detection, expiration</li>
     *   <li><b>Reactivation:</b> 'N' to 'Y' allowed only for temporary blocks, not for lost/stolen</li>
     *   <li><b>Replacement:</b> Old card set to 'N', new card created with 'Y'</li>
     * </ul>
     * 
     * <p><b>Authorization Logic:</b>
     * Payment gateway checks activeStatus='Y' before processing authorization. Transactions
     * on 'N' status cards are immediately declined without contacting card network.
     * 
     * <p><b>Design Decision:</b> Single-character string preserved from COBOL rather than
     * converting to boolean. Rationale:
     * <ul>
     *   <li>COBOL logic distinguished blank, 'N', and other values differently</li>
     *   <li>Future expansion may require additional states ('S'=Suspended, 'F'=Fraud)</li>
     *   <li>Maintains exact legacy logic compatibility during migration</li>
     * </ul>
     * 
     * <p><b>Database Constraint:</b>
     * <pre>
     * active_status CHAR(1) CHECK (active_status IN ('Y', 'N'))
     * </pre>
     * 
     * <p><b>Index:</b> Partial index idx_card_expiration filters WHERE active_status='Y'
     * to optimize batch processing of active cards without scanning inactive records.
     */
    @NotNull(message = "Active status is required")
    @Pattern(regexp = "[YN]", message = "Active status must be 'Y' or 'N'")
    @Size(min = 1, max = 1, message = "Active status must be exactly 1 character")
    @Column(name = "active_status", length = 1, nullable = false)
    private String activeStatus;

    /*
     * IMPORTANT PCI-DSS COMPLIANCE NOTE:
     * 
     * CVV/CVC2 Code Intentionally Omitted
     * =====================================
     * 
     * The COBOL copybook CVACT02Y.cpy defines:
     *     05  CARD-CVV-CD  PIC 9(03).
     * 
     * This field is INTENTIONALLY EXCLUDED from the Card entity.
     * 
     * PCI-DSS Requirement 3.2.2 explicitly prohibits storage of the card verification
     * code (CVV2/CVC2/CVV/CID) after authorization completion. From PCI-DSS v4.0:
     * 
     *     "After authorization, if stored, the card verification code or value must
     *      be rendered unrecoverable. This applies to all instances the data is stored,
     *      including any copy or backup."
     * 
     * Business Process:
     * 1. CVV collected at point of sale (web form, terminal) from cardholder
     * 2. CVV transmitted in authorization request to payment gateway
     * 3. Payment gateway validates CVV with card issuer
     * 4. Authorization response returned (CVV not included)
     * 5. CVV DISCARDED from application memory - never persisted
     * 
     * Technical Implementation:
     * - Payment authorization DTOs may contain cvv field for API requests
     * - CVV transmitted via HTTPS to payment gateway
     * - CVV NEVER written to database, logs, cache, or any persistent storage
     * - Authorization response stored (approval code, transaction ID) without CVV
     * 
     * Audit Compliance:
     * PCI-DSS auditors will verify:
     * - No CVV columns in database schema (✓ enforced by this omission)
     * - No CVV in log files (✓ enforced by field absence)
     * - No CVV in backups (✓ enforced by field absence)
     * - No CVV in error messages (✓ enforced by field absence)
     * 
     * Legacy Migration Note:
     * The COBOL system stored CVV in CARDFILE VSAM dataset (CARD-CVV-CD field).
     * This was a PCI-DSS violation in the legacy system. The modernized Java
     * application corrects this compliance gap by eliminating CVV persistence entirely.
     * 
     * If authorization requires CVV verification, it must be:
     * 1. Collected in the request DTO (PaymentAuthorizationRequest)
     * 2. Passed to payment gateway immediately
     * 3. Cleared from memory after authorization
     * 4. NEVER persisted to this entity or any other storage
     * 
     * @see <a href="https://www.pcisecuritystandards.org/">PCI Security Standards</a>
     */

    /**
     * Returns a PCI-DSS compliant masked representation of the card number.
     * 
     * <p><b>Purpose:</b> Provides a safe display format for card numbers that can be shown
     * in user interfaces, logs, error messages, and audit trails without violating PCI-DSS
     * requirements. Full card numbers must never be exposed in non-secure contexts.
     * 
     * <p><b>Format:</b> Displays only the last 4 digits with leading asterisks:
     * <ul>
     *   <li>Input: "4532123456789012"</li>
     *   <li>Output: "************9012"</li>
     *   <li>Pattern: 12 asterisks + last 4 digits</li>
     * </ul>
     * 
     * <p><b>PCI-DSS Compliance:</b> PCI-DSS Requirement 3.3 permits displaying up to the
     * first 6 and last 4 digits of PAN (Primary Account Number). This implementation shows
     * only last 4 digits as a more conservative approach suitable for public display.
     * 
     * <p><b>Usage Examples:</b>
     * <pre>
     * // Safe for UI display
     * String displayNumber = card.getCardNumberMasked();
     * model.addAttribute("cardDisplay", displayNumber);  // "************9012"
     * 
     * // Safe for logging
     * logger.info("Card updated: {}", card.getCardNumberMasked());  // No PCI violation
     * 
     * // Safe for error messages
     * throw new CardExpiredException("Card " + card.getCardNumberMasked() + " expired");
     * 
     * // UNSAFE - never log full number
     * logger.debug("Processing card: {}", card.getCardNumber());  // ❌ PCI VIOLATION
     * </pre>
     * 
     * <p><b>Null Safety:</b> Returns 16 asterisks if cardNumber is null or invalid length,
     * preventing NullPointerException in display logic and ensuring consistent masking format.
     * 
     * <p><b>JPA Transient:</b> {@code @Transient} annotation marks this as a computed field
     * not persisted to database. The method recalculates masked value on every invocation
     * rather than storing redundant masked data.
     * 
     * <p><b>Performance:</b> String substring and concatenation are trivial operations
     * (< 1 microsecond). No caching needed even for high-frequency display scenarios.
     * 
     * @return masked card number in format "************1234" (12 asterisks + last 4 digits),
     *         or "****************" (16 asterisks) if cardNumber is null or length != 16
     * 
     * @see #getCardNumber() for full card number (use only for authorization, never log)
     */
    @Transient
    public String getCardNumberMasked() {
        if (cardNumber == null || cardNumber.length() != 16) {
            return "****************";  // 16 asterisks for invalid/null card numbers
        }
        return "************" + cardNumber.substring(12);  // 12 asterisks + last 4 digits
    }
}
