/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.exception;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Custom unchecked business rule violation exception thrown when a payment or transaction
 * amount exceeds the available credit limit on an account.
 * 
 * <p>This exception replaces COBOL balance validation logic from the legacy mainframe
 * application, specifically the credit limit enforcement pattern:
 * <pre>
 * IF ACCT-CURR-BAL + TXN-AMT > ACCT-CREDIT-LIMIT 
 *     PERFORM 9999-ABEND-PROGRAM
 * END-IF
 * </pre>
 * 
 * <p>Migrated from COBOL programs:
 * <ul>
 *   <li>{@code app/cbl/CBTRN01C.cbl} - Transaction posting batch program with balance validation</li>
 *   <li>{@code app/cbl/COBIL00C.cbl} - Bill payment online program with credit checks</li>
 * </ul>
 * 
 * <p><strong>Spring Transaction Integration:</strong><br>
 * This exception extends {@link RuntimeException} to enable automatic Spring {@code @Transactional}
 * rollback. When thrown within a transactional method, Spring will automatically roll back
 * the entire transaction, preventing overdraft commits and maintaining data integrity.
 * 
 * <p><strong>HTTP Status Mapping:</strong><br>
 * Maps to HTTP 422 Unprocessable Entity status code via {@code GlobalExceptionHandler}.
 * The handler method constructs a detailed {@code ApiError} response with financial context:
 * <pre>
 * {
 *   "error": "Insufficient funds",
 *   "message": "Requested amount $2,500.00 exceeds available credit...",
 *   "requestedAmount": 2500.00,
 *   "availableBalance": 1000.00,
 *   "creditLimit": 5000.00,
 *   "timestamp": "2024-01-15T10:30:00Z"
 * }
 * </pre>
 * 
 * <p><strong>PCI-DSS Compliance:</strong><br>
 * This exception implements credit limit enforcement required for PCI-DSS compliance.
 * Financial amounts are included in error responses for legitimate transaction decline
 * notifications, but sensitive cardholder data (card numbers, CVV) must never be included.
 * 
 * <p><strong>Usage Examples:</strong><br>
 * In service layer credit validation:
 * <pre>
 * {@code
 * public TransactionResponse postTransaction(TransactionRequest request) {
 *     Account account = accountRepository.findById(accountId)
 *         .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
 *     
 *     // Calculate new balance after transaction
 *     BigDecimal newBalance = account.getCurrentBalance().add(request.getAmount());
 *     
 *     // Enforce credit limit (replaces COBOL ACCT-CURR-BAL + TXN-AMT > ACCT-CREDIT-LIMIT check)
 *     if (newBalance.compareTo(account.getCreditLimit()) > 0) {
 *         throw new InsufficientFundsException(
 *             request.getAmount(),
 *             account.getCurrentBalance(),
 *             account.getCreditLimit()
 *         );
 *     }
 *     
 *     // Process transaction...
 * }
 * }
 * </pre>
 * 
 * @see RuntimeException
 * @see java.math.BigDecimal
 * @author AWS Migration Team
 * @version 1.0
 * @since 1.0
 */
@Getter
public class InsufficientFundsException extends RuntimeException {

    /**
     * The amount requested for the payment or transaction that caused the insufficient funds condition.
     * Represents the transaction amount that would exceed the available credit limit.
     * 
     * <p>Corresponds to COBOL field: {@code TXN-AMT} (PIC S9(09)V99 COMP-3)
     */
    private final BigDecimal requestedAmount;

    /**
     * The current account balance at the time of the validation failure.
     * For credit card accounts, this represents the outstanding balance owed.
     * 
     * <p>Corresponds to COBOL field: {@code ACCT-CURR-BAL} (PIC S9(09)V99 COMP-3)
     */
    private final BigDecimal availableBalance;

    /**
     * The credit limit configured for the account.
     * Maximum balance the account holder is authorized to carry.
     * 
     * <p>Corresponds to COBOL field: {@code ACCT-CREDIT-LIMIT} (PIC S9(09)V99 COMP-3)
     */
    private final BigDecimal creditLimit;

    /**
     * Constructs a new InsufficientFundsException with detailed financial context.
     * 
     * <p>This is the primary constructor used by service layer validation logic.
     * It generates a descriptive error message including all financial amounts
     * and calculates the shortfall for user notification.
     * 
     * @param requestedAmount The amount requested for payment/transaction that caused the violation
     * @param availableBalance The current account balance (outstanding balance)
     * @param creditLimit The maximum credit limit allowed for the account
     * 
     * @throws NullPointerException if any parameter is null
     */
    public InsufficientFundsException(
            BigDecimal requestedAmount,
            BigDecimal availableBalance,
            BigDecimal creditLimit) {
        super(buildMessage(requestedAmount, availableBalance, creditLimit));
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
        this.creditLimit = creditLimit;
    }

    /**
     * Constructs a new InsufficientFundsException with detailed financial context and a custom message.
     * 
     * <p>Use this constructor when you need to provide additional context beyond the
     * standard financial details, such as business-specific error codes or validation reasons.
     * 
     * @param message Custom error message describing the specific validation failure
     * @param requestedAmount The amount requested for payment/transaction
     * @param availableBalance The current account balance
     * @param creditLimit The maximum credit limit allowed
     * 
     * @throws NullPointerException if message or any BigDecimal parameter is null
     */
    public InsufficientFundsException(
            String message,
            BigDecimal requestedAmount,
            BigDecimal availableBalance,
            BigDecimal creditLimit) {
        super(message);
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
        this.creditLimit = creditLimit;
    }

    /**
     * Constructs a new InsufficientFundsException with detailed financial context and a root cause.
     * 
     * <p>Use this constructor when the insufficient funds condition was triggered by an
     * underlying exception that should be preserved in the exception chain for debugging.
     * 
     * @param requestedAmount The amount requested for payment/transaction
     * @param availableBalance The current account balance
     * @param creditLimit The maximum credit limit allowed
     * @param cause The underlying exception that triggered this business rule violation
     * 
     * @throws NullPointerException if any BigDecimal parameter or cause is null
     */
    public InsufficientFundsException(
            BigDecimal requestedAmount,
            BigDecimal availableBalance,
            BigDecimal creditLimit,
            Throwable cause) {
        super(buildMessage(requestedAmount, availableBalance, creditLimit), cause);
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
        this.creditLimit = creditLimit;
    }

    /**
     * Constructs a new InsufficientFundsException with custom message, financial context, and root cause.
     * 
     * <p>This is the most comprehensive constructor, supporting full exception chain
     * preservation along with custom error messaging and financial details.
     * 
     * @param message Custom error message
     * @param requestedAmount The amount requested for payment/transaction
     * @param availableBalance The current account balance
     * @param creditLimit The maximum credit limit allowed
     * @param cause The underlying exception that triggered this violation
     * 
     * @throws NullPointerException if any parameter is null
     */
    public InsufficientFundsException(
            String message,
            BigDecimal requestedAmount,
            BigDecimal availableBalance,
            BigDecimal creditLimit,
            Throwable cause) {
        super(message, cause);
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
        this.creditLimit = creditLimit;
    }

    /**
     * Builds a descriptive error message with financial details for the insufficient funds condition.
     * 
     * <p>The message includes:
     * <ul>
     *   <li>Requested amount that caused the validation failure</li>
     *   <li>Current available balance on the account</li>
     *   <li>Configured credit limit</li>
     *   <li>Calculated shortfall (how much over the limit)</li>
     * </ul>
     * 
     * <p>Example output:
     * <pre>
     * "Insufficient funds: Requested amount $2,500.00 exceeds available credit. 
     *  Current balance: $3,000.00, Credit limit: $5,000.00, Amount over limit: $500.00"
     * </pre>
     * 
     * @param requestedAmount The transaction amount that was requested
     * @param availableBalance The current account balance
     * @param creditLimit The maximum credit limit
     * @return A formatted error message with financial context
     */
    private static String buildMessage(
            BigDecimal requestedAmount,
            BigDecimal availableBalance,
            BigDecimal creditLimit) {
        
        // Calculate the new balance that would result from this transaction
        BigDecimal newBalance = availableBalance.add(requestedAmount);
        
        // Calculate how much the transaction exceeds the credit limit
        BigDecimal amountOverLimit = newBalance.subtract(creditLimit);
        
        // Build comprehensive error message matching COBOL validation failure pattern
        return String.format(
            "Insufficient funds: Requested amount $%,.2f exceeds available credit. " +
            "Current balance: $%,.2f, Credit limit: $%,.2f, Amount over limit: $%,.2f",
            requestedAmount,
            availableBalance,
            creditLimit,
            amountOverLimit
        );
    }

    /**
     * Calculates the available credit remaining on the account.
     * This is the amount that can still be charged before reaching the credit limit.
     * 
     * <p>Formula: {@code creditLimit - availableBalance}
     * 
     * @return The remaining available credit, or zero if the account is at or over the limit
     */
    public BigDecimal getAvailableCredit() {
        BigDecimal remaining = creditLimit.subtract(availableBalance);
        // Return zero if already over limit (shouldn't happen, but defensive programming)
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * Calculates how much the requested transaction would exceed the credit limit.
     * 
     * <p>Formula: {@code (availableBalance + requestedAmount) - creditLimit}
     * 
     * @return The amount over the limit, or zero if within limit
     */
    public BigDecimal getAmountOverLimit() {
        BigDecimal newBalance = availableBalance.add(requestedAmount);
        BigDecimal overLimit = newBalance.subtract(creditLimit);
        return overLimit.compareTo(BigDecimal.ZERO) > 0 ? overLimit : BigDecimal.ZERO;
    }
}
