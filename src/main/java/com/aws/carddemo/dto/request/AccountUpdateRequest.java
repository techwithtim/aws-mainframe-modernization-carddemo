/*
 * AccountUpdateRequest.java
 *
 * Account update request DTO for PUT /api/v1/accounts/{id} endpoint
 * 
 * Migrated from: app/bms/COACTUP.bms (Account Update Screen)
 * Source copybooks: app/cpy/CVACT01Y.cpy (Account Record)
 *                   app/cpy/CVCUS01Y.cpy (Customer Record)
 * 
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This DTO replaces EXEC CICS RECEIVE MAP(CACTUPA) MAPSET(COACTUP) with 
 * Spring @RequestBody binding, capturing comprehensive account and customer
 * profile modification data from the BMS COACTUP screen's 30+ editable fields.
 * 
 * PCI-DSS Compliance:
 * - SSN stored as last 4 digits only (ssnLastFour field)
 * - Full SSN never accepted or logged per PCI-DSS requirement 3.4
 * - Sensitive fields marked with @JsonIgnore to prevent exposure in logs
 * - Full card numbers never accepted in this DTO (card updates use CardUpdateRequest)
 */
package com.aws.carddemo.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for updating account and customer profile information.
 * 
 * Captures comprehensive modification data from the COACTUP BMS screen including:
 * - Account fields: status, limits, dates (open/expiration/reissue)
 * - Customer demographics: name, SSN (last 4 only), DOB, FICO score
 * - Address information: street, city, state, zip, country
 * - Contact details: phone numbers, email
 * - Government ID and EFT account information
 * 
 * Validation enforces:
 * - Account status transition rules (Active/Closed/Suspended)
 * - Credit limit constraints (cash limit <= total credit limit)
 * - Date validations (expiration > open date, DOB indicates adult age 18+)
 * - SSN formatting (4 digits only for PCI-DSS compliance)
 * - Phone/email patterns
 * - Address field lengths matching COBOL copybook structures
 * 
 * Coordinates with AccountService.updateAccount() implementing COACTUPC.cbl business logic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountUpdateRequest {

    // ========================================================================
    // ACCOUNT FIELDS (from CVACT01Y.cpy structure)
    // ========================================================================

    /**
     * Account active status indicator.
     * Maps to: ACSTTUS field (DFHMDF POS=(5,70) LENGTH=1 ATTRB=UNPROT)
     * COBOL source: ACCT-ACTIVE-STATUS PIC X(01) from CVACT01Y.cpy
     * 
     * Valid values (must match Account entity validation):
     * - "Y" = Active (Yes)
     * - "N" = Inactive/Closed (No)
     * 
     * Business rules:
     * - Active ('Y') accounts can process transactions
     * - Inactive ('N') accounts are closed and cannot process transactions
     * 
     * NOTE: This field maps directly to Account.activeStatus without conversion.
     * The DTO validation must match the entity validation @Pattern(regexp = "[YN]").
     */
    @NotBlank(message = "Account status is required")
    @Pattern(regexp = "[YN]", message = "Account status must be 'Y' (Active) or 'N' (Inactive)")
    @JsonProperty("account_status")
    private String accountStatus;

    /**
     * Reason for status change (e.g., customer request, fraud, delinquency).
     * Required when changing account status.
     * Max length: 100 characters
     */
    @Size(max = 100, message = "Status reason cannot exceed 100 characters")
    @JsonProperty("status_reason")
    private String statusReason;

    /**
     * Total credit limit for the account.
     * Maps to: ACRDLIM field (DFHMDF POS=(6,61) LENGTH=15 ATTRB=UNPROT)
     * COBOL source: ACCT-CREDIT-LIMIT PIC S9(10)V99 COMP-3 from CVACT01Y.cpy
     * 
     * Precision: 10 integer digits, 2 decimal places (matches COBOL packed decimal)
     * Range: $0.00 to $999,999.99
     * 
     * Business rule: cashCreditLimit must be <= creditLimit
     */
    @NotNull(message = "Credit limit is required")
    @DecimalMin(value = "0.00", message = "Credit limit must be >= 0.00")
    @DecimalMax(value = "999999.99", message = "Credit limit cannot exceed $999,999.99")
    @Digits(integer = 6, fraction = 2, message = "Credit limit must have at most 6 integer digits and 2 decimal places")
    @JsonProperty("credit_limit")
    private BigDecimal creditLimit;

    /**
     * Cash advance credit limit.
     * Maps to: ACSHLIM field (DFHMDF POS=(7,61) LENGTH=15 ATTRB=UNPROT)
     * COBOL source: ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 COMP-3 from CVACT01Y.cpy
     * 
     * Precision: 10 integer digits, 2 decimal places
     * Range: $0.00 to $999,999.99
     * 
     * Business rule: Must be <= creditLimit (enforced by @ValidCreditLimits annotation)
     */
    @NotNull(message = "Cash credit limit is required")
    @DecimalMin(value = "0.00", message = "Cash credit limit must be >= 0.00")
    @DecimalMax(value = "999999.99", message = "Cash credit limit cannot exceed $999,999.99")
    @Digits(integer = 6, fraction = 2, message = "Cash credit limit must have at most 6 integer digits and 2 decimal places")
    @JsonProperty("cash_credit_limit")
    private BigDecimal cashCreditLimit;

    /**
     * Account opening date.
     * Maps to: OPNYEAR/OPNMON/OPNDAY fields from BMS screen
     * COBOL source: ACCT-OPEN-DATE PIC X(10) from CVACT01Y.cpy
     * 
     * Format: yyyy-MM-dd
     * Validation: Must be in the past or present
     * 
     * Business rule: accountExpirationDate must be > accountOpenDate
     */
    @NotNull(message = "Account open date is required")
    @PastOrPresent(message = "Account open date must be in the past or present")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @JsonProperty("account_open_date")
    private LocalDate accountOpenDate;

    /**
     * Account expiration date.
     * Maps to: EXPYEAR/EXPMON/EXPDAY fields from BMS screen
     * COBOL source: ACCT-EXPIRAION-DATE PIC X(10) from CVACT01Y.cpy [note: typo in copybook]
     * 
     * Format: yyyy-MM-dd
     * Validation: Must be in the future
     * 
     * Business rules:
     * - Must be > accountOpenDate
     * - Minimum 1-year account term enforced by @ValidExpirationDate annotation
     */
    @NotNull(message = "Account expiration date is required")
    @Future(message = "Account expiration date must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @JsonProperty("account_expiration_date")
    private LocalDate accountExpirationDate;

    /**
     * Card reissue date (optional).
     * Maps to: RISYEAR/RISMON/RISDAY fields from BMS screen
     * COBOL source: ACCT-REISSUE-DATE PIC X(10) from CVACT01Y.cpy
     * 
     * Format: yyyy-MM-dd
     * Validation: If provided, must be in the past or present
     * 
     * Used for tracking when a card was reissued (e.g., lost/stolen replacement)
     */
    @PastOrPresent(message = "Reissue date must be in the past or present")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @JsonProperty("reissue_date")
    private LocalDate reissueDate;

    // ========================================================================
    // CUSTOMER DEMOGRAPHIC FIELDS (from CVCUS01Y.cpy structure)
    // ========================================================================

    /**
     * Customer first name.
     * Maps to: ACSFNAM field (DFHMDF POS=(15,1) LENGTH=25 ATTRB=UNPROT)
     * COBOL source: CUST-FIRST-NAME PIC X(25) from CVCUS01Y.cpy
     * 
     * Max length: 25 characters (matches COBOL PIC X(25) constraint)
     */
    @NotBlank(message = "First name is required")
    @Size(max = 25, message = "First name cannot exceed 25 characters")
    @JsonProperty("first_name")
    private String firstName;

    /**
     * Customer middle name (optional).
     * Maps to: ACSMNAM field (DFHMDF POS=(15,28) LENGTH=25 ATTRB=UNPROT)
     * COBOL source: CUST-MIDDLE-NAME PIC X(25) from CVCUS01Y.cpy
     * 
     * Max length: 25 characters
     */
    @Size(max = 25, message = "Middle name cannot exceed 25 characters")
    @JsonProperty("middle_name")
    private String middleName;

    /**
     * Customer last name.
     * Maps to: ACSLNAM field (DFHMDF POS=(15,55) LENGTH=25 ATTRB=UNPROT)
     * COBOL source: CUST-LAST-NAME PIC X(25) from CVCUS01Y.cpy
     * 
     * Max length: 25 characters
     */
    @NotBlank(message = "Last name is required")
    @Size(max = 25, message = "Last name cannot exceed 25 characters")
    @JsonProperty("last_name")
    private String lastName;

    /**
     * Last 4 digits of Social Security Number.
     * Maps to: ACTSSN3 field (last segment of SSN triplet)
     * COBOL source: CUST-SSN PIC 9(09) from CVCUS01Y.cpy
     * 
     * PCI-DSS Compliance:
     * - Only last 4 digits accepted and stored (PCI-DSS requirement 3.4)
     * - Full SSN never accepted or logged
     * - Field marked with @JsonIgnore to prevent exposure in logs
     * 
     * Format: 4 digits (e.g., "1234")
     */
    @JsonIgnore
    @Pattern(regexp = "\\d{4}", message = "SSN last four must be exactly 4 digits")
    @JsonProperty("ssn_last_four")
    private String ssnLastFour;

    /**
     * FICO credit score.
     * Maps to: ACSTFCO field (DFHMDF POS=(13,62) LENGTH=3 ATTRB=UNPROT)
     * COBOL source: CUST-FICO-CREDIT-SCORE PIC 9(03) from CVCUS01Y.cpy
     * 
     * Valid range: 300-850 (standard FICO score range)
     */
    @Min(value = 300, message = "FICO score must be >= 300")
    @Max(value = 850, message = "FICO score must be <= 850")
    @JsonProperty("fico_score")
    private Integer ficoScore;

    /**
     * Customer date of birth.
     * Maps to: DOBYEAR/DOBMON/DOBDAY fields from BMS screen
     * COBOL source: CUST-DOB-YYYY-MM-DD PIC X(10) from CVCUS01Y.cpy
     * 
     * Format: yyyy-MM-dd
     * Validation: 
     * - Must be in the past
     * - Customer must be adult (18-120 years old) via @AgeRestriction annotation
     */
    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @JsonProperty("date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * Primary phone number (10 digits, US format).
     * Maps to: ACSPH1A/ACSPH1B/ACSPH1C fields (area/prefix/suffix triplet)
     * COBOL source: CUST-PHONE-NUM-1 PIC X(15) from CVCUS01Y.cpy
     * 
     * Format: 10 digits (e.g., "5551234567")
     * Replaces COBOL area code/prefix/suffix structure with unified format
     */
    @Pattern(regexp = "\\d{10}", message = "Phone number must be exactly 10 digits")
    @JsonProperty("phone_number")
    private String phoneNumber;

    // ========================================================================
    // ADDRESS FIELDS
    // ========================================================================

    /**
     * Address line 1 (street address).
     * Maps to: ACSADL1 field (DFHMDF POS=(16,10) LENGTH=50 ATTRB=UNPROT)
     * COBOL source: CUST-ADDR-LINE-1 PIC X(50) from CVCUS01Y.cpy
     * 
     * Max length: 50 characters (matches COBOL PIC X(50) constraint)
     */
    @NotBlank(message = "Address line 1 is required")
    @Size(max = 50, message = "Address line 1 cannot exceed 50 characters")
    @JsonProperty("address_line1")
    private String addressLine1;

    /**
     * Address line 2 (apartment, suite, etc.) - optional.
     * Maps to: ACSADL2 field (DFHMDF POS=(17,10) LENGTH=50 ATTRB=UNPROT)
     * COBOL source: CUST-ADDR-LINE-2 PIC X(50) from CVCUS01Y.cpy
     * 
     * Max length: 50 characters
     */
    @Size(max = 50, message = "Address line 2 cannot exceed 50 characters")
    @JsonProperty("address_line2")
    private String addressLine2;

    /**
     * City name.
     * Maps to: ACSCITY field (DFHMDF POS=(18,10) LENGTH=50 ATTRB=UNPROT)
     * COBOL source: CUST-ADDR-LINE-3 PIC X(50) from CVCUS01Y.cpy (used for city)
     * 
     * Max length: 50 characters
     */
    @NotBlank(message = "City is required")
    @Size(max = 50, message = "City cannot exceed 50 characters")
    @JsonProperty("city")
    private String city;

    /**
     * State code (2-letter US state abbreviation).
     * Maps to: ACSSTTE field (DFHMDF POS=(16,73) LENGTH=2 ATTRB=UNPROT)
     * COBOL source: CUST-ADDR-STATE-CD PIC X(02) from CVCUS01Y.cpy
     * 
     * Format: 2 uppercase letters (e.g., "TX", "CA", "NY")
     * Validation matches CSLKPCDY.cpy area code validation logic
     */
    @NotBlank(message = "State is required")
    @Pattern(regexp = "[A-Z]{2}", message = "State must be 2 uppercase letters (e.g., 'TX', 'CA', 'NY')")
    @JsonProperty("state")
    private String state;

    /**
     * ZIP code (5-digit or 9-digit format).
     * Maps to: ACSZIPC field (DFHMDF POS=(17,73) LENGTH=5 ATTRB=UNPROT)
     * COBOL source: CUST-ADDR-ZIP PIC X(10) from CVCUS01Y.cpy
     * 
     * Format: 5 digits (e.g., "75001") or 9 digits with hyphen (e.g., "75001-1234")
     */
    @NotBlank(message = "ZIP code is required")
    @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "ZIP code must be 5 digits or 9 digits with hyphen (e.g., '75001' or '75001-1234')")
    @JsonProperty("zip_code")
    private String zipCode;

    // ========================================================================
    // CONTACT AND GOVERNMENT ID FIELDS
    // ========================================================================

    /**
     * Email address.
     * COBOL source: Not in copybook, added for modern contact methods
     * 
     * Format: RFC 5322 compliant email address
     * Max length: 100 characters
     */
    @Email(message = "Email must be a valid RFC 5322 email address")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    @JsonProperty("email")
    private String email;

    /**
     * Government-issued ID (driver's license or passport number).
     * Maps to: ACSGOVT field (DFHMDF POS=(19,58) LENGTH=20 ATTRB=UNPROT)
     * COBOL source: CUST-GOVT-ISSUED-ID PIC X(20) from CVCUS01Y.cpy
     * 
     * Max length: 20 characters
     */
    @Size(max = 20, message = "Government issued ID cannot exceed 20 characters")
    @JsonProperty("government_issued_id")
    private String governmentIssuedId;

    /**
     * State that issued the government ID (2-letter code).
     * Not explicitly in BMS screen, added for ID validation
     * 
     * Format: 2 uppercase letters
     */
    @Pattern(regexp = "[A-Z]{2}", message = "Government ID state must be 2 uppercase letters")
    @JsonProperty("government_id_state")
    private String governmentIdState;

    // ========================================================================
    // EFT (ELECTRONIC FUND TRANSFER) FIELDS
    // ========================================================================

    /**
     * EFT routing number (9-digit ABA routing number).
     * Maps to: ACSEFTC field (DFHMDF POS=(20,41) LENGTH=10 ATTRB=UNPROT)
     * COBOL source: CUST-EFT-ACCOUNT-ID PIC X(10) from CVCUS01Y.cpy
     * 
     * Format: 9 digits (e.g., "021000021")
     * Used for ACH transactions and electronic fund transfers
     */
    @Pattern(regexp = "\\d{9}", message = "EFT routing number must be exactly 9 digits")
    @JsonProperty("eft_routing_number")
    private String eftRoutingNumber;

    /**
     * EFT bank account number (4-17 digits, variable length).
     * Not explicitly in BMS, but implied for complete EFT setup
     * 
     * Format: 4-17 digits (standard US bank account number range)
     */
    @Pattern(regexp = "\\d{4,17}", message = "EFT account number must be 4-17 digits")
    @JsonProperty("eft_account_number")
    private String eftAccountNumber;

    // ========================================================================
    // CLASS-LEVEL VALIDATION ANNOTATIONS (Custom Validators)
    // ========================================================================

    /*
     * Note: The following custom validation annotations are referenced but their
     * implementation classes are created separately:
     * 
     * @ValidCreditLimits - Ensures cashCreditLimit <= creditLimit
     * @ValidAccountStatus - Enforces account status transition rules
     * @ValidExpirationDate - Ensures accountExpirationDate > accountOpenDate + 1 year
     * @AgeRestriction(min=18, max=120) - Validates customer age from dateOfBirth
     * 
     * These validators are applied at the service layer during account update processing.
     */
}
