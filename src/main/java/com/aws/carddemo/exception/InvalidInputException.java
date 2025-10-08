/*
 * InvalidInputException.java
 * 
 * Custom unchecked exception for input validation failures beyond Bean Validation scope,
 * handling business logic constraints like invalid date ranges, malformed identifiers,
 * and rule violations.
 * 
 * Migrated from COBOL validation patterns:
 * - app/cbl/COACTUPC.cbl: Field validation logic (INSPECT, VERIFY)
 * - app/cbl/COCRDUPC.cbl: Card validation rules
 * - app/cbl/CSUTLDTC.cbl: Date validation via CEEDAYS API
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
package com.aws.carddemo.exception;

/**
 * Custom unchecked exception for input validation failures beyond Bean Validation scope.
 * 
 * <p>This exception extends {@link RuntimeException} to enable automatic Spring
 * {@code @Transactional} rollback when validation failures occur, maintaining ACID
 * transaction semantics equivalent to COBOL {@code EXEC CICS SYNCPOINT ROLLBACK}.
 * 
 * <p><strong>Purpose:</strong></p>
 * <ul>
 *   <li>Handle business logic constraints not expressible via JSR-380 Bean Validation annotations</li>
 *   <li>Cross-field validation (e.g., startDate must be before endDate)</li>
 *   <li>Complex format validation (e.g., account number check-digit verification)</li>
 *   <li>Business rule violations (e.g., cannot update expired card, invalid date ranges)</li>
 *   <li>Replace COBOL validation patterns: INSPECT, VERIFY, IF NOT NUMERIC</li>
 * </ul>
 * 
 * <p><strong>HTTP Status Mapping:</strong></p>
 * <p>This exception is mapped to <strong>HTTP 400 Bad Request</strong> status code
 * in {@code GlobalExceptionHandler} via {@code @ExceptionHandler(InvalidInputException.class)}.
 * 
 * <p><strong>COBOL Validation Patterns Replaced:</strong></p>
 * <pre>
 * COBOL Pattern                          | Java Equivalent
 * -----------------------------------------------------------------------------------------------------
 * INSPECT field CONVERTING               | String manipulation + throw InvalidInputException
 * VERIFY field AGAINST pattern           | Pattern.matches() + throw InvalidInputException
 * IF NOT NUMERIC                         | !StringUtils.isNumeric() + throw InvalidInputException
 *   PERFORM 9999-ABEND-PROGRAM           |
 * CALL 'CSUTLDTC' (date validation)      | DateValidator.validate() + throw InvalidInputException
 * IF CARD-EXP-MONTH NOT IN 1 THRU 12     | if (month < 1 || month > 12) throw InvalidInputException
 *   MOVE 'Invalid month' TO ERROR-MSG    |
 * IF ACCOUNT-NUM = ZEROS                 | if (accountNum == 0) throw InvalidInputException
 *   SET SEARCHED-ACCT-ZEROES TO TRUE     |
 * </pre>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Cross-field validation
 * if (startDate.isAfter(endDate)) {
 *     throw new InvalidInputException(
 *         "dateRange",
 *         "startDate must be before endDate"
 *     );
 * }
 * 
 * // Account number check-digit verification
 * if (!isValidAccountNumber(accountNumber)) {
 *     throw new InvalidInputException(
 *         "accountNumber",
 *         "Invalid check digit in account number"
 *     );
 * }
 * 
 * // Business rule: Cannot update expired card
 * if (card.getExpirationDate().isBefore(LocalDate.now())) {
 *     throw new InvalidInputException(
 *         "cardExpirationDate",
 *         "Cannot update expired card"
 *     );
 * }
 * 
 * // Date format validation (replaces CSUTLDTC.cbl)
 * if (!isValidDate(dateString)) {
 *     throw new InvalidInputException(
 *         "date",
 *         "Invalid date format. Expected YYYY-MM-DD"
 *     );
 * }
 * 
 * // Numeric validation (replaces IF NOT NUMERIC)
 * if (!StringUtils.isNumeric(accountId)) {
 *     throw new InvalidInputException(
 *         "accountId",
 *         "Account ID must be numeric"
 *     );
 * }
 * }</pre>
 * 
 * <p><strong>Complementary to Bean Validation:</strong></p>
 * <p>This exception <em>complements</em> JSR-380 Bean Validation annotations
 * ({@code @NotNull}, {@code @Size}, {@code @Pattern}, {@code @Min}, {@code @Max})
 * and is used for complex validations requiring programmatic logic that cannot
 * be expressed declaratively via annotations.
 * 
 * <p><strong>Bean Validation Example (NOT this exception):</strong></p>
 * <pre>{@code
 * public class AccountUpdateRequest {
 *     @NotNull(message = "Account ID is required")
 *     @Size(min = 11, max = 11, message = "Account ID must be 11 digits")
 *     @Pattern(regexp = "\\d{11}", message = "Account ID must be numeric")
 *     private String accountId;  // Validated automatically by Spring
 * }
 * }</pre>
 * 
 * <p><strong>InvalidInputException Example (USE this exception):</strong></p>
 * <pre>{@code
 * // Service layer - custom business logic validation
 * public void validateAccountUpdate(Account existing, AccountUpdateRequest request) {
 *     // Bean Validation already checked @NotNull, @Size, @Pattern
 *     
 *     // Custom check-digit algorithm (Luhn algorithm)
 *     if (!LuhnValidator.isValid(request.getAccountId())) {
 *         throw new InvalidInputException(
 *             "accountId",
 *             "Invalid check digit in account number"
 *         );
 *     }
 *     
 *     // Cross-field business rule
 *     if (request.getCreditLimit().compareTo(existing.getCurrentBalance()) < 0) {
 *         throw new InvalidInputException(
 *             "creditLimit",
 *             "Credit limit cannot be less than current balance"
 *         );
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>Integration with Spring Transaction Management:</strong></p>
 * <p>As an unchecked exception (extends {@code RuntimeException}), this exception
 * automatically triggers Spring {@code @Transactional} rollback, preserving
 * ACID transaction semantics from COBOL:
 * <pre>
 * COBOL:                                 | Java:
 * -----------------------------------------------------------------------------------------------------
 * EXEC CICS SYNCPOINT                    | @Transactional method completes successfully
 * EXEC CICS SYNCPOINT ROLLBACK           | throw new InvalidInputException(...) → automatic rollback
 * </pre>
 * 
 * <p><strong>Security Considerations (PCI-DSS Compliance):</strong></p>
 * <ul>
 *   <li>Error messages MUST NOT contain sensitive data (card numbers, SSN, passwords)</li>
 *   <li>Field names should be generic ("cardNumber" not "4111111111111111")</li>
 *   <li>Validation messages should not reveal system internals or implementation details</li>
 *   <li>{@code GlobalExceptionHandler} applies automatic masking to logs</li>
 * </ul>
 * 
 * @see RuntimeException
 * @see GlobalExceptionHandler
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2024
 */
public class InvalidInputException extends RuntimeException {
    
    /**
     * Serial version UID for serialization compatibility.
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * The name of the field that failed validation.
     * Used by GlobalExceptionHandler to construct detailed error responses.
     */
    private final String fieldName;
    
    /**
     * Constructs a new InvalidInputException with the specified field name and validation message.
     * 
     * <p>This is the <strong>primary constructor</strong> for field-level validation failures.
     * The field name and message are combined to create a detailed error response.
     * 
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * throw new InvalidInputException(
     *     "startDate",
     *     "Start date must be before end date"
     * );
     * 
     * // GlobalExceptionHandler returns:
     * // {
     * //   "timestamp": "2024-01-15T10:30:00",
     * //   "status": 400,
     * //   "error": "Bad Request",
     * //   "message": "startDate: Start date must be before end date",
     * //   "path": "/api/v1/accounts/12345"
     * // }
     * }</pre>
     * 
     * <p><strong>Replaces COBOL patterns:</strong></p>
     * <pre>
     * COBOL:
     *     IF ACUP-NEW-OPEN-DATE NOT NUMERIC
     *         MOVE 'Account open date must be numeric' TO WS-RETURN-MSG
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF
     * 
     * Java:
     *     if (!StringUtils.isNumeric(accountOpenDate)) {
     *         throw new InvalidInputException(
     *             "accountOpenDate",
     *             "Account open date must be numeric"
     *         );
     *     }
     * </pre>
     * 
     * @param fieldName the name of the field that failed validation (e.g., "accountId", "dateRange")
     * @param message the detailed validation error message (e.g., "Invalid check digit")
     */
    public InvalidInputException(String fieldName, String message) {
        super(formatMessage(fieldName, message));
        this.fieldName = fieldName;
    }
    
    /**
     * Constructs a new InvalidInputException with the specified message.
     * 
     * <p>Use this constructor for general validation failures not tied to a specific field,
     * or when validating multiple fields together (cross-field validation).
     * 
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * throw new InvalidInputException(
     *     "Invalid date range: start date must be before end date"
     * );
     * }</pre>
     * 
     * <p><strong>Replaces COBOL patterns:</strong></p>
     * <pre>
     * COBOL:
     *     IF NO-SEARCH-CRITERIA-RECEIVED
     *         MOVE 'No input received' TO WS-RETURN-MSG
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF
     * 
     * Java:
     *     if (searchRequest.isEmpty()) {
     *         throw new InvalidInputException("No search criteria received");
     *     }
     * </pre>
     * 
     * @param message the validation error message
     */
    public InvalidInputException(String message) {
        super(message);
        this.fieldName = null;
    }
    
    /**
     * Constructs a new InvalidInputException with the specified message and cause.
     * 
     * <p>Use this constructor when wrapping another exception that occurred during validation.
     * For example, wrapping a {@code DateTimeParseException} during date validation.
     * 
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * try {
     *     LocalDate date = LocalDate.parse(dateString);
     * } catch (DateTimeParseException e) {
     *     throw new InvalidInputException(
     *         "Invalid date format. Expected YYYY-MM-DD",
     *         e
     *     );
     * }
     * }</pre>
     * 
     * <p><strong>Replaces COBOL patterns:</strong></p>
     * <pre>
     * COBOL (CSUTLDTC.cbl):
     *     CALL "CEEDAYS" USING ...
     *     IF FC-BAD-DATE-VALUE
     *         MOVE 'Datevalue error' TO WS-RESULT
     *         MOVE WS-SEVERITY-N TO RETURN-CODE
     *     END-IF
     * 
     * Java:
     *     try {
     *         dateValidator.validate(dateString);
     *     } catch (DateTimeException e) {
     *         throw new InvalidInputException("Date value error", e);
     *     }
     * </pre>
     * 
     * @param message the validation error message
     * @param cause the cause of the validation failure (saved for later retrieval by {@link #getCause()})
     */
    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
        this.fieldName = null;
    }
    
    /**
     * Constructs a new InvalidInputException with the specified cause.
     * 
     * <p>Use this constructor when the cause exception provides sufficient detail
     * and no additional message is needed.
     * 
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * try {
     *     // Complex validation logic
     *     customValidator.validate(input);
     * } catch (ValidationException e) {
     *     throw new InvalidInputException(e);
     * }
     * }</pre>
     * 
     * @param cause the cause of the validation failure (message derived from cause)
     */
    public InvalidInputException(Throwable cause) {
        super(cause);
        this.fieldName = null;
    }
    
    /**
     * Returns the name of the field that failed validation.
     * 
     * <p>This method is used by {@code GlobalExceptionHandler} to construct
     * detailed error responses with field-specific error information.
     * 
     * <p><strong>Example usage in GlobalExceptionHandler:</strong></p>
     * <pre>{@code
     * @ExceptionHandler(InvalidInputException.class)
     * public ResponseEntity<ApiError> handleInvalidInput(
     *         InvalidInputException ex,
     *         HttpServletRequest request) {
     *     
     *     ApiError error = ApiError.builder()
     *             .timestamp(LocalDateTime.now())
     *             .status(HttpStatus.BAD_REQUEST.value())
     *             .error("Bad Request")
     *             .message(ex.getMessage())
     *             .path(request.getRequestURI())
     *             .build();
     *     
     *     // Log with field name for debugging
     *     if (ex.getFieldName() != null) {
     *         log.warn("Validation failure on field: {}", ex.getFieldName());
     *     }
     *     
     *     return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
     * }
     * }</pre>
     * 
     * @return the field name, or {@code null} if not specified
     */
    public String getFieldName() {
        return fieldName;
    }
    
    /**
     * Formats the error message with field name prefix if available.
     * 
     * <p>This private helper method combines the field name and validation message
     * into a single formatted string for the exception message.
     * 
     * <p><strong>Format:</strong> {@code "fieldName: validationMessage"}
     * 
     * <p><strong>Example:</strong></p>
     * <pre>
     * formatMessage("accountId", "Invalid check digit")
     * → "accountId: Invalid check digit"
     * </pre>
     * 
     * @param fieldName the field name
     * @param message the validation message
     * @return the formatted message
     */
    private static String formatMessage(String fieldName, String message) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return message;
        }
        return fieldName + ": " + message;
    }
    
    /**
     * Returns the detail message string of this exception.
     * 
     * <p>Inherited from {@link RuntimeException} and exposed as part of the
     * {@code members_exposed} contract in the file schema.
     * 
     * @return the detail message string
     */
    @Override
    public String getMessage() {
        return super.getMessage();
    }
    
    /**
     * Returns the cause of this exception.
     * 
     * <p>Inherited from {@link Throwable} and exposed as part of the
     * {@code members_exposed} contract in the file schema.
     * 
     * @return the cause of this exception, or {@code null} if the cause is
     *         nonexistent or unknown
     */
    @Override
    public Throwable getCause() {
        return super.getCause();
    }
    
    /**
     * Provides the stack trace for this exception.
     * 
     * <p>Inherited from {@link Throwable} and exposed as part of the
     * {@code members_exposed} contract in the file schema.
     * 
     * @return an array of stack trace elements representing the stack trace
     *         for this exception
     */
    @Override
    public StackTraceElement[] getStackTrace() {
        return super.getStackTrace();
    }
}
