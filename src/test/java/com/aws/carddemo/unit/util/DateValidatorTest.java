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

package com.aws.carddemo.unit.util;

import com.aws.carddemo.util.DateValidator;
import com.aws.carddemo.util.DateValidator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit test class for {@link DateValidator} utility.
 * 
 * <p>This test class proves functional equivalence with COBOL program CSUTLDTC.cbl,
 * which wraps the Language Environment CEEDAYS API for calendar date validation.
 * Test coverage includes leap year handling, boundary dates, invalid date rejection,
 * format validation, and business day calculations.</p>
 * 
 * <p><b>COBOL Source Reference:</b> app/cbl/CSUTLDTC.cbl</p>
 * 
 * <p><b>Test Coverage Goals:</b></p>
 * <ul>
 *   <li>≥80% line coverage of DateValidator class</li>
 *   <li>≥70% branch coverage for all validation logic</li>
 *   <li>Prove functional parity with CEEDAYS feedback codes</li>
 *   <li>Validate leap year logic (2024, 2000 valid; 2023, 1900 invalid)</li>
 *   <li>Test boundary conditions (Jan 1, Dec 31, Feb 28/29)</li>
 *   <li>Validate month range (1-12) and day range per month</li>
 *   <li>Test multiple date formats (yyyy-MM-dd, yyyyMMdd, MM/dd/yyyy)</li>
 * </ul>
 * 
 * @author AWS CardDemo Modernization Team
 * @since 1.0.0
 */
@DisplayName("DateValidator Unit Tests")
class DateValidatorTest {

    private DateValidator dateValidator;

    @BeforeEach
    void setUp() {
        dateValidator = new DateValidator();
    }

    // ========================================================================
    // Parameterized Tests for isValidDate() - Valid Dates with Multiple Formats
    // ========================================================================

    @ParameterizedTest
    @DisplayName("Valid dates with yyyy-MM-dd format should return valid result")
    @CsvSource({
        "2023-01-15, yyyy-MM-dd",
        "2024-02-29, yyyy-MM-dd",  // Leap year
        "2023-12-31, yyyy-MM-dd",  // Boundary: end of year
        "2023-01-01, yyyy-MM-dd",  // Boundary: start of year
        "2023-02-28, yyyy-MM-dd",  // Non-leap year Feb 28
        "2024-02-29, yyyy-MM-dd",  // Leap year Feb 29
        "2023-06-15, yyyy-MM-dd",  // Mid-year date
        "2023-04-30, yyyy-MM-dd",  // Last day of 30-day month
        "2023-03-31, yyyy-MM-dd",  // Last day of 31-day month
        "1900-01-01, yyyy-MM-dd",  // Boundary: min reasonable date
    })
    void testIsValidDate_ValidDatesWithStandardFormat(String dateStr, String format) {
        ValidationResult result = dateValidator.isValidDate(dateStr, format);
        
        assertTrue(result.isValid(), 
            String.format("Date %s with format %s should be valid", dateStr, format));
        assertEquals(0, result.getSeverityCode(), 
            "Valid date should have severity code 0");
        assertEquals("Date is valid", result.getMessage(),
            "Valid date should have 'Date is valid' message");
        assertEquals(dateStr, result.getDateValue(),
            "Result should contain the original date value");
        assertEquals(format, result.getFormatPattern(),
            "Result should contain the format pattern");
    }

    @ParameterizedTest
    @DisplayName("Valid dates with yyyyMMdd format should return valid result")
    @CsvSource({
        "20230115, yyyyMMdd",
        "20240229, yyyyMMdd",  // Leap year
        "20231231, yyyyMMdd",  // Boundary
        "20230101, yyyyMMdd",  // Boundary
        "20230630, yyyyMMdd",
        "19000101, yyyyMMdd",  // Min reasonable date
    })
    void testIsValidDate_ValidDatesWithCompactFormat(String dateStr, String format) {
        ValidationResult result = dateValidator.isValidDate(dateStr, format);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getSeverityCode());
        assertEquals("Date is valid", result.getMessage());
    }

    @ParameterizedTest
    @DisplayName("Valid dates with MM/dd/yyyy format should return valid result")
    @CsvSource({
        "01/15/2023, MM/dd/yyyy",
        "02/29/2024, MM/dd/yyyy",  // Leap year
        "12/31/2023, MM/dd/yyyy",  // Boundary
        "01/01/2023, MM/dd/yyyy",  // Boundary
        "06/15/2023, MM/dd/yyyy",
    })
    void testIsValidDate_ValidDatesWithSlashFormat(String dateStr, String format) {
        ValidationResult result = dateValidator.isValidDate(dateStr, format);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getSeverityCode());
        assertEquals("Date is valid", result.getMessage());
    }

    // ========================================================================
    // Leap Year Validation Tests - Critical for Feb 29 Validation
    // ========================================================================

    @ParameterizedTest
    @DisplayName("Leap year Feb 29 dates should be valid (2024, 2000)")
    @MethodSource("provideLeapYearValidDates")
    void testIsValidDate_LeapYearFeb29Valid(String dateStr, String format, String description) {
        ValidationResult result = dateValidator.isValidDate(dateStr, format);
        
        assertTrue(result.isValid(), 
            String.format("Leap year date %s (%s) should be valid", dateStr, description));
        assertEquals(0, result.getSeverityCode());
        assertEquals("Date is valid", result.getMessage());
    }

    private static Stream<Arguments> provideLeapYearValidDates() {
        return Stream.of(
            Arguments.of("2024-02-29", "yyyy-MM-dd", "2024 is leap year (divisible by 4)"),
            Arguments.of("2000-02-29", "yyyy-MM-dd", "2000 is leap year (divisible by 400)"),
            Arguments.of("2020-02-29", "yyyy-MM-dd", "2020 is leap year (divisible by 4)"),
            Arguments.of("2028-02-29", "yyyy-MM-dd", "2028 is leap year (divisible by 4)"),
            Arguments.of("20240229", "yyyyMMdd", "2024 leap year compact format"),
            Arguments.of("20000229", "yyyyMMdd", "2000 leap year compact format")
        );
    }

    @ParameterizedTest
    @DisplayName("Non-leap year Feb 29 dates should be invalid (2023, 1900)")
    @MethodSource("provideNonLeapYearInvalidDates")
    void testIsValidDate_NonLeapYearFeb29Invalid(String dateStr, String format, String description) {
        ValidationResult result = dateValidator.isValidDate(dateStr, format);
        
        assertFalse(result.isValid(), 
            String.format("Non-leap year date %s (%s) should be invalid", dateStr, description));
        assertTrue(result.getSeverityCode() > 0,
            "Invalid date should have severity code > 0");
        assertNotEquals("Date is valid", result.getMessage(),
            "Invalid date should not have 'Date is valid' message");
    }

    private static Stream<Arguments> provideNonLeapYearInvalidDates() {
        return Stream.of(
            Arguments.of("2023-02-29", "yyyy-MM-dd", "2023 is not leap year"),
            Arguments.of("1900-02-29", "yyyy-MM-dd", "1900 is not leap year (century not divisible by 400)"),
            Arguments.of("2021-02-29", "yyyy-MM-dd", "2021 is not leap year"),
            Arguments.of("2022-02-29", "yyyy-MM-dd", "2022 is not leap year"),
            Arguments.of("20230229", "yyyyMMdd", "2023 non-leap year compact format"),
            Arguments.of("19000229", "yyyyMMdd", "1900 non-leap year compact format")
        );
    }

    // ========================================================================
    // Boundary Date Tests - Jan 1, Dec 31, Month Boundaries
    // ========================================================================

    @ParameterizedTest
    @DisplayName("Boundary dates (Jan 1, Dec 31) should be valid")
    @MethodSource("provideBoundaryDates")
    void testIsValidDate_BoundaryDates(String dateStr, String format, String description) {
        ValidationResult result = dateValidator.isValidDate(dateStr, format);
        
        assertTrue(result.isValid(), 
            String.format("Boundary date %s (%s) should be valid", dateStr, description));
        assertEquals(0, result.getSeverityCode());
    }

    private static Stream<Arguments> provideBoundaryDates() {
        return Stream.of(
            Arguments.of("2023-01-01", "yyyy-MM-dd", "Start of year 2023"),
            Arguments.of("2023-12-31", "yyyy-MM-dd", "End of year 2023"),
            Arguments.of("2024-01-01", "yyyy-MM-dd", "Start of leap year 2024"),
            Arguments.of("2024-12-31", "yyyy-MM-dd", "End of leap year 2024"),
            Arguments.of("2023-02-28", "yyyy-MM-dd", "Last day of Feb in non-leap year"),
            Arguments.of("2024-02-29", "yyyy-MM-dd", "Last day of Feb in leap year"),
            Arguments.of("2023-04-30", "yyyy-MM-dd", "Last day of April (30-day month)"),
            Arguments.of("2023-05-31", "yyyy-MM-dd", "Last day of May (31-day month)"),
            Arguments.of("2023-06-30", "yyyy-MM-dd", "Last day of June (30-day month)"),
            Arguments.of("2023-09-30", "yyyy-MM-dd", "Last day of September (30-day month)"),
            Arguments.of("2023-11-30", "yyyy-MM-dd", "Last day of November (30-day month)")
        );
    }

    // ========================================================================
    // Invalid Month Tests - Months 0 and 13
    // ========================================================================

    @ParameterizedTest
    @DisplayName("Invalid month values (0, 13, negative) should be rejected")
    @MethodSource("provideInvalidMonths")
    void testIsValidDate_InvalidMonths(String dateStr, String format, String description) {
        ValidationResult result = dateValidator.isValidDate(dateStr, format);
        
        assertFalse(result.isValid(), 
            String.format("Date with invalid month %s (%s) should be invalid", dateStr, description));
        assertTrue(result.getSeverityCode() > 0);
    }

    private static Stream<Arguments> provideInvalidMonths() {
        return Stream.of(
            Arguments.of("2023-00-15", "yyyy-MM-dd", "Month 0 is invalid"),
            Arguments.of("2023-13-15", "yyyy-MM-dd", "Month 13 is invalid"),
            Arguments.of("2023-14-15", "yyyy-MM-dd", "Month 14 is invalid"),
            Arguments.of("20231315", "yyyyMMdd", "Month 13 compact format")
        );
    }

    // ========================================================================
    // Invalid Day Tests - Feb 30, Apr 31, Nov 31, etc.
    // ========================================================================

    @ParameterizedTest
    @DisplayName("Invalid day values for each month should be rejected")
    @MethodSource("provideInvalidDays")
    void testIsValidDate_InvalidDays(String dateStr, String format, String description) {
        ValidationResult result = dateValidator.isValidDate(dateStr, format);
        
        assertFalse(result.isValid(), 
            String.format("Date with invalid day %s (%s) should be invalid", dateStr, description));
        assertTrue(result.getSeverityCode() > 0);
        assertEquals(2, result.getSeverityCode(),
            "Invalid day should return severity code 2 (Date value error)");
        assertEquals("Date value error", result.getMessage());
    }

    private static Stream<Arguments> provideInvalidDays() {
        return Stream.of(
            Arguments.of("2023-02-30", "yyyy-MM-dd", "Feb 30 doesn't exist"),
            Arguments.of("2023-02-31", "yyyy-MM-dd", "Feb 31 doesn't exist"),
            Arguments.of("2023-04-31", "yyyy-MM-dd", "April only has 30 days"),
            Arguments.of("2023-06-31", "yyyy-MM-dd", "June only has 30 days"),
            Arguments.of("2023-09-31", "yyyy-MM-dd", "September only has 30 days"),
            Arguments.of("2023-11-31", "yyyy-MM-dd", "November only has 30 days"),
            Arguments.of("2023-01-32", "yyyy-MM-dd", "January only has 31 days"),
            Arguments.of("2023-03-32", "yyyy-MM-dd", "March only has 31 days"),
            Arguments.of("2023-05-32", "yyyy-MM-dd", "May only has 31 days"),
            Arguments.of("2023-07-32", "yyyy-MM-dd", "July only has 31 days"),
            Arguments.of("2023-08-32", "yyyy-MM-dd", "August only has 31 days"),
            Arguments.of("2023-10-32", "yyyy-MM-dd", "October only has 31 days"),
            Arguments.of("2023-12-32", "yyyy-MM-dd", "December only has 31 days"),
            Arguments.of("2023-01-00", "yyyy-MM-dd", "Day 0 is invalid"),
            Arguments.of("20230230", "yyyyMMdd", "Feb 30 compact format"),
            Arguments.of("20230431", "yyyyMMdd", "April 31 compact format")
        );
    }

    // ========================================================================
    // Null, Empty, and Malformed Input Tests
    // ========================================================================

    @Test
    @DisplayName("Null date string should return insufficient data error")
    void testIsValidDate_NullDateString() {
        ValidationResult result = dateValidator.isValidDate(null, "yyyy-MM-dd");
        
        assertFalse(result.isValid());
        assertEquals(1, result.getSeverityCode(),
            "Null date should return severity code 1 (Insufficient data)");
        assertEquals("Insufficient data", result.getMessage());
    }

    @Test
    @DisplayName("Empty date string should return insufficient data error")
    void testIsValidDate_EmptyDateString() {
        ValidationResult result = dateValidator.isValidDate("", "yyyy-MM-dd");
        
        assertFalse(result.isValid());
        assertEquals(1, result.getSeverityCode());
        assertEquals("Insufficient data", result.getMessage());
    }

    @Test
    @DisplayName("Whitespace-only date string should return insufficient data error")
    void testIsValidDate_WhitespaceOnlyDateString() {
        ValidationResult result = dateValidator.isValidDate("   ", "yyyy-MM-dd");
        
        assertFalse(result.isValid());
        assertEquals(1, result.getSeverityCode());
        assertEquals("Insufficient data", result.getMessage());
    }

    @Test
    @DisplayName("Null format pattern should return insufficient data error")
    void testIsValidDate_NullFormatPattern() {
        ValidationResult result = dateValidator.isValidDate("2023-01-15", null);
        
        assertFalse(result.isValid());
        assertEquals(1, result.getSeverityCode());
        assertEquals("Insufficient data", result.getMessage());
    }

    @Test
    @DisplayName("Empty format pattern should return insufficient data error")
    void testIsValidDate_EmptyFormatPattern() {
        ValidationResult result = dateValidator.isValidDate("2023-01-15", "");
        
        assertFalse(result.isValid());
        assertEquals(1, result.getSeverityCode());
        assertEquals("Insufficient data", result.getMessage());
    }

    @Test
    @DisplayName("Malformed date string should return date value error")
    void testIsValidDate_MalformedDateString() {
        ValidationResult result = dateValidator.isValidDate("not-a-date", "yyyy-MM-dd");
        
        assertFalse(result.isValid());
        assertTrue(result.getSeverityCode() > 0);
        assertNotEquals("Date is valid", result.getMessage());
    }

    @Test
    @DisplayName("Invalid format pattern should return bad format pattern error")
    void testIsValidDate_InvalidFormatPattern() {
        ValidationResult result = dateValidator.isValidDate("2023-01-15", "invalid-pattern");
        
        assertFalse(result.isValid());
        assertEquals(5, result.getSeverityCode(),
            "Invalid format pattern should return severity code 5");
        assertEquals("Bad format pattern", result.getMessage());
    }

    // ========================================================================
    // Date Range Validation Tests - Out of Reasonable Range
    // ========================================================================

    @Test
    @DisplayName("Date before year 1900 should be rejected")
    void testIsValidDate_DateBeforeMinimum() {
        ValidationResult result = dateValidator.isValidDate("1899-12-31", "yyyy-MM-dd");
        
        assertFalse(result.isValid());
        assertEquals(2, result.getSeverityCode());
        assertEquals("Date value error", result.getMessage());
    }

    @Test
    @DisplayName("Date more than 100 years in the future should be rejected")
    void testIsValidDate_DateAfterMaximum() {
        LocalDate futureDate = LocalDate.now().plusYears(101);
        String futureDateStr = futureDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        ValidationResult result = dateValidator.isValidDate(futureDateStr, "yyyy-MM-dd");
        
        assertFalse(result.isValid());
        assertEquals(2, result.getSeverityCode());
        assertEquals("Date value error", result.getMessage());
    }

    // ========================================================================
    // parseDateWithFormat() Tests - Optional Return Type
    // ========================================================================

    @Test
    @DisplayName("parseDateWithFormat should return Optional with valid date")
    void testParseDateWithFormat_ValidDate() {
        Optional<LocalDate> result = dateValidator.parseDateWithFormat("2023-01-15", "yyyy-MM-dd");
        
        assertTrue(result.isPresent(), "Valid date should return non-empty Optional");
        assertEquals(LocalDate.of(2023, 1, 15), result.get());
    }

    @Test
    @DisplayName("parseDateWithFormat should return empty Optional for invalid date")
    void testParseDateWithFormat_InvalidDate() {
        Optional<LocalDate> result = dateValidator.parseDateWithFormat("2023-02-30", "yyyy-MM-dd");
        
        assertFalse(result.isPresent(), "Invalid date should return empty Optional");
    }

    @Test
    @DisplayName("parseDateWithFormat should return empty Optional for null date")
    void testParseDateWithFormat_NullDate() {
        Optional<LocalDate> result = dateValidator.parseDateWithFormat(null, "yyyy-MM-dd");
        
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("parseDateWithFormat should return empty Optional for null format")
    void testParseDateWithFormat_NullFormat() {
        Optional<LocalDate> result = dateValidator.parseDateWithFormat("2023-01-15", null);
        
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("parseDateWithFormat should handle leap year Feb 29")
    void testParseDateWithFormat_LeapYearFeb29() {
        Optional<LocalDate> result = dateValidator.parseDateWithFormat("2024-02-29", "yyyy-MM-dd");
        
        assertTrue(result.isPresent());
        assertEquals(LocalDate.of(2024, 2, 29), result.get());
    }

    @Test
    @DisplayName("parseDateWithFormat should reject non-leap year Feb 29")
    void testParseDateWithFormat_NonLeapYearFeb29() {
        Optional<LocalDate> result = dateValidator.parseDateWithFormat("2023-02-29", "yyyy-MM-dd");
        
        assertFalse(result.isPresent());
    }

    // ========================================================================
    // validateDateRange() Tests - Min/Max Date Boundaries
    // ========================================================================

    @Test
    @DisplayName("validateDateRange should accept date within range")
    void testValidateDateRange_DateWithinRange() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        LocalDate minDate = LocalDate.of(2023, 1, 1);
        LocalDate maxDate = LocalDate.of(2023, 12, 31);
        
        ValidationResult result = dateValidator.validateDateRange(date, minDate, maxDate);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getSeverityCode());
        assertEquals("Date is within valid range", result.getMessage());
    }

    @Test
    @DisplayName("validateDateRange should accept date at minimum boundary")
    void testValidateDateRange_DateAtMinimum() {
        LocalDate date = LocalDate.of(2023, 1, 1);
        LocalDate minDate = LocalDate.of(2023, 1, 1);
        LocalDate maxDate = LocalDate.of(2023, 12, 31);
        
        ValidationResult result = dateValidator.validateDateRange(date, minDate, maxDate);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getSeverityCode());
    }

    @Test
    @DisplayName("validateDateRange should accept date at maximum boundary")
    void testValidateDateRange_DateAtMaximum() {
        LocalDate date = LocalDate.of(2023, 12, 31);
        LocalDate minDate = LocalDate.of(2023, 1, 1);
        LocalDate maxDate = LocalDate.of(2023, 12, 31);
        
        ValidationResult result = dateValidator.validateDateRange(date, minDate, maxDate);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getSeverityCode());
    }

    @Test
    @DisplayName("validateDateRange should reject date before minimum")
    void testValidateDateRange_DateBeforeMinimum() {
        LocalDate date = LocalDate.of(2022, 12, 31);
        LocalDate minDate = LocalDate.of(2023, 1, 1);
        LocalDate maxDate = LocalDate.of(2023, 12, 31);
        
        ValidationResult result = dateValidator.validateDateRange(date, minDate, maxDate);
        
        assertFalse(result.isValid());
        assertEquals(6, result.getSeverityCode());
        assertTrue(result.getMessage().contains("before minimum"));
    }

    @Test
    @DisplayName("validateDateRange should reject date after maximum")
    void testValidateDateRange_DateAfterMaximum() {
        LocalDate date = LocalDate.of(2024, 1, 1);
        LocalDate minDate = LocalDate.of(2023, 1, 1);
        LocalDate maxDate = LocalDate.of(2023, 12, 31);
        
        ValidationResult result = dateValidator.validateDateRange(date, minDate, maxDate);
        
        assertFalse(result.isValid());
        assertEquals(7, result.getSeverityCode());
        assertTrue(result.getMessage().contains("after maximum"));
    }

    @Test
    @DisplayName("validateDateRange should reject null date")
    void testValidateDateRange_NullDate() {
        LocalDate minDate = LocalDate.of(2023, 1, 1);
        LocalDate maxDate = LocalDate.of(2023, 12, 31);
        
        ValidationResult result = dateValidator.validateDateRange(null, minDate, maxDate);
        
        assertFalse(result.isValid());
        assertEquals(1, result.getSeverityCode());
        assertEquals("Insufficient data", result.getMessage());
    }

    @Test
    @DisplayName("validateDateRange should reject null minimum date")
    void testValidateDateRange_NullMinDate() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        LocalDate maxDate = LocalDate.of(2023, 12, 31);
        
        ValidationResult result = dateValidator.validateDateRange(date, null, maxDate);
        
        assertFalse(result.isValid());
        assertEquals(1, result.getSeverityCode());
        assertEquals("Insufficient data", result.getMessage());
    }

    @Test
    @DisplayName("validateDateRange should reject null maximum date")
    void testValidateDateRange_NullMaxDate() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        LocalDate minDate = LocalDate.of(2023, 1, 1);
        
        ValidationResult result = dateValidator.validateDateRange(date, minDate, null);
        
        assertFalse(result.isValid());
        assertEquals(1, result.getSeverityCode());
        assertEquals("Insufficient data", result.getMessage());
    }

    // ========================================================================
    // isBusinessDay() Tests - Monday-Friday Validation
    // ========================================================================

    @Test
    @DisplayName("isBusinessDay should return true for Monday")
    void testIsBusinessDay_Monday() {
        // January 2, 2023 is a Monday
        LocalDate monday = LocalDate.of(2023, 1, 2);
        
        assertTrue(dateValidator.isBusinessDay(monday),
            "Monday should be a business day");
    }

    @Test
    @DisplayName("isBusinessDay should return true for Tuesday")
    void testIsBusinessDay_Tuesday() {
        // January 3, 2023 is a Tuesday
        LocalDate tuesday = LocalDate.of(2023, 1, 3);
        
        assertTrue(dateValidator.isBusinessDay(tuesday),
            "Tuesday should be a business day");
    }

    @Test
    @DisplayName("isBusinessDay should return true for Wednesday")
    void testIsBusinessDay_Wednesday() {
        // January 4, 2023 is a Wednesday
        LocalDate wednesday = LocalDate.of(2023, 1, 4);
        
        assertTrue(dateValidator.isBusinessDay(wednesday),
            "Wednesday should be a business day");
    }

    @Test
    @DisplayName("isBusinessDay should return true for Thursday")
    void testIsBusinessDay_Thursday() {
        // January 5, 2023 is a Thursday
        LocalDate thursday = LocalDate.of(2023, 1, 5);
        
        assertTrue(dateValidator.isBusinessDay(thursday),
            "Thursday should be a business day");
    }

    @Test
    @DisplayName("isBusinessDay should return true for Friday")
    void testIsBusinessDay_Friday() {
        // January 6, 2023 is a Friday
        LocalDate friday = LocalDate.of(2023, 1, 6);
        
        assertTrue(dateValidator.isBusinessDay(friday),
            "Friday should be a business day");
    }

    @Test
    @DisplayName("isBusinessDay should return false for Saturday")
    void testIsBusinessDay_Saturday() {
        // January 7, 2023 is a Saturday
        LocalDate saturday = LocalDate.of(2023, 1, 7);
        
        assertFalse(dateValidator.isBusinessDay(saturday),
            "Saturday should not be a business day");
    }

    @Test
    @DisplayName("isBusinessDay should return false for Sunday")
    void testIsBusinessDay_Sunday() {
        // January 8, 2023 is a Sunday
        LocalDate sunday = LocalDate.of(2023, 1, 8);
        
        assertFalse(dateValidator.isBusinessDay(sunday),
            "Sunday should not be a business day");
    }

    @Test
    @DisplayName("isBusinessDay should return false for null date")
    void testIsBusinessDay_NullDate() {
        assertFalse(dateValidator.isBusinessDay(null),
            "Null date should not be a business day");
    }

    // ========================================================================
    // isWeekend() Tests - Saturday/Sunday Validation
    // ========================================================================

    @Test
    @DisplayName("isWeekend should return true for Saturday")
    void testIsWeekend_Saturday() {
        // January 7, 2023 is a Saturday
        LocalDate saturday = LocalDate.of(2023, 1, 7);
        
        assertTrue(dateValidator.isWeekend(saturday),
            "Saturday should be a weekend");
    }

    @Test
    @DisplayName("isWeekend should return true for Sunday")
    void testIsWeekend_Sunday() {
        // January 8, 2023 is a Sunday
        LocalDate sunday = LocalDate.of(2023, 1, 8);
        
        assertTrue(dateValidator.isWeekend(sunday),
            "Sunday should be a weekend");
    }

    @Test
    @DisplayName("isWeekend should return false for Monday")
    void testIsWeekend_Monday() {
        // January 2, 2023 is a Monday
        LocalDate monday = LocalDate.of(2023, 1, 2);
        
        assertFalse(dateValidator.isWeekend(monday),
            "Monday should not be a weekend");
    }

    @Test
    @DisplayName("isWeekend should return false for Friday")
    void testIsWeekend_Friday() {
        // January 6, 2023 is a Friday
        LocalDate friday = LocalDate.of(2023, 1, 6);
        
        assertFalse(dateValidator.isWeekend(friday),
            "Friday should not be a weekend");
    }

    @Test
    @DisplayName("isWeekend should return false for null date")
    void testIsWeekend_NullDate() {
        assertFalse(dateValidator.isWeekend(null),
            "Null date should not be a weekend");
    }

    // ========================================================================
    // isValidDateOfBirth() Tests - Age Validation (18-120 years)
    // ========================================================================

    @Test
    @DisplayName("isValidDateOfBirth should accept valid adult date of birth")
    void testIsValidDateOfBirth_ValidAdult() {
        // Person born 25 years ago
        LocalDate dateOfBirth = LocalDate.now().minusYears(25);
        
        ValidationResult result = dateValidator.isValidDateOfBirth(dateOfBirth);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getSeverityCode());
        assertEquals("Date of birth is valid", result.getMessage());
    }

    @Test
    @DisplayName("isValidDateOfBirth should accept date of birth exactly 18 years ago")
    void testIsValidDateOfBirth_Exactly18YearsOld() {
        // Person who turned 18 today
        LocalDate dateOfBirth = LocalDate.now().minusYears(18);
        
        ValidationResult result = dateValidator.isValidDateOfBirth(dateOfBirth);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getSeverityCode());
    }

    @Test
    @DisplayName("isValidDateOfBirth should accept date of birth exactly 120 years ago")
    void testIsValidDateOfBirth_Exactly120YearsOld() {
        // Person who turned 120 today
        LocalDate dateOfBirth = LocalDate.now().minusYears(120);
        
        ValidationResult result = dateValidator.isValidDateOfBirth(dateOfBirth);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getSeverityCode());
    }

    @Test
    @DisplayName("isValidDateOfBirth should reject date of birth less than 18 years ago")
    void testIsValidDateOfBirth_TooYoung() {
        // Person born 17 years ago
        LocalDate dateOfBirth = LocalDate.now().minusYears(17);
        
        ValidationResult result = dateValidator.isValidDateOfBirth(dateOfBirth);
        
        assertFalse(result.isValid());
        assertEquals(9, result.getSeverityCode());
        assertTrue(result.getMessage().contains("at least 18 years old"));
    }

    @Test
    @DisplayName("isValidDateOfBirth should reject date of birth more than 120 years ago")
    void testIsValidDateOfBirth_TooOld() {
        // Person born 121 years ago
        LocalDate dateOfBirth = LocalDate.now().minusYears(121);
        
        ValidationResult result = dateValidator.isValidDateOfBirth(dateOfBirth);
        
        assertFalse(result.isValid());
        assertEquals(10, result.getSeverityCode());
        assertTrue(result.getMessage().contains("unrealistic age"));
    }

    @Test
    @DisplayName("isValidDateOfBirth should reject future date")
    void testIsValidDateOfBirth_FutureDate() {
        // Date 1 year in the future
        LocalDate futureDate = LocalDate.now().plusYears(1);
        
        ValidationResult result = dateValidator.isValidDateOfBirth(futureDate);
        
        assertFalse(result.isValid());
        assertEquals(8, result.getSeverityCode());
        assertTrue(result.getMessage().contains("must be in the past"));
    }

    @Test
    @DisplayName("isValidDateOfBirth should reject today's date")
    void testIsValidDateOfBirth_TodayDate() {
        LocalDate today = LocalDate.now();
        
        ValidationResult result = dateValidator.isValidDateOfBirth(today);
        
        assertFalse(result.isValid());
        assertEquals(8, result.getSeverityCode());
        assertTrue(result.getMessage().contains("must be in the past"));
    }

    @Test
    @DisplayName("isValidDateOfBirth should reject null date")
    void testIsValidDateOfBirth_NullDate() {
        ValidationResult result = dateValidator.isValidDateOfBirth(null);
        
        assertFalse(result.isValid());
        assertEquals(1, result.getSeverityCode());
        assertEquals("Insufficient data", result.getMessage());
    }

    // ========================================================================
    // ValidationResult Tests - Inner Class Functionality
    // ========================================================================

    @Test
    @DisplayName("ValidationResult toString should format correctly")
    void testValidationResult_ToString() {
        ValidationResult result = new ValidationResult(
            true,
            0,
            "Date is valid",
            "2023-01-15",
            "yyyy-MM-dd"
        );
        
        String resultString = result.toString();
        
        assertTrue(resultString.contains("Severity: 0000"));
        assertTrue(resultString.contains("Result: Date is valid"));
        assertTrue(resultString.contains("Date: 2023-01-15"));
        assertTrue(resultString.contains("Format: yyyy-MM-dd"));
    }

    @Test
    @DisplayName("ValidationResult should handle null date value in toString")
    void testValidationResult_ToStringWithNullDate() {
        ValidationResult result = new ValidationResult(
            false,
            1,
            "Insufficient data",
            null,
            null
        );
        
        String resultString = result.toString();
        
        assertTrue(resultString.contains("Severity: 0001"));
        assertTrue(resultString.contains("Result: Insufficient data"));
        assertFalse(resultString.contains("Date: null"));
        assertFalse(resultString.contains("Format: null"));
    }

    @Test
    @DisplayName("ValidationResult getters should return correct values")
    void testValidationResult_Getters() {
        ValidationResult result = new ValidationResult(
            true,
            0,
            "Date is valid",
            "2023-01-15",
            "yyyy-MM-dd"
        );
        
        assertTrue(result.isValid());
        assertEquals(0, result.getSeverityCode());
        assertEquals("Date is valid", result.getMessage());
        assertEquals("2023-01-15", result.getDateValue());
        assertEquals("yyyy-MM-dd", result.getFormatPattern());
    }
}
