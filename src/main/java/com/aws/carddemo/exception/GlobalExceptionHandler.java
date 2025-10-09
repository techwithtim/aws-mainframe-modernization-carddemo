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

package com.aws.carddemo.exception;

import com.aws.carddemo.dto.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler providing centralized exception interception across all REST controllers.
 * 
 * <p>This class is annotated with {@code @ControllerAdvice} to enable application-wide exception handling,
 * replacing the distributed COBOL 9999-ABEND-PROGRAM error handling paragraphs found in every legacy
 * COBOL program with a single, unified exception management strategy.</p>
 * 
 * <p><strong>Migrated from COBOL Error Handling Patterns:</strong></p>
 * <ul>
 *   <li>{@code app/cpy/CSMSG01Y.cpy} - Common message literals (CCDA-MSG-INVALID-KEY, CCDA-MSG-THANK-YOU)</li>
 *   <li>{@code app/cpy/CSMSG02Y.cpy} - ABEND-DATA structure (ABEND-CODE, ABEND-CULPRIT, ABEND-REASON, ABEND-MSG)</li>
 *   <li>{@code app/cbl/COSGN00C.cbl} - Authentication error handling (lines 242, 249, 254)</li>
 *   <li>{@code app/cbl/*.cbl} - Distributed 9999-ABEND-PROGRAM paragraphs in all 29 COBOL programs</li>
 * </ul>
 * 
 * <p><strong>COBOL Error Handling Patterns Replaced:</strong></p>
 * <pre>
 * COBOL Pattern                                      | Java Equivalent
 * --------------------------------------------------------------------------------------------------
 * PERFORM 9999-ABEND-PROGRAM                         | throw new CustomException(...)
 * EXEC CICS HANDLE CONDITION ERROR(ERROR-PARA)      | @ExceptionHandler method
 * EXEC CICS ABEND ABCODE('ABCD')                     | HTTP status code in ResponseEntity
 * MOVE 'Error message' TO WS-RETURN-MSG              | ApiError.builder().message(...)
 * MOVE WS-PGMNAME TO ABEND-CULPRIT                   | request.getRequestURI() in ApiError.path
 * MOVE 'Error reason' TO ABEND-REASON                | Exception getMessage()
 * EXEC CICS SEND TEXT FROM(WS-MESSAGE)               | ResponseEntity.status(...).body(apiError)
 * EXEC CICS SYNCPOINT ROLLBACK                       | Automatic @Transactional rollback on RuntimeException
 * </pre>
 * 
 * <p><strong>PCI-DSS Compliance:</strong></p>
 * <p>This handler implements PCI-DSS compliant error logging with automatic masking of sensitive data:
 * <ul>
 *   <li>Card numbers (16 digits): Masked as ************1234 (last 4 digits visible)</li>
 *   <li>SSN (9 digits): Masked as *****6789 (last 4 digits visible)</li>
 *   <li>Passwords: Completely excluded from logs and error responses</li>
 *   <li>CVV codes: Never logged or returned in responses</li>
 * </ul>
 * 
 * <p><strong>Integration with Spring Transaction Management:</strong></p>
 * <p>All custom exceptions extend {@code RuntimeException}, enabling automatic Spring
 * {@code @Transactional} rollback behavior. This preserves ACID transaction semantics equivalent
 * to COBOL {@code EXEC CICS SYNCPOINT ROLLBACK} commands.</p>
 * 
 * <p><strong>Standardized Error Response Format:</strong></p>
 * <p>All exception handlers return {@code ResponseEntity<ApiError>} with consistent structure:
 * <pre>
 * {
 *   "timestamp": "2024-01-15T10:30:00.000Z",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Account with ID 12345 not found",
 *   "path": "/api/v1/accounts/12345",
 *   "traceId": "a7f3d2c1-4b5e-6789-0abc-def123456789",
 *   "validationErrors": {
 *     "accountNumber": "must be exactly 11 digits",
 *     "creditLimit": "must be greater than 0"
 *   }
 * }
 * </pre>
 * 
 * <p><strong>CloudWatch Logs Integration:</strong></p>
 * <p>All exceptions are logged to CloudWatch Logs with structured JSON format including:
 * <ul>
 *   <li>Timestamp in UTC timezone</li>
 *   <li>Exception class name and message</li>
 *   <li>Request URI and HTTP method</li>
 *   <li>User ID (if authenticated)</li>
 *   <li>Trace ID for distributed tracing</li>
 *   <li>Stack trace (for 500 errors)</li>
 *   <li>Automatic sensitive data masking via Logback converters</li>
 * </ul>
 * 
 * @see ApiError
 * @see ResourceNotFoundException
 * @see InvalidInputException
 * @see DuplicateResourceException
 * @see InsufficientFundsException
 * @see AuthenticationFailedException
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles ResourceNotFoundException thrown when entity lookups fail (entity not found in database).
     * 
     * <p>Maps to <strong>HTTP 404 Not Found</strong> status code.</p>
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COBOL:
     *     EXEC CICS HANDLE CONDITION NOTFND(NOT-FOUND-PARA)
     *     EXEC CICS READ DATASET('ACCTFILE') ...
     *     IF APPL-EOF = 'Y'
     *         MOVE 'Account not found' TO WS-RETURN-MSG
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF
     * 
     * Java:
     *     Account account = accountRepository.findById(accountId)
     *         .orElseThrow(() -&gt; new ResourceNotFoundException("Account", accountId));
     * </pre>
     * 
     * <p><strong>Example API Response:</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00.000Z",
     *   "status": 404,
     *   "error": "Not Found",
     *   "message": "Account with ID 12345 not found",
     *   "path": "/api/v1/accounts/12345"
     * }
     * </pre>
     * 
     * @param ex the ResourceNotFoundException thrown by service layer
     * @param request the HttpServletRequest for extracting request URI
     * @return ResponseEntity with ApiError body and HTTP 404 status
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFoundException(
            ResourceNotFoundException ex, 
            HttpServletRequest request) {
        
        log.warn("Resource not found: {} at URI: {}", ex.getMessage(), request.getRequestURI());
        
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handles InvalidInputException thrown for input validation failures beyond Bean Validation scope.
     * 
     * <p>Maps to <strong>HTTP 400 Bad Request</strong> status code.</p>
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COBOL:
     *     IF ACUP-NEW-OPEN-DATE NOT NUMERIC
     *         MOVE 'Account open date must be numeric' TO WS-RETURN-MSG
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF
     *     INSPECT ACCOUNT-NUM CONVERTING SPACES TO ZEROS
     *     IF ACCOUNT-NUM = ZEROS
     *         MOVE 'Account number is required' TO WS-RETURN-MSG
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF
     * 
     * Java:
     *     if (!StringUtils.isNumeric(accountOpenDate)) {
     *         throw new InvalidInputException("accountOpenDate", "Account open date must be numeric");
     *     }
     *     if (accountNumber == null || accountNumber.equals("00000000000")) {
     *         throw new InvalidInputException("accountNumber", "Account number is required");
     *     }
     * </pre>
     * 
     * <p><strong>Example API Response:</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00.000Z",
     *   "status": 400,
     *   "error": "Bad Request",
     *   "message": "accountNumber: Invalid check digit in account number",
     *   "path": "/api/v1/accounts"
     * }
     * </pre>
     * 
     * @param ex the InvalidInputException thrown by service or controller layer
     * @param request the HttpServletRequest for extracting request URI
     * @return ResponseEntity with ApiError body and HTTP 400 status
     */
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiError> handleInvalidInputException(
            InvalidInputException ex, 
            HttpServletRequest request) {
        
        log.warn("Invalid input: {} at URI: {}", ex.getMessage(), request.getRequestURI());
        
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles DuplicateResourceException thrown for unique constraint violations on database columns.
     * 
     * <p>Maps to <strong>HTTP 409 Conflict</strong> status code.</p>
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COBOL (from COUSR01C.cbl lines 260-266):
     *     EXEC CICS WRITE DATASET('USRSEC') ...
     *         RIDFLD(SEC-USR-ID)
     *     END-EXEC
     *     EVALUATE EIBRESP
     *         WHEN DFHRESP(NORMAL)
     *             MOVE 'User Created Successfully' TO WS-MESSAGE
     *         WHEN DFHRESP(DUPKEY)
     *             MOVE 'Y' TO WS-ERR-FLG
     *             MOVE 'User ID already exist. Try with different ID' TO WS-MESSAGE
     *         WHEN OTHER
     *             MOVE 'Y' TO WS-ERR-FLG
     *             MOVE 'Unable to add User. Try again later' TO WS-MESSAGE
     *     END-EVALUATE
     * 
     * Java:
     *     if (userRepository.findByUsername(username).isPresent()) {
     *         throw new DuplicateResourceException("User", "username", username);
     *     }
     * </pre>
     * 
     * <p><strong>Example API Response:</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00.000Z",
     *   "status": 409,
     *   "error": "Conflict",
     *   "message": "User with username 'john.doe' already exists",
     *   "path": "/api/v1/admin/users"
     * }
     * </pre>
     * 
     * @param ex the DuplicateResourceException thrown by service layer
     * @param request the HttpServletRequest for extracting request URI
     * @return ResponseEntity with ApiError body and HTTP 409 status
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicateResourceException(
            DuplicateResourceException ex, 
            HttpServletRequest request) {
        
        log.warn("Duplicate resource: {} at URI: {}", ex.getMessage(), request.getRequestURI());
        
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handles InsufficientFundsException thrown when payment/transaction amount exceeds credit limit.
     * 
     * <p>Maps to <strong>HTTP 422 Unprocessable Entity</strong> status code.</p>
     * 
     * <p><strong>COBOL Equivalent (from CBTRN01C.cbl):</strong></p>
     * <pre>
     * COBOL:
     *     COMPUTE NEW-BALANCE = ACCT-CURR-BAL + TXN-AMT
     *     IF NEW-BALANCE > ACCT-CREDIT-LIMIT
     *         MOVE 'Transaction declined - exceeds credit limit' TO WS-RETURN-MSG
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF
     * 
     * Java:
     *     BigDecimal newBalance = account.getCurrentBalance().add(transactionAmount);
     *     if (newBalance.compareTo(account.getCreditLimit()) > 0) {
     *         throw new InsufficientFundsException(
     *             transactionAmount,
     *             account.getCurrentBalance(),
     *             account.getCreditLimit()
     *         );
     *     }
     * </pre>
     * 
     * <p><strong>Example API Response:</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00.000Z",
     *   "status": 422,
     *   "error": "Unprocessable Entity",
     *   "message": "Requested amount $2,500.00 exceeds available credit...",
     *   "path": "/api/v1/transactions"
     * }
     * </pre>
     * 
     * @param ex the InsufficientFundsException thrown by service layer
     * @param request the HttpServletRequest for extracting request URI
     * @return ResponseEntity with ApiError body and HTTP 422 status
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFundsException(
            InsufficientFundsException ex, 
            HttpServletRequest request) {
        
        log.warn("Insufficient funds: {} at URI: {}", ex.getMessage(), request.getRequestURI());
        
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handles AuthenticationFailedException thrown for authentication and authorization failures.
     * 
     * <p>Maps to <strong>HTTP 401 Unauthorized</strong> status code.</p>
     * 
     * <p><strong>COBOL Equivalent (from COSGN00C.cbl):</strong></p>
     * <pre>
     * COBOL (lines 223-254):
     *     EXEC CICS READ DATASET('USRSEC') ...
     *         RIDFLD(WS-USER-ID)
     *     END-EXEC
     *     EVALUATE EIBRESP
     *         WHEN DFHRESP(NORMAL)
     *             IF SEC-USR-PWD = WS-USER-PWD
     *                 MOVE 'Sign In Successful' TO WS-MESSAGE
     *             ELSE
     *                 MOVE 'Y' TO WS-ERR-FLG
     *                 MOVE 'Wrong Password. Try again ...' TO WS-MESSAGE
     *             END-IF
     *         WHEN DFHRESP(NOTFND)
     *             MOVE 'Y' TO WS-ERR-FLG
     *             MOVE 'User not found. Try again ...' TO WS-MESSAGE
     *         WHEN OTHER
     *             MOVE 'Y' TO WS-ERR-FLG
     *             MOVE 'Unable to verify the User ...' TO WS-MESSAGE
     *     END-EVALUATE
     * 
     * Java:
     *     if (!passwordEncoder.matches(password, user.getPasswordHash())) {
     *         throw new AuthenticationFailedException("Invalid credentials");
     *     }
     * </pre>
     * 
     * <p><strong>PCI-DSS Security Note:</strong></p>
     * <p>Error responses return generic "Authentication failed" message to prevent credential
     * enumeration attacks. Detailed failure reasons are logged internally for security audit.</p>
     * 
     * <p><strong>Example API Response:</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00.000Z",
     *   "status": 401,
     *   "error": "Unauthorized",
     *   "message": "Authentication failed",
     *   "path": "/api/v1/auth/login"
     * }
     * </pre>
     * 
     * @param ex the AuthenticationFailedException thrown by authentication service
     * @param request the HttpServletRequest for extracting request URI
     * @return ResponseEntity with ApiError body and HTTP 401 status
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailedException(
            AuthenticationFailedException ex, 
            HttpServletRequest request) {
        
        // Log detailed reason internally for security audit (with automatic PCI-DSS masking)
        log.warn("Authentication failed: {} at URI: {}", ex.getMessage(), request.getRequestURI());
        
        // Special case: Account lockout message should be shown to users
        // (they need to know their account is locked to contact administrator)
        // This does not compromise security - it prevents brute force attacks
        String clientMessage = "Authentication failed";
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("locked")) {
            clientMessage = ex.getMessage(); // Preserve specific lockout message
        }
        
        // Return generic message to client to prevent credential enumeration
        // EXCEPT for account lockout which is a legitimate user-facing concern
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message(clientMessage)
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handles MethodArgumentNotValidException thrown by Spring MVC when Bean Validation fails.
     * 
     * <p>Maps to <strong>HTTP 400 Bad Request</strong> status code.</p>
     * 
     * <p>This handler extracts field-level validation errors from {@code BindingResult} and
     * constructs a detailed {@code ApiError} response with a map of field names to error messages.</p>
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COBOL:
     *     IF ACCOUNT-NUMBER = SPACES
     *         MOVE 'Account number is required' TO ERROR-MSG
     *         SET FIELD-ERROR TO TRUE
     *     END-IF
     *     IF CREDIT-LIMIT NOT NUMERIC
     *         MOVE 'Credit limit must be numeric' TO ERROR-MSG
     *         SET FIELD-ERROR TO TRUE
     *     END-IF
     *     IF FIELD-ERROR
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF
     * 
     * Java (DTO with Bean Validation):
     *     public class AccountUpdateRequest {
     *         @NotBlank(message = "Account number is required")
     *         @Size(min = 11, max = 11, message = "Account number must be 11 digits")
     *         private String accountNumber;
     *         
     *         @NotNull(message = "Credit limit is required")
     *         @Min(value = 0, message = "Credit limit must be greater than or equal to 0")
     *         private BigDecimal creditLimit;
     *     }
     * </pre>
     * 
     * <p><strong>Example API Response:</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00.000Z",
     *   "status": 400,
     *   "error": "Bad Request",
     *   "message": "Validation failed for 2 fields",
     *   "path": "/api/v1/accounts",
     *   "validationErrors": {
     *     "accountNumber": "Account number is required",
     *     "creditLimit": "Credit limit must be greater than or equal to 0"
     *   }
     * }
     * </pre>
     * 
     * @param ex the MethodArgumentNotValidException thrown by Spring MVC
     * @param request the HttpServletRequest for extracting request URI
     * @return ResponseEntity with ApiError body (including validationErrors map) and HTTP 400 status
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, 
            HttpServletRequest request) {
        
        Map<String, String> validationErrors = new HashMap<>();
        
        // Extract field errors from BindingResult
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
        });
        
        log.warn("Validation failed for {} fields at URI: {}", validationErrors.size(), request.getRequestURI());
        
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed for " + validationErrors.size() + " field(s)")
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles HttpMessageNotReadableException thrown when request body cannot be parsed.
     * 
     * <p>This exception occurs when:</p>
     * <ul>
     *   <li>JSON syntax is invalid (missing quotes, brackets, etc.)</li>
     *   <li>JSON data types don't match DTO fields (string for numeric field, etc.)</li>
     *   <li>Request body is empty when content is expected</li>
     * </ul>
     * 
     * <p>Replaces COBOL: Input validation logic that checks for valid numeric/alphanumeric data</p>
     * 
     * @param ex the HttpMessageNotReadableException thrown by Spring MVC
     * @param request the HttpServletRequest for extracting request URI
     * @return ResponseEntity with ApiError body and HTTP 400 status
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        
        log.warn("Malformed JSON request at URI: {} - {}", request.getRequestURI(), ex.getMessage());
        
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Malformed JSON request body")
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles DataIntegrityViolationException thrown by Spring Data JPA for database constraint violations.
     * 
     * <p>This handler detects UNIQUE constraint violations and translates them to
     * {@code DuplicateResourceException}. Other constraint violations (foreign key, NOT NULL)
     * are returned as HTTP 500 Internal Server Error with generic message.</p>
     * 
     * <p>Maps to:</p>
     * <ul>
     *   <li><strong>HTTP 409 Conflict</strong> - UNIQUE constraint violations (duplicate key)</li>
     *   <li><strong>HTTP 500 Internal Server Error</strong> - Other constraint violations</li>
     * </ul>
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COBOL:
     *     EXEC CICS WRITE DATASET('USRSEC') ...
     *     END-EXEC
     *     EVALUATE EIBRESP
     *         WHEN DFHRESP(DUPKEY)
     *             MOVE 'Duplicate key detected' TO WS-RETURN-MSG
     *             PERFORM 9999-ABEND-PROGRAM
     *         WHEN DFHRESP(INVREQ)
     *             MOVE 'Foreign key violation' TO WS-RETURN-MSG
     *             PERFORM 9999-ABEND-PROGRAM
     *     END-EVALUATE
     * 
     * Java:
     *     try {
     *         userRepository.save(user);
     *     } catch (DataIntegrityViolationException e) {
     *         // Automatically handled by this handler
     *     }
     * </pre>
     * 
     * <p><strong>Example API Response (UNIQUE constraint):</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00.000Z",
     *   "status": 409,
     *   "error": "Conflict",
     *   "message": "A resource with the provided unique identifier already exists",
     *   "path": "/api/v1/admin/users"
     * }
     * </pre>
     * 
     * <p><strong>Example API Response (other constraints):</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00.000Z",
     *   "status": 500,
     *   "error": "Internal Server Error",
     *   "message": "Database constraint violation occurred",
     *   "path": "/api/v1/accounts"
     * }
     * </pre>
     * 
     * @param ex the DataIntegrityViolationException thrown by Spring Data JPA
     * @param request the HttpServletRequest for extracting request URI
     * @return ResponseEntity with ApiError body and HTTP 409 (duplicate) or 500 (other) status
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, 
            HttpServletRequest request) {
        
        log.error("Data integrity violation at URI: {}", request.getRequestURI(), ex);
        
        // Check if this is a UNIQUE constraint violation
        String message = ex.getMessage();
        if (message != null && (message.contains("unique") || message.contains("duplicate") || message.contains("Unique"))) {
            ApiError error = ApiError.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.CONFLICT.value())
                    .error(HttpStatus.CONFLICT.getReasonPhrase())
                    .message("A resource with the provided unique identifier already exists")
                    .path(request.getRequestURI())
                    .build();
            
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        
        // Other constraint violations (foreign key, NOT NULL, etc.)
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Database constraint violation occurred")
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handles all unhandled exceptions as a catch-all safety net.
     * 
     * <p>Maps to <strong>HTTP 500 Internal Server Error</strong> status code.</p>
     * 
     * <p>This handler logs the full stack trace for debugging while returning a generic error
     * message to clients to prevent sensitive data exposure.</p>
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COBOL:
     *     9999-ABEND-PROGRAM.
     *         MOVE WS-PGMNAME TO ABEND-CULPRIT
     *         MOVE 'Unexpected error occurred' TO ABEND-REASON
     *         EXEC CICS ABEND ABCODE('ABND')
     *         END-EXEC.
     * 
     * Java:
     *     // Any unhandled RuntimeException is caught here
     * </pre>
     * 
     * <p><strong>Example API Response:</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00.000Z",
     *   "status": 500,
     *   "error": "Internal Server Error",
     *   "message": "An unexpected error occurred. Please contact support.",
     *   "path": "/api/v1/accounts/12345"
     * }
     * </pre>
     * 
     * @param ex the Exception thrown by any layer of the application
     * @param request the HttpServletRequest for extracting request URI
     * @return ResponseEntity with ApiError body and HTTP 500 status
     */
    /**
     * Handles Spring Security AccessDeniedException when @PreAuthorize checks fail.
     * 
     * <p>This handler specifically catches authorization failures from method-level security
     * annotations (@PreAuthorize, @Secured) and returns 403 Forbidden, replacing generic 500 errors.</p>
     * 
     * <p>COBOL Legacy: Equivalent to checking user authorization before EXEC CICS XCTL to restricted programs.</p>
     * 
     * @param ex the AccessDeniedException thrown by Spring Security
     * @param request the HTTP request that triggered the exception
     * @return ResponseEntity with 403 Forbidden status and standardized ApiError body
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(
            org.springframework.security.access.AccessDeniedException ex,
            HttpServletRequest request) {
        
        log.warn("Access denied at URI: {} - Reason: {}", 
                request.getRequestURI(), ex.getMessage());
        
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message("Access denied. You do not have permission to access this resource.")
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(
            Exception ex, 
            HttpServletRequest request) {
        
        // Log full stack trace for debugging (with automatic PCI-DSS masking)
        log.error("Unexpected error at URI: {}", request.getRequestURI(), ex);
        
        // Return generic message to prevent sensitive data exposure
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("An unexpected error occurred. Please contact support.")
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
