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
package com.aws.carddemo.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class for precise BigDecimal arithmetic operations in financial calculations.
 * 
 * This class provides utility methods for currency operations that preserve COBOL COMP-3 
 * packed decimal precision (PIC S9(09)V99 format). All monetary calculations use 
 * RoundingMode.HALF_UP (banker's rounding) to match COBOL ROUNDED behavior, ensuring 
 * consistent precision for all currency operations.
 * 
 * <p>Migrated from: app/cbl/CBACT04C.cbl (Interest Calculation Program)
 * 
 * <p>Key Design Principles:
 * <ul>
 *   <li>All methods are static for consistent reuse across service layer</li>
 *   <li>Default scale is 2 decimal places for currency amounts</li>
 *   <li>RoundingMode.HALF_UP is used throughout for financial precision</li>
 *   <li>Null-safe: throws IllegalArgumentException for null inputs</li>
 * </ul>
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2025-01-01
 */
public final class FinancialCalculator {

    /**
     * Default scale for currency calculations (2 decimal places).
     * Matches COBOL PIC S9(09)V99 COMP-3 format.
     */
    private static final int DEFAULT_CURRENCY_SCALE = 2;

    /**
     * Default rounding mode for financial calculations.
     * HALF_UP corresponds to COBOL ROUNDED behavior.
     */
    private static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Constant for converting annual percentage rate to monthly rate.
     * Formula: APR / 1200 = APR / (12 months * 100 for percentage)
     */
    private static final BigDecimal MONTHLY_RATE_DIVISOR = new BigDecimal("1200");

    /**
     * Constant for percentage calculations (divide by 100).
     */
    private static final BigDecimal PERCENTAGE_DIVISOR = new BigDecimal("100");

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private FinancialCalculator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Calculates monthly interest for a given balance and annual percentage rate.
     * 
     * <p>Formula migrated from CBACT04C.cbl lines 464-465:
     * <pre>
     * COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
     * </pre>
     * 
     * <p>The formula divides by 1200 to convert annual percentage rate to monthly rate:
     * <ul>
     *   <li>12 months in a year</li>
     *   <li>100 to convert percentage to decimal</li>
     *   <li>12 * 100 = 1200</li>
     * </ul>
     * 
     * <p>Example: Balance of $1,000.00 at 18% APR:
     * <pre>
     * monthlyInterest = 1000.00 * 18 / 1200 = 15.00
     * </pre>
     * 
     * @param balance the account or transaction category balance (must not be null)
     * @param annualPercentageRate the annual percentage rate (APR) as a decimal 
     *                             (e.g., 18.0 for 18% APR, must not be null)
     * @return the calculated monthly interest amount, rounded to 2 decimal places 
     *         using HALF_UP rounding mode
     * @throws IllegalArgumentException if balance or annualPercentageRate is null
     */
    public static BigDecimal calculateMonthlyInterest(BigDecimal balance, 
                                                      BigDecimal annualPercentageRate) {
        validateNotNull(balance, "balance");
        validateNotNull(annualPercentageRate, "annualPercentageRate");

        // Formula: (balance * APR) / 1200
        return balance.multiply(annualPercentageRate)
                     .divide(MONTHLY_RATE_DIVISOR, DEFAULT_CURRENCY_SCALE, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Adds two BigDecimal amounts with currency precision.
     * 
     * <p>Result is rounded to 2 decimal places using HALF_UP rounding mode.
     * 
     * @param a the first amount (must not be null)
     * @param b the second amount (must not be null)
     * @return the sum of a and b, rounded to 2 decimal places
     * @throws IllegalArgumentException if a or b is null
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        validateNotNull(a, "first amount");
        validateNotNull(b, "second amount");

        return a.add(b).setScale(DEFAULT_CURRENCY_SCALE, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Subtracts one BigDecimal amount from another with currency precision.
     * 
     * <p>Result is rounded to 2 decimal places using HALF_UP rounding mode.
     * Commonly used for balance reductions, payment processing, and fee deductions.
     * 
     * @param a the amount to subtract from (minuend, must not be null)
     * @param b the amount to subtract (subtrahend, must not be null)
     * @return the difference (a - b), rounded to 2 decimal places
     * @throws IllegalArgumentException if a or b is null
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        validateNotNull(a, "minuend");
        validateNotNull(b, "subtrahend");

        return a.subtract(b).setScale(DEFAULT_CURRENCY_SCALE, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Multiplies two BigDecimal amounts with currency precision.
     * 
     * <p>Result is rounded to 2 decimal places using HALF_UP rounding mode.
     * Commonly used for fee calculations, quantity-based pricing, and rate applications.
     * 
     * @param amount the base amount (must not be null)
     * @param factor the multiplication factor (must not be null)
     * @return the product (amount * factor), rounded to 2 decimal places
     * @throws IllegalArgumentException if amount or factor is null
     */
    public static BigDecimal multiply(BigDecimal amount, BigDecimal factor) {
        validateNotNull(amount, "amount");
        validateNotNull(factor, "factor");

        return amount.multiply(factor).setScale(DEFAULT_CURRENCY_SCALE, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Divides one BigDecimal amount by another with configurable precision.
     * 
     * <p>Supports configurable scale for scenarios requiring different precision levels.
     * Uses HALF_UP rounding mode to match COBOL ROUNDED behavior.
     * 
     * @param dividend the amount to be divided (must not be null)
     * @param divisor the amount to divide by (must not be null and not zero)
     * @param scale the number of decimal places in the result
     * @return the quotient (dividend / divisor), rounded to the specified scale
     * @throws IllegalArgumentException if dividend or divisor is null
     * @throws ArithmeticException if divisor is zero
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor, int scale) {
        validateNotNull(dividend, "dividend");
        validateNotNull(divisor, "divisor");

        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }

        return dividend.divide(divisor, scale, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Calculates a percentage of a given amount.
     * 
     * <p>Formula: amount * (percent / 100)
     * 
     * <p>Example: 10% of $100.00 = $10.00
     * <pre>
     * percentage(new BigDecimal("100.00"), new BigDecimal("10")) = 10.00
     * </pre>
     * 
     * @param amount the base amount (must not be null)
     * @param percent the percentage value (e.g., 10 for 10%, must not be null)
     * @return the calculated percentage amount, rounded to 2 decimal places
     * @throws IllegalArgumentException if amount or percent is null
     */
    public static BigDecimal percentage(BigDecimal amount, BigDecimal percent) {
        validateNotNull(amount, "amount");
        validateNotNull(percent, "percent");

        return amount.multiply(percent)
                     .divide(PERCENTAGE_DIVISOR, DEFAULT_CURRENCY_SCALE, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Rounds a BigDecimal amount to currency precision (2 decimal places).
     * 
     * <p>Enforces 2 decimal places with HALF_UP rounding, matching COBOL COMP-3 
     * PIC S9(09)V99 format. This ensures all monetary values maintain consistent 
     * precision throughout the system.
     * 
     * @param amount the amount to round (must not be null)
     * @return the amount rounded to 2 decimal places
     * @throws IllegalArgumentException if amount is null
     */
    public static BigDecimal roundToCurrency(BigDecimal amount) {
        validateNotNull(amount, "amount");

        return amount.setScale(DEFAULT_CURRENCY_SCALE, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Compares two BigDecimal amounts.
     * 
     * <p>Returns:
     * <ul>
     *   <li>-1 if a < b (a is less than b)</li>
     *   <li>0 if a == b (a is equal to b)</li>
     *   <li>1 if a > b (a is greater than b)</li>
     * </ul>
     * 
     * @param a the first amount (must not be null)
     * @param b the second amount (must not be null)
     * @return -1, 0, or 1 as a is numerically less than, equal to, or greater than b
     * @throws IllegalArgumentException if a or b is null
     */
    public static int compareAmounts(BigDecimal a, BigDecimal b) {
        validateNotNull(a, "first amount");
        validateNotNull(b, "second amount");

        return a.compareTo(b);
    }

    /**
     * Checks if a BigDecimal amount is positive (greater than zero).
     * 
     * @param amount the amount to check (must not be null)
     * @return true if amount > 0, false otherwise
     * @throws IllegalArgumentException if amount is null
     */
    public static boolean isPositive(BigDecimal amount) {
        validateNotNull(amount, "amount");

        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks if a BigDecimal amount is negative (less than zero).
     * 
     * @param amount the amount to check (must not be null)
     * @return true if amount < 0, false otherwise
     * @throws IllegalArgumentException if amount is null
     */
    public static boolean isNegative(BigDecimal amount) {
        validateNotNull(amount, "amount");

        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Checks if a BigDecimal amount is zero.
     * 
     * @param amount the amount to check (must not be null)
     * @return true if amount == 0, false otherwise
     * @throws IllegalArgumentException if amount is null
     */
    public static boolean isZero(BigDecimal amount) {
        validateNotNull(amount, "amount");

        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Validates that a parameter is not null.
     * 
     * @param value the value to check
     * @param parameterName the name of the parameter for error messaging
     * @throws IllegalArgumentException if value is null
     */
    private static void validateNotNull(Object value, String parameterName) {
        if (value == null) {
            throw new IllegalArgumentException(parameterName + " must not be null");
        }
    }
}
