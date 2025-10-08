/*
 * LoginRequest.java
 *
 * Login request DTO for user authentication via REST API.
 * Migrated from: app/bms/COSGN00.bms
 *
 * This class replaces the BMS COSGN00 3270 terminal login screen with a modern
 * REST API request structure. The COBOL pattern of EXEC CICS RECEIVE MAP(COSGN0A)
 * MAPSET(COSGN00) is replaced by Spring MVC @RequestBody parameter binding that
 * deserializes JSON payloads into this DTO object.
 *
 * Original BMS Fields Mapping:
 * - USERID  (DFHMDF LENGTH=8, UNPROT, POS=(19,43))  -> username field
 * - PASSWD  (DFHMDF LENGTH=8, UNPROT, DRK, POS=(20,43)) -> password field
 *
 * The DRK (dark) attribute on PASSWD in BMS provided password masking on the
 * 3270 terminal. In the REST API, this is handled by the client application
 * and secure HTTPS transport.
 *
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
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for user authentication login requests.
 * 
 * <p>This DTO captures user credentials submitted to the POST /api/v1/auth/login
 * endpoint, replacing the legacy BMS COSGN00 3270 terminal login screen with
 * modern REST API authentication.</p>
 * 
 * <p><strong>Legacy Mapping:</strong></p>
 * <ul>
 *   <li>USERID field (8 characters, unprotected input) → username</li>
 *   <li>PASSWD field (8 characters, unprotected with dark attribute) → password</li>
 * </ul>
 * 
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li>Both fields are required (@NotBlank)</li>
 *   <li>Maximum length of 8 characters (@Size(max=8)), preserving COBOL PIC X(08) constraints</li>
 *   <li>Bean Validation ensures input validation equivalent to COBOL field length checks</li>
 * </ul>
 * 
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li>Password field is excluded from toString() via Lombok to prevent accidental logging</li>
 *   <li>Sensitive data masking is enforced in logging configuration (logback-spring.xml)</li>
 *   <li>HTTPS/TLS encryption required for transport security (no plain HTTP)</li>
 *   <li>Passwords are hashed with BCrypt before storage (never stored plain-text)</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * {@code
 * // POST /api/v1/auth/login
 * // Request Body:
 * {
 *   "username": "USER0001",
 *   "password": "secret123"
 * }
 * 
 * // Programmatic construction:
 * LoginRequest request = LoginRequest.builder()
 *     .username("USER0001")
 *     .password("secret123")
 *     .build();
 * 
 * // Or using constructor:
 * LoginRequest request = new LoginRequest("USER0001", "secret123");
 * }
 * </pre>
 * 
 * @see com.aws.carddemo.controller.AuthController
 * @see com.aws.carddemo.service.AuthenticationService
 * @see com.aws.carddemo.dto.response.LoginResponse
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * User identifier for authentication.
     * 
     * <p>Maps to the USERID field from BMS COSGN00 screen (DFHMDF LENGTH=8, POS=(19,43)).</p>
     * 
     * <p>This field corresponds to the COBOL copybook field defined as PIC X(08) in
     * the user security record (CSUSR01Y.cpy). The 8-character limit is preserved
     * from the legacy mainframe system for compatibility with existing user accounts.</p>
     * 
     * <p><strong>Validation:</strong></p>
     * <ul>
     *   <li>@NotBlank: Field cannot be null, empty, or whitespace-only</li>
     *   <li>@Size(max=8): Maximum length of 8 characters (COBOL PIC X(08) constraint)</li>
     * </ul>
     * 
     * <p><strong>Example Values:</strong></p>
     * <ul>
     *   <li>"USER0001" - Standard user account</li>
     *   <li>"ADMIN001" - Administrative account</li>
     *   <li>"BATCH001" - Batch processing service account</li>
     * </ul>
     */
    @JsonProperty("username")
    @NotBlank(message = "Username is required and cannot be blank")
    @Size(max = 8, message = "Username must not exceed 8 characters")
    private String username;

    /**
     * User password for authentication.
     * 
     * <p>Maps to the PASSWD field from BMS COSGN00 screen (DFHMDF LENGTH=8, ATTRB=(DRK,UNPROT), POS=(20,43)).</p>
     * 
     * <p>The original BMS field used the DRK (dark) attribute to mask password input on the
     * 3270 terminal display. In the REST API architecture, password masking is handled by
     * the client application (web browser, mobile app), and transport security is provided
     * by HTTPS/TLS encryption.</p>
     * 
     * <p>This field corresponds to the COBOL copybook field defined as PIC X(08) in the
     * user security record (CSUSR01Y.cpy). Passwords are stored as BCrypt hashes in the
     * database, never in plain text.</p>
     * 
     * <p><strong>Validation:</strong></p>
     * <ul>
     *   <li>@NotBlank: Field cannot be null, empty, or whitespace-only</li>
     *   <li>@Size(max=8): Maximum length of 8 characters (COBOL PIC X(08) constraint)</li>
     * </ul>
     * 
     * <p><strong>Security Notes:</strong></p>
     * <ul>
     *   <li>This field is excluded from Lombok's toString() method to prevent password logging</li>
     *   <li>Passwords are never logged in application logs (masked by logback configuration)</li>
     *   <li>BCrypt hashing with 10+ rounds applied before database storage</li>
     *   <li>Password complexity rules may be enforced by UserService validation</li>
     *   <li>Failed login attempts are rate-limited to prevent brute-force attacks</li>
     * </ul>
     * 
     * <p><strong>PCI-DSS Compliance:</strong></p>
     * <ul>
     *   <li>Requirement 8.2.3: Passwords must be encrypted during transmission (HTTPS enforced)</li>
     *   <li>Requirement 8.2.5: Passwords must not be displayed in clear text (masked in logs)</li>
     *   <li>Requirement 10.2.4: Invalid login attempts must be logged (audit trail in AuthenticationService)</li>
     * </ul>
     */
    @JsonProperty("password")
    @NotBlank(message = "Password is required and cannot be blank")
    @Size(max = 8, message = "Password must not exceed 8 characters")
    private String password;
}
