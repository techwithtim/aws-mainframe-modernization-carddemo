/*
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * User Creation Request DTO
 * 
 * <p>Data Transfer Object for creating new users in the CardDemo system.
 * This DTO captures user registration data submitted to POST /api/v1/admin/users endpoint,
 * replacing the legacy CICS BMS screen COUSR01 (Add User) input fields.</p>
 * 
 * <p><strong>Migration Context:</strong></p>
 * <ul>
 *   <li>Migrated from: app/bms/COUSR01.bms</li>
 *   <li>Legacy Program: COUSR01C.cbl (User Add Transaction)</li>
 *   <li>CICS Transaction: Replaced EXEC CICS RECEIVE MAP(COUSR1A) MAPSET(COUSR01)</li>
 *   <li>REST Endpoint: POST /api/v1/admin/users</li>
 *   <li>Authorization: Requires @PreAuthorize("hasRole('ADMIN')")</li>
 * </ul>
 * 
 * <p><strong>Field Mappings from BMS COUSR01:</strong></p>
 * <ul>
 *   <li>USERID (POS=(11,15), LENGTH=8) → userId (String, alphanumeric, 3-8 chars)</li>
 *   <li>PASSWD (POS=(11,55), LENGTH=8, ATTRB=DRK) → password (String, 8-20 chars, BCrypt hashed)</li>
 *   <li>FNAME (POS=(8,18), LENGTH=20) → firstName (String, max 20 chars)</li>
 *   <li>LNAME (POS=(8,56), LENGTH=20) → lastName (String, max 20 chars)</li>
 *   <li>USRTYPE (POS=(14,17), LENGTH=1) → userType (String, 'A' or 'R')</li>
 *   <li>confirmPassword → New field for REST API (not in BMS, password confirmation)</li>
 * </ul>
 * 
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li>Password will be BCrypt hashed with 10 rounds (PCI-DSS compliant)</li>
 *   <li>Original COBOL stored plain-text passwords (PIC X(08)) - modernized with BCrypt</li>
 *   <li>Passwords are never logged or included in error messages</li>
 *   <li>Password matching validation (password == confirmPassword) performed in service layer</li>
 *   <li>User type 'A' maps to Spring Security ROLE_ADMIN, 'R' maps to ROLE_USER</li>
 * </ul>
 * 
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li>userId: Alphanumeric uppercase only, 3-8 characters (COBOL PIC X(08) pattern)</li>
 *   <li>password: 8-20 characters, must meet security requirements</li>
 *   <li>confirmPassword: Must match password field</li>
 *   <li>firstName: Non-blank, max 20 characters</li>
 *   <li>lastName: Non-blank, max 20 characters</li>
 *   <li>userType: Single character, 'A' (Admin) or 'R' (Regular user)</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * UserCreateRequest request = UserCreateRequest.builder()
 *     .userId("ADMIN001")
 *     .password("SecureP@ss123")
 *     .confirmPassword("SecureP@ss123")
 *     .firstName("John")
 *     .lastName("Doe")
 *     .userType("A")
 *     .build();
 * </pre>
 * 
 * @see com.aws.carddemo.controller.AdminController#createUser(UserCreateRequest)
 * @see com.aws.carddemo.service.UserService
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    /**
     * User ID (unique identifier for system login)
     * 
     * <p>Maps to USERID field from COUSR01.bms (POS=(11,15), LENGTH=8)</p>
     * <p>BMS Hint: "(8 Char)" - allows up to 8 characters</p>
     * <p>Validation enforces alphanumeric uppercase only, matching COBOL PIC X(08) pattern
     * with implicit INSPECT CONVERTING validation for uppercase letters and digits.</p>
     * 
     * <p>Constraints:</p>
     * <ul>
     *   <li>Required: Cannot be blank or null</li>
     *   <li>Length: Minimum 3 characters, maximum 8 characters</li>
     *   <li>Pattern: [A-Z0-9]+ (uppercase letters and digits only)</li>
     *   <li>Must be unique across all users (enforced at database level)</li>
     * </ul>
     * 
     * <p>Examples: "ADMIN001", "USER123", "SUPPORT1"</p>
     */
    @NotBlank(message = "User ID is required")
    @Size(min = 3, max = 8, message = "User ID must be between 3 and 8 characters")
    @Pattern(regexp = "[A-Z0-9]+", message = "User ID must contain only uppercase letters and digits")
    @JsonProperty("userId")
    private String userId;

    /**
     * User Password (for authentication)
     * 
     * <p>Maps to PASSWD field from COUSR01.bms (POS=(11,55), LENGTH=8, ATTRB=DRK)</p>
     * <p>BMS Hint: "(8 Char)" with DRK (dark) attribute for hidden input</p>
     * <p>Original COBOL stored plain-text passwords in CSUSR01Y.cpy (PIC X(08)).
     * Modernized implementation uses BCrypt hashing with 10 rounds for PCI-DSS compliance.</p>
     * 
     * <p>Constraints:</p>
     * <ul>
     *   <li>Required: Cannot be blank or null</li>
     *   <li>Length: Minimum 8 characters, maximum 20 characters (expanded from COBOL limit)</li>
     *   <li>Security: Will be BCrypt hashed before storage (actual stored hash is 60 chars)</li>
     *   <li>Must match confirmPassword field (validated in service layer)</li>
     * </ul>
     * 
     * <p>Security Notes:</p>
     * <ul>
     *   <li>Never logged or included in toString() output</li>
     *   <li>Never returned in API responses</li>
     *   <li>Should contain mix of uppercase, lowercase, digits, and special characters</li>
     * </ul>
     * 
     * <p>Examples: "SecureP@ss123", "MyP@ssw0rd!", "Admin#2024"</p>
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 20, message = "Password must be between 8 and 20 characters")
    @JsonProperty("password")
    private String password;

    /**
     * Password Confirmation (for validation)
     * 
     * <p>This field is NOT present in the original BMS COUSR01 screen but is added
     * as a best practice for REST API user experience to prevent password entry errors.</p>
     * 
     * <p>Constraints:</p>
     * <ul>
     *   <li>Required: Cannot be blank or null</li>
     *   <li>Must match password field exactly (validated in service layer)</li>
     *   <li>Same length and security requirements as password field</li>
     * </ul>
     * 
     * <p>Validation Logic:</p>
     * <pre>
     * if (!password.equals(confirmPassword)) {
     *     throw new InvalidInputException("Password and confirmation do not match");
     * }
     * </pre>
     * 
     * <p>Note: A custom @PasswordsMatch class-level annotation could be implemented
     * for declarative validation, but is not currently in the dependency list.</p>
     */
    @NotBlank(message = "Password confirmation is required")
    @JsonProperty("confirmPassword")
    private String confirmPassword;

    /**
     * User First Name
     * 
     * <p>Maps to FNAME field from COUSR01.bms (POS=(8,18), LENGTH=20, ATTRB=UNPROT)</p>
     * <p>Original COBOL field: PIC X(20) in user record structure</p>
     * 
     * <p>Constraints:</p>
     * <ul>
     *   <li>Required: Cannot be blank or null</li>
     *   <li>Length: Maximum 20 characters (COBOL LENGTH=20 preserved)</li>
     *   <li>Free-form text: Accepts any characters (letters, spaces, hyphens, apostrophes)</li>
     * </ul>
     * 
     * <p>Examples: "John", "Mary-Ann", "José", "O'Brien"</p>
     */
    @NotBlank(message = "First name is required")
    @Size(max = 20, message = "First name must not exceed 20 characters")
    @JsonProperty("firstName")
    private String firstName;

    /**
     * User Last Name
     * 
     * <p>Maps to LNAME field from COUSR01.bms (POS=(8,56), LENGTH=20, ATTRB=UNPROT)</p>
     * <p>Original COBOL field: PIC X(20) in user record structure</p>
     * 
     * <p>Constraints:</p>
     * <ul>
     *   <li>Required: Cannot be blank or null</li>
     *   <li>Length: Maximum 20 characters (COBOL LENGTH=20 preserved)</li>
     *   <li>Free-form text: Accepts any characters (letters, spaces, hyphens, apostrophes)</li>
     * </ul>
     * 
     * <p>Examples: "Doe", "Smith-Jones", "García", "O'Neill"</p>
     */
    @NotBlank(message = "Last name is required")
    @Size(max = 20, message = "Last name must not exceed 20 characters")
    @JsonProperty("lastName")
    private String lastName;

    /**
     * User Type Code (determines role and permissions)
     * 
     * <p>Maps to USRTYPE field from COUSR01.bms (POS=(14,17), LENGTH=1, ATTRB=UNPROT)</p>
     * <p>BMS Hint: "(A=Admin, U=User)" - original screen showed 'U' for user</p>
     * <p>Modernized to use 'R' (Regular) instead of 'U' for alignment with Spring Security
     * ROLE_USER naming convention and to avoid confusion with 'U' vs 'User'.</p>
     * 
     * <p>Valid Values:</p>
     * <ul>
     *   <li>'A' = Admin user (maps to Spring Security ROLE_ADMIN)</li>
     *   <li>'R' = Regular user (maps to Spring Security ROLE_USER)</li>
     * </ul>
     * 
     * <p>Constraints:</p>
     * <ul>
     *   <li>Required: Cannot be blank or null</li>
     *   <li>Pattern: Must be exactly 'A' or 'R' (single uppercase character)</li>
     *   <li>Case-sensitive: Lowercase 'a' or 'r' will be rejected</li>
     * </ul>
     * 
     * <p>Role Mapping:</p>
     * <pre>
     * 'A' → ROLE_ADMIN (can manage users, access admin endpoints)
     * 'R' → ROLE_USER (standard user, can manage own account and cards)
     * </pre>
     * 
     * <p>Security Note: Admin role grants access to user management endpoints
     * (POST /api/v1/admin/users, PUT /api/v1/admin/users/{id}, DELETE /api/v1/admin/users/{id})</p>
     */
    @NotBlank(message = "User type is required")
    @Pattern(regexp = "[AR]", message = "User type must be 'A' (Admin) or 'R' (Regular user)")
    @JsonProperty("userType")
    private String userType;

    /**
     * Custom toString() implementation to exclude sensitive fields
     * 
     * <p>Overrides Lombok-generated toString() to prevent password leakage in logs.
     * This is critical for PCI-DSS compliance - passwords must never appear in log files.</p>
     * 
     * @return String representation with masked password fields
     */
    @Override
    public String toString() {
        return "UserCreateRequest{" +
                "userId='" + userId + '\'' +
                ", password='***MASKED***'" +
                ", confirmPassword='***MASKED***'" +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", userType='" + userType + '\'' +
                '}';
    }
}
