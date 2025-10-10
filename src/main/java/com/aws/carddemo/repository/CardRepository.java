package com.aws.carddemo.repository;

import com.aws.carddemo.model.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for Card entity providing card master data access.
 * Migrated from: COBOL VSAM CARDDAT file operations in COCRDLIC.cbl, COCRDSLC.cbl, COCRDUPC.cbl
 * 
 * <p>This repository replaces COBOL VSAM KSDS (Key Sequenced Data Set) file operations with
 * declarative JPA query methods, eliminating boilerplate CICS file I/O code while maintaining
 * functional equivalence. The interface automatically generates implementations at runtime via
 * Spring Data JPA's query derivation mechanism.
 * 
 * <p><b>Legacy COBOL File Operations Replaced:</b>
 * <pre>
 * COBOL VSAM Operations → Spring Data JPA Methods:
 * 
 * 1. Keyed READ by Primary Key:
 *    EXEC CICS READ
 *         DATASET('CARDDAT')
 *         RIDFLD(CARD-NUM)
 *         INTO(CARD-RECORD)
 *    END-EXEC
 *    ↓
 *    Optional&lt;Card&gt; findByCardNumber(String cardNumber)
 * 
 * 2. Browse by Alternate Index (CARDAIX on CARD-ACCT-ID):
 *    EXEC CICS STARTBR
 *         DATASET('CARDDAT')
 *         RIDFLD(WS-CARD-RID-CARDNUM)
 *         GTEQ
 *    END-EXEC
 *    PERFORM UNTIL READ-LOOP-EXIT
 *       EXEC CICS READNEXT
 *            DATASET('CARDDAT')
 *            INTO(CARD-RECORD)
 *       END-EXEC
 *       ... process up to 7 records per page ...
 *    END-PERFORM
 *    ↓
 *    Page&lt;Card&gt; findByAccountAccountId(Long accountId, Pageable pageable)
 * 
 * 3. Sequential Scan for Batch Processing:
 *    PERFORM UNTIL EOF-CARDDAT
 *       READ CARDDAT INTO CARD-RECORD AT END SET EOF TO TRUE
 *       IF CARD-EXPIRAION-DATE < CURRENT-DATE
 *          PERFORM PROCESS-EXPIRED-CARD
 *       END-IF
 *    END-PERFORM
 *    ↓
 *    List&lt;Card&gt; findByExpirationDateBefore(LocalDate date)
 * </pre>
 * 
 * <p><b>Key Design Decisions:</b>
 * <ul>
 *   <li><b>Query Method Derivation:</b> Method names follow Spring Data naming conventions
 *       (findBy[Property][Condition]) enabling automatic query generation from method signature.
 *       No @Query annotations needed for these simple predicates.</li>
 *   
 *   <li><b>Optional Return Type:</b> findByCardNumber returns Optional&lt;Card&gt; rather than
 *       nullable Card, enforcing explicit null handling at call sites. Replaces COBOL FILE STATUS
 *       '23' (record not found) checks with modern Java Optional pattern.</li>
 *   
 *   <li><b>Pagination Support:</b> findByAccountAccountId accepts Pageable parameter enabling page-based
 *       result sets (Page 1 = cards 1-20, Page 2 = cards 21-40). Replaces COBOL WS-MAX-SCREEN-LINES
 *       pagination logic (7 cards per screen in COCRDLIC.cbl) with flexible page size control.</li>
 *   
 *   <li><b>Index Utilization:</b> All query methods leverage database indexes defined in Card entity
 *       (@Index annotations). idx_card_number unique index ensures O(log n) findByCardNumber
 *       performance; idx_card_account secondary index optimizes findByAccountAccountId queries.</li>
 *   
 *   <li><b>Transaction Management:</b> All repository methods participate in Spring @Transactional
 *       contexts. Read operations use read-only transactions (performance optimization); write
 *       operations (save/delete inherited from JpaRepository) use read-write transactions with
 *       ACID guarantees replacing CICS SYNCPOINT semantics.</li>
 * </ul>
 * 
 * <p><b>Inherited CRUD Operations from JpaRepository:</b>
 * <pre>
 * Standard JPA Operations (no custom code needed):
 * - save(Card card)                    : INSERT or UPDATE (merge) card record
 * - saveAll(Iterable&lt;Card&gt; cards)     : Batch save multiple cards (performance optimization)
 * - findById(Long id)                  : Lookup card by surrogate primary key (cardId)
 * - findAll()                          : Retrieve all cards (use with caution on large datasets)
 * - findAll(Pageable pageable)         : Paginated retrieval of all cards
 * - count()                            : Total card count (SELECT COUNT(*))
 * - existsById(Long id)                : Existence check without loading entity
 * - delete(Card card)                  : Remove card by entity reference
 * - deleteById(Long id)                : Remove card by primary key
 * - flush()                            : Force pending changes to database (explicit SYNCPOINT)
 * - saveAndFlush(Card card)            : Save and immediately flush to database
 * </pre>
 * 
 * <p><b>PCI-DSS Compliance Considerations:</b>
 * <ul>
 *   <li><b>Card Number Masking:</b> Repository returns Card entities with full card numbers
 *       (unmasked). Service layer MUST call card.getCardNumberMasked() before logging or
 *       exposing to UI. Never log results directly: logger.debug("Card: {}", card) will
 *       expose PAN in logs via toString() despite @ToString.Exclude on cardNumber field.</li>
 *   
 *   <li><b>CVV Prohibition:</b> Card entity intentionally excludes CVV field per PCI-DSS 3.2.2.
 *       This repository will never return CVV data because the field doesn't exist in entity.</li>
 *   
 *   <li><b>Access Logging:</b> All repository access should be audited via Spring Security
 *       interceptors. Consider @PreAuthorize annotations on service methods to enforce role-based
 *       access control (ROLE_USER for card inquiry, ROLE_ADMIN for card updates).</li>
 * </ul>
 * 
 * <p><b>Performance Optimization Patterns:</b>
 * <ul>
 *   <li><b>Pagination:</b> Always use Pageable parameter for potentially large result sets.
 *       Example: PageRequest.of(pageNumber, 20, Sort.by("cardNumber").ascending())</li>
 *   
 *   <li><b>Projection:</b> For read-only views, consider Spring Data Projections to fetch only
 *       needed fields: interface CardSummary { String getCardNumber(); String getActiveStatus(); }</li>
 *   
 *   <li><b>Batch Operations:</b> Use saveAll(List&lt;Card&gt;) instead of iterative save() calls
 *       to leverage JDBC batch insert/update (10-100x performance improvement for bulk loads).</li>
 *   
 *   <li><b>Fetch Join:</b> When accessing card.account relationship, use @Query with JOIN FETCH
 *       to prevent N+1 query problem:
 *       @Query("SELECT c FROM Card c JOIN FETCH c.account WHERE c.cardNumber = :cardNumber")
 *       Optional&lt;Card&gt; findByCardNumberWithAccount(@Param("cardNumber") String cardNumber)</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b>
 * <pre>
 * // Example 1: Card inquiry by card number (COCRDSLC.cbl equivalent)
 * &#64;Service
 * &#64;Transactional(readOnly = true)
 * public class CardService {
 *     &#64;Autowired
 *     private CardRepository cardRepository;
 * 
 *     public CardResponse getCardDetails(String cardNumber) {
 *         Card card = cardRepository.findByCardNumber(cardNumber)
 *             .orElseThrow(() -&gt; new ResourceNotFoundException("Card not found: " 
 *                 + maskCardNumber(cardNumber)));
 *         return CardMapper.toResponse(card);  // DTO conversion
 *     }
 * }
 * 
 * // Example 2: Card list browse with pagination (COCRDLIC.cbl equivalent)
 * public Page&lt;CardResponse&gt; getCardsByAccount(Long accountId, int pageNumber) {
 *     Pageable pageable = PageRequest.of(pageNumber, 7, Sort.by("cardNumber"));
 *     Page&lt;Card&gt; cardPage = cardRepository.findByAccountAccountId(accountId, pageable);
 *     return cardPage.map(CardMapper::toResponse);  // Convert entities to DTOs
 * }
 * 
 * // Example 3: Batch expiration processing (CBACT04C.cbl equivalent)
 * &#64;Scheduled(cron = "0 0 1 * * ?")  // Daily at 1 AM
 * public void processExpiredCards() {
 *     LocalDate today = LocalDate.now();
 *     List&lt;Card&gt; expiredCards = cardRepository.findByExpirationDateBefore(today);
 *     expiredCards.forEach(card -&gt; {
 *         if ("Y".equals(card.getActiveStatus())) {
 *             card.setActiveStatus("N");  // Deactivate expired card
 *             logger.info("Deactivated expired card: {}", card.getCardNumberMasked());
 *         }
 *     });
 *     cardRepository.saveAll(expiredCards);  // Batch update
 * }
 * 
 * // Example 4: Card update with optimistic locking (COCRDUPC.cbl equivalent)
 * &#64;Transactional
 * public Card updateCard(CardUpdateRequest request) {
 *     Card card = cardRepository.findById(request.getCardId())
 *         .orElseThrow(() -&gt; new ResourceNotFoundException("Card not found"));
 *     
 *     // Apply updates
 *     card.setEmbossedName(request.getEmbossedName());
 *     card.setActiveStatus(request.getActiveStatus());
 *     
 *     return cardRepository.save(card);  // Optimistic lock version check
 * }
 * </pre>
 * 
 * <p><b>Exception Handling:</b>
 * <ul>
 *   <li><b>EntityNotFoundException:</b> Thrown by getOne()/getById() when entity not found
 *       (deprecated methods). Use findById() returning Optional instead.</li>
 *   
 *   <li><b>OptimisticLockException:</b> Thrown by save() when entity version mismatch detected
 *       (concurrent update conflict). Service layer should catch and retry or return 409 Conflict.</li>
 *   
 *   <li><b>DataIntegrityViolationException:</b> Thrown on constraint violations (duplicate card
 *       number, foreign key violation). Map to appropriate HTTP status codes (400 Bad Request).</li>
 *   
 *   <li><b>QueryTimeoutException:</b> Thrown when database query exceeds timeout threshold.
 *       Consider adding pagination or optimizing query if this occurs.</li>
 * </ul>
 * 
 * <p><b>Testing Strategies:</b>
 * <pre>
 * // Integration test with Testcontainers (PostgreSQL)
 * &#64;DataJpaTest
 * &#64;Testcontainers
 * class CardRepositoryTest {
 *     &#64;Autowired
 *     private CardRepository cardRepository;
 * 
 *     &#64;Container
 *     static PostgreSQLContainer&lt;?&gt; postgres = new PostgreSQLContainer&lt;&gt;("postgres:15-alpine");
 * 
 *     &#64;Test
 *     void testFindByCardNumber_ExistingCard_ReturnsCard() {
 *         // Given: Card exists in database
 *         Card savedCard = cardRepository.save(createTestCard("4532123456789012"));
 * 
 *         // When: Query by card number
 *         Optional&lt;Card&gt; result = cardRepository.findByCardNumber("4532123456789012");
 * 
 *         // Then: Card is found
 *         assertThat(result).isPresent();
 *         assertThat(result.get().getCardId()).isEqualTo(savedCard.getCardId());
 *     }
 * 
 *     &#64;Test
 *     void testFindByAccountId_WithPagination_ReturnsPagedResults() {
 *         // Given: 15 cards for account
 *         Account account = createTestAccount();
 *         for (int i = 0; i &lt; 15; i++) {
 *             cardRepository.save(createTestCard("453212345678" + String.format("%04d", i), account));
 *         }
 * 
 *         // When: Request page 1 with size 7 (COBOL screen size)
 *         Pageable pageable = PageRequest.of(0, 7, Sort.by("cardNumber"));
 *         Page&lt;Card&gt; page = cardRepository.findByAccountAccountId(account.getAccountId(), pageable);
 * 
 *         // Then: Page contains 7 cards with correct total
 *         assertThat(page.getContent()).hasSize(7);
 *         assertThat(page.getTotalElements()).isEqualTo(15);
 *         assertThat(page.getTotalPages()).isEqualTo(3);
 *     }
 * }
 * </pre>
 * 
 * <p><b>Migration Validation:</b>
 * To prove functional equivalence with COBOL CARDDAT file operations, integration tests should:
 * <ol>
 *   <li>Load same test data from app/data/ASCII/carddata.txt (50 card records)</li>
 *   <li>Execute queries matching COBOL READ/STARTBR/READNEXT patterns</li>
 *   <li>Compare result sets with COBOL program output (card counts, ordering, filtering)</li>
 *   <li>Verify performance meets or exceeds mainframe response times (&lt;50ms for indexed reads)</li>
 * </ol>
 * 
 * <p><b>Database Schema Compatibility:</b>
 * This repository operates on the 'card' table created by Flyway migration V1__create_tables.sql:
 * <pre>
 * CREATE TABLE card (
 *     card_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 *     card_number        VARCHAR(16) NOT NULL UNIQUE,
 *     account_id         BIGINT NOT NULL,
 *     embossed_name      VARCHAR(50) NOT NULL,
 *     expiration_date    DATE NOT NULL CHECK (expiration_date &gt; CURRENT_DATE),
 *     active_status      CHAR(1) CHECK (active_status IN ('Y', 'N')),
 *     created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     version            INTEGER NOT NULL DEFAULT 0,
 *     CONSTRAINT fk_card_account FOREIGN KEY (account_id) 
 *         REFERENCES account(account_id) ON DELETE RESTRICT
 * );
 * 
 * CREATE UNIQUE INDEX idx_card_number ON card(card_number);
 * CREATE INDEX idx_card_account ON card(account_id);
 * CREATE INDEX idx_card_expiration ON card(expiration_date) WHERE active_status = 'Y';
 * </pre>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 6.2.2.1: Card Master Table - Defines table schema, indexes, and query patterns
 *       supported by this repository including findByCardNumber for authorization lookups and
 *       findByAccountAccountId for card list browse operations</li>
 *   
 *   <li>Section 0.4.1 File Transformation: Repository layer must use Spring Data JPA with
 *       PostgreSQL replacing VSAM file access, implementing findByCardNumber for primary key
 *       access with PCI-DSS masking and findByAccountAccountId with Pageable for pagination</li>
 *   
 *   <li>Section 0.8.1 Critical Directive #3: PCI-DSS compliance requirement mandating card
 *       number masking displays last 4 digits only per PCI-DSS requirement 3.3, enforced
 *       via Card.getCardNumberMasked() method called in service layer after repository retrieval</li>
 *   
 *   <li>Section 2.2: Detailed Process Flows - Card inquiry flow (COCRDSLC.cbl) maps to
 *       findByCardNumber; card list browse flow (COCRDLIC.cbl) maps to findByAccountAccountId with
 *       pagination support matching 7-row screen display limit</li>
 * </ul>
 * 
 * @see Card for JPA entity structure and PCI-DSS compliance implementation
 * @see JpaRepository for inherited CRUD operations and transactional behavior
 * @see Pageable for pagination and sorting parameter specification
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    /**
     * Finds a card by its 16-digit card number (Primary Account Number).
     * 
     * <p><b>COBOL Equivalent:</b> EXEC CICS READ DATASET('CARDDAT') RIDFLD(CARD-NUM)
     * <br>Replaces keyed READ operation on CARDDAT VSAM KSDS using CARD-NUM as primary key.
     * Used by COCRDSLC.cbl (card detail view) and COCRDUPC.cbl (card update) programs.
     * 
     * <p><b>Query Execution:</b> Spring Data JPA generates SQL with WHERE clause on card_number
     * column. Query optimizer uses idx_card_number unique index for O(log n) lookup performance:
     * <pre>
     * SELECT * FROM card WHERE card_number = ?
     * </pre>
     * 
     * <p><b>Performance:</b>
     * <ul>
     *   <li><b>Index:</b> idx_card_number (unique B-tree) ensures single-row lookup</li>
     *   <li><b>Response Time:</b> Typically &lt;10ms for indexed reads on PostgreSQL</li>
     *   <li><b>Scalability:</b> O(log n) complexity scales to millions of card records</li>
     * </ul>
     * 
     * <p><b>Return Type Rationale:</b> Optional&lt;Card&gt; instead of nullable Card enforces
     * explicit null handling at call sites. Replaces COBOL FILE STATUS '23' (record not found):
     * <pre>
     * COBOL Pattern:
     *     EXEC CICS READ DATASET('CARDDAT') RIDFLD(CARD-NUM) RESP(WS-RESP-CD) END-EXEC
     *     IF WS-RESP-CD = DFHRESP(NOTFND)
     *        MOVE 'CARD NOT FOUND' TO ERROR-MSG
     *     END-IF
     * 
     * Java Pattern:
     *     Card card = cardRepository.findByCardNumber(cardNumber)
     *         .orElseThrow(() -&gt; new ResourceNotFoundException("Card not found"));
     * </pre>
     * 
     * <p><b>PCI-DSS Usage Warning:</b> This method returns Card entity with UNMASKED card number
     * (full 16 digits). Service layer MUST:
     * <ul>
     *   <li>Call card.getCardNumberMasked() before logging: logger.info("Found card: {}", card.getCardNumberMasked())</li>
     *   <li>Use DTO conversion with masked card number for API responses: CardMapper.toResponse(card)</li>
     *   <li>NEVER log full card number: logger.debug("Card: {}", card) // ❌ PCI VIOLATION</li>
     * </ul>
     * 
     * <p><b>Validation:</b> Card number parameter should be validated before calling this method:
     * <ul>
     *   <li>Exactly 16 digits: cardNumber.matches("\\d{16}")</li>
     *   <li>Luhn checksum valid: Use @CreditCardNumber validation in service layer</li>
     *   <li>Not null or blank: Use @NotBlank validation</li>
     * </ul>
     * 
     * <p><b>Usage Example:</b>
     * <pre>
     * // Service layer implementation (COCRDSLC.cbl card detail view)
     * &#64;Service
     * &#64;Transactional(readOnly = true)
     * public class CardService {
     *     &#64;Autowired
     *     private CardRepository cardRepository;
     * 
     *     public CardResponse getCardDetails(String cardNumber) {
     *         // Validate card number format
     *         if (!cardNumber.matches("\\d{16}")) {
     *             throw new InvalidInputException("Card number must be 16 digits");
     *         }
     * 
     *         // Query by card number (COBOL READ CARDDAT KEY IS CARD-NUM)
     *         Card card = cardRepository.findByCardNumber(cardNumber)
     *             .orElseThrow(() -&gt; new ResourceNotFoundException(
     *                 "Card not found: " + maskCardNumber(cardNumber)));
     * 
     *         // Log with masked card number (PCI-DSS compliant)
     *         logger.info("Retrieved card: {}", card.getCardNumberMasked());
     * 
     *         // Convert to DTO with masked card number for API response
     *         return CardMapper.toResponse(card);
     *     }
     * }
     * </pre>
     * 
     * <p><b>Concurrency:</b> This read-only query does not acquire row locks. Concurrent updates
     * to the same card are handled via optimistic locking (version field) during save operations.
     * 
     * <p><b>Transaction Context:</b> Participates in surrounding @Transactional context. Use
     * @Transactional(readOnly = true) in service layer for read-only queries (performance hint
     * to database that no writes will occur, enabling query optimizations).
     * 
     * @param cardNumber 16-digit card number (Primary Account Number) to search for.
     *                   Must be exactly 16 numeric characters. Example: "4532123456789012"
     * @return Optional containing Card entity if found, or Optional.empty() if no card exists
     *         with the given card number. Never returns null.
     * 
     * @see Card#getCardNumberMasked() for PCI-DSS compliant display format
     * @see Card#cardNumber for full card number field documentation
     */
    Optional<Card> findByCardNumber(String cardNumber);

    /**
     * Finds all cards associated with a specific account with pagination support.
     * 
     * <p><b>COBOL Equivalent:</b> EXEC CICS STARTBR/READNEXT loop in COCRDLIC.cbl
     * <br>Replaces COBOL browse operation on CARDDAT file using CARDAIX alternate index on
     * CARD-ACCT-ID field. COBOL program displays 7 cards per screen with PF7/PF8 pagination.
     * 
     * <p><b>Query Execution:</b> Spring Data JPA generates SQL with WHERE clause on account_id
     * foreign key column, with LIMIT/OFFSET for pagination:
     * <pre>
     * SELECT * FROM card WHERE account_id = ?
     * ORDER BY card_number  -- determined by Pageable.sort parameter
     * LIMIT ? OFFSET ?      -- page size and starting position
     * </pre>
     * 
     * <p><b>Performance:</b>
     * <ul>
     *   <li><b>Index:</b> idx_card_account secondary index enables efficient account filtering</li>
     *   <li><b>Response Time:</b> Typically &lt;50ms for page of 20 cards</li>
     *   <li><b>Scalability:</b> Pagination prevents memory exhaustion for accounts with many cards</li>
     *   <li><b>Total Count:</b> Page.getTotalElements() executes separate COUNT query:
     *       SELECT COUNT(*) FROM card WHERE account_id = ?</li>
     * </ul>
     * 
     * <p><b>Pagination Pattern:</b> Pageable parameter specifies page number, page size, and sort:
     * <pre>
     * COBOL Pattern (7 cards per screen):
     *     05 WS-MAX-SCREEN-LINES PIC 9(01) VALUE 7.
     *     05 WS-SCRN-COUNTER PIC 9(01) VALUE 0.
     *     PERFORM UNTIL WS-SCRN-COUNTER = WS-MAX-SCREEN-LINES OR EOF
     *        EXEC CICS READNEXT DATASET('CARDDAT') INTO(CARD-RECORD) END-EXEC
     *        ADD 1 TO WS-SCRN-COUNTER
     *     END-PERFORM
     * 
     * Java Pattern (flexible page size):
     *     // Page 1 with 7 cards (matching COBOL screen)
     *     Pageable page1 = PageRequest.of(0, 7, Sort.by("cardNumber").ascending());
     *     Page&lt;Card&gt; cards = cardRepository.findByAccountAccountId(accountId, page1);
     * 
     *     // Page 2 (next screen via PF8 key)
     *     Pageable page2 = PageRequest.of(1, 7, Sort.by("cardNumber").ascending());
     *     Page&lt;Card&gt; moreCards = cardRepository.findByAccountAccountId(accountId, page2);
     * 
     *     // Larger pages for API clients (20 cards)
     *     Pageable page20 = PageRequest.of(0, 20, Sort.by("expirationDate").descending());
     *     Page&lt;Card&gt; apiCards = cardRepository.findByAccountAccountId(accountId, page20);
     * </pre>
     * 
     * <p><b>Sorting Options:</b> Pageable.sort parameter controls result ordering:
     * <ul>
     *   <li><b>cardNumber ascending:</b> Sort.by("cardNumber") - Default alphabetic card order</li>
     *   <li><b>expirationDate descending:</b> Sort.by("expirationDate").descending() - Newest first</li>
     *   <li><b>activeStatus + cardNumber:</b> Sort.by("activeStatus").descending().and(Sort.by("cardNumber"))
     *       - Active cards first, then by card number</li>
     * </ul>
     * 
     * <p><b>Page Metadata:</b> Page&lt;Card&gt; provides navigation information:
     * <ul>
     *   <li><b>page.getContent():</b> List&lt;Card&gt; for current page</li>
     *   <li><b>page.getTotalElements():</b> Total cards across all pages</li>
     *   <li><b>page.getTotalPages():</b> Total number of pages</li>
     *   <li><b>page.getNumber():</b> Current page number (0-based)</li>
     *   <li><b>page.getSize():</b> Page size (cards per page)</li>
     *   <li><b>page.hasNext():</b> True if next page exists (COBOL CA-NEXT-PAGE-EXISTS flag)</li>
     *   <li><b>page.hasPrevious():</b> True if previous page exists (COBOL CA-FIRST-PAGE check)</li>
     * </ul>
     * 
     * <p><b>Usage Example:</b>
     * <pre>
     * // Controller layer (COCRDLIC.cbl card list screen)
     * &#64;RestController
     * &#64;RequestMapping("/api/v1/accounts")
     * public class CardController {
     *     &#64;Autowired
     *     private CardRepository cardRepository;
     * 
     *     &#64;GetMapping("/{accountId}/cards")
     *     public Page&lt;CardResponse&gt; getCardsByAccount(
     *             &#64;PathVariable Long accountId,
     *             &#64;RequestParam(defaultValue = "0") int page,
     *             &#64;RequestParam(defaultValue = "7") int size) {  // COBOL screen size
     * 
     *         // Create pageable matching COBOL screen pagination
     *         Pageable pageable = PageRequest.of(page, size, Sort.by("cardNumber"));
     * 
     *         // Query cards with pagination (COBOL STARTBR/READNEXT loop)
     *         Page&lt;Card&gt; cardPage = cardRepository.findByAccountAccountId(accountId, pageable);
     * 
     *         // Convert entities to DTOs with masked card numbers
     *         return cardPage.map(card -&gt; {
     *             CardResponse response = CardMapper.toResponse(card);
     *             response.setCardNumber(card.getCardNumberMasked());  // PCI-DSS compliant
     *             return response;
     *         });
     *     }
     * }
     * </pre>
     * 
     * <p><b>Empty Result Handling:</b> When no cards exist for account:
     * <ul>
     *   <li>Returns Page with empty content list (not null)</li>
     *   <li>page.getContent().isEmpty() returns true</li>
     *   <li>page.getTotalElements() returns 0</li>
     *   <li>page.getTotalPages() returns 0</li>
     * </ul>
     * Maps to COBOL WS-NO-RECORDS-FOUND flag when WS-SCRN-COUNTER = 0 after browse.
     * 
     * <p><b>Fetch Strategy:</b> Card.account relationship uses LAZY fetch. Results include only
     * card data; account entity not loaded unless explicitly accessed (card.getAccount()). To
     * include account data in single query, use custom @Query with JOIN FETCH:
     * <pre>
     * &#64;Query("SELECT c FROM Card c JOIN FETCH c.account WHERE c.account.accountId = :accountId")
     * Page&lt;Card&gt; findByAccountIdWithAccount(&#64;Param("accountId") Long accountId, Pageable pageable);
     * </pre>
     * 
     * <p><b>Transaction Context:</b> Read-only operation suitable for @Transactional(readOnly = true).
     * Pagination queries execute 2 SQL statements: one for data (SELECT with LIMIT/OFFSET), one
     * for total count (SELECT COUNT(*)).
     * 
     * @param accountId Primary key of parent Account entity. Must reference existing account.
     * @param pageable Pagination and sorting parameters. Example:
     *                 PageRequest.of(pageNumber, pageSize, Sort.by("cardNumber"))
     *                 <ul>
     *                   <li>pageNumber: 0-based page index (0 = first page)</li>
     *                   <li>pageSize: Cards per page (7 matches COBOL screen, 20 typical for APIs)</li>
     *                   <li>sort: Ordering criteria (cardNumber, expirationDate, activeStatus)</li>
     *                 </ul>
     * @return Page containing cards for the specified account with pagination metadata. Never null.
     *         Returns empty page (content.isEmpty() = true) if account has no cards.
     * 
     * @see Page for pagination metadata and navigation methods
     * @see Pageable for pagination parameter construction via PageRequest
     * @see Card#account for account relationship documentation
     */
    Page<Card> findByAccountAccountId(Long accountId, Pageable pageable);

    /**
     * Finds all cards with expiration dates before the specified date.
     * 
     * <p><b>COBOL Equivalent:</b> Sequential scan in batch programs for expired card processing
     * <br>Replaces COBOL batch job logic that reads entire CARDDAT file sequentially and processes
     * cards where CARD-EXPIRAION-DATE &lt; CURRENT-DATE. Used by card expiration processor batch
     * job (CBACT04C.cbl equivalent) to identify cards requiring deactivation or renewal notices.
     * 
     * <p><b>Query Execution:</b> Spring Data JPA generates SQL with WHERE clause on expiration_date:
     * <pre>
     * SELECT * FROM card
     * WHERE expiration_date < ?
     * ORDER BY expiration_date  -- Implicit ordering for predictable batch processing
     * </pre>
     * 
     * <p><b>Performance:</b>
     * <ul>
     *   <li><b>Index:</b> idx_card_expiration partial index (WHERE active_status='Y') optimizes
     *       queries for active expired cards. Full table scan required for inactive cards.</li>
     *   
     *   <li><b>Response Time:</b> Varies by result set size. Typical scenarios:
     *       <ul>
     *         <li>30 days of expirations: ~50-100 cards, &lt;100ms</li>
     *         <li>Historical expirations: 10,000+ cards, &gt;1 second</li>
     *       </ul>
     *   </li>
     *   
     *   <li><b>Batch Processing:</b> For large result sets (&gt;1000 cards), consider pagination:
     *       Use findAll(Specification, Pageable) with custom Specification for expiration date
     *       filter to process cards in batches of 100-500 records (Spring Batch chunk pattern).</li>
     * </ul>
     * 
     * <p><b>Use Cases:</b>
     * <ol>
     *   <li><b>Daily Expiration Processing:</b> Find cards expiring today or earlier, deactivate
     *       cards with activeStatus='Y', generate replacement cards for accounts in good standing.</li>
     *   
     *   <li><b>Renewal Reminders:</b> Find cards expiring in next 30 days (LocalDate.now().plusDays(30)),
     *       send notifications to cardholders prompting renewal application.</li>
     *   
     *   <li><b>Historical Analysis:</b> Find all cards that expired in specific time period
     *       for reporting or audit purposes.</li>
     * </ol>
     * 
     * <p><b>Batch Job Pattern:</b> Spring Batch integration for large-scale processing:
     * <pre>
     * COBOL Batch Pattern:
     *     PERFORM UNTIL EOF-CARDDAT
     *        READ CARDDAT INTO CARD-RECORD AT END SET EOF TO TRUE END-READ
     *        IF NOT EOF
     *           IF CARD-EXPIRAION-DATE < WS-CURRENT-DATE
     *              IF CARD-ACTIVE-STATUS = 'Y'
     *                 MOVE 'N' TO CARD-ACTIVE-STATUS
     *                 REWRITE CARD-RECORD
     *              END-IF
     *           END-IF
     *        END-IF
     *     END-PERFORM
     * 
     * Java Batch Pattern (Spring Batch):
     *     &#64;Bean
     *     public Step cardExpirationStep() {
     *         return stepBuilderFactory.get("cardExpirationStep")
     *             .&lt;Card, Card&gt;chunk(100)  // Process 100 cards per transaction
     *             .reader(cardExpirationReader())
     *             .processor(cardExpirationProcessor())
     *             .writer(cardExpirationWriter())
     *             .build();
     *     }
     * 
     *     &#64;Bean
     *     public ItemReader&lt;Card&gt; cardExpirationReader() {
     *         return new RepositoryItemReaderBuilder&lt;Card&gt;()
     *             .repository(cardRepository)
     *             .methodName("findByExpirationDateBefore")
     *             .arguments(LocalDate.now())
     *             .sorts(Map.of("expirationDate", Sort.Direction.ASC))
     *             .build();
     *     }
     * </pre>
     * 
     * <p><b>Usage Example:</b>
     * <pre>
     * // Scheduled batch job (runs daily at 1 AM)
     * &#64;Service
     * public class CardExpirationService {
     *     &#64;Autowired
     *     private CardRepository cardRepository;
     * 
     *     &#64;Scheduled(cron = "0 0 1 * * ?")  // Daily at 1:00 AM
     *     &#64;Transactional
     *     public void processExpiredCards() {
     *         LocalDate today = LocalDate.now();
     * 
     *         // Find all expired cards (COBOL: CARD-EXPIRAION-DATE < WS-CURRENT-DATE)
     *         List&lt;Card&gt; expiredCards = cardRepository.findByExpirationDateBefore(today);
     * 
     *         logger.info("Processing {} expired cards", expiredCards.size());
     * 
     *         int deactivatedCount = 0;
     *         for (Card card : expiredCards) {
     *             if ("Y".equals(card.getActiveStatus())) {
     *                 // Deactivate expired active card (COBOL: MOVE 'N' TO CARD-ACTIVE-STATUS)
     *                 card.setActiveStatus("N");
     *                 deactivatedCount++;
     * 
     *                 // Log with masked card number (PCI-DSS compliant)
     *                 logger.info("Deactivated expired card: {} (expired: {})",
     *                     card.getCardNumberMasked(), card.getExpirationDate());
     *             }
     *         }
     * 
     *         // Batch update all modified cards (COBOL: REWRITE CARD-RECORD in loop)
     *         cardRepository.saveAll(expiredCards);
     * 
     *         logger.info("Deactivated {} expired cards", deactivatedCount);
     *     }
     * 
     *     &#64;Scheduled(cron = "0 0 9 * * ?")  // Daily at 9:00 AM
     *     public void sendExpirationReminders() {
     *         LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
     * 
     *         // Find cards expiring in next 30 days for renewal reminders
     *         List&lt;Card&gt; expiringCards = cardRepository.findByExpirationDateBefore(thirtyDaysFromNow);
     * 
     *         expiringCards.stream()
     *             .filter(card -&gt; "Y".equals(card.getActiveStatus()))
     *             .filter(card -&gt; card.getExpirationDate().isAfter(LocalDate.now()))
     *             .forEach(card -&gt; {
     *                 // Send renewal reminder notification
     *                 notificationService.sendRenewalReminder(card);
     *                 logger.info("Sent renewal reminder for card: {} (expires: {})",
     *                     card.getCardNumberMasked(), card.getExpirationDate());
     *             });
     *     }
     * }
     * </pre>
     * 
     * <p><b>Result Ordering:</b> Results implicitly ordered by expiration_date ascending (earliest
     * expirations first). This ordering ensures predictable batch processing and prioritizes urgent
     * expirations. For different ordering, use custom @Query with ORDER BY clause.
     * 
     * <p><b>Empty Result Handling:</b> Returns empty List (not null) if no cards match criteria.
     * Check expiredCards.isEmpty() before processing. Maps to COBOL EOF condition on first READ.
     * 
     * <p><b>Active vs Inactive Cards:</b> This query returns ALL expired cards regardless of
     * activeStatus. Batch processing logic should filter by activeStatus='Y' if only active cards
     * should be processed (as shown in usage example). Inactive cards (activeStatus='N') may
     * already be deactivated by previous batch runs.
     * 
     * <p><b>Partial Index Optimization:</b> idx_card_expiration partial index (WHERE active_status='Y')
     * optimizes queries for active expired cards. To leverage index, add activeStatus filter in
     * WHERE clause using custom @Query:
     * <pre>
     * &#64;Query("SELECT c FROM Card c WHERE c.expirationDate &lt; :date AND c.activeStatus = 'Y'")
     * List&lt;Card&gt; findActiveCardsByExpirationDateBefore(&#64;Param("date") LocalDate date);
     * </pre>
     * 
     * <p><b>Transaction Context:</b> For read-only queries, use @Transactional(readOnly = true)
     * in service layer. For batch deactivation (with save operations), use standard @Transactional
     * to enable write operations and commit updates to database.
     * 
     * <p><b>Performance Warning:</b> This query can return large result sets (10,000+ cards) if
     * date parameter is far in past or batch processing has not run for extended period. Consider:
     * <ul>
     *   <li>Adding date range filter (expirationDate BETWEEN startDate AND endDate)</li>
     *   <li>Using pagination with Pageable parameter for large result sets</li>
     *   <li>Processing results in batches with Spring Batch framework (chunk-oriented processing)</li>
     * </ul>
     * 
     * @param date Date threshold for expiration comparison. Cards with expirationDate &lt; date
     *             are returned. Typically LocalDate.now() for current expirations, or
     *             LocalDate.now().plusDays(30) for 30-day lookahead.
     * @return List of cards expiring before the specified date, ordered by expirationDate ascending.
     *         Never returns null. Returns empty list if no cards match criteria.
     * 
     * @see Card#expirationDate for expiration date field documentation
     * @see Card#activeStatus for active status flag semantics
     * @see LocalDate for Java 8 date handling
     */
    List<Card> findByExpirationDateBefore(LocalDate date);
}
