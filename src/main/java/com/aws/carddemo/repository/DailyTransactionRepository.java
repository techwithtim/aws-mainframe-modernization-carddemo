package com.aws.carddemo.repository;

import com.aws.carddemo.model.DailyTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository interface for DailyTransaction entity providing batch processing input data access.
 * Migrated from: app/cbl/CBTRN01C.cbl (DALYTRAN-FILE sequential READ operations)
 * 
 * <p>This repository replaces COBOL VSAM sequential file operations on the daily transaction feed file,
 * enabling Spring Batch chunk-oriented processing of incoming transaction records. The COBOL program
 * CBTRN01C.cbl reads daily transaction records sequentially (READ DALYTRAN-FILE INTO DALYTRAN-RECORD)
 * until end-of-file status '10', validates card numbers via cross-reference lookups, posts transactions
 * to account balances, and creates transaction history records.</p>
 * 
 * <h3>COBOL File Operations Replaced</h3>
 * <pre>
 * COBOL (CBTRN01C.cbl):
 *   SELECT DALYTRAN-FILE ASSIGN TO DALYTRAN
 *          ORGANIZATION IS SEQUENTIAL
 *          ACCESS MODE IS SEQUENTIAL
 *          FILE STATUS IS DALYTRAN-STATUS.
 *   
 *   1000-DALYTRAN-GET-NEXT.
 *       READ DALYTRAN-FILE INTO DALYTRAN-RECORD.
 *       IF DALYTRAN-STATUS = '00'
 *           MOVE 0 TO APPL-RESULT
 *       ELSE
 *           IF DALYTRAN-STATUS = '10'
 *               MOVE 16 TO APPL-RESULT
 *               MOVE 'Y' TO END-OF-DAILY-TRANS-FILE
 *           END-IF
 *       END-IF.
 * 
 * Java (Spring Batch):
 *   List&lt;DailyTransaction&gt; unprocessed = 
 *       dailyTransactionRepository.findByProcessingStatusOrderByOriginalTimestampAsc("PENDING", pageable);
 * </pre>
 * 
 * <h3>Spring Batch Integration</h3>
 * <p>This repository integrates with Spring Batch components defined in TransactionPostingJobConfig:</p>
 * <ul>
 *   <li><strong>Reader:</strong> JpaPagingItemReader configured with chunk size 100 reads unprocessed records</li>
 *   <li><strong>Processor:</strong> TransactionProcessor validates card numbers, calculates balances</li>
 *   <li><strong>Writer:</strong> TransactionWriter posts transactions, updates processing_status to 'PROCESSED'</li>
 * </ul>
 * 
 * <h3>Processing Status Values</h3>
 * <ul>
 *   <li><strong>PENDING:</strong> New record from daily feed, awaiting batch processing</li>
 *   <li><strong>PROCESSED:</strong> Successfully validated and posted to transaction history</li>
 *   <li><strong>FAILED:</strong> Validation or processing error (invalid card, insufficient funds, etc.)</li>
 * </ul>
 * 
 * <h3>Query Methods</h3>
 * <p>Spring Data JPA derives query implementations from method names following naming conventions:</p>
 * <ul>
 *   <li><code>findByProcessingStatus</code> → SELECT ... WHERE processing_status = ?</li>
 *   <li><code>OrderByOriginalTimestampAsc</code> → ORDER BY original_timestamp ASC</li>
 *   <li><code>Pageable</code> parameter → LIMIT ? OFFSET ? (pagination)</li>
 * </ul>
 * 
 * <h3>Batch Processing Flow</h3>
 * <pre>
 * 1. Daily feed file loaded into daily_transaction table with processing_status='PENDING'
 * 2. Spring Batch job triggered (scheduled or manual)
 * 3. JpaPagingItemReader calls findByProcessingStatusOrderByOriginalTimestampAsc("PENDING", PageRequest.of(page, 100))
 * 4. Chunk of 100 records read and passed to TransactionProcessor
 * 5. TransactionProcessor validates:
 *    - Card number exists in card_xref table
 *    - Account ID exists in account table
 *    - Account has sufficient credit/balance
 * 6. TransactionWriter:
 *    - Creates transaction history record
 *    - Updates account balance
 *    - Sets daily_transaction.processing_status='PROCESSED'
 * 7. Spring Batch commits chunk transaction (all 100 records succeed or rollback)
 * 8. Repeat until no more PENDING records
 * </pre>
 * 
 * <h3>Error Handling and Restartability</h3>
 * <p>Processing status enables Spring Batch job restart capability:</p>
 * <ul>
 *   <li>Job failure mid-execution: Uncommitted chunks remain PENDING</li>
 *   <li>Job restart: Resumes from first PENDING record (skips PROCESSED)</li>
 *   <li>Idempotency: Reprocessing same records is safe (processing_status check)</li>
 * </ul>
 * 
 * <h3>Performance Considerations</h3>
 * <ul>
 *   <li><strong>Index:</strong> idx_daily_transaction_status on processing_status for fast PENDING lookups</li>
 *   <li><strong>Chunk Size:</strong> 100 records balances memory usage and transaction commit frequency</li>
 *   <li><strong>Pagination:</strong> Prevents loading entire PENDING set into memory</li>
 *   <li><strong>Ordering:</strong> original_timestamp ASC ensures FIFO processing (chronological order)</li>
 * </ul>
 * 
 * <h3>Data Integrity</h3>
 * <p>Monetary fields preserve exact decimal precision:</p>
 * <ul>
 *   <li>COBOL: DALYTRAN-AMT PIC S9(09)V99 (signed packed decimal)</li>
 *   <li>Java: BigDecimal with @Digits(integer=9, fraction=2)</li>
 *   <li>Database: NUMERIC(11,2) column</li>
 * </ul>
 * 
 * @see com.aws.carddemo.model.DailyTransaction
 * @see com.aws.carddemo.batch.config.TransactionPostingJobConfig
 * @see com.aws.carddemo.batch.reader.DailyTransactionReader
 * @see com.aws.carddemo.batch.processor.TransactionProcessor
 * @see com.aws.carddemo.batch.writer.TransactionWriter
 * @author AWS CardDemo Modernization Team
 * @version 1.0
 * @since 2024
 */
@Repository
public interface DailyTransactionRepository extends JpaRepository<DailyTransaction, Long> {

    /**
     * Finds all daily transaction records matching the specified processing status.
     * 
     * <p>This method returns all records regardless of count, which may be inefficient
     * for large datasets. Consider using the paginated variant for batch processing.</p>
     * 
     * <p><strong>SQL Generated:</strong></p>
     * <pre>
     * SELECT * FROM daily_transaction 
     * WHERE processing_status = ?
     * </pre>
     * 
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Administrative queries to count pending transactions</li>
     *   <li>Reporting on processing success/failure rates</li>
     *   <li>Small-scale testing with limited data</li>
     * </ul>
     * 
     * @param processingStatus Processing status filter ("PENDING", "PROCESSED", or "FAILED")
     * @return List of all daily transaction records with matching status (may be large)
     * @throws IllegalArgumentException if processingStatus is null
     */
    List<DailyTransaction> findByProcessingStatus(String processingStatus);

    /**
     * Finds all daily transaction records matching the specified processing status,
     * ordered by original timestamp in ascending (chronological) order.
     * 
     * <p>This method ensures FIFO (First-In-First-Out) processing of transactions,
     * maintaining the chronological order from the original merchant authorization timestamps.
     * This is critical for correct balance calculations and transaction history sequencing.</p>
     * 
     * <p><strong>SQL Generated:</strong></p>
     * <pre>
     * SELECT * FROM daily_transaction 
     * WHERE processing_status = ?
     * ORDER BY original_timestamp ASC
     * </pre>
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COBOL sequential READ operations implicitly maintain file order:
     *   1000-DALYTRAN-GET-NEXT.
     *       READ DALYTRAN-FILE INTO DALYTRAN-RECORD.
     * 
     * Java ensures same ordering via ORDER BY original_timestamp ASC clause.
     * </pre>
     * 
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Simple batch jobs with small datasets (few hundred records)</li>
     *   <li>Testing transaction posting logic with sample data</li>
     *   <li>Administrative reports requiring chronological transaction listing</li>
     * </ul>
     * 
     * <p><strong>Warning:</strong> This method loads all matching records into memory.
     * For production batch processing with thousands of records, use the paginated variant
     * {@link #findByProcessingStatusOrderByOriginalTimestampAsc(String, Pageable)}.</p>
     * 
     * @param processingStatus Processing status filter ("PENDING", "PROCESSED", or "FAILED")
     * @return List of all daily transaction records with matching status, chronologically ordered
     * @throws IllegalArgumentException if processingStatus is null
     */
    List<DailyTransaction> findByProcessingStatusOrderByOriginalTimestampAsc(String processingStatus);

    /**
     * Finds daily transaction records matching the specified processing status,
     * ordered by original timestamp in ascending order, with pagination support.
     * 
     * <p><strong>THIS IS THE PRIMARY METHOD FOR SPRING BATCH PROCESSING.</strong>
     * This method enables chunk-oriented processing with configurable page size (typically 100 records),
     * preventing memory exhaustion when processing large daily transaction feeds (10,000+ records).</p>
     * 
     * <p><strong>SQL Generated:</strong></p>
     * <pre>
     * SELECT * FROM daily_transaction 
     * WHERE processing_status = ?
     * ORDER BY original_timestamp ASC
     * LIMIT ? OFFSET ?
     * </pre>
     * 
     * <p><strong>Spring Batch Integration:</strong></p>
     * <pre>
     * // TransactionPostingJobConfig.java
     * {@code @Bean}
     * public JpaPagingItemReader&lt;DailyTransaction&gt; dailyTransactionReader() {
     *     JpaPagingItemReader&lt;DailyTransaction&gt; reader = new JpaPagingItemReader&lt;&gt;();
     *     reader.setEntityManagerFactory(entityManagerFactory);
     *     reader.setPageSize(100); // Chunk size
     *     reader.setQueryProvider(new JpaNativeQueryProvider&lt;&gt;() {
     *         public Query createQuery() {
     *             // Uses this repository method internally
     *             return dailyTransactionRepository
     *                 .findByProcessingStatusOrderByOriginalTimestampAsc(
     *                     "PENDING", 
     *                     PageRequest.of(pageNumber, 100, Sort.by("originalTimestamp").ascending())
     *                 );
     *         }
     *     });
     *     return reader;
     * }
     * </pre>
     * 
     * <p><strong>Pagination Parameters:</strong></p>
     * <ul>
     *   <li><strong>Page Number:</strong> Zero-based page index (0 = first page)</li>
     *   <li><strong>Page Size:</strong> Number of records per page (100 for optimal performance)</li>
     *   <li><strong>Sort:</strong> Automatically applied from method name (original_timestamp ASC)</li>
     * </ul>
     * 
     * <p><strong>Example Usage:</strong></p>
     * <pre>
     * // Retrieve first chunk of 100 pending transactions
     * Pageable firstPage = PageRequest.of(0, 100, Sort.by("originalTimestamp").ascending());
     * List&lt;DailyTransaction&gt; firstChunk = 
     *     dailyTransactionRepository.findByProcessingStatusOrderByOriginalTimestampAsc("PENDING", firstPage);
     * 
     * // Retrieve second chunk
     * Pageable secondPage = PageRequest.of(1, 100, Sort.by("originalTimestamp").ascending());
     * List&lt;DailyTransaction&gt; secondChunk = 
     *     dailyTransactionRepository.findByProcessingStatusOrderByOriginalTimestampAsc("PENDING", secondPage);
     * </pre>
     * 
     * <p><strong>Transaction Processing Workflow:</strong></p>
     * <ol>
     *   <li>Spring Batch reader calls this method with page 0, size 100</li>
     *   <li>100 PENDING records returned in chronological order</li>
     *   <li>TransactionProcessor validates each record (card number, account, balance)</li>
     *   <li>TransactionWriter posts valid transactions, sets processing_status='PROCESSED'</li>
     *   <li>Spring Batch commits chunk transaction (all 100 succeed or rollback)</li>
     *   <li>Reader advances to page 1, repeats until no more PENDING records</li>
     * </ol>
     * 
     * <p><strong>Performance Optimization:</strong></p>
     * <ul>
     *   <li><strong>Index Usage:</strong> idx_daily_transaction_status accelerates WHERE clause</li>
     *   <li><strong>Memory Efficiency:</strong> Only 100 records in memory per chunk</li>
     *   <li><strong>Transaction Scope:</strong> Chunk-level commit reduces lock contention</li>
     *   <li><strong>Parallel Processing:</strong> Multiple job instances can process different pages</li>
     * </ul>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li><strong>Validation Failure:</strong> Skip policy allows up to 10 failed records per chunk</li>
     *   <li><strong>Database Error:</strong> Chunk rollback, records remain PENDING for retry</li>
     *   <li><strong>Job Restart:</strong> Resumes from first PENDING record (idempotent processing)</li>
     * </ul>
     * 
     * <p><strong>Comparison with COBOL:</strong></p>
     * <table border="1">
     *   <tr>
     *     <th>Aspect</th>
     *     <th>COBOL (CBTRN01C.cbl)</th>
     *     <th>Java (Spring Batch)</th>
     *   </tr>
     *   <tr>
     *     <td>File Access</td>
     *     <td>Sequential READ DALYTRAN-FILE</td>
     *     <td>SQL SELECT with LIMIT/OFFSET</td>
     *   </tr>
     *   <tr>
     *     <td>Processing</td>
     *     <td>Record-by-record, implicit commit</td>
     *     <td>Chunk-oriented, explicit commit every 100</td>
     *   </tr>
     *   <tr>
     *     <td>Error Handling</td>
     *     <td>Abend on file error (PERFORM Z-ABEND-PROGRAM)</td>
     *     <td>Skip/retry policy, job restart capability</td>
     *   </tr>
     *   <tr>
     *     <td>Restartability</td>
     *     <td>Manual file repositioning required</td>
     *     <td>Automatic via processing_status flag</td>
     *   </tr>
     * </table>
     * 
     * @param processingStatus Processing status filter ("PENDING" for batch input, "PROCESSED" for history, "FAILED" for errors)
     * @param pageable Pagination and sorting parameters (page number, page size, sort order)
     * @return List of daily transaction records for the requested page, chronologically ordered (typically 100 records)
     * @throws IllegalArgumentException if processingStatus or pageable is null
     * @throws org.springframework.dao.DataAccessException if database query fails
     */
    List<DailyTransaction> findByProcessingStatusOrderByOriginalTimestampAsc(String processingStatus, Pageable pageable);

    /**
     * Find all daily transactions with transaction IDs in the given collection.
     * 
     * <p>This method is used by TransactionWriter to retrieve daily transaction records
     * after posting them to the transaction history table, in order to update their
     * processing status from 'PENDING' to 'PROCESSED'.</p>
     * 
     * <p>Spring Data JPA derives the query implementation from the method name:
     * <code>findByTransactionIdIn</code> → SELECT ... WHERE transaction_id IN (?)</p>
     * 
     * @param transactionIds Collection of transaction IDs to search for
     * @return List of daily transaction records matching the transaction IDs
     * @throws IllegalArgumentException if transactionIds is null
     * @throws org.springframework.dao.DataAccessException if database query fails
     */
    List<DailyTransaction> findByTransactionIdIn(List<String> transactionIds);

    // NOTE: Additional methods are inherited from JpaRepository<DailyTransaction, Long>:
    //
    // <S extends DailyTransaction> S save(S entity)
    //   - Insert new daily transaction record (processing_status='PENDING')
    //   - Update existing record (set processing_status='PROCESSED' after posting)
    //
    // <S extends DailyTransaction> List<S> saveAll(Iterable<S> entities)
    //   - Batch insert daily transaction feed (load from file or external API)
    //
    // Optional<DailyTransaction> findById(Long id)
    //   - Retrieve specific daily transaction by surrogate key
    //
    // List<DailyTransaction> findAll()
    //   - Retrieve all daily transactions (use with caution, may be large)
    //
    // Page<DailyTransaction> findAll(Pageable pageable)
    //   - Paginated retrieval of all daily transactions
    //
    // void delete(DailyTransaction entity)
    //   - Delete processed daily transaction (archival/cleanup)
    //
    // void deleteById(Long id)
    //   - Delete by primary key
    //
    // void deleteAll(Iterable<? extends DailyTransaction> entities)
    //   - Batch delete old daily transactions (retention policy)
    //
    // long count()
    //   - Count total daily transaction records
    //
    // boolean existsById(Long id)
    //   - Check if daily transaction exists
}
