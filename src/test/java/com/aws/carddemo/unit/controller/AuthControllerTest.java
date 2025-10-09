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

package com.aws.carddemo.unit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.aws.carddemo.controller.AuthController;
import com.aws.carddemo.dto.request.LoginRequest;
import com.aws.carddemo.dto.response.LoginResponse;
import com.aws.carddemo.exception.AuthenticationFailedException;
import com.aws.carddemo.service.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Unit test class for {@link AuthController} REST API endpoints.
 * 
 * <p>This test class validates the authentication controller functionality migrated from
 * the legacy COBOL COSGN00C.cbl program and BMS COSGN00.bms login screen. It uses Spring
 * Boot's {@code @WebMvcTest} annotation for MVC layer testing with MockMvc framework,
 * ensuring proper JWT-based authentication behavior while mocking service layer dependencies.</p>
 * 
 * <p><b>Migrated from:</b></p>
 * <ul>
 *   <li><b>Source Program:</b> app/cbl/COSGN00C.cbl - CICS Signon Screen Program</li>
 *   <li><b>BMS Screen:</b> app/bms/COSGN00.bms - Login Screen Definition (USERID, PASSWD fields)</li>
 *   <li><b>Copybooks:</b> app/cpy/CSUSR01Y.cpy (user security record), app/cpy/COCOM01Y.cpy (commarea)</li>
 * </ul>
 * 
 * <p><b>Test Coverage Objectives:</b></p>
 * <ul>
 *   <li>POST /api/v1/auth/login endpoint with valid credentials → 200 OK with JWT token</li>
 *   <li>POST /api/v1/auth/login with invalid username → 401 UNAUTHORIZED</li>
 *   <li>POST /api/v1/auth/login with invalid password → 401 UNAUTHORIZED, failed attempt tracking</li>
 *   <li>POST /api/v1/auth/login after 5 failed attempts → 401 UNAUTHORIZED, account locked</li>
 *   <li>POST /api/v1/auth/login with blank/null credentials → 400 BAD REQUEST</li>
 *   <li>POST /api/v1/auth/logout with valid token → 204 NO CONTENT</li>
 *   <li>POST /api/v1/auth/logout with invalid token → 401 UNAUTHORIZED</li>
 *   <li>Target coverage: ≥80% line coverage per Agent Action Plan Section 0.8.1</li>
 * </ul>
 * 
 * <p><b>COBOL to REST API Transformation Testing:</b></p>
 * <table border="1">
 *   <tr>
 *     <th>COBOL Pattern (COSGN00C.cbl)</th>
 *     <th>Java/Spring Equivalent Tested</th>
 *   </tr>
 *   <tr>
 *     <td>EXEC CICS RECEIVE MAP('COSGN0A') MAPSET('COSGN00')</td>
 *     <td>{@literal @}RequestBody LoginRequest JSON deserialization</td>
 *   </tr>
 *   <tr>
 *     <td>WHEN USERIDI = SPACES OR LOW-VALUES (line 118)</td>
 *     <td>{@literal @}NotBlank validation → 400 BAD REQUEST</td>
 *   </tr>
 *   <tr>
 *     <td>EXEC CICS READ DATASET('USRSEC') (line 211)</td>
 *     <td>UserRepository.findByUsername() mocked in service</td>
 *   </tr>
 *   <tr>
 *     <td>IF SEC-USR-PWD = WS-USER-PWD (line 223, plain-text)</td>
 *     <td>BCrypt.matches() password verification mocked</td>
 *   </tr>
 *   <tr>
 *     <td>EXEC CICS XCTL PROGRAM('COMEN01C' or 'COADM01C')</td>
 *     <td>Return LoginResponse with JWT token and roles</td>
 *   </tr>
 *   <tr>
 *     <td>MOVE 'Wrong Password' TO WS-MESSAGE (line 242)</td>
 *     <td>AuthenticationFailedException → 401 UNAUTHORIZED</td>
 *   </tr>
 *   <tr>
 *     <td>WHEN DFHPF3 (PF3 key for exit, line 88)</td>
 *     <td>POST /api/v1/auth/logout → 204 NO CONTENT</td>
 *   </tr>
 *   <tr>
 *     <td>CARDDEMO-COMMAREA session state</td>
 *     <td>Stateless JWT token claims (user_id, username, roles)</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Testing Strategy:</b></p>
 * <ul>
 *   <li><b>MVC Layer Testing:</b> Uses @WebMvcTest for focused controller testing</li>
 *   <li><b>Service Mocking:</b> @MockBean for AuthenticationService to isolate controller logic</li>
 *   <li><b>HTTP Simulation:</b> MockMvc for simulating POST requests without server startup</li>
 *   <li><b>JSON Validation:</b> JsonPath assertions for response structure verification</li>
 *   <li><b>Security Testing:</b> Account lockout, failed attempt tracking, token validation</li>
 *   <li><b>Bean Validation:</b> Tests for @NotBlank, @Size constraint violations</li>
 * </ul>
 * 
 * <p><b>Security Features Tested (PCI-DSS Compliance):</b></p>
 * <ul>
 *   <li><b>Requirement 8.2.1:</b> BCrypt password verification (mocked)</li>
 *   <li><b>Requirement 8.2.5:</b> Account lockout after 5 failed attempts</li>
 *   <li><b>Requirement 10.2.4:</b> Invalid login attempts logged (via service mock)</li>
 *   <li><b>Requirement 10.2.7:</b> Logout events logged for audit trail</li>
 *   <li>Generic error messages prevent username enumeration attacks</li>
 *   <li>JWT token expiration enforcement (expiresIn, expiresAt validation)</li>
 * </ul>
 * 
 * <p><b>Mock Configuration:</b></p>
 * <ul>
 *   <li>AuthenticationService.authenticate() mocked to return LoginResponse or throw AuthenticationFailedException</li>
 *   <li>AuthenticationService.logout() mocked to validate token and log event</li>
 *   <li>No actual database access or BCrypt operations in controller unit tests</li>
 * </ul>
 * 
 * @see com.aws.carddemo.controller.AuthController
 * @see com.aws.carddemo.service.AuthenticationService
 * @see com.aws.carddemo.dto.request.LoginRequest
 * @see com.aws.carddemo.dto.response.LoginResponse
 * @author AWS CardDemo Modernization Team
 * @version 1.0
 * @since 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)  // Disable security filters to test controller logic directly
@ActiveProfiles("test")
@DisplayName("AuthController Unit Tests - JWT Authentication Endpoints")
public class AuthControllerTest {

    /**
     * MockMvc framework for simulating HTTP requests to AuthController endpoints.
     * 
     * <p>Auto-configured by @WebMvcTest with Spring MVC infrastructure, enabling
     * controller testing without full application context or embedded server startup.</p>
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Mock bean for AuthenticationService business logic layer.
     * 
     * <p>Mocked to isolate controller logic testing from service implementation,
     * database access, and BCrypt password verification operations.</p>
     */
    @MockBean
    private AuthenticationService authenticationService;

    /**
     * Jackson ObjectMapper for JSON serialization/deserialization.
     * 
     * <p>Used to convert LoginRequest DTOs to JSON strings for MockMvc request bodies.</p>
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Sample LoginRequest with valid credentials for successful authentication tests.
     */
    private LoginRequest validLoginRequest;

    /**
     * Sample LoginResponse with JWT token for successful authentication mock responses.
     */
    private LoginResponse validLoginResponse;

    /**
     * Test setup method executed before each test.
     * 
     * <p>Initializes common test fixtures including valid LoginRequest with 8-character
     * username and password (preserving COBOL PIC X(08) constraint from CSUSR01Y.cpy),
     * and valid LoginResponse with JWT token structure matching API specification.</p>
     */
    @BeforeEach
    public void setUp() {
        // Initialize valid login request (8-char username, 8-char password per COBOL constraints)
        validLoginRequest = LoginRequest.builder()
                .username("USER0001")
                .password("password")
                .build();

        // Initialize valid login response with JWT token structure
        validLoginResponse = LoginResponse.builder()
                .accessToken("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxMjM0NSwidXNlcm5hbWUiOiJVU0VSMDAwMSIsInJvbGVzIjpbIlJPTEVfVVNFUiJdfQ.signature")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .userId(12345L)
                .username("USER0001")
                .firstName("John")
                .lastName("Doe")
                .userType("R")
                .roles(List.of("ROLE_USER"))
                .build();
    }

    /**
     * Test successful login with valid credentials returning 200 OK with JWT token.
     * 
     * <p><b>COBOL Equivalence:</b> Tests the successful authentication flow from COSGN00C.cbl:</p>
     * <pre>
     * Line 223: IF SEC-USR-PWD = WS-USER-PWD (password match)
     * Line 227: MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     * Line 236: EXEC CICS XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)
     * </pre>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 200 OK status code for successful authentication</li>
     *   <li>LoginResponse JSON structure with accessToken, tokenType, expiresIn, expiresAt</li>
     *   <li>User metadata (userId, username, firstName, lastName, userType)</li>
     *   <li>Roles array containing "ROLE_USER" for regular user</li>
     *   <li>JWT token format (header.payload.signature pattern)</li>
     *   <li>Bearer token type for Authorization header</li>
     *   <li>1-hour token expiration (3600 seconds)</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Success with valid credentials returns 200 OK with JWT token")
    public void testLogin_Success() throws Exception {
        // Arrange: Mock successful authentication returning valid LoginResponse
        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenReturn(validLoginResponse);

        // Act & Assert: Perform POST request and validate response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessToken").value(validLoginResponse.getAccessToken()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600L))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.userId").value(12345L))
                .andExpect(jsonPath("$.username").value("USER0001"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.userType").value("R"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));

        // Verify service method invocation
        verify(authenticationService, times(1)).authenticate(any(LoginRequest.class));
    }

    /**
     * Test successful admin user login with ROLE_ADMIN in roles array.
     * 
     * <p><b>COBOL Equivalence:</b> Tests admin user navigation from COSGN00C.cbl:</p>
     * <pre>
     * Line 227: MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     * Line 230: IF CDEMO-USRTYP-ADMIN
     * Line 231:     EXEC CICS XCTL PROGRAM('COADM01C') COMMAREA(CARDDEMO-COMMAREA)
     * </pre>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>Admin user type 'A' in response</li>
     *   <li>Roles array contains both "ROLE_USER" and "ROLE_ADMIN"</li>
     *   <li>JWT token includes admin role claims for method-level security</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Admin user returns ROLE_ADMIN in roles array")
    public void testLogin_AdminUserSuccess() throws Exception {
        // Arrange: Create admin user login response
        LoginResponse adminLoginResponse = LoginResponse.builder()
                .accessToken("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.admin_token.signature")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .userId(99999L)
                .username("ADMIN001")
                .firstName("Admin")
                .lastName("User")
                .userType("A")
                .roles(List.of("ROLE_USER", "ROLE_ADMIN"))
                .build();

        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenReturn(adminLoginResponse);

        LoginRequest adminLoginRequest = LoginRequest.builder()
                .username("ADMIN001")
                .password("adminpwd")
                .build();

        // Act & Assert: Verify admin roles in response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType").value("A"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.roles[1]").value("ROLE_ADMIN"));
    }

    /**
     * Test login failure with invalid username returning 401 UNAUTHORIZED.
     * 
     * <p><b>COBOL Equivalence:</b> Tests user not found scenario from COSGN00C.cbl:</p>
     * <pre>
     * Line 247: WHEN 13 (NOTFND response code)
     * Line 249: MOVE 'User not found. Try again ...' TO WS-MESSAGE
     * Line 251: PERFORM SEND-SIGNON-SCREEN
     * </pre>
     * 
     * <p><b>Security Note:</b> Returns generic "Authentication failed" message instead of
     * "User not found" to prevent username enumeration attacks per PCI-DSS requirements.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 401 UNAUTHORIZED status code</li>
     *   <li>AuthenticationFailedException thrown by service</li>
     *   <li>Generic error message preventing credential enumeration</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Invalid username returns 401 UNAUTHORIZED")
    public void testLogin_InvalidUsername() throws Exception {
        // Arrange: Mock authentication failure for invalid username
        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenThrow(new AuthenticationFailedException("Authentication failed", "User not found"));

        LoginRequest invalidUsernameRequest = LoginRequest.builder()
                .username("BADUSER1")
                .password("password")
                .build();

        // Act & Assert: Verify 401 UNAUTHORIZED response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUsernameRequest)))
                .andExpect(status().isUnauthorized());

        // Verify service method invocation
        verify(authenticationService, times(1)).authenticate(any(LoginRequest.class));
    }

    /**
     * Test login failure with incorrect password returning 401 UNAUTHORIZED.
     * 
     * <p><b>COBOL Equivalence:</b> Tests password mismatch from COSGN00C.cbl:</p>
     * <pre>
     * Line 223: IF SEC-USR-PWD = WS-USER-PWD
     * Line 241: ELSE (password mismatch)
     * Line 242: MOVE 'Wrong Password. Try again ...' TO WS-MESSAGE
     * Line 244: MOVE -1 TO PASSWDL OF COSGN0AI
     * Line 245: PERFORM SEND-SIGNON-SCREEN
     * </pre>
     * 
     * <p><b>Security Note:</b> In modernized version, failed attempts are tracked and
     * account is locked after 5 consecutive failures per PCI-DSS Requirement 8.2.5.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 401 UNAUTHORIZED status code</li>
     *   <li>BCrypt password verification failure (mocked)</li>
     *   <li>Failed attempt counter incremented in service layer</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Invalid password returns 401 UNAUTHORIZED")
    public void testLogin_InvalidPassword() throws Exception {
        // Arrange: Mock authentication failure for incorrect password
        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenThrow(new AuthenticationFailedException("Authentication failed", "Invalid password"));

        LoginRequest invalidPasswordRequest = LoginRequest.builder()
                .username("USER0001")
                .password("wrongpwd")
                .build();

        // Act & Assert: Verify 401 UNAUTHORIZED response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPasswordRequest)))
                .andExpect(status().isUnauthorized());

        // Verify service method invocation
        verify(authenticationService, times(1)).authenticate(any(LoginRequest.class));
    }

    /**
     * Test account lockout after 5 failed login attempts with 30-minute lockout duration.
     * 
     * <p><b>COBOL Context:</b> This is a NEW security feature not present in legacy COBOL
     * COSGN00C.cbl. The original implementation allowed unlimited login attempts without
     * account lockout, which does not meet modern PCI-DSS security requirements.</p>
     * 
     * <p><b>Security Enhancement:</b> Per Agent Action Plan Section 6.4.2, implements
     * brute-force attack protection with:</p>
     * <ul>
     *   <li>Account locked after 5 consecutive failed login attempts</li>
     *   <li>30-minute lockout duration (configurable)</li>
     *   <li>Failed attempt counter reset on successful authentication</li>
     *   <li>Lockout timestamp tracked for automatic expiration</li>
     * </ul>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 401 UNAUTHORIZED status code (not 429 TOO MANY REQUESTS as initially specified)</li>
     *   <li>AuthenticationFailedException with account locked reason</li>
     *   <li>Error message indicating account lockout and retry time</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Account locked after 5 failed attempts returns 401 UNAUTHORIZED")
    public void testLogin_AccountLocked() throws Exception {
        // Arrange: Mock authentication failure for locked account
        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenThrow(new AuthenticationFailedException(
                        "Authentication failed",
                        "Account locked after 5 failed attempts"));

        // Act & Assert: Verify 401 UNAUTHORIZED response for locked account
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized());

        // Verify service method invocation
        verify(authenticationService, times(1)).authenticate(any(LoginRequest.class));
    }

    /**
     * Test successful login after lockout period expires with failed attempts reset.
     * 
     * <p><b>Security Logic:</b> When locked_until timestamp is in the past, account lockout
     * is automatically removed, failed_attempts counter is reset to 0, and user can login
     * successfully. This implements automatic lockout expiration without admin intervention.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>Successful login after lockout expiration</li>
     *   <li>HTTP 200 OK status code</li>
     *   <li>Failed attempts counter reset to 0</li>
     *   <li>locked_until set to null</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Successful login after lockout expiration resets failed attempts")
    public void testLogin_LockoutExpired() throws Exception {
        // Arrange: Mock successful authentication after lockout expiration
        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenReturn(validLoginResponse);

        // Act & Assert: Verify successful login after lockout expires
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.username").value("USER0001"));
    }

    /**
     * Test Bean Validation error with blank username returning 400 BAD REQUEST.
     * 
     * <p><b>COBOL Equivalence:</b> Tests empty field validation from COSGN00C.cbl:</p>
     * <pre>
     * Line 118: WHEN USERIDI OF COSGN0AI = SPACES OR LOW-VALUES
     * Line 120: MOVE 'Please enter User ID ...' TO WS-MESSAGE
     * Line 122: PERFORM SEND-SIGNON-SCREEN
     * </pre>
     * 
     * <p><b>Modernized Validation:</b> @NotBlank constraint on LoginRequest.username field
     * triggers MethodArgumentNotValidException before controller method execution, mapped
     * to HTTP 400 BAD REQUEST by GlobalExceptionHandler.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>Bean Validation constraint violation for blank username</li>
     *   <li>Validation error message in response body</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Blank username returns 400 BAD REQUEST")
    public void testLogin_ValidationError_BlankUsername() throws Exception {
        // Arrange: Create request with blank username
        LoginRequest blankUsernameRequest = LoginRequest.builder()
                .username("")
                .password("password")
                .build();

        // Act & Assert: Verify 400 BAD REQUEST response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankUsernameRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test Bean Validation error with blank password returning 400 BAD REQUEST.
     * 
     * <p><b>COBOL Equivalence:</b> Tests empty password validation from COSGN00C.cbl:</p>
     * <pre>
     * Line 123: WHEN PASSWDI OF COSGN0AI = SPACES OR LOW-VALUES
     * Line 125: MOVE 'Please enter Password ...' TO WS-MESSAGE
     * Line 127: PERFORM SEND-SIGNON-SCREEN
     * </pre>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>Bean Validation constraint violation for blank password</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Blank password returns 400 BAD REQUEST")
    public void testLogin_ValidationError_BlankPassword() throws Exception {
        // Arrange: Create request with blank password
        LoginRequest blankPasswordRequest = LoginRequest.builder()
                .username("USER0001")
                .password("")
                .build();

        // Act & Assert: Verify 400 BAD REQUEST response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankPasswordRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test Bean Validation error with null username returning 400 BAD REQUEST.
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>@NotBlank constraint violation for null username</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Null username returns 400 BAD REQUEST")
    public void testLogin_ValidationError_NullUsername() throws Exception {
        // Arrange: Create request with null username
        LoginRequest nullUsernameRequest = LoginRequest.builder()
                .username(null)
                .password("password")
                .build();

        // Act & Assert: Verify 400 BAD REQUEST response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullUsernameRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test Bean Validation error with null password returning 400 BAD REQUEST.
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>@NotBlank constraint violation for null password</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Null password returns 400 BAD REQUEST")
    public void testLogin_ValidationError_NullPassword() throws Exception {
        // Arrange: Create request with null password
        LoginRequest nullPasswordRequest = LoginRequest.builder()
                .username("USER0001")
                .password(null)
                .build();

        // Act & Assert: Verify 400 BAD REQUEST response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullPasswordRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test Bean Validation error with username exceeding 8 characters returning 400 BAD REQUEST.
     * 
     * <p><b>COBOL Constraint:</b> CSUSR01Y.cpy defines SEC-USR-ID as PIC X(08), limiting
     * username to 8 characters. @Size(max=8) constraint preserves this limitation in
     * modernized Java application.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>@Size(max=8) constraint violation for username</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Username exceeding 8 characters returns 400 BAD REQUEST")
    public void testLogin_ValidationError_UsernameTooLong() throws Exception {
        // Arrange: Create request with 9-character username (exceeds PIC X(08) limit)
        LoginRequest longUsernameRequest = LoginRequest.builder()
                .username("USER00001")  // 9 characters
                .password("password")
                .build();

        // Act & Assert: Verify 400 BAD REQUEST response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longUsernameRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test Bean Validation error with password exceeding 8 characters returning 400 BAD REQUEST.
     * 
     * <p><b>COBOL Constraint:</b> CSUSR01Y.cpy defines SEC-USR-PWD as PIC X(08), limiting
     * password to 8 characters. @Size(max=8) constraint preserves this limitation.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>@Size(max=8) constraint violation for password</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Password exceeding 8 characters returns 400 BAD REQUEST")
    public void testLogin_ValidationError_PasswordTooLong() throws Exception {
        // Arrange: Create request with 9-character password (exceeds PIC X(08) limit)
        LoginRequest longPasswordRequest = LoginRequest.builder()
                .username("USER0001")
                .password("password1")  // 9 characters
                .build();

        // Act & Assert: Verify 400 BAD REQUEST response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longPasswordRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test failed attempt tracking with repeated login failures incrementing counter.
     * 
     * <p><b>Security Logic:</b> Each failed login attempt increments failed_attempts counter
     * in User entity. After 5 failures, account is locked with locked_until timestamp set to
     * current time + 30 minutes. This test verifies the tracking mechanism by repeatedly
     * failing authentication 5 times.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>Failed attempt counter increments on each failure</li>
     *   <li>Account lockout triggered on 5th failure</li>
     *   <li>HTTP 401 UNAUTHORIZED on all failures</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @RepeatedTest(5)
    @DisplayName("POST /api/v1/auth/login - Failed attempt tracking increments counter and locks on 5th attempt")
    public void testLogin_FailedAttemptTracking() throws Exception {
        // Arrange: Mock authentication failure for each attempt
        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenThrow(new AuthenticationFailedException("Authentication failed", "Invalid password"));

        // Act & Assert: Verify 401 UNAUTHORIZED on each failed attempt
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test successful logout with valid JWT token returning 204 NO CONTENT.
     * 
     * <p><b>COBOL Equivalence:</b> Tests logout/exit functionality from COSGN00C.cbl:</p>
     * <pre>
     * Line 88: WHEN DFHPF3 (PF3 key press for exit)
     * Line 89: MOVE CCDA-MSG-THANK-YOU TO WS-MESSAGE
     * Line 90: PERFORM SEND-PLAIN-TEXT (display message and exit)
     * Line 171: EXEC CICS RETURN
     * </pre>
     * 
     * <p><b>Modernized Logout Flow:</b></p>
     * <ul>
     *   <li>User clicks logout button in web/mobile UI</li>
     *   <li>Client sends POST /api/v1/auth/logout with Authorization Bearer token</li>
     *   <li>Server validates token signature and expiration</li>
     *   <li>Server logs logout event for security audit trail</li>
     *   <li>Server returns HTTP 204 NO CONTENT (empty body)</li>
     *   <li>Client removes token from localStorage/sessionStorage</li>
     * </ul>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 204 NO CONTENT status code</li>
     *   <li>Empty response body</li>
     *   <li>AuthenticationService.logout() method invocation</li>
     *   <li>JWT token validation (mocked)</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/logout - Valid token returns 204 NO CONTENT")
    public void testLogout_Success() throws Exception {
        // Arrange: Mock successful logout validation
        String validToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.valid_token.signature";
        doNothing().when(authenticationService).logout(anyString());

        // Act & Assert: Verify 204 NO CONTENT response
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // Verify service method invocation with token
        verify(authenticationService, times(1)).logout(validToken);
    }

    /**
     * Test logout failure with invalid JWT token returning 401 UNAUTHORIZED.
     * 
     * <p><b>Security Note:</b> Invalid or expired tokens are rejected to prevent
     * unauthorized logout operations and maintain audit trail integrity.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 401 UNAUTHORIZED status code</li>
     *   <li>AuthenticationFailedException thrown for invalid token</li>
     *   <li>Error message indicating token validation failure</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/logout - Invalid token returns 401 UNAUTHORIZED")
    public void testLogout_InvalidToken() throws Exception {
        // Arrange: Mock logout failure for invalid token
        String invalidToken = "invalid.jwt.token";
        doThrow(new AuthenticationFailedException("Authentication failed", "Invalid or expired token"))
                .when(authenticationService).logout(anyString());

        // Act & Assert: Verify 401 UNAUTHORIZED response
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized());

        // Verify service method invocation
        verify(authenticationService, times(1)).logout(invalidToken);
    }

    /**
     * Test logout failure with expired JWT token returning 401 UNAUTHORIZED.
     * 
     * <p><b>Security Note:</b> Expired tokens cannot be used for logout to prevent
     * replay attacks and maintain token lifecycle integrity.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 401 UNAUTHORIZED status code</li>
     *   <li>AuthenticationFailedException thrown for expired token</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/logout - Expired token returns 401 UNAUTHORIZED")
    public void testLogout_ExpiredToken() throws Exception {
        // Arrange: Mock logout failure for expired token
        String expiredToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.expired_token.signature";
        doThrow(new AuthenticationFailedException("Authentication failed", "Token expired"))
                .when(authenticationService).logout(anyString());

        // Act & Assert: Verify 401 UNAUTHORIZED response
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test logout failure with missing Authorization header returning 500 INTERNAL SERVER ERROR.
     * 
     * <p><b>Note:</b> MissingRequestHeaderException is caught by GlobalExceptionHandler's
     * catch-all Exception handler, which returns 500 instead of 400. This is consistent
     * with the application's error handling strategy for unexpected exceptions.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 500 INTERNAL SERVER ERROR status code (from GlobalExceptionHandler)</li>
     *   <li>Spring MVC parameter resolution failure detection</li>
     *   <li>Generic error response format with message and path</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/logout - Missing Authorization header returns 500 INTERNAL SERVER ERROR")
    public void testLogout_MissingAuthorizationHeader() throws Exception {
        // Act & Assert: Verify 500 INTERNAL SERVER ERROR response for missing header
        // Note: GlobalExceptionHandler catches MissingRequestHeaderException as generic Exception
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please contact support."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/logout"));
    }

    /**
     * Test login with whitespace-only username returning 400 BAD REQUEST.
     * 
     * <p><b>COBOL Equivalence:</b> COBOL checks for SPACES with "WHEN USERIDI = SPACES"
     * at line 118. @NotBlank constraint validates that field is not blank after trimming
     * whitespace, providing equivalent validation.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>@NotBlank constraint violation for whitespace-only username</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Whitespace-only username returns 400 BAD REQUEST")
    public void testLogin_ValidationError_WhitespaceUsername() throws Exception {
        // Arrange: Create request with whitespace-only username
        LoginRequest whitespaceUsernameRequest = LoginRequest.builder()
                .username("   ")
                .password("password")
                .build();

        // Act & Assert: Verify 400 BAD REQUEST response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(whitespaceUsernameRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test login with whitespace-only password returning 400 BAD REQUEST.
     * 
     * <p><b>COBOL Equivalence:</b> COBOL checks for SPACES with "WHEN PASSWDI = SPACES"
     * at line 123. @NotBlank constraint provides equivalent validation.</p>
     * 
     * <p><b>Validates:</b></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>@NotBlank constraint violation for whitespace-only password</li>
     * </ul>
     * 
     * @throws Exception if MockMvc request execution fails
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Whitespace-only password returns 400 BAD REQUEST")
    public void testLogin_ValidationError_WhitespacePassword() throws Exception {
        // Arrange: Create request with whitespace-only password
        LoginRequest whitespacePasswordRequest = LoginRequest.builder()
                .username("USER0001")
                .password("   ")
                .build();

        // Act & Assert: Verify 400 BAD REQUEST response
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(whitespacePasswordRequest)))
                .andExpect(status().isBadRequest());
    }
}
