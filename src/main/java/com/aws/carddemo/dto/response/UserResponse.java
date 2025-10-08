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

package com.aws.carddemo.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Response DTO for user management operations.
 * 
 * <p>Maps User entity data to JSON API responses for:
 * <ul>
 *   <li>GET /api/v1/admin/users/{id} - Single user retrieval</li>
 *   <li>GET /api/v1/admin/users - User list retrieval</li>
 * </ul>
 * 
 * <p>Migrated from COBOL sources:
 * <ul>
 *   <li>app/bms/COUSR00.bms - User list screen output fields (USRID, FNAME, LNAME, UTYPE)</li>
 *   <li>app/cpy/CSUSR01Y.cpy - User security record copybook (SEC-USER-DATA structure)</li>
 * </ul>
 * 
 * <p><strong>SECURITY NOTE:</strong> Password hash is NEVER included in any API response
 * per PCI-DSS compliance and security best practices. This DTO explicitly excludes
 * the password field from serialization.
 * 
 * <p>Field Mappings from COBOL:
 * <pre>
 * COBOL Field (CSUSR01Y.cpy)  → Java Field
 * ──────────────────────────────────────────
 * SEC-USR-ID (PIC X(08))      → username (String)
 * SEC-USR-FNAME (PIC X(20))   → firstName (String)
 * SEC-USR-LNAME (PIC X(20))   → lastName (String)
 * SEC-USR-TYPE (PIC X(01))    → userType (String)
 * SEC-USR-PWD (PIC X(08))     → [EXCLUDED - NEVER EXPOSED]
 * </pre>
 * 
 * <p>Role Derivation Logic:
 * <ul>
 *   <li>userType='A' (Admin) → roles=['ROLE_ADMIN', 'ROLE_USER']</li>
 *   <li>userType='R' (Regular) → roles=['ROLE_USER']</li>
 * </ul>
 * 
 * @see com.aws.carddemo.model.User
 * @see com.aws.carddemo.mapper.UserMapper
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    /**
     * Internal database user identifier (primary key).
     * Corresponds to the User entity's auto-generated ID.
     */
    @JsonProperty("userId")
    private Long userId;

    /**
     * Unique username for authentication.
     * 
     * <p>Maps from COBOL: SEC-USR-ID (PIC X(08))
     * <p>BMS field: USRID (8 characters)
     * 
     * <p>Constraints: Max length 8 characters, alphanumeric
     */
    @JsonProperty("username")
    private String username;

    /**
     * User's first name.
     * 
     * <p>Maps from COBOL: SEC-USR-FNAME (PIC X(20))
     * <p>BMS field: FNAME (20 characters)
     * 
     * <p>Constraints: Max length 20 characters
     */
    @JsonProperty("firstName")
    private String firstName;

    /**
     * User's last name.
     * 
     * <p>Maps from COBOL: SEC-USR-LNAME (PIC X(20))
     * <p>BMS field: LNAME (20 characters)
     * 
     * <p>Constraints: Max length 20 characters
     */
    @JsonProperty("lastName")
    private String lastName;

    /**
     * User type code from COBOL system.
     * 
     * <p>Maps from COBOL: SEC-USR-TYPE (PIC X(01))
     * <p>BMS field: UTYPE (1 character)
     * 
     * <p>Valid values:
     * <ul>
     *   <li>'A' - Administrator</li>
     *   <li>'R' - Regular User</li>
     * </ul>
     */
    @JsonProperty("userType")
    private String userType;

    /**
     * Human-readable description of the user type.
     * 
     * <p>Derived from userType field:
     * <ul>
     *   <li>userType='A' → "Administrator"</li>
     *   <li>userType='R' → "Regular User"</li>
     * </ul>
     */
    @JsonProperty("userTypeDescription")
    private String userTypeDescription;

    /**
     * List of Spring Security role names assigned to the user.
     * 
     * <p>Derived from userType field per role derivation logic:
     * <ul>
     *   <li>userType='A' → ["ROLE_ADMIN", "ROLE_USER"]</li>
     *   <li>userType='R' → ["ROLE_USER"]</li>
     * </ul>
     * 
     * <p>These roles correspond to Spring Security GrantedAuthority values
     * and can be used for client-side role-based UI rendering and access control.
     */
    @JsonProperty("roles")
    private List<String> roles;

    /**
     * Timestamp of the user's most recent successful authentication.
     * 
     * <p>Will be null if the user has never successfully logged in.
     * Due to @JsonInclude(NON_NULL) on the class, this field will be
     * omitted from JSON output when null, reducing response payload size.
     * 
     * <p>Format: ISO 8601 timestamp (e.g., "2024-10-08T14:30:00")
     */
    @JsonProperty("lastLogin")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastLogin;

    /**
     * Account lockout status indicating if the user is currently locked
     * out due to excessive failed login attempts or administrative action.
     * 
     * <p>Values:
     * <ul>
     *   <li>true - Account is locked, user cannot authenticate</li>
     *   <li>false - Account is active, user can authenticate</li>
     * </ul>
     * 
     * <p>Admins can use this field to monitor security events and
     * identify accounts requiring attention.
     */
    @JsonProperty("accountLocked")
    private Boolean accountLocked;

    /**
     * Current count of consecutive failed login attempts.
     * 
     * <p>This counter increments on each failed authentication and
     * resets to 0 upon successful login. Used for security monitoring
     * and automatic account lockout policies.
     * 
     * <p>Typical lockout threshold: 5 failed attempts
     */
    @JsonProperty("failedLoginAttempts")
    private Integer failedLoginAttempts;

    /**
     * Timestamp when the user record was created.
     * 
     * <p>Format: ISO 8601 timestamp (e.g., "2024-10-08T14:30:00")
     * 
     * <p>Audit field for tracking user account creation.
     */
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Timestamp when the user record was last modified.
     * 
     * <p>Format: ISO 8601 timestamp (e.g., "2024-10-08T14:30:00")
     * 
     * <p>Audit field for tracking user account modifications
     * (name changes, type changes, password resets, etc.)
     */
    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * SECURITY NOTE: Password hash is intentionally NOT included in this DTO.
     * 
     * <p>The password field from the User entity (SEC-USR-PWD in COBOL) must
     * NEVER be exposed in API responses per:
     * <ul>
     *   <li>PCI-DSS compliance requirements</li>
     *   <li>Security best practices</li>
     *   <li>Data protection regulations</li>
     * </ul>
     * 
     * <p>The UserMapper must be configured to explicitly exclude the password
     * field when mapping from User entity to UserResponse DTO.
     */
    // NO PASSWORD FIELD - NEVER EXPOSE CREDENTIALS IN API RESPONSES

}
