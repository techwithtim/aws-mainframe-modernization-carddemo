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

package com.aws.carddemo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * JPA Entity representing application user security credentials.
 * 
 * <p>Migrated from: app/cpy/CSUSR01Y.cpy (COBOL SEC-USER-DATA 80-byte record)</p>
 * 
 * <p>This entity maps the legacy COBOL user security structure to a modern JPA entity with
 * enhanced security features including BCrypt password hashing (replacing 8-character plain-text),
 * account lockout protection, failed login attempt tracking, and audit trail with last login timestamp.</p>
 * 
 * <p>Original COBOL Structure (80 bytes):</p>
 * <pre>
 * 01 SEC-USER-DATA.
 *   05 SEC-USR-ID       PIC X(08).  → username (extended to 50 chars)
 *   05 SEC-USR-FNAME    PIC X(20).  → firstName (extended to 50 chars)
 *   05 SEC-USR-LNAME    PIC X(20).  → lastName (extended to 50 chars)
 *   05 SEC-USR-PWD      PIC X(08).  → passwordHash (BCrypt 255 chars)
 *   05 SEC-USR-TYPE     PIC X(01).  → userType (A=Admin, R=Regular User)
 *   05 SEC-USR-FILLER   PIC X(23).  → (not migrated)
 * </pre>
 * 
 * <p>Security Enhancements (Section 0.8.1):</p>
 * <ul>
 *   <li>BCrypt password hashing with 10+ rounds (Section 6.2.2.1)</li>
 *   <li>Account lockout after 5 failed login attempts</li>
 *   <li>Audit trail with last login timestamp</li>
 *   <li>Spring Security integration via getAuthorities() method</li>
 * </ul>
 * 
 * <p>Role Mapping (Section 0.1.1):</p>
 * <ul>
 *   <li>userType='A' → ROLE_ADMIN, ROLE_USER (full administrative access)</li>
 *   <li>userType='R' → ROLE_USER (regular user access)</li>
 * </ul>
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @see org.springframework.security.core.userdetails.UserDetails
 */
@Entity
@Table(
    name = "app_user",
    indexes = {
        @Index(name = "idx_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_type", columnList = "user_type")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key - auto-generated user identifier.
     * 
     * <p>Not present in original COBOL structure. Added for modern JPA entity requirements
     * and to support surrogate key pattern for better database performance and referential integrity.</p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    /**
     * Unique username for authentication.
     * 
     * <p>Migrated from: SEC-USR-ID PIC X(08)</p>
     * <p>Extended from 8 to 50 characters to support modern authentication standards
     * including email-based usernames and longer identifiers.</p>
     * <p>Unique constraint enforced at database level via idx_user_username index.</p>
     */
    @NotNull(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    /**
     * BCrypt hashed password.
     * 
     * <p>Migrated from: SEC-USR-PWD PIC X(08) (plain-text)</p>
     * <p>Security Enhancement: Replaced insecure 8-character plain-text password with
     * BCrypt hash (255 characters) using 10+ rounds per PCI-DSS compliance requirements.</p>
     * <p>This field is excluded from:</p>
     * <ul>
     *   <li>JSON serialization (@JsonIgnore) to prevent exposure in REST API responses</li>
     *   <li>toString() output (@ToString.Exclude) to prevent logging sensitive data</li>
     * </ul>
     * 
     * @see org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
     */
    @NotNull(message = "Password hash is required")
    @Size(max = 255, message = "Password hash must not exceed 255 characters")
    @Column(name = "password_hash", length = 255, nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private String passwordHash;

    /**
     * User's first name.
     * 
     * <p>Migrated from: SEC-USR-FNAME PIC X(20)</p>
     * <p>Extended from 20 to 50 characters to accommodate longer international names.</p>
     */
    @Size(max = 50, message = "First name must not exceed 50 characters")
    @Column(name = "first_name", length = 50)
    private String firstName;

    /**
     * User's last name.
     * 
     * <p>Migrated from: SEC-USR-LNAME PIC X(20)</p>
     * <p>Extended from 20 to 50 characters to accommodate longer international names.</p>
     */
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    @Column(name = "last_name", length = 50)
    private String lastName;

    /**
     * User type indicator for role-based access control.
     * 
     * <p>Migrated from: SEC-USR-TYPE PIC X(01)</p>
     * <p>Valid values:</p>
     * <ul>
     *   <li>'A' - Administrator (maps to ROLE_ADMIN + ROLE_USER)</li>
     *   <li>'R' - Regular User (maps to ROLE_USER)</li>
     * </ul>
     * <p>Database constraint: chk_user_type CHECK (user_type IN ('A', 'R'))</p>
     * <p>Indexed via idx_user_type for efficient role-based queries.</p>
     */
    @NotNull(message = "User type is required")
    @Pattern(regexp = "[AR]", message = "User type must be 'A' (Admin) or 'R' (Regular User)")
    @Column(name = "user_type", length = 1, nullable = false)
    private String userType;

    /**
     * Timestamp of last successful login.
     * 
     * <p>Security Enhancement: Added for audit trail compliance and security monitoring.
     * Updated by AuthenticationService upon successful authentication.</p>
     * <p>Used for:</p>
     * <ul>
     *   <li>Security audit reporting</li>
     *   <li>Inactive account detection</li>
     *   <li>Compliance requirements tracking</li>
     * </ul>
     */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * Account lockout status flag.
     * 
     * <p>Security Enhancement: Prevents brute-force password attacks by locking account
     * after excessive failed login attempts (typically 5 attempts per Section 0.8.1).</p>
     * <p>When true, authentication attempts are rejected regardless of password correctness.
     * Requires administrator intervention or automated unlock after timeout period.</p>
     */
    @NotNull(message = "Account locked status is required")
    @Column(name = "account_locked", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean accountLocked = false;

    /**
     * Counter for failed login attempts.
     * 
     * <p>Security Enhancement: Tracks consecutive failed authentication attempts.
     * Reset to 0 upon successful login. When threshold (5) is reached, accountLocked
     * is set to true.</p>
     * <p>Database constraint: chk_login_attempts CHECK (failed_login_attempts >= 0)</p>
     */
    @NotNull(message = "Failed login attempts count is required")
    @Min(value = 0, message = "Failed login attempts cannot be negative")
    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    /**
     * Version field for optimistic locking.
     * 
     * <p>Prevents lost updates in concurrent modification scenarios. Automatically
     * incremented by JPA on each update operation. If version mismatch occurs during
     * update, OptimisticLockException is thrown.</p>
     */
    @Version
    @Column(name = "version")
    private Integer version;

    /**
     * Returns Spring Security authorities (roles) based on user type.
     * 
     * <p>This method implements Spring Security role-based access control by mapping
     * the legacy COBOL user type field to Spring Security GrantedAuthority objects.</p>
     * 
     * <p>Role Mapping Logic:</p>
     * <ul>
     *   <li>userType='A' → [ROLE_ADMIN, ROLE_USER] (administrators have both roles)</li>
     *   <li>userType='R' → [ROLE_USER] (regular users have standard access)</li>
     *   <li>Any other value → [ROLE_USER] (default to minimal privileges)</li>
     * </ul>
     * 
     * <p>This method is marked @Transient to exclude it from database persistence.
     * It is computed on-the-fly when needed by Spring Security's authentication and
     * authorization infrastructure.</p>
     * 
     * <p>Usage in Spring Security:</p>
     * <pre>
     * &#64;PreAuthorize("hasRole('ADMIN')")
     * public void adminOnlyOperation() { }
     * 
     * &#64;PreAuthorize("hasRole('USER')")
     * public void userOperation() { }
     * </pre>
     * 
     * @return Collection of granted authorities representing user's roles
     * @see org.springframework.security.core.userdetails.UserDetailsService
     * @see org.springframework.security.access.prepost.PreAuthorize
     */
    @Transient
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if ("A".equals(this.userType)) {
            // Administrators have both ADMIN and USER roles
            return List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER")
            );
        } else {
            // Regular users and default case have only USER role
            return List.of(
                new SimpleGrantedAuthority("ROLE_USER")
            );
        }
    }
}
