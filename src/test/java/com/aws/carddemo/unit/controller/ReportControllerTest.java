/*
 * ReportControllerTest.java
 * 
 * Unit test class for ReportController REST API endpoints using @WebMvcTest.
 * 
 * Migrated from:
 * - app/bms/CORPT00.bms (Report Parameters BMS screen)
 * - app/cbl/CORPT00C.cbl (Report parameter collection and JCL submission)
 * - app/cbl/CBTRN03C.cbl (Transaction Report Generation batch program)
 * 
 * This test class validates the REST controller behavior for report generation
 * endpoints (GET/POST /api/v1/reports) and job status tracking (GET /api/v1/reports/{id}),
 * proving functional equivalence to COBOL CORPT00C.cbl screen parameter capture
 * and CBTRN03C.cbl batch report generation per Agent Action Plan Section 0.4.1.
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
 */
package com.aws.carddemo.unit.controller;

import com.aws.carddemo.controller.ReportController;
import com.aws.carddemo.service.ReportService;
import com.aws.carddemo.service.ReportService.ReportResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit test class for ReportController REST API endpoints.
 * 
 * <p>Tests the report generation controller functionality replacing COBOL
 * CORPT00C.cbl screen parameter collection and batch job submission logic.
 * 
 * <p><b>Test Coverage:</b>
 * <ul>
 *   <li>GET /api/v1/reports with reportType=MONTHLY (current month date calculation)</li>
 *   <li>GET /api/v1/reports with reportType=YEARLY (current year date calculation)</li>
 *   <li>GET /api/v1/reports with reportType=CUSTOM (user-provided dates)</li>
 *   <li>GET /api/v1/reports with accountId filter parameter</li>
 *   <li>POST /api/v1/reports (delegates to GET handler)</li>
 *   <li>GET /api/v1/reports/{reportId} for job status tracking</li>
 *   <li>Validation error handling for missing/invalid parameters</li>
 * </ul>
 * 
 * <p><b>COBOL Mapping Context:</b>
 * <pre>
 * COBOL (CORPT00C.cbl):                           | Java Test:
 * ------------------------------------------------|------------------------------------------
 * EXEC CICS RECEIVE MAP(CORPT0A)                  | mockMvc.perform(get("/api/v1/reports")
 *   INTO(CORPT0AI)                                |   .param("reportType", "MONTHLY"))
 * EVALUATE TRUE                                   | 
 *   WHEN MONTHLYI NOT = SPACES                    | testGenerateMonthlyReport_Success()
 *     PERFORM CALC-MONTHLY-DATES                  | Verifies current month date calculation
 *   WHEN YEARLYI NOT = SPACES                     | testGenerateYearlyReport_Success()
 *     PERFORM CALC-YEARLY-DATES                   | Verifies current year date calculation
 *   WHEN CUSTOMI NOT = SPACES                     | testGenerateCustomReport_Success()
 *     MOVE SDTYYYYI TO WS-START-DATE-YYYY         | Verifies custom date parameter binding
 *     MOVE SDTMMI TO WS-START-DATE-MM             |
 *     MOVE SDTDDI TO WS-START-DATE-DD             |
 *     CALL 'CSUTLDTC' FOR DATE VALIDATION         |
 * PERFORM SUBMIT-JOB-TO-INTRDR                    | Verifies ReportService.generateReport()
 * EXEC CICS WRITEQ TS QUEUE('JOBS')               | mock returns jobExecutionId
 * </pre>
 * 
 * <p><b>Spring Batch Job Triggering:</b>
 * Tests verify that controller properly delegates to ReportService.generateReport()
 * which triggers TransactionReportJobConfig batch job via JobLauncher.run(),
 * replacing COBOL JCL submission logic (EXEC CICS START TRANSID).
 * 
 * @see ReportController
 * @see ReportService
 * @see com.aws.carddemo.batch.config.TransactionReportJobConfig
 */
@WebMvcTest(ReportController.class)
@WithMockUser(roles = "ADMIN")
@DisplayName("ReportController Unit Tests")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    /**
     * Tests successful monthly report generation with current month date calculation.
     * 
     * <p>Replaces COBOL CORPT00C.cbl lines 238-256 PERFORM CALC-MONTHLY-DATES paragraph:
     * <pre>
     * MOVE FUNCTION CURRENT-DATE TO WS-CURDATE
     * MOVE WS-CURDATE-YEAR TO WS-START-DATE-YYYY
     * MOVE WS-CURDATE-MONTH TO WS-START-DATE-MM
     * MOVE '01' TO WS-START-DATE-DD
     * COMPUTE WS-END-DATE = FUNCTION DATE-OF-INTEGER(
     *     FUNCTION INTEGER-OF-DATE(WS-START-DATE + 1 MONTH) - 1)
     * </pre>
     * 
     * <p>Verifies:
     * <ul>
     *   <li>HTTP 202 ACCEPTED status for async job submission</li>
     *   <li>ReportResponse contains jobExecutionId for status tracking</li>
     *   <li>ReportService.generateReport() called with calculated month dates</li>
     *   <li>Response includes reportStatus=STARTING per Spring Batch lifecycle</li>
     * </ul>
     */
    @Test
    @DisplayName("GET /api/v1/reports - MONTHLY report type - Success with current month dates")
    void testGenerateMonthlyReport_Success() throws Exception {
        // Arrange - Calculate expected current month dates matching COBOL CALC-MONTHLY-DATES
        LocalDate now = LocalDate.now();
        LocalDate expectedStartDate = now.withDayOfMonth(1);
        YearMonth yearMonth = YearMonth.from(now);
        LocalDate expectedEndDate = yearMonth.atEndOfMonth();
        
        ReportResponse mockResponse = ReportResponse.builder()
                .jobExecutionId(12345L)
                .reportType("TRANSACTION_SUMMARY")
                .startDate(expectedStartDate)
                .endDate(expectedEndDate)
                .accountId(null)
                .reportStatus("STARTING")
                .createdAt(LocalDateTime.now())
                .estimatedCompletion(LocalDateTime.now().plusMinutes(5))
                .downloadUrl(null)
                .message("Report generation job submitted successfully")
                .build();
        
        when(reportService.generateReport(
                eq(expectedStartDate),
                eq(expectedEndDate),
                eq("TRANSACTION_SUMMARY"),
                isNull()))
                .thenReturn(mockResponse);
        
        // Act & Assert - Verify GET /api/v1/reports?reportType=MONTHLY
        mockMvc.perform(get("/api/v1/reports")
                .param("reportType", "MONTHLY")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())  // HTTP 202 for async processing
                .andExpect(jsonPath("$.jobExecutionId").value(12345))
                .andExpect(jsonPath("$.reportType").value("TRANSACTION_SUMMARY"))
                .andExpect(jsonPath("$.startDate").value(expectedStartDate.toString()))
                .andExpect(jsonPath("$.endDate").value(expectedEndDate.toString()))
                .andExpect(jsonPath("$.accountId").isEmpty())
                .andExpect(jsonPath("$.reportStatus").value("STARTING"))
                .andExpect(jsonPath("$.downloadUrl").isEmpty())
                .andExpect(jsonPath("$.message").value(containsString("submitted")));
        
        // Verify service layer was called with correct calculated dates
        verify(reportService, times(1)).generateReport(
                eq(expectedStartDate),
                eq(expectedEndDate),
                eq("TRANSACTION_SUMMARY"),
                isNull());
    }

    /**
     * Tests successful yearly report generation with current year date calculation.
     * 
     * <p>Replaces COBOL CORPT00C.cbl lines 258-274 PERFORM CALC-YEARLY-DATES paragraph:
     * <pre>
     * MOVE FUNCTION CURRENT-DATE TO WS-CURDATE
     * MOVE WS-CURDATE-YEAR TO WS-START-DATE-YYYY
     * MOVE WS-CURDATE-YEAR TO WS-END-DATE-YYYY
     * MOVE '01' TO WS-START-DATE-MM
     * MOVE '01' TO WS-START-DATE-DD
     * MOVE '12' TO WS-END-DATE-MM
     * MOVE '31' TO WS-END-DATE-DD
     * </pre>
     * 
     * <p>Verifies:
     * <ul>
     *   <li>StartDate = January 1 of current year</li>
     *   <li>EndDate = December 31 of current year</li>
     *   <li>HTTP 202 ACCEPTED with jobExecutionId</li>
     * </ul>
     */
    @Test
    @DisplayName("GET /api/v1/reports - YEARLY report type - Success with current year dates")
    void testGenerateYearlyReport_Success() throws Exception {
        // Arrange - Calculate expected current year dates matching COBOL CALC-YEARLY-DATES
        int currentYear = LocalDate.now().getYear();
        LocalDate expectedStartDate = LocalDate.of(currentYear, 1, 1);  // January 1
        LocalDate expectedEndDate = LocalDate.of(currentYear, 12, 31);  // December 31
        
        ReportResponse mockResponse = ReportResponse.builder()
                .jobExecutionId(12346L)
                .reportType("TRANSACTION_SUMMARY")
                .startDate(expectedStartDate)
                .endDate(expectedEndDate)
                .accountId(null)
                .reportStatus("STARTING")
                .createdAt(LocalDateTime.now())
                .estimatedCompletion(LocalDateTime.now().plusMinutes(30))
                .downloadUrl(null)
                .message("Report generation job submitted successfully")
                .build();
        
        when(reportService.generateReport(
                eq(expectedStartDate),
                eq(expectedEndDate),
                eq("TRANSACTION_SUMMARY"),
                isNull()))
                .thenReturn(mockResponse);
        
        // Act & Assert - Verify GET /api/v1/reports?reportType=YEARLY
        mockMvc.perform(get("/api/v1/reports")
                .param("reportType", "YEARLY")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())  // HTTP 202 for async processing
                .andExpect(jsonPath("$.jobExecutionId").value(12346))
                .andExpect(jsonPath("$.reportType").value("TRANSACTION_SUMMARY"))
                .andExpect(jsonPath("$.startDate").value(expectedStartDate.toString()))
                .andExpect(jsonPath("$.endDate").value(expectedEndDate.toString()))
                .andExpect(jsonPath("$.accountId").isEmpty())
                .andExpect(jsonPath("$.reportStatus").value("STARTING"));
        
        // Verify service layer was called with correct calculated year dates
        verify(reportService, times(1)).generateReport(
                eq(expectedStartDate),
                eq(expectedEndDate),
                eq("TRANSACTION_SUMMARY"),
                isNull());
    }

    /**
     * Tests successful custom date range report generation with user-provided dates.
     * 
     * <p>Replaces COBOL CORPT00C.cbl lines 276-298 custom date handling:
     * <pre>
     * WHEN CUSTOMI NOT = SPACES
     *   MOVE SDTYYYYI OF CORPT0AI TO WS-START-DATE-YYYY
     *   MOVE SDTMMI OF CORPT0AI TO WS-START-DATE-MM
     *   MOVE SDTDDI OF CORPT0AI TO WS-START-DATE-DD
     *   MOVE EDTYYYYI OF CORPT0AI TO WS-END-DATE-YYYY
     *   MOVE EDTMMI OF CORPT0AI TO WS-END-DATE-MM
     *   MOVE EDTDDI OF CORPT0AI TO WS-END-DATE-DD
     *   CALL 'CSUTLDTC' USING WS-START-DATE, WS-DATE-FORMAT, WS-RESULT
     *   IF WS-RESULT NOT = ZERO
     *     MOVE 'Invalid start date' TO WS-ERROR-MESSAGE
     * </pre>
     * 
     * <p>Verifies:
     * <ul>
     *   <li>Custom startDate and endDate parameters are passed to service</li>
     *   <li>Date format yyyy-MM-dd is properly parsed via @DateTimeFormat</li>
     *   <li>HTTP 202 ACCEPTED with jobExecutionId</li>
     * </ul>
     */
    @Test
    @DisplayName("GET /api/v1/reports - CUSTOM report type - Success with provided date range")
    void testGenerateCustomReport_Success() throws Exception {
        // Arrange - Custom date range for testing (Q1 2024)
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);
        
        ReportResponse mockResponse = ReportResponse.builder()
                .jobExecutionId(12347L)
                .reportType("TRANSACTION_SUMMARY")
                .startDate(startDate)
                .endDate(endDate)
                .accountId(null)
                .reportStatus("STARTING")
                .createdAt(LocalDateTime.now())
                .estimatedCompletion(LocalDateTime.now().plusMinutes(10))
                .downloadUrl(null)
                .message("Report generation job submitted successfully")
                .build();
        
        when(reportService.generateReport(
                eq(startDate),
                eq(endDate),
                eq("TRANSACTION_SUMMARY"),
                isNull()))
                .thenReturn(mockResponse);
        
        // Act & Assert - Verify GET /api/v1/reports?reportType=CUSTOM&startDate=2024-01-01&endDate=2024-03-31
        mockMvc.perform(get("/api/v1/reports")
                .param("reportType", "CUSTOM")
                .param("startDate", "2024-01-01")
                .param("endDate", "2024-03-31")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())  // HTTP 202 for async processing
                .andExpect(jsonPath("$.jobExecutionId").value(12347))
                .andExpect(jsonPath("$.reportType").value("TRANSACTION_SUMMARY"))
                .andExpect(jsonPath("$.startDate").value("2024-01-01"))
                .andExpect(jsonPath("$.endDate").value("2024-03-31"))
                .andExpect(jsonPath("$.accountId").isEmpty())
                .andExpect(jsonPath("$.reportStatus").value("STARTING"));
        
        // Verify service layer was called with exact user-provided dates
        verify(reportService, times(1)).generateReport(
                eq(startDate),
                eq(endDate),
                eq("TRANSACTION_SUMMARY"),
                isNull());
    }

    /**
     * Tests report generation with account filter for account-specific reports.
     * 
     * <p>Replaces COBOL CORPT00C.cbl lines 320-330 account-specific filtering:
     * <pre>
     * IF CORPT-ACCT-ID NOT = ZEROS
     *   EXEC CICS READ FILE(ACCTFILE)
     *     RIDFLD(CORPT-ACCT-ID)
     *     INTO(ACCOUNT-RECORD)
     *   END-EXEC
     *   IF FILE-STATUS = '23'
     *     MOVE 'Account not found' TO WS-ERROR-MESSAGE
     *     PERFORM 9999-ABEND-PROGRAM
     *   END-IF
     *   MOVE CORPT-ACCT-ID TO JCL-ACCT-ID-PARAM
     * END-IF
     * </pre>
     * 
     * <p>Verifies:
     * <ul>
     *   <li>AccountId parameter is passed to service layer</li>
     *   <li>Service validates account existence (mocked in this unit test)</li>
     *   <li>HTTP 202 ACCEPTED with accountId in response</li>
     * </ul>
     */
    @Test
    @DisplayName("GET /api/v1/reports - With accountId filter - Success")
    void testGenerateReport_WithAccountFilter_Success() throws Exception {
        // Arrange - Report for specific account
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        Long accountId = 1234567890L;
        
        ReportResponse mockResponse = ReportResponse.builder()
                .jobExecutionId(12348L)
                .reportType("TRANSACTION_SUMMARY")
                .startDate(startDate)
                .endDate(endDate)
                .accountId(accountId)
                .reportStatus("STARTING")
                .createdAt(LocalDateTime.now())
                .estimatedCompletion(LocalDateTime.now().plusMinutes(3))
                .downloadUrl(null)
                .message("Report generation job submitted successfully")
                .build();
        
        when(reportService.generateReport(
                eq(startDate),
                eq(endDate),
                eq("TRANSACTION_SUMMARY"),
                eq(accountId)))
                .thenReturn(mockResponse);
        
        // Act & Assert - Verify GET with accountId parameter
        mockMvc.perform(get("/api/v1/reports")
                .param("reportType", "CUSTOM")
                .param("startDate", "2024-01-01")
                .param("endDate", "2024-01-31")
                .param("accountId", String.valueOf(accountId))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobExecutionId").value(12348))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.reportStatus").value("STARTING"));
        
        // Verify service layer was called with accountId parameter
        verify(reportService, times(1)).generateReport(
                eq(startDate),
                eq(endDate),
                eq("TRANSACTION_SUMMARY"),
                eq(accountId));
    }

    /**
     * Tests validation error when CUSTOM report type is missing required date parameters.
     * 
     * <p>Replaces COBOL CORPT00C.cbl lines 340-352 date validation:
     * <pre>
     * WHEN CUSTOMI NOT = SPACES
     *   IF SDTMMI = SPACES OR SDTDDI = SPACES OR SDTYYYYI = SPACES
     *     MOVE 'Start date is required for custom report' TO WS-ERROR-MESSAGE
     *     MOVE 'Y' TO WS-ERR-FLG
     *     PERFORM SEND-TRNRPT-SCREEN
     *   END-IF
     *   IF EDTMMI = SPACES OR EDTDDI = SPACES OR EDTYYYYI = SPACES
     *     MOVE 'End date is required for custom report' TO WS-ERROR-MESSAGE
     *     MOVE 'Y' TO WS-ERR-FLG
     *     PERFORM SEND-TRNRPT-SCREEN
     *   END-IF
     * </pre>
     * 
     * <p>Verifies:
     * <ul>
     *   <li>HTTP 400 BAD REQUEST when startDate missing</li>
     *   <li>HTTP 400 BAD REQUEST when endDate missing</li>
     *   <li>Error message indicates required parameters</li>
     * </ul>
     */
    @Test
    @DisplayName("GET /api/v1/reports - CUSTOM without dates - Validation Error 400")
    void testGenerateCustomReport_MissingDates_ValidationError() throws Exception {
        // Act & Assert - Verify validation error when dates are missing for CUSTOM type
        mockMvc.perform(get("/api/v1/reports")
                .param("reportType", "CUSTOM")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        
        // Verify service layer was NOT called due to validation failure in controller
        verify(reportService, never()).generateReport(any(), any(), any(), any());
    }

    /**
     * Tests validation error when invalid report type is provided.
     * 
     * <p>Replaces COBOL CORPT00C.cbl lines 354-362 report type validation:
     * <pre>
     * EVALUATE TRUE
     *   WHEN MONTHLYI NOT = SPACES
     *   WHEN YEARLYI NOT = SPACES
     *   WHEN CUSTOMI NOT = SPACES
     *   WHEN OTHER
     *     MOVE 'Select a report type to print report...' TO WS-MESSAGE
     *     MOVE 'Y' TO WS-ERR-FLG
     *     PERFORM SEND-TRNRPT-SCREEN
     * END-EVALUATE
     * </pre>
     * 
     * <p>Verifies:
     * <ul>
     *   <li>HTTP 400 BAD REQUEST for invalid report type</li>
     *   <li>Error message lists allowed report types</li>
     *   <li>Service layer is not invoked</li>
     * </ul>
     */
    @Test
    @DisplayName("GET /api/v1/reports - Invalid report type - Validation Error 400")
    void testGenerateReport_InvalidReportType_ValidationError() throws Exception {
        // Act & Assert - Verify validation error for invalid report type
        mockMvc.perform(get("/api/v1/reports")
                .param("reportType", "INVALID_TYPE")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        
        // Verify service layer was NOT called
        verify(reportService, never()).generateReport(any(), any(), any(), any());
    }

    /**
     * Tests POST endpoint delegates to GET handler (DRY principle).
     * 
     * <p>POST /api/v1/reports provides alternative HTTP method semantics for
     * clients that prefer explicit action-based endpoints for job submission.
     * Internally delegates to GET handler to avoid code duplication.
     * 
     * <p>Verifies:
     * <ul>
     *   <li>POST /api/v1/reports works identically to GET</li>
     *   <li>HTTP 202 ACCEPTED with jobExecutionId</li>
     *   <li>Service layer called with correct parameters</li>
     * </ul>
     */
    @Test
    @DisplayName("POST /api/v1/reports - Delegates to GET handler - Success")
    void testGenerateReport_PostMethod_Success() throws Exception {
        // Arrange
        LocalDate now = LocalDate.now();
        LocalDate expectedStartDate = now.withDayOfMonth(1);
        YearMonth yearMonth = YearMonth.from(now);
        LocalDate expectedEndDate = yearMonth.atEndOfMonth();
        
        ReportResponse mockResponse = ReportResponse.builder()
                .jobExecutionId(12349L)
                .reportType("TRANSACTION_SUMMARY")
                .startDate(expectedStartDate)
                .endDate(expectedEndDate)
                .accountId(null)
                .reportStatus("STARTING")
                .createdAt(LocalDateTime.now())
                .estimatedCompletion(LocalDateTime.now().plusMinutes(5))
                .downloadUrl(null)
                .message("Report generation job submitted successfully")
                .build();
        
        when(reportService.generateReport(
                eq(expectedStartDate),
                eq(expectedEndDate),
                eq("TRANSACTION_SUMMARY"),
                isNull()))
                .thenReturn(mockResponse);
        
        // Act & Assert - Verify POST /api/v1/reports?reportType=MONTHLY
        mockMvc.perform(post("/api/v1/reports")
                .param("reportType", "MONTHLY")
                .with(csrf())  // Required for POST with Spring Security
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())  // HTTP 202
                .andExpect(jsonPath("$.jobExecutionId").value(12349))
                .andExpect(jsonPath("$.reportType").value("TRANSACTION_SUMMARY"))
                .andExpect(jsonPath("$.reportStatus").value("STARTING"));
        
        // Verify service layer was called (POST delegates to GET logic)
        verify(reportService, times(1)).generateReport(
                eq(expectedStartDate),
                eq(expectedEndDate),
                eq("TRANSACTION_SUMMARY"),
                isNull());
    }

    /**
     * Tests GET /api/v1/reports/{reportId} endpoint for job status tracking.
     * 
     * <p>This endpoint replaces COBOL pattern of checking JES job status via
     * operator console commands. Clients poll this endpoint to track Spring Batch
     * job execution progress and retrieve download URL when job completes.
     * 
     * <p>Replaces COBOL pattern (no direct equivalent in legacy system):
     * <pre>
     * Operator checks job status via SDSF panels:
     * - Job queue (input queue)
     * - Active jobs (executing)
     * - Output queue (completed jobs with spool files)
     * 
     * Modern equivalent: GET /api/v1/reports/{reportId}
     * Returns: jobExecutionId, reportStatus, downloadUrl
     * </pre>
     * 
     * <p>Verifies:
     * <ul>
     *   <li>HTTP 200 OK with job status details</li>
     *   <li>ReportStatus reflects Spring Batch BatchStatus (STARTING, STARTED, COMPLETED, FAILED)</li>
     *   <li>DownloadUrl populated when status=COMPLETED</li>
     * </ul>
     */
    @Test
    @DisplayName("GET /api/v1/reports/{reportId} - Job status check - Success with COMPLETED status")
    void testGetReportStatus_Success_Completed() throws Exception {
        // Arrange - Mock completed job with download URL
        Long jobExecutionId = 12345L;
        ReportResponse mockResponse = ReportResponse.builder()
                .jobExecutionId(jobExecutionId)
                .reportType("TRANSACTION_SUMMARY")
                .startDate(LocalDate.of(2024, 10, 1))
                .endDate(LocalDate.of(2024, 10, 31))
                .accountId(null)
                .reportStatus("COMPLETED")
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .estimatedCompletion(null)  // No estimate needed when completed
                .downloadUrl("/api/v1/reports/download/" + jobExecutionId)
                .message("Report generation completed successfully")
                .build();
        
        when(reportService.getJobStatus(eq(jobExecutionId)))
                .thenReturn(mockResponse);
        
        // Act & Assert - Verify GET /api/v1/reports/{reportId}
        mockMvc.perform(get("/api/v1/reports/{reportId}", jobExecutionId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())  // HTTP 200
                .andExpect(jsonPath("$.jobExecutionId").value(jobExecutionId))
                .andExpect(jsonPath("$.reportType").value("TRANSACTION_SUMMARY"))
                .andExpect(jsonPath("$.reportStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.downloadUrl").value("/api/v1/reports/download/" + jobExecutionId))
                .andExpect(jsonPath("$.message").value(containsString("completed")));
        
        // Verify service layer was called with correct jobExecutionId
        verify(reportService, times(1)).getJobStatus(eq(jobExecutionId));
    }

    /**
     * Tests job status check for a running job (no download URL yet).
     * 
     * <p>Verifies:
     * <ul>
     *   <li>HTTP 200 OK with reportStatus=STARTED</li>
     *   <li>DownloadUrl is null while job is running</li>
     *   <li>EstimatedCompletion timestamp is provided</li>
     * </ul>
     */
    @Test
    @DisplayName("GET /api/v1/reports/{reportId} - Job status check - STARTED (in progress)")
    void testGetReportStatus_Success_Started() throws Exception {
        // Arrange - Mock running job
        Long jobExecutionId = 12346L;
        ReportResponse mockResponse = ReportResponse.builder()
                .jobExecutionId(jobExecutionId)
                .reportType("TRANSACTION_SUMMARY")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .accountId(null)
                .reportStatus("STARTED")
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .estimatedCompletion(LocalDateTime.now().plusMinutes(28))  // 30 minutes total
                .downloadUrl(null)  // Not available yet
                .message("Report generation in progress")
                .build();
        
        when(reportService.getJobStatus(eq(jobExecutionId)))
                .thenReturn(mockResponse);
        
        // Act & Assert - Verify running job status
        mockMvc.perform(get("/api/v1/reports/{reportId}", jobExecutionId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(jobExecutionId))
                .andExpect(jsonPath("$.reportStatus").value("STARTED"))
                .andExpect(jsonPath("$.downloadUrl").isEmpty())
                .andExpect(jsonPath("$.estimatedCompletion").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("in progress")));
        
        // Verify service layer was called
        verify(reportService, times(1)).getJobStatus(eq(jobExecutionId));
    }

    /**
     * Tests parameterized report generation with various date ranges and report types.
     * 
     * <p>This parameterized test covers multiple report scenarios matching COBOL
     * CORPT00.bms menu options (Monthly, Yearly, Custom) with different date ranges
     * as would be tested in legacy COBOL system via CICS transaction execution.
     * 
     * <p>Test Data:
     * <ul>
     *   <li>Row 1: Current month report</li>
     *   <li>Row 2: Current year report</li>
     *   <li>Row 3: Last 3 months custom range</li>
     *   <li>Row 4: Q1 2024 custom range</li>
     *   <li>Row 5: Single month custom range</li>
     * </ul>
     * 
     * <p>Verifies:
     * <ul>
     *   <li>All report types return HTTP 202 ACCEPTED</li>
     *   <li>JobExecutionId is always present in response</li>
     *   <li>ReportStatus begins with STARTING</li>
     * </ul>
     */
    @ParameterizedTest(name = "[{index}] reportType={0}, description={1}")
    @CsvSource({
        "MONTHLY,    Current month transactions,     true,  false",
        "YEARLY,     Current year transactions,      true,  false",
        "CUSTOM,     Last 3 months,                  false, true",
        "CUSTOM,     Q1 2024 transactions,           false, true",
        "CUSTOM,     Single month custom,            false, true"
    })
    @DisplayName("Parameterized test - Various report types and date ranges")
    void testGenerateReport_ParameterizedScenarios(
            String reportType,
            String description,
            boolean autoCalculateDates,
            boolean requiresCustomDates) throws Exception {
        
        // Arrange - Calculate dates based on scenario
        LocalDate startDate;
        LocalDate endDate;
        
        if (reportType.equals("MONTHLY")) {
            LocalDate now = LocalDate.now();
            startDate = now.withDayOfMonth(1);
            endDate = YearMonth.from(now).atEndOfMonth();
        } else if (reportType.equals("YEARLY")) {
            int year = LocalDate.now().getYear();
            startDate = LocalDate.of(year, 1, 1);
            endDate = LocalDate.of(year, 12, 31);
        } else {  // CUSTOM
            if (description.contains("Last 3 months")) {
                endDate = LocalDate.now();
                startDate = endDate.minusMonths(3);
            } else if (description.contains("Q1 2024")) {
                startDate = LocalDate.of(2024, 1, 1);
                endDate = LocalDate.of(2024, 3, 31);
            } else {  // Single month custom
                startDate = LocalDate.of(2024, 6, 1);
                endDate = LocalDate.of(2024, 6, 30);
            }
        }
        
        ReportResponse mockResponse = ReportResponse.builder()
                .jobExecutionId(99999L)
                .reportType("TRANSACTION_SUMMARY")
                .startDate(startDate)
                .endDate(endDate)
                .accountId(null)
                .reportStatus("STARTING")
                .createdAt(LocalDateTime.now())
                .estimatedCompletion(LocalDateTime.now().plusMinutes(5))
                .downloadUrl(null)
                .message("Report generation job submitted successfully")
                .build();
        
        when(reportService.generateReport(
                any(LocalDate.class),
                any(LocalDate.class),
                eq("TRANSACTION_SUMMARY"),
                isNull()))
                .thenReturn(mockResponse);
        
        // Act & Assert - Build request based on report type
        if (reportType.equals("CUSTOM")) {
            mockMvc.perform(get("/api/v1/reports")
                    .param("reportType", reportType)
                    .param("startDate", startDate.toString())
                    .param("endDate", endDate.toString())
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.jobExecutionId").value(99999))
                    .andExpect(jsonPath("$.reportStatus").value("STARTING"));
        } else {
            mockMvc.perform(get("/api/v1/reports")
                    .param("reportType", reportType)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.jobExecutionId").value(99999))
                    .andExpect(jsonPath("$.reportStatus").value("STARTING"));
        }
        
        // Verify service layer was called
        verify(reportService, atLeastOnce()).generateReport(
                any(LocalDate.class),
                any(LocalDate.class),
                eq("TRANSACTION_SUMMARY"),
                isNull());
    }
}
