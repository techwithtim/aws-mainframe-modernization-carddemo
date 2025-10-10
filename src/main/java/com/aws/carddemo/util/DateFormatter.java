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
 * language governing permissions and limitations under the License
 */
package com.aws.carddemo.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Date format conversion utility class migrated from COBDATFT.asm assembler module.
 * 
 * <p>This utility provides bidirectional transformation between legacy mainframe 
 * YYYYMMDD format and ISO-8601 YYYY-MM-DD format, maintaining backward compatibility 
 * with mainframe date feeds for batch processing and data migration.</p>
 * 
 * <p><b>Supported Format Conversions:</b></p>
 * <ul>
 *   <li>YYYYMMDD (8 digits, no separators) → YYYY-MM-DD (ISO-8601 with hyphens)</li>
 *   <li>YYYY-MM-DD (ISO-8601 with hyphens) → YYYYMMDD (8 digits, no separators)</li>
 * </ul>
 * 
 * <p><b>Date Validation:</b></p>
 * <p>Uses {@link SimpleDateFormat} with lenient mode disabled to ensure strict 
 * calendar date validation. Invalid dates such as February 30, April 31, or 
 * dates with invalid month/day values will be rejected with an 
 * {@link IllegalArgumentException}.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Convert legacy mainframe format to ISO-8601
 * String iso8601 = DateFormatter.formatToIso8601("20240315");
 * // Returns: "2024-03-15"
 * 
 * // Convert ISO-8601 back to legacy format
 * String legacy = DateFormatter.formatToLegacy("2024-03-15");
 * // Returns: "20240315"
 * 
 * // Invalid dates throw IllegalArgumentException
 * try {
 *     DateFormatter.formatToIso8601("20240230"); // Feb 30 is invalid
 * } catch (IllegalArgumentException e) {
 *     // Handle validation error: "INVALID INPUT: 20240230 ..."
 * }
 * </pre>
 * 
 * <p><b>Migration Notes:</b></p>
 * <ul>
 *   <li>Replaces COBDATFT.asm VALIDIN1 logic (YYYYMMDD → YYYY-MM-DD conversion)</li>
 *   <li>Replaces COBDATFT.asm VALIDIN2 logic (YYYY-MM-DD → YYYYMMDD conversion)</li>
 *   <li>Replaces GOTOERR error handling with IllegalArgumentException</li>
 *   <li>Maintains exact format specifications from assembler implementation</li>
 * </ul>
 * 
 * @author AWS Modernization Team
 * @see SimpleDateFormat
 * @since 1.0.0
 */
public final class DateFormatter {
    
    /**
     * Legacy mainframe date format pattern (YYYYMMDD).
     * Example: 20240315 represents March 15, 2024
     */
    private static final String LEGACY_FORMAT = "yyyyMMdd";
    
    /**
     * ISO-8601 date format pattern with hyphens (YYYY-MM-DD).
     * Example: 2024-03-15 represents March 15, 2024
     */
    private static final String ISO_8601_FORMAT = "yyyy-MM-dd";
    
    /**
     * Expected length of legacy format input (8 digits).
     */
    private static final int LEGACY_FORMAT_LENGTH = 8;
    
    /**
     * Expected length of ISO-8601 format input (10 characters including hyphens).
     */
    private static final int ISO_8601_FORMAT_LENGTH = 10;
    
    /**
     * Private constructor to prevent instantiation of utility class.
     * 
     * @throws UnsupportedOperationException if instantiation is attempted
     */
    private DateFormatter() {
        throw new UnsupportedOperationException("DateFormatter is a utility class and cannot be instantiated");
    }
    
    /**
     * Converts a date string from legacy mainframe format (YYYYMMDD) to ISO-8601 format (YYYY-MM-DD).
     * 
     * <p>This method replicates the VALIDIN1 logic from COBDATFT.asm:</p>
     * <pre>
     * VALIDIN1:
     *     MVC   COOUTDT(4),COINPDT      # Copy YYYY
     *     MVI   COOUTDT+4,C'-'          # Insert hyphen
     *     MVC   COOUTDT+5(2),COINPDT+4  # Copy MM
     *     MVI   COOUTDT+7,C'-'          # Insert hyphen
     *     MVC   COOUTDT+8(2),COINPDT+6  # Copy DD
     * </pre>
     * 
     * <p><b>Input Format:</b> YYYYMMDD (8 digits, no separators)</p>
     * <p><b>Output Format:</b> YYYY-MM-DD (10 characters with hyphens)</p>
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li>Input must be exactly 8 characters long</li>
     *   <li>Input must contain only digits</li>
     *   <li>Input must represent a valid calendar date (no Feb 30, Apr 31, etc.)</li>
     *   <li>Year must be 4 digits (1000-9999)</li>
     *   <li>Month must be 01-12</li>
     *   <li>Day must be valid for the specified month and year</li>
     * </ul>
     * 
     * <p><b>Examples:</b></p>
     * <pre>
     * formatToIso8601("20240315") → "2024-03-15"  (Valid date)
     * formatToIso8601("20240229") → "2024-02-29"  (Valid leap year date)
     * formatToIso8601("19991231") → "1999-12-31"  (End of millennium)
     * formatToIso8601("20240230") → IllegalArgumentException (Feb 30 invalid)
     * formatToIso8601("20241301") → IllegalArgumentException (Month 13 invalid)
     * formatToIso8601("2024031")  → IllegalArgumentException (Too short)
     * formatToIso8601("2024-03-15") → IllegalArgumentException (Contains hyphens)
     * </pre>
     * 
     * @param yyyymmdd the date string in YYYYMMDD format (8 digits)
     * @return the date string converted to YYYY-MM-DD format (10 characters with hyphens)
     * @throws IllegalArgumentException if the input is null, empty, not 8 characters long, 
     *         contains non-digit characters, or represents an invalid calendar date
     */
    public static String formatToIso8601(String yyyymmdd) {
        // Validate input is not null or empty
        if (yyyymmdd == null || yyyymmdd.isEmpty()) {
            throw new IllegalArgumentException("INVALID INPUT: Date string cannot be null or empty");
        }
        
        // Validate input length matches YYYYMMDD format (8 characters)
        if (yyyymmdd.length() != LEGACY_FORMAT_LENGTH) {
            throw new IllegalArgumentException(
                String.format("INVALID INPUT: Expected %d characters for YYYYMMDD format, but got %d: %s",
                    LEGACY_FORMAT_LENGTH, yyyymmdd.length(), yyyymmdd)
            );
        }
        
        // Validate input contains only digits (no hyphens or other characters)
        if (!yyyymmdd.matches("\\d{8}")) {
            throw new IllegalArgumentException(
                String.format("INVALID INPUT: YYYYMMDD format must contain only digits: %s", yyyymmdd)
            );
        }
        
        // Parse and validate the date using SimpleDateFormat with strict validation
        SimpleDateFormat legacyFormatter = new SimpleDateFormat(LEGACY_FORMAT);
        legacyFormatter.setLenient(false); // Enforce strict calendar date validation
        
        try {
            // Parse the legacy format date to ensure it's a valid calendar date
            Date parsedDate = legacyFormatter.parse(yyyymmdd);
            
            // Format the validated date to ISO-8601 format
            SimpleDateFormat iso8601Formatter = new SimpleDateFormat(ISO_8601_FORMAT);
            iso8601Formatter.setLenient(false);
            
            return iso8601Formatter.format(parsedDate);
            
        } catch (ParseException e) {
            throw new IllegalArgumentException(
                String.format("INVALID INPUT: %s is not a valid calendar date: %s", 
                    yyyymmdd, e.getMessage()),
                e
            );
        }
    }
    
    /**
     * Converts a date string from ISO-8601 format (YYYY-MM-DD) to legacy mainframe format (YYYYMMDD).
     * 
     * <p>This method replicates the VALIDIN2 logic from COBDATFT.asm:</p>
     * <pre>
     * VALIDIN2:
     *     MVC   COOUTDT(4),COINPDT      # Copy YYYY
     *     MVC   COOUTDT+4(2),COINPDT+5  # Copy MM (skip hyphen at position 4)
     *     MVC   COOUTDT+6(2),COINPDT+8  # Copy DD (skip hyphen at position 7)
     * </pre>
     * 
     * <p><b>Input Format:</b> YYYY-MM-DD (10 characters with hyphens)</p>
     * <p><b>Output Format:</b> YYYYMMDD (8 digits, no separators)</p>
     * 
     * <p><b>Validation Rules:</b></p>
     * <ul>
     *   <li>Input must be exactly 10 characters long</li>
     *   <li>Input must have hyphens at positions 4 and 7 (zero-indexed)</li>
     *   <li>Input must represent a valid calendar date (no Feb 30, Apr 31, etc.)</li>
     *   <li>Year must be 4 digits (1000-9999)</li>
     *   <li>Month must be 01-12</li>
     *   <li>Day must be valid for the specified month and year</li>
     * </ul>
     * 
     * <p><b>Examples:</b></p>
     * <pre>
     * formatToLegacy("2024-03-15") → "20240315"  (Valid date)
     * formatToLegacy("2024-02-29") → "20240229"  (Valid leap year date)
     * formatToLegacy("1999-12-31") → "19991231"  (End of millennium)
     * formatToLegacy("2024-02-30") → IllegalArgumentException (Feb 30 invalid)
     * formatToLegacy("2024-13-01") → IllegalArgumentException (Month 13 invalid)
     * formatToLegacy("2024-3-15")  → IllegalArgumentException (Wrong format)
     * formatToLegacy("20240315")   → IllegalArgumentException (Missing hyphens)
     * </pre>
     * 
     * @param iso8601 the date string in YYYY-MM-DD format (10 characters with hyphens)
     * @return the date string converted to YYYYMMDD format (8 digits)
     * @throws IllegalArgumentException if the input is null, empty, not 10 characters long,
     *         doesn't have hyphens in the correct positions, or represents an invalid calendar date
     */
    public static String formatToLegacy(String iso8601) {
        // Validate input is not null or empty
        if (iso8601 == null || iso8601.isEmpty()) {
            throw new IllegalArgumentException("INVALID INPUT: Date string cannot be null or empty");
        }
        
        // Validate input length matches YYYY-MM-DD format (10 characters)
        if (iso8601.length() != ISO_8601_FORMAT_LENGTH) {
            throw new IllegalArgumentException(
                String.format("INVALID INPUT: Expected %d characters for YYYY-MM-DD format, but got %d: %s",
                    ISO_8601_FORMAT_LENGTH, iso8601.length(), iso8601)
            );
        }
        
        // Validate input matches YYYY-MM-DD pattern (4 digits, hyphen, 2 digits, hyphen, 2 digits)
        if (!iso8601.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException(
                String.format("INVALID INPUT: YYYY-MM-DD format must match pattern ####-##-##: %s", iso8601)
            );
        }
        
        // Parse and validate the date using SimpleDateFormat with strict validation
        SimpleDateFormat iso8601Formatter = new SimpleDateFormat(ISO_8601_FORMAT);
        iso8601Formatter.setLenient(false); // Enforce strict calendar date validation
        
        try {
            // Parse the ISO-8601 format date to ensure it's a valid calendar date
            Date parsedDate = iso8601Formatter.parse(iso8601);
            
            // Format the validated date to legacy mainframe format
            SimpleDateFormat legacyFormatter = new SimpleDateFormat(LEGACY_FORMAT);
            legacyFormatter.setLenient(false);
            
            return legacyFormatter.format(parsedDate);
            
        } catch (ParseException e) {
            throw new IllegalArgumentException(
                String.format("INVALID INPUT: %s is not a valid calendar date: %s", 
                    iso8601, e.getMessage()),
                e
            );
        }
    }
}
