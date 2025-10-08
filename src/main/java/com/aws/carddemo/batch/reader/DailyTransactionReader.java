package com.aws.carddemo.batch.reader;

import com.aws.carddemo.model.DailyTransaction;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Batch configuration class for reading daily transaction feed records.
 * Migrated from: app/cbl/CBTRN01C.cbl (DALYTRAN-FILE sequential file processing)
 * 
 * <p>This reader replaces COBOL sequential file read operations from CBTRN01C.cbl:</p>
 * <ul>
 *   <li>COBOL Lines 29-32: DALYTRAN-FILE with ORGANIZATION IS SEQUENTIAL</li>
 *   <li>COBOL Lines 66-69: FD-TRAN-RECORD (FD-TRAN-ID PIC X(16) + FD-CUST-DATA PIC X(334))</li>
 *   <li>COBOL Lines 202-224: 1000-DALYTRAN-GET-NEXT paragraph (sequential READ operations)</li>
 * </ul>
 * 
 * <p><strong>Functional Equivalence:</strong></p>
 * <p>The COBOL batch job reads the daily transaction file sequentially from start to end,
 * processing each record in order. This Java implementation provides the same sequential
 * processing behavior using JPA pagination, reading records in chronological order by
 * originalTimestamp while filtering for unprocessed transactions.</p>
 * 
 * <p><strong>Batch Processing Flow:</strong></p>
 * <pre>
 * 1. DailyTransactionReader reads PENDING transactions (this class)
 * 2. TransactionProcessor validates card number, account lookup, business rules
 * 3. TransactionWriter persists to transaction table and updates processed flag
 * 4. Chunk commit interval: 100 records
 * 5. Page size: 1000 records per database fetch
 * </pre>
 * 
 * <p><strong>Job Restart Capability:</strong></p>
 * <p>The reader is configured with saveState=true, enabling Spring Batch to persist
 * pagination offset in the ExecutionContext. If the job fails or is stopped, it can
 * restart from the last successfully processed record, avoiding duplicate processing.</p>
 * 
 * <p><strong>Performance Characteristics:</strong></p>
 * <ul>
 *   <li>Page size: 1000 records per query (balances memory vs query overhead)</li>
 *   <li>Filter: WHERE processing_status = 'PENDING' (prevents reprocessing)</li>
 *   <li>Order: BY original_timestamp ASC (chronological order matching COBOL sequential)</li>
 *   <li>Thread-safe: Single-threaded step execution with optional partition support</li>
 * </ul>
 * 
 * <p><strong>PCI-DSS Compliance:</strong></p>
 * <p>Card numbers in DailyTransaction entities are excluded from toString() output
 * to prevent sensitive data exposure in logs. Use getCardNumberMasked() for logging.</p>
 * 
 * @see com.aws.carddemo.model.DailyTransaction
 * @see com.aws.carddemo.batch.processor.TransactionProcessor
 * @see com.aws.carddemo.batch.writer.TransactionWriter
 * @see com.aws.carddemo.batch.config.TransactionPostingJobConfig
 */
@Configuration
public class DailyTransactionReader {

    /**
     * JPQL query for reading unprocessed daily transactions in chronological order.
     * 
     * <p>Query Logic:</p>
     * <ul>
     *   <li>SELECT dt FROM DailyTransaction dt: Fetch complete entity (not projection)</li>
     *   <li>WHERE dt.processingStatus = 'PENDING': Filter to unprocessed transactions only</li>
     *   <li>ORDER BY dt.originalTimestamp ASC: Chronological order (oldest first)</li>
     * </ul>
     * 
     * <p>This query replaces COBOL sequential file read behavior where records are
     * processed in the order they appear in the DALYTRAN-FILE. The originalTimestamp
     * ordering ensures transactions are processed in the order they were authorized
     * at merchant locations, matching the COBOL batch processing sequence.</p>
     */
    private static final String JPQL_QUERY = 
        "SELECT dt FROM DailyTransaction dt " +
        "WHERE dt.processingStatus = 'PENDING' " +
        "ORDER BY dt.originalTimestamp ASC";

    /**
     * Page size for database fetches.
     * 
     * <p>This value balances memory consumption against query overhead:</p>
     * <ul>
     *   <li>1000 records per fetch keeps memory usage reasonable (approx 350KB per page)</li>
     *   <li>Reduces database round trips compared to smaller page sizes</li>
     *   <li>Allows chunk-oriented processing with 100-record commit intervals</li>
     * </ul>
     * 
     * <p>With chunk size of 100, each page provides data for 10 chunk commits,
     * minimizing the frequency of OFFSET/LIMIT query executions.</p>
     */
    private static final int PAGE_SIZE = 1000;

    /**
     * Creates and configures JpaPagingItemReader for daily transaction feed processing.
     * 
     * <p>This bean method replaces COBOL file operations:</p>
     * <ul>
     *   <li>COBOL 0000-DALYTRAN-OPEN: File open → EntityManagerFactory injection</li>
     *   <li>COBOL 1000-DALYTRAN-GET-NEXT: Sequential READ → JPA paginated query</li>
     *   <li>COBOL FILE STATUS checking → JPA exception handling</li>
     *   <li>COBOL 9000-DALYTRAN-CLOSE: File close → Automatic resource cleanup</li>
     * </ul>
     * 
     * <p><strong>Configuration Details:</strong></p>
     * <table border="1">
     *   <tr>
     *     <th>Setting</th>
     *     <th>Value</th>
     *     <th>Purpose</th>
     *   </tr>
     *   <tr>
     *     <td>name</td>
     *     <td>dailyTransactionReader</td>
     *     <td>Unique bean identifier for Spring context</td>
     *   </tr>
     *   <tr>
     *     <td>queryString</td>
     *     <td>JPQL_QUERY</td>
     *     <td>Filters PENDING transactions, orders chronologically</td>
     *   </tr>
     *   <tr>
     *     <td>pageSize</td>
     *     <td>1000</td>
     *     <td>Records per database fetch (memory optimization)</td>
     *   </tr>
     *   <tr>
     *     <td>entityManagerFactory</td>
     *     <td>injected EMF</td>
     *     <td>JPA/Hibernate database connection and ORM</td>
     *   </tr>
     *   <tr>
     *     <td>saveState</td>
     *     <td>true</td>
     *     <td>Persist pagination offset for job restart capability</td>
     *   </tr>
     * </table>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <p>JpaPagingItemReader handles database exceptions according to Spring Batch
     * retry/skip policies configured in TransactionPostingJobConfig. If a database
     * error occurs during read(), the exception propagates to the Step's fault
     * tolerance configuration.</p>
     * 
     * <p><strong>Restart Behavior:</strong></p>
     * <p>When saveState=true, the reader stores its current page offset in the
     * ExecutionContext after each successful read. On job restart:</p>
     * <ol>
     *   <li>Reader retrieves stored offset from ExecutionContext</li>
     *   <li>Resumes reading from the last successfully processed record</li>
     *   <li>Skips records already marked as PROCESSED (via WHERE clause)</li>
     *   <li>Continues until all PENDING records are processed</li>
     * </ol>
     * 
     * <p><strong>Thread Safety:</strong></p>
     * <p>This reader is thread-safe for single-threaded step execution. For parallel
     * processing with step partitioning, each partition thread gets its own reader
     * instance with independent pagination state.</p>
     * 
     * <p><strong>Transaction Boundaries:</strong></p>
     * <p>The reader executes within the Spring Batch chunk transaction boundary.
     * Each chunk reads up to 100 records (configured in TransactionPostingJobConfig),
     * processes them, writes them, and commits the transaction. If the transaction
     * fails, the chunk is rolled back and can be retried.</p>
     * 
     * @param entityManagerFactory JPA EntityManagerFactory for database access,
     *        injected by Spring from the DataSourceConfig configuration class.
     *        This factory provides EntityManager instances for executing JPQL
     *        queries against the PostgreSQL database.
     * 
     * @return Configured JpaPagingItemReader&lt;DailyTransaction&gt; ready for use
     *         in Spring Batch Step definition. The reader is in an initialized state
     *         after afterPropertiesSet() is called, ready to begin reading records.
     * 
     * @throws Exception if reader initialization fails (e.g., invalid JPQL query,
     *         EntityManagerFactory not available, or database connection error).
     *         Exceptions during initialization cause the Spring application context
     *         startup to fail, preventing job execution with misconfigured readers.
     * 
     * @see org.springframework.batch.item.database.JpaPagingItemReader
     * @see jakarta.persistence.EntityManagerFactory
     * @see com.aws.carddemo.config.DataSourceConfig
     * @see com.aws.carddemo.batch.config.TransactionPostingJobConfig
     */
    @Bean
    public JpaPagingItemReader<DailyTransaction> dailyTransactionReader(
            EntityManagerFactory entityManagerFactory) throws Exception {
        
        JpaPagingItemReader<DailyTransaction> reader = new JpaPagingItemReader<>();
        
        // Set unique bean name for Spring context and ExecutionContext state storage
        reader.setName("dailyTransactionReader");
        
        // Configure JPQL query to filter PENDING transactions in chronological order
        // This replaces COBOL sequential file read pattern from CBTRN01C.cbl
        reader.setQueryString(JPQL_QUERY);
        
        // Set page size for database fetches (1000 records per query)
        // Balances memory usage (approx 350KB per page) with query performance
        reader.setPageSize(PAGE_SIZE);
        
        // Inject EntityManagerFactory for JPA/Hibernate database access
        // Replaces COBOL file handle management from DALYTRAN-FILE SELECT statement
        reader.setEntityManagerFactory(entityManagerFactory);
        
        // Enable state persistence for job restart capability
        // Stores pagination offset in ExecutionContext after each successful read
        // On restart, reader resumes from last processed record, avoiding duplicates
        reader.setSaveState(true);
        
        // Initialize reader (validates configuration, prepares query execution)
        // Must be called before reader can be used in Step execution
        // Throws exception if JPQL query is invalid or EntityManagerFactory is null
        reader.afterPropertiesSet();
        
        return reader;
    }
}
