/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.exception;

/**
 * Custom unchecked exception for unique constraint violations when attempting to create
 * resources with duplicate identifiers.
 * 
 * <p>This exception is thrown when a database operation violates a unique constraint on
 * columns such as username, account number, card number, or SSN. It replaces the COBOL
 * FILE STATUS '22' (duplicate key) error handling pattern from the legacy mainframe system.</p>
 * 
 * <p><strong>COBOL Equivalent Patterns Replaced:</strong></p>
 * <ul>
 *   <li>{@code EXEC CICS HANDLE CONDITION DUPREC} - CICS duplicate record condition</li>
 *   <li>{@code WHEN DFHRESP(DUPKEY)} - CICS duplicate key response code</li>
 *   <li>{@code WHEN DFHRESP(DUPREC)} - CICS duplicate record response code</li>
 *   <li>{@code IF FILE-STATUS = '22' PERFORM 9999-ABEND-PROGRAM} - VSAM duplicate key check</li>
 * </ul>
 * 
 * <p><strong>Source COBOL Files Referenced:</strong></p>
 * <ul>
 *   <li>{@code app/cbl/COUSR01C.cbl} - User creation with duplicate username detection
 *       (lines 260-266: "WHEN DFHRESP(DUPKEY) MOVE 'User ID already exist...'")</li>
 *   <li>{@code app/cbl/COACTUPC.cbl} - Account update operations with duplicate validation</li>
 * </ul>
 * 
 * <p><strong>Database Unique Constraints Protected:</strong></p>
 * <ul>
 *   <li>{@code app_user.username} - UNIQUE constraint on user login names</li>
 *   <li>{@code account.account_number} - UNIQUE constraint on account identifiers</li>
 *   <li>{@code card.card_number} - UNIQUE constraint on card numbers (PCI-DSS requirement)</li>
 *   <li>{@code customer.ssn} - UNIQUE constraint on Social Security Numbers</li>
 * </ul>
 * 
 * <p><strong>Spring Framework Integration:</strong></p>
 * <ul>
 *   <li>Extends {@link RuntimeException} to enable automatic Spring {@code @Transactional}
 *       rollback behavior, preventing partial data commits</li>
 *   <li>Maps to HTTP 409 Conflict status code in {@code GlobalExceptionHandler} via
 *       {@code @ExceptionHandler(DuplicateResourceException.class)} method</li>
 *   <li>Supports service layer duplicate check pattern:
 *       <pre>
 *       if (userRepository.findByUsername(username).isPresent()) {
 *           throw new DuplicateResourceException("User", "username", username);
 *       }
 *       </pre>
 *   </li>
 *   <li>Wraps Spring {@code DataIntegrityViolationException} for UNIQUE constraint violations
 *       in {@code GlobalExceptionHandler}, translating database-level errors to
 *       business-friendly exception messages</li>
 * </ul>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>
 * // Example 1: Username duplicate
 * throw new DuplicateResourceException("User", "username", "john.doe");
 * // Message: "User with username 'john.doe' already exists"
 * 
 * // Example 2: Account number duplicate
 * throw new DuplicateResourceException("Account", "account number", "00000000001");
 * // Message: "Account with account number '00000000001' already exists"
 * 
 * // Example 3: Card number duplicate
 * throw new DuplicateResourceException("Card", "card number", "4111111111111111");
 * // Message: "Card with card number '4111111111111111' already exists"
 * 
 * // Example 4: Simple message constructor
 * throw new DuplicateResourceException("Duplicate user found");
 * // Message: "Duplicate user found"
 * </pre>
 * 
 * <p><strong>Functional Equivalence Guarantee:</strong></p>
 * <p>This exception maintains the same semantic behavior as the COBOL duplicate key detection:
 * it prevents the creation of duplicate records, returns a clear error message to the user,
 * and ensures transaction rollback to maintain data integrity.</p>
 * 
 * @see RuntimeException
 * @see org.springframework.transaction.annotation.Transactional
 * @see org.springframework.dao.DataIntegrityViolationException
 * 
 * @version 1.0.0
 * @since 2024-01-01
 * 
 * @author AWS CardDemo Modernization Team
 */
public class DuplicateResourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * The type of resource that has the duplicate (e.g., "User", "Account", "Card").
     */
    private final String resourceType;

    /**
     * The field name that has the duplicate value (e.g., "username", "account number").
     */
    private final String fieldName;

    /**
     * The actual duplicate value that caused the constraint violation.
     */
    private final String fieldValue;

    /**
     * Constructs a new DuplicateResourceException with a custom message.
     * 
     * <p>Use this constructor when you need a simple, custom error message without
     * structured resource details.</p>
     * 
     * @param message the detailed error message
     */
    public DuplicateResourceException(String message) {
        super(message);
        this.resourceType = null;
        this.fieldName = null;
        this.fieldValue = null;
    }

    /**
     * Constructs a new DuplicateResourceException with a custom message and cause.
     * 
     * <p>Use this constructor when wrapping a lower-level exception like
     * {@code DataIntegrityViolationException} from Spring Data JPA.</p>
     * 
     * @param message the detailed error message
     * @param cause the underlying exception that caused this exception
     */
    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
        this.resourceType = null;
        this.fieldName = null;
        this.fieldValue = null;
    }

    /**
     * Constructs a new DuplicateResourceException with structured resource information.
     * 
     * <p>This is the recommended constructor for service layer duplicate detection.
     * It automatically formats a clear, consistent error message.</p>
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * WHEN DFHRESP(DUPKEY)
     * WHEN DFHRESP(DUPREC)
     *     MOVE 'Y' TO WS-ERR-FLG
     *     MOVE 'User ID already exist...' TO WS-MESSAGE
     * </pre>
     * 
     * @param resourceType the type of resource (e.g., "User", "Account", "Card")
     * @param fieldName the field name with the duplicate (e.g., "username", "account number")
     * @param fieldValue the duplicate value that caused the violation
     */
    public DuplicateResourceException(String resourceType, String fieldName, String fieldValue) {
        super(formatMessage(resourceType, fieldName, fieldValue));
        this.resourceType = resourceType;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    /**
     * Constructs a new DuplicateResourceException with structured resource information
     * and an underlying cause.
     * 
     * <p>Use this constructor when wrapping database exceptions while preserving
     * structured resource context.</p>
     * 
     * @param resourceType the type of resource (e.g., "User", "Account", "Card")
     * @param fieldName the field name with the duplicate (e.g., "username", "account number")
     * @param fieldValue the duplicate value that caused the violation
     * @param cause the underlying exception that caused this exception
     */
    public DuplicateResourceException(String resourceType, String fieldName, String fieldValue, Throwable cause) {
        super(formatMessage(resourceType, fieldName, fieldValue), cause);
        this.resourceType = resourceType;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    /**
     * Formats a consistent error message for duplicate resource violations.
     * 
     * <p>This method generates user-friendly error messages matching the style of
     * the original COBOL messages (e.g., "User ID already exist...").</p>
     * 
     * @param resourceType the type of resource
     * @param fieldName the field name with the duplicate
     * @param fieldValue the duplicate value
     * @return formatted error message
     */
    private static String formatMessage(String resourceType, String fieldName, String fieldValue) {
        if (resourceType == null || fieldName == null || fieldValue == null) {
            return "Duplicate resource detected";
        }
        return String.format("%s with %s '%s' already exists", resourceType, fieldName, fieldValue);
    }

    /**
     * Returns the type of resource that has the duplicate.
     * 
     * @return the resource type (e.g., "User", "Account", "Card"), or {@code null} if not set
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Returns the field name that has the duplicate value.
     * 
     * @return the field name (e.g., "username", "account number"), or {@code null} if not set
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Returns the actual duplicate value that caused the constraint violation.
     * 
     * @return the duplicate value, or {@code null} if not set
     */
    public String getFieldValue() {
        return fieldValue;
    }

    /**
     * Returns a string representation of this exception including resource details.
     * 
     * @return string representation with resource context
     */
    @Override
    public String toString() {
        if (resourceType != null && fieldName != null && fieldValue != null) {
            return String.format("DuplicateResourceException: %s with %s '%s' already exists",
                    resourceType, fieldName, fieldValue);
        }
        return super.toString();
    }
}
