/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.batch.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) representing a validated and enriched transaction
 * processed by the TransactionProcessor in Spring Batch chunk-oriented processing.
 * 
 * <p>This immutable record serves as the data carrier between Spring Batch components
 * in the transaction posting batch job pipeline:
 * <ul>
 *   <li><strong>DailyTransactionReader</strong> (ItemReader) - Reads raw DailyTransaction records from database</li>
 *   <li><strong>TransactionProcessor</strong> (ItemProcessor) - Validates card number via CardXrefRepository,
 *       validates account existence, applies business rules (balance checks, transaction limits),
 *       enriches data with resolved accountId, and produces ProcessedTransaction with validation status</li>
 *   <li><strong>Spring Batch Chunk Transaction Boundary</strong> - Passes validated DTOs through transaction commit/rollback</li>
 *   <li><strong>TransactionWriter</strong> (ItemWriter) - Filters APPROVED records, inserts to Transaction table,
 *       updates Account.currentBalance, updates TransactionCategoryBalance, marks DailyTransaction.processed=true</li>
 * </ul>
 * 
 * <p><strong>Migrated from COBOL batch programs:</strong>
 * <ul>
 *   <li>app/cbl/CBTRN01C.cbl - Daily transaction file processing and validation logic</li>
 *   <li>app/cbl/CBTRN02C.cbl - Transaction posting with account and category balance updates</li>
 * </ul>
 * 
 * <p><strong>COBOL Validation Patterns Preserved:</strong>
 * <ul>
 *   <li>Card number lookup via XREF-FILE (2000-LOOKUP-XREF) → INVALID_CARD error if not found</li>
 *   <li>Account validation via ACCOUNT-FILE (3000-READ-ACCOUNT) → INVALID_ACCOUNT error if not found</li>
 *   <li>Balance and limit checks → OVER_LIMIT error for insufficient funds or credit limit violations</li>
 * </ul>
 * 
 * <p><strong>Data Precision Requirements:</strong>
 * The {@code amount} field uses {@link BigDecimal} with scale 2 to preserve
 * COBOL PIC S9(09)V99 COMP-3 packed decimal precision, ensuring exact financial
 * calculations without floating-point rounding errors in transaction posting operations.
 * All monetary operations must use {@code setScale(2, RoundingMode.HALF_UP)} to maintain
 * consistency with the legacy COBOL mainframe system.
 * 
 * <p><strong>Validation Status Flow:</strong>
 * <ul>
 *   <li><strong>APPROVED</strong> - Transaction passed all validations, ready for posting to Transaction table</li>
 *   <li><strong>DECLINED</strong> - Business rule violation (e.g., OVER_LIMIT, INVALID_CARD), logged for audit</li>
 *   <li><strong>ERROR</strong> - System/technical error during processing (e.g., database connectivity, data corruption)</li>
 * </ul>
 * 
 * <p><strong>Serialization Support:</strong>
 * Implements {@link Serializable} to support Spring Batch chunk serialization when DTOs are
 * passed between reader, processor, and writer components across JVM boundaries in remote
 * partitioning or async step execution scenarios.
 * 
 * <p><strong>Immutability Contract:</strong>
 * This record type provides built-in immutability with final fields, ensuring thread-safety
 * for concurrent batch processing and preventing unintended side effects during the
 * Spring Batch chunk processing lifecycle.
 * 
 * @param status The validation outcome of the transaction processing (APPROVED, DECLINED, ERROR).
 *               Must not be null. Determines whether TransactionWriter posts the transaction.
 * @param accountId The resolved account identifier from CardXref lookup. Must not be null.
 *                  References Account.accountId primary key for balance updates.
 * @param transactionId The unique transaction identifier from DailyTransaction.transactionId field
 *                      (PIC X(16)). Must not be null. Serves as primary key for Transaction table.
 * @param amount The transaction monetary value with scale 2 preserving COBOL PIC S9(09)V99 COMP-3
 *               packed decimal precision. Must not be null. Used for account balance calculations.
 * @param transactionTypeCode The 2-character transaction type code (e.g., '01' Purchase,
 *                            '02' Cash Advance) from TransactionType reference table.
 * @param transactionCategoryCode The 4-character transaction category code (e.g., '0001' Grocery,
 *                                '0002' Gas) from TransactionCategory reference table.
 * @param transactionTimestamp The processing timestamp using LocalDateTime.now() matching COBOL
 *                             Z-GET-DB2-FORMAT-TIMESTAMP pattern. Records when transaction was processed.
 * @param merchantId The merchant identifier from DailyTransaction input. Optional.
 * @param merchantName The merchant name from DailyTransaction input. Optional.
 * @param merchantCity The merchant city from DailyTransaction input. Optional.
 * @param merchantZip The merchant ZIP code from DailyTransaction input. Optional.
 * @param errorCode The error code populated for DECLINED/ERROR status (e.g., 'INVALID_CARD',
 *                  'OVER_LIMIT', 'INVALID_ACCOUNT'). Null for APPROVED status.
 * @param errorMessage The human-readable error description for rejected transactions. Null for APPROVED status.
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2024-01-01
 * 
 * @see com.aws.carddemo.batch.processor.TransactionProcessor
 * @see com.aws.carddemo.batch.writer.TransactionWriter
 * @see com.aws.carddemo.batch.reader.DailyTransactionReader
 */
public record ProcessedTransaction(
    @NotNull(message = "Processing status must not be null")
    ProcessingStatus status,
    
    @NotNull(message = "Account ID must not be null")
    Long accountId,
    
    @NotNull(message = "Transaction ID must not be null")
    String transactionId,
    
    @NotNull(message = "Transaction amount must not be null")
    BigDecimal amount,
    
    String transactionTypeCode,
    String transactionCategoryCode,
    LocalDateTime transactionTimestamp,
    String merchantId,
    String merchantName,
    String merchantCity,
    String merchantZip,
    String errorCode,
    String errorMessage
) implements Serializable {
    
    /**
     * Serial version UID for Serializable interface compatibility.
     * Used by Spring Batch for chunk serialization in distributed processing scenarios.
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * Enumeration representing the validation outcome of transaction processing
     * in the Spring Batch TransactionProcessor ItemProcessor.
     * 
     * <p>This enum captures the three possible states of a processed transaction,
     * determining how the TransactionWriter ItemWriter handles the record:
     * <ul>
     *   <li><strong>APPROVED</strong> - Transaction passed all validation checks:
     *       <ul>
     *         <li>Card number successfully resolved to account ID via CardXrefRepository</li>
     *         <li>Account exists and is active in AccountRepository</li>
     *         <li>Transaction amount does not exceed account balance (for debits)</li>
     *         <li>Transaction amount does not exceed credit limit (for credit accounts)</li>
     *         <li>Transaction type and category codes are valid references</li>
     *       </ul>
     *       Result: TransactionWriter posts transaction to Transaction table, updates Account.currentBalance,
     *       updates TransactionCategoryBalance, marks DailyTransaction.processed=true
     *   </li>
     *   <li><strong>DECLINED</strong> - Business rule violation detected:
     *       <ul>
     *         <li>INVALID_CARD - Card number not found in CardXref table (COBOL: WS-XREF-READ-STATUS = 4)</li>
     *         <li>INVALID_ACCOUNT - Account ID not found in Account table (COBOL: WS-ACCT-READ-STATUS = 4)</li>
     *         <li>OVER_LIMIT - Transaction amount exceeds available balance or credit limit</li>
     *         <li>INVALID_TYPE - Transaction type code not found in TransactionType reference table</li>
     *         <li>INVALID_CATEGORY - Transaction category code not found in TransactionCategory reference table</li>
     *       </ul>
     *       Result: TransactionWriter logs declined transaction for audit, does NOT post to Transaction table,
     *       marks DailyTransaction.processed=false with rejection reason
     *   </li>
     *   <li><strong>ERROR</strong> - System/technical error during processing:
     *       <ul>
     *         <li>Database connectivity failure</li>
     *         <li>Data corruption or constraint violation</li>
     *         <li>Unexpected runtime exception</li>
     *       </ul>
     *       Result: TransactionWriter skips transaction, Spring Batch retry/skip policies apply,
     *       error logged for technical investigation
     *   </li>
     * </ul>
     * 
     * <p><strong>COBOL Equivalence:</strong>
     * This enum replaces COBOL file status checking patterns:
     * <pre>
     * IF WS-XREF-READ-STATUS = 0
     *    IF WS-ACCT-READ-STATUS = 0
     *       [Post transaction - equivalent to APPROVED]
     *    ELSE
     *       [Account not found - equivalent to DECLINED with INVALID_ACCOUNT]
     *    END-IF
     * ELSE
     *    [Card not found - equivalent to DECLINED with INVALID_CARD]
     * END-IF
     * </pre>
     * 
     * @see ProcessedTransaction
     */
    public enum ProcessingStatus {
        /**
         * Transaction passed all validation checks and is ready for posting to the Transaction table.
         * TransactionWriter will insert transaction record, update account balance, and update category balances.
         */
        APPROVED,
        
        /**
         * Transaction failed business rule validation (e.g., invalid card, insufficient funds).
         * TransactionWriter will log rejection reason but will NOT post transaction to database.
         */
        DECLINED,
        
        /**
         * System/technical error occurred during processing (e.g., database connectivity failure).
         * TransactionWriter will skip transaction, Spring Batch retry/skip policies will apply.
         */
        ERROR
    }
}
