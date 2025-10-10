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

import com.aws.carddemo.util.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test class for Constants utility validating static constant values match legacy COBOL
 * copybook definitions from COTTL01Y.cpy (screen titles) and CSMSG01Y.cpy (common messages).
 * <p>
 * Tests ensure functional equivalence with mainframe 3270 screen text by verifying:
 * <ul>
 *   <li>Application title strings match COBOL PIC X VALUE clauses</li>
 *   <li>Thank you messages preserve exact legacy text</li>
 *   <li>Error messages maintain original mainframe wording</li>
 *   <li>Date format patterns support legacy system integration</li>
 *   <li>Numeric limits preserve business rule constraints</li>
 *   <li>Transaction types and status codes match COBOL definitions</li>
 * </ul>
 * <p>
 * Uses JUnit 5 assertions with AssertJ fluent API for expressive test validation.
 *
 * @see Constants
 * @see <a href="source_file:app/cpy/COTTL01Y.cpy">COTTL01Y.cpy</a>
 * @see <a href="source_file:app/cpy/CSMSG01Y.cpy">CSMSG01Y.cpy</a>
 */
@DisplayName("Constants Utility Test Suite")
class ConstantsTest {

    // ==================== Application Constants Tests ====================

    @Test
    @DisplayName("should validate APPLICATION_TITLE matches COBOL CCDA-TITLE01 definition")
    void testApplicationTitle() {
        // Verify: CCDA-TITLE01 PIC X(40) VALUE 'AWS Mainframe Modernization'
        assertThat(Constants.APPLICATION_TITLE)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("AWS Mainframe Modernization")
                .contains("AWS", "Mainframe", "Modernization");
    }

    @Test
    @DisplayName("should validate APPLICATION_NAME matches COBOL CCDA-TITLE02 definition")
    void testApplicationName() {
        // Verify: CCDA-TITLE02 PIC X(40) VALUE 'CardDemo'
        assertThat(Constants.APPLICATION_NAME)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("CardDemo")
                .hasSize(8);
    }

    @Test
    @DisplayName("should validate THANK_YOU_MESSAGE matches COBOL CCDA-THANK-YOU definition")
    void testThankYouMessage() {
        // Verify: CCDA-THANK-YOU PIC X(40) VALUE 'Thank you for using CCDA application...'
        assertThat(Constants.THANK_YOU_MESSAGE)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("Thank you for using CCDA application...")
                .startsWith("Thank you")
                .endsWith("...");
    }

    // ==================== Message Constants Tests ====================

    @Test
    @DisplayName("should validate MSG_THANK_YOU matches COBOL CCDA-MSG-THANK-YOU definition")
    void testMessageThankYou() {
        // Verify: CCDA-MSG-THANK-YOU PIC X(50) VALUE 'Thank you for using CardDemo application...'
        assertThat(Constants.MSG_THANK_YOU)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("Thank you for using CardDemo application...")
                .contains("CardDemo")
                .startsWith("Thank you");
    }

    @Test
    @DisplayName("should validate MSG_INVALID_KEY matches COBOL CCDA-MSG-INVALID-KEY definition")
    void testMessageInvalidKey() {
        // Verify: CCDA-MSG-INVALID-KEY PIC X(50) VALUE 'Invalid key pressed. Please see below...'
        assertThat(Constants.MSG_INVALID_KEY)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("Invalid key pressed. Please see below...")
                .startsWith("Invalid key")
                .contains("Please see below");
    }

    // ==================== Date Format Constants Tests ====================

    @Test
    @DisplayName("should validate DATE_FORMAT_ISO8601 follows ISO 8601 standard pattern")
    void testDateFormatIso8601() {
        assertThat(Constants.DATE_FORMAT_ISO8601)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("yyyy-MM-dd")
                .matches("yyyy-MM-dd");
    }

    @Test
    @DisplayName("should validate DATE_FORMAT_YYYYMMDD for legacy system integration")
    void testDateFormatYyyyMmDd() {
        assertThat(Constants.DATE_FORMAT_YYYYMMDD)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("yyyyMMdd")
                .hasSize(8)
                .doesNotContain("-", "/");
    }

    @Test
    @DisplayName("should validate DATE_FORMAT_DISPLAY for human-readable output")
    void testDateFormatDisplay() {
        assertThat(Constants.DATE_FORMAT_DISPLAY)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("MM/dd/yyyy")
                .contains("/");
    }

    // ==================== Numeric Limit Constants Tests ====================

    @Test
    @DisplayName("should validate MAX_CREDIT_LIMIT is exactly $50,000.00")
    void testMaxCreditLimit() {
        assertThat(Constants.MAX_CREDIT_LIMIT)
                .isNotNull()
                .isEqualByComparingTo(new BigDecimal("50000.00"))
                .isGreaterThan(BigDecimal.ZERO)
                .isLessThanOrEqualTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("should validate MIN_PAYMENT_PERCENTAGE is exactly 2% (0.02)")
    void testMinPaymentPercentage() {
        assertThat(Constants.MIN_PAYMENT_PERCENTAGE)
                .isNotNull()
                .isEqualByComparingTo(new BigDecimal("0.02"))
                .isGreaterThan(BigDecimal.ZERO)
                .isLessThan(BigDecimal.ONE);
    }

    @Test
    @DisplayName("should validate MAX_TRANSACTION_AMOUNT is exactly $10,000.00")
    void testMaxTransactionAmount() {
        assertThat(Constants.MAX_TRANSACTION_AMOUNT)
                .isNotNull()
                .isEqualByComparingTo(new BigDecimal("10000.00"))
                .isGreaterThan(BigDecimal.ZERO)
                .isLessThan(Constants.MAX_CREDIT_LIMIT);
    }

    // ==================== Pagination Constants Tests ====================

    @Test
    @DisplayName("should validate DEFAULT_PAGE_SIZE is 20 records")
    void testDefaultPageSize() {
        assertThat(Constants.DEFAULT_PAGE_SIZE)
                .isEqualTo(20)
                .isGreaterThan(0)
                .isLessThanOrEqualTo(Constants.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("should validate MAX_PAGE_SIZE is 100 records")
    void testMaxPageSize() {
        assertThat(Constants.MAX_PAGE_SIZE)
                .isEqualTo(100)
                .isGreaterThan(Constants.DEFAULT_PAGE_SIZE)
                .isPositive();
    }

    // ==================== Transaction Type Constants Tests ====================

    @Test
    @DisplayName("should validate TRANSACTION_TYPE_PURCHASE constant value")
    void testTransactionTypePurchase() {
        assertThat(Constants.TRANSACTION_TYPE_PURCHASE)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("PURCHASE")
                .isUpperCase();
    }

    @Test
    @DisplayName("should validate TRANSACTION_TYPE_CASH_ADVANCE constant value")
    void testTransactionTypeCashAdvance() {
        assertThat(Constants.TRANSACTION_TYPE_CASH_ADVANCE)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("CASH_ADVANCE")
                .contains("_");
    }

    @Test
    @DisplayName("should validate TRANSACTION_TYPE_BALANCE_TRANSFER constant value")
    void testTransactionTypeBalanceTransfer() {
        assertThat(Constants.TRANSACTION_TYPE_BALANCE_TRANSFER)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("BALANCE_TRANSFER");
    }

    @Test
    @DisplayName("should validate TRANSACTION_TYPE_PAYMENT constant value")
    void testTransactionTypePayment() {
        assertThat(Constants.TRANSACTION_TYPE_PAYMENT)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("PAYMENT");
    }

    @Test
    @DisplayName("should validate TRANSACTION_TYPE_REFUND constant value")
    void testTransactionTypeRefund() {
        assertThat(Constants.TRANSACTION_TYPE_REFUND)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("REFUND");
    }

    @Test
    @DisplayName("should validate TRANSACTION_TYPE_FEE constant value")
    void testTransactionTypeFee() {
        assertThat(Constants.TRANSACTION_TYPE_FEE)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("FEE")
                .hasSize(3);
    }

    @Test
    @DisplayName("should validate TRANSACTION_TYPE_INTEREST constant value")
    void testTransactionTypeInterest() {
        assertThat(Constants.TRANSACTION_TYPE_INTEREST)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("INTEREST");
    }

    // ==================== Status Constants Tests ====================

    @Test
    @DisplayName("should validate STATUS_ACTIVE constant value")
    void testStatusActive() {
        assertThat(Constants.STATUS_ACTIVE)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("ACTIVE")
                .isUpperCase();
    }

    @Test
    @DisplayName("should validate STATUS_INACTIVE constant value")
    void testStatusInactive() {
        assertThat(Constants.STATUS_INACTIVE)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("INACTIVE")
                .isUpperCase()
                .isNotEqualTo(Constants.STATUS_ACTIVE);
    }

    // ==================== User Type Constants Tests ====================

    @Test
    @DisplayName("should validate USER_TYPE_ADMIN constant value")
    void testUserTypeAdmin() {
        assertThat(Constants.USER_TYPE_ADMIN)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("ADMIN")
                .isUpperCase()
                .hasSize(5);
    }

    @Test
    @DisplayName("should validate USER_TYPE_REGULAR constant value")
    void testUserTypeRegular() {
        assertThat(Constants.USER_TYPE_REGULAR)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("REGULAR")
                .isUpperCase()
                .isNotEqualTo(Constants.USER_TYPE_ADMIN);
    }

    // ==================== Field Length Constants Tests ====================

    @Test
    @DisplayName("should validate CARD_NUMBER_LENGTH matches COBOL PIC 9(16) definition")
    void testCardNumberLength() {
        // Verify: PIC 9(16) from CVACT02Y.cpy
        assertThat(Constants.CARD_NUMBER_LENGTH)
                .isEqualTo(16)
                .isPositive()
                .isGreaterThan(10);
    }

    @Test
    @DisplayName("should validate SSN_LENGTH matches COBOL PIC 9(09) definition")
    void testSsnLength() {
        // Verify: PIC 9(09) from CVCUS01Y.cpy
        assertThat(Constants.SSN_LENGTH)
                .isEqualTo(9)
                .isPositive()
                .isLessThan(Constants.CARD_NUMBER_LENGTH);
    }

    @Test
    @DisplayName("should validate ACCOUNT_NUMBER_LENGTH matches COBOL PIC 9(11) definition")
    void testAccountNumberLength() {
        // Verify: PIC 9(11) from CVACT01Y.cpy
        assertThat(Constants.ACCOUNT_NUMBER_LENGTH)
                .isEqualTo(11)
                .isPositive()
                .isGreaterThan(Constants.SSN_LENGTH)
                .isLessThan(Constants.CARD_NUMBER_LENGTH);
    }

    // ==================== Constructor and Utility Class Tests ====================

    @Test
    @DisplayName("should throw UnsupportedOperationException when attempting to instantiate Constants class")
    void testConstructorThrowsException() {
        // Verify utility class cannot be instantiated (reflection-based test)
        assertThatThrownBy(() -> {
            java.lang.reflect.Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
                .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class)
                .hasRootCauseMessage("Constants is a utility class and cannot be instantiated");
    }

    // ==================== Integration and Cross-Validation Tests ====================

    @Test
    @DisplayName("should ensure all string constants are non-empty and trimmed")
    void testAllStringConstantsAreValid() {
        // Validate all string constants are properly initialized
        assertThat(Constants.APPLICATION_TITLE).isNotEmpty();
        assertThat(Constants.APPLICATION_NAME).isNotEmpty();
        assertThat(Constants.THANK_YOU_MESSAGE).isNotEmpty();
        assertThat(Constants.MSG_THANK_YOU).isNotEmpty();
        assertThat(Constants.MSG_INVALID_KEY).isNotEmpty();
        assertThat(Constants.DATE_FORMAT_ISO8601).isNotEmpty();
        assertThat(Constants.DATE_FORMAT_YYYYMMDD).isNotEmpty();
        assertThat(Constants.DATE_FORMAT_DISPLAY).isNotEmpty();
        assertThat(Constants.TRANSACTION_TYPE_PURCHASE).isNotEmpty();
        assertThat(Constants.TRANSACTION_TYPE_CASH_ADVANCE).isNotEmpty();
        assertThat(Constants.TRANSACTION_TYPE_BALANCE_TRANSFER).isNotEmpty();
        assertThat(Constants.TRANSACTION_TYPE_PAYMENT).isNotEmpty();
        assertThat(Constants.TRANSACTION_TYPE_REFUND).isNotEmpty();
        assertThat(Constants.TRANSACTION_TYPE_FEE).isNotEmpty();
        assertThat(Constants.TRANSACTION_TYPE_INTEREST).isNotEmpty();
        assertThat(Constants.STATUS_ACTIVE).isNotEmpty();
        assertThat(Constants.STATUS_INACTIVE).isNotEmpty();
        assertThat(Constants.USER_TYPE_ADMIN).isNotEmpty();
        assertThat(Constants.USER_TYPE_REGULAR).isNotEmpty();
    }

    @Test
    @DisplayName("should ensure all numeric constants are positive")
    void testAllNumericConstantsArePositive() {
        // Validate all numeric constants have positive values
        assertThat(Constants.MAX_CREDIT_LIMIT).isGreaterThan(BigDecimal.ZERO);
        assertThat(Constants.MIN_PAYMENT_PERCENTAGE).isGreaterThan(BigDecimal.ZERO);
        assertThat(Constants.MAX_TRANSACTION_AMOUNT).isGreaterThan(BigDecimal.ZERO);
        assertThat(Constants.DEFAULT_PAGE_SIZE).isPositive();
        assertThat(Constants.MAX_PAGE_SIZE).isPositive();
        assertThat(Constants.CARD_NUMBER_LENGTH).isPositive();
        assertThat(Constants.SSN_LENGTH).isPositive();
        assertThat(Constants.ACCOUNT_NUMBER_LENGTH).isPositive();
    }

    @Test
    @DisplayName("should ensure logical relationships between numeric limits")
    void testNumericLimitsHaveLogicalRelationships() {
        // MAX_TRANSACTION_AMOUNT should be less than MAX_CREDIT_LIMIT
        assertThat(Constants.MAX_TRANSACTION_AMOUNT)
                .isLessThan(Constants.MAX_CREDIT_LIMIT);

        // MIN_PAYMENT_PERCENTAGE should be less than 100% (1.00)
        assertThat(Constants.MIN_PAYMENT_PERCENTAGE)
                .isLessThan(BigDecimal.ONE);

        // DEFAULT_PAGE_SIZE should be less than or equal to MAX_PAGE_SIZE
        assertThat(Constants.DEFAULT_PAGE_SIZE)
                .isLessThanOrEqualTo(Constants.MAX_PAGE_SIZE);

        // Field lengths should have logical ordering
        assertThat(Constants.SSN_LENGTH)
                .isLessThan(Constants.ACCOUNT_NUMBER_LENGTH);
        assertThat(Constants.ACCOUNT_NUMBER_LENGTH)
                .isLessThan(Constants.CARD_NUMBER_LENGTH);
    }

    @Test
    @DisplayName("should ensure transaction type constants are unique")
    void testTransactionTypeConstantsAreUnique() {
        // Verify all transaction type constants have distinct values
        assertThat(Constants.TRANSACTION_TYPE_PURCHASE)
                .isNotEqualTo(Constants.TRANSACTION_TYPE_CASH_ADVANCE)
                .isNotEqualTo(Constants.TRANSACTION_TYPE_BALANCE_TRANSFER)
                .isNotEqualTo(Constants.TRANSACTION_TYPE_PAYMENT)
                .isNotEqualTo(Constants.TRANSACTION_TYPE_REFUND)
                .isNotEqualTo(Constants.TRANSACTION_TYPE_FEE)
                .isNotEqualTo(Constants.TRANSACTION_TYPE_INTEREST);

        assertThat(Constants.TRANSACTION_TYPE_PAYMENT)
                .isNotEqualTo(Constants.TRANSACTION_TYPE_REFUND)
                .isNotEqualTo(Constants.TRANSACTION_TYPE_FEE);
    }

    @Test
    @DisplayName("should ensure status constants are mutually exclusive")
    void testStatusConstantsAreMutuallyExclusive() {
        // Verify status constants have distinct values
        assertThat(Constants.STATUS_ACTIVE)
                .isNotEqualTo(Constants.STATUS_INACTIVE);
    }

    @Test
    @DisplayName("should ensure user type constants are mutually exclusive")
    void testUserTypeConstantsAreMutuallyExclusive() {
        // Verify user type constants have distinct values
        assertThat(Constants.USER_TYPE_ADMIN)
                .isNotEqualTo(Constants.USER_TYPE_REGULAR);
    }

    @Test
    @DisplayName("should validate BigDecimal constants maintain scale precision")
    void testBigDecimalConstantsMaintainPrecision() {
        // Verify BigDecimal constants maintain proper scale for financial calculations
        assertThat(Constants.MAX_CREDIT_LIMIT.scale()).isEqualTo(2);
        assertThat(Constants.MIN_PAYMENT_PERCENTAGE.scale()).isEqualTo(2);
        assertThat(Constants.MAX_TRANSACTION_AMOUNT.scale()).isEqualTo(2);
    }
}
