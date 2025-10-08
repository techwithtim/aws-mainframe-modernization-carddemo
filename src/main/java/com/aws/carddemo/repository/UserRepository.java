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

package com.aws.carddemo.repository;

import com.aws.carddemo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository interface for User entity providing authentication
 * and user management data access operations.
 * 
 * <p>Migrated from: app/cpy/CSUSR01Y.cpy (COBOL copybook) and app/cbl/COSGN00C.cbl
 * (signon program), app/cbl/COUSR00C.cbl (user administration program)</p>
 * 
 * <p>This repository replaces COBOL VSAM keyed file READ operations on USRSEC file
 * with modern Spring Data JPA query methods, enabling declarative data access without
 * boilerplate code. It integrates seamlessly with Spring Security for JWT-based
 * authentication, replacing the legacy RACF mainframe security system.</p>
 * 
 * <p><strong>Original COBOL File Operations (COSGN00C.cbl lines 211-219):</strong></p>
 * <pre>
 * EXEC CICS READ
 *      DATASET   (WS-USRSEC-FILE)      - VSAM file name "USRSEC"
 *      INTO      (SEC-USER-DATA)       - 80-byte user record structure
 *      LENGTH    (LENGTH OF SEC-USER-DATA)
 *      RIDFLD    (WS-USER-ID)          - Key field for lookup (PIC X(08))
 *      KEYLENGTH (LENGTH OF WS-USER-ID)
 *      RESP      (WS-RESP-CD)
 *      RESP2     (WS-REAS-CD)
 * END-EXEC.
 * 
 * EVALUATE WS-RESP-CD
 *     WHEN 0                           - User found
 *         IF SEC-USR-PWD = WS-USER-PWD - Plain-text password comparison
 *             ... authenticate user ...
 *     WHEN 13                          - User not found
 *         MOVE 'User not found. Try again ...' TO WS-MESSAGE
 * </pre>
 * 
 * <p><strong>Modernization Changes:</strong></p>
 * <ul>
 *   <li>VSAM keyed READ by USER-ID → Spring Data {@code findByUsername(String)} query method</li>
 *   <li>Plain-text password (PIC X(08)) → BCrypt hashed password (255 chars, 10+ rounds)</li>
 *   <li>CICS RESP-CD 0/13 → Java {@code Optional<User>} (empty() for not found)</li>
 *   <li>80-byte fixed record → PostgreSQL normalized table with extended fields</li>
 *   <li>File-level locking → Database ACID transaction management</li>
 *   <li>RACF security → Spring Security with JWT tokens</li>
 * </ul>
 * 
 * <p><strong>Query Methods:</strong></p>
 * <ul>
 *   <li>{@link #findByUsername(String)} - Primary authentication lookup by username
 *       <br>Replaces: COBOL READ USRSEC FILE KEY IS USER-ID (COSGN00C.cbl line 211-219)
 *       <br>Used by: {@code UserDetailsService.loadUserByUsername()} for Spring Security</li>
 *   <li>{@link #existsByUsername(String)} - Duplicate username validation
 *       <br>Used by: User creation in admin screens (COUSR01C.cbl logic)
 *       <br>Prevents: Duplicate user ID errors (VSAM duplicate key condition)</li>
 * </ul>
 * 
 * <p><strong>Security Enhancements (Section 0.8.1):</strong></p>
 * <ul>
 *   <li>BCrypt password hashing replaces plain-text COBOL passwords</li>
 *   <li>Account lockout tracking after failed login attempts</li>
 *   <li>Last login timestamp for audit trail compliance</li>
 *   <li>Password field excluded from JSON serialization (@JsonIgnore)</li>
 *   <li>Sensitive data masked in logs (@ToString.Exclude on User entity)</li>
 * </ul>
 * 
 * <p><strong>Spring Security Integration:</strong></p>
 * <p>This repository is used by {@code UserDetailsServiceImpl} to load user credentials
 * during authentication. The flow replaces COBOL signon logic (COSGN00C.cbl):</p>
 * <ol>
 *   <li>Client sends POST /api/v1/auth/login with username and password</li>
 *   <li>{@code AuthenticationService} calls {@code findByUsername(username)}</li>
 *   <li>If user found, BCrypt compares hashed password vs. plain-text input</li>
 *   <li>On success, JWT token generated with user roles (ROLE_ADMIN or ROLE_USER)</li>
 *   <li>On failure, increment failedLoginAttempts; lock account after 5 attempts</li>
 * </ol>
 * 
 * <p><strong>User Administration Integration:</strong></p>
 * <p>Admin operations (COUSR00C.cbl family - list/add/update/delete users) use
 * {@code existsByUsername()} to validate unique usernames before creation:</p>
 * <pre>
 * // Before creating new user (replaces COUSR01C.cbl duplicate check)
 * if (userRepository.existsByUsername(newUsername)) {
 *     throw new DuplicateResourceException("Username already exists");
 * }
 * </pre>
 * 
 * <p><strong>Performance Optimizations:</strong></p>
 * <ul>
 *   <li>Database index: {@code idx_user_username} (unique) on username column</li>
 *   <li>Query performance: O(log n) lookup via B-tree index vs. VSAM keyed access</li>
 *   <li>Connection pooling: HikariCP with max 20 connections</li>
 *   <li>Result set size: Single user record (typically <1 KB)</li>
 * </ul>
 * 
 * <p><strong>COBOL Data Structure Mapping:</strong></p>
 * <pre>
 * COBOL (CSUSR01Y.cpy):              Java (User.java):
 * =====================              =================
 * SEC-USR-ID PIC X(08)       →       username String(50) UNIQUE NOT NULL
 * SEC-USR-FNAME PIC X(20)    →       firstName String(50)
 * SEC-USR-LNAME PIC X(20)    →       lastName String(50)
 * SEC-USR-PWD PIC X(08)      →       passwordHash String(255) BCrypt
 * SEC-USR-TYPE PIC X(01)     →       userType String(1) CHECK('A', 'R')
 * SEC-USR-FILLER PIC X(23)   →       (not migrated)
 *                            +       userId Long (auto-generated PK)
 *                            +       lastLogin LocalDateTime (audit)
 *                            +       accountLocked Boolean (security)
 *                            +       failedLoginAttempts Integer (security)
 *                            +       version Integer (optimistic locking)
 * </pre>
 * 
 * <p><strong>Exception Handling:</strong></p>
 * <ul>
 *   <li>User not found → {@code Optional.empty()} (no exception thrown)</li>
 *   <li>Database connection error → {@code DataAccessException} (Spring exception translation)</li>
 *   <li>Constraint violation → {@code DataIntegrityViolationException} (duplicate username)</li>
 * </ul>
 * 
 * <p><strong>Transaction Management:</strong></p>
 * <p>All write operations (save, delete) are wrapped in {@code @Transactional} service methods,
 * ensuring ACID properties. Read operations (find, exists) do not require explicit transactions
 * and use read-committed isolation level by default.</p>
 * 
 * <p><strong>Testing:</strong></p>
 * <ul>
 *   <li>Unit tests: Mock repository with Mockito in service layer tests</li>
 *   <li>Integration tests: Testcontainers with PostgreSQL for end-to-end authentication flow</li>
 *   <li>Test coverage: Verify username lookup, duplicate validation, account lockout logic</li>
 * </ul>
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @see User
 * @see org.springframework.security.core.userdetails.UserDetailsService
 * @see org.springframework.data.jpa.repository.JpaRepository
 * @since 1.0.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique username for authentication and authorization.
     * 
     * <p>This method replaces the COBOL VSAM keyed READ operation on USRSEC file
     * from the signon program (COSGN00C.cbl lines 211-219). It is the primary method
     * used by Spring Security's {@code UserDetailsService.loadUserByUsername()} to
     * load user credentials during authentication.</p>
     * 
     * <p><strong>Original COBOL Logic (COSGN00C.cbl lines 211-257):</strong></p>
     * <pre>
     * READ-USER-SEC-FILE.
     *     EXEC CICS READ
     *          DATASET   (WS-USRSEC-FILE)      - "USRSEC" file
     *          INTO      (SEC-USER-DATA)       - User record structure
     *          RIDFLD    (WS-USER-ID)          - Key: Username (PIC X(08))
     *          RESP      (WS-RESP-CD)          - Response code
     *     END-EXEC.
     * 
     *     EVALUATE WS-RESP-CD
     *         WHEN 0                           - User found
     *             IF SEC-USR-PWD = WS-USER-PWD
     *                 ... authenticate user ...
     *             ELSE
     *                 MOVE 'Wrong Password. Try again ...' TO WS-MESSAGE
     *         WHEN 13                          - User not found
     *             MOVE 'User not found. Try again ...' TO WS-MESSAGE
     *         WHEN OTHER
     *             MOVE 'Unable to verify the User ...' TO WS-MESSAGE
     *     END-EVALUATE.
     * </pre>
     * 
     * <p><strong>Modern Implementation:</strong></p>
     * <pre>
     * Optional&lt;User&gt; userOpt = userRepository.findByUsername(username);
     * if (userOpt.isEmpty()) {
     *     throw new UsernameNotFoundException("User not found: " + username);
     * }
     * User user = userOpt.get();
     * if (passwordEncoder.matches(plainPassword, user.getPasswordHash())) {
     *     // Authentication successful
     *     user.setLastLogin(LocalDateTime.now());
     *     user.setFailedLoginAttempts(0);  // Reset counter
     *     userRepository.save(user);
     * } else {
     *     // Authentication failed
     *     user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
     *     if (user.getFailedLoginAttempts() &gt;= 5) {
     *         user.setAccountLocked(true);  // Lock after 5 failed attempts
     *     }
     *     userRepository.save(user);
     *     throw new BadCredentialsException("Invalid password");
     * }
     * </pre>
     * 
     * <p><strong>Query Derivation:</strong></p>
     * <p>Spring Data JPA automatically generates the SQL query from the method name:</p>
     * <pre>
     * SELECT u FROM User u WHERE u.username = :username
     * 
     * Translates to PostgreSQL:
     * SELECT user_id, username, password_hash, first_name, last_name, user_type,
     *        last_login, account_locked, failed_login_attempts, version
     * FROM app_user
     * WHERE username = ?
     * LIMIT 1
     * </pre>
     * 
     * <p><strong>Performance Characteristics:</strong></p>
     * <ul>
     *   <li>Index: Uses unique B-tree index {@code idx_user_username} on username column</li>
     *   <li>Complexity: O(log n) lookup time, typically 1-2 disk seeks for small user table</li>
     *   <li>Result size: Single user record (~200 bytes with all fields)</li>
     *   <li>Caching: Not cached (authentication requires fresh data for security)</li>
     * </ul>
     * 
     * <p><strong>Security Considerations:</strong></p>
     * <ul>
     *   <li>Username lookup is case-sensitive (database collation dependent)</li>
     *   <li>Password hash is included in result but excluded from JSON serialization</li>
     *   <li>Account lockout status is checked after retrieval to prevent brute-force attacks</li>
     *   <li>Last login timestamp is updated on successful authentication</li>
     * </ul>
     * 
     * <p><strong>Exception Handling:</strong></p>
     * <ul>
     *   <li>User not found → Returns {@code Optional.empty()} (no exception)</li>
     *   <li>Multiple users with same username → Prevented by UNIQUE constraint on username column</li>
     *   <li>Database error → Throws {@code DataAccessException} (Spring exception translation)</li>
     * </ul>
     * 
     * <p><strong>Usage Examples:</strong></p>
     * <pre>
     * // Example 1: Spring Security UserDetailsService
     * &#64;Override
     * public UserDetails loadUserByUsername(String username) {
     *     User user = userRepository.findByUsername(username)
     *         .orElseThrow(() -&gt; new UsernameNotFoundException("User not found"));
     *     
     *     if (user.getAccountLocked()) {
     *         throw new LockedException("Account is locked");
     *     }
     *     
     *     return new org.springframework.security.core.userdetails.User(
     *         user.getUsername(),
     *         user.getPasswordHash(),
     *         user.getAuthorities()  // ROLE_ADMIN or ROLE_USER based on userType
     *     );
     * }
     * 
     * // Example 2: Login endpoint
     * &#64;PostMapping("/api/v1/auth/login")
     * public ResponseEntity&lt;LoginResponse&gt; login(&#64;RequestBody LoginRequest request) {
     *     User user = userRepository.findByUsername(request.getUsername())
     *         .orElseThrow(() -&gt; new AuthenticationFailedException("Invalid credentials"));
     *     
     *     if (passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
     *         String token = jwtTokenProvider.generateToken(user);
     *         return ResponseEntity.ok(new LoginResponse(token, user.getUsername()));
     *     }
     *     throw new AuthenticationFailedException("Invalid credentials");
     * }
     * </pre>
     * 
     * <p><strong>Equivalent COBOL Operation:</strong></p>
     * <p>This method replaces COSGN00C.cbl READ-USER-SEC-FILE paragraph (lines 209-257)
     * which performs a keyed READ on USRSEC VSAM file with USER-ID as the key.</p>
     * 
     * @param username the unique username to search for (case-sensitive, max 50 chars)
     * @return an {@code Optional} containing the {@code User} if found, or {@code Optional.empty()}
     *         if no user exists with the given username
     * @throws IllegalArgumentException if username is null (validated by Spring Data)
     * @throws DataAccessException if database error occurs (Spring exception translation)
     * 
     * @see User#getUsername()
     * @see org.springframework.security.core.userdetails.UserDetailsService#loadUserByUsername(String)
     */
    Optional<User> findByUsername(String username);

    /**
     * Checks if a user with the specified username already exists in the database.
     * 
     * <p>This method is used for duplicate username validation during user creation
     * in admin screens, replacing COBOL duplicate key checking logic from user
     * administration programs (COUSR01C.cbl user creation). It prevents duplicate
     * username errors that would occur with VSAM duplicate key violations.</p>
     * 
     * <p><strong>Original COBOL Duplicate Check Pattern:</strong></p>
     * <p>In COBOL, duplicate checking was implicit in the WRITE operation:</p>
     * <pre>
     * * Attempt to write new user record to USRSEC file
     * EXEC CICS WRITE
     *      DATASET   (WS-USRSEC-FILE)
     *      FROM      (SEC-USER-DATA)
     *      RIDFLD    (SEC-USR-ID)     - Unique key: Username
     *      RESP      (WS-RESP-CD)
     * END-EXEC.
     * 
     * EVALUATE WS-RESP-CD
     *     WHEN 0
     *         MOVE 'User created successfully' TO WS-MESSAGE
     *     WHEN 14                      - Duplicate key error
     *         MOVE 'Username already exists' TO WS-MESSAGE
     *     WHEN OTHER
     *         MOVE 'Error creating user' TO WS-MESSAGE
     * END-EVALUATE.
     * </pre>
     * 
     * <p><strong>Modern Best Practice:</strong></p>
     * <p>Explicitly check for existence before attempting insert to provide better
     * user feedback and avoid unnecessary database constraint violations:</p>
     * <pre>
     * // User creation in admin service (replaces COUSR01C.cbl)
     * if (userRepository.existsByUsername(newUser.getUsername())) {
     *     throw new DuplicateResourceException(
     *         "Username '" + newUser.getUsername() + "' already exists. Please choose a different username."
     *     );
     * }
     * 
     * // Hash password before saving
     * newUser.setPasswordHash(passwordEncoder.encode(plainPassword));
     * newUser.setFailedLoginAttempts(0);
     * newUser.setAccountLocked(false);
     * userRepository.save(newUser);
     * </pre>
     * 
     * <p><strong>Query Derivation:</strong></p>
     * <p>Spring Data JPA generates an efficient existence check query:</p>
     * <pre>
     * SELECT CASE WHEN COUNT(u) &gt; 0 THEN TRUE ELSE FALSE END
     * FROM User u
     * WHERE u.username = :username
     * 
     * Translates to optimized PostgreSQL:
     * SELECT EXISTS (
     *     SELECT 1 FROM app_user WHERE username = ? LIMIT 1
     * )
     * </pre>
     * 
     * <p><strong>Performance Characteristics:</strong></p>
     * <ul>
     *   <li>Index: Leverages unique B-tree index {@code idx_user_username} on username column</li>
     *   <li>Complexity: O(log n) lookup time with early termination (EXISTS clause)</li>
     *   <li>Result size: Single boolean value (1 bit)</li>
     *   <li>Optimization: Database stops scanning after first match (EXISTS optimization)</li>
     *   <li>Network overhead: Minimal (boolean return value)</li>
     * </ul>
     * 
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>User creation form validation (COUSR01C.cbl - add user screen)</li>
     *   <li>Username availability check during registration</li>
     *   <li>Preventing duplicate user creation in batch import operations</li>
     *   <li>Pre-validation before attempting database INSERT</li>
     * </ul>
     * 
     * <p><strong>Exception Handling:</strong></p>
     * <ul>
     *   <li>Username not found → Returns {@code false} (no exception)</li>
     *   <li>Username exists → Returns {@code true}</li>
     *   <li>Database error → Throws {@code DataAccessException}</li>
     * </ul>
     * 
     * <p><strong>Usage Examples:</strong></p>
     * <pre>
     * // Example 1: User creation validation (admin endpoint)
     * &#64;PostMapping("/api/v1/admin/users")
     * public ResponseEntity&lt;UserResponse&gt; createUser(&#64;Valid &#64;RequestBody UserCreateRequest request) {
     *     // Validate username uniqueness
     *     if (userRepository.existsByUsername(request.getUsername())) {
     *         throw new DuplicateResourceException(
     *             "Username '" + request.getUsername() + "' is already taken"
     *         );
     *     }
     *     
     *     User newUser = userMapper.toEntity(request);
     *     newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
     *     User savedUser = userRepository.save(newUser);
     *     return ResponseEntity.status(HttpStatus.CREATED)
     *         .body(userMapper.toResponse(savedUser));
     * }
     * 
     * // Example 2: Username availability check endpoint
     * &#64;GetMapping("/api/v1/users/check-availability")
     * public ResponseEntity&lt;Boolean&gt; checkUsernameAvailability(&#64;RequestParam String username) {
     *     boolean available = !userRepository.existsByUsername(username);
     *     return ResponseEntity.ok(available);
     * }
     * 
     * // Example 3: Batch user import with duplicate prevention
     * public void importUsers(List&lt;UserCreateRequest&gt; users) {
     *     List&lt;User&gt; newUsers = users.stream()
     *         .filter(req -&gt; !userRepository.existsByUsername(req.getUsername()))
     *         .map(req -&gt; {
     *             User user = userMapper.toEntity(req);
     *             user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
     *             return user;
     *         })
     *         .toList();
     *     userRepository.saveAll(newUsers);
     * }
     * </pre>
     * 
     * <p><strong>Equivalent COBOL Operation:</strong></p>
     * <p>This method provides explicit duplicate checking that was implicit in COBOL
     * WRITE operations with RESP-CD 14 (duplicate key) error handling in COUSR01C.cbl
     * user creation program.</p>
     * 
     * <p><strong>Alternative Approach:</strong></p>
     * <p>Instead of explicit existence check, could rely on database UNIQUE constraint
     * and catch {@code DataIntegrityViolationException}. However, explicit check provides
     * better user experience with descriptive error messages before attempting insert.</p>
     * 
     * @param username the username to check for existence (case-sensitive, max 50 chars)
     * @return {@code true} if a user with the given username exists, {@code false} otherwise
     * @throws IllegalArgumentException if username is null (validated by Spring Data)
     * @throws DataAccessException if database error occurs (Spring exception translation)
     * 
     * @see User#getUsername()
     * @see #findByUsername(String)
     */
    boolean existsByUsername(String username);
}
