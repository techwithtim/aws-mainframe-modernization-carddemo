/*
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

package com.aws.carddemo.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response DTO used by GlobalExceptionHandler for all REST API error scenarios.
 * 
 * <p>Provides consistent error structure across all endpoints with timestamp, HTTP status code,
 * error message, validation errors, request path, and trace ID for debugging.</p>
 * 
 * <p><strong>Migrated from COBOL:</strong></p>
 * <ul>
 *   <li>app/cpy/CSMSG01Y.cpy - Common message literals (CCDA-MSG-INVALID-KEY, etc.)</li>
 *   <li>app/cpy/CSMSG02Y.cpy - ABEND-DATA structure (ABEND-CODE, ABEND-CULPRIT, ABEND-REASON, ABEND-MSG)</li>
 * </ul>
 * 
 * <p><strong>COBOL Error Handling Patterns Replaced:</strong></p>
 * <ul>
 *   <li>9999-ABEND-PROGRAM paragraph → Exception throwing with ApiError response</li>
 *   <li>EXEC CICS ABEND ABCODE → HTTP status codes (400, 404, 500, etc.)</li>
 *   <li>ABEND-CODE (PIC X(4)) → status (int HTTP status code)</li>
 *   <li>ABEND-CULPRIT (PIC X(8)) → path (String request URI)</li>
 *   <li>ABEND-REASON (PIC X(50)) → message (String error description)</li>
 *   <li>ABEND-MSG (PIC X(72)) → Full JSON structure with all fields</li>
 * </ul>
 * 
 * <p><strong>Usage by GlobalExceptionHandler:</strong></p>
 * <pre>
 * {@code
 * @ExceptionHandler(ResourceNotFoundException.class)
 * public ResponseEntity<ApiError> handleResourceNotFound(
 *         ResourceNotFoundException ex, HttpServletRequest request) {
 *     ApiError error = ApiError.builder()
 *         .timestamp(LocalDateTime.now())
 *         .status(HttpStatus.NOT_FOUND.value())
 *         .error(HttpStatus.NOT_FOUND.getReasonPhrase())
 *         .message(ex.getMessage())
 *         .path(request.getRequestURI())
 *         .traceId(MDC.get("traceId"))
 *         .build();
 *     return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
 * }
 * }
 * </pre>
 * 
 * <p><strong>PCI-DSS Compliance:</strong> Error messages MUST NOT contain sensitive data
 * (card numbers, SSNs, passwords, CVVs). Regex-based masking applied before error construction
 * to prevent accidental exposure in logs or API responses.</p>
 * 
 * @see com.aws.carddemo.exception.GlobalExceptionHandler
 * @see com.aws.carddemo.exception.ResourceNotFoundException
 * @see com.aws.carddemo.exception.InvalidInputException
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    /**
     * Timestamp when the error occurred in ISO 8601 format (UTC timezone).
     * 
     * <p>Provides audit trail for error tracking and debugging. Formatted as
     * "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" for consistent datetime representation
     * across all API responses.</p>
     * 
     * <p><strong>Replaces:</strong> COBOL FUNCTION CURRENT-DATE</p>
     * 
     * <p><strong>Example:</strong> "2024-01-15T14:30:45.123Z"</p>
     */
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime timestamp;

    /**
     * HTTP status code indicating the error category.
     * 
     * <p><strong>Common Status Codes:</strong></p>
     * <ul>
     *   <li>400 - Bad Request (invalid input, validation failure)</li>
     *   <li>401 - Unauthorized (authentication failure)</li>
     *   <li>403 - Forbidden (insufficient permissions)</li>
     *   <li>404 - Not Found (resource does not exist)</li>
     *   <li>409 - Conflict (duplicate resource)</li>
     *   <li>422 - Unprocessable Entity (business rule violation like insufficient funds)</li>
     *   <li>500 - Internal Server Error (unexpected system error)</li>
     * </ul>
     * 
     * <p><strong>Replaces:</strong> COBOL ABEND-CODE (PIC X(4))</p>
     * 
     * <p><strong>Example:</strong> 404</p>
     */
    @JsonProperty("status")
    private int status;

    /**
     * HTTP status reason phrase providing standard description of status code.
     * 
     * <p>Maps directly to HttpStatus enum reason phrases for consistency
     * with HTTP specification.</p>
     * 
     * <p><strong>Examples:</strong> "Bad Request", "Not Found", "Internal Server Error"</p>
     */
    @JsonProperty("error")
    private String error;

    /**
     * Human-readable error message describing what went wrong.
     * 
     * <p>This message should be meaningful to API consumers for debugging
     * and error handling. MUST NOT contain sensitive data per PCI-DSS requirements.</p>
     * 
     * <p><strong>Replaces:</strong> COBOL ABEND-REASON (PIC X(50))</p>
     * 
     * <p><strong>Valid Examples:</strong></p>
     * <ul>
     *   <li>"Account not found with ID: 999"</li>
     *   <li>"Invalid account number format"</li>
     *   <li>"Insufficient funds for transaction"</li>
     *   <li>"Authentication failed for user"</li>
     * </ul>
     * 
     * <p><strong>INVALID Examples (PCI-DSS violations):</strong></p>
     * <ul>
     *   <li>"Card 4532-1234-5678-9010 not found" (exposes card number)</li>
     *   <li>"SSN 123-45-6789 is invalid" (exposes SSN)</li>
     *   <li>"Password 'secret123' is incorrect" (exposes password)</li>
     * </ul>
     */
    @JsonProperty("message")
    private String message;

    /**
     * Request URI that triggered the error.
     * 
     * <p>Provides context for debugging by identifying which endpoint
     * produced the error. Extracted from HttpServletRequest.getRequestURI().</p>
     * 
     * <p><strong>Replaces:</strong> COBOL ABEND-CULPRIT (PIC X(8) program name)
     * with full REST API path context</p>
     * 
     * <p><strong>Examples:</strong></p>
     * <ul>
     *   <li>"/api/v1/accounts/999"</li>
     *   <li>"/api/v1/transactions"</li>
     *   <li>"/api/v1/auth/login"</li>
     * </ul>
     */
    @JsonProperty("path")
    private String path;

    /**
     * Correlation identifier linking this error response to log entries in CloudWatch Logs.
     * 
     * <p>Generated from MDC (Mapped Diagnostic Context) or extracted from request header
     * (X-Trace-Id). Enables distributed tracing across service boundaries and facilitates
     * log aggregation for troubleshooting.</p>
     * 
     * <p><strong>Usage Pattern:</strong></p>
     * <pre>
     * {@code
     * // In filter or interceptor:
     * String traceId = UUID.randomUUID().toString();
     * MDC.put("traceId", traceId);
     * 
     * // In exception handler:
     * String traceId = MDC.get("traceId");
     * ApiError error = ApiError.builder()
     *     .traceId(traceId)
     *     // ... other fields
     *     .build();
     * }
     * </pre>
     * 
     * <p><strong>Example:</strong> "a7f3d2c1-4b5e-6789-0abc-def123456789"</p>
     */
    @JsonProperty("traceId")
    private String traceId;

    /**
     * Map of field names to validation error messages for Bean Validation constraint violations.
     * 
     * <p>Populated when handling MethodArgumentNotValidException by extracting
     * BindingResult field errors. Enables client-side display of field-level
     * validation errors for forms and API requests.</p>
     * 
     * <p><strong>Only included in response when validation errors are present</strong>
     * (due to @JsonInclude(NON_NULL) annotation at class level).</p>
     * 
     * <p><strong>Example Structure:</strong></p>
     * <pre>
     * {
     *   "accountNumber": "must be exactly 11 digits",
     *   "creditLimit": "must be greater than or equal to 0",
     *   "email": "must be a well-formed email address"
     * }
     * </pre>
     * 
     * <p><strong>Population from MethodArgumentNotValidException:</strong></p>
     * <pre>
     * {@code
     * Map<String, String> validationErrors = new HashMap<>();
     * BindingResult bindingResult = ex.getBindingResult();
     * bindingResult.getFieldErrors().forEach(fieldError -> {
     *     validationErrors.put(
     *         fieldError.getField(),
     *         fieldError.getDefaultMessage()
     *     );
     * });
     * 
     * ApiError error = ApiError.builder()
     *     .validationErrors(validationErrors)
     *     // ... other fields
     *     .build();
     * }
     * </pre>
     * 
     * <p><strong>Replaces:</strong> COBOL field-level edit validation with move to error fields</p>
     */
    @JsonProperty("validationErrors")
    private Map<String, String> validationErrors;

}
