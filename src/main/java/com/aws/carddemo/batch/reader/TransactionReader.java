package com.aws.carddemo.batch.reader;

import com.aws.carddemo.model.Transaction;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.HashMap;
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
@Configuration
public class TransactionReader {

    /**
     * Creates a JPA-based paginated ItemReader for Transaction entities with date range filtering.
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
    public JpaPagingItemReader<Transaction> transactionReader(
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
}
