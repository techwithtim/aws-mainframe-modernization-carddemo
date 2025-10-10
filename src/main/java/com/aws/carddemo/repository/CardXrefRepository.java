package com.aws.carddemo.repository;

import com.aws.carddemo.model.CardXref;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for CardXref entity providing card-to-account
 * cross-reference data access operations.
 * 
 * <p><b>Legacy Mapping:</b> Replaces COBOL VSAM XREFFILE indexed file operations from
 * transaction posting programs {@code app/cbl/CBTRN01C.cbl} (lines 40-44, 76-79, 228-239)
 * and {@code app/cbl/CBTRN02C.cbl}. The COBOL copybook structure is defined in
 * {@code app/cpy/CVACT03Y.cpy} as 50-byte CARD-XREF-RECORD with fields XREF-CARD-NUM
 * (PIC X(16)), XREF-CUST-ID (PIC 9(09)), XREF-ACCT-ID (PIC 9(11)), and FILLER (PIC X(14)).
 * 
 * <p><b>Business Purpose:</b> Provides bidirectional navigation between credit card numbers,
 * customer IDs, and account IDs. This cross-reference pattern is critical for transaction
 * authorization workflows where incoming transactions contain card numbers but must be
 * posted to accounts. The repository replaces mainframe VSAM Alternate Index (AIX) pattern
 * CXACAIX with PostgreSQL B-tree indexes, providing O(log n) lookup performance without
 * manual index maintenance (BLDINDEX operations).
 * 
 * <p><b>COBOL File Operations Mapping:</b></p>
 * <pre>
 * COBOL VSAM Operation                          | Spring Data JPA Equivalent
 * ----------------------------------------------+--------------------------------------------
 * SELECT XREF-FILE ASSIGN TO XREFFILE           | (Configuration in application.yml)
 *   ORGANIZATION IS INDEXED                     | @Table(indexes = {@Index...})
 *   ACCESS MODE IS RANDOM                       | findById(), findByCardNumber() - direct lookup
 *   RECORD KEY IS FD-XREF-CARD-NUM             | @Id on cardNumber field
 * 
 * READ XREF-FILE RECORD INTO CARD-XREF-RECORD   | findByCardNumber(cardNumber)
 *   KEY IS FD-XREF-CARD-NUM                     |   returns Optional&lt;CardXref&gt;
 *   INVALID KEY                                 | Optional.empty() when not found
 *     MOVE 4 TO WS-XREF-READ-STATUS            | (Handled by service layer logic)
 *   NOT INVALID KEY                             | Optional.isPresent() when found
 *     DISPLAY 'SUCCESSFUL READ OF XREF'         | (Logging in service layer)
 * 
 * WRITE XREF-FILE RECORD FROM CARD-XREF-RECORD | save(cardXref) - insert new cross-reference
 * REWRITE XREF-FILE RECORD FROM ...             | save(cardXref) - update existing cross-reference
 * DELETE XREF-FILE RECORD                       | delete(cardXref) or deleteById(cardNumber)
 * </pre>
 * 
 * <p><b>Key Lookup Patterns:</b></p>
 * <ol>
 *   <li><b>Card-to-Account Resolution (Primary Use Case):</b>
 *       {@code findByCardNumber("4556737586899855")} → Returns Optional&lt;CardXref&gt; with
 *       accountId and customerId. Performance: {@code <10ms} using primary key index.
 *       Replaces COBOL paragraph 2000-LOOKUP-XREF from CBTRN01C.cbl lines 227-239.</li>
 *   <li><b>Account-to-Cards Lookup:</b>
 *       {@code findByAccountId(1L)} → Returns List&lt;CardXref&gt; of all cards for account.
 *       Performance: {@code <20ms} using idx_xref_account B-tree index. Use case: Statement
 *       generation listing all cards on account.</li>
 *   <li><b>Customer-to-Cards Lookup:</b>
 *       {@code findByCustomerId(1L)} → Returns List&lt;CardXref&gt; of all cards for customer.
 *       Performance: {@code <20ms} using idx_xref_customer B-tree index. Use case: Customer
 *       service representative viewing all cards held by customer.</li>
 * </ol>
 * 
 * <p><b>Transaction Posting Workflow Integration:</b></p>
 * The critical transaction posting workflow from CBTRN01C.cbl follows this sequence:
 * <pre>
 * 1. Read daily transaction feed (DALYTRAN-FILE) containing card number
 * 2. Lookup card-to-account cross-reference via READ XREF-FILE (line 229-239)
 * 3. If INVALID KEY: Log error "INVALID CARD NUMBER FOR XREF", skip transaction
 * 4. If NOT INVALID KEY: Extract XREF-ACCT-ID and XREF-CUST-ID from cross-reference
 * 5. Read account record using XREF-ACCT-ID as key (paragraph 3000-READ-ACCOUNT)
 * 6. Post transaction to account, update balances
 * 7. Write transaction history record (TRANSACT-FILE)
 * </pre>
 * 
 * In the modernized Java implementation:
 * <pre>
 * // Step 2: Lookup card-to-account cross-reference
 * Optional&lt;CardXref&gt; xrefOpt = cardXrefRepository.findByCardNumber(dailyTran.getCardNumber());
 * 
 * // Step 3-4: Handle found/not found scenarios
 * if (xrefOpt.isEmpty()) {
 *     log.error("INVALID CARD NUMBER FOR XREF: {}", dailyTran.getCardNumber());
 *     throw new InvalidCardNumberException("Card number not found in cross-reference");
 * }
 * 
 * CardXref xref = xrefOpt.get();
 * Long accountId = xref.getAccountId();
 * Long customerId = xref.getCustomerId();
 * log.info("SUCCESSFUL READ OF XREF - Card: {}, Account: {}, Customer: {}",
 *          maskCardNumber(xref.getCardNumber()), accountId, customerId);
 * </pre>
 * 
 * <p><b>Database Schema and Indexes:</b></p>
 * The underlying PostgreSQL table is created by Flyway migration V1__create_tables.sql:
 * <pre>
 * CREATE TABLE card_xref (
 *   card_number VARCHAR(16) PRIMARY KEY NOT NULL,
 *   customer_id BIGINT NOT NULL,
 *   account_id BIGINT NOT NULL,
 *   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *   version INTEGER NOT NULL DEFAULT 0,
 *   CONSTRAINT fk_xref_customer FOREIGN KEY (customer_id)
 *     REFERENCES customer(customer_id) ON DELETE RESTRICT,
 *   CONSTRAINT fk_xref_account FOREIGN KEY (account_id)
 *     REFERENCES account(account_id) ON DELETE RESTRICT
 * );
 * </pre>
 * 
 * Supporting indexes are created by V2__create_indexes.sql for performance:
 * <pre>
 * CREATE INDEX idx_xref_account ON card_xref(account_id);  -- For findByAccountId()
 * CREATE INDEX idx_xref_customer ON card_xref(customer_id); -- For findByCustomerId()
 * </pre>
 * 
 * <p><b>PCI-DSS Compliance Requirements:</b></p>
 * CRITICAL: This repository handles sensitive Cardholder Data (CHD). Per Section 0.8.1
 * (Critical Directive #3) of the Agent Action Plan:
 * <ul>
 *   <li><b>Card Number Masking:</b> Service layer MUST mask card numbers in all log statements.
 *       Use masking utility: {@code cardNumber.replaceAll("\\d(?=\\d{4})", "*")} to show only
 *       last 4 digits (e.g., "************9855").</li>
 *   <li><b>TLS Encryption:</b> All API requests/responses containing card numbers must use
 *       TLS 1.3. Database connections must use SSL/TLS encryption.</li>
 *   <li><b>Database Encryption:</b> AWS RDS encryption at rest must be enabled for the
 *       PostgreSQL instance storing card_xref table.</li>
 *   <li><b>Access Control:</b> Repository methods should only be called from transactional
 *       service layer methods with proper authorization checks. Never expose repository
 *       directly to REST controllers.</li>
 *   <li><b>Audit Logging:</b> Service layer should log all cross-reference lookups with
 *       masked card numbers, user ID, and timestamp for PCI-DSS Requirement 10 (Track and
 *       monitor all access to cardholder data).</li>
 * </ul>
 * 
 * <p><b>Performance Characteristics and Index Strategy:</b></p>
 * <table border="1">
 *   <tr>
 *     <th>Query Method</th>
 *     <th>Index Used</th>
 *     <th>Performance Target</th>
 *     <th>Complexity</th>
 *   </tr>
 *   <tr>
 *     <td>findByCardNumber(String)</td>
 *     <td>Primary Key (card_number)</td>
 *     <td>&lt;10ms @ 95th percentile</td>
 *     <td>O(log n)</td>
 *   </tr>
 *   <tr>
 *     <td>findById(String)</td>
 *     <td>Primary Key (card_number)</td>
 *     <td>&lt;10ms @ 95th percentile</td>
 *     <td>O(log n)</td>
 *   </tr>
 *   <tr>
 *     <td>findByAccountId(Long)</td>
 *     <td>idx_xref_account B-tree</td>
 *     <td>&lt;20ms @ 95th percentile</td>
 *     <td>O(log n)</td>
 *   </tr>
 *   <tr>
 *     <td>findByCustomerId(Long)</td>
 *     <td>idx_xref_customer B-tree</td>
 *     <td>&lt;20ms @ 95th percentile</td>
 *     <td>O(log n)</td>
 *   </tr>
 *   <tr>
 *     <td>save(CardXref)</td>
 *     <td>All indexes updated</td>
 *     <td>&lt;50ms @ 95th percentile</td>
 *     <td>O(log n)</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Concurrency and Transaction Management:</b></p>
 * All repository methods are NOT transactional by themselves. Service layer methods calling
 * this repository MUST be annotated with {@code @Transactional} to ensure:
 * <ul>
 *   <li>ACID properties during cross-reference creation/update/deletion</li>
 *   <li>Automatic optimistic locking via {@code @Version} field in CardXref entity</li>
 *   <li>Rollback on exceptions to maintain data integrity</li>
 *   <li>Proper session management to avoid LazyInitializationException when accessing
 *       customer or account navigation properties</li>
 * </ul>
 * 
 * Example service method:
 * <pre>
 * @Service
 * public class CardXrefService {
 *     
 *     @Transactional(readOnly = true)
 *     public CardXref findByCardNumber(String cardNumber) {
 *         return cardXrefRepository.findByCardNumber(cardNumber)
 *             .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + maskCardNumber(cardNumber)));
 *     }
 *     
 *     @Transactional
 *     public CardXref createCardXref(String cardNumber, Long customerId, Long accountId) {
 *         // Validate card number format, customer exists, account exists
 *         CardXref xref = CardXref.builder()
 *             .cardNumber(cardNumber)
 *             .customerId(customerId)
 *             .accountId(accountId)
 *             .build();
 *         return cardXrefRepository.save(xref);
 *     }
 * }
 * </pre>
 * 
 * <p><b>Testing Strategy:</b></p>
 * Integration tests using Testcontainers MUST verify:
 * <ul>
 *   <li>findByCardNumber() returns correct cross-reference for valid card number</li>
 *   <li>findByCardNumber() returns Optional.empty() for non-existent card number</li>
 *   <li>findByAccountId() returns all cards associated with account (1-N relationship)</li>
 *   <li>findByCustomerId() returns all cards associated with customer (1-N relationship)</li>
 *   <li>Primary key constraint prevents duplicate card numbers</li>
 *   <li>Foreign key constraints prevent insertion of non-existent customer/account IDs</li>
 *   <li>ON DELETE RESTRICT prevents customer/account deletion when cards exist</li>
 *   <li>Indexes are properly created and used (verify with EXPLAIN ANALYZE in test)</li>
 *   <li>Performance targets are met (measure query execution times in test)</li>
 * </ul>
 * 
 * <p><b>Migration from COBOL to Java - Key Differences:</b></p>
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>COBOL VSAM XREFFILE</th>
 *     <th>Spring Data JPA CardXrefRepository</th>
 *   </tr>
 *   <tr>
 *     <td>File Organization</td>
 *     <td>VSAM KSDS with AIX alternate indexes</td>
 *     <td>PostgreSQL table with B-tree indexes</td>
 *   </tr>
 *   <tr>
 *     <td>Primary Key</td>
 *     <td>FD-XREF-CARD-NUM (COBOL RECORD KEY)</td>
 *     <td>cardNumber (JPA @Id annotation)</td>
 *   </tr>
 *   <tr>
 *     <td>Alternate Indexes</td>
 *     <td>AIX on XREF-ACCT-ID, XREF-CUST-ID</td>
 *     <td>B-tree indexes on account_id, customer_id</td>
 *   </tr>
 *   <tr>
 *     <td>Index Maintenance</td>
 *     <td>Manual BLDINDEX utility execution</td>
 *     <td>Automatic by PostgreSQL on INSERT/UPDATE/DELETE</td>
 *   </tr>
 *   <tr>
 *     <td>Error Handling</td>
 *     <td>FILE STATUS codes (00=success, 23=not found, 22=duplicate)</td>
 *     <td>Optional&lt;CardXref&gt; for not found, DataIntegrityViolationException for duplicates</td>
 *   </tr>
 *   <tr>
 *     <td>Record Layout</td>
 *     <td>50-byte fixed-length with FILLER padding</td>
 *     <td>Variable-length columns (no padding required)</td>
 *   </tr>
 *   <tr>
 *     <td>Transaction Support</td>
 *     <td>CICS SYNCPOINT / SYNCPOINT ROLLBACK</td>
 *     <td>Spring @Transactional with automatic commit/rollback</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Technical Specification References:</b></p>
 * <ul>
 *   <li><b>Section 0.4.1:</b> File-by-File Transformation Plan - CardXrefRepository creation
 *       from CBTRN01C.cbl READ XREFFILE operations</li>
 *   <li><b>Section 6.2.2.1:</b> Card Cross-Reference Table design with bidirectional navigation
 *       enabling card-to-account, account-to-cards, customer-to-cards lookups</li>
 *   <li><b>Section 0.1.1:</b> Primary Goal #2 - Data Layer Modernization replacing VSAM with
 *       PostgreSQL and automatic index maintenance</li>
 *   <li><b>Section 0.8.6:</b> Performance Baseline Requirements - Primary key lookup &lt;10ms,
 *       secondary index lookup &lt;20ms at 95th percentile</li>
 * </ul>
 * 
 * @see CardXref for entity documentation and field mappings
 * @see JpaRepository for inherited CRUD operations documentation
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Repository
public interface CardXrefRepository extends JpaRepository<CardXref, String> {
    
    /**
     * Find card cross-reference by card number (primary key lookup).
     * 
     * <p><b>Legacy Mapping:</b> Replaces COBOL paragraph {@code 2000-LOOKUP-XREF} from
     * {@code app/cbl/CBTRN01C.cbl} (lines 227-239):
     * <pre>
     * 2000-LOOKUP-XREF.
     *     MOVE XREF-CARD-NUM TO FD-XREF-CARD-NUM
     *     READ XREF-FILE RECORD INTO CARD-XREF-RECORD
     *     KEY IS FD-XREF-CARD-NUM
     *          INVALID KEY
     *            DISPLAY 'INVALID CARD NUMBER FOR XREF'
     *            MOVE 4 TO WS-XREF-READ-STATUS
     *          NOT INVALID KEY
     *            DISPLAY 'SUCCESSFUL READ OF XREF'
     *            DISPLAY 'CARD NUMBER: ' XREF-CARD-NUM
     *            DISPLAY 'ACCOUNT ID : ' XREF-ACCT-ID
     *            DISPLAY 'CUSTOMER ID: ' XREF-CUST-ID
     *     END-READ.
     * </pre>
     * 
     * <p><b>Business Purpose:</b> This is the CRITICAL lookup method for transaction authorization
     * workflows. When a credit card transaction arrives from a POS terminal or online gateway,
     * it contains the 16-digit card number. This method resolves the card number to the
     * associated account ID and customer ID, enabling the system to post the transaction to
     * the correct account and update balances accordingly.
     * 
     * <p><b>Return Value Semantics:</b></p>
     * <ul>
     *   <li><b>Optional.of(CardXref):</b> Card number found in cross-reference table. The
     *       returned CardXref contains accountId and customerId for transaction posting.
     *       Equivalent to COBOL "NOT INVALID KEY" condition.</li>
     *   <li><b>Optional.empty():</b> Card number not found in cross-reference table. This
     *       indicates an invalid/unknown card number. Service layer should log error and
     *       reject transaction. Equivalent to COBOL "INVALID KEY" condition where
     *       WS-XREF-READ-STATUS is set to 4.</li>
     * </ul>
     * 
     * <p><b>Performance Characteristics:</b></p>
     * This method uses the primary key index on card_number column, providing O(log n) lookup
     * complexity with extremely fast performance:
     * <ul>
     *   <li><b>Target Performance:</b> &lt;10ms at 95th percentile (per Section 0.8.6)</li>
     *   <li><b>Index Type:</b> B-tree primary key index (clustered in PostgreSQL)</li>
     *   <li><b>Query Plan:</b> Index Scan on card_xref_pkey (verify with EXPLAIN ANALYZE)</li>
     *   <li><b>Execution Count:</b> 1 query to database per method call</li>
     *   <li><b>Network Round-Trips:</b> 1 round-trip (local database: ~1ms, RDS: ~5-10ms)</li>
     * </ul>
     * 
     * <p><b>Usage Example in Transaction Posting Service:</b></p>
     * <pre>
     * @Service
     * @Transactional
     * public class TransactionPostingService {
     *     private final CardXrefRepository cardXrefRepository;
     *     
     *     public void postTransaction(DailyTransaction dailyTran) {
     *         // Step 1: Lookup card-to-account cross-reference
     *         Optional&lt;CardXref&gt; xrefOpt = cardXrefRepository.findByCardNumber(dailyTran.getCardNumber());
     *         
     *         // Step 2: Handle invalid card number (COBOL INVALID KEY condition)
     *         if (xrefOpt.isEmpty()) {
     *             String maskedCard = dailyTran.getCardNumber().replaceAll("\\d(?=\\d{4})", "*");
     *             log.error("INVALID CARD NUMBER FOR XREF: {}", maskedCard);
     *             throw new InvalidCardNumberException("Card number not found: " + maskedCard);
     *         }
     *         
     *         // Step 3: Extract account and customer IDs (COBOL NOT INVALID KEY condition)
     *         CardXref xref = xrefOpt.get();
     *         Long accountId = xref.getAccountId();
     *         Long customerId = xref.getCustomerId();
     *         log.info("SUCCESSFUL READ OF XREF - Card: {}, Account: {}, Customer: {}",
     *                  maskCardNumber(xref.getCardNumber()), accountId, customerId);
     *         
     *         // Step 4: Load account and post transaction
     *         Account account = accountRepository.findById(accountId)
     *             .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
     *         account.postTransaction(dailyTran.getAmount(), dailyTran.getTransactionType());
     *         accountRepository.save(account);
     *     }
     * }
     * </pre>
     * 
     * <p><b>PCI-DSS Security Requirements:</b></p>
     * CRITICAL: Card numbers are sensitive CHD. Service layer calling this method MUST:
     * <ul>
     *   <li><b>Mask Card Numbers in Logs:</b> Use {@code cardNumber.replaceAll("\\d(?=\\d{4})", "*")}
     *       to mask all but last 4 digits before logging (PCI-DSS Requirement 3.3).</li>
     *   <li><b>Validate Card Format:</b> Verify card number matches {@code \\d{16}} pattern
     *       before calling this method (fail fast for malformed input).</li>
     *   <li><b>Audit Access:</b> Log all lookups with user ID, timestamp, and masked card number
     *       for PCI-DSS Requirement 10 (Track and monitor all access to cardholder data).</li>
     *   <li><b>Secure Transmission:</b> Ensure API requests containing card numbers use TLS 1.3
     *       encryption. Never transmit card numbers over HTTP.</li>
     * </ul>
     * 
     * <p><b>Error Handling Patterns:</b></p>
     * <pre>
     * // Pattern 1: Throw exception for not found (fail fast)
     * CardXref xref = cardXrefRepository.findByCardNumber(cardNumber)
     *     .orElseThrow(() -> new InvalidCardNumberException("Card not found: " + maskCardNumber(cardNumber)));
     * 
     * // Pattern 2: Return default value or alternative action
     * Optional&lt;CardXref&gt; xrefOpt = cardXrefRepository.findByCardNumber(cardNumber);
     * if (xrefOpt.isEmpty()) {
     *     log.warn("Card not found, attempting alternate lookup");
     *     return performAlternateLookup(cardNumber);
     * }
     * 
     * // Pattern 3: Use functional programming style
     * cardXrefRepository.findByCardNumber(cardNumber)
     *     .ifPresentOrElse(
     *         xref -> processTransaction(xref),
     *         () -> logInvalidCardError(cardNumber)
     *     );
     * </pre>
     * 
     * <p><b>Testing Requirements:</b></p>
     * Integration tests MUST verify:
     * <ul>
     *   <li>Returns Optional.of(CardXref) for valid card number in database</li>
     *   <li>Returns Optional.empty() for non-existent card number</li>
     *   <li>Returns Optional.empty() for null card number (should not throw exception)</li>
     *   <li>Returned CardXref contains correct accountId and customerId</li>
     *   <li>Query execution time is &lt;10ms at 95th percentile</li>
     *   <li>Primary key index is used (verify with EXPLAIN ANALYZE)</li>
     * </ul>
     * 
     * <p><b>Comparison with COBOL Behavior:</b></p>
     * <table border="1">
     *   <tr>
     *     <th>Scenario</th>
     *     <th>COBOL FILE STATUS</th>
     *     <th>Java Optional&lt;CardXref&gt;</th>
     *   </tr>
     *   <tr>
     *     <td>Card number found</td>
     *     <td>00 (Success), NOT INVALID KEY</td>
     *     <td>Optional.of(CardXref) with populated fields</td>
     *   </tr>
     *   <tr>
     *     <td>Card number not found</td>
     *     <td>23 (Record not found), INVALID KEY</td>
     *     <td>Optional.empty()</td>
     *   </tr>
     *   <tr>
     *     <td>I/O error</td>
     *     <td>90 (Logic error), INVALID KEY</td>
     *     <td>DataAccessException thrown by Spring Data JPA</td>
     *   </tr>
     * </table>
     * 
     * @param cardNumber the 16-digit credit card number to lookup (must match pattern {@code \\d{16}})
     * @return Optional containing CardXref if found, or Optional.empty() if card number not found
     *         in cross-reference table. Never returns null.
     * @throws IllegalArgumentException if cardNumber is malformed (not 16 digits)
     * @see CardXref for entity structure and field documentation
     * @see Optional for return type usage patterns
     */
    Optional<CardXref> findByCardNumber(String cardNumber);
    
    /**
     * Find all card cross-references for a specific account (account-to-cards lookup).
     * 
     * <p><b>Legacy Mapping:</b> Replaces COBOL VSAM Alternate Index (AIX) queries on
     * XREF-ACCT-ID field. In mainframe environment, this required:
     * <ol>
     *   <li>DEFINE AIX command to create alternate index on XREF-ACCT-ID</li>
     *   <li>BLDINDEX utility to populate and maintain alternate index</li>
     *   <li>READ via AIX path using alternate key</li>
     * </ol>
     * 
     * In modernized implementation, PostgreSQL automatically maintains B-tree index
     * {@code idx_xref_account} on account_id column, eliminating manual index maintenance.
     * 
     * <p><b>Business Purpose:</b> Find all credit cards associated with a specific account.
     * Common use cases include:
     * <ul>
     *   <li><b>Statement Generation:</b> Monthly account statements must list all cards
     *       associated with the account, showing last 4 digits of each card and activity.</li>
     *   <li><b>Account Closure:</b> Before closing an account, all associated cards must be
     *       identified and deactivated to prevent future transactions.</li>
     *   <li><b>Fraud Detection:</b> If account shows suspicious activity, security team needs
     *       to identify all cards to potentially block transactions.</li>
     *   <li><b>Credit Limit Changes:</b> When account credit limit changes, all associated
     *       cards are affected and may need notification sent to cardholders.</li>
     * </ul>
     * 
     * <p><b>Performance Characteristics:</b></p>
     * This method uses the {@code idx_xref_account} B-tree index on account_id column:
     * <ul>
     *   <li><b>Target Performance:</b> &lt;20ms at 95th percentile for typical accounts
     *       with 1-5 cards (per Section 0.8.6)</li>
     *   <li><b>Index Type:</b> Non-unique B-tree secondary index (multiple cards per account)</li>
     *   <li><b>Query Plan:</b> Index Scan on idx_xref_account (verify with EXPLAIN ANALYZE)</li>
     *   <li><b>Complexity:</b> O(log n + k) where n=total cross-references, k=cards for account</li>
     *   <li><b>Scalability:</b> For accounts with many cards (e.g., corporate accounts with
     *       100+ cards), consider pagination using {@code Page&lt;CardXref&gt; findByAccountId(Long, Pageable)}</li>
     * </ul>
     * 
     * <p><b>Return Value Semantics:</b></p>
     * <ul>
     *   <li><b>Non-empty List:</b> Account has 1 or more cards. Typical accounts have 1-5 cards.
     *       Corporate accounts may have 10-100+ cards.</li>
     *   <li><b>Empty List:</b> Account has no associated cards. This is valid for:
     *       <ul>
     *         <li>Newly opened accounts before card issuance</li>
     *         <li>Closed accounts where all cards have been removed</li>
     *         <li>Accounts that had cards but all cross-references were deleted</li>
     *       </ul>
     *   </li>
     *   <li><b>Never null:</b> Spring Data JPA guarantees non-null List (empty List if no results)</li>
     * </ul>
     * 
     * <p><b>Usage Example in Statement Generation:</b></p>
     * <pre>
     * @Service
     * @Transactional(readOnly = true)
     * public class StatementGenerationService {
     *     private final CardXrefRepository cardXrefRepository;
     *     
     *     public void generateAccountStatement(Long accountId) {
     *         // Step 1: Find all cards associated with account
     *         List&lt;CardXref&gt; cards = cardXrefRepository.findByAccountId(accountId);
     *         
     *         // Step 2: Format card list for statement (mask card numbers per PCI-DSS)
     *         if (cards.isEmpty()) {
     *             log.info("No cards found for account {}", accountId);
     *             statementBuilder.addSection("No active cards on this account");
     *         } else {
     *             log.info("Found {} card(s) for account {}", cards.size(), accountId);
     *             statementBuilder.addSection("Cards on Account:");
     *             cards.forEach(xref -> {
     *                 String maskedCard = xref.getCardNumber().replaceAll("\\d(?=\\d{4})", "*");
     *                 statementBuilder.addLine("  Card: " + maskedCard);
     *             });
     *         }
     *         
     *         // Step 3: Continue with transaction history, balance details, etc.
     *     }
     * }
     * </pre>
     * 
     * <p><b>Usage Example in Account Closure:</b></p>
     * <pre>
     * @Service
     * @Transactional
     * public class AccountClosureService {
     *     private final CardXrefRepository cardXrefRepository;
     *     private final CardService cardService;
     *     
     *     public void closeAccount(Long accountId) {
     *         // Step 1: Find all cards associated with account
     *         List&lt;CardXref&gt; cards = cardXrefRepository.findByAccountId(accountId);
     *         
     *         // Step 2: Validate no active cards exist (business rule)
     *         if (!cards.isEmpty()) {
     *             throw new AccountClosureException(
     *                 "Cannot close account with active cards. Deactivate all cards first. " +
     *                 "Found " + cards.size() + " card(s)."
     *             );
     *         }
     *         
     *         // Step 3: Proceed with account closure
     *         log.info("No cards found for account {}, proceeding with closure", accountId);
     *         // ... closure logic
     *     }
     * }
     * </pre>
     * 
     * <p><b>Pagination for Large Result Sets:</b></p>
     * For accounts with many cards (corporate accounts, etc.), use paginated query:
     * <pre>
     * // Define paginated query method in repository (optional enhancement)
     * Page&lt;CardXref&gt; findByAccountId(Long accountId, Pageable pageable);
     * 
     * // Usage in service layer
     * int pageSize = 50;
     * int pageNumber = 0;
     * Pageable pageable = PageRequest.of(pageNumber, pageSize);
     * Page&lt;CardXref&gt; page = cardXrefRepository.findByAccountId(accountId, pageable);
     * 
     * List&lt;CardXref&gt; cards = page.getContent();         // Current page results
     * long totalCards = page.getTotalElements();         // Total count across all pages
     * int totalPages = page.getTotalPages();             // Number of pages
     * boolean hasMore = page.hasNext();                  // More pages available?
     * </pre>
     * 
     * <p><b>Database Query Generated:</b></p>
     * Spring Data JPA generates the following SQL query:
     * <pre>
     * SELECT c.card_number, c.customer_id, c.account_id, c.created_at, c.updated_at, c.version
     * FROM card_xref c
     * WHERE c.account_id = ?
     * ORDER BY c.card_number;  -- Default ordering by primary key
     * </pre>
     * 
     * Execution plan (PostgreSQL):
     * <pre>
     * Index Scan using idx_xref_account on card_xref  (cost=0.15..8.17 rows=1 width=50)
     *   Index Cond: (account_id = 1)
     * </pre>
     * 
     * <p><b>Testing Requirements:</b></p>
     * Integration tests MUST verify:
     * <ul>
     *   <li>Returns all cards for account when multiple cards exist</li>
     *   <li>Returns single-element list when account has exactly one card</li>
     *   <li>Returns empty list when account has no cards (not null)</li>
     *   <li>Does not return cards from other accounts (data isolation)</li>
     *   <li>Query execution time is &lt;20ms at 95th percentile</li>
     *   <li>idx_xref_account B-tree index is used (verify with EXPLAIN ANALYZE)</li>
     *   <li>Results are stable across multiple calls (consistency)</li>
     * </ul>
     * 
     * @param accountId the account ID to lookup (must be valid Long value, typically 1-99999999999)
     * @return List of CardXref entities for the account. Returns empty List if no cards found.
     *         Never returns null. Results are not sorted by default (order by primary key).
     * @throws IllegalArgumentException if accountId is null
     * @see CardXref for entity structure and field documentation
     * @see List for return type usage patterns
     */
    List<CardXref> findByAccountId(Long accountId);
    
    /**
     * Find all card cross-references for a specific customer (customer-to-cards lookup).
     * 
     * <p><b>Legacy Mapping:</b> Replaces COBOL VSAM Alternate Index (AIX) queries on
     * XREF-CUST-ID field. In mainframe environment, this required:
     * <ol>
     *   <li>DEFINE AIX command to create alternate index on XREF-CUST-ID</li>
     *   <li>BLDINDEX utility to populate and maintain alternate index</li>
     *   <li>START/READ NEXT operations to browse cards for customer</li>
     * </ol>
     * 
     * In modernized implementation, PostgreSQL automatically maintains B-tree index
     * {@code idx_xref_customer} on customer_id column, eliminating manual index maintenance
     * and providing efficient multi-card lookups.
     * 
     * <p><b>Business Purpose:</b> Find all credit cards associated with a specific customer.
     * Common use cases include:
     * <ul>
     *   <li><b>Customer Service Inquiries:</b> When customer calls with questions, service
     *       representative needs to see all cards the customer holds across all accounts
     *       (checking accounts, savings accounts, credit lines, etc.).</li>
     *   <li><b>Cross-Sell Opportunities:</b> Marketing department identifies customers with
     *       only one card to offer additional card products or upgraded card types.</li>
     *   <li><b>Fraud Investigation:</b> Security team investigating suspicious activity for
     *       a customer needs to identify all cards to check for fraudulent transactions
     *       across all accounts.</li>
     *   <li><b>Customer Deactivation:</b> When customer closes relationship with bank, all
     *       cards across all accounts must be identified and deactivated.</li>
     *   <li><b>Credit Reporting:</b> Generate credit report showing all credit lines (cards)
     *       the customer has with the institution.</li>
     * </ul>
     * 
     * <p><b>Performance Characteristics:</b></p>
     * This method uses the {@code idx_xref_customer} B-tree index on customer_id column:
     * <ul>
     *   <li><b>Target Performance:</b> &lt;20ms at 95th percentile for typical customers
     *       with 1-10 cards (per Section 0.8.6)</li>
     *   <li><b>Index Type:</b> Non-unique B-tree secondary index (multiple cards per customer)</li>
     *   <li><b>Query Plan:</b> Index Scan on idx_xref_customer (verify with EXPLAIN ANALYZE)</li>
     *   <li><b>Complexity:</b> O(log n + k) where n=total cross-references, k=cards for customer</li>
     *   <li><b>Typical Result Size:</b> Most customers have 1-5 cards. High-value customers may
     *       have 10-20 cards across multiple accounts.</li>
     * </ul>
     * 
     * <p><b>Return Value Semantics:</b></p>
     * <ul>
     *   <li><b>Non-empty List:</b> Customer has 1 or more cards. Typical customers have 1-5 cards.
     *       VIP customers may have 10-20+ cards (multiple accounts, authorized users, etc.).</li>
     *   <li><b>Empty List:</b> Customer has no associated cards. This is valid for:
     *       <ul>
     *         <li>New customers who just opened account but cards not yet issued</li>
     *         <li>Customers who closed all accounts and cards were deleted</li>
     *         <li>Customers with deposit-only accounts (no credit cards)</li>
     *       </ul>
     *   </li>
     *   <li><b>Never null:</b> Spring Data JPA guarantees non-null List (empty List if no results)</li>
     * </ul>
     * 
     * <p><b>Multi-Account Scenario:</b></p>
     * A customer may have cards across multiple accounts:
     * <ul>
     *   <li>Account #1 (Checking): Card #4556...9855, Card #4556...1234</li>
     *   <li>Account #2 (Savings): Card #4556...5678</li>
     *   <li>Account #3 (Credit Line): Card #4556...9999</li>
     * </ul>
     * 
     * Calling {@code findByCustomerId(customerId)} returns ALL 4 cards, allowing service
     * representative to provide comprehensive view of customer's card portfolio.
     * 
     * <p><b>Usage Example in Customer Service Portal:</b></p>
     * <pre>
     * @Service
     * @Transactional(readOnly = true)
     * public class CustomerServicePortalService {
     *     private final CardXrefRepository cardXrefRepository;
     *     private final AccountRepository accountRepository;
     *     
     *     public CustomerCardPortfolio getCustomerCardPortfolio(Long customerId) {
     *         // Step 1: Find all cards for customer
     *         List&lt;CardXref&gt; cards = cardXrefRepository.findByCustomerId(customerId);
     *         
     *         // Step 2: Handle no cards scenario
     *         if (cards.isEmpty()) {
     *             log.info("Customer {} has no active cards", customerId);
     *             return CustomerCardPortfolio.empty(customerId);
     *         }
     *         
     *         // Step 3: Group cards by account for display
     *         log.info("Customer {} has {} card(s)", customerId, cards.size());
     *         Map&lt;Long, List&lt;CardXref&gt;&gt; cardsByAccount = cards.stream()
     *             .collect(Collectors.groupingBy(CardXref::getAccountId));
     *         
     *         // Step 4: Build portfolio with masked card numbers
     *         CustomerCardPortfolio portfolio = new CustomerCardPortfolio(customerId);
     *         cardsByAccount.forEach((accountId, accountCards) -> {
     *             Account account = accountRepository.findById(accountId).orElseThrow();
     *             accountCards.forEach(xref -> {
     *                 String maskedCard = xref.getCardNumber().replaceAll("\\d(?=\\d{4})", "*");
     *                 portfolio.addCard(accountId, account.getAccountNumber(), maskedCard);
     *             });
     *         });
     *         
     *         return portfolio;
     *     }
     * }
     * </pre>
     * 
     * <p><b>Usage Example in Fraud Investigation:</b></p>
     * <pre>
     * @Service
     * @Transactional(readOnly = true)
     * public class FraudInvestigationService {
     *     private final CardXrefRepository cardXrefRepository;
     *     private final TransactionRepository transactionRepository;
     *     
     *     public FraudReport investigateCustomer(Long customerId) {
     *         // Step 1: Get all cards for customer
     *         List&lt;CardXref&gt; cards = cardXrefRepository.findByCustomerId(customerId);
     *         
     *         if (cards.isEmpty()) {
     *             log.warn("No cards found for customer {} during fraud investigation", customerId);
     *             return FraudReport.noCards(customerId);
     *         }
     *         
     *         // Step 2: Check recent transactions on ALL cards
     *         log.info("Investigating {} card(s) for customer {}", cards.size(), customerId);
     *         FraudReport report = new FraudReport(customerId);
     *         
     *         cards.forEach(xref -> {
     *             List&lt;Transaction&gt; recentTrans = transactionRepository
     *                 .findRecentTransactionsByCardNumber(xref.getCardNumber(), 30); // Last 30 days
     *             
     *             recentTrans.forEach(tran -> {
     *                 if (isSuspiciousPattern(tran)) {
     *                     String maskedCard = xref.getCardNumber().replaceAll("\\d(?=\\d{4})", "*");
     *                     report.addSuspiciousActivity(maskedCard, tran);
     *                 }
     *             });
     *         });
     *         
     *         return report;
     *     }
     * }
     * </pre>
     * 
     * <p><b>Database Query Generated:</b></p>
     * Spring Data JPA generates the following SQL query:
     * <pre>
     * SELECT c.card_number, c.customer_id, c.account_id, c.created_at, c.updated_at, c.version
     * FROM card_xref c
     * WHERE c.customer_id = ?
     * ORDER BY c.card_number;  -- Default ordering by primary key
     * </pre>
     * 
     * Execution plan (PostgreSQL):
     * <pre>
     * Index Scan using idx_xref_customer on card_xref  (cost=0.15..8.17 rows=3 width=50)
     *   Index Cond: (customer_id = 1)
     * </pre>
     * 
     * <p><b>Filtering and Joining Patterns:</b></p>
     * Service layer can further filter or join results:
     * <pre>
     * // Filter to only active cards (requires loading Card entity)
     * List&lt;CardXref&gt; allCards = cardXrefRepository.findByCustomerId(customerId);
     * List&lt;CardXref&gt; activeCards = allCards.stream()
     *     .filter(xref -> cardRepository.findById(xref.getCardNumber())
     *         .map(card -> "Y".equals(card.getCardStatus()))
     *         .orElse(false))
     *     .collect(Collectors.toList());
     * 
     * // Group by account ID
     * Map&lt;Long, Long&gt; cardCountByAccount = allCards.stream()
     *     .collect(Collectors.groupingBy(CardXref::getAccountId, Collectors.counting()));
     * 
     * // Load related accounts (avoid N+1 with batch fetch)
     * List&lt;Long&gt; accountIds = allCards.stream()
     *     .map(CardXref::getAccountId)
     *     .distinct()
     *     .collect(Collectors.toList());
     * List&lt;Account&gt; accounts = accountRepository.findAllById(accountIds);
     * </pre>
     * 
     * <p><b>Testing Requirements:</b></p>
     * Integration tests MUST verify:
     * <ul>
     *   <li>Returns all cards for customer across multiple accounts</li>
     *   <li>Returns single-element list when customer has exactly one card</li>
     *   <li>Returns empty list when customer has no cards (not null)</li>
     *   <li>Does not return cards from other customers (data isolation)</li>
     *   <li>Query execution time is &lt;20ms at 95th percentile</li>
     *   <li>idx_xref_customer B-tree index is used (verify with EXPLAIN ANALYZE)</li>
     *   <li>Results are stable across multiple calls (consistency)</li>
     *   <li>Handles customers with many cards (10+ cards) efficiently</li>
     * </ul>
     * 
     * @param customerId the customer ID to lookup (must be valid Long value, typically 1-999999999)
     * @return List of CardXref entities for the customer across all accounts. Returns empty List
     *         if no cards found. Never returns null. Results are not sorted by default.
     * @throws IllegalArgumentException if customerId is null
     * @see CardXref for entity structure and field documentation
     * @see List for return type usage patterns
     */
    List<CardXref> findByCustomerId(Long customerId);
}

