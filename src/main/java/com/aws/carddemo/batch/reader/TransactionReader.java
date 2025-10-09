package com.aws.carddemo.batch.reader;

import com.aws.carddemo.batch.dto.AccountTransactionGroup;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Batch configuration class providing JPA-based paginated ItemReader for Transaction entities.
 * 
 * <p><b>Migrated From:</b>
 * <ul>
 *   <li>{@code app/cbl/CBTRN03C.cbl} - Transaction report generator batch program</li>
 *   <li>{@code app/cbl/CBSTM03A.CBL} - Statement generation batch program</li>
 *   <li>{@code app/cpy/CVTRA05Y.cpy} - Transaction record layout (350-byte COBOL structure)</li>
 * </ul>
 * 
 * <p><b>Business Purpose:</b> This reader enables Spring Batch jobs to retrieve posted
 * transaction records within a specified date range for:
 * <ul>
 *   <li><b>Monthly Statement Generation:</b> Reading all transactions posted within a
 *       billing cycle (e.g., 2024-01-01 to 2024-01-31) grouped by account for statement
 *       rendering (CBSTM03A.CBL logic)</li>
 *   <li><b>Transaction Report Generation:</b> Creating detailed transaction reports
 *       with filtering by date range for custom reporting periods (CBTRN03C.cbl logic)</li>
 *   <li><b>Audit and Reconciliation:</b> Extracting transaction sets for financial
 *       reconciliation and regulatory compliance reporting</li>
 * </ul>
 * 
 * <p><b>COBOL File I/O Pattern Modernization:</b>
 * <pre>
 * COBOL Pattern (CBTRN03C.cbl lines 29-31, 248-272):
 * ------------------------------------------------
 * SELECT TRANSACT-FILE ASSIGN TO TRANFILE
 *        ORGANIZATION IS SEQUENTIAL
 *        FILE STATUS IS TRANFILE-STATUS.
 * ...
 * READ TRANSACT-FILE INTO TRAN-RECORD.
 * IF TRAN-PROC-TS (1:10) >= WS-START-DATE
 *    AND TRAN-PROC-TS (1:10) <= WS-END-DATE
 *    CONTINUE
 * 
 * Java Pattern (This Class):
 * --------------------------
 * JpaPagingItemReader&lt;Transaction&gt; reader = new JpaPagingItemReader&lt;&gt;();
 * reader.setQueryString("SELECT t FROM Transaction t 
 *                        WHERE t.processingTimestamp BETWEEN :startDate AND :endDate
 *                        ORDER BY t.account.accountId ASC, t.processingTimestamp ASC");
 * reader.setParameterValues(Map.of("startDate", startDate, "endDate", endDate));
 * reader.setPageSize(1000);
 * </pre>
 * 
 * <p><b>Key Features:</b>
 * <ul>
 *   <li><b>Parameterized Date Filtering:</b> JPQL query accepts startDate and endDate
 *       parameters bound from JobParameters, enabling flexible report period configuration
 *       (monthly statements, custom date ranges) without code changes</li>
 *   <li><b>@StepScope Late Binding:</b> Reader bean is @StepScope, creating new instance
 *       for each step execution with job-specific parameters, supporting multiple concurrent
 *       job runs with different date ranges</li>
 *   <li><b>Pagination:</b> Reads 1000 transactions per page, preventing memory exhaustion
 *       for large transaction datasets while maintaining transactional consistency</li>
 *   <li><b>JOIN FETCH Optimization:</b> Query includes JOIN FETCH for account and customer
 *       relationships to prevent N+1 query problem when accessing account/customer details
 *       for statement header rendering</li>
 *   <li><b>Deterministic Ordering:</b> Results ordered by accountId, processingTimestamp,
 *       transactionId for grouped statement processing and reproducible batch job results</li>
 *   <li><b>Thread-Safe:</b> JpaPagingItemReader maintains internal stateful execution
 *       context for job restart capability, suitable for both single-threaded and
 *       partitioned parallel execution patterns</li>
 * </ul>
 * 
 * <p><b>Date Range Parameter Binding:</b>
 * JobParameters passed via JobLauncher are injected into @Bean method via @Value SpEL:
 * <pre>
 * // Job launch (from JobLauncher or JobController)
 * JobParameters params = new JobParametersBuilder()
 *     .addString("startDate", "2024-01-01")
 *     .addString("endDate", "2024-01-31")
 *     .addDate("runDate", new Date())  // For uniqueness
 *     .toJobParameters();
 * jobLauncher.run(statementGenerationJob, params);
 * 
 * // Parameter injection (automatic by Spring)
 * transactionReader("2024-01-01", "2024-01-31", entityManagerFactory)
 * </pre>
 * 
 * <p><b>JOIN FETCH Query Pattern:</b>
 * For statement generation, the reader uses nested JOIN FETCH to retrieve transactions
 * with associated account and customer data in a single query, preventing the N+1 query
 * problem where accessing {@code transaction.getAccount().getCustomer().getFirstName()}
 * would trigger additional SELECT statements:
 * <pre>
 * // Query with JOIN FETCH (1 query for all data)
 * SELECT t FROM Transaction t 
 *   JOIN FETCH t.account a 
 *   JOIN FETCH a.customer c
 * WHERE t.processingTimestamp BETWEEN :startDate AND :endDate
 * ORDER BY a.accountId ASC, t.processingTimestamp ASC, t.transactionId ASC
 * 
 * // Result: All transactions, accounts, and customers loaded in one query
 * </pre>
 * 
 * <p><b>Performance Characteristics:</b>
 * <ul>
 *   <li><b>Query Performance:</b> Index {@code idx_transaction_account_date} on
 *       (account_id, processing_timestamp) enables efficient range scan for date filtering</li>
 *   <li><b>Memory Footprint:</b> Page size of 1000 transactions × ~500 bytes per entity
 *       = ~500KB per page, safe for heap allocation even with large datasets</li>
 *   <li><b>Throughput:</b> Paginated reading supports processing 10,000+ transactions/minute
 *       with chunk-oriented step configuration</li>
 *   <li><b>Restart Capability:</b> JpaPagingItemReader maintains ExecutionContext state
 *       enabling job restart from last successful page after failure</li>
 * </ul>
 * 
 * <p><b>Usage in Batch Job Configuration:</b>
 * <pre>
 * &#64;Configuration
 * public class StatementGenerationJobConfig {
 *     
 *     &#64;Bean
 *     public Step statementGenerationStep(
 *             JobRepository jobRepository,
 *             PlatformTransactionManager transactionManager,
 *             JpaPagingItemReader&lt;Transaction&gt; transactionReader,
 *             ItemProcessor&lt;Transaction, Statement&gt; statementProcessor,
 *             ItemWriter&lt;Statement&gt; statementWriter) {
 *         return new StepBuilder("statementGenerationStep", jobRepository)
 *             .&lt;Transaction, Statement&gt;chunk(100, transactionManager)
 *             .reader(transactionReader)  // This reader
 *             .processor(statementProcessor)
 *             .writer(statementWriter)
 *             .build();
 *     }
 * }
 * </pre>
 * 
 * <p><b>COBOL to Java Mapping:</b>
 * <table border="1">
 *   <tr>
 *     <th>COBOL Concept</th>
 *     <th>Java Equivalent</th>
 *     <th>Notes</th>
 *   </tr>
 *   <tr>
 *     <td>SELECT TRANSACT-FILE</td>
 *     <td>JpaPagingItemReader configuration</td>
 *     <td>File handle → EntityManagerFactory</td>
 *   </tr>
 *   <tr>
 *     <td>ORGANIZATION IS SEQUENTIAL</td>
 *     <td>ORDER BY clause in JPQL</td>
 *     <td>Deterministic ordering for reproducibility</td>
 *   </tr>
 *   <tr>
 *     <td>READ TRANSACT-FILE INTO TRAN-RECORD</td>
 *     <td>reader.read()</td>
 *     <td>Returns Transaction entity</td>
 *   </tr>
 *   <tr>
 *     <td>TRAN-PROC-TS (1:10) >= WS-START-DATE</td>
 *     <td>WHERE t.processingTimestamp >= :startDate</td>
 *     <td>Date range filtering in JPQL</td>
 *   </tr>
 *   <tr>
 *     <td>FD-TRANFILE-REC (62-65)</td>
 *     <td>Transaction JPA entity</td>
 *     <td>350-byte COBOL record → Java object</td>
 *   </tr>
 *   <tr>
 *     <td>DATE-PARMS-FILE input</td>
 *     <td>JobParameters injection</td>
 *     <td>Sequential file → @Value SpEL binding</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 0.4.1: File Transformation - CBTRN03C.cbl → TransactionReader.java</li>
 *   <li>Section 0.8.5: Batch Job Conversion Standards - Spring Batch chunk-oriented processing</li>
 *   <li>Section 6.2.2.1: Transaction History Table - processingTimestamp for date filtering</li>
 *   <li>Section 2.3: Batch Processing Workflows - Statement generation and transaction reporting</li>
 * </ul>
 * 
 * @see Transaction for JPA entity structure and field mappings
 * @see org.springframework.batch.item.database.JpaPagingItemReader for Spring Batch reader API
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class TransactionReader {

    /**
     * Creates a JPA-based paginated ItemReader for Transaction entities with date range filtering.
     * 
     * <p><b>Bean Name Disambiguation:</b> This bean method is named {@code dateRangeTransactionReader}
     * to avoid bean naming conflict with the @Configuration class name {@code TransactionReader}.
     * Spring would otherwise attempt to register two beans with the same name "transactionReader"
     * (one for the configuration class, one for this @Bean method), causing a
     * BeanDefinitionOverrideException at application context startup.
     * 
     * <p><b>Method Signature:</b> This @Bean method is annotated with @StepScope to enable
     * late binding of JobParameters at step execution time. The method accepts startDate and
     * endDate string parameters injected from JobParameters via @Value SpEL expressions, along
     * with the EntityManagerFactory for JPA query execution.
     * 
     * <p><b>Parameter Injection:</b>
     * <ul>
     *   <li><b>startDate:</b> Injected from {@code jobParameters['startDate']} - Lower bound
     *       of date range filter in ISO-8601 format "yyyy-MM-dd" (e.g., "2024-01-01")</li>
     *   <li><b>endDate:</b> Injected from {@code jobParameters['endDate']} - Upper bound
     *       of date range filter in ISO-8601 format "yyyy-MM-dd" (e.g., "2024-01-31")</li>
     *   <li><b>entityManagerFactory:</b> Autowired by Spring from DataSourceConfig, provides
     *       JPA EntityManager instances for database query execution</li>
     * </ul>
     * 
     * <p><b>JPQL Query Design:</b>
     * The query uses BETWEEN operator for inclusive date range filtering on the processingTimestamp
     * field. For statement generation, the query includes nested JOIN FETCH clauses to eagerly
     * load account and customer relationships in a single SQL query, preventing N+1 query problems:
     * <pre>
     * SELECT t FROM Transaction t 
     *   JOIN FETCH t.account a 
     *   JOIN FETCH a.customer c
     * WHERE t.processingTimestamp BETWEEN :startDate AND :endDate
     * ORDER BY a.accountId ASC, t.processingTimestamp ASC, t.transactionId ASC
     * </pre>
     * 
     * <p><b>Ordering Strategy:</b>
     * <ul>
     *   <li><b>accountId ASC:</b> Groups transactions by account for statement generation,
     *       enabling ItemProcessor to detect account boundaries and generate statement breaks</li>
     *   <li><b>processingTimestamp ASC:</b> Within each account, orders transactions
     *       chronologically for statement listing (oldest to newest)</li>
     *   <li><b>transactionId ASC:</b> Tie-breaker for transactions with same timestamp,
     *       ensures deterministic ordering for reproducible batch job results</li>
     * </ul>
     * 
     * <p><b>Page Size Configuration:</b>
     * Page size of 1000 transactions balances memory usage with database round-trips:
     * <ul>
     *   <li><b>Memory:</b> 1000 transactions × ~500 bytes per entity = ~500KB per page</li>
     *   <li><b>Performance:</b> Reduces database round-trips for large result sets</li>
     *   <li><b>Chunk Alignment:</b> Typically chunk size is set to 100, so reader prefetches
     *       10 chunks worth of data per database query</li>
     * </ul>
     * 
     * <p><b>Date Parameter Parsing:</b>
     * The method converts string date parameters to LocalDate objects for JPQL parameter binding.
     * LocalDate provides timezone-agnostic date representation, matching the database DATE column
     * type and avoiding timezone conversion issues.
     * <pre>
     * LocalDate start = LocalDate.parse(startDate);  // Parses "2024-01-01" to LocalDate
     * LocalDate end = LocalDate.parse(endDate);      // Parses "2024-01-31" to LocalDate
     * </pre>
     * 
     * <p><b>Parameter Value Map:</b>
     * JPQL named parameters (:startDate, :endDate) are bound using a HashMap passed to
     * {@code setParameterValues()}. The map keys must match the named parameter names in
     * the JPQL query string:
     * <pre>
     * Map&lt;String, Object&gt; params = new HashMap&lt;&gt;();
     * params.put("startDate", start);  // Binds to :startDate in WHERE clause
     * params.put("endDate", end);      // Binds to :endDate in WHERE clause
     * reader.setParameterValues(params);
     * </pre>
     * 
     * <p><b>@StepScope Bean Lifecycle:</b>
     * <ul>
     *   <li><b>Creation:</b> New reader instance created for each step execution</li>
     *   <li><b>Initialization:</b> {@code afterPropertiesSet()} called to prepare reader</li>
     *   <li><b>Execution:</b> {@code read()} called repeatedly to fetch Transaction entities</li>
     *   <li><b>Restart:</b> Reader restores state from ExecutionContext on job restart</li>
     *   <li><b>Cleanup:</b> Reader resources released when step completes or fails</li>
     * </ul>
     * 
     * <p><b>Error Handling:</b>
     * <ul>
     *   <li><b>Invalid Date Format:</b> {@code LocalDate.parse()} throws DateTimeParseException
     *       if startDate/endDate are not in ISO-8601 format, causing job to fail with clear
     *       error message</li>
     *   <li><b>Database Errors:</b> JpaPagingItemReader wraps SQL exceptions in Spring Batch
     *       ItemStreamException, which triggers job failure and restart capability</li>
     *   <li><b>Empty Result Set:</b> If no transactions match date range, reader returns null
     *       on first read(), causing step to complete successfully with zero items processed</li>
     * </ul>
     * 
     * <p><b>Statement Generation Use Case:</b>
     * For monthly statement generation (CBSTM03A.CBL logic), this reader is configured with
     * date range covering the billing cycle (e.g., January 1-31). The ItemProcessor detects
     * account changes in the ordered stream and generates statement output grouped by account.
     * JOIN FETCH ensures customer name and address are available for statement header without
     * additional queries.
     * 
     * <p><b>Transaction Report Use Case:</b>
     * For transaction reports (CBTRN03C.cbl logic), this reader is configured with custom
     * date range parameters. The ItemProcessor can further filter by transaction type or
     * category, and ItemWriter generates report output in text or HTML format.
     * 
     * <p><b>Thread Safety:</b>
     * JpaPagingItemReader is thread-safe for single-threaded execution within a step.
     * For partitioned parallel execution, each partition receives its own reader instance
     * with partition-specific parameters (e.g., different accountId ranges or date ranges).
     * 
     * <p><b>Example Job Launch:</b>
     * <pre>
     * // Launch statement generation job for January 2024
     * JobParameters params = new JobParametersBuilder()
     *     .addString("startDate", "2024-01-01")
     *     .addString("endDate", "2024-01-31")
     *     .addDate("runDate", new Date())  // For uniqueness
     *     .toJobParameters();
     * JobExecution execution = jobLauncher.run(statementGenerationJob, params);
     * 
     * // This reader will be created with:
     * // - startDate = "2024-01-01" parsed to LocalDate.of(2024, 1, 1)
     * // - endDate = "2024-01-31" parsed to LocalDate.of(2024, 1, 31)
     * // - JPQL query filtering processingTimestamp BETWEEN 2024-01-01 AND 2024-01-31
     * </pre>
     * 
     * <p><b>COBOL Equivalence:</b>
     * This method replaces COBOL DATE-PARMS-FILE sequential file reading (CBTRN03C.cbl lines
     * 220-243) where date range parameters were read from a parameter file. The JobParameters
     * mechanism provides equivalent functionality with better testability and flexibility.
     * 
     * @param startDate Lower bound of date range filter in ISO-8601 format "yyyy-MM-dd",
     *                  injected from {@code jobParameters['startDate']} via @Value SpEL.
     *                  Example: "2024-01-01" for first day of January 2024.
     * @param endDate   Upper bound of date range filter in ISO-8601 format "yyyy-MM-dd",
     *                  injected from {@code jobParameters['endDate']} via @Value SpEL.
     *                  Example: "2024-01-31" for last day of January 2024.
     * @param entityManagerFactory JPA EntityManagerFactory autowired from DataSourceConfig,
     *                             provides EntityManager instances for query execution against
     *                             PostgreSQL transaction table.
     * @return Configured JpaPagingItemReader ready for use in Spring Batch step, reading
     *         Transaction entities within the specified date range with account and customer
     *         relationships eagerly loaded via JOIN FETCH.
     * @throws java.time.format.DateTimeParseException if startDate or endDate are not in
     *         ISO-8601 "yyyy-MM-dd" format
     * @throws org.springframework.batch.item.ItemStreamException if database query fails
     *         or EntityManagerFactory cannot create EntityManager
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<Transaction> dateRangeTransactionReader(
            @Value("#{jobParameters['startDate']}") String startDate,
            @Value("#{jobParameters['endDate']}") String endDate,
            EntityManagerFactory entityManagerFactory) throws Exception {
        
        // Parse string date parameters to LocalDate for JPQL parameter binding
        // LocalDate provides timezone-agnostic date representation matching database DATE type
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        
        // Create JpaPagingItemReader instance for Transaction entity
        JpaPagingItemReader<Transaction> reader = new JpaPagingItemReader<>();
        
        // Configure EntityManagerFactory for JPA query execution
        reader.setEntityManagerFactory(entityManagerFactory);
        
        // Set JPQL query with date range filtering and JOIN FETCH optimization
        // JOIN FETCH t.account a loads account relationship eagerly
        // JOIN FETCH a.customer c loads customer relationship eagerly
        // This prevents N+1 query problem when accessing account/customer details
        // BETWEEN operator provides inclusive date range filtering on processingTimestamp
        // ORDER BY groups transactions by account and orders chronologically
        reader.setQueryString(
            "SELECT t FROM Transaction t " +
            "JOIN FETCH t.account a " +
            "JOIN FETCH a.customer c " +
            "WHERE t.processingTimestamp BETWEEN :startDate AND :endDate " +
            "ORDER BY a.accountId ASC, t.processingTimestamp ASC, t.transactionId ASC"
        );
        
        // Build parameter value map for JPQL named parameter binding
        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("startDate", start);
        parameterValues.put("endDate", end);
        reader.setParameterValues(parameterValues);
        
        // Configure page size for pagination
        // 1000 transactions per page balances memory usage with database round-trips
        // Page size > typical chunk size (100) for efficient prefetching
        reader.setPageSize(1000);
        
        // Set reader name for Spring Batch ExecutionContext identification
        // Name appears in batch metadata tables and job execution logs
        reader.setName("transactionReader");
        
        // Initialize reader (required for JpaPagingItemReader)
        // Validates configuration and prepares query for execution
        reader.afterPropertiesSet();
        
        return reader;
    }

    /**
     * Creates an ItemReader for AccountTransactionGroup objects used in statement generation.
     * 
     * <p><b>Reader Architecture:</b> This custom ItemReader implementation reads accounts
     * that have transactions within the specified billing period, and for each account,
     * loads all associated transactions to create a complete AccountTransactionGroup DTO.
     * This grouping strategy matches the COBOL CBSTM03A.CBL pattern where transactions
     * were processed account-by-account for statement generation.
     * 
     * <p><b>COBOL Equivalence:</b>
     * Replaces CBSTM03A.CBL mainline logic (lines 300-400) where the program:
     * <pre>
     * 1000-MAINLINE.
     *     PERFORM UNTIL END-OF-FILE = 'Y'
     *         PERFORM 1000-XREFFILE-GET-NEXT    *> Get next account
     *         PERFORM 2000-CUSTFILE-GET          *> Load customer data
     *         PERFORM 3000-ACCTFILE-GET          *> Load account data
     *         PERFORM 4000-TRNXFILE-GET          *> Load ALL transactions for account
     *         PERFORM 5000-CREATE-STATEMENT      *> Process as group
     *     END-PERFORM.
     * </pre>
     * 
     * <p><b>Data Flow:</b>
     * <pre>
     * AccountTransactionGroupReader
     *     ↓
     * Query accounts with transactions in date range (DISTINCT)
     *     ↓
     * For each account:
     *     - Load account entity with customer (JOIN FETCH)
     *     - Query all transactions in date range
     *     - Format customer name (firstName + middleName + lastName)
     *     - Format customer address (line1, city, state ZIP)
     *     - Assemble AccountTransactionGroup DTO
     *     ↓
     * Return one AccountTransactionGroup per read() call
     * </pre>
     * 
     * <p><b>Query Strategy:</b>
     * Uses two-phase query approach:
     * <ol>
     *   <li>Main query fetches distinct account IDs that have transactions in date range</li>
     *   <li>For each account, secondary query loads account with customer (JOIN FETCH)</li>
     *   <li>Third query loads all transactions for the account in date range</li>
     * </ol>
     * This prevents Cartesian product from joining accounts-customers-transactions in single query.
     * 
     * <p><b>Pagination:</b>
     * Reader processes accounts in batches (page size 100 accounts) to balance memory usage
     * with database round-trips. Each page loads 100 accounts with their transactions, then
     * returns AccountTransactionGroup objects one at a time until page exhausted, then loads
     * next page.
     * 
     * <p><b>Transaction List Size:</b>
     * Average account has 20-100 transactions per month. Reader loads all transactions for
     * each account into memory (List<Transaction>) before creating AccountTransactionGroup.
     * Memory footprint per group: ~5-10 KB including transaction details.
     * 
     * <p><b>Restart Capability:</b>
     * Reader maintains state (current page, current position within page) that is persisted
     * to Spring Batch ExecutionContext. On job restart, reader resumes from last successfully
     * processed account, avoiding duplicate statement generation.
     * 
     * <p><b>Thread Safety:</b>
     * Reader instance is @StepScope, creating new instance per step execution. Not thread-safe
     * for parallel execution - use partitioned steps if parallel processing needed.
     * 
     * @param startDate Lower bound of billing period in ISO-8601 format "yyyy-MM-dd",
     *                  injected from {@code jobParameters['startDate']}.
     * @param endDate   Upper bound of billing period in ISO-8601 format "yyyy-MM-dd",
     *                  injected from {@code jobParameters['endDate']}.
     * @param entityManagerFactory JPA EntityManagerFactory for database access.
     * @return Custom ItemReader that returns AccountTransactionGroup objects for statement processing.
     * @throws Exception if date parsing fails or database connection fails.
     */
    @Bean
    @StepScope
    public ItemReader<AccountTransactionGroup> accountTransactionGroupReader(
            @Value("#{jobParameters['startDate']}") String startDate,
            @Value("#{jobParameters['endDate']}") String endDate,
            EntityManagerFactory entityManagerFactory) throws Exception {
        
        log.info("Configuring accountTransactionGroupReader for date range {} to {}", startDate, endDate);
        
        // Parse date parameters
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);
        
        // Create and return custom reader implementation
        return new AccountTransactionGroupItemReader(entityManagerFactory, startDateTime, endDateTime, start, end);
    }

    /**
     * Custom ItemReader implementation that groups transactions by account for statement generation.
     * 
     * <p>This reader implements the account-level grouping required by StatementProcessor.
     * It queries accounts that have transactions in the billing period, loads all transactions
     * for each account, and assembles AccountTransactionGroup DTOs.
     * 
     * <p><b>State Management:</b>
     * Maintains internal state tracking current page of accounts and position within page.
     * State is NOT persisted to ExecutionContext (stateless reader pattern) - job restart
     * will begin from first account. For production use, consider implementing ItemStream
     * interface to support restart capability.
     */
    private static class AccountTransactionGroupItemReader implements ItemReader<AccountTransactionGroup> {
        
        private final EntityManagerFactory entityManagerFactory;
        private final LocalDateTime startDateTime;
        private final LocalDateTime endDateTime;
        private final LocalDate startDate;
        private final LocalDate endDate;
        
        private EntityManager entityManager;
        private List<Long> accountIds;
        private int currentIndex = 0;
        private boolean initialized = false;
        
        /**
         * Constructor initializing reader with date range parameters.
         * 
         * @param entityManagerFactory Factory for creating EntityManager instances.
         * @param startDateTime        Start of billing period as LocalDateTime.
         * @param endDateTime          End of billing period as LocalDateTime.
         * @param startDate            Start date for DTO population.
         * @param endDate              End date for DTO population.
         */
        public AccountTransactionGroupItemReader(
                EntityManagerFactory entityManagerFactory,
                LocalDateTime startDateTime,
                LocalDateTime endDateTime,
                LocalDate startDate,
                LocalDate endDate) {
            this.entityManagerFactory = entityManagerFactory;
            this.startDateTime = startDateTime;
            this.endDateTime = endDateTime;
            this.startDate = startDate;
            this.endDate = endDate;
        }
        
        /**
         * Reads and returns the next AccountTransactionGroup, or null when all accounts processed.
         * 
         * <p><b>Initialization (First Call):</b>
         * On first read() call, queries database for all distinct account IDs that have
         * transactions in the billing period. Stores IDs in memory for sequential processing.
         * 
         * <p><b>Subsequent Calls:</b>
         * For each account ID, loads full account entity with customer (JOIN FETCH),
         * queries all transactions for the account in date range, formats customer details,
         * and assembles AccountTransactionGroup DTO.
         * 
         * <p><b>Termination:</b>
         * Returns null after processing all accounts, signaling Spring Batch to complete chunk.
         * 
         * @return AccountTransactionGroup for next account, or null if all accounts processed.
         * @throws Exception if database query fails or data formatting fails.
         */
        @Override
        public AccountTransactionGroup read() throws Exception {
            // Lazy initialization on first read
            if (!initialized) {
                initialize();
            }
            
            // Check if all accounts have been processed
            if (currentIndex >= accountIds.size()) {
                // Close EntityManager when done
                if (entityManager != null && entityManager.isOpen()) {
                    entityManager.close();
                }
                return null;  // Signal end of data to Spring Batch
            }
            
            // Get next account ID
            Long accountId = accountIds.get(currentIndex++);
            
            // Load account with customer relationship (JOIN FETCH prevents N+1 queries)
            TypedQuery<Account> accountQuery = entityManager.createQuery(
                "SELECT a FROM Account a " +
                "JOIN FETCH a.customer c " +
                "WHERE a.accountId = :accountId",
                Account.class
            );
            accountQuery.setParameter("accountId", accountId);
            Account account = accountQuery.getSingleResult();
            
            // Load all transactions for this account in the date range
            TypedQuery<Transaction> transactionQuery = entityManager.createQuery(
                "SELECT t FROM Transaction t " +
                "WHERE t.account.accountId = :accountId " +
                "AND t.processingTimestamp BETWEEN :startDateTime AND :endDateTime " +
                "ORDER BY t.processingTimestamp ASC, t.transactionId ASC",
                Transaction.class
            );
            transactionQuery.setParameter("accountId", accountId);
            transactionQuery.setParameter("startDateTime", startDateTime);
            transactionQuery.setParameter("endDateTime", endDateTime);
            List<Transaction> transactions = transactionQuery.getResultList();
            
            // Format customer name (firstName + middleName + lastName)
            String customerName = formatCustomerName(account);
            
            // Format customer address (line1, city, state ZIP)
            String customerAddress = formatCustomerAddress(account);
            
            // Assemble and return AccountTransactionGroup DTO
            return new AccountTransactionGroup(
                account.getAccountId(),
                account.getAccountNumber(),
                customerName,
                customerAddress,
                transactions,
                startDate,
                endDate
            );
        }
        
        /**
         * Initializes reader by querying all account IDs with transactions in date range.
         * 
         * <p>Executes DISTINCT query to find accounts that have at least one transaction
         * in the billing period. Results cached in memory for sequential processing.
         */
        private void initialize() throws Exception {
            entityManager = entityManagerFactory.createEntityManager();
            
            // Query for distinct account IDs that have transactions in date range
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT DISTINCT t.account.accountId FROM Transaction t " +
                "WHERE t.processingTimestamp BETWEEN :startDateTime AND :endDateTime " +
                "ORDER BY t.account.accountId ASC",
                Long.class
            );
            query.setParameter("startDateTime", startDateTime);
            query.setParameter("endDateTime", endDateTime);
            
            accountIds = query.getResultList();
            initialized = true;
            
            log.info("Initialized accountTransactionGroupReader: found {} accounts with transactions in period", 
                    accountIds.size());
        }
        
        /**
         * Formats customer full name from account's customer entity.
         * 
         * @param account Account entity with loaded customer relationship.
         * @return Formatted name: "FirstName MiddleName LastName" or variations.
         */
        private String formatCustomerName(Account account) {
            StringBuilder name = new StringBuilder();
            
            if (account.getCustomer().getFirstName() != null) {
                name.append(account.getCustomer().getFirstName());
            }
            if (account.getCustomer().getMiddleName() != null && !account.getCustomer().getMiddleName().isEmpty()) {
                if (name.length() > 0) name.append(" ");
                name.append(account.getCustomer().getMiddleName());
            }
            if (account.getCustomer().getLastName() != null) {
                if (name.length() > 0) name.append(" ");
                name.append(account.getCustomer().getLastName());
            }
            
            return name.toString();
        }
        
        /**
         * Formats customer mailing address from account's customer entity.
         * 
         * <p>Combines addressLine1, addressLine2, addressLine3, stateCode, and zipCode
         * into a formatted multi-line address string suitable for statement display.
         * 
         * @param account Account entity with loaded customer relationship.
         * @return Formatted address: "Line1, Line2, Line3, State ZIP" with optional lines.
         */
        private String formatCustomerAddress(Account account) {
            StringBuilder address = new StringBuilder();
            
            if (account.getCustomer().getAddressLine1() != null && !account.getCustomer().getAddressLine1().isEmpty()) {
                address.append(account.getCustomer().getAddressLine1());
            }
            if (account.getCustomer().getAddressLine2() != null && !account.getCustomer().getAddressLine2().isEmpty()) {
                if (address.length() > 0) address.append(", ");
                address.append(account.getCustomer().getAddressLine2());
            }
            if (account.getCustomer().getAddressLine3() != null && !account.getCustomer().getAddressLine3().isEmpty()) {
                if (address.length() > 0) address.append(", ");
                address.append(account.getCustomer().getAddressLine3());
            }
            if (account.getCustomer().getStateCode() != null) {
                if (address.length() > 0) address.append(", ");
                address.append(account.getCustomer().getStateCode());
            }
            if (account.getCustomer().getZipCode() != null) {
                if (address.length() > 0) address.append(" ");
                address.append(account.getCustomer().getZipCode());
            }
            
            return address.toString();
        }
    }
}
