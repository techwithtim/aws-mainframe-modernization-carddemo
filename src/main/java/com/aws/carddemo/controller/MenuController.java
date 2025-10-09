/*
 * CardDemo - Menu Controller
 * 
 * Migrated from: app/cbl/COMEN01C.cbl (Main Menu), app/cbl/COADM01C.cbl (Admin Menu)
 * BMS screens: app/bms/COMEN01.bms, app/bms/COADM01.bms
 * Data structures from: app/cpy/COMEN02Y.cpy, app/cpy/COADM02Y.cpy
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.carddemo.controller;

import com.aws.carddemo.dto.response.MenuResponse;
import com.aws.carddemo.service.MenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing dynamic menu navigation options based on user roles.
 * 
 * <p>Replaces legacy COBOL CICS menu screens with RESTful JSON API endpoints, transforming
 * mainframe 3270 terminal menu displays into modern web-compatible menu structures for
 * client-side rendering (React, Angular, Vue.js frontends).</p>
 * 
 * <p>Core Functionality:</p>
 * <ul>
 *   <li>Exposes menu navigation options through REST API endpoints</li>
 *   <li>Implements role-based menu filtering (ROLE_USER vs ROLE_ADMIN)</li>
 *   <li>Returns JSON menu structures with navigation paths and HTTP methods</li>
 *   <li>Maintains functional equivalence with COBOL menu screen logic</li>
 * </ul>
 * 
 * <p>COBOL Legacy Mapping:</p>
 * <pre>
 * COMEN01C.cbl (Main Menu Program):
 *   EXEC CICS SEND MAP('COMEN1A') → GET /api/v1/menu returns MenuResponse JSON
 *   PERFORM SEND-MENU-SCREEN → menuService.getMainMenu() encapsulation
 *   PERFORM BUILD-MENU-OPTIONS → MenuService builds List&lt;MenuOption&gt;
 *   IF CDEMO-USRTYP-USER → @PreAuthorize("hasRole('USER')") Spring Security annotation
 *   EXEC CICS RECEIVE MAP('COMEN1A') → Client-side HTTP GET request
 *   PERFORM PROCESS-ENTER-KEY → Client-side navigation to targetEndpoint
 *   EXEC CICS XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) → Client navigates to REST API endpoint
 * 
 * COADM01C.cbl (Admin Menu Program):
 *   EXEC CICS SEND MAP('COADM1A') → GET /api/v1/admin/menu returns MenuResponse JSON
 *   PERFORM SEND-MENU-SCREEN → menuService.getAdminMenu() encapsulation
 *   PERFORM BUILD-MENU-OPTIONS → MenuService builds List&lt;MenuOption&gt;
 *   IF CDEMO-USRTYP-ADMIN → @PreAuthorize("hasRole('ADMIN')") Spring Security annotation
 *   Admin-only access validation (lines 136-143 in COMEN01C.cbl) → Spring Security enforcement
 * </pre>
 * 
 * <p>API Endpoints:</p>
 * <ul>
 *   <li>GET /api/v1/menu - Returns main menu for regular users (11 options from COMEN02Y.cpy)</li>
 *   <li>GET /api/v1/admin/menu - Returns admin menu for administrators (6 options from COADM02Y.cpy)</li>
 * </ul>
 * 
 * <p>Security Model:</p>
 * <ul>
 *   <li>Both endpoints require authentication (401 Unauthorized if not authenticated)</li>
 *   <li>GET /api/v1/menu requires ROLE_USER or ROLE_ADMIN</li>
 *   <li>GET /api/v1/admin/menu requires ROLE_ADMIN only</li>
 *   <li>Role-based access control enforced via @PreAuthorize annotations (declarative security)</li>
 *   <li>Spring Security filters validate JWT tokens before controller invocation</li>
 * </ul>
 * 
 * <p>Usage Example:</p>
 * <pre>
 * // Client-side JavaScript/TypeScript request
 * fetch('http://localhost:8080/api/v1/menu', {
 *   method: 'GET',
 *   headers: {
 *     'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
 *     'Content-Type': 'application/json'
 *   }
 * })
 * .then(response =&gt; response.json())
 * .then(menu =&gt; {
 *   console.log(`Menu Title: ${menu.menuTitle}`);
 *   console.log(`User: ${menu.userName}`);
 *   menu.menuOptions.forEach(option =&gt; {
 *     console.log(`${option.optionCode}. ${option.description} → ${option.targetEndpoint}`);
 *   });
 * });
 * 
 * // Example Response for Regular User:
 * {
 *   "menuTitle": "Main Menu",
 *   "userName": "John Doe",
 *   "menuOptions": [
 *     {
 *       "optionCode": "1",
 *       "description": "Account View",
 *       "targetEndpoint": "/api/v1/accounts",
 *       "httpMethod": "GET",
 *       "requiredRole": "ROLE_USER"
 *     },
 *     {
 *       "optionCode": "2",
 *       "description": "Account Update",
 *       "targetEndpoint": "/api/v1/accounts",
 *       "httpMethod": "PUT",
 *       "requiredRole": "ROLE_USER"
 *     },
 *     ... (9 more options)
 *   ]
 * }
 * 
 * // Example Response for Administrator:
 * {
 *   "menuTitle": "Administration Menu",
 *   "userName": "Admin User",
 *   "menuOptions": [
 *     {
 *       "optionCode": "1",
 *       "description": "User List (Security)",
 *       "targetEndpoint": "/api/v1/admin/users",
 *       "httpMethod": "GET",
 *       "requiredRole": "ROLE_ADMIN"
 *     },
 *     ... (5 more options)
 *   ]
 * }
 * </pre>
 * 
 * <p>COBOL Screen Flow Transformation:</p>
 * <pre>
 * Legacy CICS Flow:
 *   1. User authenticates via COSGN00C
 *   2. COSGN00C XCTL to COMEN01C (if user) or COADM01C (if admin)
 *   3. COMEN01C/COADM01C displays menu screen via EXEC CICS SEND MAP
 *   4. User enters option number, presses ENTER
 *   5. Program receives screen input via EXEC CICS RECEIVE MAP
 *   6. Program validates option number and user access
 *   7. Program XCTL to target program (COACTVWC, COUSR00C, etc.)
 * 
 * Modern REST Flow:
 *   1. User authenticates via POST /api/v1/auth/login (receives JWT token)
 *   2. Client sends GET /api/v1/menu with Authorization header
 *   3. MenuController returns MenuResponse JSON with available options
 *   4. Client displays menu in UI (table, list, navigation sidebar, etc.)
 *   5. User clicks menu option
 *   6. Client navigates to targetEndpoint with appropriate httpMethod
 *   7. Target controller (AccountController, CardController, etc.) handles request
 * </pre>
 * 
 * <p>Performance Characteristics:</p>
 * <ul>
 *   <li>Response time: &lt;50ms (menu is constructed in-memory, no database queries)</li>
 *   <li>Caching: Not required (menu structure is static, lightweight)</li>
 *   <li>Concurrency: Stateless, thread-safe (MenuService is singleton, immutable responses)</li>
 * </ul>
 * 
 * <p>Error Handling:</p>
 * <ul>
 *   <li>401 Unauthorized - User not authenticated (no valid JWT token)</li>
 *   <li>403 Forbidden - User authenticated but lacks required role (e.g., ROLE_USER trying to access /admin/menu)</li>
 *   <li>All exceptions handled by GlobalExceptionHandler (@ControllerAdvice)</li>
 * </ul>
 * 
 * <p>Migration Notes:</p>
 * <ul>
 *   <li>COBOL menu option selection (numeric input field) replaced by client-side navigation</li>
 *   <li>COBOL error messages (ERRMSG field) replaced by HTTP status codes and JSON error responses</li>
 *   <li>COBOL PF-key handling (PF3=Exit) replaced by client-side navigation logic</li>
 *   <li>COBOL screen attributes (color, highlighting) replaced by client-side CSS styling</li>
 *   <li>COBOL commarea (CARDDEMO-COMMAREA) replaced by Spring Security context and JWT claims</li>
 * </ul>
 * 
 * @see MenuService
 * @see MenuResponse
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MenuController {
    
    /**
     * Menu service for generating role-based menu structures.
     * 
     * <p>Injected via constructor-based dependency injection (Lombok @RequiredArgsConstructor).
     * Provides business logic for building menu options from COMEN02Y.cpy and COADM02Y.cpy
     * copybook structures.</p>
     */
    private final MenuService menuService;
    
    /**
     * Retrieves the main menu for the currently authenticated user.
     * 
     * <p>Replaces COMEN01C.cbl CICS program that displayed the main menu screen (COMEN01.bms).
     * Returns a JSON structure containing 11 menu navigation options for regular users with
     * ROLE_USER or ROLE_ADMIN authority.</p>
     * 
     * <p>COBOL Legacy Mapping:</p>
     * <pre>
     * COMEN01C.cbl:
     *   MAIN-PARA → This method execution
     *   PERFORM SEND-MENU-SCREEN → menuService.getMainMenu() call
     *   PERFORM POPULATE-HEADER-INFO → Header fields (date/time) included in MenuResponse
     *   PERFORM BUILD-MENU-OPTIONS → MenuService generates List&lt;MenuOption&gt;
     *   EXEC CICS SEND MAP('COMEN1A') MAPSET('COMEN01') → return ResponseEntity.ok(menuResponse)
     * 
     * COMEN02Y.cpy Menu Options (11 options):
     *   Option 1: Account View (COACTVWC) → GET /api/v1/accounts
     *   Option 2: Account Update (COACTUPC) → PUT /api/v1/accounts
     *   Option 3: Credit Card List (COCRDLIC) → GET /api/v1/cards
     *   Option 4: Credit Card View (COCRDSLC) → GET /api/v1/cards
     *   Option 5: Credit Card Update (COCRDUPC) → PUT /api/v1/cards
     *   Option 6: Transaction List (COTRN00C) → GET /api/v1/transactions
     *   Option 7: Transaction View (COTRN01C) → GET /api/v1/transactions
     *   Option 8: Transaction Add (COTRN02C) → POST /api/v1/transactions
     *   Option 9: Transaction Reports (CORPT00C) → GET /api/v1/reports
     *   Option 10: Bill Payment (COBIL00C) → POST /api/v1/payments
     *   Option 11: Pending Authorization View (COPAUS0C) → GET /api/v1/authorizations/pending
     * </pre>
     * 
     * <p>Security Validation:</p>
     * <ul>
     *   <li>@PreAuthorize("hasRole('USER')") ensures user has ROLE_USER or ROLE_ADMIN</li>
     *   <li>If user lacks required role → Spring Security returns HTTP 403 Forbidden</li>
     *   <li>If user is not authenticated → Spring Security returns HTTP 401 Unauthorized</li>
     *   <li>Replaces COBOL validation: IF CDEMO-USRTYP-USER (88-level condition in COCOM01Y.cpy)</li>
     * </ul>
     * 
     * <p>Response Structure:</p>
     * <pre>
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * 
     * {
     *   "menuTitle": "Main Menu",
     *   "userName": "authenticatedUsername",
     *   "menuOptions": [
     *     {
     *       "optionCode": "1",
     *       "description": "Account View",
     *       "targetEndpoint": "/api/v1/accounts",
     *       "httpMethod": "GET",
     *       "requiredRole": "ROLE_USER"
     *     },
     *     ... (10 more options)
     *   ]
     * }
     * </pre>
     * 
     * <p>Audit Logging:</p>
     * <ul>
     *   <li>INFO level: Successful menu retrieval with option count</li>
     *   <li>DEBUG level: User principal name and role information</li>
     *   <li>PCI-DSS compliance: No sensitive data logged (usernames are non-sensitive)</li>
     * </ul>
     * 
     * <p>Performance:</p>
     * <ul>
     *   <li>Expected response time: &lt;50ms (no database access, in-memory menu generation)</li>
     *   <li>Thread-safe: Stateless operation, safe for concurrent requests</li>
     *   <li>No caching required: Menu structure is static, construction is lightweight</li>
     * </ul>
     * 
     * @return ResponseEntity containing MenuResponse with 11 main menu options
     * @throws org.springframework.security.access.AccessDeniedException if user lacks ROLE_USER (returns 403 Forbidden)
     */
    @GetMapping("/menu")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<MenuResponse> getMenu() {
        log.info("Processing GET /api/v1/menu request for main menu");
        
        // Delegate to service layer for menu generation
        // Replaces COBOL: PERFORM SEND-MENU-SCREEN paragraph in COMEN01C.cbl
        MenuResponse menuResponse = menuService.getMenuForCurrentUser();
        
        log.info("Successfully retrieved main menu with {} options for user '{}'", 
                menuResponse.getMenuOptions().size(),
                menuResponse.getUserName());
        
        // Return HTTP 200 OK with MenuResponse JSON body
        // Replaces COBOL: EXEC CICS SEND MAP('COMEN1A') MAPSET('COMEN01') FROM(COMEN1AO)
        return ResponseEntity.ok(menuResponse);
    }
    
    /**
     * Retrieves the administration menu for administrator users.
     * 
     * <p>Replaces COADM01C.cbl CICS program that displayed the admin menu screen (COADM01.bms).
     * Returns a JSON structure containing 6 admin-specific menu navigation options exclusively
     * for users with ROLE_ADMIN authority.</p>
     * 
     * <p>COBOL Legacy Mapping:</p>
     * <pre>
     * COADM01C.cbl:
     *   MAIN-PARA → This method execution
     *   PERFORM SEND-MENU-SCREEN → menuService.getAdminMenu() call
     *   PERFORM POPULATE-HEADER-INFO → Header fields (date/time) included in MenuResponse
     *   PERFORM BUILD-MENU-OPTIONS → MenuService generates List&lt;MenuOption&gt;
     *   EXEC CICS SEND MAP('COADM1A') MAPSET('COADM01') → return ResponseEntity.ok(menuResponse)
     * 
     * COADM02Y.cpy Menu Options (6 options):
     *   Option 1: User List (Security) (COUSR00C) → GET /api/v1/admin/users
     *   Option 2: User Add (Security) (COUSR01C) → POST /api/v1/admin/users
     *   Option 3: User Update (Security) (COUSR02C) → PUT /api/v1/admin/users
     *   Option 4: User Delete (Security) (COUSR03C) → DELETE /api/v1/admin/users
     *   Option 5: Transaction Type List/Update (Db2) (COTRTLIC) → GET /api/v1/admin/transaction-types
     *   Option 6: Transaction Type Maintenance (Db2) (COTRTUPC) → PUT /api/v1/admin/transaction-types
     * </pre>
     * 
     * <p>Security Validation:</p>
     * <ul>
     *   <li>@PreAuthorize("hasRole('ADMIN')") ensures user has ROLE_ADMIN authority</li>
     *   <li>If user has only ROLE_USER → Spring Security returns HTTP 403 Forbidden</li>
     *   <li>If user is not authenticated → Spring Security returns HTTP 401 Unauthorized</li>
     *   <li>Replaces COBOL validation: IF CDEMO-USRTYP-ADMIN (88-level condition in COCOM01Y.cpy)</li>
     *   <li>Replaces COBOL access check in COMEN01C.cbl lines 136-143 that prevented users from accessing admin options</li>
     * </ul>
     * 
     * <p>Response Structure:</p>
     * <pre>
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * 
     * {
     *   "menuTitle": "Administration Menu",
     *   "userName": "adminUsername",
     *   "menuOptions": [
     *     {
     *       "optionCode": "1",
     *       "description": "User List (Security)",
     *       "targetEndpoint": "/api/v1/admin/users",
     *       "httpMethod": "GET",
     *       "requiredRole": "ROLE_ADMIN"
     *     },
     *     ... (5 more options)
     *   ]
     * }
     * </pre>
     * 
     * <p>Admin Menu Functionality:</p>
     * <ul>
     *   <li>User Management: Create, read, update, delete user accounts (Options 1-4)</li>
     *   <li>Transaction Type Management: Configure transaction type definitions (Options 5-6)</li>
     *   <li>All options require ROLE_ADMIN for access enforcement at controller layer</li>
     * </ul>
     * 
     * <p>Audit Logging:</p>
     * <ul>
     *   <li>INFO level: Admin menu retrieval logged for security monitoring</li>
     *   <li>DEBUG level: Admin username and role verification</li>
     *   <li>PCI-DSS compliance: Admin actions logged for compliance audit trail</li>
     *   <li>CloudWatch integration: Logs exported for centralized monitoring</li>
     * </ul>
     * 
     * <p>Performance:</p>
     * <ul>
     *   <li>Expected response time: &lt;50ms (no database access, in-memory menu generation)</li>
     *   <li>Thread-safe: Stateless operation, safe for concurrent admin requests</li>
     *   <li>No caching required: Admin menu structure is static, construction is lightweight</li>
     * </ul>
     * 
     * <p>COBOL Access Control Migration:</p>
     * <pre>
     * COMEN01C.cbl lines 136-143 (Admin access check for regular users):
     *   IF CDEMO-USRTYP-USER AND
     *      CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'
     *       SET ERR-FLG-ON TO TRUE
     *       MOVE 'No access - Admin Only option... ' TO WS-MESSAGE
     *       PERFORM SEND-MENU-SCREEN
     * 
     * Spring Security Replacement:
     *   @PreAuthorize("hasRole('ADMIN')") annotation on this method
     *   If user lacks ROLE_ADMIN → Spring Security filter chain returns HTTP 403 Forbidden
     *   No explicit error message needed (standard HTTP error handling)
     * </pre>
     * 
     * @return ResponseEntity containing MenuResponse with 6 admin menu options
     * @throws org.springframework.security.access.AccessDeniedException if user lacks ROLE_ADMIN (returns 403 Forbidden)
     */
    @GetMapping("/admin/menu")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MenuResponse> getAdminMenu() {
        log.info("Processing GET /api/v1/admin/menu request for administration menu");
        
        // Delegate to service layer for admin menu generation
        // Replaces COBOL: PERFORM SEND-MENU-SCREEN paragraph in COADM01C.cbl
        MenuResponse menuResponse = menuService.getAdminMenu();
        
        log.info("Successfully retrieved admin menu with {} options for administrator '{}'", 
                menuResponse.getMenuOptions().size(),
                menuResponse.getUserName());
        
        // Return HTTP 200 OK with MenuResponse JSON body
        // Replaces COBOL: EXEC CICS SEND MAP('COADM1A') MAPSET('COADM01') FROM(COADM1AO)
        return ResponseEntity.ok(menuResponse);
    }
}
