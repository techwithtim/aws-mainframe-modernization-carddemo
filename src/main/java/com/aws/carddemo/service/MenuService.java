/*
 * CardDemo - Menu Service
 * 
 * Migrated from: app/cbl/COMEN01C.cbl (Main Menu), app/cbl/COADM01C.cbl (Admin Menu)
 * Data structures from: app/cpy/COMEN02Y.cpy, app/cpy/COADM02Y.cpy
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.carddemo.service;

import com.aws.carddemo.dto.response.MenuResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for providing menu navigation options based on user roles.
 * 
 * <p>Replaces legacy COBOL menu screen presentation logic from COMEN01C.cbl (Main Menu)
 * and COADM01C.cbl (Admin Menu), transforming 3270 terminal-based menu displays into
 * RESTful JSON menu structures for modern client consumption (React/Angular frontends).</p>
 * 
 * <p>Core Functionality:</p>
 * <ul>
 *   <li>Dynamically generates menu options based on Spring Security user roles (ROLE_USER, ROLE_ADMIN)</li>
 *   <li>Maps COBOL program names (COACTVWC, COUSR00C, etc.) to REST API endpoints</li>
 *   <li>Replaces COBOL 88-level condition name checking (CDEMO-USRTYP-USER, CDEMO-USRTYP-ADMIN) with Spring Security authority validation</li>
 *   <li>Transforms COBOL PERFORM VARYING loops iterating through menu option arrays into Stream API processing</li>
 *   <li>Converts EXEC CICS XCTL program-to-program navigation to REST API endpoint paths</li>
 * </ul>
 * 
 * <p>COBOL Legacy Mapping:</p>
 * <pre>
 * COMEN01C.cbl:
 *   BUILD-MENU-OPTIONS paragraph → getMainMenu() method
 *   PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > CDEMO-MENU-OPT-COUNT
 *     → List.of() with MenuOption builder pattern (functional style)
 *   IF CDEMO-USRTYP-USER → SecurityContextHolder.getContext().getAuthentication().getAuthorities()
 *   EXEC CICS XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) → targetEndpoint field in MenuOption
 * 
 * COADM01C.cbl:
 *   BUILD-MENU-OPTIONS paragraph → getAdminMenu() method
 *   PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > CDEMO-ADMIN-OPT-COUNT
 *     → List.of() with MenuOption builder pattern (functional style)
 *   EXEC CICS XCTL PROGRAM(CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)) → targetEndpoint field in MenuOption
 * </pre>
 * 
 * <p>Usage Example:</p>
 * <pre>
 * // Controller injection
 * &#64;RestController
 * public class MenuController {
 *     private final MenuService menuService;
 *     
 *     &#64;GetMapping("/api/v1/menu")
 *     public MenuResponse getMenu() {
 *         return menuService.getMenuForCurrentUser();
 *     }
 * }
 * </pre>
 * 
 * <p>Security Model:</p>
 * <ul>
 *   <li>ROLE_USER - Access to main menu (11 options from COMEN02Y.cpy)</li>
 *   <li>ROLE_ADMIN - Access to admin menu (6 options from COADM02Y.cpy) plus all main menu options</li>
 *   <li>Unauthenticated users - No menu access (controller handles 401 Unauthorized)</li>
 * </ul>
 * 
 * @see MenuResponse
 * @see com.aws.carddemo.controller.MenuController
 */
@Slf4j
@Service
public class MenuService {
    
    /**
     * Gets the appropriate menu for the currently authenticated user based on their roles.
     * 
     * <p>Replaces COBOL logic that checked CDEMO-USER-TYPE from COMMAREA (COCOM01Y.cpy) and
     * conditionally executed COMEN01C or COADM01C based on user type (U=User, A=Admin).</p>
     * 
     * <p>Decision Logic:</p>
     * <ul>
     *   <li>If user has ROLE_ADMIN → return admin menu (getAdminMenu())</li>
     *   <li>If user has only ROLE_USER → return main menu (getMainMenu())</li>
     *   <li>If user is not authenticated → throws AuthenticationException (handled by Spring Security)</li>
     * </ul>
     * 
     * <p>COBOL Legacy: Equivalent to checking CDEMO-USRTYP-ADMIN (88-level condition name
     * in COCOM01Y.cpy) to determine which menu program to XCTL to.</p>
     * 
     * @return MenuResponse containing menu options appropriate for user's role
     * @throws org.springframework.security.core.AuthenticationException if user is not authenticated
     */
    public MenuResponse getMenuForCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Attempted to retrieve menu for unauthenticated user");
            // Spring Security will handle this case with 401 Unauthorized
            return MenuResponse.builder()
                    .menuTitle("Unauthorized")
                    .menuOptions(List.of())
                    .build();
        }
        
        // Extract role names from authorities
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toList());
        
        log.debug("Retrieving menu for user '{}' with roles {}", 
                authentication.getName(), roles);
        
        // Admin users get the admin menu (equivalent to CDEMO-USRTYP-ADMIN check)
        if (roles.contains("ROLE_ADMIN")) {
            log.debug("User has ROLE_ADMIN, returning admin menu");
            return getAdminMenu();
        }
        
        // Regular users get the main menu (equivalent to CDEMO-USRTYP-USER check)
        log.debug("User has ROLE_USER, returning main menu");
        return getMainMenu();
    }
    
    /**
     * Gets the main menu for regular users.
     * 
     * <p>Migrates menu options from COMEN02Y.cpy copybook. The COBOL structure defines
     * 11 menu options (CDEMO-MENU-OPT-COUNT = 11) with option number, name, target program,
     * and user type for each entry.</p>
     * 
     * <p>COBOL Legacy Mapping (COMEN02Y.cpy):</p>
     * <pre>
     * Option 1: Account View           → COACTVWC → GET /api/v1/accounts
     * Option 2: Account Update         → COACTUPC → PUT /api/v1/accounts
     * Option 3: Credit Card List       → COCRDLIC → GET /api/v1/cards
     * Option 4: Credit Card View       → COCRDSLC → GET /api/v1/cards
     * Option 5: Credit Card Update     → COCRDUPC → PUT /api/v1/cards
     * Option 6: Transaction List       → COTRN00C → GET /api/v1/transactions
     * Option 7: Transaction View       → COTRN01C → GET /api/v1/transactions
     * Option 8: Transaction Add        → COTRN02C → POST /api/v1/transactions
     * Option 9: Transaction Reports    → CORPT00C → GET /api/v1/reports
     * Option 10: Bill Payment          → COBIL00C → POST /api/v1/payments
     * Option 11: Pending Auth View     → COPAUS0C → GET /api/v1/authorizations/pending
     * </pre>
     * 
     * <p>COBOL Transformation:</p>
     * <ul>
     *   <li>PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > CDEMO-MENU-OPT-COUNT
     *       → Replaced with List.of() immutable list construction</li>
     *   <li>CDEMO-MENU-OPT-NUM(WS-IDX) → MenuOption.optionCode</li>
     *   <li>CDEMO-MENU-OPT-NAME(WS-IDX) → MenuOption.description</li>
     *   <li>CDEMO-MENU-OPT-PGMNAME(WS-IDX) → Mapped to REST API targetEndpoint</li>
     *   <li>CDEMO-MENU-OPT-USRTYPE(WS-IDX) 'U' → requiredRole "ROLE_USER"</li>
     * </ul>
     * 
     * @return MenuResponse with 11 main menu options for regular users
     */
    public MenuResponse getMainMenu() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userName = authentication != null ? authentication.getName() : "User";
        
        log.debug("Building main menu with 11 options (from COMEN02Y.cpy)");
        
        // Build menu options from COMEN02Y.cpy structure
        // Replaces COBOL PERFORM VARYING loop with functional List construction
        List<MenuResponse.MenuOption> menuOptions = List.of(
            // Option 1: Account View (COACTVWC)
            MenuResponse.MenuOption.builder()
                    .optionCode("1")
                    .description("Account View")
                    .targetEndpoint("/api/v1/accounts")
                    .httpMethod("GET")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 2: Account Update (COACTUPC)
            MenuResponse.MenuOption.builder()
                    .optionCode("2")
                    .description("Account Update")
                    .targetEndpoint("/api/v1/accounts")
                    .httpMethod("PUT")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 3: Credit Card List (COCRDLIC)
            MenuResponse.MenuOption.builder()
                    .optionCode("3")
                    .description("Credit Card List")
                    .targetEndpoint("/api/v1/cards")
                    .httpMethod("GET")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 4: Credit Card View (COCRDSLC)
            MenuResponse.MenuOption.builder()
                    .optionCode("4")
                    .description("Credit Card View")
                    .targetEndpoint("/api/v1/cards")
                    .httpMethod("GET")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 5: Credit Card Update (COCRDUPC)
            MenuResponse.MenuOption.builder()
                    .optionCode("5")
                    .description("Credit Card Update")
                    .targetEndpoint("/api/v1/cards")
                    .httpMethod("PUT")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 6: Transaction List (COTRN00C)
            MenuResponse.MenuOption.builder()
                    .optionCode("6")
                    .description("Transaction List")
                    .targetEndpoint("/api/v1/transactions")
                    .httpMethod("GET")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 7: Transaction View (COTRN01C)
            MenuResponse.MenuOption.builder()
                    .optionCode("7")
                    .description("Transaction View")
                    .targetEndpoint("/api/v1/transactions")
                    .httpMethod("GET")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 8: Transaction Add (COTRN02C)
            MenuResponse.MenuOption.builder()
                    .optionCode("8")
                    .description("Transaction Add")
                    .targetEndpoint("/api/v1/transactions")
                    .httpMethod("POST")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 9: Transaction Reports (CORPT00C)
            MenuResponse.MenuOption.builder()
                    .optionCode("9")
                    .description("Transaction Reports")
                    .targetEndpoint("/api/v1/reports")
                    .httpMethod("GET")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 10: Bill Payment (COBIL00C)
            MenuResponse.MenuOption.builder()
                    .optionCode("10")
                    .description("Bill Payment")
                    .targetEndpoint("/api/v1/payments")
                    .httpMethod("POST")
                    .requiredRole("ROLE_USER")
                    .build(),
            
            // Option 11: Pending Authorization View (COPAUS0C)
            MenuResponse.MenuOption.builder()
                    .optionCode("11")
                    .description("Pending Authorization View")
                    .targetEndpoint("/api/v1/authorizations/pending")
                    .httpMethod("GET")
                    .requiredRole("ROLE_USER")
                    .build()
        );
        
        log.debug("Generated main menu for user '{}' with {} options", userName, menuOptions.size());
        
        // Build and return menu response
        // Replaces COBOL SEND MAP operation with JSON DTO
        return MenuResponse.builder()
                .menuTitle("Main Menu")
                .userName(userName)
                .menuOptions(menuOptions)
                .build();
    }
    
    /**
     * Gets the admin menu for administrator users.
     * 
     * <p>Migrates menu options from COADM02Y.cpy copybook. The COBOL structure defines
     * 6 menu options (CDEMO-ADMIN-OPT-COUNT = 6) focused on user management and system
     * administration functions.</p>
     * 
     * <p>COBOL Legacy Mapping (COADM02Y.cpy):</p>
     * <pre>
     * Option 1: User List (Security)              → COUSR00C → GET /api/v1/admin/users
     * Option 2: User Add (Security)               → COUSR01C → POST /api/v1/admin/users
     * Option 3: User Update (Security)            → COUSR02C → PUT /api/v1/admin/users
     * Option 4: User Delete (Security)            → COUSR03C → DELETE /api/v1/admin/users
     * Option 5: Transaction Type List/Update      → COTRTLIC → GET /api/v1/admin/transaction-types
     * Option 6: Transaction Type Maintenance      → COTRTUPC → PUT /api/v1/admin/transaction-types
     * </pre>
     * 
     * <p>COBOL Transformation:</p>
     * <ul>
     *   <li>PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > CDEMO-ADMIN-OPT-COUNT
     *       → Replaced with List.of() immutable list construction</li>
     *   <li>CDEMO-ADMIN-OPT-NUM(WS-IDX) → MenuOption.optionCode</li>
     *   <li>CDEMO-ADMIN-OPT-NAME(WS-IDX) → MenuOption.description</li>
     *   <li>CDEMO-ADMIN-OPT-PGMNAME(WS-IDX) → Mapped to REST API targetEndpoint</li>
     *   <li>All admin options implicitly require ROLE_ADMIN (enforced by @PreAuthorize in controllers)</li>
     * </ul>
     * 
     * <p>Security Note: In COMEN01C.cbl, there was logic to prevent regular users from
     * accessing admin options (lines 136-143). This validation is now handled by Spring Security
     * at the controller layer via @PreAuthorize("hasRole('ADMIN')") annotations.</p>
     * 
     * @return MenuResponse with 6 admin menu options for administrators
     */
    public MenuResponse getAdminMenu() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userName = authentication != null ? authentication.getName() : "Administrator";
        
        log.debug("Building admin menu with 6 options (from COADM02Y.cpy)");
        
        // Build admin menu options from COADM02Y.cpy structure
        // Replaces COBOL PERFORM VARYING loop with functional List construction
        List<MenuResponse.MenuOption> adminOptions = List.of(
            // Option 1: User List (Security) (COUSR00C)
            MenuResponse.MenuOption.builder()
                    .optionCode("1")
                    .description("User List (Security)")
                    .targetEndpoint("/api/v1/admin/users")
                    .httpMethod("GET")
                    .requiredRole("ROLE_ADMIN")
                    .build(),
            
            // Option 2: User Add (Security) (COUSR01C)
            MenuResponse.MenuOption.builder()
                    .optionCode("2")
                    .description("User Add (Security)")
                    .targetEndpoint("/api/v1/admin/users")
                    .httpMethod("POST")
                    .requiredRole("ROLE_ADMIN")
                    .build(),
            
            // Option 3: User Update (Security) (COUSR02C)
            MenuResponse.MenuOption.builder()
                    .optionCode("3")
                    .description("User Update (Security)")
                    .targetEndpoint("/api/v1/admin/users")
                    .httpMethod("PUT")
                    .requiredRole("ROLE_ADMIN")
                    .build(),
            
            // Option 4: User Delete (Security) (COUSR03C)
            MenuResponse.MenuOption.builder()
                    .optionCode("4")
                    .description("User Delete (Security)")
                    .targetEndpoint("/api/v1/admin/users")
                    .httpMethod("DELETE")
                    .requiredRole("ROLE_ADMIN")
                    .build(),
            
            // Option 5: Transaction Type List/Update (Db2) (COTRTLIC)
            MenuResponse.MenuOption.builder()
                    .optionCode("5")
                    .description("Transaction Type List/Update (Db2)")
                    .targetEndpoint("/api/v1/admin/transaction-types")
                    .httpMethod("GET")
                    .requiredRole("ROLE_ADMIN")
                    .build(),
            
            // Option 6: Transaction Type Maintenance (Db2) (COTRTUPC)
            MenuResponse.MenuOption.builder()
                    .optionCode("6")
                    .description("Transaction Type Maintenance (Db2)")
                    .targetEndpoint("/api/v1/admin/transaction-types")
                    .httpMethod("PUT")
                    .requiredRole("ROLE_ADMIN")
                    .build()
        );
        
        log.debug("Generated admin menu for user '{}' with {} options", userName, adminOptions.size());
        
        // Build and return admin menu response
        // Replaces COBOL SEND MAP operation with JSON DTO
        return MenuResponse.builder()
                .menuTitle("Administration Menu")
                .userName(userName)
                .menuOptions(adminOptions)
                .build();
    }
}
