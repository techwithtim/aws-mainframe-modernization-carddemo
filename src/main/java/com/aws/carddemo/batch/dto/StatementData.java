/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.batch.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Data Transfer Object (DTO) representing fully formatted monthly account statement data
 * ready for template rendering (Thymeleaf HTML or iText PDF generation).
 * <p>
 * This immutable DTO is produced by StatementProcessor ItemProcessor as output in Spring Batch
 * chunk-oriented processing for the statement generation job. It contains all formatted data
 * necessary for downstream StatementWriter to render complete statement documents without
 * requiring additional business logic processing during the write phase.
 * </p>
 * <p>
 * <b>COBOL Source Mapping:</b> Migrated from app/cbl/CBSTM03A.CBL paragraph 5000-CREATE-STATEMENT
 * and STATEMENT-LINES copybook structure. The nested record hierarchy preserves the COBOL
 * statement layout sections:
 * <ul>
 *   <li>ST-LINE1 through ST-LINE4 → CustomerInfo (name and address header)</li>
 *   <li>ST-LINE7, ST-LINE8, ST-LINE9 → AccountSummary (basic details section)</li>
 *   <li>ST-LINE13, ST-LINE14 → FormattedTransaction (transaction detail lines)</li>
 *   <li>ST-LINE14A → AccountSummary totalDebits/totalCredits (summary totals)</li>
 * </ul>
 * </p>
 * <p>
 * <b>Usage in Spring Batch:</b>
 * <pre>
 * StatementProcessor (ItemProcessor) → StatementData → StatementWriter (ItemWriter)
 * </pre>
 * The processor aggregates account, customer, and transaction data, formats monetary values,
 * masks sensitive data per PCI-DSS requirements, and produces this immutable DTO for rendering.
 * </p>
 * <p>
 * <b>Serialization:</b> Implements Serializable to support Spring Batch chunk serialization when
 * formatted statement DTOs are passed between processor and writer components across JVM boundaries
 * in remote partitioning or restart scenarios.
 * </p>
 *
 * @param accountSummary  Financial summary with balances, charges, and payment due information
 * @param transactionList Chronologically ordered list of formatted transaction details
 * @param customerInfo    Customer identification and address with PCI-DSS compliant masking
 * @param statementPeriod Billing cycle date range and statement generation date
 * 
 * @see com.aws.carddemo.batch.processor.StatementProcessor
 * @see com.aws.carddemo.batch.writer.StatementWriter
 */
public record StatementData(
        @NotNull AccountSummary accountSummary,
        @NotNull List<FormattedTransaction> transactionList,
        @NotNull CustomerInfo customerInfo,
        @NotNull StatementPeriod statementPeriod
) implements Serializable {

    /**
     * Serial version UID for Serializable compatibility in Spring Batch serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Account financial summary section containing balances, charges, and payment information.
     * <p>
     * <b>COBOL Mapping:</b> Corresponds to STATEMENT-LINES ST-LINE8, ST-LINE12, ST-LINE14A
     * summary fields from CBSTM03A.CBL statement generation logic.
     * </p>
     * <p>
     * All monetary fields use BigDecimal with scale=2 to preserve exact decimal precision
     * matching COBOL PIC S9(09)V99 COMP-3 packed decimal representation for financial data.
     * </p>
     *
     * @param previousBalance    Account balance at start of statement period (opening balance)
     * @param currentBalance     Account balance at end of statement period (closing balance)
     *                           from ACCT-CURR-BAL → ST-CURR-BAL (COBOL line 484)
     * @param totalDebits        Sum of all debit transactions (purchases, fees) in statement period
     * @param totalCredits       Sum of all credit transactions (payments, refunds) in statement period
     * @param financeCharges     Interest charges accrued during statement period
     * @param minimumPaymentDue  Minimum payment amount required (typically 2-5% of balance)
     * @param paymentDueDate     Date by which minimum payment must be received (typically statement date + 21 days)
     */
    public record AccountSummary(
            @NotNull BigDecimal previousBalance,
            @NotNull BigDecimal currentBalance,
            @NotNull BigDecimal totalDebits,
            @NotNull BigDecimal totalCredits,
            @NotNull BigDecimal financeCharges,
            @NotNull BigDecimal minimumPaymentDue,
            @NotNull LocalDate paymentDueDate
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Formatted individual transaction detail for statement body section.
     * <p>
     * <b>COBOL Mapping:</b> Corresponds to STATEMENT-LINES ST-LINE13 and ST-LINE14 transaction
     * detail lines written by paragraph 6000-WRITE-TRANS in CBSTM03A.CBL (lines 675-723).
     * </p>
     * <p>
     * Transactions are pre-formatted by StatementProcessor with proper date formatting,
     * description truncation/padding, and monetary value display formatting (e.g., "1,234.56-" for credits).
     * </p>
     *
     * @param transactionDate    Formatted transaction date (e.g., "2024-01-15" or "Jan 15, 2024")
     * @param description        Transaction description/merchant name from TRNX-DESC → ST-TRANDT
     *                           (max 49 characters, left-aligned, COBOL line 677)
     * @param referenceNumber    Transaction ID from TRNX-ID → ST-TRANID (16 characters, COBOL line 676)
     * @param formattedAmount    Pre-formatted amount string with currency symbol and sign
     *                           from TRNX-AMT → ST-TRANAMT PIC Z(9).99- (COBOL line 678)
     */
    public record FormattedTransaction(
            @NotNull String transactionDate,
            @NotNull String description,
            @NotNull String referenceNumber,
            @NotNull String formattedAmount
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Customer identification and address information for statement header section.
     * <p>
     * <b>COBOL Mapping:</b> Corresponds to STATEMENT-LINES ST-LINE1 through ST-LINE4 header
     * section built in paragraph 5000-CREATE-STATEMENT (CBSTM03A.CBL lines 462-481).
     * Customer name constructed via STRING from CUST-FIRST-NAME, CUST-MIDDLE-NAME, CUST-LAST-NAME.
     * Address lines from CUST-ADDR-LINE-1, CUST-ADDR-LINE-2, CUST-ADDR-LINE-3 with city/state/zip.
     * </p>
     * <p>
     * <b>PCI-DSS Compliance:</b> Account number is masked in format "XXXX-XXXX-XXXX-1234" showing
     * only last 4 digits. Full account number must never appear in rendered statements.
     * </p>
     *
     * @param maskedAccountNumber PCI-DSS compliant masked account number (format: XXXX-XXXX-XXXX-1234)
     *                            from ACCT-ID → ST-ACCT-ID with masking applied (COBOL line 483)
     * @param customerName        Full customer name (FirstName MiddleName LastName) from
     *                            CUST-FIRST-NAME + CUST-MIDDLE-NAME + CUST-LAST-NAME concatenation
     *                            (COBOL lines 462-469)
     * @param formattedAddress    Complete formatted multi-line address string including street,
     *                            city, state, country, and zip code from CUST-ADDR-LINE-1/2/3,
     *                            CUST-ADDR-STATE-CD, CUST-ADDR-COUNTRY-CD, CUST-ADDR-ZIP
     *                            (COBOL lines 470-481)
     */
    public record CustomerInfo(
            @NotNull String maskedAccountNumber,
            @NotNull String customerName,
            @NotNull String formattedAddress
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Statement billing cycle period and generation date metadata.
     * <p>
     * <b>COBOL Mapping:</b> Corresponds to statement period calculation logic in CBSTM03A.CBL.
     * While not explicitly visible in STATEMENT-LINES structure, period dates are derived from
     * transaction file date ranges and current processing date logic in the batch job.
     * </p>
     * <p>
     * The billing cycle typically spans one month, with statement generated on the cycle close date
     * and payment due 21 days later. This metadata supports statement header rendering
     * ("Statement Period: Jan 1, 2024 - Jan 31, 2024").
     * </p>
     *
     * @param startDate     First day of billing cycle (e.g., first day of month)
     * @param endDate       Last day of billing cycle (e.g., last day of month)
     * @param statementDate Statement generation date (typically same as endDate or next business day)
     */
    public record StatementPeriod(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull LocalDate statementDate
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
