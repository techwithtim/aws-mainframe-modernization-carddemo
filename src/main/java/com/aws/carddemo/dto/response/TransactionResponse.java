/*
 * TransactionResponse.java
 * 
 * Response DTO for individual transaction inquiry operations.
 * Migrated from: app/bms/COTRN01.bms, app/cpy/CVTRA05Y.cpy
 * 
 * This DTO maps Transaction entity data to JSON API responses for 
 * GET /api/v1/transactions/{id} endpoint, replacing COTRN01.bms 
 * transaction detail screen output with Jackson-serialized JSON structure.
 * 
 * PCI-DSS Compliance:
 * - Card numbers are masked showing only last 4 digits (************1234)
 * - Raw card numbers are never included in JSON responses per PCI-DSS 3.3
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.carddemo.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for transaction detail view operations.
 * 
 * Maps CVTRA05Y.cpy transaction record fields to JSON response structure
 * with appropriate data type conversions and PCI-DSS compliant masking.
 * 
 * Field Mapping:
 * <ul>
 *   <li>TRAN-ID (PIC X(16)) → transactionId (Long) + transactionNumber (String)</li>
 *   <li>TRAN-TYPE-CD (PIC X(02)) → transactionTypeCode (String)</li>
 *   <li>TRAN-CAT-CD (PIC 9(04)) → transactionCategoryCode (String)</li>
 *   <li>TRAN-SOURCE (PIC X(10)) → transactionSource (String)</li>
 *   <li>TRAN-DESC (PIC X(100)) → description (String)</li>
 *   <li>TRAN-AMT (PIC S9(09)V99) → amount (BigDecimal with 9,2 precision)</li>
 *   <li>TRAN-MERCHANT-ID (PIC 9(09)) → merchantId (String)</li>
 *   <li>TRAN-MERCHANT-NAME (PIC X(50)) → merchantName (String)</li>
 *   <li>TRAN-MERCHANT-CITY (PIC X(50)) → merchantCity (String)</li>
 *   <li>TRAN-MERCHANT-ZIP (PIC X(10)) → merchantZip (String)</li>
 *   <li>TRAN-CARD-NUM (PIC X(16)) → cardNumberMasked (String with ************1234 format)</li>
 *   <li>TRAN-ORIG-TS (PIC X(26)) → originalTimestamp (LocalDateTime ISO 8601)</li>
 *   <li>TRAN-PROC-TS (PIC X(26)) → processingTimestamp (LocalDateTime ISO 8601)</li>
 * </ul>
 * 
 * Usage Example:
 * <pre>
 * TransactionResponse response = TransactionResponse.builder()
 *     .transactionId(1000000000000001L)
 *     .transactionNumber("1000000000000001")
 *     .transactionTypeCode("DB")
 *     .transactionCategoryCode("5010")
 *     .transactionTypeDescription("Purchase")
 *     .transactionCategoryDescription("Grocery Stores")
 *     .amount(new BigDecimal("125.50"))
 *     .cardNumberMasked("************4321")
 *     .build();
 * </pre>
 * 
 * @see com.aws.carddemo.model.Transaction
 * @see com.aws.carddemo.mapper.TransactionMapper
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    /**
     * Unique transaction identifier (database primary key).
     * Maps to Transaction.id field.
     */
    @JsonProperty("transaction_id")
    @NotNull(message = "Transaction ID is required")
    private Long transactionId;

    /**
     * Transaction number string representation.
     * Maps to TRAN-ID (PIC X(16)) from CVTRA05Y.cpy.
     * Used for display and reference purposes.
     */
    @JsonProperty("transaction_number")
    @NotNull(message = "Transaction number is required")
    private String transactionNumber;

    /**
     * Transaction type code (2 characters).
     * Maps to TRAN-TYPE-CD (PIC X(02)) from CVTRA05Y.cpy.
     * Examples: "DB" (Debit/Purchase), "CR" (Credit/Refund), "PM" (Payment)
     * 
     * Displayed on COTRN01.bms screen as TTYPCD field.
     */
    @JsonProperty("transaction_type_code")
    @NotNull(message = "Transaction type code is required")
    private String transactionTypeCode;

    /**
     * Transaction category code (4 digits).
     * Maps to TRAN-CAT-CD (PIC 9(04)) from CVTRA05Y.cpy.
     * Examples: "5010" (Grocery), "5411" (Restaurant), "4900" (Utilities)
     * 
     * Displayed on COTRN01.bms screen as TCATCD field.
     */
    @JsonProperty("transaction_category_code")
    @NotNull(message = "Transaction category code is required")
    private String transactionCategoryCode;

    /**
     * Transaction type description for display.
     * Joined from TransactionType entity based on transactionTypeCode.
     * Not stored in CVTRA05Y.cpy but derived from reference data.
     */
    @JsonProperty("transaction_type_description")
    private String transactionTypeDescription;

    /**
     * Transaction category description for display.
     * Joined from TransactionCategory entity based on transactionCategoryCode.
     * Not stored in CVTRA05Y.cpy but derived from reference data.
     */
    @JsonProperty("transaction_category_description")
    private String transactionCategoryDescription;

    /**
     * Transaction source/origin (10 characters).
     * Maps to TRAN-SOURCE (PIC X(10)) from CVTRA05Y.cpy.
     * Examples: "POS" (Point of Sale), "ONLINE", "ATM", "PHONE"
     * 
     * Displayed on COTRN01.bms screen as TRNSRC field.
     */
    @JsonProperty("transaction_source")
    @NotNull(message = "Transaction source is required")
    private String transactionSource;

    /**
     * Transaction description (up to 100 characters).
     * Maps to TRAN-DESC (PIC X(100)) from CVTRA05Y.cpy.
     * Provides human-readable description of the transaction.
     * 
     * Displayed on COTRN01.bms screen as TDESC field.
     */
    @JsonProperty("description")
    @NotNull(message = "Transaction description is required")
    private String description;

    /**
     * Transaction amount with exact decimal precision.
     * Maps to TRAN-AMT (PIC S9(09)V99) from CVTRA05Y.cpy.
     * Preserved as BigDecimal to maintain financial accuracy without floating-point errors.
     * 
     * Validation enforces NUMERIC(11,2) precision: 9 integer digits, 2 decimal places.
     * Displayed on COTRN01.bms screen as TRNAMT field (12 chars formatted with currency symbol).
     */
    @JsonProperty("amount")
    @NotNull(message = "Transaction amount is required")
    @Digits(integer = 9, fraction = 2, message = "Amount must have at most 9 integer digits and 2 decimal places")
    private BigDecimal amount;

    /**
     * Merchant identifier (9 digits).
     * Maps to TRAN-MERCHANT-ID (PIC 9(09)) from CVTRA05Y.cpy.
     * Null for non-merchant transactions (e.g., payments, transfers).
     * 
     * Displayed on COTRN01.bms screen as MID field.
     * Excluded from JSON when null per @JsonInclude(NON_NULL) class annotation.
     */
    @JsonProperty("merchant_id")
    private String merchantId;

    /**
     * Merchant name (up to 50 characters).
     * Maps to TRAN-MERCHANT-NAME (PIC X(50)) from CVTRA05Y.cpy.
     * Null for non-merchant transactions.
     * 
     * Displayed on COTRN01.bms screen as MNAME field.
     * Excluded from JSON when null per @JsonInclude(NON_NULL) class annotation.
     */
    @JsonProperty("merchant_name")
    private String merchantName;

    /**
     * Merchant city (up to 50 characters).
     * Maps to TRAN-MERCHANT-CITY (PIC X(50)) from CVTRA05Y.cpy.
     * Null for online transactions without physical merchant location.
     * 
     * Displayed on COTRN01.bms screen as MCITY field.
     * Excluded from JSON when null per @JsonInclude(NON_NULL) class annotation.
     */
    @JsonProperty("merchant_city")
    private String merchantCity;

    /**
     * Merchant ZIP/postal code (10 characters).
     * Maps to TRAN-MERCHANT-ZIP (PIC X(10)) from CVTRA05Y.cpy.
     * Null for online transactions without physical merchant location.
     * 
     * Displayed on COTRN01.bms screen as MZIP field.
     * Excluded from JSON when null per @JsonInclude(NON_NULL) class annotation.
     */
    @JsonProperty("merchant_zip")
    private String merchantZip;

    /**
     * Masked card number showing only last 4 digits (PCI-DSS 3.3 compliance).
     * Derived from TRAN-CARD-NUM (PIC X(16)) from CVTRA05Y.cpy.
     * Format: ************1234 (12 asterisks + last 4 digits)
     * 
     * PCI-DSS Requirement: Raw card numbers must never be exposed in logs or API responses.
     * Only the masked version is included in JSON responses.
     * 
     * Displayed on COTRN01.bms screen as CARDNUM field (may show full number in secure context).
     */
    @JsonProperty("card_number_masked")
    @NotNull(message = "Card number masked is required")
    private String cardNumberMasked;

    /**
     * Original transaction timestamp from merchant authorization.
     * Maps to TRAN-ORIG-TS (PIC X(26)) from CVTRA05Y.cpy.
     * Formatted as ISO 8601 date-time string: yyyy-MM-dd'T'HH:mm:ss
     * 
     * Displayed on COTRN01.bms screen as TORIGDT field (date portion only).
     */
    @JsonProperty("original_timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @NotNull(message = "Original timestamp is required")
    private LocalDateTime originalTimestamp;

    /**
     * System processing timestamp when transaction was posted to account.
     * Maps to TRAN-PROC-TS (PIC X(26)) from CVTRA05Y.cpy.
     * Formatted as ISO 8601 date-time string: yyyy-MM-dd'T'HH:mm:ss
     * 
     * Displayed on COTRN01.bms screen as TPROCDT field (date portion only).
     */
    @JsonProperty("processing_timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @NotNull(message = "Processing timestamp is required")
    private LocalDateTime processingTimestamp;

    /**
     * Associated account identifier for cross-reference navigation.
     * Not stored in CVTRA05Y.cpy but derived from Transaction entity relationships.
     * Allows API clients to navigate to account details via GET /api/v1/accounts/{accountId}
     */
    @JsonProperty("account_id")
    @NotNull(message = "Account ID is required")
    private Long accountId;
}
