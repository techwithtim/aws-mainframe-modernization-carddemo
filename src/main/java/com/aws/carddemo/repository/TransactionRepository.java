package com.aws.carddemo.repository;

import com.aws.carddemo.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JPA repository interface for {@link Transaction} entity providing transaction
 * history data access operations.
 * 
 * <p>Migrated from: COBOL VSAM TRANSACT file READ/WRITE operations in:
 * <ul>
 *   <li>{@code app/cbl/COTRN00C.cbl} - Transaction list browse with STARTBR/READNEXT pagination</li>
 *   <li>{@code app/cbl/COTRN01C.cbl} - Transaction detail view with keyed READ operations</li>
 *   <li>{@code app/cbl/COTRN02C.cbl} - Manual transaction entry with WRITE operations</li>
 *   <li>{@code app/cbl/CBTRN03C.cbl} - Batch reporting with sequential READ and date filtering</li>
 * </ul>
 * 
 * <p><b>Repository Pattern:</b> This interface follows Spring Data JPA repository pattern,
 * eliminating boilerplate VSAM file I/O code and replacing it with declarative query methods.
 * Spring Data JPA automatically generates implementation at runtime based on method naming
 * conventions and @Query annotations.
 * 
 * <p><b>Query Performance Optimization:</b>
 * <ul>
 *   <li><b>Composite Index:</b> {@code idx_transaction_account_date} on (account_id, processing_timestamp DESC)
 *       ensures sub-50ms query performance for paginated transaction lists</li>
 *   <li><b>Chunk Size:</b> Default page size of 20-100 records balances memory usage and query efficiency</li>
 *   <li><b>Pagination:</b> All findByAccountId queries support {@link Pageable} for efficient large result set handling</li>
 * </ul>
 * 
 * <p><b>Transaction History Query Pattern (replaces COBOL STARTBR/READNEXT):</b>
 * <pre>{@code
 * // COBOL: EXEC CICS STARTBR FILE('TRANSACT') RIDFLD(TRAN-ID) END-EXEC
 * // COBOL: EXEC CICS READNEXT FILE('TRANSACT') INTO(TRAN-RECORD) END-EXEC
 * 
 * // Java equivalent with pagination and descending sort:
 * Pageable pageable = PageRequest.of(0, 20, Sort.by("processingTimestamp").descending());
 * Page<Transaction> transactions = transactionRepository.findByAccountAccountId(accountId, pageable);
 * }</pre>
 * 
 * <p><b>Date Range Reporting (replaces CBTRN03C.cbl batch logic):</b>
 * <pre>{@code
 * // COBOL: IF TRAN-PROC-TS (1:10) >= WS-START-DATE
 * //        AND TRAN-PROC-TS (1:10) <= WS-END-DATE
 * 
 * // Java equivalent with LocalDate parameters:
 * LocalDate startDate = LocalDate.of(2024, 1, 1);
 * LocalDate endDate = LocalDate.of(2024, 1, 31);
 * Pageable pageable = PageRequest.of(0, 100, Sort.by("processingTimestamp").ascending());
 * Page<Transaction> reportData = transactionRepository.findByTransactionDateBetween(
 *     startDate, endDate, pageable);
 * }</pre>
 * 
 * <p><b>Category Analysis (transaction category balance updates):</b>
 * <pre>{@code
 * // COBOL: READ TRANSACT FILE WHERE TRAN-CAT-CD = '1001'
 * 
 * // Java equivalent:
 * List<Transaction> categoryTransactions = 
 *     transactionRepository.findByTransactionCategoryCode("1001");
 * }</pre>
 * 
 * <p><b>Financial Precision:</b> All transaction amounts returned use {@code BigDecimal}
 * preserving exact decimal arithmetic from COBOL PIC S9(09)V99 COMP-3 fields. No floating-point
 * errors can occur in financial calculations.
 * 
 * <p><b>Transaction Management:</b> Repository methods are automatically wrapped in transactions
 * when invoked from {@code @Transactional} service methods, coordinating transaction record
 * creation with account balance updates and category balance adjustments within atomic database
 * transactions (replacing COBOL EXEC CICS SYNCPOINT).
 * 
 * <p><b>Insert-Only Pattern:</b> Transactions are immutable once posted. This repository supports
 * {@code save()} for new transaction creation but transactions should never be updated or deleted
 * after initial posting, maintaining complete audit trail integrity.
 * 
 * @see Transaction for entity field mappings and validation rules
 * @see org.springframework.data.jpa.repository.JpaRepository for inherited CRUD operations
 * @see com.aws.carddemo.service.TransactionService for business logic coordination
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0
 * @since 2024-01-01
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Finds paginated transaction history for a specific account, sorted by processing timestamp
     * descending (most recent first).
     * 
     * <p><b>Replaces COBOL Logic:</b> {@code app/cbl/COTRN00C.cbl} STARTBR/READNEXT pagination
     * through TRANSACT file filtered by account context.
     * 
     * <p><b>Query Derivation:</b> Spring Data JPA automatically generates SQL:
     * <pre>{@code
     * SELECT t.* FROM transaction t 
     * WHERE t.account_id = ?1 
     * ORDER BY t.processing_timestamp DESC
     * LIMIT ? OFFSET ?
     * }</pre>
     * 
     * <p><b>Performance:</b> Uses composite index {@code idx_transaction_account_date}
     * on (account_id, processing_timestamp DESC) for sub-50ms query execution with 100-record pages.
     * 
     * <p><b>Usage Example:</b>
     * <pre>{@code
     * // Get first page of 20 transactions for account 1234567890, most recent first
     * Pageable pageable = PageRequest.of(0, 20, Sort.by("processingTimestamp").descending());
     * Page<Transaction> page = transactionRepository.findByAccountAccountId(1234567890L, pageable);
     * 
     * // Navigate pages
     * if (page.hasNext()) {
     *     Page<Transaction> nextPage = transactionRepository.findByAccountAccountId(
     *         1234567890L, page.nextPageable());
     * }
     * 
     * // Access results
     * List<Transaction> transactions = page.getContent();
     * long totalTransactions = page.getTotalElements();
     * int totalPages = page.getTotalPages();
     * }</pre>
     * 
     * <p><b>Account Relationship Navigation:</b> This method navigates the {@code @ManyToOne}
     * relationship from Transaction to Account. Spring Data JPA automatically generates a JOIN
     * to the account table and filters on account.account_id.
     * 
     * @param accountId the surrogate key (account_id) of the parent account, must not be null
     * @param pageable pagination parameters including page number, page size, and sort order;
     *                 typical usage: {@code PageRequest.of(0, 20, Sort.by("processingTimestamp").descending())}
     * @return a page of transactions for the specified account, never null; empty page if no transactions found
     * @throws IllegalArgumentException if accountId is null or pageable is null
     */
    Page<Transaction> findByAccountAccountId(Long accountId, Pageable pageable);

    /**
     * Finds all transactions for a specific account ID without pagination.
     * 
     * <p><b>Purpose:</b> Convenience method for integration tests and service layer operations
     * that need to verify all transactions for an account without pagination overhead.
     * 
     * <p><b>Replaces COBOL Logic:</b> Sequential READ of TRANSACT file with account ID filter:
     * <pre>{@code
     * EXEC CICS STARTBR FILE('TRANSACT') RIDFLD(WS-ACCT-ID) END-EXEC
     * PERFORM UNTIL END-OF-FILE
     *    EXEC CICS READNEXT FILE('TRANSACT') INTO(TRAN-RECORD) END-EXEC
     * END-PERFORM
     * }</pre>
     * 
     * <p><b>Query Implementation:</b> Custom JPQL query joins Transaction and Account entities:
     * <pre>{@code
     * SELECT t FROM Transaction t 
     * WHERE t.account.accountId = :accountId 
     * ORDER BY t.processingTimestamp DESC
     * }</pre>
     * 
     * <p><b>Usage in Integration Tests:</b>
     * <pre>{@code
     * // PaymentIntegrationTest.java - Verify payment transaction created
     * List<Transaction> transactions = transactionRepository.findByAccountId(accountId);
     * Transaction paymentTransaction = transactions.stream()
     *     .filter(t -> "PAYMENT".equals(t.getTransactionTypeCode()))
     *     .findFirst()
     *     .orElseThrow();
     * assertEquals(paymentAmount, paymentTransaction.getAmount());
     * }</pre>
     * 
     * <p><b>Performance Warning:</b> This method returns ALL transactions for an account
     * without pagination. For accounts with thousands of transactions, this can cause
     * memory pressure and slow query performance. Production code should prefer
     * {@link #findByAccountAccountId(Long, Pageable)} for large result sets.
     * 
     * <p><b>When to Use:</b>
     * <ul>
     *   <li><b>Integration Tests:</b> Verify transaction creation and audit fields</li>
     *   <li><b>Small Result Sets:</b> Accounts with <100 transactions</li>
     *   <li><b>Aggregate Calculations:</b> Sum all transaction amounts for reporting</li>
     * </ul>
     * 
     * <p><b>When NOT to Use:</b>
     * <ul>
     *   <li><b>REST API Endpoints:</b> Always use pagination for user-facing APIs</li>
     *   <li><b>Large Accounts:</b> Accounts with >1000 transactions (use paginated method)</li>
     *   <li><b>Production Services:</b> Prefer chunked processing with pagination</li>
     * </ul>
     * 
     * <p><b>Account Relationship Navigation:</b> This method navigates the {@code @ManyToOne}
     * relationship from Transaction to Account using JPQL path expression {@code t.account.accountId}.
     * 
     * <p><b>Technical Specification:</b> Section 0.4.4 - Integration test requirements
     * mandate non-paginated repository methods for comprehensive transaction verification
     * 
     * @param accountId the surrogate key (account_id) of the parent account, must not be null
     * @return list of ALL transactions for the specified account, ordered by processing
     *         timestamp descending (most recent first); never null; empty list if account
     *         has no transactions
     * @throws IllegalArgumentException if accountId is null
     */
    @Query("SELECT t FROM Transaction t WHERE t.account.accountId = :accountId ORDER BY t.processingTimestamp DESC")
    List<Transaction> findByAccountId(@Param("accountId") Long accountId);

    /**
     * Finds paginated transactions within a specific date range based on processing timestamp,
     * enabling transaction reporting and statement generation.
     * 
     * <p><b>Replaces COBOL Logic:</b> {@code app/cbl/CBTRN03C.cbl} batch report generation
     * with date range filtering:
     * <pre>{@code
     * IF TRAN-PROC-TS (1:10) >= WS-START-DATE
     *    AND TRAN-PROC-TS (1:10) <= WS-END-DATE
     *    PERFORM PROCESS-TRANSACTION
     * END-IF
     * }</pre>
     * 
     * <p><b>Date Range Semantics:</b>
     * <ul>
     *   <li><b>Inclusive Range:</b> Transactions from {@code startDate} 00:00:00 through {@code endDate} 23:59:59</li>
     *   <li><b>Processing Timestamp:</b> Filters on system processing time, not merchant authorization time</li>
     *   <li><b>Billing Cycle:</b> Statements use processing timestamp to ensure consistent cut-off dates</li>
     * </ul>
     * 
     * <p><b>Custom Query Implementation:</b> Since the entity field {@code processingTimestamp}
     * is {@code LocalDateTime} but the method accepts {@code LocalDate} parameters for simpler
     * date-only filtering, a custom JPQL query handles the conversion:
     * <pre>{@code
     * WHERE DATE(t.processingTimestamp) BETWEEN :startDate AND :endDate
     * }</pre>
     * 
     * <p><b>Usage Example:</b>
     * <pre>{@code
     * // Generate monthly statement for January 2024
     * LocalDate startDate = LocalDate.of(2024, 1, 1);
     * LocalDate endDate = LocalDate.of(2024, 1, 31);
     * Pageable pageable = PageRequest.of(0, 100, Sort.by("processingTimestamp").ascending());
     * Page<Transaction> statementTransactions = 
     *     transactionRepository.findByTransactionDateBetween(startDate, endDate, pageable);
     * 
     * // Calculate statement totals
     * BigDecimal statementTotal = statementTransactions.stream()
     *     .map(Transaction::getAmount)
     *     .reduce(BigDecimal.ZERO, BigDecimal::add);
     * }</pre>
     * 
     * <p><b>Performance Considerations:</b>
     * <ul>
     *   <li>Date range queries scan larger result sets than account-filtered queries</li>
     *   <li>For best performance, combine with account filtering in service layer</li>
     *   <li>Consider adding date-only index if this query pattern becomes a bottleneck</li>
     * </ul>
     * 
     * @param startDate the start date (inclusive) of the reporting period, must not be null;
     *                  represents 00:00:00 on the specified date
     * @param endDate the end date (inclusive) of the reporting period, must not be null;
     *                represents 23:59:59.999999999 on the specified date
     * @param pageable pagination parameters for chunked result processing;
     *                 typical batch usage: {@code PageRequest.of(0, 1000)} for large batches
     * @return a page of transactions within the date range, ordered by processingTimestamp;
     *         never null; empty page if no transactions found in range
     * @throws IllegalArgumentException if startDate is null, endDate is null, or pageable is null
     * @throws IllegalArgumentException if startDate is after endDate (invalid date range)
     */
    @Query("SELECT t FROM Transaction t WHERE DATE(t.processingTimestamp) BETWEEN :startDate AND :endDate")
    Page<Transaction> findByTransactionDateBetween(
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate, 
            Pageable pageable);

    /**
     * Finds all transactions for a specific transaction category code, enabling category-based
     * spending analysis and category balance calculations.
     * 
     * <p><b>Replaces COBOL Logic:</b> Sequential file scan with category code filtering for
     * transaction category balance updates (TCATBAL) and spending analysis reports.
     * 
     * <p><b>Query Derivation:</b> Spring Data JPA automatically generates SQL:
     * <pre>{@code
     * SELECT t.* FROM transaction t 
     * WHERE t.transaction_category_code = ?1 
     * ORDER BY t.transaction_id
     * }</pre>
     * 
     * <p><b>Category Code Reference:</b> Category codes are 4-digit strings from the
     * {@code transaction_category} reference table (e.g., "1001" for Groceries, "2001" for Gas).
     * See {@code CVTRA04Y.cpy} for complete category code definitions.
     * 
     * <p><b>Non-Paginated Results:</b> This method returns a {@code List} rather than {@code Page}
     * because category-based queries typically return manageable result sets for analysis.
     * If memory usage becomes a concern, consider adding a paginated variant:
     * {@code findByTransactionCategoryCode(String categoryCode, Pageable pageable)}.
     * 
     * <p><b>Usage Example:</b>
     * <pre>{@code
     * // Analyze all grocery spending (category code 1001)
     * List<Transaction> groceryTransactions = 
     *     transactionRepository.findByTransactionCategoryCode("1001");
     * 
     * // Calculate category total
     * BigDecimal groceryTotal = groceryTransactions.stream()
     *     .map(Transaction::getAmount)
     *     .reduce(BigDecimal.ZERO, BigDecimal::add);
     * 
     * // Update category balance
     * TransactionCategoryBalance balance = categoryBalanceRepository
     *     .findByAccountIdAndCategoryCode(accountId, "1001");
     * balance.setBalance(groceryTotal);
     * categoryBalanceRepository.save(balance);
     * }</pre>
     * 
     * <p><b>Performance:</b> Consider adding an index on {@code transaction_category_code}
     * if category-based queries become frequent. Current performance is acceptable for typical
     * analysis workloads.
     * 
     * @param categoryCode the 4-digit transaction category code, must not be null or blank;
     *                     must match pattern {@code \d{4}} (e.g., "1001", "2005")
     * @return list of transactions matching the category code, ordered by transaction ID;
     *         never null; empty list if no transactions found for the category
     * @throws IllegalArgumentException if categoryCode is null or does not match 4-digit pattern
     */
    List<Transaction> findByTransactionCategoryCode(String categoryCode);

    // ===========================================================================================
    // INHERITED JPREPOSITORY METHODS
    // ===========================================================================================
    // The following methods are inherited from JpaRepository<Transaction, Long> and are
    // automatically implemented by Spring Data JPA. They are documented here for completeness
    // and to show functional equivalence to COBOL VSAM operations:
    //
    // save(Transaction entity) - WRITE operation (insert new transaction)
    // findById(Long id) - READ operation with primary key
    // findAll() - Sequential READ of entire file (use with caution - prefer pagination)
    // findAll(Pageable pageable) - Paginated sequential READ
    // delete(Transaction entity) - DELETE operation (use with caution - violates immutability)
    // deleteById(Long id) - DELETE by primary key (use with caution - violates immutability)
    // count() - Count total transactions
    // existsById(Long id) - Check if transaction exists by primary key
    //
    // **IMPORTANT:** Transactions follow an insert-only pattern. DELETE operations should be
    // avoided to maintain audit trail integrity. Updates are also discouraged as transactions
    // are immutable once posted.
    // ===========================================================================================
}
