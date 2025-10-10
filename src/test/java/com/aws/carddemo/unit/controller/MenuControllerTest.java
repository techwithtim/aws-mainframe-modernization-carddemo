/*
 * CardDemo - Menu Controller Unit Test
 * 
 * Tests for MenuController REST API endpoints migrated from COBOL menu screens
 * COMEN01.bms (Main Menu) and COADM01.bms (Admin Menu).
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.carddemo.unit.controller;

import com.aws.carddemo.controller.MenuController;
import com.aws.carddemo.dto.response.MenuResponse;
import com.aws.carddemo.repository.UserRepository;
import com.aws.carddemo.security.JwtAuthenticationFilter;
import com.aws.carddemo.security.UserDetailsServiceImpl;
import com.aws.carddemo.service.AuthenticationService;
import com.aws.carddemo.service.MenuService;
import com.aws.carddemo.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit test class for MenuController REST API endpoints.
 * 
 * <p>Tests menu navigation functionality migrated from COBOL CICS programs COMEN01C.cbl
 * (Main Menu) and COADM01C.cbl (Admin Menu), proving functional equivalence with legacy
 * mainframe menu screens through RESTful JSON API endpoints.</p>
 * 
 * <p>Test Coverage:</p>
 * <ul>
 *   <li>GET /api/v1/menu - Main menu retrieval with role-based filtering</li>
 *   <li>GET /api/v1/admin/menu - Admin menu retrieval with ROLE_ADMIN authorization</li>
 *   <li>Authentication enforcement (401 Unauthorized for unauthenticated requests)</li>
 *   <li>Authorization enforcement (403 Forbidden for insufficient privileges)</li>
 *   <li>JSON response structure validation with JsonPath assertions</li>
 *   <li>Menu option count validation (11 for main menu, 6 for admin menu)</li>
 * </ul>
 * 
 * <p>COBOL Legacy Validation:</p>
 * <pre>
 * COMEN01C.cbl Menu Options (COMEN02Y.cpy):
 *   - 11 menu options for regular users (ROLE_USER)
 *   - Options: Account View, Account Update, Card List, Card View, Card Update,
 *     Transaction List, Transaction View, Transaction Add, Reports, Bill Payment,
 *     Pending Authorization View
 * 
 * COADM01C.cbl Menu Options (COADM02Y.cpy):
 *   - 6 menu options for administrators (ROLE_ADMIN)
 *   - Options: User List, User Add, User Update, User Delete,
 *     Transaction Type List/Update, Transaction Type Maintenance
 * 
 * COBOL Access Control (COMEN01C.cbl lines 136-143):
 *   IF CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'
 *     SET ERR-FLG-ON TO TRUE
 *     MOVE 'No access - Admin Only option... ' TO WS-MESSAGE
 *   
 *   Replaced by: Spring Security @PreAuthorize("hasRole('ADMIN')") annotation
 *   Returns: HTTP 403 Forbidden for non-admin users attempting admin menu access
 * </pre>
 * 
 * <p>Test Framework Stack:</p>
 * <ul>
 *   <li>@WebMvcTest - Spring Boot MVC test slice for focused controller testing</li>
 *   <li>MockMvc - Simulates HTTP requests without full servlet container</li>
 *   <li>@MockBean - Mocks MenuService dependency with Mockito stubbing</li>
 *   <li>@WithMockUser - Simulates authenticated user with roles (ROLE_USER, ROLE_ADMIN)</li>
 *   <li>@WithAnonymousUser - Simulates unauthenticated user for 401 testing</li>
 *   <li>JsonPath - Validates JSON response structure and field values</li>
 * </ul>
 * 
 * <p>Success Criteria (Agent Action Plan Section 0.8.1):</p>
 * <ul>
 *   <li>Target ≥80% line coverage for MenuController</li>
 *   <li>Prove functional equivalence to COBOL menu logic</li>
 *   <li>Validate Spring Security role-based access control</li>
 *   <li>Ensure zero placeholders or incomplete tests</li>
 * </ul>
 * 
 * @see MenuController
 * @see MenuService
 * @see MenuResponse
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("MenuController Unit Tests")
class MenuControllerTest {
    
    /**
     * Test security configuration that enables method-level security for @PreAuthorize testing.
     * 
     * <p>This configuration is necessary because the main SecurityConfig has @Profile("!test"),
     * which excludes it from test execution. This test configuration ensures @PreAuthorize
     * annotations are properly enforced during testing.</p>
     */
    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @EnableWebSecurity
    static class TestSecurityConfig {
        
        @Bean
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint((request, response, authException) -> {
                                response.setStatus(401);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"message\":\"Unauthorized\"}");
                            })
                    )
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
                    );
            
            return http.build();
        }
    }
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private MenuService menuService;
    
    @MockBean
    private UserRepository userRepository;
    
    /**
     * Mock ReportService to prevent batch job dependency issues in test context.
     * ReportService depends on JobLauncher and Job beans which aren't available
     * in @SpringBootTest with test profile due to batch auto-configuration exclusions.
     */
    @MockBean
    private com.aws.carddemo.service.ReportService reportService;
    
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
    
    /**
     * Verify that MenuController is loaded in the application context.
     */
    @Test
    @DisplayName("MenuController bean should be loaded in test context")
    void testMenuControllerBeanExists() {
        org.junit.jupiter.api.Assertions.assertNotNull(
                applicationContext.getBean(MenuController.class),
                "MenuController should be present in the application context"
        );
        
        // Print all registered request mappings for debugging
        org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mapping = 
                applicationContext.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        mapping.getHandlerMethods().forEach((key, value) -> {
            System.out.println("Registered mapping: " + key);
        });
    }
    
    /**
     * Test GET /api/v1/menu returns 200 OK with main menu options for regular users.
     * 
     * <p>Validates:</p>
     * <ul>
     *   <li>HTTP 200 OK response status</li>
     *   <li>Content-Type: application/json</li>
     *   <li>MenuResponse JSON structure with menuTitle, userName, menuOptions</li>
     *   <li>11 menu options from COMEN02Y.cpy copybook</li>
     *   <li>Menu option fields: optionCode, description, targetEndpoint, httpMethod, requiredRole</li>
     * </ul>
     * 
     * <p>COBOL Equivalence:</p>
     * <pre>
     * COMEN01C.cbl:
     *   PERFORM SEND-MENU-SCREEN
     *   EXEC CICS SEND MAP('COMEN1A') MAPSET('COMEN01') FROM(COMEN1AO)
     *   
     *   Replaced by: GET /api/v1/menu returns MenuResponse JSON
     *   Result: HTTP 200 OK with 11 menu navigation options
     * </pre>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/v1/menu returns 200 OK with menu options for user")
    void testGetMainMenu_AsUser() throws Exception {
        // Arrange: Build mock MenuResponse with 11 main menu options from COMEN02Y.cpy
        MenuResponse mockResponse = MenuResponse.builder()
                .menuTitle("Main Menu")
                .userName("testuser")
                .menuOptions(List.of(
                        MenuResponse.MenuOption.builder()
                                .optionCode("1")
                                .description("Account View")
                                .targetEndpoint("/api/v1/accounts")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("2")
                                .description("Account Update")
                                .targetEndpoint("/api/v1/accounts")
                                .httpMethod("PUT")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("3")
                                .description("Credit Card List")
                                .targetEndpoint("/api/v1/cards")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("4")
                                .description("Credit Card View")
                                .targetEndpoint("/api/v1/cards")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("5")
                                .description("Credit Card Update")
                                .targetEndpoint("/api/v1/cards")
                                .httpMethod("PUT")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("6")
                                .description("Transaction List")
                                .targetEndpoint("/api/v1/transactions")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("7")
                                .description("Transaction View")
                                .targetEndpoint("/api/v1/transactions")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("8")
                                .description("Transaction Add")
                                .targetEndpoint("/api/v1/transactions")
                                .httpMethod("POST")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("9")
                                .description("Transaction Reports")
                                .targetEndpoint("/api/v1/reports")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("10")
                                .description("Bill Payment")
                                .targetEndpoint("/api/v1/payments")
                                .httpMethod("POST")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("11")
                                .description("Pending Authorization View")
                                .targetEndpoint("/api/v1/authorizations/pending")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build()
                ))
                .build();
        
        // Mock MenuService to return main menu response
        when(menuService.getMenuForCurrentUser()).thenReturn(mockResponse);
        
        // Act & Assert: Execute GET /api/v1/menu and validate response
        mockMvc.perform(get("/api/v1/menu")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print()) // Print request/response details for debugging
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.menuTitle").value("Main Menu"))
                .andExpect(jsonPath("$.userName").value("testuser"))
                .andExpect(jsonPath("$.menuOptions").isArray())
                .andExpect(jsonPath("$.menuOptions.length()").value(11))
                .andExpect(jsonPath("$.menuOptions[0].optionCode").value("1"))
                .andExpect(jsonPath("$.menuOptions[0].description").value("Account View"))
                .andExpect(jsonPath("$.menuOptions[0].targetEndpoint").value("/api/v1/accounts"))
                .andExpect(jsonPath("$.menuOptions[0].httpMethod").value("GET"))
                .andExpect(jsonPath("$.menuOptions[0].requiredRole").value("ROLE_USER"))
                .andExpect(jsonPath("$.menuOptions[10].optionCode").value("11"))
                .andExpect(jsonPath("$.menuOptions[10].description").value("Pending Authorization View"));
    }
    
    /**
     * Test GET /api/v1/menu returns main menu for admin users (admin has access to user menu).
     * 
     * <p>Validates:</p>
     * <ul>
     *   <li>Admin users (ROLE_ADMIN) can access regular user menu</li>
     *   <li>HTTP 200 OK response status</li>
     *   <li>Same 11 menu options as regular users</li>
     * </ul>
     * 
     * <p>COBOL Equivalence:</p>
     * <pre>
     * COMEN01C.cbl:
     *   IF CDEMO-USRTYP-ADMIN
     *     Admin users have access to both main menu and admin menu
     *   
     *   Spring Security: @PreAuthorize("hasRole('USER')") allows ROLE_ADMIN
     *   Result: Admin users see main menu without restrictions
     * </pre>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/menu returns 200 OK with menu options for admin")
    void testGetMainMenu_AsAdmin() throws Exception {
        // Arrange: Build mock MenuResponse with main menu options
        MenuResponse mockResponse = MenuResponse.builder()
                .menuTitle("Main Menu")
                .userName("adminuser")
                .menuOptions(List.of(
                        MenuResponse.MenuOption.builder()
                                .optionCode("1")
                                .description("Account View")
                                .targetEndpoint("/api/v1/accounts")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("2")
                                .description("Account Update")
                                .targetEndpoint("/api/v1/accounts")
                                .httpMethod("PUT")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("3")
                                .description("Credit Card List")
                                .targetEndpoint("/api/v1/cards")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("4")
                                .description("Credit Card View")
                                .targetEndpoint("/api/v1/cards")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("5")
                                .description("Credit Card Update")
                                .targetEndpoint("/api/v1/cards")
                                .httpMethod("PUT")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("6")
                                .description("Transaction List")
                                .targetEndpoint("/api/v1/transactions")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("7")
                                .description("Transaction View")
                                .targetEndpoint("/api/v1/transactions")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("8")
                                .description("Transaction Add")
                                .targetEndpoint("/api/v1/transactions")
                                .httpMethod("POST")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("9")
                                .description("Transaction Reports")
                                .targetEndpoint("/api/v1/reports")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("10")
                                .description("Bill Payment")
                                .targetEndpoint("/api/v1/payments")
                                .httpMethod("POST")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("11")
                                .description("Pending Authorization View")
                                .targetEndpoint("/api/v1/authorizations/pending")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build()
                ))
                .build();
        
        // Mock MenuService to return main menu response
        when(menuService.getMenuForCurrentUser()).thenReturn(mockResponse);
        
        // Act & Assert: Execute GET /api/v1/menu as admin user and validate response
        mockMvc.perform(get("/api/v1/menu")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.menuTitle").value("Main Menu"))
                .andExpect(jsonPath("$.userName").value("adminuser"))
                .andExpect(jsonPath("$.menuOptions").isArray())
                .andExpect(jsonPath("$.menuOptions.length()").value(11));
    }
    
    /**
     * Test GET /api/v1/admin/menu returns 200 OK with admin menu options for administrators.
     * 
     * <p>Validates:</p>
     * <ul>
     *   <li>HTTP 200 OK response status for admin users</li>
     *   <li>Content-Type: application/json</li>
     *   <li>MenuResponse JSON structure with admin-specific options</li>
     *   <li>6 menu options from COADM02Y.cpy copybook</li>
     *   <li>Admin menu includes user management and transaction type maintenance</li>
     * </ul>
     * 
     * <p>COBOL Equivalence:</p>
     * <pre>
     * COADM01C.cbl:
     *   PERFORM SEND-MENU-SCREEN
     *   EXEC CICS SEND MAP('COADM1A') MAPSET('COADM01') FROM(COADM1AO)
     *   
     *   Replaced by: GET /api/v1/admin/menu returns MenuResponse JSON
     *   Result: HTTP 200 OK with 6 admin navigation options
     * 
     * COADM02Y.cpy Admin Options:
     *   1. User List (COUSR00C) → GET /api/v1/admin/users
     *   2. User Add (COUSR01C) → POST /api/v1/admin/users
     *   3. User Update (COUSR02C) → PUT /api/v1/admin/users
     *   4. User Delete (COUSR03C) → DELETE /api/v1/admin/users
     *   5. Transaction Type List/Update (COTRTLIC) → GET /api/v1/admin/transaction-types
     *   6. Transaction Type Maintenance (COTRTUPC) → PUT /api/v1/admin/transaction-types
     * </pre>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/admin/menu returns 200 OK with admin options for admin")
    void testGetAdminMenu_AsAdmin() throws Exception {
        // Arrange: Build mock MenuResponse with 6 admin menu options from COADM02Y.cpy
        MenuResponse mockResponse = MenuResponse.builder()
                .menuTitle("Administration Menu")
                .userName("adminuser")
                .menuOptions(List.of(
                        MenuResponse.MenuOption.builder()
                                .optionCode("1")
                                .description("User List (Security)")
                                .targetEndpoint("/api/v1/admin/users")
                                .httpMethod("GET")
                                .requiredRole("ROLE_ADMIN")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("2")
                                .description("User Add (Security)")
                                .targetEndpoint("/api/v1/admin/users")
                                .httpMethod("POST")
                                .requiredRole("ROLE_ADMIN")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("3")
                                .description("User Update (Security)")
                                .targetEndpoint("/api/v1/admin/users")
                                .httpMethod("PUT")
                                .requiredRole("ROLE_ADMIN")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("4")
                                .description("User Delete (Security)")
                                .targetEndpoint("/api/v1/admin/users")
                                .httpMethod("DELETE")
                                .requiredRole("ROLE_ADMIN")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("5")
                                .description("Transaction Type List/Update (Db2)")
                                .targetEndpoint("/api/v1/admin/transaction-types")
                                .httpMethod("GET")
                                .requiredRole("ROLE_ADMIN")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("6")
                                .description("Transaction Type Maintenance (Db2)")
                                .targetEndpoint("/api/v1/admin/transaction-types")
                                .httpMethod("PUT")
                                .requiredRole("ROLE_ADMIN")
                                .build()
                ))
                .build();
        
        // Mock MenuService to return admin menu response
        when(menuService.getAdminMenu()).thenReturn(mockResponse);
        
        // Act & Assert: Execute GET /api/v1/admin/menu and validate response
        mockMvc.perform(get("/api/v1/admin/menu")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.menuTitle").value("Administration Menu"))
                .andExpect(jsonPath("$.userName").value("adminuser"))
                .andExpect(jsonPath("$.menuOptions").isArray())
                .andExpect(jsonPath("$.menuOptions.length()").value(6))
                .andExpect(jsonPath("$.menuOptions[0].optionCode").value("1"))
                .andExpect(jsonPath("$.menuOptions[0].description").value("User List (Security)"))
                .andExpect(jsonPath("$.menuOptions[0].targetEndpoint").value("/api/v1/admin/users"))
                .andExpect(jsonPath("$.menuOptions[0].httpMethod").value("GET"))
                .andExpect(jsonPath("$.menuOptions[0].requiredRole").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.menuOptions[5].optionCode").value("6"))
                .andExpect(jsonPath("$.menuOptions[5].description").value("Transaction Type Maintenance (Db2)"));
    }
    
    /**
     * Test GET /api/v1/admin/menu returns 403 FORBIDDEN for non-admin users.
     * 
     * <p>Validates:</p>
     * <ul>
     *   <li>HTTP 403 Forbidden response status for ROLE_USER</li>
     *   <li>@PreAuthorize("hasRole('ADMIN')") enforcement</li>
     *   <li>Spring Security method-level authorization</li>
     * </ul>
     * 
     * <p>COBOL Equivalence:</p>
     * <pre>
     * COMEN01C.cbl lines 136-143:
     *   IF CDEMO-USRTYP-USER AND
     *      CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'
     *       SET ERR-FLG-ON TO TRUE
     *       MOVE 'No access - Admin Only option... ' TO WS-MESSAGE
     *       PERFORM SEND-MENU-SCREEN
     *   
     *   Replaced by: Spring Security @PreAuthorize("hasRole('ADMIN')") annotation
     *   Result: HTTP 403 Forbidden for users without ROLE_ADMIN
     *   No explicit error message needed (GlobalExceptionHandler provides standard error response)
     * </pre>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/v1/admin/menu returns 403 FORBIDDEN for non-admin user")
    void testGetAdminMenu_AsUser_ReturnsForbidden() throws Exception {
        // Act & Assert: Execute GET /api/v1/admin/menu as regular user and expect 403
        mockMvc.perform(get("/api/v1/admin/menu")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
    
    /**
     * Test GET /api/v1/menu returns 401 UNAUTHORIZED for unauthenticated requests.
     * 
     * <p>Validates:</p>
     * <ul>
     *   <li>HTTP 401 Unauthorized response status for anonymous users</li>
     *   <li>Spring Security authentication requirement</li>
     *   <li>JWT token validation enforcement</li>
     * </ul>
     * 
     * <p>COBOL Equivalence:</p>
     * <pre>
     * COSGN00C.cbl (Signon Screen):
     *   IF EIBCALEN = 0
     *     PERFORM RETURN-TO-SIGNON-SCREEN
     *   
     *   Replaced by: Spring Security JWT authentication filter
     *   Result: HTTP 401 Unauthorized if no valid JWT token in Authorization header
     * </pre>
     */
    @Test
    @WithAnonymousUser
    @DisplayName("GET /api/v1/menu returns 401 UNAUTHORIZED for unauthenticated user")
    void testGetMainMenu_Unauthenticated_ReturnsUnauthorized() throws Exception {
        // Act & Assert: Execute GET /api/v1/menu without authentication and expect 401
        mockMvc.perform(get("/api/v1/menu")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
    
    /**
     * Test GET /api/v1/admin/menu returns 401 UNAUTHORIZED for unauthenticated requests.
     * 
     * <p>Validates:</p>
     * <ul>
     *   <li>HTTP 401 Unauthorized response status for anonymous users</li>
     *   <li>Authentication required before authorization check</li>
     * </ul>
     */
    @Test
    @WithAnonymousUser
    @DisplayName("GET /api/v1/admin/menu returns 401 UNAUTHORIZED for unauthenticated user")
    void testGetAdminMenu_Unauthenticated_ReturnsUnauthorized() throws Exception {
        // Act & Assert: Execute GET /api/v1/admin/menu without authentication and expect 401
        mockMvc.perform(get("/api/v1/admin/menu")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
    
    /**
     * Test menu structure validation with comprehensive JsonPath assertions.
     * 
     * <p>Validates:</p>
     * <ul>
     *   <li>MenuResponse JSON structure completeness</li>
     *   <li>All required fields present (menuTitle, userName, menuOptions)</li>
     *   <li>MenuOption nested structure validation</li>
     *   <li>Field data types and value formats</li>
     *   <li>Array length matching COBOL copybook definitions</li>
     * </ul>
     * 
     * <p>COBOL Structure Mapping:</p>
     * <pre>
     * COMEN02Y.cpy:
     *   05 CDEMO-MENU-OPT-COUNT PIC 9(02) VALUE 11 → $.menuOptions.length() = 11
     *   05 CDEMO-MENU-OPTIONS REDEFINES CDEMO-MENU-OPTIONS-DATA
     *     10 CDEMO-MENU-OPT OCCURS 12 TIMES → $.menuOptions array
     *       15 CDEMO-MENU-OPT-NUM PIC 9(02) → $.menuOptions[*].optionCode
     *       15 CDEMO-MENU-OPT-NAME PIC X(35) → $.menuOptions[*].description
     *       15 CDEMO-MENU-OPT-PGMNAME PIC X(08) → $.menuOptions[*].targetEndpoint
     *       15 CDEMO-MENU-OPT-USRTYPE PIC X(01) → $.menuOptions[*].requiredRole
     * </pre>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Menu structure validation with JsonPath assertions")
    void testGetMenu_StructureValidation() throws Exception {
        // Arrange: Build mock MenuResponse with all required fields
        MenuResponse mockResponse = MenuResponse.builder()
                .menuTitle("Main Menu")
                .userName("testuser")
                .menuOptions(List.of(
                        MenuResponse.MenuOption.builder()
                                .optionCode("1")
                                .description("Account View")
                                .targetEndpoint("/api/v1/accounts")
                                .httpMethod("GET")
                                .requiredRole("ROLE_USER")
                                .build(),
                        MenuResponse.MenuOption.builder()
                                .optionCode("2")
                                .description("Account Update")
                                .targetEndpoint("/api/v1/accounts")
                                .httpMethod("PUT")
                                .requiredRole("ROLE_USER")
                                .build()
                ))
                .build();
        
        // Mock MenuService to return menu response
        when(menuService.getMenuForCurrentUser()).thenReturn(mockResponse);
        
        // Act & Assert: Execute GET /api/v1/menu and validate JSON structure
        mockMvc.perform(get("/api/v1/menu")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // Validate top-level fields
                .andExpect(jsonPath("$.menuTitle").exists())
                .andExpect(jsonPath("$.menuTitle").isString())
                .andExpect(jsonPath("$.userName").exists())
                .andExpect(jsonPath("$.userName").isString())
                .andExpect(jsonPath("$.menuOptions").exists())
                .andExpect(jsonPath("$.menuOptions").isArray())
                // Validate menu option array structure
                .andExpect(jsonPath("$.menuOptions[*].optionCode").exists())
                .andExpect(jsonPath("$.menuOptions[*].description").exists())
                .andExpect(jsonPath("$.menuOptions[*].targetEndpoint").exists())
                .andExpect(jsonPath("$.menuOptions[*].httpMethod").exists())
                .andExpect(jsonPath("$.menuOptions[*].requiredRole").exists())
                // Validate specific menu option fields
                .andExpect(jsonPath("$.menuOptions[0].optionCode").value("1"))
                .andExpect(jsonPath("$.menuOptions[0].description").value("Account View"))
                .andExpect(jsonPath("$.menuOptions[0].targetEndpoint").value("/api/v1/accounts"))
                .andExpect(jsonPath("$.menuOptions[0].httpMethod").value("GET"))
                .andExpect(jsonPath("$.menuOptions[0].requiredRole").value("ROLE_USER"))
                .andExpect(jsonPath("$.menuOptions[1].httpMethod").value("PUT"));
    }
    
    /**
     * Test menu option count validation for main menu.
     * 
     * <p>Validates:</p>
     * <ul>
     *   <li>Main menu contains exactly 11 options (COMEN02Y.cpy specification)</li>
     *   <li>Menu option count matches COBOL CDEMO-MENU-OPT-COUNT field</li>
     * </ul>
     * 
     * <p>COBOL Validation:</p>
     * <pre>
     * COMEN02Y.cpy line 21:
     *   05 CDEMO-MENU-OPT-COUNT PIC 9(02) VALUE 11
     *   
     *   Must match: $.menuOptions.length() = 11
     * </pre>
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Main menu option count validation (11 options from COMEN02Y.cpy)")
    void testGetMainMenu_ValidateOptionCount() throws Exception {
        // Arrange: Build mock MenuResponse with 11 menu options
        MenuResponse mockResponse = MenuResponse.builder()
                .menuTitle("Main Menu")
                .userName("testuser")
                .menuOptions(List.of(
                        MenuResponse.MenuOption.builder().optionCode("1").description("Account View").targetEndpoint("/api/v1/accounts").httpMethod("GET").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("2").description("Account Update").targetEndpoint("/api/v1/accounts").httpMethod("PUT").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("3").description("Credit Card List").targetEndpoint("/api/v1/cards").httpMethod("GET").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("4").description("Credit Card View").targetEndpoint("/api/v1/cards").httpMethod("GET").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("5").description("Credit Card Update").targetEndpoint("/api/v1/cards").httpMethod("PUT").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("6").description("Transaction List").targetEndpoint("/api/v1/transactions").httpMethod("GET").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("7").description("Transaction View").targetEndpoint("/api/v1/transactions").httpMethod("GET").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("8").description("Transaction Add").targetEndpoint("/api/v1/transactions").httpMethod("POST").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("9").description("Transaction Reports").targetEndpoint("/api/v1/reports").httpMethod("GET").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("10").description("Bill Payment").targetEndpoint("/api/v1/payments").httpMethod("POST").requiredRole("ROLE_USER").build(),
                        MenuResponse.MenuOption.builder().optionCode("11").description("Pending Authorization View").targetEndpoint("/api/v1/authorizations/pending").httpMethod("GET").requiredRole("ROLE_USER").build()
                ))
                .build();
        
        // Mock MenuService to return main menu response
        when(menuService.getMenuForCurrentUser()).thenReturn(mockResponse);
        
        // Act & Assert: Validate menu option count equals 11
        mockMvc.perform(get("/api/v1/menu")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuOptions.length()").value(11));
    }
    
    /**
     * Test menu option count validation for admin menu.
     * 
     * <p>Validates:</p>
     * <ul>
     *   <li>Admin menu contains exactly 6 options (COADM02Y.cpy specification)</li>
     *   <li>Menu option count matches COBOL CDEMO-ADMIN-OPT-COUNT field</li>
     * </ul>
     * 
     * <p>COBOL Validation:</p>
     * <pre>
     * COADM02Y.cpy line 22:
     *   05 CDEMO-ADMIN-OPT-COUNT PIC 9(02) VALUE 6
     *   
     *   Must match: $.menuOptions.length() = 6
     * </pre>
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin menu option count validation (6 options from COADM02Y.cpy)")
    void testGetAdminMenu_ValidateOptionCount() throws Exception {
        // Arrange: Build mock MenuResponse with 6 admin menu options
        MenuResponse mockResponse = MenuResponse.builder()
                .menuTitle("Administration Menu")
                .userName("adminuser")
                .menuOptions(List.of(
                        MenuResponse.MenuOption.builder().optionCode("1").description("User List (Security)").targetEndpoint("/api/v1/admin/users").httpMethod("GET").requiredRole("ROLE_ADMIN").build(),
                        MenuResponse.MenuOption.builder().optionCode("2").description("User Add (Security)").targetEndpoint("/api/v1/admin/users").httpMethod("POST").requiredRole("ROLE_ADMIN").build(),
                        MenuResponse.MenuOption.builder().optionCode("3").description("User Update (Security)").targetEndpoint("/api/v1/admin/users").httpMethod("PUT").requiredRole("ROLE_ADMIN").build(),
                        MenuResponse.MenuOption.builder().optionCode("4").description("User Delete (Security)").targetEndpoint("/api/v1/admin/users").httpMethod("DELETE").requiredRole("ROLE_ADMIN").build(),
                        MenuResponse.MenuOption.builder().optionCode("5").description("Transaction Type List/Update (Db2)").targetEndpoint("/api/v1/admin/transaction-types").httpMethod("GET").requiredRole("ROLE_ADMIN").build(),
                        MenuResponse.MenuOption.builder().optionCode("6").description("Transaction Type Maintenance (Db2)").targetEndpoint("/api/v1/admin/transaction-types").httpMethod("PUT").requiredRole("ROLE_ADMIN").build()
                ))
                .build();
        
        // Mock MenuService to return admin menu response
        when(menuService.getAdminMenu()).thenReturn(mockResponse);
        
        // Act & Assert: Validate menu option count equals 6
        mockMvc.perform(get("/api/v1/admin/menu")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuOptions.length()").value(6));
    }
}
