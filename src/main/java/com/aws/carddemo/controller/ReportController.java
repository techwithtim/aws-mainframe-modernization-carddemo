/*
 * ReportController.java
 * 
 * REST controller managing transaction report generation endpoints, supporting monthly,
 * yearly, and custom date range reports with asynchronous Spring Batch job execution.
 * 
 * Migrated from: 
 * - app/bms/CORPT00.bms (BMS screen definition with report type selection)
 * - app/cbl/CORPT00C.cbl (CICS program for report parameter collection and JCL submission)
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
 * <p><b>Migration Context:</b> This REST controller replaces the COBOL CORPT00C.cbl CICS
 * program which presented a 3270 terminal screen (CORPT00.bms) with radio button options
 * for report type selection (MONTHLY, YEARLY, CUSTOM) and date input fields. The COBOL
 * program performed the following operations:
 * 
 * <ul>
 *   <li>EXEC CICS RECEIVE MAP(CORPT0A) → @RequestParam binding in Spring MVC</li>
 *   <li>Date field validation (SDTMM, SDTDD, SDTYYYY) → @DateTimeFormat with LocalDate</li>
 *   <li>Report type selection (radio buttons) → reportType query parameter</li>
 *   <li>EXEC CICS WRITEQ TS QUEUE('JOBS') → ReportService.generateReport() Spring Batch trigger</li>
 *   <li>JCL job submission → JobLauncher.run() asynchronous batch execution</li>
 *   <li>Screen navigation (PF3=Back) → HTTP response with status codes</li>
 * </ul>
 * 
 * <p><b>API Design:</b> This controller provides RESTful JSON API endpoints replacing
 * the 3270 terminal user interface:
 * <ul>
 *   <li>GET /api/v1/reports - Generate report with query parameters</li>
 *   <li>POST /api/v1/reports - Alternative endpoint for report generation</li>
 *   <li>GET /api/v1/reports/{reportId} - Check report generation status</li>
 * </ul>
 * 
 * <p><b>Business Logic Preservation:</b> The controller maintains functional equivalence with
 * COBOL CORPT00C.cbl:
 * <ul>
 *   <li>MONTHLY report: First day to last day of current month</li>
 *   <li>YEARLY report: January 1 to December 31 of current year</li>
 *   <li>CUSTOM report: User-specified start and end dates with validation</li>
 *   <li>Date validation: Month 1-12, day 1-31, year numeric, startDate <= endDate</li>
 *   <li>Asynchronous execution: Report generation runs in background (Spring Batch)</li>
 *   <li>Status tracking: Poll /api/v1/reports/{reportId} for job status</li>
 * </ul>
 * 
 * <p><b>Security:</b> All endpoints require ADMIN role via @PreAuthorize annotation
 * for PCI-DSS access control compliance per section 0.8.1 requirements.
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 1.0.0
 */
package com.aws.carddemo.controller;

import com.aws.carddemo.service.ReportService;
import com.aws.carddemo.service.ReportService.ReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * REST controller for report generation operations.
 * 
 * <p>This controller exposes RESTful endpoints for:
 * <ul>
 *   <li>Triggering transaction report generation with date range parameters</li>
 *   <li>Supporting predefined report types (MONTHLY, YEARLY, CUSTOM)</li>
 *   <li>Checking report generation status via job execution ID</li>
 *   <li>Retrieving report download URLs when generation completes</li>
 * </ul>
 * 
 * <p>All endpoints require ADMIN role authentication per PCI-DSS security requirements.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    /**
     * Report service for business logic operations.
     * Handles report generation, validation, and job status tracking.
     */
    private final ReportService reportService;

    /**
     * Generates a transaction report based on report type and date range parameters.
     * 
     * <p>This endpoint replaces the COBOL CORPT00C.cbl main processing logic which
     * collected report parameters from the BMS screen CORPT00.bms and submitted a
     * batch job to the internal reader queue. The modern implementation:
     * <ol>
     *   <li>Accepts report type via query parameter (MONTHLY, YEARLY, CUSTOM)</li>
     *   <li>Calculates date ranges for MONTHLY/YEARLY or uses provided dates for CUSTOM</li>
     *   <li>Validates all parameters via ReportService</li>
     *   <li>Triggers asynchronous Spring Batch job execution</li>
     *   <li>Returns 202 ACCEPTED with job execution ID for status tracking</li>
     * </ol>
     * 
     * <p><b>COBOL Mapping:</b>
     * <pre>
     * COBOL (CORPT00C.cbl):                           | Java (ReportController):
     * ------------------------------------------------|------------------------------------------
     * EXEC CICS RECEIVE MAP(CORPT0A)                  | @RequestParam binding by Spring MVC
     * EVALUATE TRUE                                   | switch (reportType.toUpperCase())
     *   WHEN MONTHLYI NOT = SPACES                    |   case "MONTHLY":
     *     MOVE FUNCTION CURRENT-DATE TO WS-CURDATE    |     LocalDate now = LocalDate.now()
     *     MOVE WS-CURDATE-YEAR TO WS-START-DATE-YYYY  |     startDate = now.withDayOfMonth(1)
     *     MOVE WS-CURDATE-MONTH TO WS-START-DATE-MM   |     endDate = now.withDayOfMonth(...)
     *     MOVE '01' TO WS-START-DATE-DD               |
     *     COMPUTE END-DATE = START-DATE + 1 MONTH - 1 |
     *   WHEN YEARLYI NOT = SPACES                     |   case "YEARLY":
     *     MOVE WS-CURDATE-YEAR TO DATES               |     startDate = LocalDate.of(year, 1, 1)
     *     MOVE '01' TO WS-START-DATE-MM/DD            |     endDate = LocalDate.of(year, 12, 31)
     *     MOVE '12' TO WS-END-DATE-MM                 |
     *     MOVE '31' TO WS-END-DATE-DD                 |
     *   WHEN CUSTOMI NOT = SPACES                     |   case "CUSTOM":
     *     MOVE SDTYYYYI TO WS-START-DATE-YYYY         |     startDate = requestParam startDate
     *     MOVE SDTMMI TO WS-START-DATE-MM             |     endDate = requestParam endDate
     *     MOVE SDTDDI TO WS-START-DATE-DD             |
     *     CALL 'CSUTLDTC' USING DATE-VALIDATION       |     (validation in ReportService)
     * PERFORM SUBMIT-JOB-TO-INTRDR                    | reportService.generateReport(...)
     * EXEC CICS WRITEQ TS QUEUE('JOBS')               | (JobLauncher.run in service layer)
     * MOVE 'Report submitted' TO ERRMSG               | return ResponseEntity.accepted()
     * </pre>
     * 
     * <p><b>Report Type Options:</b>
     * <ul>
     *   <li><b>MONTHLY</b>: Generates report for current calendar month
     *       <ul>
     *         <li>Start Date: First day of current month (YYYY-MM-01)</li>
     *         <li>End Date: Last day of current month (YYYY-MM-28/29/30/31)</li>
     *         <li>Example: 2024-10-01 to 2024-10-31 for October 2024</li>
     *       </ul>
     *   </li>
     *   <li><b>YEARLY</b>: Generates report for current calendar year
     *       <ul>
     *         <li>Start Date: January 1 of current year (YYYY-01-01)</li>
     *         <li>End Date: December 31 of current year (YYYY-12-31)</li>
     *         <li>Example: 2024-01-01 to 2024-12-31 for year 2024</li>
     *       </ul>
     *   </li>
     *   <li><b>CUSTOM</b>: Generates report for user-specified date range
     *       <ul>
     *         <li>Start Date: Provided via startDate query parameter</li>
     *         <li>End Date: Provided via endDate query parameter</li>
     *         <li>Validation: startDate <= endDate, range <= 365 days</li>
     *         <li>Example: startDate=2024-01-15&endDate=2024-02-14</li>
     *       </ul>
     *   </li>
     * </ul>
     * 
     * <p><b>Query Parameters:</b>
     * <ul>
     *   <li><b>reportType</b> (required): MONTHLY, YEARLY, or CUSTOM (case-insensitive)</li>
     *   <li><b>startDate</b> (required for CUSTOM, ignored for MONTHLY/YEARLY): 
     *       Start date in format YYYY-MM-DD</li>
     *   <li><b>endDate</b> (required for CUSTOM, ignored for MONTHLY/YEARLY): 
     *       End date in format YYYY-MM-DD</li>
     *   <li><b>accountId</b> (optional): Account ID for account-specific report (null = all accounts)</li>
     * </ul>
     * 
     * <p><b>HTTP Status Codes:</b>
     * <ul>
     *   <li><b>202 ACCEPTED</b>: Report generation job submitted successfully</li>
     *   <li><b>400 BAD REQUEST</b>: Invalid reportType, missing required dates for CUSTOM,
     *       invalid date format, or startDate > endDate</li>
     *   <li><b>404 NOT FOUND</b>: Account not found (if accountId specified)</li>
     *   <li><b>500 INTERNAL SERVER ERROR</b>: Failed to launch batch job</li>
     * </ul>
     * 
     * <p><b>Example Requests:</b>
     * <pre>
     * // Generate monthly report for current month
     * GET /api/v1/reports?reportType=MONTHLY
     * 
     * // Generate yearly report for current year
     * GET /api/v1/reports?reportType=YEARLY
     * 
     * // Generate custom date range report
     * GET /api/v1/reports?reportType=CUSTOM&startDate=2024-01-01&endDate=2024-03-31
     * 
     * // Generate account-specific report
     * GET /api/v1/reports?reportType=MONTHLY&accountId=12345
     * </pre>
     * 
     * <p><b>Example Response:</b>
     * <pre>
     * {
     *   "jobExecutionId": 12345,
     *   "reportType": "TRANSACTION_SUMMARY",
     *   "startDate": "2024-10-01",
     *   "endDate": "2024-10-31",
     *   "accountId": null,
     *   "reportStatus": "STARTING",
     *   "createdAt": "2024-10-09T14:30:00",
     *   "estimatedCompletion": "2024-10-09T14:35:00",
     *   "downloadUrl": null,
     *   "message": "Report generation job submitted successfully"
     * }
     * </pre>
     * 
     * @param reportType the type of report to generate (MONTHLY, YEARLY, or CUSTOM)
     * @param startDate the start date for CUSTOM reports (format: yyyy-MM-dd, optional for MONTHLY/YEARLY)
     * @param endDate the end date for CUSTOM reports (format: yyyy-MM-dd, optional for MONTHLY/YEARLY)
     * @param accountId the account ID for account-specific reports (optional, null = all accounts)
     * @return ResponseEntity with ReportResponse containing job execution ID and status (HTTP 202 ACCEPTED)
     * @throws com.aws.carddemo.exception.InvalidInputException if reportType is invalid or date validation fails
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if accountId specified but account not found
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportResponse> generateReport(
            @RequestParam(name = "reportType", defaultValue = "MONTHLY") String reportType,
            @RequestParam(name = "startDate", required = false) 
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(name = "endDate", required = false) 
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(name = "accountId", required = false) Long accountId) {
        
        log.info("Report generation request received - reportType: {}, startDate: {}, endDate: {}, accountId: {}",
                reportType, startDate, endDate, accountId);

        // Calculate date ranges based on report type (preserving COBOL EVALUATE logic)
        LocalDate calculatedStartDate;
        LocalDate calculatedEndDate;
        String normalizedReportType = reportType.toUpperCase();

        switch (normalizedReportType) {
            case "MONTHLY":
                // COBOL: MOVE FUNCTION CURRENT-DATE TO WS-CURDATE
                //        MOVE WS-CURDATE-YEAR TO WS-START-DATE-YYYY
                //        MOVE WS-CURDATE-MONTH TO WS-START-DATE-MM
                //        MOVE '01' TO WS-START-DATE-DD
                //        COMPUTE END-DATE = FUNCTION DATE-OF-INTEGER(FUNCTION INTEGER-OF-DATE(START-DATE + 1 MONTH) - 1)
                LocalDate now = LocalDate.now();
                calculatedStartDate = now.withDayOfMonth(1);  // First day of current month
                YearMonth yearMonth = YearMonth.from(now);
                calculatedEndDate = yearMonth.atEndOfMonth();  // Last day of current month
                log.debug("MONTHLY report dates calculated: {} to {}", calculatedStartDate, calculatedEndDate);
                break;

            case "YEARLY":
                // COBOL: MOVE FUNCTION CURRENT-DATE TO WS-CURDATE
                //        MOVE WS-CURDATE-YEAR TO WS-START-DATE-YYYY / WS-END-DATE-YYYY
                //        MOVE '01' TO WS-START-DATE-MM / WS-START-DATE-DD
                //        MOVE '12' TO WS-END-DATE-MM
                //        MOVE '31' TO WS-END-DATE-DD
                int currentYear = LocalDate.now().getYear();
                calculatedStartDate = LocalDate.of(currentYear, 1, 1);   // January 1 of current year
                calculatedEndDate = LocalDate.of(currentYear, 12, 31);   // December 31 of current year
                log.debug("YEARLY report dates calculated: {} to {}", calculatedStartDate, calculatedEndDate);
                break;

            case "CUSTOM":
                // COBOL: MOVE SDTYYYYI OF CORPT0AI TO WS-START-DATE-YYYY
                //        MOVE SDTMMI OF CORPT0AI TO WS-START-DATE-MM
                //        MOVE SDTDDI OF CORPT0AI TO WS-START-DATE-DD
                //        MOVE EDTYYYYI OF CORPT0AI TO WS-END-DATE-YYYY
                //        MOVE EDTMMI OF CORPT0AI TO WS-END-DATE-MM
                //        MOVE EDTDDI OF CORPT0AI TO WS-END-DATE-DD
                //        CALL 'CSUTLDTC' USING CSUTLDTC-DATE, CSUTLDTC-DATE-FORMAT, CSUTLDTC-RESULT
                if (startDate == null || endDate == null) {
                    log.warn("CUSTOM report requires both startDate and endDate");
                    throw new IllegalArgumentException(
                            "For CUSTOM report type, both startDate and endDate query parameters are required. " +
                            "Format: yyyy-MM-dd (e.g., startDate=2024-01-01&endDate=2024-01-31)");
                }
                calculatedStartDate = startDate;
                calculatedEndDate = endDate;
                log.debug("CUSTOM report dates provided: {} to {}", calculatedStartDate, calculatedEndDate);
                break;

            default:
                // COBOL: WHEN OTHER
                //          MOVE 'Select a report type to print report...' TO WS-MESSAGE
                //          MOVE 'Y' TO WS-ERR-FLG
                log.warn("Invalid report type requested: {}", reportType);
                throw new IllegalArgumentException(
                        "Invalid report type: " + reportType + ". " +
                        "Allowed values: MONTHLY, YEARLY, CUSTOM (case-insensitive)");
        }

        // Delegate to service layer for validation and batch job submission
        // Replaces COBOL: PERFORM SUBMIT-JOB-TO-INTRDR
        //                 EXEC CICS WRITEQ TS QUEUE('JOBS') FROM(JCL-RECORD)
        ReportResponse response = reportService.generateReport(
                calculatedStartDate, 
                calculatedEndDate, 
                "TRANSACTION_SUMMARY",  // Default report type for Spring Batch job
                accountId
        );

        log.info("Report generation job submitted successfully - jobExecutionId: {}, status: {}",
                response.getJobExecutionId(), response.getReportStatus());

        // Return HTTP 202 ACCEPTED to indicate async processing
        // Replaces COBOL: MOVE 'Monthly report submitted for printing ...' TO WS-MESSAGE
        //                 PERFORM SEND-TRNRPT-SCREEN
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Alternative endpoint for report generation using POST method.
     * 
     * <p>This endpoint provides the same functionality as the GET endpoint but uses
     * POST HTTP method for clients that prefer explicit action semantics for job submission.
     * 
     * <p>Functionally equivalent to {@link #generateReport(String, LocalDate, LocalDate, Long)},
     * but follows REST POST convention for resource creation (creating a new report job).
     * 
     * <p><b>Example Request:</b>
     * <pre>
     * POST /api/v1/reports?reportType=MONTHLY
     * </pre>
     * 
     * @param reportType the type of report to generate (MONTHLY, YEARLY, or CUSTOM)
     * @param startDate the start date for CUSTOM reports (format: yyyy-MM-dd)
     * @param endDate the end date for CUSTOM reports (format: yyyy-MM-dd)
     * @param accountId the account ID for account-specific reports (optional)
     * @return ResponseEntity with ReportResponse containing job execution ID and status (HTTP 202 ACCEPTED)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportResponse> generateReportPost(
            @RequestParam(name = "reportType", defaultValue = "MONTHLY") String reportType,
            @RequestParam(name = "startDate", required = false) 
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(name = "endDate", required = false) 
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(name = "accountId", required = false) Long accountId) {
        
        log.debug("POST report generation request delegating to GET handler");
        
        // Delegate to GET handler to avoid code duplication (DRY principle)
        return generateReport(reportType, startDate, endDate, accountId);
    }

    /**
     * Retrieves the status of a report generation job by job execution ID.
     * 
     * <p>This endpoint replaces the COBOL pattern of checking JES job status via
     * operator console commands. Clients poll this endpoint to track report generation
     * progress and retrieve the download URL when the job completes.
     * 
     * <p><b>COBOL Context:</b> In the legacy COBOL system, after submitting a job to
     * the internal reader via EXEC CICS WRITEQ TS QUEUE('JOBS'), operators would check
     * job status using JES commands or SDSF panels. This endpoint provides programmatic
     * access to job status via Spring Batch JobExecution metadata.
     * 
     * <p><b>Job Status Values:</b>
     * <ul>
     *   <li><b>STARTING</b>: Job is being initialized by Spring Batch framework</li>
     *   <li><b>STARTED</b>: Job is actively processing transactions (running reader/processor/writer)</li>
     *   <li><b>STOPPING</b>: Job is in the process of stopping (manual intervention or error)</li>
     *   <li><b>STOPPED</b>: Job was stopped before completion</li>
     *   <li><b>COMPLETED</b>: Job finished successfully, downloadUrl is populated</li>
     *   <li><b>FAILED</b>: Job terminated with errors, message contains error details</li>
     *   <li><b>ABANDONED</b>: Job was abandoned and will not be restarted</li>
     * </ul>
     * 
     * <p><b>HTTP Status Codes:</b>
     * <ul>
     *   <li><b>200 OK</b>: Job status retrieved successfully</li>
     *   <li><b>404 NOT FOUND</b>: Job execution ID does not exist</li>
     * </ul>
     * 
     * <p><b>Usage Pattern:</b>
     * <pre>
     * // Step 1: Submit report generation request
     * POST /api/v1/reports?reportType=MONTHLY
     * Response: { "jobExecutionId": 12345, "reportStatus": "STARTING", ... }
     * 
     * // Step 2: Poll for job status (every 5-10 seconds)
     * GET /api/v1/reports/12345
     * Response: { "jobExecutionId": 12345, "reportStatus": "STARTED", ... }
     * 
     * // Step 3: Job completes, download URL is available
     * GET /api/v1/reports/12345
     * Response: { 
     *   "jobExecutionId": 12345, 
     *   "reportStatus": "COMPLETED",
     *   "downloadUrl": "/api/v1/reports/download/12345",
     *   ...
     * }
     * 
     * // Step 4: Download report file
     * GET /api/v1/reports/download/12345
     * Response: (report file content with Content-Disposition header)
     * </pre>
     * 
     * <p><b>Example Response (Job Running):</b>
     * <pre>
     * {
     *   "jobExecutionId": 12345,
     *   "reportType": "TRANSACTION_SUMMARY",
     *   "startDate": "2024-10-01",
     *   "endDate": "2024-10-31",
     *   "accountId": null,
     *   "reportStatus": "STARTED",
     *   "createdAt": "2024-10-09T14:30:00",
     *   "estimatedCompletion": "2024-10-09T14:35:00",
     *   "downloadUrl": null,
     *   "message": "Report generation in progress"
     * }
     * </pre>
     * 
     * <p><b>Example Response (Job Completed):</b>
     * <pre>
     * {
     *   "jobExecutionId": 12345,
     *   "reportType": "TRANSACTION_SUMMARY",
     *   "startDate": "2024-10-01",
     *   "endDate": "2024-10-31",
     *   "accountId": null,
     *   "reportStatus": "COMPLETED",
     *   "createdAt": "2024-10-09T14:30:00",
     *   "estimatedCompletion": null,
     *   "downloadUrl": "/api/v1/reports/download/12345",
     *   "message": "Report generation completed successfully"
     * }
     * </pre>
     * 
     * @param reportId the job execution ID returned from generateReport() endpoint
     * @return ResponseEntity with ReportResponse containing current job status and download URL if completed
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if reportId (jobExecutionId) not found
     */
    @GetMapping("/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportResponse> getReportStatus(@PathVariable("reportId") Long reportId) {
        log.info("Report status check requested for reportId (jobExecutionId): {}", reportId);

        // Delegate to service layer for job status retrieval
        // Uses Spring Batch JobExplorer to query JobExecution metadata
        ReportResponse response = reportService.getJobStatus(reportId);

        log.debug("Report status retrieved - reportId: {}, status: {}, downloadUrl: {}",
                reportId, response.getReportStatus(), response.getDownloadUrl());

        // Return HTTP 200 OK with job status details
        return ResponseEntity.ok(response);
    }
}
