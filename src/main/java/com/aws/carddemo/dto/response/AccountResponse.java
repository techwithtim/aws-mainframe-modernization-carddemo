/*
 * AccountResponse.java
 *
 * Response DTO for account inquiry operations.
 * Migrated from: app/bms/COACTVW.bms (Account Viewer Screen)
 * Data structure from: app/cpy/CVACT01Y.cpy (Account Record Layout)
 *
 * Maps Account entity data to JSON API responses for GET /api/v1/accounts/{id} endpoint.
 * Replaces COBOL EXEC CICS SEND MAP with ResponseEntity<AccountResponse> return values.
 * Provides functional equivalence with 3270 BMS screen output fields while adhering to
 * REST API best practices and PCI-DSS compliance requirements.
 *
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aws.carddemo.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Account Response DTO
 * 
 * Represents account details returned by account inquiry REST API endpoint.
 * This DTO replaces the COACTVW BMS map output fields with a JSON structure
 * suitable for modern REST API consumption.
 * 
 * Field Mappings from COBOL:
 * <ul>
 *   <li>ACCTSID (PIC 9(11)) → accountNumber (String with 11 digits)</li>
 *   <li>ACCT-ID (PIC 9(11)) → accountId (Long)</li>
 *   <li>ACSTTUS / ACCT-ACTIVE-STATUS (PIC X(01)) → activeStatus (String, Y/N)</li>
 *   <li>ACURBAL / ACCT-CURR-BAL (PIC S9(10)V99) → currentBalance (BigDecimal)</li>
 *   <li>ACRDLIM / ACCT-CREDIT-LIMIT (PIC S9(10)V99) → creditLimit (BigDecimal)</li>
 *   <li>ACSHLIM / ACCT-CASH-CREDIT-LIMIT (PIC S9(10)V99) → cashCreditLimit (BigDecimal)</li>
 *   <li>ACRCYCR / ACCT-CURR-CYC-CREDIT (PIC S9(10)V99) → currentCycleCredit (BigDecimal)</li>
 *   <li>ACRCYDB / ACCT-CURR-CYC-DEBIT (PIC S9(10)V99) → currentCycleDebit (BigDecimal)</li>
 *   <li>ADTOPEN / ACCT-OPEN-DATE (PIC X(10)) → openDate (LocalDate, ISO 8601 format)</li>
 *   <li>AEXPDT / ACCT-EXPIRAION-DATE (PIC X(10)) → expirationDate (LocalDate)</li>
 *   <li>AREISDT / ACCT-REISSUE-DATE (PIC X(10)) → reissueDate (LocalDate, nullable)</li>
 *   <li>ACSZIPC / ACCT-ADDR-ZIP (PIC X(10)) → addressZip (String)</li>
 *   <li>AADDGRP / ACCT-GROUP-ID (PIC X(10)) → groupId (String)</li>
 *   <li>ACSTNUM (customer ID) → customerId (Long)</li>
 *   <li>ACSFNAM (first name) → customerFirstName (String)</li>
 *   <li>ACSLNAM (last name) → customerLastName (String)</li>
 * </ul>
 * 
 * PCI-DSS Compliance:
 * - Account numbers are included in responses but should be masked in logs
 * - Sensitive customer data (SSN, DOB, FICO) excluded from this DTO
 * - Full customer details available through dedicated customer endpoints
 * 
 * Usage Example:
 * <pre>
 * AccountResponse response = AccountResponse.builder()
 *     .accountId(1000000001L)
 *     .accountNumber("10000000001")
 *     .activeStatus("Y")
 *     .currentBalance(new BigDecimal("1500.50"))
 *     .creditLimit(new BigDecimal("5000.00"))
 *     .build();
 * </pre>
 * 
 * @see com.aws.carddemo.model.Account
 * @see com.aws.carddemo.controller.AccountController
 * @see com.aws.carddemo.mapper.AccountMapper
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique account identifier (primary key).
     * Mapped from COBOL: ACCT-ID PIC 9(11)
     * Range: 1 to 99999999999
     */
    @JsonProperty("accountId")
    private Long accountId;

    /**
     * Account number displayed to users (11-digit string).
     * Mapped from COBOL: ACCTSID (BMS field), ACCT-ID (copybook field)
     * Format: 11 numeric digits (e.g., "10000000001")
     * 
     * Note: While not a PAN (Primary Account Number), this should be masked
     * in application logs for security best practices.
     */
    @JsonProperty("accountNumber")
    private String accountNumber;

    /**
     * Account active status indicator.
     * Mapped from COBOL: ACSTTUS / ACCT-ACTIVE-STATUS PIC X(01)
     * Values: "Y" (active), "N" (inactive)
     */
    @JsonProperty("activeStatus")
    private String activeStatus;

    /**
     * Current account balance with 2 decimal precision.
     * Mapped from COBOL: ACURBAL / ACCT-CURR-BAL PIC S9(10)V99 COMP-3
     * 
     * BigDecimal ensures precise financial calculations without floating-point
     * rounding errors. Maintains exact decimal arithmetic for monetary operations.
     */
    @JsonProperty("currentBalance")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal currentBalance;

    /**
     * Maximum credit limit for the account.
     * Mapped from COBOL: ACRDLIM / ACCT-CREDIT-LIMIT PIC S9(10)V99 COMP-3
     * Range: -9999999999.99 to +9999999999.99
     */
    @JsonProperty("creditLimit")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal creditLimit;

    /**
     * Cash credit limit (cash advance limit).
     * Mapped from COBOL: ACSHLIM / ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 COMP-3
     * Typically lower than the overall credit limit.
     */
    @JsonProperty("cashCreditLimit")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal cashCreditLimit;

    /**
     * Total credits applied in the current billing cycle.
     * Mapped from COBOL: ACRCYCR / ACCT-CURR-CYC-CREDIT PIC S9(10)V99 COMP-3
     * Includes payments, refunds, and other credit adjustments.
     */
    @JsonProperty("currentCycleCredit")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal currentCycleCredit;

    /**
     * Total debits (charges) in the current billing cycle.
     * Mapped from COBOL: ACRCYDB / ACCT-CURR-CYC-DEBIT PIC S9(10)V99 COMP-3
     * Includes purchases, fees, and interest charges.
     */
    @JsonProperty("currentCycleDebit")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal currentCycleDebit;

    /**
     * Date the account was opened.
     * Mapped from COBOL: ADTOPEN / ACCT-OPEN-DATE PIC X(10)
     * Original format: YYYY-MM-DD string
     * JSON format: ISO 8601 date (e.g., "2020-01-15")
     */
    @JsonProperty("openDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate openDate;

    /**
     * Account expiration date.
     * Mapped from COBOL: AEXPDT / ACCT-EXPIRAION-DATE PIC X(10)
     * Typically the card expiration date associated with the account.
     */
    @JsonProperty("expirationDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expirationDate;

    /**
     * Reissue date (optional field).
     * Mapped from COBOL: AREISDT / ACCT-REISSUE-DATE PIC X(10)
     * Populated when account/card is reissued (e.g., after loss or theft).
     * 
     * Nullable field - excluded from JSON response when null per @JsonInclude.
     */
    @JsonProperty("reissueDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reissueDate;

    /**
     * ZIP/postal code from account address.
     * Mapped from COBOL: ACSZIPC / ACCT-ADDR-ZIP PIC X(10)
     * Used for address verification and billing purposes.
     */
    @JsonProperty("addressZip")
    private String addressZip;

    /**
     * Account group identifier for categorization.
     * Mapped from COBOL: AADDGRP / ACCT-GROUP-ID PIC X(10)
     * Used for account segmentation and reporting.
     */
    @JsonProperty("groupId")
    private String groupId;

    /**
     * Customer ID associated with this account.
     * Mapped from COBOL: ACSTNUM (BMS field for customer number)
     * Links account to customer entity via foreign key relationship.
     */
    @JsonProperty("customerId")
    private Long customerId;

    /**
     * Customer first name (for display convenience).
     * Mapped from COBOL: ACSFNAM (BMS field)
     * Denormalized from Customer entity for UI display.
     */
    @JsonProperty("customerFirstName")
    private String customerFirstName;

    /**
     * Customer last name (for display convenience).
     * Mapped from COBOL: ACSLNAM (BMS field)
     * Denormalized from Customer entity for UI display.
     */
    @JsonProperty("customerLastName")
    private String customerLastName;

    /**
     * Timestamp when account record was created.
     * Inherited from Account entity's BaseEntity audit fields.
     * ISO 8601 datetime format in JSON responses.
     */
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Timestamp when account record was last updated.
     * Inherited from Account entity's BaseEntity audit fields.
     * Automatically updated on any account modification.
     */
    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // Lombok @Builder generates the following methods automatically:
    // - builder() - Returns AccountResponse.AccountResponseBuilder instance
    // - AccountResponseBuilder.accountId(Long) - Builder setter for accountId
    // - AccountResponseBuilder.accountNumber(String) - Builder setter for accountNumber
    // - AccountResponseBuilder.activeStatus(String) - Builder setter for activeStatus
    // - AccountResponseBuilder.currentBalance(BigDecimal) - Builder setter
    // - AccountResponseBuilder.creditLimit(BigDecimal) - Builder setter
    // - AccountResponseBuilder.cashCreditLimit(BigDecimal) - Builder setter
    // - AccountResponseBuilder.currentCycleCredit(BigDecimal) - Builder setter
    // - AccountResponseBuilder.currentCycleDebit(BigDecimal) - Builder setter
    // - AccountResponseBuilder.openDate(LocalDate) - Builder setter
    // - AccountResponseBuilder.expirationDate(LocalDate) - Builder setter
    // - AccountResponseBuilder.reissueDate(LocalDate) - Builder setter
    // - AccountResponseBuilder.addressZip(String) - Builder setter
    // - AccountResponseBuilder.groupId(String) - Builder setter
    // - AccountResponseBuilder.customerId(Long) - Builder setter
    // - AccountResponseBuilder.customerFirstName(String) - Builder setter
    // - AccountResponseBuilder.customerLastName(String) - Builder setter
    // - AccountResponseBuilder.createdAt(LocalDateTime) - Builder setter
    // - AccountResponseBuilder.updatedAt(LocalDateTime) - Builder setter
    // - AccountResponseBuilder.build() - Constructs AccountResponse instance
    
    // Lombok @Data generates the following methods automatically:
    // - getAccountId() - Getter for accountId
    // - getAccountNumber() - Getter for accountNumber
    // - getActiveStatus() - Getter for activeStatus
    // - getCurrentBalance() - Getter for currentBalance
    // - getCreditLimit() - Getter for creditLimit
    // - getCashCreditLimit() - Getter for cashCreditLimit
    // - getCurrentCycleCredit() - Getter for currentCycleCredit
    // - getCurrentCycleDebit() - Getter for currentCycleDebit
    // - getOpenDate() - Getter for openDate
    // - getExpirationDate() - Getter for expirationDate
    // - getReissueDate() - Getter for reissueDate
    // - getAddressZip() - Getter for addressZip
    // - getGroupId() - Getter for groupId
    // - getCustomerId() - Getter for customerId
    // - getCustomerFirstName() - Getter for customerFirstName
    // - getCustomerLastName() - Getter for customerLastName
    // - getCreatedAt() - Getter for createdAt
    // - getUpdatedAt() - Getter for updatedAt
    // - equals(Object) - Equality comparison based on all fields
    // - hashCode() - Hash code generation based on all fields
    // - toString() - String representation of all fields
    
    // Lombok @AllArgsConstructor generates:
    // - AccountResponse(Long accountId, String accountNumber, ...) - Full constructor
    
    // Lombok @NoArgsConstructor generates:
    // - AccountResponse() - No-args constructor for Jackson deserialization
}
