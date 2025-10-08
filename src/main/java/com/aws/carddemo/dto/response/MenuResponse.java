/*
 * CardDemo - Menu Response DTO
 * 
 * Migrated from: app/bms/COMEN01.bms (Main Menu), app/bms/COADM01.bms (Admin Menu)
 * Data structures from: app/cpy/COMEN02Y.cpy, app/cpy/COADM02Y.cpy
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.carddemo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for menu navigation operations.
 * 
 * <p>Replaces COMEN01.bms (Main Menu) and COADM01.bms (Admin Menu) 3270 terminal screen
 * displays with RESTful JSON menu structure. Maps COBOL PF-key selections to menu option
 * identifiers for client-side navigation.</p>
 * 
 * <p>Used by:</p>
 * <ul>
 *   <li>GET /api/v1/menu - Returns main menu for regular users (11 options from COMEN02Y.cpy)</li>
 *   <li>GET /api/v1/admin/menu - Returns admin menu for administrators (6 options from COADM02Y.cpy)</li>
 * </ul>
 * 
 * <p>COBOL Legacy Mapping:</p>
 * <pre>
 * COMEN02Y.cpy:
 *   CDEMO-MENU-OPT-COUNT (PIC 9(02)) → menuOptions.size()
 *   CDEMO-MENU-OPT-NUM (PIC 9(02)) → MenuOption.optionCode
 *   CDEMO-MENU-OPT-NAME (PIC X(35)) → MenuOption.description
 *   CDEMO-MENU-OPT-PGMNAME (PIC X(08)) → MenuOption.targetEndpoint (mapped to REST API)
 *   CDEMO-MENU-OPT-USRTYPE (PIC X(01)) → MenuOption.requiredRole (U='ROLE_USER', A='ROLE_ADMIN')
 * 
 * COADM02Y.cpy:
 *   CDEMO-ADMIN-OPT-COUNT (PIC 9(02)) → menuOptions.size()
 *   CDEMO-ADMIN-OPT-NUM (PIC 9(02)) → MenuOption.optionCode
 *   CDEMO-ADMIN-OPT-NAME (PIC X(35)) → MenuOption.description
 *   CDEMO-ADMIN-OPT-PGMNAME (PIC X(08)) → MenuOption.targetEndpoint (mapped to REST API)
 * </pre>
 * 
 * <p>Example JSON Response (Main Menu):</p>
 * <pre>
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
 *     }
 *   ]
 * }
 * </pre>
 * 
 * <p>Security Note: The requiredRole field indicates the minimum role needed to access
 * each menu option, enabling client-side menu filtering and access control validation.</p>
 * 
 * @see com.aws.carddemo.controller.MenuController
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuResponse {
    
    /**
     * Menu title displayed to user.
     * 
     * <p>Values:</p>
     * <ul>
     *   <li>"Main Menu" - For regular user menu (GET /api/v1/menu)</li>
     *   <li>"Administration Menu" - For admin menu (GET /api/v1/admin/menu)</li>
     * </ul>
     * 
     * <p>COBOL Legacy: Derived from BMS map screen title field (COMEN01.bms line 79, COADM01.bms line 79)</p>
     */
    private String menuTitle;
    
    /**
     * Display name of the authenticated user.
     * 
     * <p>Provides personalization context for the menu display. In COBOL system, this
     * would be derived from the COMMAREA user ID after successful authentication.</p>
     * 
     * <p>Example: "John Doe" or "Admin User"</p>
     * 
     * <p>Optional field - excluded from JSON if null (@JsonInclude configuration).</p>
     */
    private String userName;
    
    /**
     * List of menu options available to the user based on their role.
     * 
     * <p>Main Menu (COMEN02Y.cpy): 11 options</p>
     * <ul>
     *   <li>1 - Account View (COACTVWC)</li>
     *   <li>2 - Account Update (COACTUPC)</li>
     *   <li>3 - Credit Card List (COCRDLIC)</li>
     *   <li>4 - Credit Card View (COCRDSLC)</li>
     *   <li>5 - Credit Card Update (COCRDUPC)</li>
     *   <li>6 - Transaction List (COTRN00C)</li>
     *   <li>7 - Transaction View (COTRN01C)</li>
     *   <li>8 - Transaction Add (COTRN02C)</li>
     *   <li>9 - Transaction Reports (CORPT00C)</li>
     *   <li>10 - Bill Payment (COBIL00C)</li>
     *   <li>11 - Pending Authorization View (COPAUS0C)</li>
     * </ul>
     * 
     * <p>Admin Menu (COADM02Y.cpy): 6 options</p>
     * <ul>
     *   <li>1 - User List (COUSR00C)</li>
     *   <li>2 - User Add (COUSR01C)</li>
     *   <li>3 - User Update (COUSR02C)</li>
     *   <li>4 - User Delete (COUSR03C)</li>
     *   <li>5 - Transaction Type List/Update (COTRTLIC)</li>
     *   <li>6 - Transaction Type Maintenance (COTRTUPC)</li>
     * </ul>
     * 
     * <p>COBOL Legacy: Replaces OCCURS 12 TIMES array in COMEN02Y.cpy and OCCURS 9 TIMES in COADM02Y.cpy</p>
     */
    private List<MenuOption> menuOptions;
    
    /**
     * Nested DTO representing a single menu option.
     * 
     * <p>Maps COBOL menu option structure (CDEMO-MENU-OPT or CDEMO-ADMIN-OPT) to REST API navigation.</p>
     * 
     * <p>Replaces COBOL PF-key navigation (EVALUATE EIBAID) with explicit endpoint references,
     * enabling modern client-side routing and navigation.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MenuOption {
        
        /**
         * Option code or number for menu selection.
         * 
         * <p>Corresponds to the numeric option the user selects (1-11 for main menu, 1-6 for admin menu).</p>
         * 
         * <p>Examples: "1", "2", "3", ... "11"</p>
         * 
         * <p>COBOL Legacy: CDEMO-MENU-OPT-NUM (PIC 9(02)) or CDEMO-ADMIN-OPT-NUM (PIC 9(02))</p>
         */
        private String optionCode;
        
        /**
         * Human-readable description of the menu option.
         * 
         * <p>Displayed to the user for menu selection. Maximum 35 characters to match COBOL field length.</p>
         * 
         * <p>Examples:</p>
         * <ul>
         *   <li>"Account View"</li>
         *   <li>"Account Update"</li>
         *   <li>"Credit Card List"</li>
         *   <li>"User List (Security)"</li>
         *   <li>"Transaction Type Maintenance (Db2)"</li>
         * </ul>
         * 
         * <p>COBOL Legacy: CDEMO-MENU-OPT-NAME (PIC X(35)) or CDEMO-ADMIN-OPT-NAME (PIC X(35))</p>
         */
        private String description;
        
        /**
         * REST API endpoint path for this menu option.
         * 
         * <p>Maps COBOL program names (COACTVWC, COACTUPC, etc.) to modern REST API endpoints.
         * Client applications use this to navigate to the appropriate API resource.</p>
         * 
         * <p>Main Menu Endpoint Mappings:</p>
         * <pre>
         * COACTVWC → /api/v1/accounts       (Account View)
         * COACTUPC → /api/v1/accounts       (Account Update)
         * COCRDLIC → /api/v1/cards          (Card List)
         * COCRDSLC → /api/v1/cards          (Card View)
         * COCRDUPC → /api/v1/cards          (Card Update)
         * COTRN00C → /api/v1/transactions   (Transaction List)
         * COTRN01C → /api/v1/transactions   (Transaction View)
         * COTRN02C → /api/v1/transactions   (Transaction Add)
         * CORPT00C → /api/v1/reports        (Reports)
         * COBIL00C → /api/v1/payments       (Bill Payment)
         * COPAUS0C → /api/v1/authorizations/pending (Pending Auth)
         * </pre>
         * 
         * <p>Admin Menu Endpoint Mappings:</p>
         * <pre>
         * COUSR00C → /api/v1/admin/users              (User List)
         * COUSR01C → /api/v1/admin/users              (User Add)
         * COUSR02C → /api/v1/admin/users              (User Update)
         * COUSR03C → /api/v1/admin/users              (User Delete)
         * COTRTLIC → /api/v1/admin/transaction-types  (Transaction Type List)
         * COTRTUPC → /api/v1/admin/transaction-types  (Transaction Type Update)
         * </pre>
         * 
         * <p>COBOL Legacy: Derived from CDEMO-MENU-OPT-PGMNAME (PIC X(08)) or CDEMO-ADMIN-OPT-PGMNAME (PIC X(08))</p>
         */
        private String targetEndpoint;
        
        /**
         * HTTP method for accessing the target endpoint.
         * 
         * <p>Indicates the appropriate HTTP verb for the menu option's operation.</p>
         * 
         * <p>Valid values:</p>
         * <ul>
         *   <li>"GET" - For view/list operations (Account View, Card List, Transaction List, etc.)</li>
         *   <li>"POST" - For create operations (Transaction Add, User Add)</li>
         *   <li>"PUT" - For update operations (Account Update, Card Update, User Update)</li>
         *   <li>"DELETE" - For delete operations (User Delete)</li>
         * </ul>
         * 
         * <p>COBOL Legacy: Derived from program function (view programs → GET, update programs → PUT, etc.)</p>
         */
        private String httpMethod;
        
        /**
         * Required role to access this menu option.
         * 
         * <p>Used for client-side menu filtering and access control validation. Server-side
         * authorization is still enforced via Spring Security @PreAuthorize annotations.</p>
         * 
         * <p>Valid values:</p>
         * <ul>
         *   <li>"ROLE_USER" - Regular user access (all main menu options)</li>
         *   <li>"ROLE_ADMIN" - Administrator access (all admin menu options)</li>
         * </ul>
         * 
         * <p>COBOL Legacy: CDEMO-MENU-OPT-USRTYPE (PIC X(01)) where 'U'='ROLE_USER', 'A'='ROLE_ADMIN'</p>
         * 
         * <p>Optional field - excluded from JSON if null (@JsonInclude configuration).</p>
         */
        private String requiredRole;
    }
}
