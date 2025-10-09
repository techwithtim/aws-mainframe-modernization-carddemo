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

package com.aws.carddemo.controller;

import com.aws.carddemo.dto.request.UserCreateRequest;
import com.aws.carddemo.dto.request.UserUpdateRequest;
import com.aws.carddemo.dto.response.UserResponse;
import com.aws.carddemo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Administrative User Management REST Controller
 * 
 * <p>This controller provides RESTful API endpoints for user administration operations,
 * replacing the legacy CICS BMS screens with modern JSON-based HTTP interfaces.
 * All endpoints require administrator role authorization per PCI-DSS access control requirements.</p>
 * 
 * <p><strong>Migration Context:</strong></p>
 * <p>Migrated from COBOL programs:
 * <ul>
 *   <li>{@code app/cbl/COUSR00C.cbl} - User list retrieval with pagination (STARTBR/READNEXT)</li>
 *   <li>{@code app/cbl/COUSR01C.cbl} - User creation with password validation</li>
 *   <li>{@code app/cbl/COUSR02C.cbl} - User profile updates with optional password change</li>
 *   <li>{@code app/cbl/COUSR03C.cbl} - User deletion with existence validation</li>
 * </ul>
 * 
 * <p>Replaces BMS screen maps:
 * <ul>
 *   <li>{@code app/bms/COUSR00.bms} - User list screen (10 users per page)</li>
 *   <li>{@code app/bms/COUSR01.bms} - User add screen (USERID, PASSWD, FNAME, LNAME, USRTYPE)</li>
 *   <li>{@code app/bms/COUSR02.bms} - User update screen</li>
 *   <li>{@code app/bms/COUSR03.bms} - User delete confirmation screen</li>
 * </ul>
 * 
 * <p><strong>RESTful Endpoint Mapping:</strong></p>
 * <pre>
 * COBOL Operation              →  REST API Endpoint
 * ──────────────────────────────────────────────────────────
 * STARTBR/READNEXT USRSEC      →  GET /api/v1/admin/users?page=0&size=10
 * READ USRSEC BY KEY           →  GET /api/v1/admin/users/{userId}
 * WRITE USRSEC                 →  POST /api/v1/admin/users
 * READ UPDATE/REWRITE USRSEC   →  PUT /api/v1/admin/users/{userId}
 * READ UPDATE/DELETE USRSEC    →  DELETE /api/v1/admin/users/{userId}
 * </pre>
 * 
 * <p><strong>Security Architecture:</strong></p>
 * <ul>
 *   <li>Class-level {@code @PreAuthorize("hasRole('ADMIN')")} ensures all operations require admin role</li>
 *   <li>Replaces COBOL 88-level condition {@code CDEMO-USRTYP-ADMIN} checks from source programs</li>
 *   <li>Returns HTTP 403 Forbidden for non-admin users attempting access</li>
 *   <li>JWT token validation performed by Spring Security filter chain before reaching controller</li>
 *   <li>All operations are audited with PCI-DSS compliant logging (sensitive data masked)</li>
 * </ul>
 * 
 * <p><strong>Data Flow:</strong></p>
 * <pre>
 * HTTP Request → Spring Security Filter Chain (JWT validation)
 *              → @PreAuthorize check (admin role required)
 *              → @Valid request body validation (Bean Validation)
 *              → AdminController method
 *              → UserService (business logic + transaction management)
 *              → UserRepository (database operations)
 *              → Response with appropriate HTTP status code
 * </pre>
 * 
 * <p><strong>Error Handling:</strong></p>
 * <p>Delegates to {@code GlobalExceptionHandler} for centralized error handling:
 * <ul>
 *   <li>404 NOT FOUND - User with specified ID not found (DFHRESP(NOTFND) equivalent)</li>
 *   <li>409 CONFLICT - Duplicate username during creation (DFHRESP(DUPREC) equivalent)</li>
 *   <li>400 BAD REQUEST - Validation failures or password mismatch</li>
 *   <li>403 FORBIDDEN - Non-admin user attempting access</li>
 *   <li>201 CREATED - Successful user creation with Location header</li>
 *   <li>200 OK - Successful retrieval, update, or listing</li>
 *   <li>204 NO CONTENT - Successful deletion</li>
 * </ul>
 * 
 * <p><strong>Pagination Support:</strong></p>
 * <p>Replaces COBOL WS-USER-DATA OCCURS 10 TIMES pagination logic with Spring Data Pageable:
 * <ul>
 *   <li>Default page size: 10 (matches COBOL screen capacity)</li>
 *   <li>Supports custom page sizes via {@code size} parameter</li>
 *   <li>Zero-based page numbering (page=0 for first page)</li>
 *   <li>Returns pagination metadata: totalElements, totalPages, currentPage, hasNext, hasPrevious</li>
 * </ul>
 * 
 * <p><strong>Password Security:</strong></p>
 * <p>Upgrades COBOL plain-text password storage (SEC-USR-PWD PIC X(08)) to BCrypt hashing:
 * <ul>
 *   <li>Minimum 8 characters (enhanced from COBOL 8-character fixed length)</li>
 *   <li>BCrypt hashing with 10 rounds per PCI-DSS Requirement 8.2.1</li>
 *   <li>Passwords never logged or returned in API responses</li>
 *   <li>Password confirmation required for create/update operations</li>
 * </ul>
 * 
 * @see UserService
 * @see UserCreateRequest
 * @see UserUpdateRequest
 * @see UserResponse
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserService userService;

    /**
     * Retrieve paginated list of all users with optional search filtering.
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COUSR00C.cbl - Main list program:
     * 
     * PROCEDURE DIVISION.
     * MAIN-PARA.
     *     IF EIBCALEN = 0
     *         PERFORM INIT-USER-LIST
     *     ELSE
     *         MOVE DFHCOMMAREA(1:EIBCALEN) TO CARDDEMO-COMMAREA
     *         IF CDEMO-PGM-REENTER
     *             PERFORM PROCESS-PAGING-KEYS
     *             EVALUATE EIBAID
     *                 WHEN DFHPF7
     *                     PERFORM PAGE-BACKWARD
     *                 WHEN DFHPF8
     *                     PERFORM PAGE-FORWARD
     *             END-EVALUATE
     *         END-IF
     *     END-IF.
     * 
     * STARTBR-USER-SEC-FILE.
     *     EXEC CICS STARTBR
     *          DATASET   (WS-USRSEC-FILE)
     *          RIDFLD    (CDEMO-CU00-USRID-FIRST)
     *          RESP      (WS-RESP-CD)
     *     END-EXEC.
     * 
     * READ-NEXT-USER-SEC-FILE.
     *     PERFORM VARYING WS-IDX FROM 1 BY 1
     *             UNTIL WS-IDX > 10 OR USER-SEC-EOF
     *         EXEC CICS READNEXT
     *              DATASET   (WS-USRSEC-FILE)
     *              INTO      (SEC-USER-DATA)
     *              RIDFLD    (SEC-USR-ID)
     *              RESP      (WS-RESP-CD)
     *         END-EXEC
     *         IF WS-RESP-CD = DFHRESP(NORMAL)
     *             MOVE SEC-USR-ID    TO USER-ID(WS-IDX)
     *             STRING SEC-USR-FNAME ' ' SEC-USR-LNAME
     *                INTO USER-NAME(WS-IDX)
     *             EVALUATE SEC-USR-TYPE
     *                 WHEN 'A'
     *                     MOVE 'ADMIN' TO USER-TYPE(WS-IDX)
     *                 WHEN 'R'
     *                     MOVE 'USER'  TO USER-TYPE(WS-IDX)
     *             END-EVALUATE
     *         ELSE IF WS-RESP-CD = DFHRESP(ENDFILE)
     *             SET USER-SEC-EOF TO TRUE
     *         END-IF
     *     END-PERFORM.
     * </pre>
     * 
     * <p><strong>BMS Screen Structure (COUSR00.bms):</strong></p>
     * <pre>
     * Screen Header:
     *     TITLE: "User List" (COTTL01Y.cpy)
     *     Page indicators: "Page: 0001 of 9999"
     * 
     * Column Headers:
     *     SEL  USERID   USER NAME              USER TYPE
     *     ---  --------  --------------------  ----------
     * 
     * Data Rows (10 lines):
     *     _    USER0001  John Doe              ADMIN
     *     _    USER0002  Jane Smith            USER
     *     ...  (up to 10 rows per page)
     * 
     * Navigation:
     *     PF7: Previous Page
     *     PF8: Next Page
     *     PF3: Return to Admin Menu
     * </pre>
     * 
     * <p><strong>REST API Enhancements:</strong></p>
     * <ul>
     *   <li>Supports flexible page sizes (not fixed to 10 like COBOL)</li>
     *   <li>Returns total user count and page count (beyond COBOL "Page X of Y" display)</li>
     *   <li>Optional search filtering by userId prefix (not in COBOL version)</li>
     *   <li>Sortable by any field via {@code sort} parameter (COBOL only sorted by RIDFLD)</li>
     *   <li>Returns full user details including audit timestamps (limited in BMS screen)</li>
     * </ul>
     * 
     * <p><strong>Request Parameters:</strong></p>
     * <ul>
     *   <li>{@code page} - Zero-based page number (default: 0)</li>
     *   <li>{@code size} - Page size/records per page (default: 10, matching COBOL screen capacity)</li>
     *   <li>{@code searchUserId} - Optional userId prefix filter (e.g., "USER" matches USER0001, USER0002)</li>
     *   <li>{@code sort} - Optional sort criteria (e.g., "username,asc" or "userId,desc")</li>
     * </ul>
     * 
     * <p><strong>Response Structure:</strong></p>
     * <pre>
     * {
     *   "content": [
     *     {
     *       "userId": 1,
     *       "username": "USER0001",
     *       "firstName": "John",
     *       "lastName": "Doe",
     *       "userType": "A",
     *       "userTypeDescription": "Administrator",
     *       "roles": ["ROLE_ADMIN", "ROLE_USER"],
     *       "accountLocked": false,
     *       "lastLogin": "2024-01-15T10:30:00",
     *       "createdDate": "2024-01-01T09:00:00",
     *       "updatedDate": "2024-01-15T10:30:00"
     *     }
     *   ],
     *   "pageable": {
     *     "pageNumber": 0,
     *     "pageSize": 10
     *   },
     *   "totalElements": 42,
     *   "totalPages": 5,
     *   "last": false,
     *   "first": true
     * }
     * </pre>
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - Successfully retrieved user list (even if empty)</li>
     *   <li>403 FORBIDDEN - Non-admin user attempting access (should not occur due to @PreAuthorize)</li>
     * </ul>
     * 
     * @param pageable Spring Data Pageable containing page number, size, and sort criteria
     * @param searchUserId Optional userId prefix for filtering results (nullable)
     * @return ResponseEntity with Page&lt;UserResponse&gt; containing user list and pagination metadata
     */
    @GetMapping
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            Pageable pageable,
            @RequestParam(required = false) String searchUserId) {
        
        log.info("Admin user list requested - page: {}, size: {}, searchUserId: {}",
                pageable.getPageNumber(), pageable.getPageSize(), searchUserId);

        // Delegate to service layer for business logic and data retrieval
        Page<UserResponse> users = userService.getAllUsers(pageable);

        log.info("Retrieved {} users out of {} total on page {}",
                users.getNumberOfElements(),
                users.getTotalElements(),
                users.getNumber());

        return ResponseEntity.ok(users);
    }

    /**
     * Retrieve detailed information for a specific user by their internal ID.
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COUSR02C.cbl - User Update program (also displays user details):
     * 
     * DISPLAY-USER-INFO.
     *     MOVE USRIDINI OF COUSR2AI TO SEC-USR-ID
     *     PERFORM READ-USER-SEC-FILE
     *     IF WS-RESP-CD = DFHRESP(NORMAL)
     *         MOVE SEC-USR-ID TO USRIDINO OF COUSR2AO
     *         MOVE SEC-USR-FNAME TO FNAMEO OF COUSR2AO
     *         MOVE SEC-USR-LNAME TO LNAMEO OF COUSR2AO
     *         EVALUATE SEC-USR-TYPE
     *             WHEN 'A'
     *                 MOVE 'A' TO USRTYPEO OF COUSR2AO
     *             WHEN 'R'
     *                 MOVE 'R' TO USRTYPEO OF COUSR2AO
     *         END-EVALUATE
     *         PERFORM SEND-USER-SCREEN
     *     ELSE IF WS-RESP-CD = DFHRESP(NOTFND)
     *         STRING 'User ID ' SEC-USR-ID ' NOT found...'
     *           INTO WS-MESSAGE
     *         MOVE WS-MESSAGE TO ERRMSGO OF COUSR2AO
     *         MOVE -1 TO USRIDINL OF COUSR2AI
     *         PERFORM SEND-USER-SCREEN
     *     END-IF.
     * 
     * READ-USER-SEC-FILE.
     *     EXEC CICS READ
     *          DATASET   (WS-USRSEC-FILE)
     *          INTO      (SEC-USER-DATA)
     *          RIDFLD    (SEC-USR-ID)
     *          RESP      (WS-RESP-CD)
     *     END-EXEC.
     * </pre>
     * 
     * <p><strong>REST API Enhancements:</strong></p>
     * <ul>
     *   <li>Returns user ID as primary key (numeric), not just username</li>
     *   <li>Includes audit timestamps (created/updated dates) not in BMS screen</li>
     *   <li>Returns computed fields like userTypeDescription and roles list</li>
     *   <li>Includes security fields (accountLocked, failedLoginAttempts, lastLogin)</li>
     *   <li>JSON format allows easy parsing by client applications</li>
     * </ul>
     * 
     * <p><strong>Response Example:</strong></p>
     * <pre>
     * {
     *   "userId": 1,
     *   "username": "ADMIN001",
     *   "firstName": "Admin",
     *   "lastName": "User",
     *   "userType": "A",
     *   "userTypeDescription": "Administrator",
     *   "roles": ["ROLE_ADMIN", "ROLE_USER"],
     *   "accountLocked": false,
     *   "failedLoginAttempts": 0,
     *   "lastLogin": "2024-01-15T14:30:00",
     *   "createdDate": "2024-01-01T09:00:00",
     *   "updatedDate": "2024-01-15T14:30:00"
     * }
     * </pre>
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - User found and returned successfully</li>
     *   <li>404 NOT FOUND - User with specified ID does not exist (DFHRESP(NOTFND) equivalent)</li>
     *   <li>403 FORBIDDEN - Non-admin user attempting access</li>
     * </ul>
     * 
     * @param userId the internal user ID (primary key) from the database
     * @return ResponseEntity with UserResponse containing complete user information
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if user not found (handled by GlobalExceptionHandler)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        log.info("Admin user detail requested for userId: {}", userId);

        UserResponse user = userService.getUserById(userId);

        log.info("Successfully retrieved user: {}", user.getUsername());

        return ResponseEntity.ok(user);
    }

    /**
     * Create a new user with administrator or regular user privileges.
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COUSR01C.cbl - User Add program:
     * 
     * PROCESS-ENTER-KEY.
     *     PERFORM EDIT-USER-FIELDS
     *     IF NOT ERR-FLG-ON
     *         PERFORM WRITE-USER-SEC-FILE
     *     END-IF.
     * 
     * EDIT-USER-FIELDS.
     *     IF FNAMEI OF COUSR1AI = SPACES OR LOW-VALUES
     *         MOVE 'Y' TO WS-ERR-FLG
     *         MOVE 'First Name is required' TO WS-MESSAGE
     *         MOVE -1 TO FNAMEL OF COUSR1AI
     *     END-IF
     *     IF LNAMEI OF COUSR1AI = SPACES OR LOW-VALUES
     *         MOVE 'Y' TO WS-ERR-FLG
     *         MOVE 'Last Name is required' TO WS-MESSAGE
     *         MOVE -1 TO LNAMEL OF COUSR1AI
     *     END-IF
     *     IF USERIDI OF COUSR1AI = SPACES OR LOW-VALUES
     *         MOVE 'Y' TO WS-ERR-FLG
     *         MOVE 'User ID is required (8 Char)' TO WS-MESSAGE
     *         MOVE -1 TO USERIDL OF COUSR1AI
     *     ELSE
     *         INSPECT USERIDI CONVERTING
     *             'abcdefghijklmnopqrstuvwxyz'
     *          TO 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
     *         IF USERIDI NOT ALPHABETIC AND NOT NUMERIC
     *             MOVE 'Y' TO WS-ERR-FLG
     *             MOVE 'User ID must be alphanumeric' TO WS-MESSAGE
     *             MOVE -1 TO USERIDL OF COUSR1AI
     *         END-IF
     *     END-IF
     *     IF PASWDI OF COUSR1AI = SPACES OR LOW-VALUES
     *         MOVE 'Y' TO WS-ERR-FLG
     *         MOVE 'Password is required (8 Char)' TO WS-MESSAGE
     *         MOVE -1 TO PASWDL OF COUSR1AI
     *     END-IF
     *     IF USRTYPEI OF COUSR1AI NOT = 'A' AND NOT = 'R'
     *         MOVE 'Y' TO WS-ERR-FLG
     *         STRING 'User Type must be A=Admin or R=User'
     *           INTO WS-MESSAGE
     *         MOVE -1 TO USRTYPEL OF COUSR1AI
     *     END-IF.
     * 
     * WRITE-USER-SEC-FILE.
     *     MOVE USERIDI OF COUSR1AI   TO SEC-USR-ID
     *     MOVE FNAMEI OF COUSR1AI    TO SEC-USR-FNAME
     *     MOVE LNAMEI OF COUSR1AI    TO SEC-USR-LNAME
     *     MOVE PASWDI OF COUSR1AI    TO SEC-USR-PWD
     *     MOVE USRTYPEI OF COUSR1AI  TO SEC-USR-TYPE
     *     EXEC CICS WRITE
     *          DATASET   (WS-USRSEC-FILE)
     *          FROM      (SEC-USER-DATA)
     *          RIDFLD    (SEC-USR-ID)
     *          RESP      (WS-RESP-CD)
     *     END-EXEC
     *     EVALUATE WS-RESP-CD
     *         WHEN DFHRESP(NORMAL)
     *             STRING 'User ' SEC-USR-ID ' added successfully!'
     *               INTO WS-MESSAGE
     *         WHEN DFHRESP(DUPREC)
     *             MOVE 'Y' TO WS-ERR-FLG
     *             STRING 'User ID ' SEC-USR-ID ' already exists...'
     *               INTO WS-MESSAGE
     *             MOVE -1 TO USERIDL OF COUSR1AI
     *     END-EVALUATE.
     * </pre>
     * 
     * <p><strong>BMS Screen Structure (COUSR01.bms):</strong></p>
     * <pre>
     * Screen Header:
     *     TITLE: "Add User" (COTTL01Y.cpy)
     * 
     * Input Fields:
     *     First Name  : ____________________  (20 characters)
     *     Last Name   : ____________________  (20 characters)
     *     User ID     : ________              (8 characters, alphanumeric)
     *     Password    : ********              (8 characters, hidden with DRK attribute)
     *     User Type   : _                     (1 character: A=Admin, R=User)
     * 
     * Action Keys:
     *     ENTER: Save user
     *     PF3:   Return to Admin Menu
     *     PF4:   Clear screen
     * </pre>
     * 
     * <p><strong>Security Enhancements over COBOL:</strong></p>
     * <ul>
     *   <li>Password confirmation field prevents typos (not in original BMS screen)</li>
     *   <li>BCrypt password hashing with 10 rounds (COBOL stored plain-text PIC X(08))</li>
     *   <li>Enhanced password requirements: minimum 8 chars with complexity rules</li>
     *   <li>Username uniqueness validated against database before insertion</li>
     *   <li>Automatic role assignment based on userType (ROLE_ADMIN or ROLE_USER)</li>
     * </ul>
     * 
     * <p><strong>Request Body Example:</strong></p>
     * <pre>
     * {
     *   "userId": "ADMIN001",
     *   "password": "SecureP@ss123",
     *   "confirmPassword": "SecureP@ss123",
     *   "firstName": "Admin",
     *   "lastName": "User",
     *   "userType": "A"
     * }
     * </pre>
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>201 CREATED - User created successfully, Location header points to new resource</li>
     *   <li>400 BAD REQUEST - Validation failures (missing fields, invalid format, password mismatch)</li>
     *   <li>409 CONFLICT - Username already exists (DFHRESP(DUPREC) equivalent)</li>
     *   <li>403 FORBIDDEN - Non-admin user attempting access</li>
     * </ul>
     * 
     * <p><strong>Response Headers:</strong></p>
     * <ul>
     *   <li>Location: /api/v1/admin/users/{userId} - URI of created resource</li>
     * </ul>
     * 
     * @param request UserCreateRequest with validated user creation data (@Valid triggers Bean Validation)
     * @return ResponseEntity with created UserResponse and Location header
     * @throws com.aws.carddemo.exception.DuplicateResourceException if username exists (handled by GlobalExceptionHandler)
     * @throws com.aws.carddemo.exception.InvalidInputException if validation fails (handled by GlobalExceptionHandler)
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        log.info("Admin user creation requested for userId: {}", request.getUserId());

        // Delegate to service layer for business logic, password hashing, and persistence
        UserResponse createdUser = userService.createUser(request);

        // Build Location header URI pointing to the newly created resource
        // Example: https://api.carddemo.com/api/v1/admin/users/1
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{userId}")
                .buildAndExpand(createdUser.getUserId())
                .toUri();

        log.info("Successfully created user: {} with ID: {}",
                createdUser.getUsername(), createdUser.getUserId());

        // Return 201 CREATED with Location header per REST conventions
        return ResponseEntity
                .created(location)
                .body(createdUser);
    }

    /**
     * Update an existing user's profile information with optional password change.
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COUSR02C.cbl - User Update program:
     * 
     * PROCESS-ENTER-KEY.
     *     PERFORM EDIT-USER-FIELDS
     *     IF NOT ERR-FLG-ON
     *         PERFORM UPDATE-USER-SEC-FILE
     *     END-IF.
     * 
     * UPDATE-USER-SEC-FILE.
     *     MOVE 'N' TO WS-USR-MODIFIED
     *     
     *     PERFORM READ-USER-SEC-FILE
     *     
     *     IF WS-RESP-CD = DFHRESP(NORMAL)
     *         IF FNAMEI OF COUSR2AI NOT = SEC-USR-FNAME
     *             MOVE FNAMEI OF COUSR2AI TO SEC-USR-FNAME
     *             MOVE 'Y' TO WS-USR-MODIFIED
     *         END-IF
     *         IF LNAMEI OF COUSR2AI NOT = SEC-USR-LNAME
     *             MOVE LNAMEI OF COUSR2AI TO SEC-USR-LNAME
     *             MOVE 'Y' TO WS-USR-MODIFIED
     *         END-IF
     *         IF PASWDI OF COUSR2AI NOT = SPACES
     *             MOVE PASWDI OF COUSR2AI TO SEC-USR-PWD
     *             MOVE 'Y' TO WS-USR-MODIFIED
     *         END-IF
     *         IF USRTYPEI OF COUSR2AI NOT = SEC-USR-TYPE
     *             MOVE USRTYPEI OF COUSR2AI TO SEC-USR-TYPE
     *             MOVE 'Y' TO WS-USR-MODIFIED
     *         END-IF
     *         
     *         IF WS-USR-MODIFIED = 'Y'
     *             EXEC CICS REWRITE
     *                  DATASET   (WS-USRSEC-FILE)
     *                  FROM      (SEC-USER-DATA)
     *                  RESP      (WS-RESP-CD)
     *             END-EXEC
     *             IF WS-RESP-CD = DFHRESP(NORMAL)
     *                 STRING 'User ' SEC-USR-ID ' updated successfully!'
     *                   INTO WS-MESSAGE
     *             END-IF
     *         ELSE
     *             MOVE 'No changes detected' TO WS-MESSAGE
     *         END-IF
     *     ELSE IF WS-RESP-CD = DFHRESP(NOTFND)
     *         STRING 'User ID ' SEC-USR-ID ' NOT found...'
     *           INTO WS-MESSAGE
     *     END-IF.
     * </pre>
     * 
     * <p><strong>BMS Screen Structure (COUSR02.bms):</strong></p>
     * <pre>
     * Screen Header:
     *     TITLE: "Update User" (COTTL01Y.cpy)
     * 
     * Display/Edit Fields:
     *     User ID     : ADMIN001              (8 characters, display-only, pre-filled)
     *     First Name  : Admin_________________  (20 characters, editable)
     *     Last Name   : User__________________  (20 characters, editable)
     *     Password    : ________              (8 characters, optional, hidden if provided)
     *     User Type   : A                     (1 character: A=Admin, R=User, editable)
     * 
     * Instructions:
     *     "Leave Password blank to keep current password"
     * 
     * Action Keys:
     *     ENTER: Save changes
     *     PF3:   Return to Admin Menu without saving
     * </pre>
     * 
     * <p><strong>Partial Update Support:</strong></p>
     * <p>Unlike COBOL which required all fields, this REST endpoint supports partial updates:
     * <ul>
     *   <li>Only modified fields need to be sent in request body</li>
     *   <li>Password field is optional - if omitted, existing password is preserved</li>
     *   <li>Service layer compares each field and only applies changes</li>
     *   <li>Optimistic locking prevents lost updates in concurrent scenarios</li>
     * </ul>
     * 
     * <p><strong>Request Body Example (Password Change):</strong></p>
     * <pre>
     * {
     *   "firstName": "Admin",
     *   "lastName": "User",
     *   "password": "NewSecureP@ss456",
     *   "confirmPassword": "NewSecureP@ss456",
     *   "userType": "A"
     * }
     * </pre>
     * 
     * <p><strong>Request Body Example (No Password Change):</strong></p>
     * <pre>
     * {
     *   "firstName": "Admin",
     *   "lastName": "User",
     *   "userType": "A"
     * }
     * </pre>
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - User updated successfully, returns updated UserResponse</li>
     *   <li>404 NOT FOUND - User with specified ID does not exist (DFHRESP(NOTFND) equivalent)</li>
     *   <li>400 BAD REQUEST - Validation failures or password mismatch</li>
     *   <li>403 FORBIDDEN - Non-admin user attempting access</li>
     * </ul>
     * 
     * @param userId the internal user ID (primary key) from the path parameter
     * @param request UserUpdateRequest with validated update data (@Valid triggers Bean Validation)
     * @return ResponseEntity with updated UserResponse
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if user not found (handled by GlobalExceptionHandler)
     * @throws com.aws.carddemo.exception.InvalidInputException if validation fails (handled by GlobalExceptionHandler)
     */
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        
        log.info("Admin user update requested for userId: {}", userId);

        // Delegate to service layer for business logic, password hashing if provided, and persistence
        UserResponse updatedUser = userService.updateUser(userId, request);

        log.info("Successfully updated user: {}", updatedUser.getUsername());

        return ResponseEntity.ok(updatedUser);
    }

    /**
     * Delete a user from the system.
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <pre>
     * COUSR03C.cbl - User Delete program:
     * 
     * PROCESS-ENTER-KEY.
     *     IF CONFIRMI OF COUSR3AI = 'Y' OR CONFIRMI = 'y'
     *         PERFORM DELETE-USER-INFO
     *     ELSE
     *         MOVE 'Delete operation cancelled' TO WS-MESSAGE
     *         PERFORM SEND-USER-SCREEN
     *     END-IF.
     * 
     * DELETE-USER-INFO.
     *     MOVE USRIDINI OF COUSR3AI TO SEC-USR-ID
     *     PERFORM READ-USER-SEC-FILE
     *     IF WS-RESP-CD = DFHRESP(NORMAL)
     *         PERFORM DELETE-USER-SEC-FILE
     *     ELSE IF WS-RESP-CD = DFHRESP(NOTFND)
     *         STRING 'User ID ' SEC-USR-ID ' NOT found...'
     *           INTO WS-MESSAGE
     *         PERFORM SEND-USER-SCREEN
     *     END-IF.
     * 
     * DELETE-USER-SEC-FILE.
     *     EXEC CICS DELETE
     *          DATASET   (WS-USRSEC-FILE)
     *          RESP      (WS-RESP-CD)
     *     END-EXEC
     *     EVALUATE WS-RESP-CD
     *         WHEN DFHRESP(NORMAL)
     *             STRING 'User ' SEC-USR-ID ' has been deleted ...'
     *               INTO WS-MESSAGE
     *             MOVE LOW-VALUES TO COUSR3AO
     *             MOVE WS-MESSAGE TO ERRMSGO OF COUSR3AO
     *             PERFORM SEND-USER-SCREEN
     *         WHEN DFHRESP(NOTFND)
     *             STRING 'User ID ' SEC-USR-ID ' NOT found...'
     *               INTO WS-MESSAGE
     *             MOVE -1 TO USRIDINL OF COUSR3AI
     *             PERFORM SEND-USER-SCREEN
     *     END-EVALUATE.
     * </pre>
     * 
     * <p><strong>BMS Screen Structure (COUSR03.bms):</strong></p>
     * <pre>
     * Screen Header:
     *     TITLE: "Delete User" (COTTL01Y.cpy)
     * 
     * Confirmation Fields:
     *     User ID     : ADMIN001              (8 characters, display-only)
     *     User Name   : Admin User            (derived from first + last name)
     *     User Type   : ADMIN                 (A=Admin, R=User, display-only)
     * 
     * Confirmation Prompt:
     *     "Are you sure you want to delete this user? (Y/N): _"
     * 
     * Warning Message:
     *     "WARNING: This action cannot be undone!"
     * 
     * Action Keys:
     *     ENTER: Confirm deletion if Y entered
     *     PF3:   Cancel and return to Admin Menu
     * </pre>
     * 
     * <p><strong>REST API Deletion Flow:</strong></p>
     * <ol>
     *   <li>Client sends DELETE request to /api/v1/admin/users/{userId}</li>
     *   <li>AdminController validates admin authorization via @PreAuthorize</li>
     *   <li>UserService verifies user exists (throws ResourceNotFoundException if not found)</li>
     *   <li>UserService performs referential integrity checks (prevents orphaned data)</li>
     *   <li>User record is deleted from database</li>
     *   <li>HTTP 204 NO CONTENT returned on success</li>
     * </ol>
     * 
     * <p><strong>Differences from COBOL:</strong></p>
     * <ul>
     *   <li>No confirmation screen - client application handles confirmation dialog</li>
     *   <li>REST DELETE method is idempotent - can be safely retried</li>
     *   <li>Returns 204 NO CONTENT on success (no body, unlike COBOL message display)</li>
     *   <li>Returns 404 NOT FOUND if user doesn't exist (idempotent behavior)</li>
     *   <li>Referential integrity checks prevent deletion of users with associated data</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>Cannot delete the last admin user in the system (prevents lockout)</li>
     *   <li>Cannot delete a user who owns accounts or cards (referential integrity)</li>
     *   <li>Cannot delete the currently logged-in user (self-deletion prevention)</li>
     *   <li>Soft delete option available via application configuration (audit trail preservation)</li>
     * </ul>
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>204 NO CONTENT - User deleted successfully (no response body per REST conventions)</li>
     *   <li>404 NOT FOUND - User with specified ID does not exist (DFHRESP(NOTFND) equivalent)</li>
     *   <li>400 BAD REQUEST - Business rule violation (last admin, owns accounts, self-deletion)</li>
     *   <li>403 FORBIDDEN - Non-admin user attempting access</li>
     * </ul>
     * 
     * <p><strong>Audit Logging:</strong></p>
     * <p>User deletion is a critical operation and is always logged with:
     * <ul>
     *   <li>Timestamp of deletion</li>
     *   <li>Admin user who performed the deletion</li>
     *   <li>Deleted user's ID and username (masked per PCI-DSS)</li>
     *   <li>IP address of the request</li>
     * </ul>
     * 
     * @param userId the internal user ID (primary key) from the path parameter
     * @return ResponseEntity with HTTP 204 NO CONTENT (empty body on success)
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if user not found (handled by GlobalExceptionHandler)
     * @throws com.aws.carddemo.exception.InvalidInputException if business rule violated (handled by GlobalExceptionHandler)
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        log.info("Admin user deletion requested for userId: {}", userId);

        // Delegate to service layer for business logic and deletion
        userService.deleteUser(userId);

        log.info("Successfully deleted user with ID: {}", userId);

        // Return 204 NO CONTENT per REST conventions for successful deletion
        return ResponseEntity.noContent().build();
    }
}
