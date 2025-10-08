/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User update request DTO for modifying existing user profiles.
 * 
 * <p>Migrated from: app/bms/COUSR02.bms (Update User screen)</p>
 * 
 * <p>This DTO captures user profile modification data for the 
 * PUT /api/v1/admin/users/{userId} endpoint, replacing the CICS BMS 
 * COUSR02 screen (EXEC CICS RECEIVE MAP(COUSR2A) MAPSET(COUSR02)).</p>
 * 
 * <p><strong>BMS Field Mappings:</strong></p>
 * <ul>
 *   <li>USRIDIN (POS=(6,21) LENGTH=8 ATTRB=UNPROT) → userId (passed as path variable, not in request body)</li>
 *   <li>FNAME (POS=(11,18) LENGTH=20 ATTRB=UNPROT) → firstName (required, max 20 chars)</li>
 *   <li>LNAME (POS=(11,56) LENGTH=20 ATTRB=UNPROT) → lastName (required, max 20 chars)</li>
 *   <li>PASSWD (POS=(13,16) LENGTH=8 ATTRB=(UNPROT,DRK)) → password (optional, 8-20 chars if provided)</li>
 *   <li>USRTYPE (POS=(15,17) LENGTH=1 ATTRB=UNPROT) → userType (required, 'A' or 'R')</li>
 * </ul>
 * 
 * <p><strong>Password Change Workflow:</strong></p>
 * <p>The password field is optional to support scenarios where administrators
 * update user profiles without changing passwords. If password is provided
 * (non-null and non-empty), it will be BCrypt hashed per PCI-DSS requirements
 * before storage. If password is null or empty, the existing password is preserved.</p>
 * 
 * <p><strong>Password Confirmation:</strong></p>
 * <p>The confirmPassword field must match the password field if password is provided.
 * This validation is enforced at the service layer (UserService.updateUser()) to ensure
 * password changes are intentional and free from typos. When password is null/empty,
 * confirmPassword validation is skipped.</p>
 * 
 * <p><strong>User Type Transitions:</strong></p>
 * <p>The userType field accepts 'A' (Admin) or 'R' (Regular), enabling role transitions
 * such as promoting a regular user to admin or demoting an admin to regular user.
 * Note: The original BMS screen displayed '(A=Admin, U=User)' but the modernized
 * system uses 'R' for Regular to align with Spring Security ROLE_ADMIN/ROLE_USER
 * naming conventions.</p>
 * 
 * <p><strong>Security:</strong></p>
 * <p>This endpoint requires @PreAuthorize("hasRole('ADMIN')") authorization.
 * Only administrators can update user profiles. Audit logging is performed
 * for all user modifications to maintain compliance with security requirements.</p>
 * 
 * @see com.aws.carddemo.controller.AdminController#updateUser(String, UserUpdateRequest)
 * @see com.aws.carddemo.service.UserService#updateUser(String, UserUpdateRequest)
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    /**
     * User's first name.
     * 
     * <p>Maps to FNAME field in COUSR02.bms (POS=(11,18) LENGTH=20 ATTRB=UNPROT).</p>
     * 
     * <p>Validation:</p>
     * <ul>
     *   <li>Required: Must not be blank</li>
     *   <li>Maximum length: 20 characters (matching COBOL PIC X(20))</li>
     * </ul>
     */
    @NotBlank(message = "First name is required")
    @Size(max = 20, message = "First name must not exceed 20 characters")
    @JsonProperty("firstName")
    private String firstName;

    /**
     * User's last name.
     * 
     * <p>Maps to LNAME field in COUSR02.bms (POS=(11,56) LENGTH=20 ATTRB=UNPROT).</p>
     * 
     * <p>Validation:</p>
     * <ul>
     *   <li>Required: Must not be blank</li>
     *   <li>Maximum length: 20 characters (matching COBOL PIC X(20))</li>
     * </ul>
     */
    @NotBlank(message = "Last name is required")
    @Size(max = 20, message = "Last name must not exceed 20 characters")
    @JsonProperty("lastName")
    private String lastName;

    /**
     * New password for the user (optional).
     * 
     * <p>Maps to PASSWD field in COUSR02.bms (POS=(13,16) LENGTH=8 ATTRB=(UNPROT,DRK)).</p>
     * 
     * <p>This field is optional to support profile updates without password changes.
     * If provided, the password will be BCrypt hashed before storage per PCI-DSS
     * compliance requirements. If null or empty, the existing password is preserved.</p>
     * 
     * <p>Validation:</p>
     * <ul>
     *   <li>Optional: Can be null or empty to preserve existing password</li>
     *   <li>If provided: Minimum 8 characters, maximum 20 characters</li>
     *   <li>Must match confirmPassword field if provided (validated at service layer)</li>
     * </ul>
     * 
     * <p><strong>Security Notes:</strong></p>
     * <ul>
     *   <li>Never logged or included in error messages</li>
     *   <li>BCrypt hashed with minimum 10 rounds</li>
     *   <li>Original BMS screen used ATTRB=DRK (dark attribute) to hide password input</li>
     * </ul>
     */
    @Size(min = 8, max = 20, message = "Password must be between 8 and 20 characters if provided")
    @JsonProperty("password")
    private String password;

    /**
     * Password confirmation field (optional).
     * 
     * <p>This field does not map to a BMS screen field; it is added for the modernized
     * REST API to ensure password changes are intentional and free from typos.</p>
     * 
     * <p>Validation:</p>
     * <ul>
     *   <li>Must match password field if password is provided</li>
     *   <li>Validation skipped if password is null or empty</li>
     *   <li>Enforced at service layer via custom validation logic</li>
     * </ul>
     * 
     * <p>Validation logic in UserService.updateUser():</p>
     * <pre>
     * if (password != null && !password.isEmpty()) {
     *     if (!password.equals(confirmPassword)) {
     *         throw new InvalidInputException("Password and confirm password must match");
     *     }
     * }
     * </pre>
     */
    @JsonProperty("confirmPassword")
    private String confirmPassword;

    /**
     * User type code indicating role assignment.
     * 
     * <p>Maps to USRTYPE field in COUSR02.bms (POS=(15,17) LENGTH=1 ATTRB=UNPROT).</p>
     * 
     * <p>Valid values:</p>
     * <ul>
     *   <li>'A' - Admin: Full system access with user management privileges (maps to ROLE_ADMIN)</li>
     *   <li>'R' - Regular: Standard user access without admin privileges (maps to ROLE_USER)</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> The original BMS screen displayed "(A=Admin, U=User)" but the
     * modernized system uses 'R' for Regular to align with Spring Security ROLE_USER
     * naming conventions.</p>
     * 
     * <p>Validation:</p>
     * <ul>
     *   <li>Required: Must not be blank</li>
     *   <li>Pattern: Must be exactly 'A' or 'R' (case-sensitive)</li>
     * </ul>
     * 
     * <p>This field enables user type transitions:</p>
     * <ul>
     *   <li>Promoting Regular users to Admin</li>
     *   <li>Demoting Admin users to Regular</li>
     * </ul>
     */
    @NotBlank(message = "User type is required")
    @Pattern(regexp = "[AR]", message = "User type must be 'A' (Admin) or 'R' (Regular)")
    @JsonProperty("userType")
    private String userType;

    /**
     * Validates that password and confirmPassword match if password is provided.
     * 
     * <p>This method provides a convenient validation check that can be called
     * from service layer logic. It implements the optional password change workflow
     * by only validating password confirmation when a new password is provided.</p>
     * 
     * @return true if passwords match or if password is not provided, false otherwise
     */
    public boolean isPasswordConfirmed() {
        if (password == null || password.isEmpty()) {
            // No password change requested, confirmation not required
            return true;
        }
        // Password provided, must match confirmation
        return password.equals(confirmPassword);
    }

    /**
     * Checks if this update request includes a password change.
     * 
     * @return true if password field is non-null and non-empty
     */
    public boolean hasPasswordChange() {
        return password != null && !password.isEmpty();
    }
}
