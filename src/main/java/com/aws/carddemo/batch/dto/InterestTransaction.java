/*
 * InterestTransaction.java
 *
 * Data Transfer Object (DTO) for interest transaction data transfer 
 * between Spring Batch components in the interest calculation job.
 *
 * Migrated from: app/cbl/CBACT04C.cbl (Interest Calculator Program)
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.batch.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable Data Transfer Object representing a calculated interest charge 
 * for Spring Batch chunk processing in the monthly interest calculation job.
 * 
 * <p><strong>Batch Processing Pipeline Flow:</strong></p>
 * <ol>
 *   <li><strong>InterestProcessor</strong> - Calculates monthly interest charges 
 *       using the formula: {@code balance × (APR / 1200)} with {@code RoundingMode.HALF_UP} 
 *       scale 2, preserving COBOL COMP-3 packed decimal precision from CBACT04C.cbl 
 *       (line 464-465: COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200).
 *       Wraps the calculated interest and account details into this DTO.</li>
 *   
 *   <li><strong>Spring Batch Framework</strong> - Passes the DTO through the chunk 
 *       transaction boundary with commit-interval processing, ensuring transactional 
 *       consistency for batch updates.</li>
 *   
 *   <li><strong>AccountWriter</strong> - Unpacks the DTO to:
 *       <ul>
 *         <li>Create a new Transaction entity with transactionTypeCode='07' 
 *             (Interest Charge) and the calculated interest amount</li>
 *         <li>Update Account.currentBalance by adding the interest charge</li>
 *         <li>Update Account.interestPaidYtd by adding to the year-to-date total</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><strong>COBOL Mapping:</strong></p>
 * <ul>
 *   <li>{@code accountId} - Maps to ACCT-ID (PIC 9(11)) from CBACT04C.cbl</li>
 *   <li>{@code calculatedInterest} - Maps to WS-MONTHLY-INT (PIC S9(09)V99 COMP-3)</li>
 *   <li>{@code transactionDate} - Maps to PARM-DATE (batch execution date)</li>
 *   <li>{@code yearToDateInterest} - Maps to WS-TOTAL-INT accumulated interest</li>
 * </ul>
 * 
 * <p>This record implements {@link Serializable} to support Spring Batch chunk 
 * serialization when using remote partitioning or async step execution patterns, 
 * enabling distributed batch processing scenarios where DTOs are passed between 
 * reader, processor, and writer components across JVM boundaries.</p>
 * 
 * <p><strong>Functional Equivalence:</strong> Maintains exact decimal precision 
 * using {@link BigDecimal} with scale 2, preserving the COBOL PIC S9(09)V99 COMP-3 
 * (packed decimal) format used in mainframe financial calculations. This ensures 
 * byte-for-byte accuracy in interest calculations when migrated from COBOL to Java.</p>
 * 
 * @param accountId The account identifier (primary key reference to Account entity),
 *                  corresponding to ACCT-ID in CBACT04C.cbl. Must not be null.
 * 
 * @param calculatedInterest The monthly interest amount calculated by InterestProcessor
 *                          using the formula: balance × (APR / 1200), with 
 *                          {@code RoundingMode.HALF_UP} and scale 2 for exact monetary
 *                          precision. Maps to WS-MONTHLY-INT in COBOL. Must not be null.
 * 
 * @param transactionDate The interest posting date, defaulting to the first day of the 
 *                       current month via {@code LocalDate.now().withDayOfMonth(1)}.
 *                       This matches the COBOL batch job execution date pattern where
 *                       interest is calculated and posted on the first of each month.
 *                       Must not be null.
 * 
 * @param yearToDateInterest The running total of interest charges for the account in 
 *                          the current calendar year, used to update the 
 *                          Account.interestPaidYtd field. Maps to WS-TOTAL-INT 
 *                          accumulated in CBACT04C.cbl (line 467: ADD WS-MONTHLY-INT 
 *                          TO WS-TOTAL-INT). Must not be null.
 * 
 * @see com.aws.carddemo.batch.processor.InterestProcessor
 * @see com.aws.carddemo.batch.writer.AccountWriter
 * @see com.aws.carddemo.batch.config.InterestCalculationJobConfig
 * 
 * @since 1.0.0
 * @version 1.0.0
 * @author AWS CardDemo Modernization Team
 */
public record InterestTransaction(
    @NotNull(message = "Account ID must not be null")
    Long accountId,
    
    @NotNull(message = "Calculated interest amount must not be null")
    BigDecimal calculatedInterest,
    
    @NotNull(message = "Transaction date must not be null")
    LocalDate transactionDate,
    
    @NotNull(message = "Year-to-date interest must not be null")
    BigDecimal yearToDateInterest
) implements Serializable {
    
    /**
     * Serial version UID for serialization compatibility.
     * Required for Spring Batch chunk serialization when using remote partitioning
     * or async step execution patterns.
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * Compact constructor for validation and default value handling.
     * 
     * <p>This constructor is invoked automatically by the canonical constructor
     * and provides an opportunity to validate inputs and apply defaults before
     * field initialization.</p>
     * 
     * <p>Default Behavior:</p>
     * <ul>
     *   <li>If {@code transactionDate} is null, defaults to the first day of the
     *       current month: {@code LocalDate.now().withDayOfMonth(1)}, matching
     *       the COBOL batch job pattern where interest is calculated monthly.</li>
     * </ul>
     * 
     * <p>The {@link NotNull} Bean Validation annotations ensure fail-fast validation
     * in the Spring Batch framework, preventing null pointer exceptions when 
     * AccountWriter processes interest transaction DTOs.</p>
     * 
     * @throws jakarta.validation.ValidationException if any @NotNull constraint is violated
     */
    public InterestTransaction {
        // Apply default for transactionDate if null
        // Default to first day of current month to match COBOL batch execution pattern
        if (transactionDate == null) {
            transactionDate = LocalDate.now().withDayOfMonth(1);
        }
        
        // Validation is handled by @NotNull annotations
        // Bean Validation will be triggered by Spring Batch framework
    }
    
    /**
     * Convenience constructor with automatic transaction date defaulting.
     * 
     * <p>This constructor allows creating an InterestTransaction without explicitly
     * providing a transaction date, which will default to the first day of the 
     * current month.</p>
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>
     * InterestTransaction transaction = new InterestTransaction(
     *     accountId,
     *     calculatedInterest,
     *     null, // Will default to first day of current month
     *     yearToDateInterest
     * );
     * </pre>
     * 
     * @param accountId The account identifier
     * @param calculatedInterest The monthly interest amount
     * @param yearToDateInterest The year-to-date interest total
     * @return A new InterestTransaction with transactionDate set to first of current month
     */
    public static InterestTransaction withDefaultDate(
            Long accountId,
            BigDecimal calculatedInterest,
            BigDecimal yearToDateInterest) {
        return new InterestTransaction(
            accountId,
            calculatedInterest,
            LocalDate.now().withDayOfMonth(1),
            yearToDateInterest
        );
    }
    
    /**
     * JavaBean-style getter for accountId to support frameworks expecting 
     * traditional getter/setter patterns.
     * 
     * <p>Note: Java Records automatically provide accessor methods named after
     * the field (e.g., {@code accountId()}), but some frameworks (like Spring Batch,
     * Jackson, MapStruct) may expect JavaBean-style {@code getXxx()} methods.
     * This method delegates to the canonical accessor for compatibility.</p>
     * 
     * @return The account identifier
     */
    public Long getAccountId() {
        return accountId;
    }
    
    /**
     * JavaBean-style getter for calculatedInterest to support frameworks expecting 
     * traditional getter/setter patterns.
     * 
     * @return The calculated monthly interest amount
     */
    public BigDecimal getCalculatedInterest() {
        return calculatedInterest;
    }
    
    /**
     * JavaBean-style getter for transactionDate to support frameworks expecting 
     * traditional getter/setter patterns.
     * 
     * @return The interest posting date
     */
    public LocalDate getTransactionDate() {
        return transactionDate;
    }
    
    /**
     * JavaBean-style getter for yearToDateInterest to support frameworks expecting 
     * traditional getter/setter patterns.
     * 
     * @return The year-to-date interest total
     */
    public BigDecimal getYearToDateInterest() {
        return yearToDateInterest;
    }
}
