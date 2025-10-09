/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payment response DTO containing payment confirmation details.
 * 
 * <p><b>Legacy Mapping:</b> Replaces COBOL BMS screen output fields from COBIL0A map 
 * (app/bms/COBIL00.bms) used by bill payment transaction processor COBIL00C.cbl.
 * 
 * <p><b>Response Fields Mapping:</b></p>
 * <ul>
 *   <li><b>confirmationNumber:</b> UUID generated for payment tracking (new field, not in COBOL)</li>
 *   <li><b>accountId:</b> Maps to ACCT-ID (COBOL PIC 9(11))</li>
 *   <li><b>accountNumber:</b> Maps to ACCT-NUM (COBOL PIC X(11))</li>
 *   <li><b>paymentAmount:</b> Maps to TRAN-AMT (COBOL PIC S9(09)V99 COMP-3)</li>
 *   <li><b>previousBalance:</b> Account balance before payment (COBOL ACCT-CURR-BAL before update)</li>
 *   <li><b>newBalance:</b> Account balance after payment (COBOL ACCT-CURR-BAL after COMPUTE)</li>
 *   <li><b>paymentDate:</b> Maps to TRAN-DATE (COBOL PIC X(10) YYYY-MM-DD)</li>
 *   <li><b>transactionId:</b> Maps to TRAN-ID (COBOL PIC 9(16))</li>
 *   <li><b>message:</b> Success message displayed to user (COBOL ERRMSG field)</li>
 * </ul>
 * 
 * <p><b>COBOL Success Message Example:</b></p>
 * <pre>
 * MOVE "Payment successful. Your Transaction ID is " TO ERRMSG
 * STRING TRAN-ID DELIMITED BY SIZE INTO ERRMSG WITH POINTER WS-PTR
 * EXEC CICS SEND MAP('COBIL0A') MAPSET('COBIL00') ERASE
 * </pre>
 * 
 * <p><b>JSON Response Example:</b></p>
 * <pre>
 * {
 *   "confirmationNumber": "550e8400-e29b-41d4-a716-446655440000",
 *   "accountId": 1234567890,
 *   "accountNumber": "1234567890",
 *   "paymentAmount": 1500.00,
 *   "previousBalance": 5000.00,
 *   "newBalance": 3500.00,
 *   "paymentDate": "2024-01-15",
 *   "transactionId": 987654321,
 *   "message": "Payment processed successfully"
 * }
 * </pre>
 * 
 * <p><b>Immutability:</b> Uses Lombok @Builder for immutable construction pattern, 
 * preventing accidental modification of response data after creation.
 * 
 * <p><b>Serialization:</b> Automatically serialized to JSON by Spring Boot's Jackson 
 * ObjectMapper with proper formatting (snake_case or camelCase based on configuration).
 * 
 * <p>Migrated from: app/bms/COBIL00.bms (BMS map COBIL0A output fields)
 * <p>Related COBOL: app/cbl/COBIL00C.cbl (lines 527-531 success message handling)
 * 
 * @since 1.0.0
 * @author AWS CardDemo Modernization Team
 */
@Data
@Builder
public class PaymentResponse {
    
    /**
     * Unique payment confirmation number (UUID) for tracking and reconciliation.
     * This is a new field added in the modernized system for better payment tracking.
     * Not present in original COBOL implementation.
     */
    private String confirmationNumber;
    
    /**
     * Account identifier (11-digit account ID from COBOL PIC 9(11)).
     * Maps to ACCT-ID in ACCTFILE record layout (CVACT01Y.cpy).
     */
    private Long accountId;
    
    /**
     * Account number string representation (11 characters from COBOL PIC X(11)).
     * Maps to ACCT-NUM in ACCTFILE record layout (CVACT01Y.cpy).
     */
    private String accountNumber;
    
    /**
     * Payment amount processed (BigDecimal with precision 11,2 from COBOL PIC S9(09)V99 COMP-3).
     * This is the amount deducted from the account balance.
     * Must be positive value with max 9 integer digits and 2 decimal places.
     */
    private BigDecimal paymentAmount;
    
    /**
     * Account balance before payment was processed (COBOL ACCT-CURR-BAL before COMPUTE).
     * Provided for user verification and reconciliation purposes.
     */
    private BigDecimal previousBalance;
    
    /**
     * Account balance after payment was processed (COBOL ACCT-CURR-BAL after COMPUTE).
     * Calculated as: newBalance = previousBalance - paymentAmount
     */
    private BigDecimal newBalance;
    
    /**
     * Payment effective date (ISO 8601 format YYYY-MM-DD from COBOL PIC X(10)).
     * Maps to TRAN-DATE in TRANSACT record layout (CVTRA05Y.cpy).
     * Must be today or earlier (no future-dated payments).
     */
    private LocalDate paymentDate;
    
    /**
     * Transaction record identifier (16-digit transaction ID from COBOL PIC 9(16)).
     * Maps to TRAN-ID in TRANSACT record layout (CVTRA05Y.cpy).
     * Used for payment tracking, audit trail, and transaction history queries.
     */
    private Long transactionId;
    
    /**
     * Success message displayed to user (maps to COBOL ERRMSG field in COBIL0A map).
     * Typical value: "Payment processed successfully"
     * Provides user-friendly confirmation text.
     */
    private String message;
}
