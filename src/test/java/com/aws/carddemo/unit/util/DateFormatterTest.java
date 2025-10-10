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
package com.aws.carddemo.unit.util;

import com.aws.carddemo.util.DateFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive unit test class for {@link DateFormatter} utility proving bidirectional 
 * date format transformation YYYYMMDD ↔ YYYY-MM-DD maintains functional equivalence 
 * with COBDATFT.asm assembler logic.
 * 
 * <p>This test suite validates the migration from mainframe assembler date formatting
 * (COBDATFT.asm VALIDIN1/VALIDIN2 routines) to modern Java implementations, ensuring
 * complete behavioral parity including:</p>
 * <ul>
 *   <li>Standard date conversions in both directions</li>
 *   <li>Boundary date handling (January 1, December 31)</li>
 *   <li>Leap year date validation (February 29 in leap/non-leap years)</li>
 *   <li>Invalid input rejection (null, empty, malformed dates)</li>
 *   <li>Invalid calendar date rejection (February 30, month 13, day 32)</li>
 * </ul>
 * 
 * <p><b>Test Coverage Requirements:</b></p>
 * <ul>
 *   <li>Line Coverage: ≥80% per Agent Action Plan Section 0.8.1</li>
 *   <li>Branch Coverage: ≥70% with comprehensive edge case validation</li>
 *   <li>Functional Equivalence: Proves COBDATFT.asm algorithmic correctness</li>
 * </ul>
 * 
 * <p><b>Assembler Logic Mapping:</b></p>
 * <pre>
 * VALIDIN1 (Type 1): YYYYMMDD → YYYY-MM-DD
 *     MVC   COOUTDT(4),COINPDT      # Copy YYYY → formatToIso8601()
 *     MVI   COOUTDT+4,C'-'          # Insert hyphen
 *     MVC   COOUTDT+5(2),COINPDT+4  # Copy MM
 *     MVI   COOUTDT+7,C'-'          # Insert hyphen
 *     MVC   COOUTDT+8(2),COINPDT+6  # Copy DD
 * 
 * VALIDIN2 (Type 2): YYYY-MM-DD → YYYYMMDD
 *     MVC   COOUTDT(4),COINPDT      # Copy YYYY → formatToLegacy()
 *     MVC   COOUTDT+4(2),COINPDT+5  # Copy MM (skip hyphen)
 *     MVC   COOUTDT+6(2),COINPDT+8  # Copy DD (skip hyphen)
 * </pre>
 * 
 * @author AWS Modernization Team
 * @see DateFormatter
 * @since 1.0.0
 */
@DisplayName("DateFormatter Unit Tests - Bidirectional Date Format Transformation (COBDATFT.asm Migration)")
class DateFormatterTest {

    // ==================================================================================
    // Test Data Providers for Parameterized Tests
    // ==================================================================================

    /**
     * Provides comprehensive test cases for formatToIso8601() method testing 
     * YYYYMMDD → YYYY-MM-DD conversion.
     * 
     * <p>Test case categories:</p>
     * <ul>
     *   <li>Standard dates across different months</li>
     *   <li>Boundary dates (first and last day of year)</li>
     *   <li>Leap year dates (February 29 in leap years)</li>
     *   <li>Edge cases (millennium boundary, current era dates)</li>
     * </ul>
     * 
     * @return stream of test arguments (input YYYYMMDD, expected YYYY-MM-DD)
     */
    static Stream<Arguments> provideValidLegacyToIso8601TestCases() {
        return Stream.of(
            // Standard date conversions across different months
            Arguments.of("20230115", "2023-01-15"),  // Mid-January
            Arguments.of("20230315", "2023-03-15"),  // Mid-March
            Arguments.of("20230615", "2023-06-15"),  // Mid-June
            Arguments.of("20231015", "2023-10-15"),  // Mid-October
            Arguments.of("20231215", "2023-12-15"),  // Mid-December
            
            // Boundary dates - first day of year
            Arguments.of("20230101", "2023-01-01"),  // January 1, 2023
            Arguments.of("20240101", "2024-01-01"),  // January 1, 2024 (leap year)
            Arguments.of("19990101", "1999-01-01"),  // January 1, 1999
            Arguments.of("20000101", "2000-01-01"),  // January 1, 2000 (millennium leap year)
            
            // Boundary dates - last day of year
            Arguments.of("20231231", "2023-12-31"),  // December 31, 2023
            Arguments.of("20241231", "2024-12-31"),  // December 31, 2024 (leap year)
            Arguments.of("19991231", "1999-12-31"),  // December 31, 1999 (millennium boundary)
            
            // Leap year dates - February 29 in valid leap years
            Arguments.of("20240229", "2024-02-29"),  // Feb 29, 2024 (leap year - divisible by 4)
            Arguments.of("20200229", "2020-02-29"),  // Feb 29, 2020 (leap year)
            Arguments.of("20000229", "2000-02-29"),  // Feb 29, 2000 (leap year - divisible by 400)
            
            // End-of-month dates for different month lengths
            Arguments.of("20230228", "2023-02-28"),  // Feb 28 in non-leap year
            Arguments.of("20230430", "2023-04-30"),  // April 30 (30-day month)
            Arguments.of("20230531", "2023-05-31"),  // May 31 (31-day month)
            Arguments.of("20230630", "2023-06-30"),  // June 30 (30-day month)
            Arguments.of("20230930", "2023-09-30"),  // September 30 (30-day month)
            Arguments.of("20231031", "2023-10-31"),  // October 31 (31-day month)
            
            // Historical and future dates
            Arguments.of("19700101", "1970-01-01"),  // Unix epoch
            Arguments.of("20501231", "2050-12-31"),  // Future date
            Arguments.of("21000101", "2100-01-01")   // 22nd century (not a leap year - divisible by 100 but not 400)
        );
    }

    /**
     * Provides comprehensive test cases for formatToLegacy() method testing 
     * YYYY-MM-DD → YYYYMMDD conversion.
     * 
     * <p>Test case categories:</p>
     * <ul>
     *   <li>Standard dates across different months</li>
     *   <li>Boundary dates (first and last day of year)</li>
     *   <li>Leap year dates (February 29 in leap years)</li>
     *   <li>Edge cases (millennium boundary, current era dates)</li>
     * </ul>
     * 
     * @return stream of test arguments (input YYYY-MM-DD, expected YYYYMMDD)
     */
    static Stream<Arguments> provideValidIso8601ToLegacyTestCases() {
        return Stream.of(
            // Standard date conversions across different months
            Arguments.of("2023-01-15", "20230115"),  // Mid-January
            Arguments.of("2023-03-15", "20230315"),  // Mid-March
            Arguments.of("2023-06-15", "20230615"),  // Mid-June
            Arguments.of("2023-10-15", "20231015"),  // Mid-October
            Arguments.of("2023-12-15", "20231215"),  // Mid-December
            
            // Boundary dates - first day of year
            Arguments.of("2023-01-01", "20230101"),  // January 1, 2023
            Arguments.of("2024-01-01", "20240101"),  // January 1, 2024 (leap year)
            Arguments.of("1999-01-01", "19990101"),  // January 1, 1999
            Arguments.of("2000-01-01", "20000101"),  // January 1, 2000 (millennium leap year)
            
            // Boundary dates - last day of year
            Arguments.of("2023-12-31", "20231231"),  // December 31, 2023
            Arguments.of("2024-12-31", "20241231"),  // December 31, 2024 (leap year)
            Arguments.of("1999-12-31", "19991231"),  // December 31, 1999 (millennium boundary)
            
            // Leap year dates - February 29 in valid leap years
            Arguments.of("2024-02-29", "20240229"),  // Feb 29, 2024 (leap year - divisible by 4)
            Arguments.of("2020-02-29", "20200229"),  // Feb 29, 2020 (leap year)
            Arguments.of("2000-02-29", "20000229"),  // Feb 29, 2000 (leap year - divisible by 400)
            
            // End-of-month dates for different month lengths
            Arguments.of("2023-02-28", "20230228"),  // Feb 28 in non-leap year
            Arguments.of("2023-04-30", "20230430"),  // April 30 (30-day month)
            Arguments.of("2023-05-31", "20230531"),  // May 31 (31-day month)
            Arguments.of("2023-06-30", "20230630"),  // June 30 (30-day month)
            Arguments.of("2023-09-30", "20230930"),  // September 30 (30-day month)
            Arguments.of("2023-10-31", "20231031"),  // October 31 (31-day month)
            
            // Historical and future dates
            Arguments.of("1970-01-01", "19700101"),  // Unix epoch
            Arguments.of("2050-12-31", "20501231"),  // Future date
            Arguments.of("2100-01-01", "21000101")   // 22nd century (not a leap year)
        );
    }

    /**
     * Provides test cases for invalid legacy format (YYYYMMDD) inputs that should 
     * throw IllegalArgumentException.
     * 
     * <p>Invalid input categories:</p>
     * <ul>
     *   <li>Invalid calendar dates (Feb 30, Apr 31, etc.)</li>
     *   <li>Invalid month values (month 00, month 13)</li>
     *   <li>Invalid day values (day 00, day 32)</li>
     *   <li>February 29 in non-leap years</li>
     * </ul>
     * 
     * @return stream of invalid legacy format inputs
     */
    static Stream<Arguments> provideInvalidLegacyFormatTestCases() {
        return Stream.of(
            // Invalid month values
            Arguments.of("20230001", "Day 00 is invalid"),
            Arguments.of("20231301", "Month 13 is invalid"),
            Arguments.of("20230013", "Month 00 is invalid"),
            
            // Invalid day values
            Arguments.of("20230132", "Day 32 in January is invalid"),
            Arguments.of("20230100", "Day 00 is invalid"),
            
            // Invalid dates for specific months
            Arguments.of("20230230", "February 30 does not exist"),
            Arguments.of("20230231", "February 31 does not exist"),
            Arguments.of("20230431", "April 31 does not exist (30-day month)"),
            Arguments.of("20230631", "June 31 does not exist (30-day month)"),
            Arguments.of("20230931", "September 31 does not exist (30-day month)"),
            Arguments.of("20231131", "November 31 does not exist (30-day month)"),
            
            // February 29 in non-leap years
            Arguments.of("20230229", "February 29, 2023 is invalid (non-leap year)"),
            Arguments.of("21000229", "February 29, 2100 is invalid (not a leap year - divisible by 100 but not 400)"),
            Arguments.of("19000229", "February 29, 1900 is invalid (not a leap year - divisible by 100 but not 400)")
        );
    }

    /**
     * Provides test cases for invalid ISO-8601 format (YYYY-MM-DD) inputs that should 
     * throw IllegalArgumentException.
     * 
     * <p>Invalid input categories:</p>
     * <ul>
     *   <li>Invalid calendar dates (Feb 30, Apr 31, etc.)</li>
     *   <li>Invalid month values (month 00, month 13)</li>
     *   <li>Invalid day values (day 00, day 32)</li>
     *   <li>February 29 in non-leap years</li>
     * </ul>
     * 
     * @return stream of invalid ISO-8601 format inputs
     */
    static Stream<Arguments> provideInvalidIso8601FormatTestCases() {
        return Stream.of(
            // Invalid month values
            Arguments.of("2023-00-01", "Month 00 is invalid"),
            Arguments.of("2023-13-01", "Month 13 is invalid"),
            
            // Invalid day values
            Arguments.of("2023-01-00", "Day 00 is invalid"),
            Arguments.of("2023-01-32", "Day 32 in January is invalid"),
            
            // Invalid dates for specific months
            Arguments.of("2023-02-30", "February 30 does not exist"),
            Arguments.of("2023-02-31", "February 31 does not exist"),
            Arguments.of("2023-04-31", "April 31 does not exist (30-day month)"),
            Arguments.of("2023-06-31", "June 31 does not exist (30-day month)"),
            Arguments.of("2023-09-31", "September 31 does not exist (30-day month)"),
            Arguments.of("2023-11-31", "November 31 does not exist (30-day month)"),
            
            // February 29 in non-leap years
            Arguments.of("2023-02-29", "February 29, 2023 is invalid (non-leap year)"),
            Arguments.of("2100-02-29", "February 29, 2100 is invalid (not a leap year - divisible by 100 but not 400)"),
            Arguments.of("1900-02-29", "February 29, 1900 is invalid (not a leap year - divisible by 100 but not 400)")
        );
    }

    // ==================================================================================
    // Parameterized Tests - Valid Date Conversions
    // ==================================================================================

    @ParameterizedTest
    @MethodSource("provideValidLegacyToIso8601TestCases")
    @DisplayName("formatToIso8601() should convert valid YYYYMMDD dates to YYYY-MM-DD format (COBDATFT.asm VALIDIN1 logic)")
    void testFormatToIso8601WithValidDates(String legacyFormat, String expectedIso8601) {
        // Execute the conversion
        String actualIso8601 = DateFormatter.formatToIso8601(legacyFormat);
        
        // Verify the result matches expected ISO-8601 format
        assertThat(actualIso8601)
            .as("Converting %s to ISO-8601 format", legacyFormat)
            .isNotNull()
            .isEqualTo(expectedIso8601);
    }

    @ParameterizedTest
    @MethodSource("provideValidIso8601ToLegacyTestCases")
    @DisplayName("formatToLegacy() should convert valid YYYY-MM-DD dates to YYYYMMDD format (COBDATFT.asm VALIDIN2 logic)")
    void testFormatToLegacyWithValidDates(String iso8601Format, String expectedLegacy) {
        // Execute the conversion
        String actualLegacy = DateFormatter.formatToLegacy(iso8601Format);
        
        // Verify the result matches expected legacy format
        assertThat(actualLegacy)
            .as("Converting %s to legacy YYYYMMDD format", iso8601Format)
            .isNotNull()
            .isEqualTo(expectedLegacy);
    }

    // ==================================================================================
    // Bidirectional Conversion Tests - Round-Trip Validation
    // ==================================================================================

    @Test
    @DisplayName("Bidirectional conversion: YYYYMMDD → YYYY-MM-DD → YYYYMMDD should return original value")
    void testBidirectionalConversionLegacyToIso8601AndBack() {
        // Test with a standard date
        String originalLegacy = "20230315";
        
        // Convert to ISO-8601
        String iso8601 = DateFormatter.formatToIso8601(originalLegacy);
        assertThat(iso8601).isEqualTo("2023-03-15");
        
        // Convert back to legacy format
        String convertedBackToLegacy = DateFormatter.formatToLegacy(iso8601);
        
        // Verify round-trip conversion preserves original value
        assertThat(convertedBackToLegacy)
            .as("Round-trip conversion should preserve original value")
            .isEqualTo(originalLegacy);
    }

    @Test
    @DisplayName("Bidirectional conversion: YYYY-MM-DD → YYYYMMDD → YYYY-MM-DD should return original value")
    void testBidirectionalConversionIso8601ToLegacyAndBack() {
        // Test with a standard date
        String originalIso8601 = "2023-03-15";
        
        // Convert to legacy format
        String legacy = DateFormatter.formatToLegacy(originalIso8601);
        assertThat(legacy).isEqualTo("20230315");
        
        // Convert back to ISO-8601 format
        String convertedBackToIso8601 = DateFormatter.formatToIso8601(legacy);
        
        // Verify round-trip conversion preserves original value
        assertThat(convertedBackToIso8601)
            .as("Round-trip conversion should preserve original value")
            .isEqualTo(originalIso8601);
    }

    @Test
    @DisplayName("Bidirectional conversion: Leap year date Feb 29, 2024 should preserve value in both directions")
    void testBidirectionalConversionWithLeapYearDate() {
        // Test with leap year date
        String originalLegacy = "20240229";
        String originalIso8601 = "2024-02-29";
        
        // Convert legacy to ISO-8601 and back
        String iso8601 = DateFormatter.formatToIso8601(originalLegacy);
        String backToLegacy = DateFormatter.formatToLegacy(iso8601);
        
        assertThat(iso8601).isEqualTo(originalIso8601);
        assertThat(backToLegacy).isEqualTo(originalLegacy);
        
        // Convert ISO-8601 to legacy and back
        String legacy = DateFormatter.formatToLegacy(originalIso8601);
        String backToIso8601 = DateFormatter.formatToIso8601(legacy);
        
        assertThat(legacy).isEqualTo(originalLegacy);
        assertThat(backToIso8601).isEqualTo(originalIso8601);
    }

    // ==================================================================================
    // Invalid Input Tests - Null and Empty Strings
    // ==================================================================================

    @Test
    @DisplayName("formatToIso8601() should throw IllegalArgumentException for null input (COBDATFT.asm GOTOERR)")
    void testFormatToIso8601WithNullInput() {
        assertThatThrownBy(() -> DateFormatter.formatToIso8601(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("formatToIso8601() should throw IllegalArgumentException for empty string input")
    void testFormatToIso8601WithEmptyString() {
        assertThatThrownBy(() -> DateFormatter.formatToIso8601(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("formatToLegacy() should throw IllegalArgumentException for null input (COBDATFT.asm GOTOERR)")
    void testFormatToLegacyWithNullInput() {
        assertThatThrownBy(() -> DateFormatter.formatToLegacy(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("formatToLegacy() should throw IllegalArgumentException for empty string input")
    void testFormatToLegacyWithEmptyString() {
        assertThatThrownBy(() -> DateFormatter.formatToLegacy(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("cannot be null or empty");
    }

    // ==================================================================================
    // Invalid Input Tests - Incorrect Length
    // ==================================================================================

    @Test
    @DisplayName("formatToIso8601() should throw IllegalArgumentException for input shorter than 8 characters")
    void testFormatToIso8601WithShortInput() {
        assertThatThrownBy(() -> DateFormatter.formatToIso8601("2023031"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("Expected 8 characters");
    }

    @Test
    @DisplayName("formatToIso8601() should throw IllegalArgumentException for input longer than 8 characters")
    void testFormatToIso8601WithLongInput() {
        assertThatThrownBy(() -> DateFormatter.formatToIso8601("202303155"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("Expected 8 characters");
    }

    @Test
    @DisplayName("formatToLegacy() should throw IllegalArgumentException for input shorter than 10 characters")
    void testFormatToLegacyWithShortInput() {
        assertThatThrownBy(() -> DateFormatter.formatToLegacy("2023-03-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("Expected 10 characters");
    }

    @Test
    @DisplayName("formatToLegacy() should throw IllegalArgumentException for input longer than 10 characters")
    void testFormatToLegacyWithLongInput() {
        assertThatThrownBy(() -> DateFormatter.formatToLegacy("2023-03-155"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("Expected 10 characters");
    }

    // ==================================================================================
    // Invalid Input Tests - Malformed Format
    // ==================================================================================

    @Test
    @DisplayName("formatToIso8601() should throw IllegalArgumentException for input containing non-digit characters")
    void testFormatToIso8601WithNonDigitCharacters() {
        assertThatThrownBy(() -> DateFormatter.formatToIso8601("2023-315"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("must contain only digits");
    }

    @Test
    @DisplayName("formatToIso8601() should throw IllegalArgumentException for input containing letters")
    void testFormatToIso8601WithLetters() {
        assertThatThrownBy(() -> DateFormatter.formatToIso8601("2023031A"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("must contain only digits");
    }

    @Test
    @DisplayName("formatToLegacy() should throw IllegalArgumentException for input without hyphens")
    void testFormatToLegacyWithoutHyphens() {
        // Input "20230315" has length 8, not 10, so it fails length validation first
        assertThatThrownBy(() -> DateFormatter.formatToLegacy("20230315"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("Expected 10 characters");
    }

    @Test
    @DisplayName("formatToLegacy() should throw IllegalArgumentException for input with incorrect hyphen positions")
    void testFormatToLegacyWithIncorrectHyphenPositions() {
        // Input "2023-3-15" has length 9, not 10, so it fails length validation first
        assertThatThrownBy(() -> DateFormatter.formatToLegacy("2023-3-15"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("Expected 10 characters");
    }

    @Test
    @DisplayName("formatToLegacy() should throw IllegalArgumentException for input with slashes instead of hyphens")
    void testFormatToLegacyWithSlashes() {
        // Input "2023/03/15" has correct length (10) but wrong pattern (slashes instead of hyphens)
        assertThatThrownBy(() -> DateFormatter.formatToLegacy("2023/03/15"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("must match pattern");
    }
    
    @Test
    @DisplayName("formatToLegacy() should throw IllegalArgumentException for input with letters")
    void testFormatToLegacyWithLettersInInput() {
        // Input "202A-03-15" has correct length (10) but contains letters
        assertThatThrownBy(() -> DateFormatter.formatToLegacy("202A-03-15"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT")
            .hasMessageContaining("must match pattern");
    }

    // ==================================================================================
    // Invalid Input Tests - Invalid Calendar Dates (Parameterized)
    // ==================================================================================

    @ParameterizedTest
    @MethodSource("provideInvalidLegacyFormatTestCases")
    @DisplayName("formatToIso8601() should throw IllegalArgumentException for invalid calendar dates")
    void testFormatToIso8601WithInvalidCalendarDates(String invalidDate, String description) {
        assertThatThrownBy(() -> DateFormatter.formatToIso8601(invalidDate))
            .as("Testing invalid date: %s - %s", invalidDate, description)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidIso8601FormatTestCases")
    @DisplayName("formatToLegacy() should throw IllegalArgumentException for invalid calendar dates")
    void testFormatToLegacyWithInvalidCalendarDates(String invalidDate, String description) {
        assertThatThrownBy(() -> DateFormatter.formatToLegacy(invalidDate))
            .as("Testing invalid date: %s - %s", invalidDate, description)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID INPUT");
    }

    // ==================================================================================
    // Edge Case Tests - Specific Boundary Conditions
    // ==================================================================================

    @Test
    @DisplayName("formatToIso8601() should correctly handle millennium boundary date (January 1, 2000)")
    void testFormatToIso8601WithMillenniumBoundary() {
        String result = DateFormatter.formatToIso8601("20000101");
        assertThat(result).isEqualTo("2000-01-01");
    }

    @Test
    @DisplayName("formatToIso8601() should correctly handle Y2K eve date (December 31, 1999)")
    void testFormatToIso8601WithY2KEve() {
        String result = DateFormatter.formatToIso8601("19991231");
        assertThat(result).isEqualTo("1999-12-31");
    }

    @Test
    @DisplayName("formatToLegacy() should correctly handle millennium boundary date (January 1, 2000)")
    void testFormatToLegacyWithMillenniumBoundary() {
        String result = DateFormatter.formatToLegacy("2000-01-01");
        assertThat(result).isEqualTo("20000101");
    }

    @Test
    @DisplayName("formatToLegacy() should correctly handle Y2K eve date (December 31, 1999)")
    void testFormatToLegacyWithY2KEve() {
        String result = DateFormatter.formatToLegacy("1999-12-31");
        assertThat(result).isEqualTo("19991231");
    }

    @Test
    @DisplayName("formatToIso8601() should correctly handle leap year century date (February 29, 2000)")
    void testFormatToIso8601WithLeapYearCenturyDate() {
        // 2000 is a leap year because it's divisible by 400
        String result = DateFormatter.formatToIso8601("20000229");
        assertThat(result).isEqualTo("2000-02-29");
    }

    @Test
    @DisplayName("formatToLegacy() should correctly handle leap year century date (February 29, 2000)")
    void testFormatToLegacyWithLeapYearCenturyDate() {
        // 2000 is a leap year because it's divisible by 400
        String result = DateFormatter.formatToLegacy("2000-02-29");
        assertThat(result).isEqualTo("20000229");
    }

    // ==================================================================================
    // Utility Class Instantiation Tests
    // ==================================================================================

    @Test
    @DisplayName("DateFormatter utility class should not be instantiable")
    void testDateFormatterCannotBeInstantiated() {
        // Verify that attempting to instantiate the utility class throws an exception
        assertThatThrownBy(() -> {
            // Use reflection to attempt instantiation of the private constructor
            java.lang.reflect.Constructor<DateFormatter> constructor = 
                DateFormatter.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class)
        .cause()
        .hasMessageContaining("DateFormatter is a utility class and cannot be instantiated");
    }
}
