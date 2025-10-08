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

package com.aws.carddemo.service;

import com.aws.carddemo.dto.request.UserCreateRequest;
import com.aws.carddemo.dto.request.UserUpdateRequest;
import com.aws.carddemo.dto.response.UserResponse;
import com.aws.carddemo.exception.DuplicateResourceException;
import com.aws.carddemo.exception.InvalidInputException;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.mapper.UserMapper;
import com.aws.carddemo.model.User;
import com.aws.carddemo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * User management service handling CRUD operations for admin functionality.
 * <p>
 * Migrated from COBOL programs:
 * - COUSR00C.cbl: User list retrieval with pagination
 * - COUSR01C.cbl: User creation with password hashing
 * - COUSR02C.cbl: User profile updates
 * - COUSR03C.cbl: User deletion
 * <p>
 * Replaces VSAM USRSEC file operations with Spring Data JPA repository operations
 * and implements BCrypt password hashing per PCI-DSS requirements (Requirement 8.2.1).
 * <p>
 * All methods require ROLE_ADMIN access per role-based access control.
 *
 * @see User
 * @see UserRepository
 * @see UserMapper
 * @since 1.0.0
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Password complexity regex pattern enforcing:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character from @$!%*?&#
     */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$"
    );

    /**
     * Retrieve all users with pagination support.
     * <p>
     * Replaces COBOL STARTBR/READNEXT pagination logic from COUSR00C.cbl
     * (WS-USER-DATA OCCURS 10 TIMES) with JPA Pageable interface supporting
     * flexible page sizes and sorting criteria.
     * <p>
     * COBOL Equivalent:
     * <pre>
     * STARTBR-USER-SEC-FILE.
     *     EXEC CICS STARTBR
     *          DATASET   (WS-USRSEC-FILE)
     *          RIDFLD    (SEC-USR-ID)
     *     END-EXEC.
     * READNEXT-USER-SEC-FILE.
     *     EXEC CICS READNEXT
     *          DATASET   (WS-USRSEC-FILE)
     *          INTO      (SEC-USER-DATA)
     *     END-EXEC.
     * </pre>
     *
     * @param pageable pagination parameters (page number, page size, sort order)
     * @return page of UserResponse DTOs with password hashes excluded
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.info("Retrieving users with pagination - page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<User> users = userRepository.findAll(pageable);

        log.info("Retrieved {} users out of {} total",
                users.getNumberOfElements(), users.getTotalElements());

        return users.map(userMapper::toResponse);
    }

    /**
     * Retrieve a single user by their internal database ID.
     * <p>
     * Replaces COBOL READ operation with JPA findById.
     * <p>
     * COBOL Equivalent:
     * <pre>
     * READ-USER-SEC-FILE.
     *     EXEC CICS READ
     *          DATASET   (WS-USRSEC-FILE)
     *          INTO      (SEC-USER-DATA)
     *          RIDFLD    (SEC-USR-ID)
     *          RESP      (WS-RESP-CD)
     *     END-EXEC.
     *     EVALUATE WS-RESP-CD
     *         WHEN DFHRESP(NOTFND)
     *             MOVE 'User ID NOT found...' TO WS-MESSAGE
     *     END-EVALUATE.
     * </pre>
     *
     * @param id the internal user ID
     * @return UserResponse DTO with password hash excluded
     * @throws ResourceNotFoundException if user with given ID not found
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        log.info("Retrieving user by ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", id);
                    return new ResourceNotFoundException("User not found with ID: " + id);
                });

        log.info("Successfully retrieved user: {}", user.getUsername());
        return userMapper.toResponse(user);
    }

    /**
     * Retrieve a single user by their username.
     * <p>
     * Replaces COBOL READ operation keyed by SEC-USR-ID.
     * <p>
     * COBOL Equivalent:
     * <pre>
     * MOVE USRIDINI OF COUSR2AI TO SEC-USR-ID
     * PERFORM READ-USER-SEC-FILE
     * </pre>
     *
     * @param username the unique username
     * @return UserResponse DTO with password hash excluded
     * @throws ResourceNotFoundException if user with given username not found
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        log.info("Retrieving user by username: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found with username: {}", username);
                    return new ResourceNotFoundException("User not found with username: " + username);
                });

        log.info("Successfully retrieved user: {}", user.getUsername());
        return userMapper.toResponse(user);
    }

    /**
     * Create a new user with BCrypt password hashing.
     * <p>
     * Replaces COBOL WRITE operation from COUSR01C.cbl with JPA save,
     * upgrading plain-text password (PIC X(08) from CSUSR01Y.cpy) to
     * BCrypt hashed password per PCI-DSS Requirement 8.2.1.
     * <p>
     * COBOL Equivalent:
     * <pre>
     * WRITE-USER-SEC-FILE.
     *     EXEC CICS WRITE
     *          DATASET   (WS-USRSEC-FILE)
     *          FROM      (SEC-USER-DATA)
     *          RIDFLD    (SEC-USR-ID)
     *          RESP      (WS-RESP-CD)
     *     END-EXEC.
     *     EVALUATE WS-RESP-CD
     *         WHEN DFHRESP(DUPREC)
     *             MOVE 'User ID already exists...' TO WS-MESSAGE
     *     END-EVALUATE.
     * </pre>
     *
     * @param request UserCreateRequest containing username, password, firstName, lastName, userType
     * @return UserResponse DTO of created user
     * @throws DuplicateResourceException if username already exists
     * @throws InvalidInputException      if password doesn't meet complexity requirements
     */
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating new user: {}", request.getUserId());

        // Validate username uniqueness (replaces COBOL DUPREC condition)
        if (userRepository.existsByUsername(request.getUserId())) {
            log.warn("Attempted to create duplicate user: {}", request.getUserId());
            throw new DuplicateResourceException(
                    "User already exists with username: " + request.getUserId()
            );
        }

        // Validate password strength (enhanced beyond COBOL 8-character limit)
        validatePasswordStrength(request.getPassword());

        // Map DTO to entity
        User user = userMapper.toEntity(request);

        // Hash password with BCrypt (replaces COBOL plain-text password)
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        user.setPasswordHash(hashedPassword);

        // Initialize security fields
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);

        // Persist user entity
        User savedUser = userRepository.save(user);

        log.info("Successfully created user: {} with ID: {}",
                savedUser.getUsername(), savedUser.getUserId());

        return userMapper.toResponse(savedUser);
    }

    /**
     * Update an existing user's profile information.
     * <p>
     * Replaces COBOL READ UPDATE/REWRITE operation from COUSR02C.cbl,
     * supporting partial updates with optional password change workflow.
     * <p>
     * COBOL Equivalent:
     * <pre>
     * READ-USER-SEC-FILE.
     *     EXEC CICS READ
     *          DATASET   (WS-USRSEC-FILE)
     *          INTO      (SEC-USER-DATA)
     *          RIDFLD    (SEC-USR-ID)
     *          UPDATE
     *     END-EXEC.
     * UPDATE-USER-SEC-FILE.
     *     IF FNAMEI OF COUSR2AI NOT = SEC-USR-FNAME
     *         MOVE FNAMEI OF COUSR2AI TO SEC-USR-FNAME
     *         SET USR-MODIFIED-YES TO TRUE
     *     END-IF.
     *     IF USR-MODIFIED-YES
     *         EXEC CICS REWRITE
     *              DATASET   (WS-USRSEC-FILE)
     *              FROM      (SEC-USER-DATA)
     *         END-EXEC.
     *     END-IF.
     * </pre>
     *
     * @param id      the internal user ID to update
     * @param request UserUpdateRequest containing updated fields
     * @return UserResponse DTO of updated user
     * @throws ResourceNotFoundException if user with given ID not found
     * @throws InvalidInputException     if password provided but doesn't meet complexity requirements
     */
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        log.info("Updating user with ID: {}", id);

        // Retrieve existing user (replaces COBOL READ UPDATE)
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", id);
                    return new ResourceNotFoundException("User not found with ID: " + id);
                });

        boolean modified = false;

        // Apply partial updates (replaces COBOL field-by-field comparison)
        if (request.getFirstName() != null && !request.getFirstName().equals(user.getFirstName())) {
            user.setFirstName(request.getFirstName());
            modified = true;
        }

        if (request.getLastName() != null && !request.getLastName().equals(user.getLastName())) {
            user.setLastName(request.getLastName());
            modified = true;
        }

        // Optional password change workflow
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            validatePasswordStrength(request.getPassword());
            String hashedPassword = passwordEncoder.encode(request.getPassword());
            user.setPasswordHash(hashedPassword);
            modified = true;
            log.info("Password updated for user: {}", user.getUsername());
        }

        if (request.getUserType() != null && !request.getUserType().equals(user.getUserType())) {
            user.setUserType(request.getUserType());
            modified = true;
        }

        if (!modified) {
            log.info("No modifications detected for user: {}", user.getUsername());
            throw new InvalidInputException("No changes detected. Please modify at least one field to update.");
        }

        // Persist changes (replaces COBOL REWRITE)
        User updatedUser = userRepository.save(user);

        log.info("Successfully updated user: {}", updatedUser.getUsername());

        return userMapper.toResponse(updatedUser);
    }

    /**
     * Delete a user from the system.
     * <p>
     * Replaces COBOL READ UPDATE/DELETE operation from COUSR03C.cbl.
     * <p>
     * COBOL Equivalent:
     * <pre>
     * DELETE-USER-INFO.
     *     MOVE USRIDINI OF COUSR3AI TO SEC-USR-ID
     *     PERFORM READ-USER-SEC-FILE
     *     PERFORM DELETE-USER-SEC-FILE.
     * DELETE-USER-SEC-FILE.
     *     EXEC CICS DELETE
     *          DATASET   (WS-USRSEC-FILE)
     *          RESP      (WS-RESP-CD)
     *     END-EXEC.
     *     EVALUATE WS-RESP-CD
     *         WHEN DFHRESP(NORMAL)
     *             STRING 'User ' SEC-USR-ID ' has been deleted ...'
     *               INTO WS-MESSAGE
     *         WHEN DFHRESP(NOTFND)
     *             MOVE 'User ID NOT found...' TO WS-MESSAGE
     *     END-EVALUATE.
     * </pre>
     *
     * @param id the internal user ID to delete
     * @throws ResourceNotFoundException if user with given ID not found
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long id) {
        log.info("Deleting user with ID: {}", id);

        // Verify user exists before deletion (replaces COBOL READ UPDATE)
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", id);
                    return new ResourceNotFoundException("User not found with ID: " + id);
                });

        String username = user.getUsername();

        // Delete user (replaces COBOL DELETE)
        userRepository.delete(user);

        log.info("Successfully deleted user: {} with ID: {}", username, id);
    }

    /**
     * Check if a username already exists in the system.
     * <p>
     * Used for duplicate validation during user creation, replacing
     * COBOL DUPREC condition handling.
     *
     * @param username the username to check
     * @return true if username exists, false otherwise
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        log.debug("Checking existence of username: {}", username);
        return userRepository.existsByUsername(username);
    }

    /**
     * Validate password strength against complexity requirements.
     * <p>
     * Enforces password policy exceeding legacy COBOL 8-character limit:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character from @$!%*?&#
     * <p>
     * Per PCI-DSS Requirement 8.2.3: "Passwords/passphrases must meet minimum strength
     * requirements including minimum length of at least seven characters and contain both
     * numeric and alphabetic characters."
     * <p>
     * Legacy COBOL implementation (CSUSR01Y.cpy):
     * <pre>
     * 05 SEC-USR-PWD  PIC X(08).  * Plain-text, 8-char max
     * </pre>
     *
     * @param password the password to validate
     * @throws InvalidInputException if password doesn't meet complexity requirements
     */
    public void validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            throw new InvalidInputException("Password cannot be empty");
        }

        if (password.length() < 8) {
            throw new InvalidInputException(
                    "Password must be at least 8 characters long"
            );
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new InvalidInputException(
                    "Password must contain at least one uppercase letter, one lowercase letter, " +
                            "one digit, and one special character (@$!%*?&#)"
            );
        }

        log.debug("Password strength validation passed");
    }
}
