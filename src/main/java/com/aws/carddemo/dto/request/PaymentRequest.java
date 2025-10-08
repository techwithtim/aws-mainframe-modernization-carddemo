/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payment Request DTO for bill payment transaction processing.
 * 
 * <p>Migrated from: app/bms/COBIL00.bms (Bill Payment screen)
 * 
 * <p>This DTO replaces the CICS BMS screen COBIL00.bms for bill payment operations.
 * It captures payment transaction details submitted via POST /api/v1/accounts/{id}/payments
 * endpoint, providing RESTful equivalent to the legacy 3270 terminal screen interaction.
 * 
 * <p>Field Mappings from COBIL00.bms:
 * <ul>
 *   <li>ACTIDIN (POS=(6,21) LENGTH=11 ATTRB=UNPROT) → accountId (Long)</li>
 *   <li>CONFIRM (POS=(15,60) LENGTH=1 ATTRB=UNPROT) → confirmationFlag (String)</li>
 *   <li>CURBAL (display field) → paymentAmount (BigDecimal, user-specified amount)</li>
 * </ul>
 * 
 * <p>Business Logic Preserved:
 * The COBOL program COBIL00C.cbl validates account existence, displays current balance,
 * and processes payment confirmation. This DTO enforces the same validation rules through
 * Bean Validation annotations, ensuring data integrity before payment processing.
 * 
 * <p>Validation Rules:
 * <ul>
 *   <li>Account ID must be a positive Long value (11 digits max in COBOL)</li>
 *   <li>Payment amount must be positive with max 9 integer digits and 2 decimal places
 *       (COBOL PIC S9(09)V99 COMP-3 precision)</li>
 *   <li>Payment date must be today or earlier (prevent future-dated payments)</li>
 *   <li>Confirmation flag must be exactly 'Y' or 'N' (matches COBOL Y/N prompt)</li>
 * </ul>
 * 
 * <p>Security Considerations:
 * Account identifiers are validated but not masked in logs (not PII). Payment amounts
 * are logged for audit purposes. This DTO coordinates with PaymentService.processPayment()
 * which performs authorization checks and account balance validations.
 * 
 * @see com.aws.carddemo.service.PaymentService#processPayment(PaymentRequest)
 * @see com.aws.carddemo.controller.PaymentController
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    /**
     * Account identifier for the payment transaction.
     * 
     * <p>Mapped from: COBIL00.bms ACTIDIN field (LENGTH=11, ATTRB=UNPROT)
     * 
     * <p>Original COBOL: PIC 9(11) representing 11-digit account ID
     * 
     * <p>Validation: Must be a positive Long value. The COBOL program validates account
     * existence by reading ACCTFILE with ACCT-ID as the key. Invalid account IDs result
     * in "Account not found" error messages displayed in ERRMSG field.
     * 
     * <p>Example: 1234567890 (11-digit account identifier)
     */
    @NotNull(message = "Account ID is required")
    @Positive(message = "Account ID must be a positive number")
    @JsonProperty("accountId")
    private Long accountId;

    /**
     * Payment amount in USD with exact decimal precision.
     * 
     * <p>Mapped from: Payment amount implied by COBIL00C.cbl payment processing logic.
     * The BMS screen displays CURBAL (current balance) at POS=(11,32) LENGTH=14, and
     * the COBOL program processes payment amounts using TRAN-AMT field from transaction
     * copybook (CVTRA05Y.cpy PIC S9(09)V99 COMP-3).
     * 
     * <p>Original COBOL: PIC S9(09)V99 COMP-3 (signed packed decimal, 9 integer digits,
     * 2 decimal places) used for ACCT-CURR-BAL in CVACT01Y.cpy and TRAN-AMT in CVTRA05Y.cpy
     * 
     * <p>Validation: Must be at least $0.01, maximum 9 integer digits and exactly 2 decimal
     * places. This preserves COBOL packed decimal precision and prevents floating-point
     * rounding errors in financial calculations per Agent Action Plan data type mapping
     * standards.
     * 
     * <p>Example: 1250.75 (represents $1,250.75)
     */
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be at least 0.01")
    @Digits(integer = 9, fraction = 2, message = "Payment amount must have at most 9 integer digits and 2 decimal places")
    @JsonProperty("paymentAmount")
    private BigDecimal paymentAmount;

    /**
     * Payment transaction date in YYYY-MM-DD format.
     * 
     * <p>Mapped from: COBIL00C.cbl GET-CURRENT-TIMESTAMP paragraph which executes
     * EXEC CICS FORMATTIME YYYYMMDD with DATESEP('-') to produce WS-CUR-DATE-X10
     * (PIC X(10) format YYYY-MM-DD) for TRAN-ORIG-TS and TRAN-PROC-TS timestamp fields.
     * 
     * <p>Original COBOL: WS-CUR-DATE-X10 PIC X(10) representing date in YYYY-MM-DD format
     * 
     * <p>Validation: Must be today or earlier (PastOrPresent constraint). This prevents
     * users from scheduling future-dated payments, matching COBOL behavior which uses
     * current system date for transaction timestamps.
     * 
     * <p>Example: 2024-01-15 (ISO 8601 date format)
     */
    @NotNull(message = "Payment date is required")
    @PastOrPresent(message = "Payment date cannot be in the future")
    @JsonProperty("paymentDate")
    private LocalDate paymentDate;

    /**
     * Payment confirmation flag indicating user consent.
     * 
     * <p>Mapped from: COBIL00.bms CONFIRM field (POS=(15,60) LENGTH=1 ATTRB=UNPROT)
     * with prompt text "Do you want to pay your balance now. Please confirm: (Y/N)"
     * 
     * <p>Original COBOL: PIC X(01) with validation logic in COBIL00C.cbl checking for
     * 'Y' or 'N' values. Invalid values trigger error message "Please enter Y or N"
     * displayed in ERRMSG field at POS=(23,1).
     * 
     * <p>Validation: Must be exactly 'Y' (yes, proceed with payment) or 'N' (no, cancel).
     * The Pattern annotation enforces single-character Y/N values matching COBOL validation.
     * 
     * <p>Business Logic: When confirmationFlag='Y', PaymentService processes the payment
     * by creating a transaction record, updating account balance, and returning success
     * confirmation. When confirmationFlag='N', the service returns without processing.
     * 
     * <p>Example: "Y" (user confirms payment), "N" (user cancels)
     */
    @NotBlank(message = "Confirmation flag is required")
    @Pattern(regexp = "[YN]", message = "Confirmation flag must be Y or N")
    @JsonProperty("confirmationFlag")
    private String confirmationFlag;
}
