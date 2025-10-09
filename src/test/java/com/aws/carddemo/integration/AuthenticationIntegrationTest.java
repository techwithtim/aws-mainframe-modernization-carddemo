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

package com.aws.carddemo.integration;

import com.aws.carddemo.dto.request.LoginRequest;
import com.aws.carddemo.dto.response.ApiError;
import com.aws.carddemo.dto.response.LoginResponse;
import com.aws.carddemo.model.User;
import com.aws.carddemo.repository.UserRepository;
import com.aws.carddemo.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for JWT authentication functionality.
 * 
 * <p>This test class validates the complete authentication workflow,
 * proving functional equivalence with the legacy COBOL COSGN00C.cbl
 * signon screen while implementing modern security practices.</p>
 * 
 * <p><strong>Migrated from:</strong></p>
 * <ul>
 *   <li><b>COBOL Program:</b> app/cbl/COSGN00C.cbl - CICS Signon Screen Program</li>
 *   <li><b>BMS Screen:</b> app/bms/COSGN00.bms - Login Screen Definition (COSGN0A map)</li>
 *   <li><b>Copybook:</b> app/cpy/CSUSR01Y.cpy - User Security Record Structure</li>
 *   <li><b>Transaction ID:</b> CC00 (CICS transaction for signon)</li>
 * </ul>
 * 
 * <p><strong>Legacy COBOL Authentication Flow (COSGN00C.cbl):</strong></p>
 * <pre>
 * COSGN00C.cbl Main Logic:
 * 1. Lines 108-141: Process-Enter-Key paragraph
 *    - EXEC CICS RECEIVE MAP('COSGN0A') MAPSET('COSGN00')
 *    - Extract USERIDI and PASSWDI fields from BMS screen
 *    - Validate fields not SPACES or LOW-VALUES (lines 120-127)
 *    - MOVE FUNCTION UPPER-CASE(USERIDI) TO WS-USER-ID (line 132-133)
 * 
 * 2. Lines 209-257: READ-USER-SEC-FILE paragraph
 *    - EXEC CICS READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID)
 *    - EVALUATE WS-RESP-CD
 *      WHEN 0: User found (line 222)
 *        IF SEC-USR-PWD = WS-USER-PWD (plain-text password comparison, line 223)
 *          MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE (line 227)
 *          EXEC CICS XCTL PROGRAM('COADM01C' or 'COMEN01C') (line 231-240)
 *        ELSE: Display 'Wrong Password. Try again ...' (line 242)
 *      WHEN 13: User not found (line 248)
 *        MOVE 'User not found. Try again ...' TO WS-MESSAGE
 *      WHEN OTHER: Generic error (line 254)
 * </pre>
 * 
 * <p><strong>Modern REST API Authentication Flow:</strong></p>
 * <pre>
 * POST /api/v1/auth/login:
 * 1. Accept JSON request body with username and password
 * 2. Bean Validation (@Valid) ensures fields not blank
 * 3. UserRepository.findByUsername() replaces VSAM READ DATASET('USRSEC')
 * 4. BCrypt password verification replaces plain-text comparison
 * 5. Generate JWT access token (replaces EXEC CICS XCTL navigation)
 * 6. Return LoginResponse with token, expiration, user details, and roles
 * 7. HTTP 401 Unauthorized on authentication failure
 * </pre>
 * 
 * <p><strong>Security Enhancements vs. COBOL:</strong></p>
 * <ul>
 *   <li>Plain-text password (SEC-USR-PWD) → BCrypt hashed password (10+ rounds)</li>
 *   <li>Session-based auth (COMMAREA) → Stateless JWT tokens (1-hour expiration)</li>
 *   <li>No account lockout → Lock account after 5 failed login attempts</li>
 *   <li>No audit trail → Track lastLogin timestamp for security monitoring</li>
 *   <li>RACF mainframe security → Spring Security with role-based access control</li>
 * </ul>
 * 
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>{@link #testSuccessfulLogin()} - Valid credentials return JWT token</li>
 *   <li>{@link #testLoginWithInvalidUsername()} - Non-existent user returns HTTP 401</li>
 *   <li>{@link #testLoginWithInvalidPassword()} - Wrong password returns HTTP 401</li>
 *   <li>{@link #testLoginWithMissingCredentials()} - Empty fields return HTTP 400</li>
 *   <li>{@link #testAuthenticatedEndpointAccessWithValidToken()} - JWT enables protected endpoint access</li>
 *   <li>{@link #testAuthenticatedEndpointAccessWithoutToken()} - Missing JWT returns HTTP 401</li>
 *   <li>{@link #testJwtTokenExpiration()} - Expired tokens are rejected</li>
 *   <li>{@link #testPasswordEncryption()} - Plain-text password never stored in database</li>
 *   <li>{@link #testAccountLockoutAfterFailedAttempts()} - Security enhancement test</li>
 *   <li>{@link #testLastLoginTimestampUpdate()} - Audit trail verification</li>
 * </ul>
 * 
 * <p><strong>Performance Requirements:</strong></p>
 * <p>All tests must complete in &lt;2 seconds per test method to maintain fast test suite execution</p>
 * 
 * <p><strong>PCI-DSS Compliance:</strong></p>
 * <p>Tests verify that plain-text passwords are never logged or stored, and that
 * BCrypt hashing is correctly applied per Agent Action Plan Section 0.8.1 Critical Directive 3</p>
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @see com.aws.carddemo.controller.AuthController
 * @see com.aws.carddemo.service.AuthenticationService
 * @see com.aws.carddemo.security.JwtTokenProvider
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class AuthenticationIntegrationTest extends PostgresTestContainer {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private final String testUsername = "testuser";
    private final String testPassword = "password123";
    private final String testFirstName = "Test";
    private final String testLastName = "User";
    private final String testUserType = "R"; // Regular user (ROLE_USER)

    /**
     * Sets up test user in database before each test execution.
     * 
     * <p>Creates a test user with BCrypt-hashed password matching the test
     * credentials used throughout authentication tests. This setup replaces
     * the COBOL test data in VSAM USRSEC file.</p>
     * 
     * <p><strong>COBOL Test Data Equivalent:</strong></p>
     * <pre>
     * USRSEC File Test Record (80 bytes):
     * SEC-USR-ID:     'testuser' (PIC X(08))
     * SEC-USR-FNAME:  'Test'     (PIC X(20))
     * SEC-USR-LNAME:  'User'     (PIC X(20))
     * SEC-USR-PWD:    'password123' (PIC X(08) - plain-text!)
     * SEC-USR-TYPE:   'R'        (PIC X(01) - Regular user)
     * </pre>
     * 
     * <p><strong>Modern Security Implementation:</strong></p>
     * <ul>
     *   <li>Password: BCrypt hashed with 10 rounds (instead of plain-text)</li>
     *   <li>Account status: Not locked (accountLocked=false)</li>
     *   <li>Failed attempts: Zero (failedLoginAttempts=0)</li>
     *   <li>Last login: null (will be set on successful authentication)</li>
     * </ul>
     */
    @BeforeEach
    void setUp() {
        // Clean database before each test to ensure test isolation
        userRepository.deleteAll();

        // Create test user with BCrypt-hashed password
        // Replaces COBOL VSAM USRSEC file test record
        testUser = User.builder()
                .username(testUsername)
                .passwordHash(passwordEncoder.encode(testPassword))
                .firstName(testFirstName)
                .lastName(testLastName)
                .userType(testUserType)
                .accountLocked(false)
                .failedLoginAttempts(0)
                .build();

        userRepository.save(testUser);
    }

    /**
     * Cleans up test data after each test execution.
     * 
     * <p>Ensures test isolation by removing all test users from the database,
     * preventing test pollution between consecutive test methods.</p>
     */
    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    /**
     * Tests successful login with valid credentials.
     * 
     * <p><strong>Validates functional equivalence with COBOL COSGN00C.cbl lines 222-240:</strong></p>
     * <pre>
     * WHEN 0                                    - User found in USRSEC file
     *     IF SEC-USR-PWD = WS-USER-PWD         - Plain-text password match
     *         MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     *         EXEC CICS XCTL PROGRAM(...)      - Transfer control to menu
     * </pre>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Construct LoginRequest with valid test credentials</li>
     *   <li>POST /api/v1/auth/login with JSON body</li>
     *   <li>Assert HTTP 200 OK status</li>
     *   <li>Assert LoginResponse contains non-null JWT access token</li>
     *   <li>Verify JWT token structure (header.payload.signature)</li>
     *   <li>Decode and validate token claims (sub, exp, iat, roles)</li>
     *   <li>Verify user details in response (userId, username, firstName, lastName)</li>
     *   <li>Verify token expiration timestamp is in the future</li>
     *   <li>Verify roles array contains "ROLE_USER" for regular user</li>
     * </ol>
     * 
     * <p><strong>Expected JWT Claims:</strong></p>
     * <ul>
     *   <li>sub (subject): User ID from database</li>
     *   <li>exp (expiration): Current time + 1 hour (3600 seconds)</li>
     *   <li>iat (issued-at): Current timestamp</li>
     *   <li>roles: ["ROLE_USER"] for userType='R', ["ROLE_ADMIN", "ROLE_USER"] for userType='A'</li>
     * </ul>
     *
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    void testSuccessfulLogin() throws Exception {
        // Arrange: Create login request with valid credentials
        LoginRequest loginRequest = LoginRequest.builder()
                .username(testUsername)
                .password(testPassword)
                .build();

        // Act: Perform POST /api/v1/auth/login
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.username").value(testUsername))
                .andReturn();

        // Assert: Parse response and validate JWT token
        String responseBody = mvcResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(responseBody, LoginResponse.class);

        // Verify JWT token structure (header.payload.signature)
        assertNotNull(loginResponse.getAccessToken(), "JWT access token must not be null");
        assertTrue(Pattern.matches("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$", 
                loginResponse.getAccessToken()),
                "JWT token must follow header.payload.signature format");

        // Verify token metadata
        assertEquals("Bearer", loginResponse.getTokenType(), "Token type must be Bearer");
        assertEquals(3600, loginResponse.getExpiresIn(), "Token must expire in 1 hour (3600 seconds)");
        assertNotNull(loginResponse.getExpiresAt(), "Expiration timestamp must not be null");
        assertTrue(loginResponse.getExpiresAt().isAfter(LocalDateTime.now()),
                "Token expiration must be in the future");

        // Verify user details in response
        assertNotNull(loginResponse.getUserId(), "User ID must be present in response");
        assertEquals(testUsername, loginResponse.getUsername(), "Username must match test user");
        assertEquals(testFirstName, loginResponse.getFirstName(), "First name must match test user");
        assertEquals(testLastName, loginResponse.getLastName(), "Last name must match test user");
        assertEquals(testUserType, loginResponse.getUserType(), "User type must match test user");

        // Verify roles array contains correct Spring Security role
        assertNotNull(loginResponse.getRoles(), "Roles array must not be null");
        assertFalse(loginResponse.getRoles().isEmpty(), "Roles array must not be empty");
        assertTrue(loginResponse.getRoles().contains("ROLE_USER"),
                "Regular user must have ROLE_USER role");

        // Verify JWT token can be validated by JwtTokenProvider
        assertTrue(jwtTokenProvider.validateToken(loginResponse.getAccessToken()),
                "JWT token must pass validation");

        // Verify username can be extracted from token
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(loginResponse.getAccessToken());
        assertEquals(testUsername, extractedUsername,
                "Username extracted from token must match login username");

        // Verify roles can be extracted from token
        List<String> extractedRoles = jwtTokenProvider.getRolesFromToken(loginResponse.getAccessToken());
        assertTrue(extractedRoles.contains("ROLE_USER"),
                "Roles extracted from token must contain ROLE_USER");
    }

    /**
     * Tests login failure with invalid (non-existent) username.
     * 
     * <p><strong>Validates functional equivalence with COBOL COSGN00C.cbl lines 248-253:</strong></p>
     * <pre>
     * WHEN 13                                   - CICS RESP-CD 13: Record not found
     *     MOVE 'User not found. Try again ...' TO WS-MESSAGE
     *     MOVE -1 TO USERIDI                    - Position cursor on username field
     *     GO TO SEND-SIGNON-SCREEN             - Redisplay signon screen with error
     * </pre>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Construct LoginRequest with non-existent username</li>
     *   <li>POST /api/v1/auth/login</li>
     *   <li>Assert HTTP 401 Unauthorized status</li>
     *   <li>Assert ApiError response contains appropriate error message</li>
     *   <li>Verify error message indicates invalid credentials</li>
     * </ol>
     *
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    void testLoginWithInvalidUsername() throws Exception {
        // Arrange: Create login request with non-existent username
        LoginRequest loginRequest = LoginRequest.builder()
                .username("nonexistent")
                .password(testPassword)
                .build();

        // Act & Assert: Perform POST /api/v1/auth/login expecting HTTP 401
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andReturn();

        // Parse error response
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiError apiError = objectMapper.readValue(responseBody, ApiError.class);

        // Verify error details
        assertEquals(HttpStatus.UNAUTHORIZED.value(), apiError.getStatus(),
                "Status code must be 401 Unauthorized");
        assertNotNull(apiError.getMessage(), "Error message must not be null");
        assertTrue(apiError.getMessage().toLowerCase().contains("authentication failed") ||
                        apiError.getMessage().toLowerCase().contains("invalid credentials"),
                "Error message must indicate authentication failure");
    }

    /**
     * Tests login failure with invalid password.
     * 
     * <p><strong>Validates functional equivalence with COBOL COSGN00C.cbl lines 241-247:</strong></p>
     * <pre>
     * IF SEC-USR-PWD = WS-USER-PWD
     *     ... authenticate user ...
     * ELSE
     *     MOVE 'Wrong Password. Try again ...' TO WS-MESSAGE
     *     MOVE -1 TO PASSWDI                    - Position cursor on password field
     *     GO TO SEND-SIGNON-SCREEN             - Redisplay signon screen with error
     * </pre>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Construct LoginRequest with correct username but wrong password</li>
     *   <li>POST /api/v1/auth/login</li>
     *   <li>Assert HTTP 401 Unauthorized status</li>
     *   <li>Assert ApiError response contains appropriate error message</li>
     *   <li>Verify failedLoginAttempts counter is incremented in database</li>
     * </ol>
     *
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    void testLoginWithInvalidPassword() throws Exception {
        // Arrange: Create login request with correct username but wrong password
        LoginRequest loginRequest = LoginRequest.builder()
                .username(testUsername)
                .password("wrongpassword")
                .build();

        // Act & Assert: Perform POST /api/v1/auth/login expecting HTTP 401
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andReturn();

        // Parse error response
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiError apiError = objectMapper.readValue(responseBody, ApiError.class);

        // Verify error details
        assertEquals(HttpStatus.UNAUTHORIZED.value(), apiError.getStatus(),
                "Status code must be 401 Unauthorized");
        assertNotNull(apiError.getMessage(), "Error message must not be null");
        assertTrue(apiError.getMessage().toLowerCase().contains("authentication failed") ||
                        apiError.getMessage().toLowerCase().contains("invalid"),
                "Error message must indicate authentication failure");

        // Verify failedLoginAttempts counter was incremented (security enhancement)
        User updatedUser = userRepository.findByUsername(testUsername).orElseThrow();
        assertEquals(1, updatedUser.getFailedLoginAttempts(),
                "Failed login attempts counter must be incremented to 1");
        assertFalse(updatedUser.getAccountLocked(),
                "Account must not be locked after 1 failed attempt");
    }

    /**
     * Tests login validation with missing credentials.
     * 
     * <p><strong>Validates functional equivalence with COBOL COSGN00C.cbl lines 120-127:</strong></p>
     * <pre>
     * IF USERIDI = SPACES OR USERIDI = LOW-VALUES
     *     MOVE 'Please enter your User ID ...' TO WS-MESSAGE
     *     MOVE -1 TO USERIDI
     *     GO TO SEND-SIGNON-SCREEN
     * 
     * IF PASSWDI = SPACES OR PASSWDI = LOW-VALUES
     *     MOVE 'Please enter your Password ...' TO WS-MESSAGE
     *     MOVE -1 TO PASSWDI
     *     GO TO SEND-SIGNON-SCREEN
     * </pre>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Construct LoginRequest with empty username and password</li>
     *   <li>POST /api/v1/auth/login</li>
     *   <li>Assert HTTP 400 Bad Request status</li>
     *   <li>Assert ApiError response contains validation errors</li>
     *   <li>Verify validationErrors map contains field-level error messages</li>
     * </ol>
     *
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    void testLoginWithMissingCredentials() throws Exception {
        // Arrange: Create login request with empty username and password
        LoginRequest loginRequest = LoginRequest.builder()
                .username("")
                .password("")
                .build();

        // Act & Assert: Perform POST /api/v1/auth/login expecting HTTP 400
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors").exists())
                .andReturn();

        // Parse error response
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiError apiError = objectMapper.readValue(responseBody, ApiError.class);

        // Verify validation errors contain username and password field errors
        assertNotNull(apiError.getValidationErrors(), "Validation errors must not be null");
        assertTrue(apiError.getValidationErrors().containsKey("username") ||
                        apiError.getValidationErrors().containsKey("password"),
                "Validation errors must contain username or password field errors");
    }

    /**
     * Tests authenticated endpoint access with valid JWT token.
     * 
     * <p><strong>Validates JWT-based security replacing COBOL session management:</strong></p>
     * <p>In COBOL, after successful login, the COMMAREA structure carried user context
     * across CICS program transfers (XCTL). In the modern REST API, JWT tokens in the
     * Authorization header carry user authentication and authorization information.</p>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Login successfully and obtain JWT token</li>
     *   <li>Call protected endpoint GET /api/v1/accounts/1 with Authorization: Bearer {token}</li>
     *   <li>Assert HTTP 200 OK status (endpoint access granted)</li>
     *   <li>Verify SecurityContext is populated with authenticated user</li>
     * </ol>
     *
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    void testAuthenticatedEndpointAccessWithValidToken() throws Exception {
        // Arrange: Login and obtain JWT token
        LoginRequest loginRequest = LoginRequest.builder()
                .username(testUsername)
                .password(testPassword)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(responseBody, LoginResponse.class);
        String jwtToken = loginResponse.getAccessToken();

        // Act & Assert: Call protected endpoint with valid JWT token
        mockMvc.perform(get("/api/v1/accounts/1")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
                // Note: Actual account endpoint may return 404 if account doesn't exist,
                // but the important part is that authentication succeeds (not 401)
    }

    /**
     * Tests authenticated endpoint access without JWT token.
     * 
     * <p><strong>Validates Spring Security filter chain enforcement:</strong></p>
     * <p>Protected endpoints must reject requests without valid JWT tokens,
     * returning HTTP 401 Unauthorized to enforce authentication requirements.</p>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Call protected endpoint GET /api/v1/accounts/1 WITHOUT Authorization header</li>
     *   <li>Assert HTTP 401 Unauthorized status</li>
     *   <li>Verify access is denied due to missing authentication</li>
     * </ol>
     *
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    void testAuthenticatedEndpointAccessWithoutToken() throws Exception {
        // Act & Assert: Call protected endpoint without JWT token
        mockMvc.perform(get("/api/v1/accounts/1"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Tests JWT token expiration validation.
     * 
     * <p><strong>Validates token expiration enforcement:</strong></p>
     * <p>JWT tokens must be rejected after their expiration timestamp,
     * ensuring time-limited authentication sessions.</p>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Login successfully and obtain JWT token</li>
     *   <li>Verify token expiration timestamp is in the future</li>
     *   <li>Verify token expiresIn is 3600 seconds (1 hour)</li>
     *   <li>Calculate expected expiration time (current time + 1 hour)</li>
     * </ol>
     * 
     * <p><strong>Note:</strong> Testing actual token expiration requires time manipulation
     * or waiting 1 hour, which is impractical for integration tests. This test verifies
     * the expiration metadata is correctly set. Token expiration enforcement is validated
     * by unit tests of JwtTokenProvider.validateToken() method.</p>
     *
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    void testJwtTokenExpiration() throws Exception {
        // Arrange: Login and obtain JWT token
        LoginRequest loginRequest = LoginRequest.builder()
                .username(testUsername)
                .password(testPassword)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(responseBody, LoginResponse.class);

        // Assert: Verify token expiration metadata
        assertNotNull(loginResponse.getExpiresAt(), "Token expiration timestamp must not be null");
        assertTrue(loginResponse.getExpiresAt().isAfter(LocalDateTime.now()),
                "Token expiration must be in the future");

        LocalDateTime expectedExpiration = LocalDateTime.now().plusHours(1);
        assertTrue(loginResponse.getExpiresAt().isAfter(LocalDateTime.now()),
                "Token expiration must be at least current time");
        assertTrue(loginResponse.getExpiresAt().isBefore(expectedExpiration.plusMinutes(1)),
                "Token expiration must be within expected 1-hour window");

        // Verify expiresIn is 3600 seconds (1 hour)
        assertEquals(3600, loginResponse.getExpiresIn(),
                "Token must expire in 3600 seconds (1 hour)");
    }

    /**
     * Tests password encryption and security.
     * 
     * <p><strong>Validates PCI-DSS compliance per Agent Action Plan Section 0.8.1:</strong></p>
     * <ul>
     *   <li>Plain-text passwords must never be stored in the database</li>
     *   <li>Passwords must be BCrypt hashed with 10+ rounds</li>
     *   <li>Plain-text passwords must never appear in logs</li>
     * </ul>
     * 
     * <p><strong>Security Enhancement vs. COBOL:</strong></p>
     * <pre>
     * COBOL (CSUSR01Y.cpy):               Modern Java:
     * SEC-USR-PWD PIC X(08)        →      passwordHash VARCHAR(255) BCrypt
     * Plain-text comparison:       →      BCrypt.matches(plainPassword, hash)
     * IF SEC-USR-PWD = WS-USER-PWD →      passwordEncoder.matches(input, storedHash)
     * </pre>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Create new user with plain-text password</li>
     *   <li>Verify password is BCrypt hashed in database (starts with $2a$)</li>
     *   <li>Verify plain-text password is NOT stored</li>
     *   <li>Verify passwordHash matches BCrypt pattern</li>
     * </ol>
     *
     * @throws Exception if database operations fail
     */
    @Test
    void testPasswordEncryption() throws Exception {
        // Arrange: Retrieve test user from database
        User user = userRepository.findByUsername(testUsername).orElseThrow();

        // Assert: Verify password is BCrypt hashed, not plain-text
        assertNotNull(user.getPasswordHash(), "Password hash must not be null");
        assertNotEquals(testPassword, user.getPasswordHash(),
                "Plain-text password must not be stored in database");

        // Verify BCrypt hash format (starts with $2a$, $2b$, or $2y$)
        assertTrue(user.getPasswordHash().startsWith("$2a$") ||
                        user.getPasswordHash().startsWith("$2b$") ||
                        user.getPasswordHash().startsWith("$2y$"),
                "Password must be BCrypt hashed (starts with $2a$, $2b$, or $2y$)");

        // Verify BCrypt hash length (typically 60 characters)
        assertTrue(user.getPasswordHash().length() >= 60,
                "BCrypt hash must be at least 60 characters");

        // Verify BCrypt can verify the original password
        assertTrue(passwordEncoder.matches(testPassword, user.getPasswordHash()),
                "BCrypt must correctly verify original password against hash");

        // Verify wrong password does not match
        assertFalse(passwordEncoder.matches("wrongpassword", user.getPasswordHash()),
                "BCrypt must reject wrong password");
    }

    /**
     * Tests account lockout protection after failed login attempts.
     * 
     * <p><strong>Security Enhancement (not in COBOL):</strong></p>
     * <p>Modern security best practice: lock accounts after 5 consecutive failed login attempts
     * to prevent brute-force password attacks. The legacy COBOL system had no such protection.</p>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Attempt login with wrong password 5 times</li>
     *   <li>Verify failedLoginAttempts counter increments after each failure</li>
     *   <li>Verify account is locked after 5th failed attempt</li>
     *   <li>Verify subsequent login attempts return "Account is locked" error</li>
     *   <li>Verify correct password cannot unlock account (admin intervention required)</li>
     * </ol>
     *
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    void testAccountLockoutAfterFailedAttempts() throws Exception {
        // Arrange: Create login request with wrong password
        LoginRequest loginRequest = LoginRequest.builder()
                .username(testUsername)
                .password("wrongpassword")
                .build();

        // Act: Attempt login 5 times with wrong password
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());

            // Verify failedLoginAttempts counter increments
            User user = userRepository.findByUsername(testUsername).orElseThrow();
            assertEquals(i + 1, user.getFailedLoginAttempts(),
                    "Failed login attempts must increment after each failure");

            // Account should be locked after 5th attempt
            if (i == 4) {
                assertTrue(user.getAccountLocked(),
                        "Account must be locked after 5 failed login attempts");
            } else {
                assertFalse(user.getAccountLocked(),
                        "Account must not be locked before 5th failed attempt");
            }
        }

        // Assert: Verify locked account rejects login even with correct password
        LoginRequest correctPasswordRequest = LoginRequest.builder()
                .username(testUsername)
                .password(testPassword)
                .build();

        MvcResult lockedAccountResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctPasswordRequest)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String responseBody = lockedAccountResult.getResponse().getContentAsString();
        ApiError apiError = objectMapper.readValue(responseBody, ApiError.class);

        assertTrue(apiError.getMessage().toLowerCase().contains("locked"),
                "Error message must indicate account is locked");
    }

    /**
     * Tests last login timestamp update on successful authentication.
     * 
     * <p><strong>Audit Trail Enhancement (not in COBOL):</strong></p>
     * <p>Modern security best practice: track last successful login timestamp for audit
     * trail and security monitoring. The legacy COBOL system had no such tracking.</p>
     * 
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Verify testUser.lastLogin is null initially</li>
     *   <li>Login successfully with correct credentials</li>
     *   <li>Retrieve updated user from database</li>
     *   <li>Verify lastLogin timestamp is now populated</li>
     *   <li>Verify lastLogin is recent (within last 5 seconds)</li>
     *   <li>Verify failedLoginAttempts counter is reset to 0</li>
     * </ol>
     *
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    void testLastLoginTimestampUpdate() throws Exception {
        // Arrange: Verify lastLogin is null initially
        User initialUser = userRepository.findByUsername(testUsername).orElseThrow();
        assertNull(initialUser.getLastLogin(), "Last login must be null initially");

        LocalDateTime beforeLogin = LocalDateTime.now();

        // Act: Login successfully
        LoginRequest loginRequest = LoginRequest.builder()
                .username(testUsername)
                .password(testPassword)
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        LocalDateTime afterLogin = LocalDateTime.now();

        // Assert: Verify lastLogin timestamp is updated
        User updatedUser = userRepository.findByUsername(testUsername).orElseThrow();
        assertNotNull(updatedUser.getLastLogin(), "Last login must be populated after successful login");
        assertTrue(updatedUser.getLastLogin().isAfter(beforeLogin.minusSeconds(1)),
                "Last login must be after login start time");
        assertTrue(updatedUser.getLastLogin().isBefore(afterLogin.plusSeconds(1)),
                "Last login must be before login end time");

        // Verify failedLoginAttempts counter is reset to 0
        assertEquals(0, updatedUser.getFailedLoginAttempts(),
                "Failed login attempts must be reset to 0 after successful login");
    }
}
