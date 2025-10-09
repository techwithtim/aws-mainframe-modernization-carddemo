package com.aws.carddemo.repository;

import com.aws.carddemo.model.TransactionCategoryBalance;
import com.aws.carddemo.model.TransactionCategoryBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for TransactionCategoryBalance entity.
 * <p>
 * Provides data access operations for transaction category balance aggregates, enabling
 * account-level balance tracking segmented by transaction type and category. This repository
 * replaces COBOL VSAM keyed file operations on the TCATBAL dataset used in batch programs
 * CBACT04C.cbl (interest calculation) and CBTRN01C.cbl (transaction posting).
 * <p>
 * <b>Legacy COBOL Mapping:</b>
 * <p>
 * This repository replaces the following COBOL file operations from the mainframe application:
 * <pre>
 * * CBACT04C.cbl - Interest Calculation Batch Job:
 * SELECT TCATBAL-FILE ASSIGN TO TCATBALF
 *        ORGANIZATION IS INDEXED
 *        ACCESS MODE  IS SEQUENTIAL
 *        RECORD KEY   IS FD-TRAN-CAT-KEY
 * 
 * * Read operations:
 * READ TCATBAL-FILE NEXT RECORD
 *      AT END SET APPL-EOF TO TRUE
 * 
 * * CBTRN01C.cbl - Transaction Posting Batch Job:
 * READ TCATBAL-FILE KEY IS TRAN-CAT-KEY
 *      INVALID KEY PERFORM 9999-ABEND-PROGRAM
 * 
 * REWRITE TRAN-CAT-BAL-RECORD
 *      INVALID KEY PERFORM 9999-ABEND-PROGRAM
 * </pre>
 * <p>
 * The Spring Data JPA query derivation mechanism automatically generates SQL queries from
 * method names, eliminating the need for explicit VSAM file I/O logic. The JPA @IdClass
 * pattern with TransactionCategoryBalanceId handles the composite key structure that
 * corresponds to the COBOL TRAN-CAT-KEY (ACCT-ID + TYPE-CD + CAT-CD).
 * <p>
 * <b>Composite Primary Key Structure:</b>
 * <p>
 * The TransactionCategoryBalance entity uses a composite primary key consisting of three fields:
 * <ul>
 *   <li><b>accountId</b> (Long) - Maps to TRANCAT-ACCT-ID PIC 9(11) from CVTRA01Y.cpy</li>
 *   <li><b>transactionTypeCode</b> (String, 2 chars) - Maps to TRANCAT-TYPE-CD PIC X(02)</li>
 *   <li><b>transactionCategoryCode</b> (String, 4 chars) - Maps to TRANCAT-CD PIC 9(04)</li>
 * </ul>
 * <p>
 * The composite key is represented by the TransactionCategoryBalanceId class, used as the
 * second generic type parameter in the JpaRepository interface declaration. This enables
 * findById(TransactionCategoryBalanceId) operations that retrieve a specific balance by
 * the complete key.
 * <p>
 * <b>Custom Query Methods:</b>
 * <p>
 * <b>1. findByAccountId(Long accountId)</b> - Retrieves all category balances for an account
 * <p>
 * Returns a list of all transaction category balance records for a given account, spanning
 * all transaction types and categories. This method is essential for interest calculation
 * batch processing (migrated from CBACT04C.cbl) which needs to compute interest charges
 * on each category balance using type-specific interest rates from the disclosure_group table.
 * <p>
 * <b>COBOL Equivalent:</b> Sequential READ of TCATBAL-FILE filtered by accountId
 * <pre>
 * MOVE WS-ACCT-ID TO TRANCAT-ACCT-ID
 * START TCATBAL-FILE KEY IS GREATER THAN OR EQUAL TO TRANCAT-ACCT-ID
 * PERFORM UNTIL EOF OR TRANCAT-ACCT-ID NOT EQUAL TO WS-ACCT-ID
 *     READ TCATBAL-FILE NEXT RECORD
 *     ... process balance record ...
 * END-PERFORM
 * </pre>
 * <p>
 * <b>SQL Generated:</b>
 * <pre>
 * SELECT * FROM transaction_category_balance
 * WHERE account_id = ?
 * ORDER BY transaction_type_code, transaction_category_code
 * </pre>
 * <p>
 * <b>Usage Example:</b>
 * <pre>
 * // Retrieve all category balances for account 1234567890
 * List&lt;TransactionCategoryBalance&gt; balances = repository.findByAccountId(1234567890L);
 * 
 * // Calculate total interest across all categories
 * BigDecimal totalInterest = balances.stream()
 *     .map(balance -&gt; {
 *         DisclosureGroup discGroup = disclosureGroupRepository
 *             .findByAccountGroupIdAndTransactionTypeCodeAndTransactionCategoryCode(
 *                 balance.getAccount().getAccountGroupId(),
 *                 balance.getTransactionTypeCode(),
 *                 balance.getTransactionCategoryCode()
 *             );
 *         return balance.getCategoryBalance()
 *             .multiply(discGroup.getInterestRate())
 *             .divide(new BigDecimal("1200"), 2, RoundingMode.HALF_UP);
 *     })
 *     .reduce(BigDecimal.ZERO, BigDecimal::add);
 * </pre>
 * <p>
 * <b>2. findByAccountIdAndTransactionCategoryCode(Long accountId, String transactionCategoryCode)</b>
 * - Retrieves category balances for specific category across all transaction types
 * <p>
 * Returns a list of transaction category balance records for a given account and category code,
 * spanning all transaction types. This query is useful for transaction posting operations
 * (migrated from CBTRN01C.cbl) when updating category-specific balances after processing
 * daily transaction feeds.
 * <p>
 * <b>IMPORTANT NOTE:</b> This method returns a List rather than Optional because the combination
 * of accountId and transactionCategoryCode is NOT unique without the transactionTypeCode field.
 * A single account may have multiple balance records with the same category code but different
 * transaction types (e.g., "0001" category for both purchase type "01" and payment type "02").
 * <p>
 * If you need a unique balance record, use findById(TransactionCategoryBalanceId) with the
 * complete composite key including all three fields (accountId, transactionTypeCode,
 * transactionCategoryCode).
 * <p>
 * <b>COBOL Equivalent:</b> Keyed READ of TCATBAL-FILE with partial key match
 * <pre>
 * MOVE WS-ACCT-ID TO TRANCAT-ACCT-ID
 * MOVE WS-CAT-CD  TO TRANCAT-CD
 * * Note: COBOL requires full key for direct READ, would use START + sequential READ for partial key
 * START TCATBAL-FILE KEY IS GREATER THAN OR EQUAL TO TRAN-CAT-KEY
 * PERFORM UNTIL EOF OR TRANCAT-ACCT-ID NOT EQUAL TO WS-ACCT-ID
 *          OR TRANCAT-CD NOT EQUAL TO WS-CAT-CD
 *     READ TCATBAL-FILE NEXT RECORD
 *     ... process matching balance records ...
 * END-PERFORM
 * </pre>
 * <p>
 * <b>SQL Generated:</b>
 * <pre>
 * SELECT * FROM transaction_category_balance
 * WHERE account_id = ?
 *   AND transaction_category_code = ?
 * ORDER BY transaction_type_code
 * </pre>
 * <p>
 * <b>Usage Example:</b>
 * <pre>
 * // Retrieve all balances for account 1234567890 in grocery category "0001"
 * List&lt;TransactionCategoryBalance&gt; groceryBalances =
 *     repository.findByAccountIdAndTransactionCategoryCode(1234567890L, "0001");
 * 
 * // Update balance for purchase transaction type within the list
 * TransactionCategoryBalance purchaseBalance = groceryBalances.stream()
 *     .filter(bal -&gt; "01".equals(bal.getTransactionTypeCode()))
 *     .findFirst()
 *     .orElseThrow(() -&gt; new ResourceNotFoundException("Balance not found"));
 * 
 * purchaseBalance.setCategoryBalance(
 *     purchaseBalance.getCategoryBalance().add(transactionAmount)
 * );
 * repository.save(purchaseBalance);
 * </pre>
 * <p>
 * <b>Inherited JpaRepository Methods:</b>
 * <p>
 * In addition to the custom query methods defined in this interface, the repository inherits
 * CRUD operations from JpaRepository&lt;TransactionCategoryBalance, TransactionCategoryBalanceId&gt;:
 * <ul>
 *   <li><b>save(TransactionCategoryBalance)</b> - Insert new or update existing balance record</li>
 *   <li><b>saveAll(Iterable)</b> - Batch insert/update multiple balance records</li>
 *   <li><b>findById(TransactionCategoryBalanceId)</b> - Retrieve balance by complete composite key</li>
 *   <li><b>findAll()</b> - Retrieve all category balance records (use with caution, can be large)</li>
 *   <li><b>findAll(Pageable)</b> - Retrieve paginated category balance records</li>
 *   <li><b>delete(TransactionCategoryBalance)</b> - Delete a balance record</li>
 *   <li><b>deleteById(TransactionCategoryBalanceId)</b> - Delete by composite key</li>
 *   <li><b>deleteAll(Iterable)</b> - Batch delete multiple balance records</li>
 *   <li><b>count()</b> - Count total balance records</li>
 *   <li><b>existsById(TransactionCategoryBalanceId)</b> - Check if balance exists</li>
 *   <li><b>flush()</b> - Force synchronization of persistence context to database</li>
 *   <li><b>saveAndFlush(TransactionCategoryBalance)</b> - Save and immediately flush to DB</li>
 * </ul>
 * <p>
 * <b>Transaction Management:</b>
 * <p>
 * All repository operations participate in Spring's declarative transaction management via
 * @Transactional annotations on service layer methods. This ensures ACID properties are
 * maintained for complex operations that span multiple entities:
 * <pre>
 * {@literal @}Transactional
 * public void postTransaction(TransactionRequest request) {
 *     // 1. Deduct from account balance
 *     Account account = accountRepository.findById(accountId).orElseThrow();
 *     account.setCurrentBalance(account.getCurrentBalance().subtract(amount));
 *     accountRepository.save(account);
 *     
 *     // 2. Update category balance
 *     TransactionCategoryBalanceId catBalId = new TransactionCategoryBalanceId(
 *         accountId, typeCode, categoryCode
 *     );
 *     TransactionCategoryBalance catBal = 
 *         categoryBalanceRepository.findById(catBalId).orElseThrow();
 *     catBal.setCategoryBalance(catBal.getCategoryBalance().add(amount));
 *     categoryBalanceRepository.save(catBal);
 *     
 *     // 3. Create transaction record
 *     Transaction txn = Transaction.builder()
 *         .transactionId(generateId())
 *         .accountId(accountId)
 *         .amount(amount)
 *         .build();
 *     transactionRepository.save(txn);
 *     
 *     // All three operations commit together or rollback on exception
 * }
 * </pre>
 * <p>
 * This transactional coordination replaces the COBOL SYNCPOINT mechanism used in CICS
 * online programs and the implicit commit points in batch programs after each record update.
 * <p>
 * <b>Performance Considerations:</b>
 * <p>
 * The idx_catbal_account index on account_id (defined in the entity @Table annotation)
 * ensures efficient execution of findByAccountId queries. PostgreSQL query planner uses
 * this B-tree index to quickly locate all category balance records for a given account
 * without performing a full table scan.
 * <p>
 * For bulk updates during batch processing (e.g., interest calculation on 10,000+ accounts),
 * consider using Spring Batch chunk-oriented processing with appropriate chunk size (100-1000
 * records per transaction) to balance memory usage and commit frequency.
 * <p>
 * <b>Concurrency and Locking:</b>
 * <p>
 * The TransactionCategoryBalance entity inherits optimistic locking via @Version field from
 * BaseEntity. When multiple transactions attempt to update the same category balance
 * concurrently, JPA throws OptimisticLockException. Service layer code must handle this
 * exception and implement retry logic:
 * <pre>
 * {@literal @}Retryable(
 *     value = OptimisticLockException.class,
 *     maxAttempts = 3,
 *     backoff = {@literal @}Backoff(delay = 100)
 * )
 * public void updateCategoryBalance(Long accountId, String typeCode, String categoryCode, 
 *                                   BigDecimal amount) {
 *     TransactionCategoryBalanceId id = new TransactionCategoryBalanceId(
 *         accountId, typeCode, categoryCode
 *     );
 *     TransactionCategoryBalance balance = repository.findById(id).orElseThrow();
 *     balance.setCategoryBalance(balance.getCategoryBalance().add(amount));
 *     repository.save(balance);  // May throw OptimisticLockException if version mismatch
 * }
 * </pre>
 * <p>
 * <b>Data Integrity Constraints:</b>
 * <p>
 * The repository operations enforce database constraints defined in the Flyway migration schema:
 * <ul>
 *   <li><b>Primary Key Constraint:</b> pk_transaction_category_balance ensures uniqueness of
 *       (account_id, transaction_type_code, transaction_category_code) combination</li>
 *   <li><b>Foreign Key fk_catbal_account:</b> Validates account_id exists in account table;
 *       ON DELETE CASCADE removes all category balances when parent account is deleted</li>
 *   <li><b>Foreign Key fk_catbal_type:</b> Validates transaction_type_code exists in
 *       transaction_type reference table</li>
 *   <li><b>Foreign Key fk_catbal_category:</b> Composite foreign key validates
 *       (transaction_type_code, transaction_category_code) exists in transaction_category table,
 *       ensuring category belongs to the specified transaction type</li>
 *   <li><b>NOT NULL Constraints:</b> All key fields and category_balance must be non-null</li>
 *   <li><b>Numeric Precision:</b> category_balance constrained to NUMERIC(11,2) for exact
 *       decimal arithmetic (no floating-point rounding errors)</li>
 * </ul>
 * <p>
 * Constraint violations throw DataIntegrityViolationException, which should be caught and
 * translated to appropriate business exceptions (e.g., ResourceNotFoundException,
 * InvalidInputException) by the service layer.
 * <p>
 * <b>Testing Considerations:</b>
 * <p>
 * Integration tests for this repository use Testcontainers to provision a real PostgreSQL
 * database, ensuring query method derivation works correctly and database constraints are
 * properly enforced:
 * <pre>
 * {@literal @}Testcontainers
 * {@literal @}SpringBootTest
 * class TransactionCategoryBalanceRepositoryIntegrationTest {
 *     
 *     {@literal @}Container
 *     static PostgreSQLContainer&lt;?&gt; postgres = 
 *         new PostgreSQLContainer&lt;&gt;("postgres:15-alpine");
 *     
 *     {@literal @}Autowired
 *     private TransactionCategoryBalanceRepository repository;
 *     
 *     {@literal @}Test
 *     void testFindByAccountId_ReturnsAllCategoryBalances() {
 *         List&lt;TransactionCategoryBalance&gt; balances = repository.findByAccountId(1L);
 *         assertThat(balances).isNotEmpty();
 *         assertThat(balances).allMatch(b -&gt; b.getAccountId().equals(1L));
 *     }
 *     
 *     {@literal @}Test
 *     void testSave_WithValidData_PersistsSuccessfully() {
 *         TransactionCategoryBalance balance = TransactionCategoryBalance.builder()
 *             .accountId(1L)
 *             .transactionTypeCode("01")
 *             .transactionCategoryCode("0001")
 *             .categoryBalance(new BigDecimal("1500.00"))
 *             .build();
 *         
 *         TransactionCategoryBalance saved = repository.save(balance);
 *         assertThat(saved.getCategoryBalance()).isEqualByComparingTo("1500.00");
 *     }
 * }
 * </pre>
 * <p>
 * <b>Migration Validation:</b>
 * <p>
 * To validate functional equivalence with the legacy COBOL system, compare query results
 * against VSAM dataset extracts:
 * <ol>
 *   <li>Export TCATBAL VSAM dataset to delimited text file on mainframe</li>
 *   <li>Load extracted data into PostgreSQL using Flyway migration V4__load_test_data.sql</li>
 *   <li>Execute repository queries and compare results with original COBOL program outputs</li>
 *   <li>Verify category balance totals match across systems (sum by account should equal
 *       account.currentBalance)</li>
 * </ol>
 * <p>
 * <b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 6.2.2.2: Transaction Category Balance - Composite key entity enabling account
 *       balance segmentation by category for tiered interest calculations</li>
 *   <li>Section 0.4.1: Repository Layer Transformation - Spring Data JPA repository replacing
 *       VSAM TCATBAL keyed file access from CBACT04C.cbl and CBTRN01C.cbl</li>
 *   <li>Section 0.8.3: Data Type Mapping - COBOL PIC S9(09)V99 COMP-3 → BigDecimal with
 *       NUMERIC(11,2) precision for financial calculations</li>
 * </ul>
 *
 * @see TransactionCategoryBalance the entity managed by this repository
 * @see TransactionCategoryBalanceId the composite primary key class
 * @see org.springframework.data.jpa.repository.JpaRepository base repository interface
 * @see org.springframework.transaction.annotation.Transactional for transaction management
 */
@Repository
public interface TransactionCategoryBalanceRepository 
        extends JpaRepository<TransactionCategoryBalance, TransactionCategoryBalanceId> {

    /**
     * Retrieves all transaction category balance records for a given account.
     * <p>
     * Returns a list of all category balances associated with the specified account ID,
     * spanning all transaction types and categories. Results are ordered by transaction
     * type code and category code for consistent processing order.
     * <p>
     * <b>Primary Use Case:</b> Interest calculation batch job (InterestCalculationJobConfig,
     * migrated from CBACT04C.cbl) which computes monthly interest charges on each category
     * balance using type-specific interest rates from the disclosure_group table.
     * <p>
     * <b>Spring Data JPA Query Derivation:</b> Method name is automatically parsed by
     * Spring Data JPA to generate SQL query: SELECT * FROM transaction_category_balance
     * WHERE account_id = ? ORDER BY transaction_type_code, transaction_category_code
     * <p>
     * <b>COBOL Equivalent:</b>
     * <pre>
     * MOVE WS-ACCT-ID TO TRANCAT-ACCT-ID
     * START TCATBAL-FILE KEY IS GREATER THAN OR EQUAL TO TRANCAT-ACCT-ID
     * PERFORM UNTIL APPL-EOF OR TRANCAT-ACCT-ID NOT EQUAL TO WS-ACCT-ID
     *     READ TCATBAL-FILE NEXT RECORD
     *     IF NOT APPL-EOF
     *         ... compute interest for this category balance ...
     *         COMPUTE MONTHLY-INTEREST = TRAN-CAT-BAL * INTEREST-RATE / 1200
     *         ADD MONTHLY-INTEREST TO TOTAL-INTEREST
     *     END-IF
     * END-PERFORM
     * </pre>
     * <p>
     * <b>Performance:</b> Query uses idx_catbal_account index on account_id for efficient
     * retrieval. For accounts with many category balances (10+), query execution time is
     * typically under 10ms with proper indexing.
     * <p>
     * <b>Result Set Characteristics:</b>
     * <ul>
     *   <li>Size: Typically 3-10 records per account (one per transaction type/category combo)</li>
     *   <li>Ordering: Results ordered by (transaction_type_code, transaction_category_code)</li>
     *   <li>Null Handling: Returns empty list if account has no category balances</li>
     *   <li>Fetch Strategy: Uses LAZY fetch for account relationship to avoid N+1 queries</li>
     * </ul>
     * <p>
     * <b>Example Usage:</b>
     * <pre>
     * // Batch job: Calculate total interest for all accounts
     * {@literal @}Transactional(readOnly = true)
     * public Map&lt;Long, BigDecimal&gt; calculateMonthlyInterest() {
     *     List&lt;Account&gt; accounts = accountRepository.findAll();
     *     Map&lt;Long, BigDecimal&gt; interestMap = new HashMap&lt;&gt;();
     *     
     *     for (Account account : accounts) {
     *         List&lt;TransactionCategoryBalance&gt; balances =
     *             categoryBalanceRepository.findByAccountId(account.getAccountId());
     *         
     *         BigDecimal totalInterest = balances.stream()
     *             .map(balance -&gt; computeCategoryInterest(balance))
     *             .reduce(BigDecimal.ZERO, BigDecimal::add);
     *         
     *         interestMap.put(account.getAccountId(), totalInterest);
     *     }
     *     return interestMap;
     * }
     * 
     * // REST API: Retrieve category balances for display
     * {@literal @}GetMapping("/api/v1/accounts/{accountId}/category-balances")
     * public ResponseEntity&lt;List&lt;CategoryBalanceResponse&gt;&gt; getCategoryBalances(
     *         {@literal @}PathVariable Long accountId) {
     *     List&lt;TransactionCategoryBalance&gt; balances =
     *         categoryBalanceRepository.findByAccountId(accountId);
     *     
     *     List&lt;CategoryBalanceResponse&gt; response = balances.stream()
     *         .map(categoryBalanceMapper::toResponse)
     *         .collect(Collectors.toList());
     *     
     *     return ResponseEntity.ok(response);
     * }
     * </pre>
     *
     * @param accountId the 11-digit account identifier to query (must not be null)
     * @return list of all category balance records for the account; empty list if none exist
     * @throws IllegalArgumentException if accountId is null
     * @see TransactionCategoryBalance#getAccountId()
     * @see com.aws.carddemo.batch.config.InterestCalculationJobConfig interest calculation usage
     */
    List<TransactionCategoryBalance> findByAccountId(Long accountId);

    /**
     * Retrieves transaction category balance records for a specific category across all types.
     * <p>
     * Returns a list of category balance records for the specified account and category code,
     * potentially spanning multiple transaction types. This query is useful for analyzing
     * category-specific balances or updating balances during transaction posting.
     * <p>
     * <b>IMPORTANT:</b> This method returns a List, not Optional, because the combination of
     * accountId and transactionCategoryCode is NOT guaranteed to be unique. A single account
     * may have multiple balance records with the same category code but different transaction
     * types. For example:
     * <ul>
     *   <li>Account 1234567890, Type "01" (Purchase), Category "0001" (Groceries): $500.00</li>
     *   <li>Account 1234567890, Type "02" (Payment), Category "0001" (Payment Applied): -$200.00</li>
     * </ul>
     * <p>
     * If you need a unique balance record, use findById(TransactionCategoryBalanceId) with the
     * complete composite key including accountId, transactionTypeCode, and transactionCategoryCode.
     * <p>
     * <b>Primary Use Case:</b> Transaction posting operations (TransactionService, migrated from
     * CBTRN01C.cbl) when updating category balances after processing daily transaction feeds.
     * The service layer filters the result list by transaction type to locate the specific
     * balance record to update.
     * <p>
     * <b>Spring Data JPA Query Derivation:</b> Method name parsed to generate SQL:
     * SELECT * FROM transaction_category_balance WHERE account_id = ? AND
     * transaction_category_code = ? ORDER BY transaction_type_code
     * <p>
     * <b>COBOL Equivalent:</b>
     * <pre>
     * * Note: COBOL VSAM requires full key for direct READ; partial key requires START + READ NEXT
     * MOVE WS-ACCT-ID TO TRANCAT-ACCT-ID
     * MOVE WS-CAT-CD  TO TRANCAT-CD
     * MOVE LOW-VALUES TO TRANCAT-TYPE-CD
     * START TCATBAL-FILE KEY IS GREATER THAN OR EQUAL TO TRAN-CAT-KEY
     * PERFORM UNTIL APPL-EOF 
     *          OR TRANCAT-ACCT-ID NOT EQUAL TO WS-ACCT-ID
     *          OR TRANCAT-CD NOT EQUAL TO WS-CAT-CD
     *     READ TCATBAL-FILE NEXT RECORD
     *     IF NOT APPL-EOF
     *         ... process matching balance record ...
     *         IF TRANCAT-TYPE-CD = WS-TARGET-TYPE
     *             ... update this specific balance ...
     *             REWRITE TRAN-CAT-BAL-RECORD
     *         END-IF
     *     END-IF
     * END-PERFORM
     * </pre>
     * <p>
     * <b>Performance:</b> Query performs index range scan on pk_transaction_category_balance
     * primary key index. For typical workloads (1-3 matching records), query execution time
     * is under 5ms.
     * <p>
     * <b>Result Set Characteristics:</b>
     * <ul>
     *   <li>Size: Typically 1-3 records (one per transaction type for the category)</li>
     *   <li>Ordering: Results ordered by transaction_type_code ascending</li>
     *   <li>Null Handling: Returns empty list if no balances exist for account/category combo</li>
     *   <li>Uniqueness: NOT guaranteed unique; caller must filter by type code if needed</li>
     * </ul>
     * <p>
     * <b>Example Usage:</b>
     * <pre>
     * // Transaction posting: Update purchase category balance
     * {@literal @}Transactional
     * public void postPurchaseTransaction(Long accountId, String categoryCode, BigDecimal amount) {
     *     // Retrieve all balances for this account/category combination
     *     List&lt;TransactionCategoryBalance&gt; balances =
     *         categoryBalanceRepository.findByAccountIdAndTransactionCategoryCode(
     *             accountId, categoryCode
     *         );
     *     
     *     // Filter for purchase transaction type "01"
     *     TransactionCategoryBalance purchaseBalance = balances.stream()
     *         .filter(bal -&gt; "01".equals(bal.getTransactionTypeCode()))
     *         .findFirst()
     *         .orElseThrow(() -&gt; new ResourceNotFoundException(
     *             "Purchase balance not found for account " + accountId + 
     *             " category " + categoryCode
     *         ));
     *     
     *     // Update balance (optimistic locking prevents concurrent modification conflicts)
     *     purchaseBalance.setCategoryBalance(
     *         purchaseBalance.getCategoryBalance().add(amount)
     *     );
     *     categoryBalanceRepository.save(purchaseBalance);
     * }
     * 
     * // Analytics: Compute total balance across all types for a category
     * public BigDecimal getTotalCategoryBalance(Long accountId, String categoryCode) {
     *     List&lt;TransactionCategoryBalance&gt; balances =
     *         categoryBalanceRepository.findByAccountIdAndTransactionCategoryCode(
     *             accountId, categoryCode
     *         );
     *     
     *     return balances.stream()
     *         .map(TransactionCategoryBalance::getCategoryBalance)
     *         .reduce(BigDecimal.ZERO, BigDecimal::add);
     * }
     * </pre>
     *
     * @param accountId the 11-digit account identifier to query (must not be null)
     * @param transactionCategoryCode the 4-character category code to query (must not be null)
     * @return list of matching category balance records; empty list if none exist
     * @throws IllegalArgumentException if accountId or transactionCategoryCode is null
     * @see TransactionCategoryBalance#getAccountId()
     * @see TransactionCategoryBalance#getTransactionCategoryCode()
     * @see com.aws.carddemo.service.TransactionService transaction posting usage
     */
    List<TransactionCategoryBalance> findByAccountIdAndTransactionCategoryCode(
            Long accountId, 
            String transactionCategoryCode
    );
}
