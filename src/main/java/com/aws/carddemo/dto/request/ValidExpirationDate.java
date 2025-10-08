/*
 * ValidExpirationDate.java
 *
 * Custom Bean Validation annotation for card expiration date validation
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom validation annotation to ensure the card expiration date is in the future.
 * 
 * <p>This is a class-level Bean Validation constraint that validates the combination of
 * expirationMonth and expirationYear fields to ensure they represent a future date.
 * Individual field validations ensure valid ranges, but this annotation ensures the
 * combination represents a date that has not yet passed.</p>
 * 
 * <p><strong>Validation Logic:</strong></p>
 * <ul>
 *   <li>Constructs a LocalDate representing the LAST DAY of the expiration month/year</li>
 *   <li>Compares this date against the current system date (LocalDate.now())</li>
 *   <li>Fails validation if the expiration date is today or in the past</li>
 *   <li>Passes validation if either month or year is null (delegated to @NotNull)</li>
 * </ul>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * @ValidExpirationDate
 * public class CardUpdateRequest {
 *     private Integer expirationMonth;
 *     private Integer expirationYear;
 * }
 * }</pre>
 * 
 * <p><strong>Business Context:</strong></p>
 * <ul>
 *   <li>Prevents setting or updating cards with expired dates</li>
 *   <li>Ensures compliance with payment card industry standards</li>
 *   <li>Replaces COBOL date validation logic from CSUTLDTC.cbl utility</li>
 * </ul>
 * 
 * @see ExpirationDateValidator
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExpirationDateValidator.class)
@Documented
public @interface ValidExpirationDate {
    /**
     * Default validation error message.
     * Can be overridden in ValidationMessages.properties for internationalization.
     */
    String message() default "Card expiration date must be in the future";

    /**
     * Validation groups for conditional validation.
     */
    Class<?>[] groups() default {};

    /**
     * Payload for clients to assign custom payload objects to a constraint.
     */
    Class<? extends Payload>[] payload() default {};
}
