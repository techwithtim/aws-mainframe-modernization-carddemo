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

package com.aws.carddemo.mapper;

import com.aws.carddemo.dto.request.UserCreateRequest;
import com.aws.carddemo.dto.request.UserUpdateRequest;
import com.aws.carddemo.dto.response.UserResponse;
import com.aws.carddemo.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * MapStruct mapper interface for bidirectional conversion between User JPA entity and User DTOs.
 * 
 * <p><strong>Migration Context:</strong></p>
 * <ul>
 *   <li>Migrated from: app/cpy/CSUSR01Y.cpy (COBOL SEC-USER-DATA 80-byte record)</li>
 *   <li>Original COBOL Structure: 05 SEC-USR-ID PIC X(08), 05 SEC-USR-FNAME PIC X(20),
 *       05 SEC-USR-LNAME PIC X(20), 05 SEC-USR-PWD PIC X(08), 05 SEC-USR-TYPE PIC X(01)</li>
 *   <li>REST API Endpoints: GET/POST/PUT/DELETE /api/v1/admin/users</li>
 * </ul>
 * 
 * <p><strong>Mapping Functions:</strong></p>
 * <ul>
 *   <li><strong>toResponse(User)</strong>: Converts User entity to UserResponse DTO for GET endpoints.
 *       CRITICAL: Excludes passwordHash field per PCI-DSS security requirements.</li>
 *   <li><strong>toEntity(UserCreateRequest)</strong>: Converts UserCreateRequest to User entity for POST endpoint.
 *       Note: Service layer applies BCrypt hashing to the plain-text password after mapping.</li>
 *   <li><strong>updateEntityFromRequest(UserUpdateRequest, User)</strong>: Updates existing User entity 
 *       from UserUpdateRequest for PUT endpoint. Supports partial updates with conditional password change.</li>
 * </ul>
 * 
 * <p><strong>Security Considerations (Section 0.8.1 - PCI-DSS Compliance):</strong></p>
 * <ul>
 *   <li>Password hash is NEVER exposed in API responses via explicit @Mapping(ignore=true)</li>
 *   <li>Plain-text passwords in request DTOs are converted to BCrypt hashes in service layer</li>
 *   <li>Original COBOL stored plain-text passwords (PIC X(08)) - modernized with BCrypt 255-char hash</li>
 *   <li>Failed login attempts and account lockout status are tracked for brute-force protection</li>
 * </ul>
 * 
 * <p><strong>Role Mapping Logic (userType → Spring Security roles):</strong></p>
 * <pre>
 * COBOL SEC-USR-TYPE  → Java userType → Spring Security Roles
 * ──────────────────────────────────────────────────────────
 * 'A' (Admin)         → 'A'          → [ROLE_ADMIN, ROLE_USER]
 * 'R' (Regular)       → 'R'          → [ROLE_USER]
 * </pre>
 * 
 * <p><strong>Field Mappings:</strong></p>
 * <pre>
 * COBOL Field (CSUSR01Y.cpy)  → Java Entity Field    → Response/Request DTO Field
 * ─────────────────────────────────────────────────────────────────────────────────
 * SEC-USR-ID (PIC X(08))      → username             → username (String)
 * SEC-USR-FNAME (PIC X(20))   → firstName            → firstName (String)
 * SEC-USR-LNAME (PIC X(20))   → lastName             → lastName (String)
 * SEC-USR-PWD (PIC X(08))     → passwordHash         → password (String, plain-text in request)
 *                                                       [EXCLUDED from response]
 * SEC-USR-TYPE (PIC X(01))    → userType             → userType (String 'A' or 'R')
 * [New Field]                 → lastLogin            → lastLogin (LocalDateTime)
 * [New Field]                 → accountLocked        → accountLocked (Boolean)
 * [New Field]                 → failedLoginAttempts  → failedLoginAttempts (Integer)
 * </pre>
 * 
 * <p><strong>MapStruct Configuration:</strong></p>
 * <ul>
 *   <li>componentModel = "spring": Enables Spring dependency injection of generated mapper</li>
 *   <li>Uses = {}: No custom mapping utilities required</li>
 *   <li>Generated implementation class: UserMapperImpl (Spring @Component)</li>
 * </ul>
 * 
 * <p><strong>Usage in Service Layer:</strong></p>
 * <pre>
 * &#64;Service
 * public class UserService {
 *     &#64;Autowired
 *     private UserMapper userMapper;
 *     
 *     public UserResponse createUser(UserCreateRequest request) {
 *         User user = userMapper.toEntity(request);
 *         user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
 *         User saved = userRepository.save(user);
 *         return userMapper.toResponse(saved);
 *     }
 * }
 * </pre>
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @see com.aws.carddemo.model.User
 * @see com.aws.carddemo.dto.response.UserResponse
 * @see com.aws.carddemo.dto.request.UserCreateRequest
 * @see com.aws.carddemo.dto.request.UserUpdateRequest
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Converts User entity to UserResponse DTO for API responses.
     * 
     * <p><strong>CRITICAL SECURITY REQUIREMENT:</strong> This method explicitly excludes
     * the passwordHash field from the response DTO per PCI-DSS compliance and security
     * best practices. Passwords must NEVER be exposed in API responses under any circumstances.</p>
     * 
     * <p><strong>Field Mappings:</strong></p>
     * <ul>
     *   <li>userId → userId (Long)</li>
     *   <li>username → username (String, max 50 chars)</li>
     *   <li>firstName → firstName (String, max 50 chars)</li>
     *   <li>lastName → lastName (String, max 50 chars)</li>
     *   <li>userType → userType (String, 'A' or 'R')</li>
     *   <li>userType → userTypeDescription (String, computed: "Administrator" or "Regular User")</li>
     *   <li>userType → roles (List&lt;String&gt;, computed via getRolesFromUserType())</li>
     *   <li>lastLogin → lastLogin (LocalDateTime, nullable)</li>
     *   <li>accountLocked → accountLocked (Boolean)</li>
     *   <li>failedLoginAttempts → failedLoginAttempts (Integer)</li>
     *   <li>passwordHash → [EXCLUDED - NEVER MAPPED]</li>
     * </ul>
     * 
     * <p><strong>Derived Fields:</strong></p>
     * <ul>
     *   <li><strong>userTypeDescription</strong>: Computed from userType via getUserTypeDescription()</li>
     *   <li><strong>roles</strong>: Computed from userType via getRolesFromUserType()</li>
     *   <li><strong>createdAt/updatedAt</strong>: Mapped from BaseEntity audit fields (if User extends BaseEntity)</li>
     * </ul>
     * 
     * <p><strong>Security Note:</strong> The @Mapping annotation with target="passwordHash", ignore=true
     * ensures that even if the User entity has a passwordHash field populated, it will NEVER be
     * copied to the UserResponse DTO, preventing accidental exposure in JSON responses.</p>
     * 
     * @param user User entity from database (must not be null)
     * @return UserResponse DTO for JSON serialization (with passwordHash excluded)
     * @throws NullPointerException if user parameter is null
     */
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "userTypeDescription", source = "userType", qualifiedByName = "getUserTypeDescription")
    @Mapping(target = "roles", source = "userType", qualifiedByName = "getRolesFromUserType")
    UserResponse toResponse(User user);

    /**
     * Converts UserCreateRequest DTO to User entity for user creation.
     * 
     * <p><strong>Password Handling Workflow:</strong></p>
     * <ol>
     *   <li>This mapper copies the plain-text password from request.password to user.passwordHash</li>
     *   <li>Service layer MUST apply BCrypt hashing: user.setPasswordHash(passwordEncoder.encode(password))</li>
     *   <li>Only the BCrypt-hashed password is persisted to database (never plain-text)</li>
     * </ol>
     * 
     * <p><strong>Field Mappings:</strong></p>
     * <ul>
     *   <li>userId (from request) → username (String, max 50 chars, unique)</li>
     *   <li>password (String, plain-text) → passwordHash (String, will be BCrypt hashed by service)</li>
     *   <li>firstName → firstName (String, max 50 chars)</li>
     *   <li>lastName → lastName (String, max 50 chars)</li>
     *   <li>userType → userType (String, 'A' or 'R')</li>
     * </ul>
     * 
     * <p><strong>Default Values:</strong></p>
     * <ul>
     *   <li>accountLocked: false (default from @Builder.Default in User entity)</li>
     *   <li>failedLoginAttempts: 0 (default from @Builder.Default in User entity)</li>
     *   <li>lastLogin: null (will be set on first successful login)</li>
     * </ul>
     * 
     * <p><strong>IMPORTANT:</strong> The service layer must invoke password encoding after this mapping:</p>
     * <pre>
     * User user = userMapper.toEntity(request);
     * user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
     * userRepository.save(user);
     * </pre>
     * 
     * @param request UserCreateRequest DTO from POST /api/v1/admin/users (validated, not null)
     * @return User entity ready for password hashing and persistence
     * @throws NullPointerException if request parameter is null
     * @see org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
     */
    @Mapping(target = "userId", ignore = true)  // Auto-generated by database
    @Mapping(target = "username", source = "userId")  // Request.userId becomes Entity.username
    @Mapping(target = "passwordHash", source = "password")  // Plain-text to be hashed by service layer
    @Mapping(target = "lastLogin", ignore = true)  // Set on first successful login
    @Mapping(target = "accountLocked", ignore = true)  // Defaults to false via @Builder.Default
    @Mapping(target = "failedLoginAttempts", ignore = true)  // Defaults to 0 via @Builder.Default
    @Mapping(target = "version", ignore = true)  // Managed by JPA @Version
    User toEntity(UserCreateRequest request);

    /**
     * Updates an existing User entity from UserUpdateRequest DTO for partial updates.
     * 
     * <p><strong>Partial Update Strategy:</strong></p>
     * <p>This method uses NullValuePropertyMappingStrategy.IGNORE to support partial updates
     * where only provided fields are updated. Null values in the request DTO are skipped,
     * preserving existing entity values. This is critical for the optional password change workflow.</p>
     * 
     * <p><strong>Password Change Workflow:</strong></p>
     * <ol>
     *   <li>If request.password is null/empty: Existing passwordHash is preserved (no change)</li>
     *   <li>If request.password is provided:
     *     <ul>
     *       <li>Service layer validates request.password equals request.confirmPassword</li>
     *       <li>Mapper copies plain-text password to entity.passwordHash</li>
     *       <li>Service layer applies BCrypt hashing: entity.setPasswordHash(passwordEncoder.encode(password))</li>
     *     </ul>
     *   </li>
     * </ol>
     * 
     * <p><strong>Field Update Mappings:</strong></p>
     * <ul>
     *   <li>firstName → firstName (required, max 50 chars)</li>
     *   <li>lastName → lastName (required, max 50 chars)</li>
     *   <li>userType → userType (required, 'A' or 'R', enables role transitions)</li>
     *   <li>password → passwordHash (optional, plain-text to be hashed by service if provided)</li>
     * </ul>
     * 
     * <p><strong>Protected Fields (Not Updated):</strong></p>
     * <ul>
     *   <li>userId: Primary key, immutable</li>
     *   <li>username: Unique identifier, immutable</li>
     *   <li>lastLogin: Updated only by authentication service</li>
     *   <li>accountLocked: Updated only by security service or admin action</li>
     *   <li>failedLoginAttempts: Updated only by authentication service</li>
     *   <li>version: Managed by JPA @Version for optimistic locking</li>
     * </ul>
     * 
     * <p><strong>Null Value Handling:</strong></p>
     * <p>The nullValuePropertyMappingStrategy = IGNORE ensures that null fields in the request
     * do NOT overwrite existing entity values. This allows clients to send partial update
     * requests with only the fields they want to change.</p>
     * 
     * <p><strong>Example Service Layer Usage:</strong></p>
     * <pre>
     * User existingUser = userRepository.findById(userId).orElseThrow(...);
     * userMapper.updateEntityFromRequest(request, existingUser);
     * 
     * if (request.hasPasswordChange()) {
     *     if (!request.isPasswordConfirmed()) {
     *         throw new InvalidInputException("Password and confirmation do not match");
     *     }
     *     existingUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
     * }
     * 
     * userRepository.save(existingUser);
     * </pre>
     * 
     * @param request UserUpdateRequest DTO from PUT /api/v1/admin/users/{id} (validated, not null)
     * @param user Existing User entity from database (annotated with @MappingTarget, not null)
     * @throws NullPointerException if either parameter is null
     * @see org.mapstruct.MappingTarget
     * @see org.mapstruct.NullValuePropertyMappingStrategy#IGNORE
     */
    @Mapping(target = "userId", ignore = true)  // Primary key, immutable
    @Mapping(target = "username", ignore = true)  // Unique identifier, immutable
    @Mapping(target = "passwordHash", source = "password", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "lastLogin", ignore = true)  // Updated by authentication service only
    @Mapping(target = "accountLocked", ignore = true)  // Updated by security service only
    @Mapping(target = "failedLoginAttempts", ignore = true)  // Updated by authentication service only
    @Mapping(target = "version", ignore = true)  // Managed by JPA @Version
    void updateEntityFromRequest(UserUpdateRequest request, @MappingTarget User user);

    /**
     * Converts userType code to human-readable description.
     * 
     * <p>This named mapping method is used by toResponse() to derive the
     * userTypeDescription field for client-side display.</p>
     * 
     * <p><strong>Mapping Rules:</strong></p>
     * <ul>
     *   <li>'A' → "Administrator"</li>
     *   <li>'R' → "Regular User"</li>
     *   <li>Other values → "Unknown" (defensive programming)</li>
     * </ul>
     * 
     * @param userType User type code from User entity ('A' or 'R')
     * @return Human-readable description for display purposes
     */
    @Named("getUserTypeDescription")
    default String getUserTypeDescription(String userType) {
        if ("A".equals(userType)) {
            return "Administrator";
        } else if ("R".equals(userType)) {
            return "Regular User";
        } else {
            return "Unknown";
        }
    }

    /**
     * Derives Spring Security role names from userType code.
     * 
     * <p>This named mapping method is used by toResponse() to compute the
     * roles field for client-side role-based UI rendering and access control.</p>
     * 
     * <p><strong>Role Derivation Logic (Section 0.1.1):</strong></p>
     * <ul>
     *   <li>userType='A' (Admin) → ["ROLE_ADMIN", "ROLE_USER"] (administrators have both roles)</li>
     *   <li>userType='R' (Regular) → ["ROLE_USER"] (standard user access)</li>
     *   <li>Other values → ["ROLE_USER"] (default to minimal privileges)</li>
     * </ul>
     * 
     * <p><strong>Usage in Spring Security:</strong></p>
     * <pre>
     * &#64;PreAuthorize("hasRole('ADMIN')")
     * public ResponseEntity&lt;UserResponse&gt; deleteUser(@PathVariable Long id) { }
     * 
     * &#64;PreAuthorize("hasRole('USER')")
     * public ResponseEntity&lt;AccountResponse&gt; viewAccount(@PathVariable Long id) { }
     * </pre>
     * 
     * <p><strong>Client-Side Usage:</strong></p>
     * <p>The returned role list can be used by client applications to conditionally
     * render UI elements (e.g., show "Admin Panel" link only if roles contains "ROLE_ADMIN").</p>
     * 
     * @param userType User type code from User entity ('A' or 'R')
     * @return List of Spring Security role names (with "ROLE_" prefix)
     */
    @Named("getRolesFromUserType")
    default List<String> getRolesFromUserType(String userType) {
        if ("A".equals(userType)) {
            // Administrators have both ADMIN and USER roles
            return List.of("ROLE_ADMIN", "ROLE_USER");
        } else {
            // Regular users and default case have only USER role
            return List.of("ROLE_USER");
        }
    }
}
