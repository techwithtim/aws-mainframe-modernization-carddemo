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

import java.math.BigDecimal;

/**
 * Application-wide constants class defining static final fields for transaction type codes,
 * category codes, status codes, error message templates, date format patterns, numeric limits,
 * and configuration defaults.
 * <p>
 * This class is migrated from COBOL copybooks:
 * <ul>
 *   <li>COTTL01Y.cpy - Screen title constants</li>
 *   <li>CSMSG01Y.cpy - Message literal constants</li>
 * </ul>
 * <p>
 * Implements static utility class pattern with private constructor to prevent instantiation
 * following Java best practices.
 *
 * @see <a href="source_file:app/cpy/COTTL01Y.cpy">COTTL01Y.cpy</a>
 * @see <a href="source_file:app/cpy/CSMSG01Y.cpy">CSMSG01Y.cpy</a>
 */
public final class Constants {

    // ==================== Application Constants ====================
    // Migrated from COTTL01Y.cpy - CCDA-SCREEN-TITLE section

    /**
     * Main application title displayed in headers and responses.
     * Migrated from: CCDA-TITLE01 PIC X(40) VALUE 'AWS Mainframe Modernization'
     */
    public static final String APPLICATION_TITLE = "AWS Mainframe Modernization";

    /**
     * Application name identifier.
     * Migrated from: CCDA-TITLE02 PIC X(40) VALUE 'CardDemo'
     */
    public static final String APPLICATION_NAME = "CardDemo";

    /**
     * Thank you message displayed on application exit or completion screens.
     * Migrated from: CCDA-THANK-YOU PIC X(40) VALUE 'Thank you for using CCDA application...'
     */
    public static final String THANK_YOU_MESSAGE = "Thank you for using CCDA application...";

    // ==================== Message Constants ====================
    // Migrated from CSMSG01Y.cpy - CCDA-COMMON-MESSAGES section

    /**
     * Standard thank you message for successful operations.
     * Migrated from: CCDA-MSG-THANK-YOU PIC X(50) VALUE 'Thank you for using CardDemo application...'
     */
    public static final String MSG_THANK_YOU = "Thank you for using CardDemo application...";

    /**
     * Error message displayed when an invalid key or operation is attempted.
     * Migrated from: CCDA-MSG-INVALID-KEY PIC X(50) VALUE 'Invalid key pressed. Please see below...'
     */
    public static final String MSG_INVALID_KEY = "Invalid key pressed. Please see below...";

    // ==================== Date Format Constants ====================
    // Date format patterns for DateFormatter utility class compatibility

    /**
     * ISO 8601 date format pattern (YYYY-MM-DD).
     * Used for database storage and REST API request/response serialization.
     */
    public static final String DATE_FORMAT_ISO8601 = "yyyy-MM-dd";

    /**
     * Compact date format without delimiters (YYYYMMDD).
     * Used for legacy system integration and batch file processing.
     */
    public static final String DATE_FORMAT_YYYYMMDD = "yyyyMMdd";

    /**
     * Display date format for human-readable output (MM/DD/YYYY).
     * Used for user-facing displays and report generation.
     */
    public static final String DATE_FORMAT_DISPLAY = "MM/dd/yyyy";

    // ==================== Numeric Limit Constants ====================
    // Business rule limits using BigDecimal for exact decimal precision

    /**
     * Maximum credit limit that can be assigned to a credit card account.
     * Represented as BigDecimal to maintain precision matching COBOL COMP-3 packed decimal.
     * Value: $50,000.00
     */
    public static final BigDecimal MAX_CREDIT_LIMIT = new BigDecimal("50000.00");

    /**
     * Minimum payment percentage of outstanding balance required per billing cycle.
     * Represented as BigDecimal for exact fractional calculation.
     * Value: 2% (0.02 decimal)
     */
    public static final BigDecimal MIN_PAYMENT_PERCENTAGE = new BigDecimal("0.02");

    /**
     * Maximum transaction amount allowed for a single transaction.
     * Prevents fraudulent large-value transactions.
     * Value: $10,000.00
     */
    public static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("10000.00");

    // ==================== Pagination Constants ====================
    // Configuration defaults for paginated REST API responses

    /**
     * Default number of records per page for paginated queries.
     * Used when client does not specify page size parameter.
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Maximum number of records per page to prevent excessive data transfer.
     * Client-specified page size will be capped at this value.
     */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== Transaction Type Constants ====================
    // Transaction type codes matching COBOL CVTRA03Y.cpy transaction type definitions

    /**
     * Transaction type code for credit card purchases.
     * Used when customer makes a purchase with their card.
     */
    public static final String TRANSACTION_TYPE_PURCHASE = "PURCHASE";

    /**
     * Transaction type code for cash advance withdrawals.
     * Used when customer withdraws cash using their credit card.
     */
    public static final String TRANSACTION_TYPE_CASH_ADVANCE = "CASH_ADVANCE";

    /**
     * Transaction type code for balance transfers from other accounts.
     * Used when customer transfers balance from another credit card.
     */
    public static final String TRANSACTION_TYPE_BALANCE_TRANSFER = "BALANCE_TRANSFER";

    /**
     * Transaction type code for payment towards outstanding balance.
     * Used when customer makes a payment on their account.
     */
    public static final String TRANSACTION_TYPE_PAYMENT = "PAYMENT";

    /**
     * Transaction type code for refund or credit back to account.
     * Used when merchant issues refund for returned goods/services.
     */
    public static final String TRANSACTION_TYPE_REFUND = "REFUND";

    /**
     * Transaction type code for fees charged to account (late fee, annual fee, etc.).
     * Used for bank-imposed charges on the account.
     */
    public static final String TRANSACTION_TYPE_FEE = "FEE";

    /**
     * Transaction type code for interest charges on outstanding balance.
     * Used during interest calculation batch processing (CBACT04C).
     */
    public static final String TRANSACTION_TYPE_INTEREST = "INTEREST";

    // ==================== Status Constants ====================
    // Entity status codes for accounts, cards, and users

    /**
     * Status code indicating an active entity (account, card, or user).
     * Active entities are eligible for normal operations.
     */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * Status code indicating an inactive or suspended entity.
     * Inactive entities are not eligible for transactions or authentication.
     */
    public static final String STATUS_INACTIVE = "INACTIVE";

    // ==================== User Type Constants ====================
    // User role identifiers for access control

    /**
     * User type code for administrative users with elevated privileges.
     * Admin users can manage other users and access administrative functions.
     */
    public static final String USER_TYPE_ADMIN = "ADMIN";

    /**
     * User type code for regular users with standard privileges.
     * Regular users can manage their own accounts and perform customer operations.
     */
    public static final String USER_TYPE_REGULAR = "REGULAR";

    // ==================== Field Length Constants ====================
    // Maximum field lengths for validation, matching COBOL PIC clause definitions

    /**
     * Standard credit card number length.
     * Migrated from: PIC 9(16) in CVACT02Y.cpy
     */
    public static final int CARD_NUMBER_LENGTH = 16;

    /**
     * Social Security Number length (US format without dashes).
     * Migrated from: PIC 9(09) in CVCUS01Y.cpy
     */
    public static final int SSN_LENGTH = 9;

    /**
     * Account number length.
     * Migrated from: PIC 9(11) in CVACT01Y.cpy
     */
    public static final int ACCOUNT_NUMBER_LENGTH = 11;

    // ==================== Private Constructor ====================

    /**
     * Private constructor to prevent instantiation of this utility class.
     * This class contains only static constants and should never be instantiated.
     *
     * @throws UnsupportedOperationException if called via reflection
     */
    private Constants() {
        throw new UnsupportedOperationException("Constants is a utility class and cannot be instantiated");
    }
}
