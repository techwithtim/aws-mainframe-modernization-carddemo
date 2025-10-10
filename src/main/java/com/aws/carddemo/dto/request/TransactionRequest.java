/*
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
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
package com.aws.carddemo.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Transaction creation request DTO for POST /api/v1/transactions endpoint.
 * <p>
 * Migrated from: app/bms/COTRN02.bms (Transaction Add screen)
 * <p>
 * Replaces EXEC CICS RECEIVE MAP(COTRN2A) MAPSET(COTRN02) with Spring @RequestBody binding.
 * This DTO captures manual transaction entry data including account/card identifiers,
 * transaction type, category, amount, dates, and merchant details.
 * <p>
 * Coordinates with TransactionService.postTransaction() method that implements
 * CBTRN01C.cbl transaction posting business logic.
 * <p>
 * <strong>Business Rule:</strong> Either accountId OR cardNumber must be provided (mutually exclusive),
 * replicating COBOL EVALUATE logic from COTRN02C.cbl that chooses between account-based
 * or card-based transaction lookup.
 *
 * @see com.aws.carddemo.service.TransactionService#postTransaction(TransactionRequest)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TransactionRequest.OneOfRequired
public class TransactionRequest {

    /**
     * Account identifier for account-based transaction entry.
     * <p>
     * Mapped from: ACTIDIN DFHMDF POS=(06,21) LENGTH=11 ATTRB=UNPROT
     * <p>
     * Optional if cardNumber is provided. Validated as positive integer when present.
     */
    @JsonProperty("accountId")
    @Positive(message = "Account ID must be a positive number")
    private Long accountId;

    /**
     * Card number for card-based transaction entry.
     * <p>
     * Mapped from: CARDNIN DFHMDF POS=(06,55) LENGTH=16 ATTRB=UNPROT
     * <p>
     * Optional if accountId is provided. Must be exactly 16 digits when present.
     */
    @JsonProperty("cardNumber")
    @Pattern(regexp = "\\d{16}", message = "Card number must be exactly 16 digits")
    private String cardNumber;

    /**
     * Transaction type code (e.g., '01' for purchase, '02' for refund).
     * <p>
     * Mapped from: TTYPCD DFHMDF POS=(10,15) LENGTH=2 ATTRB=UNPROT
     * <p>
     * References: TRANTYPE-CD from CVTRA03Y.cpy
     * <p>
     * Required field - transaction type must be provided.
     */
    @JsonProperty("transactionTypeCode")
    @NotBlank(message = "Transaction type code is required")
    @Size(max = 2, message = "Transaction type code must not exceed 2 characters")
    private String transactionTypeCode;

    /**
     * Transaction category code for transaction classification.
     * <p>
     * Mapped from: TCATCD DFHMDF POS=(10,36) LENGTH=4 ATTRB=UNPROT
     * <p>
     * References: TRANCAT-CD from CVTRA04Y.cpy
     * <p>
     * Required field - transaction category must be provided.
     */
    @JsonProperty("transactionCategoryCode")
    @NotBlank(message = "Transaction category code is required")
    @Size(max = 4, message = "Transaction category code must not exceed 4 characters")
    private String transactionCategoryCode;

    /**
     * Transaction source identifier (e.g., 'ONLINE', 'ATM', 'MANUAL', 'POS').
     * <p>
     * Mapped from: TRNSRC DFHMDF POS=(10,54) LENGTH=10 ATTRB=UNPROT
     * <p>
     * Optional field - defaults to 'MANUAL' for manual transaction entry if not provided.
     */
    @JsonProperty("transactionSource")
    @Size(max = 10, message = "Transaction source must not exceed 10 characters")
    private String transactionSource;

    /**
     * Transaction amount with exact decimal precision for financial calculations.
     * <p>
     * Mapped from: TRNAMT DFHMDF POS=(14,14) LENGTH=12 (format hint: -99999999.99)
     * <p>
     * Preserves COBOL PIC S9(09)V99 COMP-3 packed decimal precision.
     * Must be positive (at least 0.01) with maximum 9 integer digits and 2 decimal places.
     * <p>
     * Required field - transaction amount must be provided.
     */
    @JsonProperty("transactionAmount")
    @NotNull(message = "Transaction amount is required")
    @DecimalMin(value = "0.01", message = "Transaction amount must be at least 0.01")
    @Digits(integer = 9, fraction = 2, message = "Transaction amount must have at most 9 integer digits and 2 decimal places")
    private BigDecimal transactionAmount;

    /**
     * Transaction date (original transaction date from user input).
     * <p>
     * Mapped from: TORIGDT DFHMDF POS=(14,42) LENGTH=10 (format hint: YYYY-MM-DD)
     * <p>
     * Required field - must not be in the future. Validated as past or present date.
     * JSON format: yyyy-MM-dd (ISO 8601)
     */
    @JsonProperty("transactionDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Transaction date is required")
    @PastOrPresent(message = "Transaction date cannot be in the future")
    private LocalDate transactionDate;

    /**
     * Transaction time (time-of-day for manual transaction entry).
     * <p>
     * Provides timezone-agnostic time representation for transaction audit trail.
     * Separate from transaction date for precise timestamp tracking.
     * <p>
     * Required field - time must be provided for audit purposes.
     * JSON format: HH:mm:ss (24-hour format)
     */
    @JsonProperty("transactionTime")
    @JsonFormat(pattern = "HH:mm:ss")
    @NotNull(message = "Transaction time is required")
    private LocalTime transactionTime;

    /**
     * Transaction description (narrative text describing the transaction).
     * <p>
     * Mapped from: TDESC DFHMDF POS=(12,19) LENGTH=60 ATTRB=UNPROT
     * <p>
     * Replaces COBOL PIC X(60).
     * Required field - description must be provided for transaction clarity.
     */
    @JsonProperty("transactionDescription")
    @NotBlank(message = "Transaction description is required")
    @Size(max = 60, message = "Transaction description must not exceed 60 characters")
    private String transactionDescription;

    /**
     * Merchant identifier (optional for non-merchant transactions).
     * <p>
     * Mapped from: MID DFHMDF POS=(16,19) LENGTH=9 ATTRB=UNPROT
     * <p>
     * Applicable for merchant-based transactions (e.g., POS purchases).
     */
    @JsonProperty("merchantId")
    @Size(max = 9, message = "Merchant ID must not exceed 9 characters")
    private String merchantId;

    /**
     * Merchant name (optional for non-merchant transactions).
     * <p>
     * Mapped from: MNAME DFHMDF POS=(16,48) LENGTH=30 ATTRB=UNPROT
     * <p>
     * Applicable for merchant-based transactions.
     */
    @JsonProperty("merchantName")
    @Size(max = 30, message = "Merchant name must not exceed 30 characters")
    private String merchantName;

    /**
     * Merchant city (optional for non-merchant transactions).
     * <p>
     * Mapped from: MCITY DFHMDF POS=(18,21) LENGTH=25 ATTRB=UNPROT
     * <p>
     * Applicable for merchant-based transactions.
     */
    @JsonProperty("merchantCity")
    @Size(max = 25, message = "Merchant city must not exceed 25 characters")
    private String merchantCity;

    /**
     * Merchant ZIP code (optional for non-merchant transactions).
     * <p>
     * Mapped from: MZIP DFHMDF POS=(18,67) LENGTH=10 ATTRB=UNPROT
     * <p>
     * Supports both 5-digit and 9-digit ZIP codes (XXXXX or XXXXX-XXXX).
     * Applicable for merchant-based transactions.
     */
    @JsonProperty("merchantZip")
    @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "Merchant ZIP must be in format XXXXX or XXXXX-XXXX")
    private String merchantZip;

    /**
     * Confirmation flag for transaction submission workflow.
     * <p>
     * Mapped from: CONFIRM DFHMDF POS=(21,63) LENGTH=1 (Y/N validation hint)
     * <p>
     * Indicates user has confirmed transaction details before posting.
     * Must be 'Y' or 'N'. Optional field.
     */
    @JsonProperty("confirmationFlag")
    @Pattern(regexp = "[YN]", message = "Confirmation flag must be 'Y' or 'N'")
    private String confirmationFlag;

    /**
     * Process date for transaction (optional, typically populated by the system).
     * <p>
     * Mapped from: TPROCDT DFHMDF POS=(14,68) LENGTH=10 (format hint: YYYY-MM-DD)
     * <p>
     * May differ from transactionDate if transaction is processed on a different date.
     * JSON format: yyyy-MM-dd (ISO 8601)
     */
    @JsonProperty("processDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate processDate;

    // ==================== Custom Validation Annotation ====================

    /**
     * Custom validation annotation ensuring either accountId or cardNumber is provided
     * but not both, replicating COBOL EVALUATE logic from COTRN02C.cbl.
     * <p>
     * <strong>Business Rule:</strong> Transaction can be entered by account ID OR card number (mutually exclusive).
     * <p>
     * This annotation is applied at the class level and validates the mutual exclusivity
     * constraint across both fields.
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = OneOfRequiredValidator.class)
    @Documented
    public @interface OneOfRequired {
        String message() default "Either accountId or cardNumber must be provided, but not both";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Validator implementation for {@link OneOfRequired} annotation.
     * <p>
     * Ensures mutual exclusivity between accountId and cardNumber fields.
     * Implements XOR logic: exactly one of the two fields must be provided.
     * <p>
     * Validation Rules:
     * <ul>
     *   <li>If neither field is provided: validation fails</li>
     *   <li>If both fields are provided: validation fails</li>
     *   <li>If exactly one field is provided: validation passes</li>
     * </ul>
     */
    public static class OneOfRequiredValidator implements ConstraintValidator<OneOfRequired, TransactionRequest> {

        @Override
        public void initialize(OneOfRequired constraintAnnotation) {
            // No initialization needed
        }

        @Override
        public boolean isValid(TransactionRequest request, ConstraintValidatorContext context) {
            if (request == null) {
                return true; // Let @NotNull handle null objects
            }

            boolean hasAccountId = request.getAccountId() != null;
            boolean hasCardNumber = request.getCardNumber() != null && !request.getCardNumber().trim().isEmpty();

            // XOR logic: exactly one must be provided
            boolean isValid = hasAccountId ^ hasCardNumber;

            if (!isValid) {
                context.disableDefaultConstraintViolation();
                if (!hasAccountId && !hasCardNumber) {
                    context.buildConstraintViolationWithTemplate(
                        "Either accountId or cardNumber must be provided"
                    ).addConstraintViolation();
                } else if (hasAccountId && hasCardNumber) {
                    context.buildConstraintViolationWithTemplate(
                        "Cannot provide both accountId and cardNumber - only one is allowed"
                    ).addConstraintViolation();
                }
            }

            return isValid;
        }
    }
}
