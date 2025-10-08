/*
 * ResourceNotFoundException.java
 * 
 * Custom unchecked exception for entity lookup failures in the CardDemo application.
 * This exception is thrown when attempting to retrieve an entity (Account, Card, Transaction, 
 * Customer, or User) by ID or business key, but the entity is not found in the database.
 * 
 * Migrated from COBOL patterns:
 * - EXEC CICS HANDLE CONDITION NOTFND (CICS record not found handling)
 * - APPL-EOF condition checks (application-level end-of-file indicators)
 * - FILE STATUS '23' checks (VSAM record not found status code)
 * - ABEND-DATA copybook (app/cpy/CSMSG02Y.cpy) error structures
 * 
 * Source References:
 * - app/cbl/COACTVWC.cbl - Account view program with entity lookup
 * - app/cbl/COCRDLIC.cbl - Card list program with entity lookup
 * - app/cpy/CSMSG02Y.cpy - COBOL ABEND-DATA structure
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.carddemo.exception;

/**
 * Exception thrown when a requested resource (entity) cannot be found in the database.
 * 
 * <p>This exception replaces legacy COBOL error handling patterns:
 * <ul>
 *   <li><code>EXEC CICS HANDLE CONDITION NOTFND</code> - CICS record not found condition</li>
 *   <li><code>IF APPL-EOF = 'Y' PERFORM 9999-ABEND-PROGRAM</code> - Application EOF checks</li>
 *   <li><code>FILE STATUS checks for '23'</code> - VSAM record not found status</li>
 * </ul>
 * 
 * <p>This exception extends {@link RuntimeException} to enable automatic transaction rollback
 * when thrown from {@code @Transactional} Spring service methods. It is mapped to HTTP 404 
 * Not Found status in the {@code GlobalExceptionHandler} via {@code @ExceptionHandler}.
 * 
 * <p><strong>Usage Pattern:</strong>
 * <pre>
 * // JPA repository Optional handling
 * Account account = accountRepository.findById(accountId)
 *     .orElseThrow(() -&gt; new ResourceNotFoundException("Account", accountId));
 * 
 * // Custom query with business key
 * Card card = cardRepository.findByCardNumber(cardNumber)
 *     .orElseThrow(() -&gt; new ResourceNotFoundException("Card", "cardNumber", cardNumber));
 * </pre>
 * 
 * <p><strong>Error Message Format:</strong>
 * <ul>
 *   <li>Simple lookup: "Account with ID 12345 not found"</li>
 *   <li>Business key lookup: "Card with cardNumber 4000123456789010 not found"</li>
 * </ul>
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Serial version UID for serialization compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The type of resource that was not found (e.g., "Account", "Card", "Transaction").
     */
    private final String resourceType;

    /**
     * The name of the field used for lookup (e.g., "id", "accountNumber", "cardNumber").
     */
    private final String fieldName;

    /**
     * The value of the field used for lookup (e.g., 12345, "4000123456789010").
     */
    private final Object fieldValue;

    /**
     * Constructs a new ResourceNotFoundException with the specified resource type and identifier.
     * 
     * <p>This constructor is used for primary key lookups where the field name is implicitly "ID".
     * 
     * @param resourceType the type of resource that was not found (e.g., "Account", "Card")
     * @param id the identifier value used for the lookup
     * 
     * @example
     * <pre>
     * throw new ResourceNotFoundException("Account", 12345L);
     * // Message: "Account with ID 12345 not found"
     * </pre>
     */
    public ResourceNotFoundException(String resourceType, Object id) {
        this(resourceType, "ID", id);
    }

    /**
     * Constructs a new ResourceNotFoundException with the specified resource type, field name, 
     * and field value.
     * 
     * <p>This constructor is used for business key lookups where the field name is explicitly specified.
     * 
     * @param resourceType the type of resource that was not found (e.g., "Card", "User")
     * @param fieldName the name of the field used for lookup (e.g., "cardNumber", "username")
     * @param fieldValue the value of the field used for lookup
     * 
     * @example
     * <pre>
     * throw new ResourceNotFoundException("Card", "cardNumber", "4000123456789010");
     * // Message: "Card with cardNumber 4000123456789010 not found"
     * </pre>
     */
    public ResourceNotFoundException(String resourceType, String fieldName, Object fieldValue) {
        super(String.format("%s with %s %s not found", resourceType, fieldName, fieldValue));
        this.resourceType = resourceType;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    /**
     * Constructs a new ResourceNotFoundException with the specified detail message.
     * 
     * <p>This constructor is used for custom error messages that don't follow the standard format.
     * 
     * @param message the detail message explaining the exception
     * 
     * @example
     * <pre>
     * throw new ResourceNotFoundException("No active accounts found for customer");
     * </pre>
     */
    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.fieldName = null;
        this.fieldValue = null;
    }

    /**
     * Constructs a new ResourceNotFoundException with the specified detail message and cause.
     * 
     * <p>This constructor is used when wrapping another exception while providing additional context.
     * 
     * @param message the detail message explaining the exception
     * @param cause the cause of this exception (which is saved for later retrieval by {@link #getCause()})
     * 
     * @example
     * <pre>
     * try {
     *     // Database operation
     * } catch (DataAccessException e) {
     *     throw new ResourceNotFoundException("Failed to retrieve account", e);
     * }
     * </pre>
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.resourceType = null;
        this.fieldName = null;
        this.fieldValue = null;
    }

    /**
     * Constructs a new ResourceNotFoundException with the specified cause.
     * 
     * <p>This constructor is useful when the detail message can be derived from the cause.
     * 
     * @param cause the cause of this exception (which is saved for later retrieval by {@link #getCause()})
     */
    public ResourceNotFoundException(Throwable cause) {
        super(cause);
        this.resourceType = null;
        this.fieldName = null;
        this.fieldValue = null;
    }

    /**
     * Returns the type of resource that was not found.
     * 
     * @return the resource type (e.g., "Account", "Card"), or {@code null} if not specified
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Returns the name of the field used for lookup.
     * 
     * @return the field name (e.g., "ID", "cardNumber"), or {@code null} if not specified
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Returns the value of the field used for lookup.
     * 
     * @return the field value used in the failed lookup, or {@code null} if not specified
     */
    public Object getFieldValue() {
        return fieldValue;
    }

    /**
     * Returns a string representation of this exception including the resource type, field name, 
     * and field value if available.
     * 
     * @return a string representation of this exception
     */
    @Override
    public String toString() {
        if (resourceType != null && fieldName != null && fieldValue != null) {
            return String.format("ResourceNotFoundException[resourceType=%s, fieldName=%s, fieldValue=%s, message=%s]",
                    resourceType, fieldName, fieldValue, getMessage());
        }
        return super.toString();
    }
}
