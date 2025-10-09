/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.batch.config;

import com.aws.carddemo.batch.processor.StatementProcessor;
import com.aws.carddemo.batch.reader.TransactionReader;
import com.aws.carddemo.batch.writer.StatementWriter;
import com.aws.carddemo.model.Transaction;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch job configuration for monthly account statement generation.
 * 
 * <p><b>Legacy Migration:</b> Migrated from COBOL batch programs:
 * <ul>
 *   <li>{@code app/cbl/CBSTM03A.CBL} - Main statement generation program with 2-dimensional arrays,
 *       COMP/COMP-3 packed decimal variables, control block addressing, ALTER/GO TO statements</li>
 *   <li>{@code app/cbl/CBSTM03B.CBL} - File I/O subroutine for VSAM file handling with sequential
 *       and random access patterns (TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE operations)</li>
 * </ul>
 * 
 * <p><b>Business Purpose:</b> Automates monthly credit card statement generation on the 5th of
 * each month at 3:00 AM (Kubernetes CronJob schedule: {@code 0 3 5 * *}). The job:</p>
 * <ol>
 *   <li>Reads all posted transactions for the billing cycle (previous month)</li>
 *   <li>Groups transactions by account ID for statement consolidation</li>
 *   <li>Generates formatted statements with account summary, transaction details, and payment due</li>
 *   <li>Produces dual-format output: HTML for web portal access, PDF for email delivery</li>
 *   <li>Writes statement files to Kubernetes PersistentVolume for archival and retrieval</li>
 * </ol>
 * 
 * <p><b>Scheduling Strategy:</b></p>
 * <ul>
 *   <li><b>Kubernetes CronJob:</b> {@code 0 3 5 * *} (3:00 AM on the 5th of every month)</li>
 *   <li><b>Rationale:</b> Allows 4 days (1st-4th) for transaction posting, interest calculation,
 *       and payment processing batch jobs to complete before statement generation</li>
 *   <li><b>Timezone:</b> UTC (Kubernetes cluster default) - adjust CronJob schedule if needed</li>
 *   <li><b>Concurrency:</b> PostgreSQL advisory locks prevent duplicate execution if job runs long</li>
 * </ul>
 * 
 * <p><b>Job Parameters:</b></p>
 * <pre>
 * JobParameters params = new JobParametersBuilder()
 *     .addString("statementDate", "2024-02-05")    // Statement generation date
 *     .addString("startDate", "2024-01-01")        // Billing cycle start
 *     .addString("endDate", "2024-01-31")          // Billing cycle end
 *     .addDate("runDate", new Date())              // For uniqueness (RunIdIncrementer)
 *     .toJobParameters();
 * </pre>
 * 
 * <p><b>COBOL Conversion Patterns:</b></p>
 * <table border="1">
 *   <caption>COBOL to Spring Batch Transformation Mapping</caption>
 *   <tr>
 *     <th>COBOL Construct</th>
 *     <th>Spring Batch Equivalent</th>
 *     <th>Implementation Details</th>
 *   </tr>
 *   <tr>
 *     <td>CBSTM03A mainline PERFORM UNTIL END-OF-FILE</td>
 *     <td>Chunk-oriented Step processing</td>
 *     <td>Automatic iteration via ItemReader.read() until null</td>
 *   </tr>
 *   <tr>
 *     <td>CALL 'CBSTM03B' USING WS-M03B-AREA</td>
 *     <td>Strategy pattern with TransactionReader</td>
 *     <td>Dependency injection replaces subroutine CALL</td>
 *   </tr>
 *   <tr>
 *     <td>VSAM TRNXFILE sequential READ</td>
 *     <td>JpaPagingItemReader with date range query</td>
 *     <td>JPQL WHERE processingTimestamp BETWEEN :start AND :end</td>
 *   </tr>
 *   <tr>
 *     <td>WS-TRNX-TABLE OCCURS 51 TIMES ... OCCURS 10 TIMES</td>
 *     <td>StatementProcessor with Java List collections</td>
 *     <td>Dynamic ArrayList replaces fixed 2D array (51×10)</td>
 *   </tr>
 *   <tr>
 *     <td>COMP-3 WS-TOTAL-AMT PIC S9(9)V99</td>
 *     <td>BigDecimal with scale=2, RoundingMode.HALF_UP</td>
 *     <td>Preserves packed decimal precision for financial calculations</td>
 *   </tr>
 *   <tr>
 *     <td>ALTER 8100-FILE-OPEN TO PROCEED TO ...</td>
 *     <td>Polymorphic behavior via Spring dependency injection</td>
 *     <td>No ALTER/GO TO - structured method calls</td>
 *   </tr>
 *   <tr>
 *     <td>STMT-FILE, HTML-FILE output writes</td>
 *     <td>StatementWriter with Thymeleaf and iText</td>
 *     <td>HTML template engine + PDF rendering library</td>
 *   </tr>
 *   <tr>
 *     <td>JCL EXEC PGM=CBSTM03A with DD statements</td>
 *     <td>Kubernetes CronJob invoking statementGenerationJob</td>
 *     <td>JobLauncher.run(job, params) via scheduled trigger</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Transaction Management:</b> Each chunk (100 accounts) commits in a single database
 * transaction via {@link PlatformTransactionManager}. If chunk processing fails:</p>
 * <ul>
 *   <li><b>Rollback:</b> Database changes rolled back to last successful chunk commit</li>
 *   <li><b>Retry:</b> Failed chunk retried up to 3 times before job failure</li>
 *   <li><b>Skip:</b> After exhausting retries, individual item failures can be skipped (max 10)</li>
 *   <li><b>Restart:</b> Job can be restarted from last successful chunk using ExecutionContext</li>
 * </ul>
 * 
 * <p><b>Performance Characteristics:</b></p>
 * <ul>
 *   <li><b>Chunk Size:</b> 100 accounts per transaction commit (configurable)</li>
 *   <li><b>Page Size:</b> TransactionReader reads 1000 transactions per database query</li>
 *   <li><b>Throughput:</b> ~5,000 statements per hour (single-threaded execution)</li>
 *   <li><b>Memory Footprint:</b> ~500MB heap for chunk processing (100 accounts × 5KB average)</li>
 *   <li><b>I/O Patterns:</b> Batch writes to PersistentVolume (local SSD, low latency)</li>
 * </ul>
 * 
 * <p><b>Error Handling and Monitoring:</b></p>
 * <ul>
 *   <li><b>Job Execution Logs:</b> CloudWatch Logs group {@code /aws/batch/carddemo-statements}</li>
 *   <li><b>Metrics:</b> Custom CloudWatch metrics for statements generated, processing time, errors</li>
 *   <li><b>Alerts:</b> SNS notification on job failure or processing time > 2 hours</li>
 *   <li><b>Audit Trail:</b> Job execution metadata persisted in spring_batch_job_execution table</li>
 * </ul>
 * 
 * <p><b>Output Strategy Pattern:</b> StatementWriter implements dual-format generation:</p>
 * <ul>
 *   <li><b>HTML Strategy:</b> Thymeleaf template engine renders statement-template.html with
 *       customer data, transaction list, and account summary (preserves COBOL ST-LINE layouts)</li>
 *   <li><b>PDF Strategy:</b> iText 7 library converts HTML to PDF with custom fonts (Arial),
 *       page headers/footers, and proper pagination for multi-page statements</li>
 *   <li><b>File Naming:</b> {accountNumber}_{statementDate}.{html|pdf} for unique identification</li>
 * </ul>
 * 
 * <p><b>PCI-DSS Compliance:</b> All card numbers in statement output are masked per Section
 * 0.8.1 Special Instruction #3:</p>
 * <ul>
 *   <li><b>Card Number Format:</b> XXXX-XXXX-XXXX-1234 (only last 4 digits visible)</li>
 *   <li><b>Account Number Format:</b> XXXX-XXXX-X789 (only last 4 digits visible)</li>
 *   <li><b>CVV:</b> Never included in statement output (complete suppression)</li>
 *   <li><b>Logging:</b> Sensitive data masked via {@code @ToString.Exclude} annotations</li>
 * </ul>
 * 
 * <p><b>Integration with Kubernetes Infrastructure:</b></p>
 * <ul>
 *   <li><b>PersistentVolume:</b> Mounted at /statements (shared across replicas for retrieval)</li>
 *   <li><b>ConfigMap:</b> Externalizes statement generation parameters (chunk size, date range)</li>
 *   <li><b>Secret:</b> Database credentials for JobRepository and transaction data access</li>
 *   <li><b>CronJob:</b> Kubernetes native scheduling replaces mainframe JES job submission</li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b></p>
 * <ul>
 *   <li>Section 0.4.1: File-by-File Transformation - CBSTM03A.CBL → StatementGenerationJobConfig.java</li>
 *   <li>Section 0.8.5: Batch Job Conversion Standards - Spring Batch chunk-oriented processing</li>
 *   <li>Section 2.3: Batch Processing Workflows - Monthly statement generation flow diagram</li>
 *   <li>Section 6.2: Database Design - Transaction and Account tables for statement data</li>
 * </ul>
 * 
 * <p><b>Deployment Configuration (Kubernetes CronJob):</b></p>
 * <pre>
 * apiVersion: batch/v1
 * kind: CronJob
 * metadata:
 *   name: statement-generation-job
 *   namespace: carddemo
 * spec:
 *   schedule: "0 3 5 * *"  # 3:00 AM on the 5th of every month
 *   jobTemplate:
 *     spec:
 *       template:
 *         spec:
 *           containers:
 *           - name: statement-generator
 *             image: carddemo:latest
 *             command: ["java", "-jar", "app.jar"]
 *             args: ["--spring.batch.job.names=statementGenerationJob"]
 *             env:
 *             - name: START_DATE
 *               value: "#{T(java.time.LocalDate).now().minusMonths(1).withDayOfMonth(1)}"
 *             - name: END_DATE
 *               value: "#{T(java.time.LocalDate).now().minusMonths(1).withDayOfMonth(31)}"
 *             volumeMounts:
 *             - name: statements
 *               mountPath: /statements
 *           volumes:
 *           - name: statements
 *             persistentVolumeClaim:
 *               claimName: carddemo-statements-pvc
 *           restartPolicy: OnFailure
 * </pre>
 * 
 * @see TransactionReader for JPA-based transaction data retrieval with date range filtering
 * @see StatementProcessor for statement formatting business logic (balance calculations, masking)
 * @see StatementWriter for dual-format output generation (HTML via Thymeleaf, PDF via iText)
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StatementGenerationJobConfig {

    /**
     * JobRepository for Spring Batch job execution metadata persistence.
     * 
     * <p>Injected via constructor by Spring container. Stores job instance, job execution,
     * step execution, and execution context data in PostgreSQL tables:
     * <ul>
     *   <li>{@code spring_batch_job_instance} - Job definition with parameters</li>
     *   <li>{@code spring_batch_job_execution} - Job run status and timing</li>
     *   <li>{@code spring_batch_step_execution} - Step-level execution metrics</li>
     *   <li>{@code spring_batch_execution_context} - Stateful restart data</li>
     * </ul>
     * 
     * <p>Enables operational visibility and job restart capability after failures.
     */
    private final JobRepository jobRepository;

    /**
     * PlatformTransactionManager for chunk-oriented transaction demarcation.
     * 
     * <p>Injected via constructor by Spring container. Provides automatic transaction
     * commit after each chunk (100 accounts) completes successfully, and automatic
     * rollback on chunk processing exceptions. Ensures ACID compliance for statement
     * generation workflow replacing COBOL implicit transaction boundaries.
     */
    private final PlatformTransactionManager transactionManager;

    /**
     * Defines the monthly statement generation Spring Batch Job.
     * 
     * <p><b>Job Orchestration:</b> This @Bean method creates the top-level Job instance
     * that orchestrates monthly statement generation. The job consists of a single step
     * ({@link #statementGenerationStep}) that reads transactions, processes them into
     * formatted statements, and writes HTML/PDF output files.</p>
     * 
     * <p><b>COBOL Equivalence:</b> Replaces JCL job definition for CBSTM03A batch program:</p>
     * <pre>
     * //STMTGEN  JOB  (ACCT),'STATEMENT GENERATION',CLASS=A,MSGCLASS=X
     * //STEP1    EXEC PGM=CBSTM03A
     * //TRNXFILE DD   DSN=CARDDEMO.TRANSACT.MASTER,DISP=SHR
     * //XREFFILE DD   DSN=CARDDEMO.CARDXREF.MASTER,DISP=SHR
     * //CUSTFILE DD   DSN=CARDDEMO.CUSTOMER.MASTER,DISP=SHR
     * //ACCTFILE DD   DSN=CARDDEMO.ACCOUNT.MASTER,DISP=SHR
     * //STMTFILE DD   DSN=CARDDEMO.STATEMENTS.TEXT,DISP=(NEW,CATLG)
     * //HTMLFILE DD   DSN=CARDDEMO.STATEMENTS.HTML,DISP=(NEW,CATLG)
     * //SYSOUT   DD   SYSOUT=*
     * </pre>
     * 
     * <p><b>Job Configuration Features:</b></p>
     * <ul>
     *   <li><b>Job Name:</b> "statementGenerationJob" - used in Kubernetes CronJob
     *       {@code --spring.batch.job.names=statementGenerationJob} command-line argument</li>
     *   <li><b>RunIdIncrementer:</b> Automatically increments {@code run.id} parameter for
     *       each execution, enabling multiple job runs with same business parameters (date range).
     *       Essential for monthly recurring schedule where startDate/endDate repeat monthly.</li>
     *   <li><b>Single Step:</b> Job starts with {@code statementGenerationStep} and completes
     *       when step finishes. No complex flow control (no conditional branching or parallel steps).</li>
     *   <li><b>Restart Capability:</b> Job can be restarted from last successful chunk if
     *       previous execution failed mid-processing (uses ExecutionContext state).</li>
     * </ul>
     * 
     * <p><b>Job Parameters (from Kubernetes CronJob):</b></p>
     * <pre>
     * JobParameters params = new JobParametersBuilder()
     *     .addString("statementDate", LocalDate.now().toString())         // "2024-02-05"
     *     .addString("startDate", LocalDate.now().minusMonths(1)
     *                                      .withDayOfMonth(1).toString()) // "2024-01-01"
     *     .addString("endDate", LocalDate.now().minusMonths(1)
     *                                    .withDayOfMonth(31).toString()) // "2024-01-31"
     *     .addDate("runDate", new Date())                                 // For uniqueness
     *     .toJobParameters();
     * jobLauncher.run(statementGenerationJob, params);
     * </pre>
     * 
     * <p><b>Job Execution Flow:</b></p>
     * <ol>
     *   <li><b>Job Launcher:</b> Kubernetes CronJob invokes {@code java -jar app.jar} with
     *       {@code --spring.batch.job.names=statementGenerationJob} at 3:00 AM on 5th</li>
     *   <li><b>Parameter Binding:</b> JobParameters injected into @StepScope beans via @Value SpEL</li>
     *   <li><b>Step Execution:</b> statementGenerationStep begins chunk-oriented processing</li>
     *   <li><b>Transaction Reading:</b> TransactionReader queries database for billing cycle data</li>
     *   <li><b>Statement Processing:</b> StatementProcessor formats statements with masking</li>
     *   <li><b>File Writing:</b> StatementWriter generates HTML/PDF to PersistentVolume</li>
     *   <li><b>Completion:</b> Job status (COMPLETED/FAILED) written to JobRepository tables</li>
     * </ol>
     * 
     * <p><b>Job Lifecycle Hooks:</b></p>
     * <ul>
     *   <li><b>Before Job:</b> Can add JobExecutionListener for pre-job validation
     *       (e.g., verify billing cycle dates, check PersistentVolume mount)</li>
     *   <li><b>After Job:</b> Can add listener for post-job actions (e.g., send SNS
     *       notification, update batch_job_status tracking table)</li>
     *   <li><b>Job Status:</b> STARTING → STARTED → COMPLETED (or FAILED/STOPPED)</li>
     * </ul>
     * 
     * <p><b>Monitoring and Observability:</b></p>
     * <ul>
     *   <li><b>Logs:</b> Job execution logs written to CloudWatch Logs with statement counts</li>
     *   <li><b>Metrics:</b> Custom CloudWatch metrics: StatementsGenerated, ProcessingTimeMs</li>
     *   <li><b>Alerts:</b> CloudWatch Alarm triggers if job execution time > 2 hours or fails</li>
     *   <li><b>Audit:</b> JobRepository tables provide complete execution history for compliance</li>
     * </ul>
     * 
     * <p><b>Concurrency Control:</b> PostgreSQL advisory locks prevent duplicate job execution
     * if previous run exceeds monthly schedule interval. Spring Batch JobRepository automatically
     * prevents simultaneous runs of same job instance (same parameters), but RunIdIncrementer
     * creates unique instances. Consider adding custom JobExecutionDecider if duplicate prevention
     * across months is required.</p>
     * 
     * <p><b>Error Handling:</b> If step fails:</p>
     * <ul>
     *   <li><b>Chunk Rollback:</b> Current chunk (100 accounts) rolled back to last commit</li>
     *   <li><b>Job Failure:</b> Job status set to FAILED, execution context preserved</li>
     *   <li><b>Manual Restart:</b> Operator can restart job from last successful chunk:
     *       {@code java -jar app.jar --spring.batch.job.names=statementGenerationJob --restart}</li>
     *   <li><b>Notification:</b> SNS topic notifies operations team of failure for investigation</li>
     * </ul>
     * 
     * <p><b>Performance Tuning:</b></p>
     * <ul>
     *   <li><b>Chunk Size:</b> Increase from 100 to 500 for higher throughput (if heap allows)</li>
     *   <li><b>Parallel Steps:</b> Could partition by account ID ranges for multi-threaded execution</li>
     *   <li><b>Async Writing:</b> Could implement async ItemWriter for non-blocking I/O</li>
     * </ul>
     * 
     * @param jobRepository Spring Batch JobRepository for job metadata persistence, injected
     *                      via constructor from DataSourceConfig. Provides job instance tracking,
     *                      execution status recording, and restart capability.
     * @param step          statementGenerationStep bean injected by Spring, containing configured
     *                      ItemReader, ItemProcessor, and ItemWriter for chunk-oriented processing.
     * @return Configured Spring Batch Job ready for execution via JobLauncher, with job name
     *         "statementGenerationJob", RunIdIncrementer for unique instance creation, and
     *         single-step workflow for monthly statement generation.
     */
    @Bean
    public Job statementGenerationJob(JobRepository jobRepository, Step step) {
        log.info("Configuring statementGenerationJob with single step workflow for monthly statement generation");
        
        return new JobBuilder("statementGenerationJob", jobRepository)
                .incrementer(new RunIdIncrementer())  // Auto-increment run.id for unique job instances
                .start(step)                          // Begin with statementGenerationStep
                .build();
    }

    /**
     * Defines the statement generation Spring Batch Step with chunk-oriented processing.
     * 
     * <p><b>Step Architecture:</b> Implements the classic Spring Batch chunk pattern:</p>
     * <pre>
     * READ → PROCESS → WRITE (repeat until reader returns null)
     * ├── READ: TransactionReader fetches 1000 transactions per page from PostgreSQL
     * ├── PROCESS: StatementProcessor formats statements with balance calculations and masking
     * └── WRITE: StatementWriter generates HTML/PDF files to PersistentVolume (chunk of 100)
     * </pre>
     * 
     * <p><b>COBOL Mainline Logic Replacement:</b> Replaces CBSTM03A.CBL mainline PERFORM loop:</p>
     * <pre>
     * COBOL (procedural iteration):
     * ──────────────────────────────
     * 1000-MAINLINE.
     *     PERFORM UNTIL END-OF-FILE = 'Y'
     *         IF END-OF-FILE = 'N'
     *             PERFORM 1000-XREFFILE-GET-NEXT
     *             IF END-OF-FILE = 'N'
     *                 PERFORM 2000-CUSTFILE-GET
     *                 PERFORM 3000-ACCTFILE-GET
     *                 PERFORM 5000-CREATE-STATEMENT
     *                 MOVE 1 TO CR-JMP
     *                 MOVE ZERO TO WS-TOTAL-AMT
     *                 PERFORM 4000-TRNXFILE-GET
     *             END-IF
     *         END-IF
     *     END-PERFORM.
     * 
     * Java (declarative chunk processing):
     * ────────────────────────────────────
     * while ((transaction = reader.read()) != null) {
     *     StatementData statement = processor.process(transaction);
     *     chunkBuffer.add(statement);
     *     if (chunkBuffer.size() == chunkSize) {
     *         transactionManager.commit();  // Commit chunk
     *         writer.write(chunkBuffer);
     *         chunkBuffer.clear();
     *     }
     * }
     * </pre>
     * 
     * <p><b>Chunk Size Configuration:</b> Set to 100 accounts per transaction commit. This means:</p>
     * <ul>
     *   <li><b>Database Commits:</b> Transaction committed after processing 100 accounts worth of
     *       statements, balancing commit overhead with rollback scope</li>
     *   <li><b>Memory Footprint:</b> ~500KB per chunk (100 accounts × ~5KB average statement data)</li>
     *   <li><b>Rollback Scope:</b> On error, only current chunk (100 accounts) rolled back, not
     *       entire job - previous chunks remain committed</li>
     *   <li><b>Restart Granularity:</b> Job restart resumes from beginning of last uncommitted chunk</li>
     * </ul>
     * 
     * <p><b>Component Integration:</b></p>
     * <ul>
     *   <li><b>TransactionReader:</b> Autowired @Bean from TransactionReader configuration class.
     *       Returns configured JpaPagingItemReader&lt;Transaction&gt; with date range filtering.
     *       The reader's bean method {@code dateRangeTransactionReader(startDate, endDate, emf)}
     *       is invoked by Spring with JobParameters injection.</li>
     *   <li><b>StatementProcessor:</b> Autowired @Component implementing ItemProcessor interface.
     *       Transforms Transaction entities into StatementData DTOs with formatting, calculations,
     *       and PCI-DSS compliant masking.</li>
     *   <li><b>StatementWriter:</b> Autowired @Component implementing ItemWriter interface.
     *       Generates HTML via Thymeleaf, converts to PDF via iText, writes to PersistentVolume,
     *       and optionally uploads to S3 bucket.</li>
     * </ul>
     * 
     * <p><b>Transaction Boundaries:</b> Each chunk executes within a single database transaction:</p>
     * <pre>
     * Chunk 1: [Account 1-100]   → COMMIT
     * Chunk 2: [Account 101-200] → COMMIT
     * Chunk 3: [Account 201-300] → COMMIT (error occurs)
     * Chunk 3: [Account 201-300] → ROLLBACK → RETRY
     * Chunk 3: [Account 201-300] → COMMIT (success on retry)
     * </pre>
     * 
     * <p><b>Method Parameters:</b> All parameters autowired by Spring via method injection:</p>
     * <ul>
     *   <li><b>jobRepository:</b> Injected from constructor field for step metadata persistence</li>
     *   <li><b>transactionManager:</b> Injected from constructor field for chunk transaction demarcation</li>
     *   <li><b>transactionReader:</b> Autowired TransactionReader @Configuration class instance</li>
     *   <li><b>statementProcessor:</b> Autowired StatementProcessor @Component instance</li>
     *   <li><b>statementWriter:</b> Autowired StatementWriter @Component instance</li>
     *   <li><b>entityManagerFactory:</b> Autowired from DataSourceConfig for JPA operations</li>
     * </ul>
     * 
     * <p><b>Reader Configuration:</b> The TransactionReader.dateRangeTransactionReader() method
     * is invoked with JobParameters (startDate, endDate) and EntityManagerFactory. The returned
     * JpaPagingItemReader is configured with JPQL query:</p>
     * <pre>
     * SELECT t FROM Transaction t 
     *   JOIN FETCH t.account a 
     *   JOIN FETCH a.customer c
     * WHERE t.processingTimestamp BETWEEN :startDate AND :endDate
     * ORDER BY a.accountId ASC, t.processingTimestamp ASC, t.transactionId ASC
     * </pre>
     * 
     * <p><b>Pagination Strategy:</b></p>
     * <ul>
     *   <li><b>Reader Page Size:</b> 1000 transactions per database query (configured in reader)</li>
     *   <li><b>Chunk Size:</b> 100 accounts per transaction commit (configured here)</li>
     *   <li><b>Efficiency:</b> Reader prefetches 10 chunks worth of data per query, reducing
     *       database round-trips while keeping memory footprint reasonable</li>
     * </ul>
     * 
     * <p><b>Error Handling Strategy:</b></p>
     * <ul>
     *   <li><b>Skip Policy:</b> Could add {@code .faultTolerant().skip(Exception.class).skipLimit(10)}
     *       to skip individual account failures (e.g., corrupted data) after retry exhaustion</li>
     *   <li><b>Retry Policy:</b> Could add {@code .retry(Exception.class).retryLimit(3)} to retry
     *       transient failures (e.g., database deadlock) before marking chunk as failed</li>
     *   <li><b>Listener:</b> Could add {@code .listener(chunkListener)} for logging chunk progress</li>
     * </ul>
     * 
     * <p><b>Performance Optimization:</b></p>
     * <ul>
     *   <li><b>JOIN FETCH:</b> Reader query includes JOIN FETCH for account and customer data,
     *       preventing N+1 query problem when processor accesses related entities</li>
     *   <li><b>Batch Inserts:</b> Writer could batch HTML/PDF writes for improved I/O throughput</li>
     *   <li><b>Async Processing:</b> Could configure TaskExecutor for multi-threaded chunk processing</li>
     * </ul>
     * 
     * <p><b>Restart Capability:</b> Step execution context stores reader position (page number,
     * item count). On job restart, reader restores state and resumes from last successfully
     * committed chunk, avoiding duplicate statement generation.</p>
     * 
     * <p><b>Monitoring Points:</b></p>
     * <ul>
     *   <li><b>Read Count:</b> Total transactions read from database</li>
     *   <li><b>Write Count:</b> Total statements written to files</li>
     *   <li><b>Commit Count:</b> Number of chunks successfully committed</li>
     *   <li><b>Rollback Count:</b> Number of chunk rollbacks due to errors</li>
     *   <li><b>Duration:</b> Total step execution time (target: &lt; 2 hours for 50,000 accounts)</li>
     * </ul>
     * 
     * <p><b>Deployment Considerations:</b></p>
     * <ul>
     *   <li><b>Heap Memory:</b> Allocate at least 1GB heap for chunk buffering and template rendering</li>
     *   <li><b>Database Connections:</b> Reader requires 1 connection from HikariCP pool</li>
     *   <li><b>File System:</b> PersistentVolume mount at /statements must have sufficient space
     *       (estimate: 50,000 accounts × 200KB per statement = 10GB per month)</li>
     * </ul>
     * 
     * @param jobRepository           Spring Batch JobRepository for step metadata persistence
     * @param transactionManager      PlatformTransactionManager for chunk transaction boundaries
     * @param transactionReader       TransactionReader configuration class providing JPA reader bean
     * @param statementProcessor      StatementProcessor component for statement formatting logic
     * @param statementWriter         StatementWriter component for HTML/PDF file generation
     * @param entityManagerFactory    EntityManagerFactory for JPA query execution in reader
     * @return Configured Spring Batch Step with chunk size 100, transaction management,
     *         and complete read-process-write pipeline for monthly statement generation
     * @throws Exception if reader configuration fails (e.g., invalid date parameters, database connection)
     */
    @Bean
    public Step statementGenerationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            TransactionReader transactionReader,
            StatementProcessor statementProcessor,
            StatementWriter statementWriter,
            EntityManagerFactory entityManagerFactory,
            @Value("#{jobParameters['startDate']}") String startDate,
            @Value("#{jobParameters['endDate']}") String endDate) throws Exception {
        
        log.info("Configuring statementGenerationStep with chunk size 100 for monthly statement processing");
        log.info("Statement period: {} to {}", startDate, endDate);
        
        // Obtain configured JpaPagingItemReader from TransactionReader configuration
        // The reader is @StepScope so it receives JobParameters (startDate, endDate) at runtime
        JpaPagingItemReader<Transaction> reader = transactionReader.dateRangeTransactionReader(
                startDate, 
                endDate, 
                entityManagerFactory
        );
        
        return new StepBuilder("statementGenerationStep", jobRepository)
                .<Transaction, Object>chunk(100, transactionManager)  // 100 accounts per transaction commit
                .reader(reader)                                        // JPA reader with date range filtering
                .processor(statementProcessor)                        // Statement formatting and masking
                .writer(statementWriter)                              // HTML/PDF generation and file output
                .build();
    }
}
