/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aws.carddemo.unit.util;

import com.aws.carddemo.util.FinancialCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit test class for FinancialCalculator utility.
 * 
 * <p>Tests BigDecimal precision for financial calculations with byte-for-byte functional 
 * equivalence to CBACT04C.cbl COBOL program. Validates interest calculation formula:
 * <pre>
 * COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
 * </pre>
 * 
 * <p>Coverage targets:
 * <ul>
 *   <li>≥80% line coverage</li>
 *   <li>≥70% branch coverage</li>
 *   <li>All edge cases and boundary conditions</li>
 *   <li>Precision validation matching COBOL PIC S9(09)V99 COMP-3 format</li>
 * </ul>
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2025-01-01
 */
@DisplayName("FinancialCalculator Unit Tests")
class FinancialCalculatorTest {

    // ===========================================
    // calculateMonthlyInterest() Tests
    // ===========================================

    /**
     * Provides standard test cases for monthly interest calculations.
     * 
     * <p>Test data validates formula: monthlyInterest = (balance * APR) / 1200
     * with RoundingMode.HALF_UP banker's rounding to 2 decimal places.
     * 
     * @return Stream of Arguments containing (balance, APR, expected monthly interest)
     */
    private static Stream<Arguments> provideStandardInterestCalculations() {
        return Stream.of(
            // Standard calculation: $1,000.00 at 18.99% APR
            // Formula: 1000.00 * 18.99 / 1200 = 15.825 → 15.83 (HALF_UP)
            Arguments.of(new BigDecimal("1000.00"), new BigDecimal("18.99"), new BigDecimal("15.83")),
            
            // Standard calculation: $5,000.00 at 24.99% APR
            // Formula: 5000.00 * 24.99 / 1200 = 104.125 → 104.13 (HALF_UP)
            Arguments.of(new BigDecimal("5000.00"), new BigDecimal("24.99"), new BigDecimal("104.13")),
            
            // Standard calculation: $250.50 at 12.50% APR
            // Formula: 250.50 * 12.50 / 1200 = 2.609375 → 2.61 (HALF_UP)
            Arguments.of(new BigDecimal("250.50"), new BigDecimal("12.50"), new BigDecimal("2.61")),
            
            // Low balance, low APR: $100.00 at 6.00% APR
            // Formula: 100.00 * 6.00 / 1200 = 0.50
            Arguments.of(new BigDecimal("100.00"), new BigDecimal("6.00"), new BigDecimal("0.50")),
            
            // High balance, high APR: $10,000.00 at 29.99% APR
            // Formula: 10000.00 * 29.99 / 1200 = 249.916... → 249.92 (HALF_UP)
            Arguments.of(new BigDecimal("10000.00"), new BigDecimal("29.99"), new BigDecimal("249.92")),
            
            // Odd balance: $1,234.56 at 15.75% APR
            // Formula: 1234.56 * 15.75 / 1200 = 16.2012 → 16.20 (HALF_UP)
            Arguments.of(new BigDecimal("1234.56"), new BigDecimal("15.75"), new BigDecimal("16.20")),
            
            // Small balance: $10.00 at 18.00% APR
            // Formula: 10.00 * 18.00 / 1200 = 0.15
            Arguments.of(new BigDecimal("10.00"), new BigDecimal("18.00"), new BigDecimal("0.15")),
            
            // Large balance: $50,000.00 at 12.99% APR
            // Formula: 50000.00 * 12.99 / 1200 = 541.25
            Arguments.of(new BigDecimal("50000.00"), new BigDecimal("12.99"), new BigDecimal("541.25")),
            
            // Decimal APR: $2,500.00 at 21.45% APR
            // Formula: 2500.00 * 21.45 / 1200 = 44.6875 → 44.69 (HALF_UP)
            Arguments.of(new BigDecimal("2500.00"), new BigDecimal("21.45"), new BigDecimal("44.69")),
            
            // Low APR: $1,000.00 at 3.50% APR
            // Formula: 1000.00 * 3.50 / 1200 = 2.916... → 2.92 (HALF_UP)
            Arguments.of(new BigDecimal("1000.00"), new BigDecimal("3.50"), new BigDecimal("2.92"))
        );
    }

    @ParameterizedTest
    @MethodSource("provideStandardInterestCalculations")
    @DisplayName("calculateMonthlyInterest - Standard Calculations")
    void testCalculateMonthlyInterest_StandardCalculations(BigDecimal balance, 
                                                           BigDecimal apr, 
                                                           BigDecimal expectedInterest) {
        // Act
        BigDecimal actualInterest = FinancialCalculator.calculateMonthlyInterest(balance, apr);
        
        // Assert
        assertThat(actualInterest)
            .as("Monthly interest for balance %s at %s%% APR", balance, apr)
            .isEqualByComparingTo(expectedInterest);
        
        // Verify scale is exactly 2 decimal places (currency precision)
        assertThat(actualInterest.scale())
            .as("Interest amount must have 2 decimal places for currency precision")
            .isEqualTo(2);
    }

    @Test
    @DisplayName("calculateMonthlyInterest - Zero Balance Returns Zero Interest")
    void testCalculateMonthlyInterest_ZeroBalance() {
        // Arrange
        BigDecimal zeroBalance = BigDecimal.ZERO;
        BigDecimal apr = new BigDecimal("18.99");
        
        // Act
        BigDecimal interest = FinancialCalculator.calculateMonthlyInterest(zeroBalance, apr);
        
        // Assert
        assertThat(interest)
            .as("Zero balance should result in zero interest")
            .isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    @DisplayName("calculateMonthlyInterest - Zero APR Returns Zero Interest")
    void testCalculateMonthlyInterest_ZeroAPR() {
        // Arrange
        BigDecimal balance = new BigDecimal("1000.00");
        BigDecimal zeroApr = BigDecimal.ZERO;
        
        // Act
        BigDecimal interest = FinancialCalculator.calculateMonthlyInterest(balance, zeroApr);
        
        // Assert
        assertThat(interest)
            .as("Zero APR should result in zero interest")
            .isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    @DisplayName("calculateMonthlyInterest - Both Zero Balance and APR Returns Zero")
    void testCalculateMonthlyInterest_BothZero() {
        // Arrange
        BigDecimal zeroBalance = BigDecimal.ZERO;
        BigDecimal zeroApr = BigDecimal.ZERO;
        
        // Act
        BigDecimal interest = FinancialCalculator.calculateMonthlyInterest(zeroBalance, zeroApr);
        
        // Assert
        assertThat(interest)
            .as("Zero balance and zero APR should result in zero interest")
            .isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    @DisplayName("calculateMonthlyInterest - Null Balance Throws IllegalArgumentException")
    void testCalculateMonthlyInterest_NullBalance() {
        // Arrange
        BigDecimal nullBalance = null;
        BigDecimal apr = new BigDecimal("18.99");
        
        // Act & Assert
        assertThatThrownBy(() -> 
            FinancialCalculator.calculateMonthlyInterest(nullBalance, apr)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("balance")
            .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("calculateMonthlyInterest - Null APR Throws IllegalArgumentException")
    void testCalculateMonthlyInterest_NullAPR() {
        // Arrange
        BigDecimal balance = new BigDecimal("1000.00");
        BigDecimal nullApr = null;
        
        // Act & Assert
        assertThatThrownBy(() -> 
            FinancialCalculator.calculateMonthlyInterest(balance, nullApr)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("annualPercentageRate")
            .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("calculateMonthlyInterest - Large Balance No Overflow")
    void testCalculateMonthlyInterest_LargeBalance() {
        // Arrange - Maximum balance: $999,999,999.99 at 99.99% APR
        BigDecimal largeBalance = new BigDecimal("999999999.99");
        BigDecimal highApr = new BigDecimal("99.99");
        
        // Expected: 999999999.99 * 99.99 / 1200 = 83,333,333.33 (rounded)
        BigDecimal expected = new BigDecimal("83333333.33");
        
        // Act
        BigDecimal interest = FinancialCalculator.calculateMonthlyInterest(largeBalance, highApr);
        
        // Assert
        assertThat(interest)
            .as("Large balance calculation should not overflow")
            .isEqualByComparingTo(expected);
    }

    /**
     * Provides test cases demonstrating HALF_UP banker's rounding behavior.
     * 
     * @return Stream of Arguments with values requiring rounding
     */
    private static Stream<Arguments> provideRoundingTestCases() {
        return Stream.of(
            // Test HALF_UP rounding: 0.005 rounds UP to 0.01
            // Balance: $100.00, APR: 0.06% → 100 * 0.06 / 1200 = 0.005 → 0.01
            Arguments.of(new BigDecimal("100.00"), new BigDecimal("0.06"), new BigDecimal("0.01")),
            
            // Test HALF_UP rounding: 0.004 rounds DOWN to 0.00
            // Balance: $100.00, APR: 0.048% → 100 * 0.048 / 1200 = 0.004 → 0.00
            Arguments.of(new BigDecimal("100.00"), new BigDecimal("0.048"), new BigDecimal("0.00")),
            
            // Test HALF_UP rounding: 0.125 rounds UP to 0.13
            // Balance: $150.00, APR: 1.00% → 150 * 1.00 / 1200 = 0.125 → 0.13
            Arguments.of(new BigDecimal("150.00"), new BigDecimal("1.00"), new BigDecimal("0.13")),
            
            // Test HALF_UP rounding: 0.124 rounds DOWN to 0.12
            // Balance: $149.00, APR: 1.00% → 149 * 1.00 / 1200 = 0.124166... → 0.12
            Arguments.of(new BigDecimal("149.00"), new BigDecimal("1.00"), new BigDecimal("0.12")),
            
            // Test HALF_UP with repeating decimal
            // Balance: $333.33, APR: 10.00% → 333.33 * 10 / 1200 = 2.77775 → 2.78
            Arguments.of(new BigDecimal("333.33"), new BigDecimal("10.00"), new BigDecimal("2.78"))
        );
    }

    @ParameterizedTest
    @MethodSource("provideRoundingTestCases")
    @DisplayName("calculateMonthlyInterest - HALF_UP Rounding Behavior")
    void testCalculateMonthlyInterest_RoundingBehavior(BigDecimal balance, 
                                                       BigDecimal apr, 
                                                       BigDecimal expectedInterest) {
        // Act
        BigDecimal actualInterest = FinancialCalculator.calculateMonthlyInterest(balance, apr);
        
        // Assert
        assertThat(actualInterest)
            .as("Interest calculation with HALF_UP rounding for balance %s at %s%% APR", balance, apr)
            .isEqualByComparingTo(expectedInterest);
    }

    @Test
    @DisplayName("calculateMonthlyInterest - Precision Validation Matches COBOL PIC S9(09)V99 COMP-3")
    void testCalculateMonthlyInterest_PrecisionValidation() {
        // Arrange - Test multiple precision scenarios
        BigDecimal balance1 = new BigDecimal("1000.00");
        BigDecimal apr1 = new BigDecimal("18.99");
        
        // Act
        BigDecimal interest1 = FinancialCalculator.calculateMonthlyInterest(balance1, apr1);
        
        // Assert - Verify exactly 2 decimal places (COBOL COMP-3 format)
        assertThat(interest1.scale())
            .as("Interest must have exactly 2 decimal places matching COBOL PIC S9(09)V99 COMP-3")
            .isEqualTo(2);
        
        // Verify the exact calculation matches expected precision
        // 1000.00 * 18.99 / 1200 = 15.825 → 15.83
        BigDecimal expected = new BigDecimal("15.83");
        assertThat(interest1.setScale(2, RoundingMode.HALF_UP))
            .isEqualByComparingTo(expected);
    }

    // ===========================================
    // add() Tests
    // ===========================================

    @Test
    @DisplayName("add - Standard Addition")
    void testAdd_StandardAddition() {
        // Arrange
        BigDecimal a = new BigDecimal("100.50");
        BigDecimal b = new BigDecimal("50.25");
        BigDecimal expected = new BigDecimal("150.75");
        
        // Act
        BigDecimal result = FinancialCalculator.add(a, b);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("add - Addition with Rounding")
    void testAdd_WithRounding() {
        // Arrange
        BigDecimal a = new BigDecimal("100.555");
        BigDecimal b = new BigDecimal("50.555");
        BigDecimal expected = new BigDecimal("151.11"); // 151.11 with HALF_UP
        
        // Act
        BigDecimal result = FinancialCalculator.add(a, b);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("add - Addition with Zero")
    void testAdd_WithZero() {
        // Arrange
        BigDecimal a = new BigDecimal("100.00");
        BigDecimal zero = BigDecimal.ZERO;
        
        // Act
        BigDecimal result = FinancialCalculator.add(a, zero);
        
        // Assert
        assertThat(result).isEqualByComparingTo(a);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("add - Null First Amount Throws IllegalArgumentException")
    void testAdd_NullFirstAmount() {
        // Arrange
        BigDecimal nullAmount = null;
        BigDecimal b = new BigDecimal("50.00");
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.add(nullAmount, b))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("first amount")
            .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("add - Null Second Amount Throws IllegalArgumentException")
    void testAdd_NullSecondAmount() {
        // Arrange
        BigDecimal a = new BigDecimal("100.00");
        BigDecimal nullAmount = null;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.add(a, nullAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("second amount")
            .hasMessageContaining("must not be null");
    }

    // ===========================================
    // subtract() Tests
    // ===========================================

    @Test
    @DisplayName("subtract - Standard Subtraction")
    void testSubtract_StandardSubtraction() {
        // Arrange
        BigDecimal a = new BigDecimal("100.50");
        BigDecimal b = new BigDecimal("50.25");
        BigDecimal expected = new BigDecimal("50.25");
        
        // Act
        BigDecimal result = FinancialCalculator.subtract(a, b);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("subtract - Subtraction with Rounding")
    void testSubtract_WithRounding() {
        // Arrange
        BigDecimal a = new BigDecimal("100.555");
        BigDecimal b = new BigDecimal("50.556");
        BigDecimal expected = new BigDecimal("50.00"); // 49.999 rounds to 50.00
        
        // Act
        BigDecimal result = FinancialCalculator.subtract(a, b);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("subtract - Result is Negative")
    void testSubtract_NegativeResult() {
        // Arrange
        BigDecimal a = new BigDecimal("50.00");
        BigDecimal b = new BigDecimal("100.00");
        BigDecimal expected = new BigDecimal("-50.00");
        
        // Act
        BigDecimal result = FinancialCalculator.subtract(a, b);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("subtract - Subtract from Zero")
    void testSubtract_FromZero() {
        // Arrange
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal b = new BigDecimal("50.00");
        BigDecimal expected = new BigDecimal("-50.00");
        
        // Act
        BigDecimal result = FinancialCalculator.subtract(zero, b);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("subtract - Null Minuend Throws IllegalArgumentException")
    void testSubtract_NullMinuend() {
        // Arrange
        BigDecimal nullAmount = null;
        BigDecimal b = new BigDecimal("50.00");
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.subtract(nullAmount, b))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("minuend")
            .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("subtract - Null Subtrahend Throws IllegalArgumentException")
    void testSubtract_NullSubtrahend() {
        // Arrange
        BigDecimal a = new BigDecimal("100.00");
        BigDecimal nullAmount = null;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.subtract(a, nullAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subtrahend")
            .hasMessageContaining("must not be null");
    }

    // ===========================================
    // multiply() Tests
    // ===========================================

    @Test
    @DisplayName("multiply - Standard Multiplication")
    void testMultiply_StandardMultiplication() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal factor = new BigDecimal("2.5");
        BigDecimal expected = new BigDecimal("250.00");
        
        // Act
        BigDecimal result = FinancialCalculator.multiply(amount, factor);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("multiply - Multiplication with Rounding")
    void testMultiply_WithRounding() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal factor = new BigDecimal("0.333");
        BigDecimal expected = new BigDecimal("33.30"); // 33.3 rounds to 33.30
        
        // Act
        BigDecimal result = FinancialCalculator.multiply(amount, factor);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("multiply - Multiplication by Zero")
    void testMultiply_ByZero() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal zero = BigDecimal.ZERO;
        
        // Act
        BigDecimal result = FinancialCalculator.multiply(amount, zero);
        
        // Assert
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("multiply - Multiplication by One")
    void testMultiply_ByOne() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.50");
        BigDecimal one = BigDecimal.ONE;
        
        // Act
        BigDecimal result = FinancialCalculator.multiply(amount, one);
        
        // Assert
        assertThat(result).isEqualByComparingTo(amount);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("multiply - Null Amount Throws IllegalArgumentException")
    void testMultiply_NullAmount() {
        // Arrange
        BigDecimal nullAmount = null;
        BigDecimal factor = new BigDecimal("2.0");
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.multiply(nullAmount, factor))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount")
            .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("multiply - Null Factor Throws IllegalArgumentException")
    void testMultiply_NullFactor() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal nullFactor = null;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.multiply(amount, nullFactor))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("factor")
            .hasMessageContaining("must not be null");
    }

    // ===========================================
    // divide() Tests
    // ===========================================

    @Test
    @DisplayName("divide - Standard Division with Scale 2")
    void testDivide_StandardDivision() {
        // Arrange
        BigDecimal dividend = new BigDecimal("100.00");
        BigDecimal divisor = new BigDecimal("4.00");
        int scale = 2;
        BigDecimal expected = new BigDecimal("25.00");
        
        // Act
        BigDecimal result = FinancialCalculator.divide(dividend, divisor, scale);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(scale);
    }

    @Test
    @DisplayName("divide - Division with Rounding")
    void testDivide_WithRounding() {
        // Arrange
        BigDecimal dividend = new BigDecimal("100.00");
        BigDecimal divisor = new BigDecimal("3.00");
        int scale = 2;
        BigDecimal expected = new BigDecimal("33.33"); // 33.333... rounds to 33.33
        
        // Act
        BigDecimal result = FinancialCalculator.divide(dividend, divisor, scale);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(scale);
    }

    @Test
    @DisplayName("divide - Division with Custom Scale")
    void testDivide_CustomScale() {
        // Arrange
        BigDecimal dividend = new BigDecimal("100.00");
        BigDecimal divisor = new BigDecimal("3.00");
        int scale = 4;
        BigDecimal expected = new BigDecimal("33.3333");
        
        // Act
        BigDecimal result = FinancialCalculator.divide(dividend, divisor, scale);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(scale);
    }

    @Test
    @DisplayName("divide - Division by Zero Throws ArithmeticException")
    void testDivide_ByZero() {
        // Arrange
        BigDecimal dividend = new BigDecimal("100.00");
        BigDecimal zeroDivisor = BigDecimal.ZERO;
        int scale = 2;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.divide(dividend, zeroDivisor, scale))
            .isInstanceOf(ArithmeticException.class)
            .hasMessageContaining("Cannot divide by zero");
    }

    @Test
    @DisplayName("divide - Null Dividend Throws IllegalArgumentException")
    void testDivide_NullDividend() {
        // Arrange
        BigDecimal nullDividend = null;
        BigDecimal divisor = new BigDecimal("4.00");
        int scale = 2;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.divide(nullDividend, divisor, scale))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dividend")
            .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("divide - Null Divisor Throws IllegalArgumentException")
    void testDivide_NullDivisor() {
        // Arrange
        BigDecimal dividend = new BigDecimal("100.00");
        BigDecimal nullDivisor = null;
        int scale = 2;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.divide(dividend, nullDivisor, scale))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("divisor")
            .hasMessageContaining("must not be null");
    }

    // ===========================================
    // roundToCurrency() Tests
    // ===========================================

    @Test
    @DisplayName("roundToCurrency - Round Up")
    void testRoundToCurrency_RoundUp() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.555");
        BigDecimal expected = new BigDecimal("100.56");
        
        // Act
        BigDecimal result = FinancialCalculator.roundToCurrency(amount);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("roundToCurrency - Round Down")
    void testRoundToCurrency_RoundDown() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.554");
        BigDecimal expected = new BigDecimal("100.55");
        
        // Act
        BigDecimal result = FinancialCalculator.roundToCurrency(amount);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("roundToCurrency - Exact Midpoint HALF_UP")
    void testRoundToCurrency_HalfUp() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.125");
        BigDecimal expected = new BigDecimal("100.13"); // 0.125 rounds UP to 0.13
        
        // Act
        BigDecimal result = FinancialCalculator.roundToCurrency(amount);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("roundToCurrency - Already Rounded Amount")
    void testRoundToCurrency_AlreadyRounded() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.50");
        
        // Act
        BigDecimal result = FinancialCalculator.roundToCurrency(amount);
        
        // Assert
        assertThat(result).isEqualByComparingTo(amount);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("roundToCurrency - Null Amount Throws IllegalArgumentException")
    void testRoundToCurrency_NullAmount() {
        // Arrange
        BigDecimal nullAmount = null;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.roundToCurrency(nullAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount")
            .hasMessageContaining("must not be null");
    }

    // ===========================================
    // compareAmounts() Tests
    // ===========================================

    @Test
    @DisplayName("compareAmounts - First Amount Less Than Second")
    void testCompareAmounts_LessThan() {
        // Arrange
        BigDecimal a = new BigDecimal("50.00");
        BigDecimal b = new BigDecimal("100.00");
        
        // Act
        int result = FinancialCalculator.compareAmounts(a, b);
        
        // Assert
        assertThat(result)
            .as("50.00 should be less than 100.00")
            .isEqualTo(-1);
    }

    @Test
    @DisplayName("compareAmounts - Amounts are Equal")
    void testCompareAmounts_Equal() {
        // Arrange
        BigDecimal a = new BigDecimal("100.00");
        BigDecimal b = new BigDecimal("100.00");
        
        // Act
        int result = FinancialCalculator.compareAmounts(a, b);
        
        // Assert
        assertThat(result)
            .as("100.00 should be equal to 100.00")
            .isEqualTo(0);
    }

    @Test
    @DisplayName("compareAmounts - First Amount Greater Than Second")
    void testCompareAmounts_GreaterThan() {
        // Arrange
        BigDecimal a = new BigDecimal("100.00");
        BigDecimal b = new BigDecimal("50.00");
        
        // Act
        int result = FinancialCalculator.compareAmounts(a, b);
        
        // Assert
        assertThat(result)
            .as("100.00 should be greater than 50.00")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("compareAmounts - Null First Amount Throws IllegalArgumentException")
    void testCompareAmounts_NullFirstAmount() {
        // Arrange
        BigDecimal nullAmount = null;
        BigDecimal b = new BigDecimal("100.00");
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.compareAmounts(nullAmount, b))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("first amount")
            .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("compareAmounts - Null Second Amount Throws IllegalArgumentException")
    void testCompareAmounts_NullSecondAmount() {
        // Arrange
        BigDecimal a = new BigDecimal("100.00");
        BigDecimal nullAmount = null;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.compareAmounts(a, nullAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("second amount")
            .hasMessageContaining("must not be null");
    }

    // ===========================================
    // isPositive() Tests
    // ===========================================

    @Test
    @DisplayName("isPositive - Positive Amount Returns True")
    void testIsPositive_PositiveAmount() {
        // Arrange
        BigDecimal positiveAmount = new BigDecimal("100.00");
        
        // Act
        boolean result = FinancialCalculator.isPositive(positiveAmount);
        
        // Assert
        assertThat(result)
            .as("100.00 should be positive")
            .isTrue();
    }

    @Test
    @DisplayName("isPositive - Zero Returns False")
    void testIsPositive_Zero() {
        // Arrange
        BigDecimal zero = BigDecimal.ZERO;
        
        // Act
        boolean result = FinancialCalculator.isPositive(zero);
        
        // Assert
        assertThat(result)
            .as("0.00 should not be positive")
            .isFalse();
    }

    @Test
    @DisplayName("isPositive - Negative Amount Returns False")
    void testIsPositive_NegativeAmount() {
        // Arrange
        BigDecimal negativeAmount = new BigDecimal("-100.00");
        
        // Act
        boolean result = FinancialCalculator.isPositive(negativeAmount);
        
        // Assert
        assertThat(result)
            .as("-100.00 should not be positive")
            .isFalse();
    }

    @Test
    @DisplayName("isPositive - Null Amount Throws IllegalArgumentException")
    void testIsPositive_NullAmount() {
        // Arrange
        BigDecimal nullAmount = null;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.isPositive(nullAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount")
            .hasMessageContaining("must not be null");
    }

    // ===========================================
    // isNegative() Tests
    // ===========================================

    @Test
    @DisplayName("isNegative - Negative Amount Returns True")
    void testIsNegative_NegativeAmount() {
        // Arrange
        BigDecimal negativeAmount = new BigDecimal("-100.00");
        
        // Act
        boolean result = FinancialCalculator.isNegative(negativeAmount);
        
        // Assert
        assertThat(result)
            .as("-100.00 should be negative")
            .isTrue();
    }

    @Test
    @DisplayName("isNegative - Zero Returns False")
    void testIsNegative_Zero() {
        // Arrange
        BigDecimal zero = BigDecimal.ZERO;
        
        // Act
        boolean result = FinancialCalculator.isNegative(zero);
        
        // Assert
        assertThat(result)
            .as("0.00 should not be negative")
            .isFalse();
    }

    @Test
    @DisplayName("isNegative - Positive Amount Returns False")
    void testIsNegative_PositiveAmount() {
        // Arrange
        BigDecimal positiveAmount = new BigDecimal("100.00");
        
        // Act
        boolean result = FinancialCalculator.isNegative(positiveAmount);
        
        // Assert
        assertThat(result)
            .as("100.00 should not be negative")
            .isFalse();
    }

    @Test
    @DisplayName("isNegative - Null Amount Throws IllegalArgumentException")
    void testIsNegative_NullAmount() {
        // Arrange
        BigDecimal nullAmount = null;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.isNegative(nullAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount")
            .hasMessageContaining("must not be null");
    }

    // ===========================================
    // isZero() Tests
    // ===========================================

    @Test
    @DisplayName("isZero - Zero Returns True")
    void testIsZero_Zero() {
        // Arrange
        BigDecimal zero = BigDecimal.ZERO;
        
        // Act
        boolean result = FinancialCalculator.isZero(zero);
        
        // Assert
        assertThat(result)
            .as("0.00 should be zero")
            .isTrue();
    }

    @Test
    @DisplayName("isZero - Zero with Scale Returns True")
    void testIsZero_ZeroWithScale() {
        // Arrange
        BigDecimal zeroWithScale = new BigDecimal("0.00");
        
        // Act
        boolean result = FinancialCalculator.isZero(zeroWithScale);
        
        // Assert
        assertThat(result)
            .as("0.00 (with scale) should be zero")
            .isTrue();
    }

    @Test
    @DisplayName("isZero - Positive Amount Returns False")
    void testIsZero_PositiveAmount() {
        // Arrange
        BigDecimal positiveAmount = new BigDecimal("100.00");
        
        // Act
        boolean result = FinancialCalculator.isZero(positiveAmount);
        
        // Assert
        assertThat(result)
            .as("100.00 should not be zero")
            .isFalse();
    }

    @Test
    @DisplayName("isZero - Negative Amount Returns False")
    void testIsZero_NegativeAmount() {
        // Arrange
        BigDecimal negativeAmount = new BigDecimal("-100.00");
        
        // Act
        boolean result = FinancialCalculator.isZero(negativeAmount);
        
        // Assert
        assertThat(result)
            .as("-100.00 should not be zero")
            .isFalse();
    }

    @Test
    @DisplayName("isZero - Null Amount Throws IllegalArgumentException")
    void testIsZero_NullAmount() {
        // Arrange
        BigDecimal nullAmount = null;
        
        // Act & Assert
        assertThatThrownBy(() -> FinancialCalculator.isZero(nullAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount")
            .hasMessageContaining("must not be null");
    }

    // ===========================================
    // Constructor Test
    // ===========================================

    @Test
    @DisplayName("Constructor - Utility Class Cannot Be Instantiated")
    void testConstructor_CannotInstantiate() {
        // Act & Assert
        assertThatThrownBy(() -> {
            java.lang.reflect.Constructor<FinancialCalculator> constructor = 
                FinancialCalculator.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
            .hasCauseInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Utility class cannot be instantiated");
    }
}
