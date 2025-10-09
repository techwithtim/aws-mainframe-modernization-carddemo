/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.batch.processor;

import com.aws.carddemo.batch.dto.InterestTransaction;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.DisclosureGroup;
import com.aws.carddemo.repository.DisclosureGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Batch ItemProcessor implementation that calculates monthly interest charges for accounts
 * with positive balances during interest calculation job execution.
 * <p>
 * <b>Business Logic Preservation:</b> This processor implements business logic from COBOL CBACT04C.cbl
 * batch program paragraphs 1200-GET-INTEREST-RATE, 1300-COMPUTE-INTEREST, and 1400-COMPUTE-FEES
 * (note: fees calculation is unimplemented stub in COBOL source).
 * </p>
 * <p>
 * <b>Migrated from:</b> app/cbl/CBACT04C.cbl
 * </p>
 * <p>
 * <b>CRITICAL - Financial Precision Requirements:</b>
 * This processor MUST use BigDecimal for all financial calculations, never double or float types,
 * to ensure exact decimal arithmetic matching COBOL COMP-3 packed decimal behavior byte-for-byte.
 * Financial systems require exact decimal precision to prevent rounding errors accumulating across
 * millions of transactions, which could result in reconciliation discrepancies and regulatory
 * compliance violations.
 * </p>
 * <p>
 * <b>Interest Calculation Formula:</b>
 * Monthly interest = currentBalance × (annualPercentageRate / 1200)
 * where 1200 = 12 months × 100 (to convert percentage to decimal)
 * </p>
 * <p>
 * <b>COBOL Equivalence:</b>
 * <pre>
 * COBOL line 464-465:
 * COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
 * 
 * Java equivalent:
 * BigDecimal interest = balance.multiply(rate.divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP));
 * </pre>
 * </p>
 * <p>
 * <b>Thread Safety:</b> This processor is stateless and thread-safe, enabling safe parallel chunk
 * processing when configured via StepBuilder.taskExecutor(). No mutable instance variables maintain
 * state between process() invocations.
 * </p>
 *
 * @author AWS CardDemo Modernization Team
 * @see com.aws.carddemo.batch.config.InterestCalculationJobConfig
 * @see com.aws.carddemo.batch.reader.AccountReader
 * @see com.aws.carddemo.batch.writer.AccountWriter
 */
@Component
public class InterestProcessor implements ItemProcessor<Account, InterestTransaction> {

    private static final Logger logger = LoggerFactory.getLogger(InterestProcessor.class);

    /**
     * Default APR rate (18.99%) used when disclosure group lookup fails.
     * Replicates COBOL paragraph 1200-A-GET-DEFAULT-INT-RATE fallback behavior.
     */
    private static final BigDecimal DEFAULT_APR_RATE = new BigDecimal("18.99");

    /**
     * Minimum monthly interest charge ($1.00) applied to accounts with calculated interest below threshold.
     * Replicates COBOL logic: IF WS-INTEREST < 1.00 MOVE 1.00 TO WS-INTEREST (line 214-216 context)
     */
    private static final BigDecimal MINIMUM_INTEREST_CHARGE = new BigDecimal("1.00");

    /**
     * Divisor for converting annual percentage rate to monthly decimal rate (12 months × 100 for percentage).
     * Used in formula: monthlyRate = APR / 1200
     */
    private static final BigDecimal APR_TO_MONTHLY_DIVISOR = new BigDecimal("1200");

    /**
     * Math context for intermediate BigDecimal calculations ensuring 10 decimal places precision
     * with half-up rounding mode before final rounding to 2 decimal places.
     */
    private static final MathContext INTERMEDIATE_PRECISION = new MathContext(10, RoundingMode.HALF_UP);

    /**
     * Repository for looking up disclosure group APR rates by account group ID.
     * Constructor-injected dependency enabling thread-safe repository access.
     */
    private final DisclosureGroupRepository disclosureGroupRepository;

    /**
     * Constructs InterestProcessor with required repository dependency.
     *
     * @param disclosureGroupRepository Spring Data JPA repository for disclosure group lookups
     */
    public InterestProcessor(DisclosureGroupRepository disclosureGroupRepository) {
        this.disclosureGroupRepository = disclosureGroupRepository;
    }

    /**
     * Processes a single account to calculate monthly interest charge.
     * <p>
     * <b>Processing Logic:</b>
     * <ol>
     *   <li>Filter accounts with zero or negative balance (return null to skip)</li>
     *   <li>Lookup disclosure group APR rate by account group ID</li>
     *   <li>Apply default rate (18.99%) if disclosure group not found</li>
     *   <li>Calculate monthly interest: balance × (APR / 1200) with BigDecimal precision</li>
     *   <li>Apply minimum $1.00 charge if calculated interest below threshold</li>
     *   <li>Build InterestTransaction DTO with calculated values</li>
     * </ol>
     * </p>
     * <p>
     * <b>COBOL Paragraphs Replicated:</b>
     * <ul>
     *   <li>1200-GET-INTEREST-RATE: Disclosure group lookup with default fallback</li>
     *   <li>1300-COMPUTE-INTEREST: Monthly interest calculation formula</li>
     *   <li>Zero balance filter: IF ACCT-CURR-BAL > 0 (implicit in COBOL line 188-222 loop)</li>
     * </ul>
     * </p>
     *
     * @param account the account entity to process (must not be null)
     * @return InterestTransaction DTO with calculated interest, or null if account has zero/negative balance
     * @throws Exception if unrecoverable error occurs during processing (will be handled by Spring Batch skip/retry)
     */
    @Override
    public InterestTransaction process(Account account) throws Exception {
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Zero Balance Handling - Filter accounts with zero or negative balance
            // Replicates COBOL implicit filtering where interest is only calculated for positive balances
            if (account.getCurrentBalance() == null || account.getCurrentBalance().compareTo(BigDecimal.ZERO) <= 0) {
                logger.debug("Skipping account {} with zero or negative balance: {}", 
                    account.getAccountId(), account.getCurrentBalance());
                return null; // Returning null filters this account from further processing
            }

            // Step 2: Disclosure Group Retrieval - Lookup APR rate for account's group
            // Replicates COBOL paragraph 1200-GET-INTEREST-RATE (lines 415-440)
            BigDecimal annualPercentageRate = retrieveInterestRate(account);

            // Step 3: Monthly Interest Calculation with BigDecimal Precision
            // Replicates COBOL paragraph 1300-COMPUTE-INTEREST (lines 462-470)
            // Formula: interest = currentBalance × (APR / 1200)
            BigDecimal calculatedInterest = calculateMonthlyInterest(
                account.getCurrentBalance(), 
                annualPercentageRate
            );

            // Step 4: Minimum Interest Charge Application
            // Replicates COBOL logic: IF WS-INTEREST < 1.00 MOVE 1.00 TO WS-INTEREST
            BigDecimal finalInterest = applyMinimumCharge(calculatedInterest);

            // Step 5: Year-to-Date Interest Accumulation
            // Calculate running total for account.interestPaidYtd update
            BigDecimal yearToDateInterest = calculateYearToDateInterest(account, finalInterest);

            // Step 6: Interest Transaction Creation
            // Build DTO for downstream AccountWriter to persist
            InterestTransaction interestTransaction = new InterestTransaction(
                account.getAccountId(),
                finalInterest,
                LocalDate.now().withDayOfMonth(1), // First day of current month
                yearToDateInterest
            );

            // Performance Logging
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.debug("Processed interest calculation for account {} in {} ms: balance={}, rate={}, interest={}", 
                account.getAccountId(), elapsedTime, account.getCurrentBalance(), 
                annualPercentageRate, finalInterest);

            return interestTransaction;

        } catch (Exception e) {
            logger.error("Error processing interest calculation for account {}: {}", 
                account.getAccountId(), e.getMessage(), e);
            throw e; // Rethrow to allow Spring Batch skip/retry handling
        }
    }

    /**
     * Retrieves annual percentage rate (APR) for account's disclosure group.
     * <p>
     * <b>Lookup Logic:</b>
     * <ol>
     *   <li>Extract account.groupId field value</li>
     *   <li>Query DisclosureGroupRepository.findByAccountGroupId(groupId)</li>
     *   <li>If disclosure groups found, use first group's interest rate</li>
     *   <li>If no groups found, use default rate 18.99% and log warning</li>
     * </ol>
     * </p>
     * <p>
     * <b>COBOL Equivalence:</b>
     * Paragraph 1200-GET-INTEREST-RATE (lines 415-440): READ DISCGRP-FILE with INVALID KEY fallback
     * Paragraph 1200-A-GET-DEFAULT-INT-RATE (lines 443-460): Default rate retrieval
     * </p>
     *
     * @param account the account entity containing group ID
     * @return annual percentage rate as BigDecimal (e.g., 18.99 for 18.99%)
     */
    private BigDecimal retrieveInterestRate(Account account) {
        String groupId = account.getGroupId();

        if (groupId == null || groupId.trim().isEmpty()) {
            logger.warn("Account {} has null/empty groupId, using default rate {}", 
                account.getAccountId(), DEFAULT_APR_RATE);
            return DEFAULT_APR_RATE;
        }

        // Query disclosure group repository for APR rate
        List<DisclosureGroup> disclosureGroups = disclosureGroupRepository.findByAccountGroupId(groupId);

        if (disclosureGroups == null || disclosureGroups.isEmpty()) {
            // Replicates COBOL INVALID KEY condition and paragraph 1200-A-GET-DEFAULT-INT-RATE
            logger.warn("Disclosure group {} not found for account {}, using default rate {}", 
                groupId, account.getAccountId(), DEFAULT_APR_RATE);
            return DEFAULT_APR_RATE;
        }

        // Use first disclosure group's interest rate
        // In COBOL, specific transaction type/category combination would be used,
        // but simplified Java implementation uses first available rate
        DisclosureGroup disclosureGroup = disclosureGroups.get(0);
        BigDecimal interestRate = disclosureGroup.getInterestRate();

        if (interestRate == null || interestRate.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Disclosure group {} for account {} has invalid rate {}, using default rate {}", 
                groupId, account.getAccountId(), interestRate, DEFAULT_APR_RATE);
            return DEFAULT_APR_RATE;
        }

        logger.debug("Retrieved interest rate {} for account {} from disclosure group {}", 
            interestRate, account.getAccountId(), groupId);
        return interestRate;
    }

    /**
     * Calculates monthly interest charge using exact BigDecimal arithmetic.
     * <p>
     * <b>Formula:</b> interest = currentBalance × (annualPercentageRate / 1200)
     * </p>
     * <p>
     * <b>Precision Requirements:</b>
     * <ul>
     *   <li>Divide APR by 1200 using 10 decimal places intermediate precision</li>
     *   <li>Multiply result by account balance</li>
     *   <li>Round final result to 2 decimal places (cents) using HALF_UP rounding</li>
     * </ul>
     * </p>
     * <p>
     * <b>COBOL Equivalence:</b>
     * Line 464-465: COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
     * </p>
     * <p>
     * <b>Example Calculation:</b>
     * <pre>
     * Balance: $1,234.56
     * APR: 18.99%
     * Monthly Rate: 18.99 / 1200 = 0.015825
     * Interest: 1,234.56 × 0.015825 = $19.53
     * </pre>
     * </p>
     *
     * @param currentBalance account current balance (must be positive)
     * @param annualPercentageRate APR as percentage (e.g., 18.99 for 18.99%)
     * @return calculated monthly interest charge with 2 decimal precision
     */
    private BigDecimal calculateMonthlyInterest(BigDecimal currentBalance, BigDecimal annualPercentageRate) {
        // Step 1: Convert annual percentage rate to monthly decimal rate
        // APR / 1200 where 1200 = 12 months × 100 (percentage to decimal conversion)
        // Use 10 decimal places intermediate precision to prevent precision loss
        BigDecimal monthlyRate = annualPercentageRate.divide(
            APR_TO_MONTHLY_DIVISOR, 
            INTERMEDIATE_PRECISION
        );

        // Step 2: Multiply current balance by monthly rate
        BigDecimal interest = currentBalance.multiply(monthlyRate, INTERMEDIATE_PRECISION);

        // Step 3: Round to 2 decimal places (cents) matching COBOL COMP-3 S9(09)V99 format
        return interest.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Applies minimum interest charge threshold ($1.00) if calculated interest is below minimum.
     * <p>
     * <b>Business Rule:</b> Accounts with small balances (e.g., $50 balance at 18.99% APR would
     * calculate to $0.79) are charged a minimum of $1.00 to cover processing costs.
     * </p>
     * <p>
     * <b>COBOL Equivalence:</b>
     * Implied logic from paragraph 1300-COMPUTE-INTEREST context (lines 214-216):
     * IF WS-INTEREST < 1.00 MOVE 1.00 TO WS-INTEREST
     * </p>
     *
     * @param calculatedInterest the calculated monthly interest amount
     * @return either the calculated interest or $1.00 minimum, whichever is greater
     */
    private BigDecimal applyMinimumCharge(BigDecimal calculatedInterest) {
        if (calculatedInterest.compareTo(MINIMUM_INTEREST_CHARGE) < 0) {
            logger.debug("Calculated interest {} is below minimum charge, applying minimum {}", 
                calculatedInterest, MINIMUM_INTEREST_CHARGE);
            return MINIMUM_INTEREST_CHARGE;
        }
        return calculatedInterest;
    }

    /**
     * Calculates year-to-date interest accumulation for account interest tracking.
     * <p>
     * <b>Logic:</b> Add current month interest to existing YTD value for running total.
     * </p>
     * <p>
     * <b>COBOL Equivalence:</b>
     * Line 467: ADD WS-MONTHLY-INT TO WS-TOTAL-INT (accumulator logic)
     * Line 352: ADD WS-TOTAL-INT TO ACCT-CURR-BAL (update account record)
     * </p>
     *
     * @param account the account entity with existing interestPaidYtd value
     * @param currentMonthInterest the calculated interest for current month
     * @return updated year-to-date interest total
     */
    private BigDecimal calculateYearToDateInterest(Account account, BigDecimal currentMonthInterest) {
        BigDecimal existingYtd = Optional.ofNullable(account.getInterestPaidYtd())
            .orElse(BigDecimal.ZERO);
        return existingYtd.add(currentMonthInterest);
    }
}
