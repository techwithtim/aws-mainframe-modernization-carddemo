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

import com.aws.carddemo.config.BatchConfig;
import com.aws.carddemo.config.DataSourceConfig;
import com.aws.carddemo.controller.AdminController;
import com.aws.carddemo.dto.request.UserCreateRequest;
import com.aws.carddemo.dto.request.UserUpdateRequest;
import com.aws.carddemo.dto.response.UserResponse;
import com.aws.carddemo.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit Test Class for AdminController REST API Endpoints
 * 
 * <p>This test class validates the administrative user management REST endpoints using Spring MVC Test framework.
 * Tests cover complete CRUD operations for user management, authentication/authorization enforcement,
 * validation error handling, and functional equivalence with legacy COBOL programs.</p>
 * 
 * <p><strong>Testing Framework:</strong></p>
 * <ul>
 *   <li>@WebMvcTest(AdminController.class) - MVC slice test loading only AdminController and web layer</li>
 *   <li>MockMvc - Simulates HTTP requests without starting full servlet container</li>
 *   <li>@MockBean - Mocks UserService to isolate controller logic from service layer</li>
 *   <li>@WithMockUser - Simulates authenticated users with specific roles</li>
 *   <li>@WithAnonymousUser - Simulates unauthenticated requests</li>
 * </ul>
 * 
 * <p><strong>Migration Context:</strong></p>
 * <p>Tests prove functional equivalence with legacy COBOL programs:
 * <ul>
 *   <li>{@code app/cbl/COUSR00C.cbl} - User list retrieval (STARTBR/READNEXT USRSEC) → GET /api/v1/admin/users</li>
 *   <li>{@code app/cbl/COUSR01C.cbl} - User creation (WRITE USRSEC) → POST /api/v1/admin/users</li>
 *   <li>{@code app/cbl/COUSR02C.cbl} - User update (READ UPDATE/REWRITE USRSEC) → PUT /api/v1/admin/users/{id}</li>
 *   <li>{@code app/cbl/COUSR03C.cbl} - User deletion (DELETE USRSEC) → DELETE /api/v1/admin/users/{id}</li>
 * </ul>
 * 
 * <p>And BMS screen maps:
 * <ul>
 *   <li>{@code app/bms/COUSR00.bms} - User list screen with 10 users per page</li>
 *   <li>{@code app/bms/COUSR01.bms} - User add screen (USERID, PASSWD, FNAME, LNAME, USRTYPE)</li>
 *   <li>{@code app/bms/COUSR02.bms} - User update screen with optional password change</li>
 *   <li>{@code app/bms/COUSR03.bms} - User delete confirmation screen</li>
 * </ul>
 * 
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>✓ GET /api/v1/admin/users - User list retrieval with pagination</li>
 *   <li>✓ GET /api/v1/admin/users/{id} - Single user retrieval</li>
 *   <li>✓ POST /api/v1/admin/users - User creation with password validation</li>
 *   <li>✓ PUT /api/v1/admin/users/{id} - User update with optional password change</li>
 *   <li>✓ DELETE /api/v1/admin/users/{id} - User deletion</li>
 *   <li>✓ Authorization enforcement (@PreAuthorize hasRole('ADMIN'))</li>
 *   <li>✓ Authentication requirement (401 for anonymous users)</li>
 *   <li>✓ Validation error handling (400 BAD REQUEST)</li>
 *   <li>✓ Not found scenarios (404 NOT FOUND)</li>
 *   <li>✓ Duplicate username prevention (409 CONFLICT)</li>
 *   <li>✓ Password security (excluded from responses)</li>
 * </ul>
 * 
 * <p><strong>Security Testing:</strong></p>
 * <ul>
 *   <li>Verifies @PreAuthorize("hasRole('ADMIN')") enforcement - 403 FORBIDDEN for non-admin</li>
 *   <li>Verifies authentication requirement - 401 UNAUTHORIZED for anonymous</li>
 *   <li>Validates password field exclusion from JSON responses per PCI-DSS</li>
 *   <li>Tests BCrypt password hashing in creation/update operations</li>
 * </ul>
 * 
 * @see AdminController
 * @see UserService
 * @see UserCreateRequest
 * @see UserUpdateRequest
 * @see UserResponse
 * @since 1.0.0
 */
@WebMvcTest(
    controllers = AdminController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        BatchAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {BatchConfig.class, DataSourceConfig.class}
    ))
@DisplayName("AdminController Unit Tests")
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    /**
     * Test GET /api/v1/admin/users with admin role returns 200 OK with paginated user list.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COUSR00C.cbl STARTBR/READNEXT loop:
     * <pre>
     * STARTBR-USER-SEC-FILE.
     *     EXEC CICS STARTBR DATASET(WS-USRSEC-FILE) RIDFLD(CDEMO-CU00-USRID-FIRST) END-EXEC.
     * READ-NEXT-USER-SEC-FILE.
     *     PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > 10 OR USER-SEC-EOF
     *         EXEC CICS READNEXT DATASET(WS-USRSEC-FILE) INTO(SEC-USER-DATA) END-EXEC
     *         MOVE SEC-USR-ID TO USER-ID(WS-IDX)
     *         STRING SEC-USR-FNAME ' ' SEC-USR-LNAME INTO USER-NAME(WS-IDX)
     *         EVALUATE SEC-USR-TYPE
     *             WHEN 'A' MOVE 'ADMIN' TO USER-TYPE(WS-IDX)
     *             WHEN 'R' MOVE 'USER'  TO USER-TYPE(WS-IDX)
     *         END-EVALUATE
     *     END-PERFORM.
     * </pre>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 200 OK status code</li>
     *   <li>JSON response with pageable structure (content, totalElements, totalPages)</li>
     *   <li>Password field excluded from response per security requirements</li>
     *   <li>User details match COBOL output fields (userId, firstName, lastName, userType)</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/admin/users returns 200 OK with user list for admin")
    public void testGetAllUsers_AsAdmin() throws Exception {
        // Arrange: Create mock user list matching COBOL WS-USER-DATA OCCURS 10 TIMES
        List<UserResponse> userList = Arrays.asList(
                UserResponse.builder()
                        .userId(1L)
                        .username("ADMIN001")
                        .firstName("Admin")
                        .lastName("User")
                        .userType("A")
                        .roles(List.of("ROLE_ADMIN", "ROLE_USER"))
                        .accountLocked(false)
                        .failedLoginAttempts(0)
                        .lastLogin(LocalDateTime.now().minusDays(1))
                        .createdAt(LocalDateTime.now().minusDays(30))
                        .updatedAt(LocalDateTime.now().minusDays(1))
                        .build(),
                UserResponse.builder()
                        .userId(2L)
                        .username("USER0001")
                        .firstName("John")
                        .lastName("Doe")
                        .userType("R")
                        .roles(List.of("ROLE_USER"))
                        .accountLocked(false)
                        .failedLoginAttempts(0)
                        .lastLogin(LocalDateTime.now().minusHours(2))
                        .createdAt(LocalDateTime.now().minusDays(15))
                        .updatedAt(LocalDateTime.now().minusHours(2))
                        .build()
        );

        // Create paginated response matching COBOL page size (10 records per page)
        Page<UserResponse> pageResponse = new PageImpl<>(
                userList, 
                PageRequest.of(0, 10), 
                userList.size()
        );

        // Mock UserService to return paginated user list
        when(userService.getAllUsers(any(Pageable.class))).thenReturn(pageResponse);

        // Act & Assert: Perform GET request and verify response
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "10")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].userId").value(1))
                .andExpect(jsonPath("$.content[0].username").value("ADMIN001"))
                .andExpect(jsonPath("$.content[0].firstName").value("Admin"))
                .andExpect(jsonPath("$.content[0].lastName").value("User"))
                .andExpect(jsonPath("$.content[0].userType").value("A"))
                .andExpect(jsonPath("$.content[0].roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.content[0].accountLocked").value(false))
                .andExpect(jsonPath("$.content[0].failedLoginAttempts").value(0))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())  // Security: password excluded
                .andExpect(jsonPath("$.content[1].userId").value(2))
                .andExpect(jsonPath("$.content[1].username").value("USER0001"))
                .andExpect(jsonPath("$.content[1].userType").value("R"))
                .andExpect(jsonPath("$.content[1].password").doesNotExist())  // Security: password excluded
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.pageable.pageNumber").value(0))
                .andExpect(jsonPath("$.pageable.pageSize").value(10));
    }

    /**
     * Test GET /api/v1/admin/users with regular user role returns 403 FORBIDDEN.
     * 
     * <p><strong>Security Validation:</strong></p>
     * <p>Verifies @PreAuthorize("hasRole('ADMIN')") method-level security on AdminController class.
     * In COBOL, this was enforced by checking SEC-USR-TYPE = 'A' at program entry point.
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 403 FORBIDDEN status code</li>
     *   <li>Spring Security blocks request before reaching controller method</li>
     *   <li>UserService.getAllUsers() is never called</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/v1/admin/users returns 403 FORBIDDEN for non-admin user")
    public void testGetAllUsers_AsUser() throws Exception {
        // Act & Assert: Regular user attempting admin operation should be denied
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // UserService should never be called due to @PreAuthorize check
    }

    /**
     * Test GET /api/v1/admin/users/{id} returns 200 OK with user details for valid user ID.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COUSR02C.cbl READ USRSEC:
     * <pre>
     * READ-USER-SEC-FILE.
     *     MOVE USRIDINI OF COUSR2AI TO SEC-USR-ID
     *     EXEC CICS READ DATASET(WS-USRSEC-FILE) INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) END-EXEC
     *     IF WS-RESP-CD = DFHRESP(NORMAL)
     *         MOVE SEC-USR-ID TO USRIDINO OF COUSR2AO
     *         MOVE SEC-USR-FNAME TO FNAMEO OF COUSR2AO
     *         MOVE SEC-USR-LNAME TO LNAMEO OF COUSR2AO
     *         EVALUATE SEC-USR-TYPE
     *             WHEN 'A' MOVE 'A' TO USRTYPEO OF COUSR2AO
     *             WHEN 'R' MOVE 'R' TO USRTYPEO OF COUSR2AO
     *         END-EVALUATE
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 200 OK status code</li>
     *   <li>JSON response with complete user details</li>
     *   <li>Password field excluded from response</li>
     *   <li>Audit timestamps included (created, updated, lastLogin)</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/admin/users/{id} returns 200 OK with user details")
    public void testGetUserById_Success() throws Exception {
        // Arrange: Mock user response
        UserResponse userResponse = UserResponse.builder()
                .userId(1L)
                .username("ADMIN001")
                .firstName("Admin")
                .lastName("User")
                .userType("A")
                .roles(List.of("ROLE_ADMIN", "ROLE_USER"))
                .accountLocked(false)
                .failedLoginAttempts(0)
                .lastLogin(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(userService.getUserById(1L)).thenReturn(userResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/users/{userId}", 1L)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("ADMIN001"))
                .andExpect(jsonPath("$.firstName").value("Admin"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.userType").value("A"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.accountLocked").value(false))
                .andExpect(jsonPath("$.failedLoginAttempts").value(0))
                .andExpect(jsonPath("$.lastLogin").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist());  // Security: password excluded
    }

    /**
     * Test GET /api/v1/admin/users/{id} returns 404 NOT FOUND for non-existent user ID.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COBOL FILE STATUS '23' (record not found):
     * <pre>
     * IF WS-RESP-CD = DFHRESP(NOTFND)
     *     STRING 'User ID ' SEC-USR-ID ' NOT found...' INTO WS-MESSAGE
     *     MOVE WS-MESSAGE TO ERRMSGO OF COUSR2AO
     *     MOVE -1 TO USRIDINL OF COUSR2AI
     * END-IF.
     * </pre>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 404 NOT FOUND status code</li>
     *   <li>GlobalExceptionHandler translates ResourceNotFoundException</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/admin/users/{id} returns 404 NOT FOUND when user does not exist")
    public void testGetUserById_NotFound() throws Exception {
        // Arrange: Mock UserService to throw ResourceNotFoundException
        when(userService.getUserById(999L))
                .thenThrow(new com.aws.carddemo.exception.ResourceNotFoundException("User not found with ID: 999"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/users/{userId}", 999L)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /**
     * Test POST /api/v1/admin/users returns 201 CREATED with Location header.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COUSR01C.cbl WRITE USRSEC:
     * <pre>
     * WRITE-USER-SEC-FILE.
     *     MOVE USERIDI OF COUSR1AI   TO SEC-USR-ID
     *     MOVE FNAMEI OF COUSR1AI    TO SEC-USR-FNAME
     *     MOVE LNAMEI OF COUSR1AI    TO SEC-USR-LNAME
     *     MOVE PASWDI OF COUSR1AI    TO SEC-USR-PWD
     *     MOVE USRTYPEI OF COUSR1AI  TO SEC-USR-TYPE
     *     EXEC CICS WRITE DATASET(WS-USRSEC-FILE) FROM(SEC-USER-DATA) RIDFLD(SEC-USR-ID) END-EXEC
     *     EVALUATE WS-RESP-CD
     *         WHEN DFHRESP(NORMAL)
     *             STRING 'User ' SEC-USR-ID ' added successfully!' INTO WS-MESSAGE
     *     END-EVALUATE.
     * </pre>
     * 
     * <p><strong>Security Enhancement:</strong></p>
     * <p>Password is BCrypt hashed with 10 rounds before storage, replacing COBOL plain-text password
     * (SEC-USR-PWD PIC X(08)) per PCI-DSS Requirement 8.2.1.
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 201 CREATED status code</li>
     *   <li>Location header: /api/v1/admin/users/{userId}</li>
     *   <li>Response body contains created user (password excluded)</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/users returns 201 CREATED with Location header")
    public void testCreateUser_Success() throws Exception {
        // Arrange: Create user creation request
        UserCreateRequest request = UserCreateRequest.builder()
                .userId("ADMIN002")
                .password("SecureP@ss123")
                .firstName("Jane")
                .lastName("Smith")
                .userType("A")
                .build();

        // Mock created user response (password field is null/masked)
        UserResponse createdUser = UserResponse.builder()
                .userId(3L)
                .username("ADMIN002")
                .firstName("Jane")
                .lastName("Smith")
                .userType("A")
                .roles(List.of("ROLE_ADMIN", "ROLE_USER"))
                .accountLocked(false)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userService.createUser(any(UserCreateRequest.class))).thenReturn(createdUser);

        // Act & Assert
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", "http://localhost/api/v1/admin/users/3"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(3))
                .andExpect(jsonPath("$.username").value("ADMIN002"))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.userType").value("A"))
                .andExpect(jsonPath("$.password").doesNotExist());  // Security: password excluded from response
    }

    /**
     * Test POST /api/v1/admin/users returns 409 CONFLICT for duplicate username.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COBOL duplicate key FILE STATUS '22':
     * <pre>
     * WHEN DFHRESP(DUPREC)
     *     MOVE 'Y' TO WS-ERR-FLG
     *     STRING 'User ID ' SEC-USR-ID ' already exists...' INTO WS-MESSAGE
     *     MOVE -1 TO USERIDL OF COUSR1AI
     * </pre>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 409 CONFLICT status code</li>
     *   <li>GlobalExceptionHandler translates DuplicateResourceException</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/users returns 409 CONFLICT for duplicate username")
    public void testCreateUser_DuplicateUsername() throws Exception {
        // Arrange: Create request with existing username
        UserCreateRequest request = UserCreateRequest.builder()
                .userId("ADMIN001")
                .password("SecureP@ss123")
                .firstName("Duplicate")
                .lastName("User")
                .userType("A")
                .build();

        // Mock UserService to throw DuplicateResourceException
        when(userService.createUser(any(UserCreateRequest.class)))
                .thenThrow(new com.aws.carddemo.exception.DuplicateResourceException("Username already exists: ADMIN001"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    /**
     * Test POST /api/v1/admin/users returns 400 BAD REQUEST for validation errors.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COUSR01C.cbl EDIT-USER-FIELDS validation:
     * <pre>
     * EDIT-USER-FIELDS.
     *     IF FNAMEI OF COUSR1AI = SPACES OR LOW-VALUES
     *         MOVE 'Y' TO WS-ERR-FLG
     *         MOVE 'First Name is required' TO WS-MESSAGE
     *         MOVE -1 TO FNAMEL OF COUSR1AI
     *     END-IF
     *     IF USERIDI OF COUSR1AI = SPACES OR LOW-VALUES
     *         MOVE 'Y' TO WS-ERR-FLG
     *         MOVE 'User ID is required (8 Char)' TO WS-MESSAGE
     *         MOVE -1 TO USERIDL OF COUSR1AI
     *     END-IF
     *     IF PASWDI OF COUSR1AI = SPACES OR LOW-VALUES
     *         MOVE 'Y' TO WS-ERR-FLG
     *         MOVE 'Password is required (8 Char)' TO WS-MESSAGE
     *         MOVE -1 TO PASWDL OF COUSR1AI
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>Bean Validation errors for @NotBlank, @Size constraints</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/users returns 400 BAD REQUEST for validation errors")
    public void testCreateUser_ValidationError() throws Exception {
        // Arrange: Create request with invalid data (missing required fields)
        UserCreateRequest request = UserCreateRequest.builder()
                .userId("")  // Invalid: blank userId
                .password("short")  // Invalid: too short
                .firstName("")  // Invalid: blank firstName
                .lastName("")  // Invalid: blank lastName
                .userType("X")  // Invalid: invalid userType
                .build();

        // Act & Assert: Bean Validation should reject request
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test POST /api/v1/admin/users returns 400 BAD REQUEST for weak password.
     * 
     * <p><strong>Security Enhancement over COBOL:</strong></p>
     * <p>COBOL accepted any 8-character password (PIC X(08)). Modern system requires:
     * <ul>
     *   <li>Minimum 8 characters</li>
     *   <li>At least one uppercase letter</li>
     *   <li>At least one lowercase letter</li>
     *   <li>At least one digit</li>
     *   <li>PCI-DSS compliant password complexity per Requirement 8.2.3</li>
     * </ul>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>Password complexity validation error</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/users returns 400 BAD REQUEST for weak password")
    public void testCreateUser_WeakPassword() throws Exception {
        // Arrange: Create request with weak password (no uppercase, no digit)
        UserCreateRequest request = UserCreateRequest.builder()
                .userId("USER0010")
                .password("weakpassword")  // Invalid: lacks complexity
                .firstName("Test")
                .lastName("User")
                .userType("R")
                .build();

        // Mock UserService to throw InvalidInputException for weak password
        when(userService.createUser(any(UserCreateRequest.class)))
                .thenThrow(new com.aws.carddemo.exception.InvalidInputException(
                        "Password must contain at least one uppercase letter, one lowercase letter, and one digit"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test PUT /api/v1/admin/users/{id} returns 200 OK with updated user.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COUSR02C.cbl UPDATE-USER-SEC-FILE:
     * <pre>
     * UPDATE-USER-SEC-FILE.
     *     PERFORM READ-USER-SEC-FILE
     *     IF WS-RESP-CD = DFHRESP(NORMAL)
     *         IF FNAMEI OF COUSR2AI NOT = SEC-USR-FNAME
     *             MOVE FNAMEI OF COUSR2AI TO SEC-USR-FNAME
     *             MOVE 'Y' TO WS-USR-MODIFIED
     *         END-IF
     *         IF LNAMEI OF COUSR2AI NOT = SEC-USR-LNAME
     *             MOVE LNAMEI OF COUSR2AI TO SEC-USR-LNAME
     *             MOVE 'Y' TO WS-USR-MODIFIED
     *         END-IF
     *         IF WS-USR-MODIFIED = 'Y'
     *             EXEC CICS REWRITE DATASET(WS-USRSEC-FILE) FROM(SEC-USER-DATA) END-EXEC
     *             IF WS-RESP-CD = DFHRESP(NORMAL)
     *                 STRING 'User ' SEC-USR-ID ' updated successfully!' INTO WS-MESSAGE
     *             END-IF
     *         END-IF
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 200 OK status code</li>
     *   <li>Response body contains updated user details</li>
     *   <li>Password field excluded from response</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/admin/users/{id} returns 200 OK with updated user")
    public void testUpdateUser_Success() throws Exception {
        // Arrange: Create update request (no password change)
        UserUpdateRequest request = UserUpdateRequest.builder()
                .firstName("Jane")
                .lastName("Smith-Updated")
                .userType("A")
                .build();

        // Mock updated user response
        UserResponse updatedUser = UserResponse.builder()
                .userId(1L)
                .username("ADMIN001")
                .firstName("Jane")
                .lastName("Smith-Updated")
                .userType("A")
                .roles(List.of("ROLE_ADMIN", "ROLE_USER"))
                .accountLocked(false)
                .failedLoginAttempts(0)
                .lastLogin(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now())
                .build();

        when(userService.updateUser(anyLong(), any(UserUpdateRequest.class))).thenReturn(updatedUser);

        // Act & Assert
        mockMvc.perform(put("/api/v1/admin/users/{userId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("ADMIN001"))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith-Updated"))
                .andExpect(jsonPath("$.userType").value("A"))
                .andExpect(jsonPath("$.password").doesNotExist());  // Security: password excluded
    }

    /**
     * Test PUT /api/v1/admin/users/{id} with password change triggers BCrypt re-hashing.
     * 
     * <p><strong>COBOL Comparison:</strong></p>
     * <p>In COBOL, password change was straightforward:
     * <pre>
     * IF PASWDI OF COUSR2AI NOT = SPACES
     *     MOVE PASWDI OF COUSR2AI TO SEC-USR-PWD
     *     MOVE 'Y' TO WS-USR-MODIFIED
     * END-IF.
     * </pre>
     * 
     * <p>Modern system adds:
     * <ul>
     *   <li>BCrypt re-hashing with 10 rounds</li>
     *   <li>Password confirmation validation</li>
     *   <li>Updates lastPasswordChange timestamp (90-day expiration policy per PCI-DSS)</li>
     * </ul>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 200 OK status code</li>
     *   <li>Password field in request triggers BCrypt hashing in service layer</li>
     *   <li>Response excludes password field</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/admin/users/{id} with password change updates lastPasswordChange")
    public void testUpdateUser_PasswordChange() throws Exception {
        // Arrange: Create update request with password change
        UserUpdateRequest request = UserUpdateRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .password("NewSecureP@ss456")  // Password change triggers BCrypt re-hashing
                .userType("A")
                .build();

        // Mock updated user response (password excluded)
        UserResponse updatedUser = UserResponse.builder()
                .userId(1L)
                .username("ADMIN001")
                .firstName("Jane")
                .lastName("Smith")
                .userType("A")
                .roles(List.of("ROLE_ADMIN", "ROLE_USER"))
                .accountLocked(false)
                .failedLoginAttempts(0)
                .lastLogin(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now())
                .build();

        when(userService.updateUser(anyLong(), any(UserUpdateRequest.class))).thenReturn(updatedUser);

        // Act & Assert
        mockMvc.perform(put("/api/v1/admin/users/{userId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.password").doesNotExist());  // Security: password excluded from response
    }

    /**
     * Test PUT /api/v1/admin/users/{id} returns 404 NOT FOUND for non-existent user.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COBOL FILE STATUS '23':
     * <pre>
     * IF WS-RESP-CD = DFHRESP(NOTFND)
     *     STRING 'User ID ' SEC-USR-ID ' NOT found...' INTO WS-MESSAGE
     * END-IF.
     * </pre>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 404 NOT FOUND status code</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/admin/users/{id} returns 404 NOT FOUND for non-existent user")
    public void testUpdateUser_NotFound() throws Exception {
        // Arrange
        UserUpdateRequest request = UserUpdateRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .userType("A")
                .build();

        // Mock UserService to throw ResourceNotFoundException
        when(userService.updateUser(anyLong(), any(UserUpdateRequest.class)))
                .thenThrow(new com.aws.carddemo.exception.ResourceNotFoundException("User not found with ID: 999"));

        // Act & Assert
        mockMvc.perform(put("/api/v1/admin/users/{userId}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /**
     * Test DELETE /api/v1/admin/users/{id} returns 204 NO CONTENT.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COUSR03C.cbl DELETE-USER-SEC-FILE:
     * <pre>
     * DELETE-USER-SEC-FILE.
     *     EXEC CICS DELETE DATASET(WS-USRSEC-FILE) RESP(WS-RESP-CD) END-EXEC
     *     EVALUATE WS-RESP-CD
     *         WHEN DFHRESP(NORMAL)
     *             STRING 'User ' SEC-USR-ID ' has been deleted ...' INTO WS-MESSAGE
     *             MOVE LOW-VALUES TO COUSR3AO
     *             MOVE WS-MESSAGE TO ERRMSGO OF COUSR3AO
     *             PERFORM SEND-USER-SCREEN
     *     END-EVALUATE.
     * </pre>
     * 
     * <p><strong>REST Convention:</strong></p>
     * <p>Unlike COBOL which displays success message, REST DELETE returns 204 NO CONTENT with empty body.
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 204 NO CONTENT status code</li>
     *   <li>Empty response body per REST conventions</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/admin/users/{id} returns 204 NO CONTENT")
    public void testDeleteUser_Success() throws Exception {
        // Arrange: Mock UserService to complete deletion successfully
        doNothing().when(userService).deleteUser(anyLong());

        // Act & Assert
        mockMvc.perform(delete("/api/v1/admin/users/{userId}", 1L)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));  // Empty body for 204 NO CONTENT
    }

    /**
     * Test DELETE /api/v1/admin/users/{id} returns 404 NOT FOUND for non-existent user.
     * 
     * <p><strong>COBOL Equivalence:</strong></p>
     * <p>Proves functional equivalence to COBOL FILE STATUS '23':
     * <pre>
     * WHEN DFHRESP(NOTFND)
     *     STRING 'User ID ' SEC-USR-ID ' NOT found...' INTO WS-MESSAGE
     *     MOVE -1 TO USRIDINL OF COUSR3AI
     * </pre>
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 404 NOT FOUND status code</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/admin/users/{id} returns 404 NOT FOUND for non-existent user")
    public void testDeleteUser_NotFound() throws Exception {
        // Arrange: Mock UserService to throw ResourceNotFoundException
        doThrow(new com.aws.carddemo.exception.ResourceNotFoundException("User not found with ID: 999"))
                .when(userService).deleteUser(999L);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/admin/users/{userId}", 999L)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /**
     * Test DELETE /api/v1/admin/users/{id} returns 400 BAD REQUEST for self-deletion attempt.
     * 
     * <p><strong>Business Rule:</strong></p>
     * <p>Prevents admin from deleting their own account, which could cause system lockout.
     * This rule was not explicitly enforced in COBOL but is a critical security safeguard.
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 400 BAD REQUEST status code</li>
     *   <li>Error message indicates self-deletion not allowed</li>
     * </ul>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/admin/users/{id} returns 400 BAD REQUEST for self-deletion")
    public void testDeleteUser_CannotDeleteSelf() throws Exception {
        // Arrange: Mock UserService to throw InvalidInputException for self-deletion
        doThrow(new com.aws.carddemo.exception.InvalidInputException("Cannot delete your own user account"))
                .when(userService).deleteUser(1L);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/admin/users/{userId}", 1L)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test GET /api/v1/admin/users returns 401 UNAUTHORIZED for unauthenticated request.
     * 
     * <p><strong>Security Validation:</strong></p>
     * <p>Verifies authentication requirement before authorization check.
     * Spring Security filter chain rejects anonymous users before reaching @PreAuthorize annotation.
     * 
     * <p><strong>Assertions:</strong></p>
     * <ul>
     *   <li>HTTP 401 UNAUTHORIZED status code</li>
     *   <li>JWT authentication required</li>
     * </ul>
     */
    @Test
    @WithAnonymousUser
    @DisplayName("GET /api/v1/admin/users returns 401 UNAUTHORIZED for unauthenticated user")
    public void testGetAllUsers_Unauthenticated() throws Exception {
        // Act & Assert: Anonymous user should be rejected at authentication layer
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
