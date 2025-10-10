/*
 * CardUpdateRequest.java
 *
 * Card Update Request DTO for REST API endpoint PUT /api/v1/cards/{id}
 * Migrated from: app/bms/COCRDUP.bms (Card Update Screen)
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

/**
 * Card Update Request DTO for PUT /api/v1/cards/{id} endpoint.
 * 
 * <p>Migrated from: app/bms/COCRDUP.bms (Card Update Screen)</p>
 * 
 * <p>This Data Transfer Object captures credit card modification data from the REST API
 * request body, replacing COBOL EXEC CICS RECEIVE MAP(CCRDUPA) MAPSET(COCRDUP) screen
 * data reception pattern from COCRDUPC.cbl card update program.</p>
 * 
 * <p><strong>BMS Field Mappings:</strong></p>
 * <ul>
 *   <li>ACCTSID (POS=(7,45) LENGTH=11 ATTRB=PROT) - Account Number (display-only, not in request)</li>
 *   <li>CARDSID (POS=(8,45) LENGTH=16 ATTRB=UNPROT) - Card Number (display-only, identified by path param)</li>
 *   <li>CRDNAME (POS=(11,25) LENGTH=50 ATTRB=UNPROT) → cardholderName (String, max 50 chars)</li>
 *   <li>CRDSTCD (POS=(13,25) LENGTH=1 ATTRB=UNPROT) → cardStatus (String, A/C/S)</li>
 *   <li>EXPMON (POS=(15,25) LENGTH=2 ATTRB=UNPROT) → expirationMonth (Integer, 1-12)</li>
 *   <li>EXPYEAR (POS=(15,30) LENGTH=4 ATTRB=UNPROT) → expirationYear (Integer, ≥2024)</li>
 * </ul>
 * 
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li>Cardholder name: required, max 50 characters (replaces COBOL PIC X(50))</li>
 *   <li>Card status: required, must be 'A' (Active), 'C' (Closed), or 'S' (Suspended)</li>
 *   <li>Expiration month: required, must be 1-12 (replaces COBOL PIC 99)</li>
 *   <li>Expiration year: required, must be ≥2024 (replaces COBOL PIC 9999)</li>
 *   <li>Expiration date: must be in the future (class-level validation)</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * CardUpdateRequest request = CardUpdateRequest.builder()
 *     .cardholderName("John Smith")
 *     .cardStatus("A")
 *     .expirationMonth(12)
 *     .expirationYear(2025)
 *     .build();
 * }</pre>
 * 
 * @see com.aws.carddemo.controller.CardController#updateCard
 * @see com.aws.carddemo.service.CardService#updateCard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidExpirationDate
public class CardUpdateRequest {

    /**
     * Cardholder name as printed on the credit card.
     * 
     * <p>Maps to CRDNAME field from COCRDUP.bms (PIC X(50) in COBOL copybook).
     * This field is editable in the BMS screen (ATTRB=UNPROT) and allows updating
     * the name displayed on the physical card.</p>
     * 
     * <p><strong>Validation:</strong></p>
     * <ul>
     *   <li>Must not be null or blank</li>
     *   <li>Maximum length: 50 characters</li>
     *   <li>Typical format: "FirstName LastName" or "FirstName MiddleInitial LastName"</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>Name changes must match customer's legal name in customer record</li>
     *   <li>Special characters and spaces are allowed</li>
     *   <li>All uppercase convention from mainframe is not enforced in REST API</li>
     * </ul>
     */
    @NotBlank(message = "Cardholder name is required")
    @Size(max = 50, message = "Cardholder name cannot exceed 50 characters")
    @JsonProperty("cardholderName")
    private String cardholderName;

    /**
     * Card status code indicating the current operational state of the card.
     * 
     * <p>Maps to CRDSTCD field from COCRDUP.bms (PIC X(01) in COBOL copybook).
     * This single-character code controls whether the card can be used for transactions.</p>
     * 
     * <p><strong>Valid Status Codes:</strong></p>
     * <ul>
     *   <li><strong>'A'</strong> - Active: Card is active and can be used for purchases,
     *       cash advances, and balance transfers</li>
     *   <li><strong>'C'</strong> - Closed: Card is permanently closed and cannot be reactivated.
     *       All transactions will be declined</li>
     *   <li><strong>'S'</strong> - Suspended: Card is temporarily suspended due to fraud alert,
     *       missed payments, or customer request. Can be reactivated</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>Status transitions from 'C' (Closed) to any other status are not allowed</li>
     *   <li>Suspended cards ('S') can be reactivated to 'A' (Active)</li>
     *   <li>Closing a card ('C') requires outstanding balance to be zero</li>
     * </ul>
     * 
     * <p><strong>Validation:</strong></p>
     * <ul>
     *   <li>Must not be null or blank</li>
     *   <li>Must match pattern [ACS] (case-sensitive)</li>
     * </ul>
     */
    @NotBlank(message = "Card status is required")
    @Pattern(regexp = "[ACS]", message = "Card status must be A (Active), C (Closed), or S (Suspended)")
    @JsonProperty("cardStatus")
    private String cardStatus;

    /**
     * Card expiration month (1-12).
     * 
     * <p>Maps to EXPMON field from COCRDUP.bms (PIC 99 in COBOL copybook).
     * Credit cards expire at the end of the specified month/year combination.</p>
     * 
     * <p><strong>Valid Range:</strong></p>
     * <ul>
     *   <li>Minimum: 1 (January)</li>
     *   <li>Maximum: 12 (December)</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>Card is valid through the LAST DAY of the expiration month</li>
     *   <li>Example: expiration 12/2025 means valid through 2025-12-31</li>
     *   <li>Combined with expirationYear, must represent a future date</li>
     * </ul>
     * 
     * <p><strong>Validation:</strong></p>
     * <ul>
     *   <li>Must not be null</li>
     *   <li>Must be between 1 and 12 (inclusive)</li>
     *   <li>Combined validation with expirationYear via @ValidExpirationDate</li>
     * </ul>
     */
    @NotNull(message = "Expiration month is required")
    @Min(value = 1, message = "Expiration month must be between 1 and 12")
    @Max(value = 12, message = "Expiration month must be between 1 and 12")
    @JsonProperty("expirationMonth")
    private Integer expirationMonth;

    /**
     * Card expiration year (4-digit year).
     * 
     * <p>Maps to EXPYEAR field from COCRDUP.bms (PIC 9999 in COBOL copybook).
     * Must be a 4-digit year format (e.g., 2025, not 25).</p>
     * 
     * <p><strong>Valid Range:</strong></p>
     * <ul>
     *   <li>Minimum: 2024 (current year as of migration)</li>
     *   <li>Maximum: No explicit upper bound, but typically within 10 years of current year</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>New card expiration dates are typically set 3-5 years in the future</li>
     *   <li>Expiration date updates must not set an expired or past date</li>
     *   <li>Combined with expirationMonth, must represent a future date</li>
     * </ul>
     * 
     * <p><strong>Validation:</strong></p>
     * <ul>
     *   <li>Must not be null</li>
     *   <li>Must be 2024 or later (prevents accidentally setting past years)</li>
     *   <li>Combined validation with expirationMonth via @ValidExpirationDate</li>
     * </ul>
     */
    @NotNull(message = "Expiration year is required")
    @Min(value = 2024, message = "Expiration year must be 2024 or later")
    @JsonProperty("expirationYear")
    private Integer expirationYear;

}
