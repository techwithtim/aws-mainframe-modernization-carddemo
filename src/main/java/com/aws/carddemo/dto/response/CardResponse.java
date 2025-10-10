/*
 * CardResponse.java
 *
 * Response DTO for card inquiry operations.
 * Migrated from: app/bms/COCRDSL.bms (Card Selection Screen)
 * Data Structure from: app/cpy/CVACT02Y.cpy (CARD-RECORD)
 *
 * This DTO maps Card entity data to JSON API responses for:
 * - GET /api/v1/cards/{id}
 * - GET /api/v1/cards/{cardNumber}
 *
 * Replaces COCRDSL.bms screen output fields with Jackson-serialized JSON structure.
 *
 * PCI-DSS Compliance:
 * - Card numbers are masked showing only last 4 digits (************1234 pattern)
 * - CVV codes are NEVER included per PCI-DSS Requirement 3.2.2
 * - Sensitive data excluded from JSON serialization and logs
 *
 * Copyright Amazon.com, Inc. or its affiliates.
 * Licensed under the Apache License, Version 2.0
 */
package com.aws.carddemo.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Response DTO for card detail inquiry operations.
 * 
 * Maps COBOL copybook CVACT02Y.cpy to JSON response:
 * - CARD-NUM (PIC X(16)) → cardNumberMasked (String) - masked for PCI-DSS compliance
 * - CARD-ACCT-ID (PIC 9(11)) → accountId (Long)
 * - CARD-EMBOSSED-NAME (PIC X(50)) → embossedName (String)
 * - CARD-EXPIRAION-DATE (PIC X(10)) → expirationDate (LocalDate) - formatted as MM/YY
 * - CARD-ACTIVE-STATUS (PIC X(01)) → activeStatus (String) - Y/N indicator
 * 
 * BMS Screen Field Mappings (COCRDSL.bms):
 * - ACCTSID (Account Number, 11 chars) → accountId
 * - CARDSID (Card Number, 16 chars) → cardNumberMasked (masked display)
 * - CRDNAME (Name on card, 50 chars) → embossedName
 * - CRDSTCD (Card Active Y/N, 1 char) → activeStatus
 * - EXPMON/EXPYEAR (Expiry MM/YYYY) → expirationDate
 * 
 * Security Features:
 * - Card numbers masked to show only last 4 digits for PCI-DSS Requirement 3.3
 * - CVV codes excluded from all responses per PCI-DSS Requirement 3.2.2
 * - Implements Serializable for distributed cache compatibility
 * - Null fields excluded from JSON per @JsonInclude(NON_NULL)
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CardResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Card identifier (internal primary key).
     * Not displayed on BMS screen but included for API resource identification.
     */
    @JsonProperty("card_id")
    private Long cardId;

    /**
     * Masked card number showing only last 4 digits.
     * Format: ************1234 (12 asterisks + last 4 digits)
     * 
     * Maps from CARD-NUM (PIC X(16)) in CVACT02Y.cpy copybook.
     * Displayed in CARDSID field on COCRDSL.bms screen.
     * 
     * PCI-DSS Compliance: Only last 4 digits shown per Requirement 3.3.
     * Full card number (PAN) NEVER transmitted in responses.
     * 
     * Example: "************1234" for card 4506445678901234
     */
    @JsonProperty("card_number_masked")
    private String cardNumberMasked;

    /**
     * Name embossed on the card.
     * Maps from CARD-EMBOSSED-NAME (PIC X(50)) in CVACT02Y.cpy copybook.
     * Displayed in CRDNAME field (50 chars) on COCRDSL.bms screen.
     * 
     * Example: "JOHN DOE"
     */
    @JsonProperty("embossed_name")
    private String embossedName;

    /**
     * Card expiration date.
     * Maps from CARD-EXPIRAION-DATE (PIC X(10), YYYY-MM-DD) in CVACT02Y.cpy copybook.
     * Displayed as MM/YY format in EXPMON/EXPYEAR fields on COCRDSL.bms screen.
     * 
     * JSON format: ISO date format "yyyy-MM-dd" (e.g., "2025-12-31")
     * Display format: Use formatExpirationDate() method to get "MM/yy" format (e.g., "12/25")
     * Internal storage: Full LocalDate for precise expiration tracking
     * 
     * Note: @JsonFormat with "MM/yy" pattern removed because it caused deserialization failures.
     * The "MM/yy" format doesn't include day information required for LocalDate.
     * Use the static formatExpirationDate() method for "MM/yy" display formatting.
     * 
     * Example: 2025-12-31 serializes as "2025-12-31", formats as "12/25" for display
     */
    @JsonProperty("expiration_date")
    private LocalDate expirationDate;

    /**
     * Card active status indicator.
     * Maps from CARD-ACTIVE-STATUS (PIC X(01)) in CVACT02Y.cpy copybook.
     * Displayed in CRDSTCD field (1 char) on COCRDSL.bms screen.
     * 
     * Valid values:
     * - "Y" = Active card, eligible for transactions
     * - "N" = Inactive card, blocked from transactions
     * 
     * Example: "Y"
     */
    @JsonProperty("active_status")
    private String activeStatus;

    /**
     * Associated account identifier.
     * Maps from CARD-ACCT-ID (PIC 9(11)) in CVACT02Y.cpy copybook.
     * Displayed in ACCTSID field (11 chars) on COCRDSL.bms screen.
     * 
     * Links card to owning account for transaction authorization.
     * Used for account balance lookups during transaction processing.
     * 
     * Example: 12345678901
     */
    @JsonProperty("account_id")
    private Long accountId;

    /**
     * Helper method to mask card number for display.
     * Replaces first 12 digits with asterisks, showing only last 4 digits.
     * 
     * Used by CardMapper.toResponse() to convert Card entity to CardResponse DTO.
     * Implements PCI-DSS Requirement 3.3 masking standard.
     * 
     * @param fullCardNumber Full 16-digit card number
     * @return Masked card number (************1234)
     */
    public static String maskCardNumber(String fullCardNumber) {
        if (fullCardNumber == null || fullCardNumber.length() < 4) {
            return "************????"; // Invalid card number
        }
        
        int length = fullCardNumber.length();
        String lastFour = fullCardNumber.substring(length - 4);
        String maskedPrefix = "*".repeat(length - 4);
        
        return maskedPrefix + lastFour;
    }

    /**
     * Formats expiration date for display.
     * Converts LocalDate to MM/YY format matching COCRDSL.bms screen display.
     * 
     * @param expirationDate Card expiration date
     * @return Formatted expiration string (MM/YY)
     */
    public static String formatExpirationDate(LocalDate expirationDate) {
        if (expirationDate == null) {
            return null;
        }
        
        int month = expirationDate.getMonthValue();
        int year = expirationDate.getYear() % 100; // Last 2 digits of year
        
        return String.format("%02d/%02d", month, year);
    }

    /**
     * Validates if card is expired based on expiration date.
     * Helper method for business logic validation in CardService.
     * 
     * Excluded from JSON serialization (computed field, no setter).
     * 
     * @return true if card is expired, false otherwise
     */
    @JsonIgnore
    public boolean isExpired() {
        if (expirationDate == null) {
            return false; // Cannot determine expiration
        }
        
        // Card expires at end of month, so compare to last day of expiration month
        LocalDate endOfExpirationMonth = expirationDate.withDayOfMonth(
            expirationDate.lengthOfMonth()
        );
        
        return LocalDate.now().isAfter(endOfExpirationMonth);
    }

    /**
     * Validates if card is active and not expired.
     * Combines activeStatus and expirationDate checks.
     * 
     * Used for transaction authorization decisions.
     * Excluded from JSON serialization (computed field, no setter).
     * 
     * @return true if card is active and not expired, false otherwise
     */
    @JsonIgnore
    public boolean isUsable() {
        return "Y".equals(activeStatus) && !isExpired();
    }

    /**
     * Returns a display-friendly string representation.
     * Excludes sensitive data (card number is already masked).
     * Safe for logging and debugging.
     * 
     * @return String representation of CardResponse
     */
    @Override
    public String toString() {
        return "CardResponse{" +
                "cardId=" + cardId +
                ", cardNumberMasked='" + cardNumberMasked + '\'' +
                ", embossedName='" + embossedName + '\'' +
                ", expirationDate=" + expirationDate +
                ", activeStatus='" + activeStatus + '\'' +
                ", accountId=" + accountId +
                ", expired=" + isExpired() +
                ", usable=" + isUsable() +
                '}';
    }
}
