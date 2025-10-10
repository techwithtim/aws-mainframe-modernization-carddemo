/*
 * ExpirationDateValidator.java
 *
 * Validator implementation for @ValidExpirationDate annotation
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.dto.request;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

/**
 * Validator implementation for @ValidExpirationDate annotation.
 * 
 * <p>This validator checks that the expiration date represented by expirationMonth and
 * expirationYear fields is in the future. Credit cards are valid through the last day
 * of the expiration month, so the validation constructs a date for the last day and
 * compares it to the current date.</p>
 * 
 * <p><strong>Validation Algorithm:</strong></p>
 * <ol>
 *   <li>Check if request object or required fields are null (return true, let @NotNull handle)</li>
 *   <li>Construct LocalDate for first day of expiration month/year</li>
 *   <li>Add one month and subtract one day to get last day of month</li>
 *   <li>Compare with LocalDate.now() to ensure it's in the future</li>
 *   <li>If validation fails, build custom error message with actual date</li>
 * </ol>
 * 
 * <p><strong>Example Validation:</strong></p>
 * <ul>
 *   <li>Today: 2024-10-08, Expiration: 12/2025 → VALID (2025-12-31 is future)</li>
 *   <li>Today: 2024-10-08, Expiration: 09/2024 → INVALID (2024-09-30 is past)</li>
 *   <li>Today: 2024-10-08, Expiration: 10/2024 → INVALID (2024-10-31 may be past depending on day)</li>
 * </ul>
 * 
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>Invalid month/year combinations (e.g., month=13) are caught and handled gracefully</li>
 *   <li>DateTimeException from LocalDate.of() is caught and validation passes</li>
 *   <li>This allows individual field validators to report the specific error</li>
 * </ul>
 */
public class ExpirationDateValidator implements ConstraintValidator<ValidExpirationDate, CardUpdateRequest> {

    /**
     * Initializes the validator (no initialization needed for this validator).
     * 
     * @param constraintAnnotation annotation instance for this constraint
     */
    @Override
    public void initialize(ValidExpirationDate constraintAnnotation) {
        // No initialization required
    }

    /**
     * Validates that the card expiration date is in the future.
     * 
     * <p>Credit cards are valid through the LAST DAY of the expiration month.
     * For example, a card with expiration 12/2025 is valid through 2025-12-31.</p>
     * 
     * @param request the CardUpdateRequest object to validate
     * @param context validation context for building custom error messages
     * @return true if expiration date is in the future or fields are null, false otherwise
     */
    @Override
    public boolean isValid(CardUpdateRequest request, ConstraintValidatorContext context) {
        // If request is null or either field is null, let @NotNull validation handle it
        if (request == null || request.getExpirationMonth() == null || request.getExpirationYear() == null) {
            return true;
        }

        try {
            // Create a date representing the last day of the expiration month
            // Credit cards are valid through the last day of the expiration month
            // Strategy: Create first day of month, add 1 month, subtract 1 day = last day
            LocalDate expirationDate = LocalDate.of(
                request.getExpirationYear(),
                request.getExpirationMonth(),
                1  // First day of the month
            ).plusMonths(1).minusDays(1);  // Last day of the month

            // Get current date for comparison
            LocalDate today = LocalDate.now();
            
            // Expiration date must be AFTER today (not equal, as card expires at end of day)
            if (!expirationDate.isAfter(today)) {
                // Build custom error message with actual date values
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    String.format(
                        "Card expiration date (%02d/%d) must be in the future. " +
                        "Card expires on %s, but today is %s.",
                        request.getExpirationMonth(),
                        request.getExpirationYear(),
                        expirationDate,
                        today
                    )
                ).addConstraintViolation();
                return false;
            }

            return true;

        } catch (Exception e) {
            // If date construction fails (e.g., invalid month/year combination like month=13),
            // let individual field validations (@Min, @Max) handle the error.
            // This validator only checks if the date combination is in the future,
            // not if the individual components are valid.
            return true;
        }
    }
}
