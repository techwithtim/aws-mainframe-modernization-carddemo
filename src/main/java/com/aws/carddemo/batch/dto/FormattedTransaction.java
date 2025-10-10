/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.batch.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * Data Transfer Object representing a single formatted transaction line for monthly account statement display.
 * 
 * <p>This DTO is produced by StatementProcessor during statement generation batch jobs and serves as an element
 * of the StatementData transactionList field. All fields are pre-formatted as strings ready for direct rendering
 * in Thymeleaf HTML or iText PDF templates without additional formatting logic required in the template layer.</p>
 * 
 * <p><strong>COBOL Source Mapping:</strong></p>
 * <pre>
 * Migrated from: app/cbl/CBSTM03A.CBL
 * COBOL Structure: STATEMENT-LINES copybook ST-LINE13, ST-LINE14
 * 
 * COBOL Definition (lines 132-137):
 *   05  ST-LINE14.
 *       10  ST-TRANID        PIC X(16).     → referenceNumber
 *       10  FILLER           VALUE ' '      PIC X(01).
 *       10  ST-TRANDT        PIC X(49).     → description
 *       10  FILLER           VALUE '$'      PIC X(01).
 *       10  ST-TRANAMT       PIC Z(9).99-.  → formattedAmount
 * 
 * Transaction date formatting is derived from TRNX-RECORD processing timestamp.
 * </pre>
 * 
 * <p><strong>Field Specifications:</strong></p>
 * <ul>
 *   <li><strong>transactionDate:</strong> Pre-formatted date string in MM/DD/YYYY format using
 *       DateTimeFormatter.ofPattern("MM/dd/yyyy"), matching COBOL date display conventions.
 *       Formatted from Transaction.processingTimestamp LocalDateTime field by StatementProcessor.</li>
 *   
 *   <li><strong>description:</strong> Merchant name for purchase transactions or transaction type description
 *       for system transactions (e.g., "Monthly interest charge", "Payment received"). Limited to 40 characters
 *       with truncation applied using substring(0, Math.min(40, description.length())) to preserve COBOL
 *       PIC X(40) field constraint from ST-TRANDT field. Ensures proper alignment in fixed-width statement layouts.</li>
 *   
 *   <li><strong>referenceNumber:</strong> Transaction ID formatted as 16-character alphanumeric string matching
 *       COBOL FD-TRANS-ID PIC X(16) format from ST-TRANID field. Zero-padded if necessary to maintain fixed
 *       16-character width for consistent statement line alignment.</li>
 *   
 *   <li><strong>formattedAmount:</strong> Pre-formatted currency string with $ symbol, comma thousands separators,
 *       and 2 decimal places using NumberFormat.getCurrencyInstance(Locale.US).format(amount). Example: "$1,234.56".
 *       Matches COBOL CURRENCY-EDITED format with PIC $Z,ZZZ,ZZ9.99 pattern from ST-TRANAMT field.
 *       Ready for direct template rendering without additional formatting.</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * FormattedTransaction transaction = new FormattedTransaction(
 *     "12/15/2024",                          // transactionDate
 *     "AMAZON.COM MARKETPLACE",              // description
 *     "TX0123456789ABCD",                    // referenceNumber
 *     "$1,234.56"                            // formattedAmount
 * );
 * 
 * // In Thymeleaf template:
 * // &lt;td th:text="${transaction.transactionDate()}"&gt;&lt;/td&gt;
 * // &lt;td th:text="${transaction.description()}"&gt;&lt;/td&gt;
 * // &lt;td th:text="${transaction.referenceNumber()}"&gt;&lt;/td&gt;
 * // &lt;td th:text="${transaction.formattedAmount()}"&gt;&lt;/td&gt;
 * </pre>
 * 
 * <p><strong>Validation:</strong></p>
 * <p>Bean Validation annotations ensure all fields are non-blank for fail-fast validation during Spring Batch
 * chunk processing. Empty or null values will cause validation errors preventing statement generation with
 * incomplete transaction data.</p>
 * 
 * <p><strong>Serialization:</strong></p>
 * <p>Implements Serializable to support Spring Batch chunk serialization when formatted transaction records
 * are part of StatementData DTO passed between StatementProcessor and StatementWriter components, particularly
 * in remote partitioning or distributed batch processing scenarios.</p>
 * 
 * @param transactionDate Pre-formatted transaction date in MM/DD/YYYY format (e.g., "12/15/2024")
 * @param description Transaction description limited to 40 characters with truncation
 * @param referenceNumber 16-character transaction ID with zero-padding
 * @param formattedAmount Pre-formatted currency amount with $ symbol (e.g., "$1,234.56")
 * 
 * @see com.aws.carddemo.batch.processor.StatementProcessor
 * @see com.aws.carddemo.batch.dto.StatementData
 * @since 1.0.0
 */
public record FormattedTransaction(
    
    @NotBlank(message = "Transaction date cannot be blank")
    String transactionDate,
    
    @NotBlank(message = "Transaction description cannot be blank")
    String description,
    
    @NotBlank(message = "Transaction reference number cannot be blank")
    String referenceNumber,
    
    @NotBlank(message = "Transaction formatted amount cannot be blank")
    String formattedAmount
    
) implements Serializable {
    
    /**
     * Serial version UID for serialization compatibility.
     * Updated when record structure changes to maintain version control.
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * Maximum length for transaction description field matching COBOL PIC X(40) constraint.
     * Descriptions exceeding this length must be truncated before constructing FormattedTransaction.
     */
    public static final int MAX_DESCRIPTION_LENGTH = 40;
    
    /**
     * Fixed length for transaction reference number matching COBOL PIC X(16) constraint.
     * Reference numbers shorter than this length must be zero-padded.
     */
    public static final int REFERENCE_NUMBER_LENGTH = 16;
    
    /**
     * Expected date format pattern MM/DD/YYYY for transactionDate field.
     * Matches COBOL date display format conventions.
     */
    public static final String DATE_FORMAT_PATTERN = "MM/dd/yyyy";
    
    /**
     * Compact constructor with validation logic.
     * Automatically invoked by Java record canonical constructor.
     * 
     * <p>Performs additional validation beyond Bean Validation annotations
     * to ensure data integrity constraints are met.</p>
     * 
     * @throws IllegalArgumentException if field length constraints are violated
     */
    public FormattedTransaction {
        // Validate field is not null or blank (Bean Validation will also check)
        if (transactionDate == null || transactionDate.isBlank()) {
            throw new IllegalArgumentException("Transaction date cannot be null or blank");
        }
        
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Transaction description cannot be null or blank");
        }
        
        if (referenceNumber == null || referenceNumber.isBlank()) {
            throw new IllegalArgumentException("Transaction reference number cannot be null or blank");
        }
        
        if (formattedAmount == null || formattedAmount.isBlank()) {
            throw new IllegalArgumentException("Transaction formatted amount cannot be null or blank");
        }
        
        // Validate length constraints matching COBOL field definitions
        if (transactionDate.length() != 10) {
            throw new IllegalArgumentException(
                String.format("Transaction date must be exactly 10 characters (MM/DD/YYYY format), got: %d", 
                    transactionDate.length())
            );
        }
        
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Transaction description exceeds maximum length of %d characters, got: %d",
                    MAX_DESCRIPTION_LENGTH, description.length())
            );
        }
        
        if (referenceNumber.length() != REFERENCE_NUMBER_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Transaction reference number must be exactly %d characters, got: %d",
                    REFERENCE_NUMBER_LENGTH, referenceNumber.length())
            );
        }
        
        // Validate formatted amount starts with currency symbol
        if (!formattedAmount.startsWith("$") && !formattedAmount.startsWith("-$")) {
            throw new IllegalArgumentException(
                "Transaction formatted amount must start with $ symbol (or -$ for negative amounts)"
            );
        }
    }
    
    /**
     * Accessor method for transactionDate field.
     * Java records automatically generate this method.
     * 
     * @return Pre-formatted transaction date in MM/DD/YYYY format
     */
    // transactionDate() - auto-generated by record
    
    /**
     * Accessor method for description field.
     * Java records automatically generate this method.
     * 
     * @return Transaction description limited to 40 characters
     */
    // description() - auto-generated by record
    
    /**
     * Accessor method for referenceNumber field.
     * Java records automatically generate this method.
     * 
     * @return 16-character transaction reference number
     */
    // referenceNumber() - auto-generated by record
    
    /**
     * Accessor method for formattedAmount field.
     * Java records automatically generate this method.
     * 
     * @return Pre-formatted currency amount string with $ symbol
     */
    // formattedAmount() - auto-generated by record
    
    /**
     * JavaBean-style getter for transactionDate to support frameworks expecting JavaBean conventions.
     * Delegates to record accessor method transactionDate().
     * 
     * @return Pre-formatted transaction date in MM/DD/YYYY format
     */
    public String getTransactionDate() {
        return transactionDate();
    }
    
    /**
     * JavaBean-style getter for description to support frameworks expecting JavaBean conventions.
     * Delegates to record accessor method description().
     * 
     * @return Transaction description limited to 40 characters
     */
    public String getDescription() {
        return description();
    }
    
    /**
     * JavaBean-style getter for referenceNumber to support frameworks expecting JavaBean conventions.
     * Delegates to record accessor method referenceNumber().
     * 
     * @return 16-character transaction reference number
     */
    public String getReferenceNumber() {
        return referenceNumber();
    }
    
    /**
     * JavaBean-style getter for formattedAmount to support frameworks expecting JavaBean conventions.
     * Delegates to record accessor method formattedAmount().
     * 
     * @return Pre-formatted currency amount string with $ symbol
     */
    public String getFormattedAmount() {
        return formattedAmount();
    }
}
