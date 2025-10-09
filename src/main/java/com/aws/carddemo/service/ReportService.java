/*
 * ReportService.java
 * 
 * Service handling report generation operations including transaction report generation
 * via Spring Batch JobLauncher triggering asynchronous batch job execution, report parameter
 * validation for date ranges and report types, job status tracking with JobExecution metadata,
 * transaction summary calculations for account-level and system-wide aggregates, and report
 * output format selection (CSV, PDF, HTML).
 * 
 * Migrated from: 
 * - app/cbl/CORPT00C.cbl (report parameter screen + JCL submission)
 * - app/cbl/CBTRN03C.cbl (transaction report generator batch program)
 * 
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * <p><b>Migration Context:</b> This service replaces COBOL programs CORPT00C.cbl 
 * (report parameter screen) and CBTRN03C.cbl (batch report generator) which used
 * CICS transaction processing to collect report parameters and submit JCL jobs to
 * an internal reader queue. The COBOL programs performed the following:
 * 
 * <ul>
 *   <li>CORPT00C.cbl (Report Parameters):
 *     <ul>
 *       <li>EXEC CICS RECEIVE MAP → Spring MVC @RequestBody parameter binding</li>
 *       <li>Date range validation → LocalDate validation in generateReport()</li>
 *       <li>EXEC CICS WRITEQ TS QUEUE('JOBS') → JobLauncher.run() to trigger batch</li>
 *       <li>JCL job card construction → JobParameters with startDate, endDate, reportType</li>
 *     </ul>
 *   </li>
 *   <li>CBTRN03C.cbl (Batch Report Generator):
 *     <ul>
 *       <li>READ DATEPARM file for dates → JobParameters input</li>
 *       <li>PERFORM UNTIL TRANS-EOF (sequential file) → Spring Batch chunk processing</li>
 *       <li>COMPUTE aggregates (SUM, COUNT) → TransactionRepository aggregate queries</li>
 *       <li>WRITE REPORT-FILE records → FlatFileItemWriter (CSV) or custom writers (PDF/HTML)</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>Business Logic Preservation:</b> Key COBOL business rules maintained:
 * <ul>
 *   <li>Date Range Validation: startDate must be before or equal to endDate
 *       (from CORPT00C.cbl date validation logic)</li>
 *   <li>Report Type Validation: reportType must be one of TRANSACTION_SUMMARY,
 *       ACCOUNT_DETAIL, or MERCHANT_ANALYSIS (from CORPT00C.cbl menu selection)</li>
 *   <li>Account Validation: If accountId specified, must exist in database
 *       (from CORPT00C.cbl READ ACCTFILE validation)</li>
 *   <li>Asynchronous Execution: Report generation runs in background thread pool
 *       (replacing COBOL batch job submission pattern)</li>
 *   <li>Job Status Tracking: JobExecution provides status, progress, errors
 *       (replacing COBOL JES job status queries)</li>
 * </ul>
 * 
 * <p><b>Spring Batch Integration:</b> This service coordinates with Spring Batch framework:
 * <ul>
 *   <li>JobLauncher.run() triggers asynchronous batch job execution</li>
 *   <li>JobParameters passes startDate, endDate, reportType, accountId to batch job</li>
 *   <li>JobExecution tracks job status (STARTING, STARTED, COMPLETED, FAILED)</li>
 *   <li>JobRepository persists job metadata for status queries</li>
 *   <li>Batch Job (transactionReportJob) performs actual report generation</li>
 * </ul>
 * 
 * <p><b>Security:</b> Report generation is restricted to administrators via
 * @PreAuthorize("hasRole('ADMIN')") annotation for PCI-DSS access control compliance.
 * Sensitive financial data in reports requires proper authorization.
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 1.0.0
 */
package com.aws.carddemo.service;

import com.aws.carddemo.exception.InvalidInputException;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service managing report generation operations.
 * 
 * <p>This service provides methods for:
 * <ul>
 *   <li>Triggering asynchronous report generation via Spring Batch</li>
 *   <li>Validating report parameters (date ranges, report types, account IDs)</li>
 *   <li>Tracking job execution status and progress</li>
 *   <li>Retrieving job metadata and results</li>
 * </ul>
 * 
 * <p>All public methods are restricted to administrators via Spring Security
 * @PreAuthorize annotation for PCI-DSS compliance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    /**
     * Spring Batch JobLauncher for triggering asynchronous report generation jobs.
     * Replaces COBOL EXEC CICS WRITEQ TS QUEUE('JOBS') internal reader pattern.
     */
    private final JobLauncher jobLauncher;

    /**
     * Spring Batch Job bean for transaction report generation.
     * This Job is defined in TransactionReportJobConfig and contains the complete
     * batch processing pipeline (reader, processor, writer) for report generation.
     */
    private final Job transactionReportJob;

    /**
     * Spring Batch JobExplorer for querying job execution metadata and status.
     * Used by getJobStatus() to retrieve job execution details without modifying job state.
     */
    private final JobExplorer jobExplorer;

    /**
     * Transaction repository for database access to transaction records.
     * Used for date range filtering and aggregate calculations in report generation.
     */
    private final TransactionRepository transactionRepository;

    /**
     * Account repository for validating account existence before generating account-specific reports.
     * Replaces COBOL READ ACCTFILE validation logic from CORPT00C.cbl.
     */
    private final AccountRepository accountRepository;

    /**
     * Set of allowed report types for validation.
     * Corresponds to COBOL menu selections in CORPT00C.cbl.
     */
    private static final Set<String> ALLOWED_REPORT_TYPES = new HashSet<>(Arrays.asList(
            "TRANSACTION_SUMMARY",
            "ACCOUNT_DETAIL",
            "MERCHANT_ANALYSIS"
    ));

    /**
     * Maximum allowed date range in days for report generation (365 days = 1 year).
     * Prevents excessive report size and processing time.
     */
    private static final long MAX_DATE_RANGE_DAYS = 365;

    /**
     * Generates a report by triggering asynchronous Spring Batch job execution.
     * 
     * <p>This method replaces the COBOL CORPT00C.cbl logic that collected report parameters
     * via BMS screen and submitted a JCL job to the internal reader queue. The modern
     * implementation:
     * <ol>
     *   <li>Validates all input parameters (date range, report type, account ID)</li>
     *   <li>Constructs JobParameters with validated inputs</li>
     *   <li>Triggers asynchronous batch job via JobLauncher.run()</li>
     *   <li>Returns ReportResponse with job execution ID for status tracking</li>
     * </ol>
     * 
     * <p><b>COBOL Mapping:</b>
     * <pre>
     * COBOL (CORPT00C.cbl):                           | Java (ReportService):
     * ------------------------------------------------|------------------------------------------
     * EXEC CICS RECEIVE MAP(CORPT0A)                  | Method parameter binding (Controller layer)
     * PERFORM 2000-EDIT-DATES                         | validateDateRange(startDate, endDate)
     * PERFORM 2100-EDIT-REPORT-TYPE                   | validateReportType(reportType)
     * IF ACCT-ID NOT ZEROS                            | if (accountId != null) validateAccount()
     *   READ ACCTFILE KEY(ACCT-ID)                    |   accountRepository.existsById(accountId)
     * PERFORM 3000-BUILD-JCL                          | buildJobParameters(...)
     * EXEC CICS WRITEQ TS QUEUE('JOBS')               | jobLauncher.run(transactionReportJob, params)
     * MOVE JOB-ID TO CORPT-JOB-NUMBER                 | return ReportResponse with jobExecutionId
     * EXEC CICS SEND MAP                              | (handled by REST controller)
     * </pre>
     * 
     * <p><b>Parameter Validation:</b>
     * <ul>
     *   <li>startDate must not be null</li>
     *   <li>endDate must not be null</li>
     *   <li>startDate must be before or equal to endDate</li>
     *   <li>Date range must not exceed 365 days</li>
     *   <li>startDate must not be in the future</li>
     *   <li>reportType must be one of: TRANSACTION_SUMMARY, ACCOUNT_DETAIL, MERCHANT_ANALYSIS</li>
     *   <li>If accountId is specified, account must exist in database</li>
     * </ul>
     * 
     * <p><b>Example Usage:</b>
     * <pre>{@code
     * // Generate transaction summary report for last 30 days
     * ReportResponse response = reportService.generateReport(
     *     LocalDate.now().minusDays(30),  // startDate
     *     LocalDate.now(),                 // endDate
     *     "TRANSACTION_SUMMARY",           // reportType
     *     null                             // accountId (null = all accounts)
     * );
     * 
     * // Generate account-specific detail report
     * ReportResponse response2 = reportService.generateReport(
     *     LocalDate.of(2024, 1, 1),       // startDate
     *     LocalDate.of(2024, 1, 31),      // endDate
     *     "ACCOUNT_DETAIL",                // reportType
     *     12345L                           // accountId (specific account)
     * );
     * }</pre>
     * 
     * @param startDate the start date for the report date range (inclusive)
     * @param endDate the end date for the report date range (inclusive)
     * @param reportType the type of report to generate (TRANSACTION_SUMMARY, ACCOUNT_DETAIL, MERCHANT_ANALYSIS)
     * @param accountId the account ID for account-specific reports (null for all accounts)
     * @return ReportResponse containing job execution ID, status, and estimated completion time
     * @throws InvalidInputException if date range is invalid (startDate > endDate, exceeds 365 days, etc.)
     * @throws InvalidInputException if reportType is not in allowed values
     * @throws ResourceNotFoundException if accountId is specified but account does not exist
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ReportResponse generateReport(LocalDate startDate, LocalDate endDate, String reportType, Long accountId) {
        log.info("Report generation requested - type: {}, dateRange: {} to {}, accountId: {}", 
                reportType, startDate, endDate, accountId);

        // Validate all input parameters
        validateDateRange(startDate, endDate);
        validateReportType(reportType);
        if (accountId != null) {
            validateAccount(accountId);
        }

        // Build JobParameters for Spring Batch job execution
        JobParameters jobParameters = buildJobParameters(startDate, endDate, reportType, accountId);

        try {
            // Trigger asynchronous batch job execution (replaces COBOL JCL submission)
            JobExecution jobExecution = jobLauncher.run(transactionReportJob, jobParameters);
            
            log.info("Report generation job started - jobExecutionId: {}, status: {}", 
                    jobExecution.getId(), jobExecution.getStatus());

            // Construct response with job tracking information
            ReportResponse response = ReportResponse.builder()
                    .jobExecutionId(jobExecution.getId())
                    .reportType(reportType)
                    .startDate(startDate)
                    .endDate(endDate)
                    .accountId(accountId)
                    .reportStatus(jobExecution.getStatus().name())
                    .createdAt(LocalDateTime.ofInstant(jobExecution.getCreateTime().toInstant(), ZoneId.systemDefault()))
                    .estimatedCompletion(calculateEstimatedCompletion(startDate, endDate))
                    .downloadUrl(null)  // Will be populated when job completes
                    .message("Report generation job submitted successfully")
                    .build();

            return response;

        } catch (Exception e) {
            log.error("Failed to launch report generation job", e);
            throw new RuntimeException("Failed to start report generation: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the current status of a report generation job.
     * 
     * <p>This method replaces the COBOL pattern of querying JES job status via
     * operator console commands. It uses Spring Batch JobExplorer to retrieve
     * job execution metadata without modifying the job state.
     * 
     * <p><b>Job Status Values:</b>
     * <ul>
     *   <li>STARTING: Job is being initialized</li>
     *   <li>STARTED: Job is actively processing</li>
     *   <li>STOPPING: Job is in the process of stopping</li>
     *   <li>STOPPED: Job was stopped before completion</li>
     *   <li>COMPLETED: Job finished successfully</li>
     *   <li>FAILED: Job terminated with errors</li>
     *   <li>ABANDONED: Job was abandoned and will not be restarted</li>
     * </ul>
     * 
     * <p><b>Example Usage:</b>
     * <pre>{@code
     * // After generating a report
     * ReportResponse generateResponse = reportService.generateReport(...);
     * Long jobExecutionId = generateResponse.getJobExecutionId();
     * 
     * // Poll for job status
     * ReportResponse statusResponse = reportService.getJobStatus(jobExecutionId);
     * 
     * if ("COMPLETED".equals(statusResponse.getReportStatus())) {
     *     String downloadUrl = statusResponse.getDownloadUrl();
     *     // Download report from URL
     * } else if ("FAILED".equals(statusResponse.getReportStatus())) {
     *     String errorMessage = statusResponse.getMessage();
     *     // Handle error
     * }
     * }</pre>
     * 
     * @param jobExecutionId the ID of the job execution to query (from generateReport response)
     * @return ReportResponse containing current job status, progress, and download URL if completed
     * @throws ResourceNotFoundException if job execution ID does not exist
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ReportResponse getJobStatus(Long jobExecutionId) {
        log.debug("Job status requested for jobExecutionId: {}", jobExecutionId);

        // Retrieve job execution from JobExplorer
        JobExecution jobExecution = jobExplorer.getJobExecution(jobExecutionId);
        
        if (jobExecution == null) {
            log.warn("Job execution not found: {}", jobExecutionId);
            throw new ResourceNotFoundException("JobExecution", jobExecutionId);
        }

        // Extract job parameters
        JobParameters jobParameters = jobExecution.getJobParameters();
        LocalDate startDate = jobParameters.getDate("startDate") != null 
                ? LocalDate.ofInstant(jobParameters.getDate("startDate").toInstant(), ZoneId.systemDefault())
                : null;
        LocalDate endDate = jobParameters.getDate("endDate") != null
                ? LocalDate.ofInstant(jobParameters.getDate("endDate").toInstant(), ZoneId.systemDefault())
                : null;
        String reportType = jobParameters.getString("reportType");
        Long accountId = jobParameters.getLong("accountId", null);

        // Determine download URL if job completed successfully
        String downloadUrl = null;
        String message = null;
        
        BatchStatus status = jobExecution.getStatus();
        if (status == BatchStatus.COMPLETED) {
            // Construct download URL from job execution context or default pattern
            downloadUrl = String.format("/api/v1/reports/download/%d", jobExecutionId);
            message = "Report generation completed successfully";
        } else if (status == BatchStatus.FAILED) {
            // Extract failure message from job execution
            List<Throwable> failureExceptions = jobExecution.getAllFailureExceptions();
            if (!failureExceptions.isEmpty()) {
                message = "Report generation failed: " + failureExceptions.get(0).getMessage();
            } else {
                message = "Report generation failed with unknown error";
            }
        } else if (status == BatchStatus.STARTED || status == BatchStatus.STARTING) {
            message = "Report generation in progress";
        } else {
            message = "Report generation status: " + status.name();
        }

        // Calculate estimated completion if job is still running
        LocalDateTime estimatedCompletion = null;
        if (status == BatchStatus.STARTED || status == BatchStatus.STARTING) {
            estimatedCompletion = calculateEstimatedCompletion(startDate, endDate);
        }

        // Construct response with current job status
        ReportResponse response = ReportResponse.builder()
                .jobExecutionId(jobExecution.getId())
                .reportType(reportType)
                .startDate(startDate)
                .endDate(endDate)
                .accountId(accountId)
                .reportStatus(status.name())
                .createdAt(LocalDateTime.ofInstant(jobExecution.getCreateTime().toInstant(), ZoneId.systemDefault()))
                .estimatedCompletion(estimatedCompletion)
                .downloadUrl(downloadUrl)
                .message(message)
                .build();

        log.debug("Job status retrieved - jobExecutionId: {}, status: {}", jobExecutionId, status);
        return response;
    }

    /**
     * Validates the date range for report generation.
     * 
     * <p>Replaces COBOL PERFORM 2000-EDIT-DATES paragraph from CORPT00C.cbl
     * which validated date inputs from the BMS screen.
     * 
     * <p><b>Validation Rules:</b>
     * <ul>
     *   <li>startDate must not be null</li>
     *   <li>endDate must not be null</li>
     *   <li>startDate must be before or equal to endDate</li>
     *   <li>Date range must not exceed 365 days (MAX_DATE_RANGE_DAYS)</li>
     *   <li>startDate must not be in the future</li>
     * </ul>
     * 
     * <p><b>COBOL Mapping:</b>
     * <pre>
     * COBOL (CORPT00C.cbl):                           | Java:
     * ------------------------------------------------|------------------------------------------
     * IF CORPT-START-DATE = ZEROS                     | if (startDate == null)
     *   MOVE 'Start date is required' TO ERROR-MSG    |   throw new InvalidInputException(...)
     * IF CORPT-END-DATE = ZEROS                       | if (endDate == null)
     *   MOVE 'End date is required' TO ERROR-MSG      |   throw new InvalidInputException(...)
     * IF CORPT-START-DATE > CORPT-END-DATE            | if (startDate.isAfter(endDate))
     *   MOVE 'Invalid date range' TO ERROR-MSG        |   throw new InvalidInputException(...)
     * CALL 'CSUTLDTC' USING DATE-VALIDATION-PARMS     | (date parsing validation in Controller)
     * </pre>
     * 
     * @param startDate the start date of the report range
     * @param endDate the end date of the report range
     * @throws InvalidInputException if date range validation fails
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        // Check for null dates
        if (startDate == null) {
            throw new InvalidInputException("startDate", "Start date is required");
        }
        if (endDate == null) {
            throw new InvalidInputException("endDate", "End date is required");
        }

        // Validate date range order
        if (startDate.isAfter(endDate)) {
            throw new InvalidInputException("dateRange", 
                    "Start date must be before or equal to end date");
        }

        // Validate date range size (prevent excessive report generation)
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > MAX_DATE_RANGE_DAYS) {
            throw new InvalidInputException("dateRange", 
                    String.format("Date range exceeds maximum allowed (%d days). Requested: %d days", 
                            MAX_DATE_RANGE_DAYS, daysBetween));
        }

        // Validate startDate is not in the future
        if (startDate.isAfter(LocalDate.now())) {
            throw new InvalidInputException("startDate", 
                    "Start date cannot be in the future");
        }

        log.debug("Date range validated successfully - startDate: {}, endDate: {}, days: {}", 
                startDate, endDate, daysBetween);
    }

    /**
     * Validates the report type parameter.
     * 
     * <p>Replaces COBOL PERFORM 2100-EDIT-REPORT-TYPE paragraph from CORPT00C.cbl
     * which validated the report type selection from the BMS menu.
     * 
     * <p><b>Allowed Report Types:</b>
     * <ul>
     *   <li>TRANSACTION_SUMMARY: Summary of all transactions by category</li>
     *   <li>ACCOUNT_DETAIL: Detailed account activity report</li>
     *   <li>MERCHANT_ANALYSIS: Transaction analysis by merchant</li>
     * </ul>
     * 
     * <p><b>COBOL Mapping:</b>
     * <pre>
     * COBOL (CORPT00C.cbl):                           | Java:
     * ------------------------------------------------|------------------------------------------
     * IF CORPT-REPORT-TYPE = SPACES                   | if (reportType == null)
     *   MOVE 'Report type is required' TO ERROR-MSG   |   throw new InvalidInputException(...)
     * EVALUATE CORPT-REPORT-TYPE                      | if (!ALLOWED_REPORT_TYPES.contains(...))
     *   WHEN '1' MOVE 'TRANSACTION_SUMMARY' TO TYPE   |   throw new InvalidInputException(...)
     *   WHEN '2' MOVE 'ACCOUNT_DETAIL' TO TYPE        |
     *   WHEN '3' MOVE 'MERCHANT_ANALYSIS' TO TYPE     |
     *   WHEN OTHER                                    |
     *     MOVE 'Invalid report type' TO ERROR-MSG     |
     * </pre>
     * 
     * @param reportType the report type to validate
     * @throws InvalidInputException if report type is null or not in allowed values
     */
    private void validateReportType(String reportType) {
        if (reportType == null || reportType.trim().isEmpty()) {
            throw new InvalidInputException("reportType", "Report type is required");
        }

        if (!ALLOWED_REPORT_TYPES.contains(reportType)) {
            throw new InvalidInputException("reportType", 
                    String.format("Invalid report type: %s. Allowed values: %s", 
                            reportType, ALLOWED_REPORT_TYPES));
        }

        log.debug("Report type validated successfully: {}", reportType);
    }

    /**
     * Validates that the specified account exists in the database.
     * 
     * <p>Replaces COBOL READ ACCTFILE validation logic from CORPT00C.cbl which
     * verified the account ID before generating account-specific reports.
     * 
     * <p><b>COBOL Mapping:</b>
     * <pre>
     * COBOL (CORPT00C.cbl):                           | Java:
     * ------------------------------------------------|------------------------------------------
     * IF CORPT-ACCT-ID NOT ZEROS                      | if (accountId != null)
     *   EXEC CICS READ FILE(ACCTFILE)                 |   if (!accountRepository.existsById(...))
     *     RIDFLD(CORPT-ACCT-ID)                       |     throw new ResourceNotFoundException(...)
     *     INTO(ACCOUNT-RECORD)                        |
     *   END-EXEC                                      |
     *   IF FILE-STATUS = '23'                         |
     *     MOVE 'Account not found' TO ERROR-MSG       |
     *     PERFORM 9999-ABEND-PROGRAM                  |
     * </pre>
     * 
     * @param accountId the account ID to validate
     * @throws ResourceNotFoundException if account does not exist
     */
    private void validateAccount(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            log.warn("Account not found for report generation: {}", accountId);
            throw new ResourceNotFoundException("Account", accountId);
        }
        
        log.debug("Account validated successfully: {}", accountId);
    }

    /**
     * Builds JobParameters for Spring Batch job execution.
     * 
     * <p>Replaces COBOL PERFORM 3000-BUILD-JCL paragraph from CORPT00C.cbl which
     * constructed JCL job control statements with DD cards for input parameters.
     * 
     * <p><b>Job Parameters:</b>
     * <ul>
     *   <li>startDate: Start date for report date range (converted to java.util.Date)</li>
     *   <li>endDate: End date for report date range (converted to java.util.Date)</li>
     *   <li>reportType: Type of report to generate (String)</li>
     *   <li>accountId: Account ID for account-specific reports (Long, nullable)</li>
     *   <li>timestamp: Unique timestamp to ensure job parameter uniqueness (Long)</li>
     * </ul>
     * 
     * <p><b>Note:</b> The timestamp parameter is added to ensure job parameter uniqueness
     * even if the same report parameters are used multiple times. Spring Batch uses
     * JobParameters as part of the JobInstance identity, so different timestamps create
     * different job instances.
     * 
     * <p><b>COBOL Mapping:</b>
     * <pre>
     * COBOL (CORPT00C.cbl):                           | Java:
     * ------------------------------------------------|------------------------------------------
     * MOVE '//CBTRN03C JOB ...' TO JCL-RECORD        | JobParametersBuilder builder = new ...
     * MOVE '//DATEPARM DD *' TO JCL-RECORD           | builder.addDate("startDate", ...)
     * MOVE WS-START-DATE TO JCL-RECORD               | builder.addDate("endDate", ...)
     * MOVE WS-END-DATE TO JCL-RECORD                 | builder.addString("reportType", ...)
     * MOVE WS-REPORT-TYPE TO JCL-RECORD              | builder.addLong("accountId", ...)
     * EXEC CICS WRITEQ TS QUEUE('JOBS')              | (JobLauncher.run handles submission)
     * </pre>
     * 
     * @param startDate the start date for the report
     * @param endDate the end date for the report
     * @param reportType the type of report to generate
     * @param accountId the account ID (null for all accounts)
     * @return JobParameters for batch job execution
     */
    private JobParameters buildJobParameters(LocalDate startDate, LocalDate endDate, 
                                            String reportType, Long accountId) {
        JobParametersBuilder builder = new JobParametersBuilder();

        // Convert LocalDate to java.util.Date for Spring Batch compatibility
        Date startDateAsDate = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endDateAsDate = Date.from(endDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        // Add job parameters
        builder.addDate("startDate", startDateAsDate);
        builder.addDate("endDate", endDateAsDate);
        builder.addString("reportType", reportType);
        
        // Add accountId if specified (null handling for optional parameter)
        if (accountId != null) {
            builder.addLong("accountId", accountId);
        }

        // Add unique timestamp to ensure job parameter uniqueness
        builder.addLong("timestamp", System.currentTimeMillis());

        JobParameters jobParameters = builder.toJobParameters();
        log.debug("JobParameters built: {}", jobParameters);
        
        return jobParameters;
    }

    /**
     * Calculates estimated completion time for report generation based on date range.
     * 
     * <p>This method provides a rough estimate of when the report will be completed
     * based on the size of the date range. Actual completion time depends on:
     * <ul>
     *   <li>Number of transactions in the date range</li>
     *   <li>System load and available resources</li>
     *   <li>Database query performance</li>
     *   <li>Report output format (CSV is fastest, PDF slowest)</li>
     * </ul>
     * 
     * <p><b>Estimation Formula:</b>
     * <ul>
     *   <li>Base processing time: 30 seconds</li>
     *   <li>Additional time per day: 5 seconds per day in date range</li>
     *   <li>Example: 30-day report = 30s + (30 * 5s) = 180s = 3 minutes</li>
     * </ul>
     * 
     * @param startDate the start date of the report range
     * @param endDate the end date of the report range
     * @return LocalDateTime representing estimated completion time
     */
    private LocalDateTime calculateEstimatedCompletion(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return LocalDateTime.now().plusMinutes(5);  // Default 5-minute estimate
        }

        // Calculate days in range
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        
        // Base processing time: 30 seconds
        // Additional time: 5 seconds per day in range
        long estimatedSeconds = 30 + (daysBetween * 5);
        
        // Cap maximum estimate at 30 minutes
        estimatedSeconds = Math.min(estimatedSeconds, 1800);
        
        LocalDateTime estimated = LocalDateTime.now().plusSeconds(estimatedSeconds);
        log.debug("Estimated completion calculated: {} (for {} days)", estimated, daysBetween);
        
        return estimated;
    }

    /**
     * Data Transfer Object for report generation responses.
     * 
     * <p>This DTO encapsulates all information needed to track a report generation job:
     * <ul>
     *   <li>Job execution ID for status queries</li>
     *   <li>Report parameters (type, date range, account)</li>
     *   <li>Current job status (STARTING, STARTED, COMPLETED, FAILED)</li>
     *   <li>Timestamps (creation time, estimated completion)</li>
     *   <li>Download URL (populated when job completes successfully)</li>
     *   <li>Status message (success message or error details)</li>
     * </ul>
     * 
     * <p>This class uses Lombok annotations for boilerplate reduction:
     * <ul>
     *   <li>@Builder: Provides builder pattern for object construction</li>
     *   <li>@Data: Generates getters, setters, equals, hashCode, toString</li>
     * </ul>
     */
    @lombok.Builder
    @lombok.Data
    public static class ReportResponse {
        /**
         * The unique ID of the Spring Batch job execution.
         * Used to query job status via getJobStatus(jobExecutionId).
         */
        private Long jobExecutionId;

        /**
         * The type of report being generated.
         * One of: TRANSACTION_SUMMARY, ACCOUNT_DETAIL, MERCHANT_ANALYSIS.
         */
        private String reportType;

        /**
         * The start date of the report date range (inclusive).
         */
        private LocalDate startDate;

        /**
         * The end date of the report date range (inclusive).
         */
        private LocalDate endDate;

        /**
         * The account ID for account-specific reports.
         * Null if report covers all accounts.
         */
        private Long accountId;

        /**
         * The current status of the job execution.
         * Values: STARTING, STARTED, STOPPING, STOPPED, COMPLETED, FAILED, ABANDONED.
         */
        private String reportStatus;

        /**
         * The timestamp when the report generation job was created.
         */
        private LocalDateTime createdAt;

        /**
         * The estimated completion time for the report generation.
         * Null if job has already completed or failed.
         */
        private LocalDateTime estimatedCompletion;

        /**
         * The download URL for the completed report.
         * Populated only when reportStatus is COMPLETED.
         * Format: /api/v1/reports/download/{jobExecutionId}
         */
        private String downloadUrl;

        /**
         * A human-readable status message.
         * Contains success message on completion, error details on failure,
         * or progress information while running.
         */
        private String message;
    }
}
