/*
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.aws.carddemo.util;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Date validation utility class migrated from COBOL program CSUTLDTC.cbl.
 * 
 * <p>This class provides comprehensive calendar date validation logic using Java's
 * LocalDate API, replacing the legacy COBOL CALL 'CEEDAYS' Language Environment
 * date validation routines. It maintains functional equivalence with the original
 * COBOL implementation while leveraging modern Java 8+ date/time capabilities.</p>
 * 
 * <p><b>Migration Notes:</b></p>
 * <ul>
 *   <li>Replaces CALL 'CEEDAYS' USING WS-DATE-TO-TEST, WS-DATE-FORMAT, OUTPUT-LILLIAN, FEEDBACK-CODE</li>
 *   <li>Maps COBOL FEEDBACK-CODE 88-level conditions to Java ValidationResult responses</li>
 *   <li>Maintains COBOL RETURN-CODE convention: 0 = valid, >0 = error severity</li>
 *   <li>WS-MESSAGE structure (WS-SEVERITY, WS-MSG-NO, WS-RESULT) mapped to ValidationResult</li>
 * </ul>
 * 
 * <p><b>COBOL Feedback Code Mapping:</b></p>
 * <pre>
 * FC-INVALID-DATE (X'0000000000000000') -> Severity 0, "Date is valid"
 * FC-INSUFFICIENT-DATA                   -> Severity 1, "Insufficient data"
 * FC-BAD-DATE-VALUE                      -> Severity 2, "Date value error"
 * FC-INVALID-MONTH                       -> Severity 3, "Invalid month"
 * FC-NON-NUMERIC-DATA                    -> Severity 4, "Non-numeric data"
 * </pre>
 * 
 * <p>Original COBOL source: app/cbl/CSUTLDTC.cbl</p>
 * 
 * @author AWS CardDemo Modernization Team
 * @since 1.0.0
 */
@Component
public class DateValidator {

    /**
     * Validates a date string against a specified format pattern.
     * 
     * <p>This method replaces the COBOL CALL 'CEEDAYS' validation logic,
     * using LocalDate.parse() with DateTimeFormatter for date string parsing.</p>
     * 
     * <p><b>COBOL Equivalent:</b></p>
     * <pre>
     * CALL "CEEDAYS" USING WS-DATE-TO-TEST, WS-DATE-FORMAT,
     *                      OUTPUT-LILLIAN, FEEDBACK-CODE
     * </pre>
     * 
     * @param dateStr the date string to validate (e.g., "2024-01-15", "20240115")
     * @param formatPattern the date format pattern (e.g., "yyyy-MM-dd", "yyyyMMdd", "MM/dd/yyyy")
     * @return ValidationResult containing severity code and validation message
     * 
     * @see ValidationResult
     */
    public ValidationResult isValidDate(String dateStr, String formatPattern) {
        // Handle null or empty inputs (FC-INSUFFICIENT-DATA)
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return new ValidationResult(
                false,
                1,
                "Insufficient data",
                dateStr,
                formatPattern
            );
        }
        
        if (formatPattern == null || formatPattern.trim().isEmpty()) {
            return new ValidationResult(
                false,
                1,
                "Insufficient data",
                dateStr,
                formatPattern
            );
        }
        
        try {
            // Attempt to parse the date with the specified format
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern);
            LocalDate parsedDate = LocalDate.parse(dateStr, formatter);
            
            // Validate that the parsed date components match the input
            // This catches cases like "2024-02-31" which might parse to "2024-03-03"
            String reformatted = parsedDate.format(formatter);
            if (!dateStr.equals(reformatted)) {
                return new ValidationResult(
                    false,
                    2,
                    "Date value error",
                    dateStr,
                    formatPattern
                );
            }
            
            // Additional validation: check for reasonable date range
            // Dates before 1900 or more than 100 years in the future are suspicious
            LocalDate minDate = LocalDate.of(1900, 1, 1);
            LocalDate maxDate = LocalDate.now().plusYears(100);
            
            if (parsedDate.isBefore(minDate) || parsedDate.isAfter(maxDate)) {
                return new ValidationResult(
                    false,
                    2,
                    "Date value error",
                    dateStr,
                    formatPattern
                );
            }
            
            // Date is valid (FC-INVALID-DATE condition in COBOL, which means valid)
            return new ValidationResult(
                true,
                0,
                "Date is valid",
                dateStr,
                formatPattern
            );
            
        } catch (DateTimeParseException e) {
            // Determine specific error type based on exception message
            String errorMessage = e.getMessage().toLowerCase();
            
            // Check specifically for MonthOfYear errors (not DayOfMonth)
            if (errorMessage.contains("monthofyear")) {
                // FC-INVALID-MONTH
                return new ValidationResult(
                    false,
                    3,
                    "Invalid month",
                    dateStr,
                    formatPattern
                );
            } 
            // Check for date component errors (DayOfMonth, Year, etc.)
            else if (errorMessage.contains("dayofmonth") || 
                     errorMessage.contains("invalid value for")) {
                // FC-BAD-DATE-VALUE (invalid day, year, etc.)
                return new ValidationResult(
                    false,
                    2,
                    "Date value error",
                    dateStr,
                    formatPattern
                );
            }
            // Check for truly non-numeric data (letters where numbers expected)
            else if (errorMessage.contains("could not be parsed at index") ||
                     errorMessage.contains("unparseable")) {
                // FC-NON-NUMERIC-DATA
                return new ValidationResult(
                    false,
                    4,
                    "Non-numeric data",
                    dateStr,
                    formatPattern
                );
            } 
            else {
                // FC-BAD-DATE-VALUE (general parsing error)
                return new ValidationResult(
                    false,
                    2,
                    "Date value error",
                    dateStr,
                    formatPattern
                );
            }
        } catch (IllegalArgumentException e) {
            // Bad format pattern (FC-BAD-PIC-STRING in COBOL)
            return new ValidationResult(
                false,
                5,
                "Bad format pattern",
                dateStr,
                formatPattern
            );
        }
    }
    
    /**
     * Validates that a date falls within a specified range.
     * 
     * <p>This method enforces business rules for acceptable date ranges,
     * commonly used for validating account opening dates, transaction dates,
     * and expiration dates.</p>
     * 
     * @param date the date to validate
     * @param minDate the minimum acceptable date (inclusive)
     * @param maxDate the maximum acceptable date (inclusive)
     * @return ValidationResult indicating whether the date is within range
     */
    public ValidationResult validateDateRange(LocalDate date, LocalDate minDate, LocalDate maxDate) {
        if (date == null) {
            return new ValidationResult(
                false,
                1,
                "Insufficient data",
                null,
                null
            );
        }
        
        if (minDate == null || maxDate == null) {
            return new ValidationResult(
                false,
                1,
                "Insufficient data",
                date.toString(),
                null
            );
        }
        
        if (date.isBefore(minDate)) {
            return new ValidationResult(
                false,
                6,
                "Date is before minimum allowed date: " + minDate.toString(),
                date.toString(),
                null
            );
        }
        
        if (date.isAfter(maxDate)) {
            return new ValidationResult(
                false,
                7,
                "Date is after maximum allowed date: " + maxDate.toString(),
                date.toString(),
                null
            );
        }
        
        return new ValidationResult(
            true,
            0,
            "Date is within valid range",
            date.toString(),
            null
        );
    }
    
    /**
     * Determines if a given date is a business day (Monday-Friday, excluding holidays).
     * 
     * <p>This method checks if the date falls on a weekday (Monday through Friday).
     * Note: This implementation does not include holiday checking. For production
     * use with holiday calendars, extend this method to check against a holiday table.</p>
     * 
     * @param date the date to check
     * @return true if the date is a business day (Monday-Friday), false otherwise
     */
    public boolean isBusinessDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        
        // Business days are Monday (1) through Friday (5)
        boolean isWeekday = dayOfWeek.getValue() >= DayOfWeek.MONDAY.getValue()
                         && dayOfWeek.getValue() <= DayOfWeek.FRIDAY.getValue();
        
        // TODO: Add holiday checking logic if needed
        // For now, return true for any weekday
        // In production, check against HOLIDAY table:
        // return isWeekday && !isHoliday(date);
        
        return isWeekday;
    }
    
    /**
     * Determines if a given date falls on a weekend (Saturday or Sunday).
     * 
     * @param date the date to check
     * @return true if the date is Saturday or Sunday, false otherwise
     */
    public boolean isWeekend(LocalDate date) {
        if (date == null) {
            return false;
        }
        
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
    
    /**
     * Parses a date string with a specified format pattern, returning an Optional.
     * 
     * <p>This method wraps LocalDate.parse with comprehensive exception handling,
     * returning Optional.empty() for invalid dates instead of throwing exceptions.</p>
     * 
     * <p>Replaces COBOL CEEDAYS feedback mechanism with type-safe Optional handling.</p>
     * 
     * @param dateStr the date string to parse
     * @param formatPattern the date format pattern
     * @return Optional containing the parsed LocalDate if valid, Optional.empty() otherwise
     */
    public Optional<LocalDate> parseDateWithFormat(String dateStr, String formatPattern) {
        try {
            if (dateStr == null || dateStr.trim().isEmpty() ||
                formatPattern == null || formatPattern.trim().isEmpty()) {
                return Optional.empty();
            }
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern);
            LocalDate parsedDate = LocalDate.parse(dateStr, formatter);
            
            // Verify the parsed date reformats to the original string
            // This catches lenient parsing issues
            String reformatted = parsedDate.format(formatter);
            if (!dateStr.equals(reformatted)) {
                return Optional.empty();
            }
            
            return Optional.of(parsedDate);
            
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Validates a date of birth, ensuring it represents a reasonable age.
     * 
     * <p>This method enforces business rules for date of birth validation:</p>
     * <ul>
     *   <li>Must be in the past</li>
     *   <li>Person must be at least 18 years old (legal age for credit cards)</li>
     *   <li>Person must not be more than 120 years old (reasonable maximum age)</li>
     * </ul>
     * 
     * @param dateOfBirth the date of birth to validate
     * @return ValidationResult indicating whether the date of birth is valid
     */
    public ValidationResult isValidDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return new ValidationResult(
                false,
                1,
                "Insufficient data",
                null,
                null
            );
        }
        
        LocalDate today = LocalDate.now();
        
        // Date of birth must be in the past
        if (!dateOfBirth.isBefore(today)) {
            return new ValidationResult(
                false,
                8,
                "Date of birth must be in the past",
                dateOfBirth.toString(),
                null
            );
        }
        
        // Calculate age
        int age = today.getYear() - dateOfBirth.getYear();
        
        // Adjust age if birthday hasn't occurred this year yet
        if (today.getMonthValue() < dateOfBirth.getMonthValue() ||
            (today.getMonthValue() == dateOfBirth.getMonthValue() &&
             today.getDayOfMonth() < dateOfBirth.getDayOfMonth())) {
            age--;
        }
        
        // Minimum age check (18 years for credit card eligibility)
        if (age < 18) {
            return new ValidationResult(
                false,
                9,
                "Person must be at least 18 years old",
                dateOfBirth.toString(),
                null
            );
        }
        
        // Maximum age check (120 years - reasonable upper limit)
        if (age > 120) {
            return new ValidationResult(
                false,
                10,
                "Date of birth indicates unrealistic age (>120 years)",
                dateOfBirth.toString(),
                null
            );
        }
        
        return new ValidationResult(
            true,
            0,
            "Date of birth is valid",
            dateOfBirth.toString(),
            null
        );
    }
    
    /**
     * Validation result class representing the outcome of date validation.
     * 
     * <p>This class replaces the COBOL WS-MESSAGE structure from CSUTLDTC.cbl:</p>
     * <pre>
     * 01 WS-MESSAGE.
     *     02 WS-SEVERITY  PIC X(04).     -> severityCode
     *     02 WS-MSG-NO    PIC X(04).     -> (implicit in message)
     *     02 WS-RESULT    PIC X(15).     -> message
     *     02 WS-DATE      PIC X(10).     -> dateValue
     *     02 WS-DATE-FMT  PIC X(10).     -> formatPattern
     * </pre>
     * 
     * <p>Severity codes match COBOL RETURN-CODE convention:</p>
     * <ul>
     *   <li>0 = Valid date (FC-INVALID-DATE condition, which paradoxically means valid)</li>
     *   <li>1 = Insufficient data</li>
     *   <li>2 = Date value error</li>
     *   <li>3 = Invalid month</li>
     *   <li>4 = Non-numeric data</li>
     *   <li>5 = Bad format pattern</li>
     *   <li>6+ = Additional business rule violations</li>
     * </ul>
     */
    public static class ValidationResult {
        private final boolean valid;
        private final int severityCode;
        private final String message;
        private final String dateValue;
        private final String formatPattern;
        
        /**
         * Constructs a ValidationResult.
         * 
         * @param valid true if date is valid, false otherwise
         * @param severityCode severity code (0 = valid, >0 = error)
         * @param message validation message
         * @param dateValue the date value that was validated
         * @param formatPattern the format pattern used (may be null)
         */
        public ValidationResult(boolean valid, int severityCode, String message,
                              String dateValue, String formatPattern) {
            this.valid = valid;
            this.severityCode = severityCode;
            this.message = message;
            this.dateValue = dateValue;
            this.formatPattern = formatPattern;
        }
        
        /**
         * Checks if the date validation was successful.
         * 
         * @return true if valid, false otherwise
         */
        public boolean isValid() {
            return valid;
        }
        
        /**
         * Gets the severity code (COBOL RETURN-CODE equivalent).
         * 
         * @return severity code (0 = valid, >0 = error)
         */
        public int getSeverityCode() {
            return severityCode;
        }
        
        /**
         * Gets the validation message (COBOL WS-RESULT equivalent).
         * 
         * @return validation message
         */
        public String getMessage() {
            return message;
        }
        
        /**
         * Gets the date value that was validated (COBOL WS-DATE equivalent).
         * 
         * @return date value string
         */
        public String getDateValue() {
            return dateValue;
        }
        
        /**
         * Gets the format pattern used (COBOL WS-DATE-FMT equivalent).
         * 
         * @return format pattern string, or null if not applicable
         */
        public String getFormatPattern() {
            return formatPattern;
        }
        
        /**
         * Returns a formatted string representation of the validation result.
         * 
         * <p>Format matches COBOL WS-MESSAGE structure for debugging purposes.</p>
         * 
         * @return formatted validation result
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Severity: ").append(String.format("%04d", severityCode));
            sb.append(" | Result: ").append(message);
            if (dateValue != null) {
                sb.append(" | Date: ").append(dateValue);
            }
            if (formatPattern != null) {
                sb.append(" | Format: ").append(formatPattern);
            }
            return sb.toString();
        }
    }
}

