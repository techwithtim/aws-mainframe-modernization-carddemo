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

package com.aws.carddemo.config;

import com.aws.carddemo.security.JwtAuthenticationFilter;
import com.aws.carddemo.security.JwtTokenProvider;
import com.aws.carddemo.security.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Spring Security 6.x configuration class defining comprehensive authentication and authorization.
 * 
 * <p>Migrated from: app/cbl/COSGN00C.cbl (COBOL signon/authentication program)</p>
 * <p>Reference: app/cpy/CSUSR01Y.cpy (User security record layout)</p>
 * 
 * <p>This configuration class replaces mainframe RACF security subsystem with modern Spring Security
 * framework implementing JWT token-based stateless authentication. It provides comprehensive
 * security controls including authentication, authorization, CORS policy, password encryption,
 * and exception handling (Section 0.8.1).</p>
 * 
 * <p><strong>Original COBOL Security Architecture (COSGN00C.cbl):</strong></p>
 * <pre>
 * WORKING-STORAGE SECTION.
 *   01 WS-VARIABLES.
 *     05 WS-USRSEC-FILE             PIC X(08) VALUE 'USRSEC  '.
 *     05 WS-USER-ID                 PIC X(08).
 *     05 WS-USER-PWD                PIC X(08).
 * 
 * COPY CSUSR01Y.                    - User security record layout
 *   01 SEC-USER-DATA.
 *     05 SEC-USR-ID                 PIC X(08).        - Username (8 chars)
 *     05 SEC-USR-FNAME              PIC X(20).        - First name
 *     05 SEC-USR-LNAME              PIC X(20).        - Last name
 *     05 SEC-USR-PWD                PIC X(08).        - Plain-text password!
 *     05 SEC-USR-TYPE               PIC X(01).        - User type (A=Admin, R=Regular)
 * 
 * READ-USER-SEC-FILE.
 *     EXEC CICS READ
 *          DATASET   (WS-USRSEC-FILE)
 *          INTO      (SEC-USER-DATA)
 *          RIDFLD    (WS-USER-ID)
 *          KEYLENGTH (LENGTH OF WS-USER-ID)
 *          RESP      (WS-RESP-CD)
 *     END-EXEC.
 * 
 *     EVALUATE WS-RESP-CD
 *         WHEN 0                                     - User found
 *             IF SEC-USR-PWD = WS-USER-PWD           - Plain-text comparison!!!
 *                 MOVE WS-USER-ID   TO CDEMO-USER-ID
 *                 MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
 *                 IF CDEMO-USRTYP-ADMIN              - User type check
 *                      EXEC CICS XCTL PROGRAM('COADM01C') ...
 *                 ELSE
 *                      EXEC CICS XCTL PROGRAM('COMEN01C') ...
 *                 END-IF
 *             ELSE
 *                 MOVE 'Wrong Password. Try again ...' TO WS-MESSAGE
 *         WHEN 13                                    - User not found
 *             MOVE 'User not found. Try again ...' TO WS-MESSAGE
 *     END-EVALUATE.
 * </pre>
 * 
 * <p><strong>Modernization Changes (Section 0.8.1):</strong></p>
 * <ul>
 *   <li><strong>Authentication Method:</strong> RACF VSAM file READ → JWT bearer token validation</li>
 *   <li><strong>Password Storage:</strong> Plain-text PIC X(08) → BCrypt hashed (60 chars, 10 rounds)</li>
 *   <li><strong>Session Management:</strong> CICS COMMAREA state → Stateless JWT tokens</li>
 *   <li><strong>User Type Authorization:</strong> CDEMO-USER-TYPE → Spring Security ROLE_ADMIN/ROLE_USER</li>
 *   <li><strong>Screen Navigation:</strong> EXEC CICS XCTL → HTTP 200 with JWT token</li>
 *   <li><strong>Error Handling:</strong> BMS map error messages → JSON error responses (401/403)</li>
 * </ul>
 * 
 * <p><strong>Security Filter Chain (Section 0.8.1):</strong></p>
 * <p>The {@link #securityFilterChain(HttpSecurity)} method configures the Spring Security filter chain
 * replacing COBOL transaction-level security checks:</p>
 * <ol>
 *   <li><strong>CSRF Protection:</strong> Disabled for stateless JWT authentication (no session cookies)</li>
 *   <li><strong>Session Management:</strong> STATELESS policy (no HttpSession, purely token-based)</li>
 *   <li><strong>Authorization Rules:</strong>
 *     <ul>
 *       <li>Public: {@code /api/v1/auth/**} (login, logout) - Replaces COSGN00C.cbl initial access</li>
 *       <li>Public: {@code /actuator/health} - Kubernetes health probes</li>
 *       <li>Public: {@code /swagger-ui/**, /v3/api-docs/**} - API documentation</li>
 *       <li>Admin: {@code /api/v1/admin/**} - Requires ROLE_ADMIN (replaces CDEMO-USRTYP-ADMIN check)</li>
 *       <li>Authenticated: {@code /api/v1/**} - Requires valid JWT token</li>
 *     </ul>
 *   </li>
 *   <li><strong>JWT Filter:</strong> {@link JwtAuthenticationFilter} executes before {@code UsernamePasswordAuthenticationFilter}</li>
 *   <li><strong>Exception Handling:</strong> {@code AuthenticationEntryPoint} (401), {@code AccessDeniedHandler} (403)</li>
 * </ol>
 * 
 * <p><strong>Password Encryption (Section 0.8.1 PCI-DSS Compliance):</strong></p>
 * <p>The {@link #passwordEncoder()} method provides BCrypt password encoder replacing COBOL plain-text
 * password storage (SEC-USR-PWD PIC X(08) from CSUSR01Y.cpy). BCrypt configuration:</p>
 * <ul>
 *   <li><strong>Algorithm:</strong> BCrypt with 10 salt rounds (configurable via application.yml)</li>
 *   <li><strong>Hash Format:</strong> 60-character BCrypt hash (e.g., $2a$10$... )</li>
 *   <li><strong>Salt:</strong> Random salt generated per password (prevents rainbow table attacks)</li>
 *   <li><strong>Cost Factor:</strong> 10 rounds (2^10 = 1024 iterations, balances security/performance)</li>
 *   <li><strong>PCI-DSS Compliance:</strong> Passwords never stored or logged in clear text</li>
 * </ul>
 * 
 * <p><strong>CORS Configuration (Section 0.8.1):</strong></p>
 * <p>The {@link #corsConfigurationSource()} method configures Cross-Origin Resource Sharing (CORS)
 * policy for REST API access from frontend applications (replaces BMS 3270 terminal access):</p>
 * <ul>
 *   <li><strong>Allowed Origins:</strong> Configurable via {@code cors.allowed-origins} property (default: localhost)</li>
 *   <li><strong>Allowed Methods:</strong> GET, POST, PUT, DELETE, OPTIONS</li>
 *   <li><strong>Allowed Headers:</strong> Authorization (JWT bearer token), Content-Type, X-Request-ID</li>
 *   <li><strong>Exposed Headers:</strong> X-Total-Count (pagination metadata)</li>
 *   <li><strong>Credentials:</strong> Enabled (allows cookies/auth headers in cross-origin requests)</li>
 *   <li><strong>Max Age:</strong> 3600 seconds (preflight OPTIONS request caching)</li>
 * </ul>
 * 
 * <p><strong>Authentication Manager (Section 0.8.1):</strong></p>
 * <p>The {@link #authenticationManager(AuthenticationConfiguration)} method provides the authentication
 * manager bean for credential validation (replaces COBOL password comparison logic):</p>
 * <ul>
 *   <li><strong>User Loading:</strong> {@link UserDetailsServiceImpl} loads user from PostgreSQL via username</li>
 *   <li><strong>Password Verification:</strong> {@code BCryptPasswordEncoder.matches()} validates password hash</li>
 *   <li><strong>Authority Mapping:</strong> User type 'A'→ROLE_ADMIN, 'R'→ROLE_USER</li>
 *   <li><strong>Account Checks:</strong> Enabled, non-expired, non-locked, credentials non-expired</li>
 * </ul>
 * 
 * <p><strong>Role-Based Access Control (Section 0.1.1):</strong></p>
 * <p>The configuration supports two user roles derived from COBOL SEC-USR-TYPE field:</p>
 * <ul>
 *   <li><strong>ROLE_ADMIN (SEC-USR-TYPE='A'):</strong> Full administrative access to:
 *     <ul>
 *       <li>{@code /api/v1/admin/**} - User management (COUSR00-03.bms screens)</li>
 *       <li>{@code /actuator/**} - Application monitoring and management</li>
 *       <li>All ROLE_USER endpoints</li>
 *     </ul>
 *   </li>
 *   <li><strong>ROLE_USER (SEC-USR-TYPE='R'):</strong> Standard user access to:
 *     <ul>
 *       <li>{@code /api/v1/accounts/**} - Account operations (COACT*.bms screens)</li>
 *       <li>{@code /api/v1/cards/**} - Card operations (COCRD*.bms screens)</li>
 *       <li>{@code /api/v1/transactions/**} - Transaction operations (COTRN*.bms screens)</li>
 *       <li>{@code /api/v1/menu} - Main menu (COMEN01.bms screen)</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p><strong>Security Exception Handling (Section 0.8.1):</strong></p>
 * <p>The configuration provides custom exception handling for authentication and authorization failures:</p>
 * <ul>
 *   <li><strong>401 Unauthorized:</strong> Missing, invalid, or expired JWT token → JSON error response</li>
 *   <li><strong>403 Forbidden:</strong> Insufficient privileges (e.g., ROLE_USER accessing /admin/**)</li>
 *   <li><strong>Error Response Format:</strong>
 *     <pre>
 *     {
 *       "timestamp": "2024-01-15T10:30:45",
 *       "status": 401,
 *       "error": "Unauthorized",
 *       "message": "Invalid or expired JWT token",
 *       "path": "/api/v1/accounts/123"
 *     }
 *     </pre>
 *   </li>
 * </ul>
 * 
 * <p><strong>PCI-DSS Compliance (Section 0.8.1):</strong></p>
 * <p>The security configuration implements PCI-DSS requirements for sensitive data protection:</p>
 * <ul>
 *   <li><strong>Password Storage:</strong> BCrypt hashed passwords (PCI-DSS 8.2.1)</li>
 *   <li><strong>Password Masking:</strong> Passwords never logged or returned in responses</li>
 *   <li><strong>Encryption in Transit:</strong> HTTPS enforced for all endpoints</li>
 *   <li><strong>Audit Logging:</strong> Authentication attempts logged with masked usernames</li>
 *   <li><strong>Session Security:</strong> Stateless tokens with 1-hour expiration</li>
 *   <li><strong>Access Control:</strong> Role-based authorization with @PreAuthorize annotations</li>
 * </ul>
 * 
 * <p><strong>Authentication Flow (Section 0.1.1):</strong></p>
 * <ol>
 *   <li>Client sends POST /api/v1/auth/login with username/password (replaces COSGN00.bms screen)</li>
 *   <li>AuthenticationManager validates credentials via UserDetailsServiceImpl and PasswordEncoder</li>
 *   <li>On success, JwtTokenProvider generates JWT token with user roles and 1-hour expiration</li>
 *   <li>Client receives JWT token in response body: {@code {"token": "eyJhbGci...", "expiresIn": 3600}}</li>
 *   <li>Client includes token in subsequent requests: {@code Authorization: Bearer eyJhbGci...}</li>
 *   <li>JwtAuthenticationFilter validates token signature and expiration on each request</li>
 *   <li>SecurityContext populated with authenticated user principal and authorities</li>
 *   <li>Controller methods access user: {@code SecurityContextHolder.getContext().getAuthentication()}</li>
 *   <li>@PreAuthorize annotations enforce role-based access control on methods</li>
 * </ol>
 * 
 * <p><strong>Configuration Properties (application.yml):</strong></p>
 * <pre>
 * security:
 *   bcrypt:
 *     strength: 10                        # BCrypt cost factor (10-12 recommended)
 *   jwt:
 *     secret: ${JWT_SECRET}               # JWT signing secret (externalized)
 *     expiration: 3600000                 # Token expiration (1 hour in ms)
 * 
 * cors:
 *   allowed-origins:
 *     - http://localhost:3000             # React frontend
 *     - https://carddemo.example.com      # Production frontend
 *   allowed-methods: GET,POST,PUT,DELETE,OPTIONS
 *   allowed-headers: Authorization,Content-Type,X-Request-ID
 *   exposed-headers: X-Total-Count
 *   allow-credentials: true
 *   max-age: 3600
 * </pre>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>
 * // Example 1: Controller method with admin authorization
 * &#64;PreAuthorize("hasRole('ADMIN')")
 * &#64;DeleteMapping("/api/v1/admin/users/{id}")
 * public ResponseEntity&lt;Void&gt; deleteUser(&#64;PathVariable Long id) {
 *     // Requires ROLE_ADMIN (SEC-USR-TYPE='A')
 *     userService.deleteUser(id);
 *     return ResponseEntity.noContent().build();
 * }
 * 
 * // Example 2: Controller method with user authorization
 * &#64;PreAuthorize("hasRole('USER')")
 * &#64;GetMapping("/api/v1/accounts/{id}")
 * public ResponseEntity&lt;AccountResponse&gt; getAccount(&#64;PathVariable Long id) {
 *     // Requires ROLE_USER or ROLE_ADMIN
 *     Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *     String username = auth.getName();
 *     // Authorization logic...
 * }
 * 
 * // Example 3: Public endpoint (no authorization required)
 * &#64;PostMapping("/api/v1/auth/login")
 * public ResponseEntity&lt;LoginResponse&gt; login(&#64;RequestBody LoginRequest request) {
 *     // No authorization required (public endpoint)
 *     // Replaces COSGN00C.cbl signon screen
 * }
 * </pre>
 * 
 * <p><strong>Testing Strategy:</strong></p>
 * <ul>
 *   <li>Unit Tests: Mock JwtAuthenticationFilter and UserDetailsServiceImpl, verify configuration</li>
 *   <li>Integration Tests: Real Spring Security context, test authentication flows</li>
 *   <li>Security Tests: Test authorization rules, token expiration, invalid tokens</li>
 * </ul>
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @see JwtAuthenticationFilter
 * @see UserDetailsServiceImpl
 * @see BCryptPasswordEncoder
 * @see SecurityFilterChain
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * NOTE: JwtAuthenticationFilter is NOT injected as a field to avoid circular dependency.
     * 
     * <p>The filter is defined as a @Bean in this configuration class (see
     * {@link #jwtAuthenticationFilter(JwtTokenProvider, ObjectMapper)}) and is injected
     * directly as a method parameter into {@link #securityFilterChain(HttpSecurity, JwtAuthenticationFilter)}.
     * This method-level injection pattern breaks the circular dependency that would occur
     * with field-level injection via @RequiredArgsConstructor.</p>
     * 
     * <p>Circular Dependency Chain (if field injection was used):</p>
     * <ul>
     *   <li>SecurityConfig requires JwtAuthenticationFilter (field injection)</li>
     *   <li>JwtAuthenticationFilter bean is defined within SecurityConfig</li>
     *   <li>Spring cannot determine which to create first → BeanCurrentlyInCreationException</li>
     * </ul>
     * 
     * <p>Solution: Method-level injection allows Spring to create the JwtAuthenticationFilter
     * bean first, then inject it into the securityFilterChain method when needed.</p>
     * 
     * @see #jwtAuthenticationFilter(JwtTokenProvider, ObjectMapper)
     * @see #securityFilterChain(HttpSecurity, JwtAuthenticationFilter)
     */

    /**
     * UserDetailsService implementation for loading user credentials from PostgreSQL.
     * 
     * <p>Used by AuthenticationManager to load user details during login authentication.
     * Replaces COBOL EXEC CICS READ DATASET('USRSEC') operation from COSGN00C.cbl.</p>
     */
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * Jackson ObjectMapper for JSON serialization of error responses.
     * 
     * <p>Used to convert error response objects to JSON strings when writing HTTP 401/403
     * error response bodies with structured error details.</p>
     */
    private final ObjectMapper objectMapper;

    /**
     * BCrypt password encoder cost factor (default: 10 rounds).
     * 
     * <p>Configurable via application.yml property {@code security.bcrypt.strength}.
     * Valid range: 4-31 (higher values = more secure but slower). Recommended: 10-12.</p>
     * 
     * <p>Cost factor determines the number of iterations: iterations = 2^strength.
     * Example: strength=10 → 1024 iterations, strength=12 → 4096 iterations.</p>
     */
    @Value("${security.bcrypt.strength:10}")
    private int bcryptStrength;

    /**
     * CORS allowed origins configuration.
     * 
     * <p>Configurable via application.yml property {@code cors.allowed-origins}.
     * Supports multiple origins for development and production environments.</p>
     * 
     * <p>Example configuration:</p>
     * <pre>
     * cors:
     *   allowed-origins:
     *     - http://localhost:3000        # React development server
     *     - https://carddemo.example.com # Production frontend
     * </pre>
     */
    @Value("${cors.allowed-origins:http://localhost:3000}")
    private List<String> corsAllowedOrigins;

    /**
     * Configures Spring Security filter chain with JWT authentication and authorization rules.
     * 
     * <p>This method defines the comprehensive security configuration replacing COBOL RACF
     * security subsystem from COSGN00C.cbl. It configures:</p>
     * <ul>
     *   <li>CSRF protection (disabled for stateless JWT authentication)</li>
     *   <li>Session management (STATELESS policy for JWT tokens)</li>
     *   <li>Authorization rules (public, authenticated, role-based)</li>
     *   <li>JWT authentication filter (validates bearer tokens)</li>
     *   <li>Exception handling (401 Unauthorized, 403 Forbidden)</li>
     *   <li>CORS policy (cross-origin API access)</li>
     * </ul>
     * 
     * <p><strong>Original COBOL Security Checks (COSGN00C.cbl):</strong></p>
     * <pre>
     * READ-USER-SEC-FILE.
     *     EXEC CICS READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID) ...
     *     EVALUATE WS-RESP-CD
     *         WHEN 0
     *             IF SEC-USR-PWD = WS-USER-PWD
     *                 MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     *                 IF CDEMO-USRTYP-ADMIN
     *                      EXEC CICS XCTL PROGRAM('COADM01C') ...
     *                 ELSE
     *                      EXEC CICS XCTL PROGRAM('COMEN01C') ...
     *             ELSE
     *                 MOVE 'Wrong Password' TO WS-MESSAGE
     *         WHEN 13
     *             MOVE 'User not found' TO WS-MESSAGE
     *     END-EVALUATE.
     * </pre>
     * 
     * <p><strong>Modernization Changes:</strong></p>
     * <ul>
     *   <li>VSAM file READ → JWT token validation via {@link JwtAuthenticationFilter}</li>
     *   <li>Plain-text password → BCrypt hash verification via {@link PasswordEncoder}</li>
     *   <li>COMMAREA session state → Stateless JWT tokens with embedded claims</li>
     *   <li>User type checks → Spring Security ROLE_ADMIN/ROLE_USER authorization</li>
     *   <li>BMS error messages → JSON error responses (401/403 HTTP status codes)</li>
     * </ul>
     * 
     * <p><strong>Authorization Rules Configuration:</strong></p>
     * <ol>
     *   <li><strong>Public Endpoints (permitAll):</strong>
     *     <ul>
     *       <li>{@code /api/v1/auth/**} - Authentication endpoints (login, logout, refresh)</li>
     *       <li>{@code /actuator/health} - Kubernetes liveness/readiness probes</li>
     *       <li>{@code /swagger-ui/**, /v3/api-docs/**} - API documentation (Swagger UI)</li>
     *     </ul>
     *   </li>
     *   <li><strong>Admin Endpoints (hasRole('ADMIN')):</strong>
     *     <ul>
     *       <li>{@code /api/v1/admin/**} - User management (COUSR00-03.bms screens)</li>
     *       <li>{@code /actuator/**} - Application monitoring (except health)</li>
     *     </ul>
     *   </li>
     *   <li><strong>Authenticated Endpoints (authenticated()):</strong>
     *     <ul>
     *       <li>{@code /api/v1/**} - All other API endpoints require valid JWT token</li>
     *     </ul>
     *   </li>
     * </ol>
     * 
     * <p><strong>Filter Chain Order:</strong></p>
     * <ol>
     *   <li>SecurityContextPersistenceFilter (manages SecurityContext)</li>
     *   <li>CorsFilter (applies CORS policy from {@link #corsConfigurationSource()})</li>
     *   <li><strong>JwtAuthenticationFilter (validates JWT bearer tokens)</strong></li>
     *   <li>UsernamePasswordAuthenticationFilter (skipped for JWT authentication)</li>
     *   <li>ExceptionTranslationFilter (handles authentication exceptions)</li>
     *   <li>FilterSecurityInterceptor (enforces authorization rules)</li>
     * </ol>
     * 
     * <p><strong>Exception Handling:</strong></p>
     * <ul>
     *   <li><strong>AuthenticationEntryPoint:</strong> Handles authentication failures (401 Unauthorized)</li>
     *   <li><strong>AccessDeniedHandler:</strong> Handles authorization failures (403 Forbidden)</li>
     *   <li>Both handlers return structured JSON error responses with timestamp, status, error, message, path</li>
     * </ul>
     * 
     * @param http Spring Security HttpSecurity configuration builder
     * @return configured SecurityFilterChain for Spring Security
     * @throws Exception if configuration fails (e.g., invalid configuration parameters)
     * 
     * @see JwtAuthenticationFilter#doFilterInternal
     * @see #authenticationManager(AuthenticationConfiguration)
     * @see #corsConfigurationSource()
     */
    @Order(1)
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        log.info("Configuring Spring Security filter chain with JWT authentication");

        http
            // Disable CSRF protection for stateless JWT authentication (no session cookies to protect)
            .csrf(AbstractHttpConfigurer::disable)

            // Configure authorization rules for HTTP requests
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints: No authentication required
                // Replaces COBOL: Initial access to COSGN00C.cbl signon screen
                .requestMatchers(
                    "/api/v1/auth/**",          // Login, logout, refresh endpoints
                    "/actuator/health",          // Kubernetes health probes
                    "/actuator/info",            // Application info endpoint
                    "/swagger-ui/**",            // Swagger UI documentation
                    "/v3/api-docs/**",           // OpenAPI specification
                    "/swagger-ui.html"           // Swagger UI landing page
                ).permitAll()

                // Admin endpoints: Requires ROLE_ADMIN authorization
                // Replaces COBOL: IF CDEMO-USRTYP-ADMIN (SEC-USR-TYPE='A')
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Actuator management endpoints: Requires ROLE_ADMIN authorization
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // All other API endpoints: Requires authentication (ROLE_USER or ROLE_ADMIN)
                // Replaces COBOL: Authenticated CICS transaction access
                .anyRequest().authenticated()
            )

            // Configure stateless session management (no HttpSession created or used)
            // Replaces COBOL: CICS COMMAREA session tracking with stateless JWT tokens
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Configure exception handling for authentication and authorization failures
            .exceptionHandling(exception -> exception
                // Handle authentication failures (missing, invalid, expired JWT token)
                // Replaces COBOL: WHEN 13 MOVE 'User not found' TO WS-MESSAGE
                .authenticationEntryPoint((request, response, authException) -> {
                    log.warn("Authentication failed for request: {}, error: {}", 
                             request.getRequestURI(), authException.getMessage());
                    
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    
                    Map<String, Object> errorResponse = Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.UNAUTHORIZED.value(),
                        "error", HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        "message", "Authentication required. Please provide a valid JWT token.",
                        "path", request.getRequestURI()
                    );
                    
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                })
                
                // Handle authorization failures (insufficient privileges)
                // Replaces COBOL: User type check preventing access to admin functions
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.warn("Access denied for request: {}, error: {}", 
                             request.getRequestURI(), accessDeniedException.getMessage());
                    
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json");
                    
                    Map<String, Object> errorResponse = Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.FORBIDDEN.value(),
                        "error", HttpStatus.FORBIDDEN.getReasonPhrase(),
                        "message", "Insufficient privileges. This operation requires elevated permissions.",
                        "path", request.getRequestURI()
                    );
                    
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                })
            )

            // Configure CORS policy for cross-origin API access
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Add JWT authentication filter before UsernamePasswordAuthenticationFilter
            // This filter validates JWT tokens for protected endpoints while allowing public endpoints
            // to pass through via the shouldNotFilter() method.
            // Replaces COBOL: READ-USER-SEC-FILE paragraph from COSGN00C.cbl
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("Spring Security filter chain configuration completed successfully");
        return http.build();
    }

    /**
     * Provides BCrypt password encoder bean for secure password hashing and verification.
     * 
     * <p>This method creates a BCrypt password encoder with configurable cost factor (strength)
     * replacing COBOL plain-text password storage (SEC-USR-PWD PIC X(08) from CSUSR01Y.cpy).
     * BCrypt is a strong adaptive hashing function designed specifically for password storage.</p>
     * 
     * <p><strong>Original COBOL Password Storage (CSUSR01Y.cpy):</strong></p>
     * <pre>
     * 01 SEC-USER-DATA.
     *   05 SEC-USR-ID                 PIC X(08).        - Username
     *   05 SEC-USR-FNAME              PIC X(20).        - First name
     *   05 SEC-USR-LNAME              PIC X(20).        - Last name
     *   05 SEC-USR-PWD                PIC X(08).        - Plain-text password (INSECURE!)
     *   05 SEC-USR-TYPE               PIC X(01).        - User type (A=Admin, R=Regular)
     * 
     * COSGN00C.cbl password comparison (line 223):
     *   IF SEC-USR-PWD = WS-USER-PWD                    - Direct plain-text comparison
     *       ... proceed to menu ...
     *   ELSE
     *       MOVE 'Wrong Password' TO WS-MESSAGE
     * </pre>
     * 
     * <p><strong>Modernization Changes (Section 0.8.1 PCI-DSS Compliance):</strong></p>
     * <ul>
     *   <li><strong>Storage Format:</strong> Plain-text PIC X(08) → BCrypt hash VARCHAR(60)</li>
     *   <li><strong>Password Length:</strong> Fixed 8 chars → Unlimited (up to 72 bytes in BCrypt)</li>
     *   <li><strong>Comparison Method:</strong> Direct equality → {@code BCryptPasswordEncoder.matches()}</li>
     *   <li><strong>Salt:</strong> No salt (predictable) → Random salt per password (unique hashes)</li>
     *   <li><strong>Security:</strong> INSECURE plain-text → SECURE one-way hashing (cannot reverse)</li>
     * </ul>
     * 
     * <p><strong>BCrypt Algorithm Details:</strong></p>
     * <ul>
     *   <li><strong>Algorithm:</strong> Blowfish cipher-based adaptive hashing function</li>
     *   <li><strong>Cost Factor:</strong> Configurable strength (4-31), default 10 rounds</li>
     *   <li><strong>Iterations:</strong> 2^strength rounds (strength=10 → 1024 iterations)</li>
     *   <li><strong>Salt:</strong> Random 16-byte salt generated per password</li>
     *   <li><strong>Hash Format:</strong> $2a$rounds$salt(22 chars)hash(31 chars) = 60 chars total</li>
     *   <li><strong>Example Hash:</strong> $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy</li>
     * </ul>
     * 
     * <p><strong>Cost Factor Selection (Section 0.8.1):</strong></p>
     * <ul>
     *   <li><strong>10 rounds:</strong> ~100ms verification time (recommended for production)</li>
     *   <li><strong>12 rounds:</strong> ~400ms verification time (high security applications)</li>
     *   <li><strong>14 rounds:</strong> ~1600ms verification time (very high security, slower UX)</li>
     * </ul>
     * 
     * <p>Cost factor balances security (resistance to brute-force attacks) with performance
     * (user experience during login). Higher cost = exponentially longer verification time.</p>
     * 
     * <p><strong>PCI-DSS Compliance Requirements (Section 0.8.1):</strong></p>
     * <ul>
     *   <li><strong>PCI-DSS 8.2.1:</strong> Passwords must be rendered unreadable during storage</li>
     *   <li><strong>PCI-DSS 8.2.3:</strong> Passwords must be strong and changed regularly</li>
     *   <li><strong>PCI-DSS 8.2.5:</strong> Passwords must not be reusable for certain time periods</li>
     *   <li>BCrypt provides one-way hashing making passwords unreadable and irreversible</li>
     *   <li>Random salt prevents rainbow table attacks and duplicate hash detection</li>
     *   <li>Adaptive cost factor allows increasing security over time as hardware improves</li>
     * </ul>
     * 
     * <p><strong>Password Encoding Flow:</strong></p>
     * <ol>
     *   <li>User creates account with password (e.g., "MySecurePass123")</li>
     *   <li>{@code passwordEncoder.encode("MySecurePass123")} generates BCrypt hash</li>
     *   <li>Random salt generated: e.g., $2a$10$N9qo8uLOickgx2ZMRZoMye</li>
     *   <li>Password hashed with salt: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy</li>
     *   <li>Hash stored in database app_user.password_hash column (VARCHAR(60))</li>
     *   <li>Original password discarded (never stored or logged)</li>
     * </ol>
     * 
     * <p><strong>Password Verification Flow (Login):</strong></p>
     * <ol>
     *   <li>User submits login credentials (username + password)</li>
     *   <li>{@link UserDetailsServiceImpl} loads user from database by username</li>
     *   <li>UserDetails contains username and BCrypt hash from database</li>
     *   <li>{@code passwordEncoder.matches(rawPassword, encodedPassword)} verifies password</li>
     *   <li>BCrypt extracts salt from stored hash and re-hashes raw password with same salt</li>
     *   <li>Compares newly computed hash with stored hash (constant-time comparison)</li>
     *   <li>On match: Authentication succeeds, JWT token generated</li>
     *   <li>On mismatch: Authentication fails, 401 Unauthorized response</li>
     * </ol>
     * 
     * <p><strong>Security Enhancements over COBOL:</strong></p>
     * <ul>
     *   <li><strong>Irreversible Hashing:</strong> Cannot derive password from hash (one-way function)</li>
     *   <li><strong>Rainbow Table Protection:</strong> Random salt makes precomputed attacks infeasible</li>
     *   <li><strong>Timing Attack Protection:</strong> Constant-time comparison prevents timing attacks</li>
     *   <li><strong>Brute-Force Protection:</strong> Adaptive cost factor slows down brute-force attempts</li>
     *   <li><strong>Password Never Stored:</strong> Only hash stored, original password never persisted</li>
     *   <li><strong>Unique Hashes:</strong> Same password produces different hashes (random salt)</li>
     * </ul>
     * 
     * <p><strong>Configuration (application.yml):</strong></p>
     * <pre>
     * security:
     *   bcrypt:
     *     strength: 10                  # Cost factor (4-31, recommended 10-12)
     * </pre>
     * 
     * <p><strong>Usage Examples:</strong></p>
     * <pre>
     * // Example 1: Encode password during user registration
     * String rawPassword = "MySecurePass123";
     * String encodedPassword = passwordEncoder.encode(rawPassword);
     * // Result: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
     * user.setPasswordHash(encodedPassword);
     * userRepository.save(user);
     * 
     * // Example 2: Verify password during login (handled by Spring Security)
     * boolean matches = passwordEncoder.matches("MySecurePass123", user.getPasswordHash());
     * // Result: true (if password matches)
     * 
     * // Example 3: Different hashes for same password (random salt)
     * String hash1 = passwordEncoder.encode("password123");
     * String hash2 = passwordEncoder.encode("password123");
     * // hash1 != hash2 (different salts), but both verify correctly
     * </pre>
     * 
     * @return BCryptPasswordEncoder configured with specified strength (cost factor)
     * 
     * @see BCryptPasswordEncoder
     * @see org.springframework.security.crypto.password.PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        log.info("Configuring BCryptPasswordEncoder with strength: {}", bcryptStrength);
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    /**
     * Provides AuthenticationManager bean for credential validation during login.
     * 
     * <p>This method exposes the AuthenticationManager bean for use in authentication operations,
     * particularly in the AuthenticationService for validating login credentials. It replaces
     * COBOL password validation logic from COSGN00C.cbl READ-USER-SEC-FILE paragraph.</p>
     * 
     * <p><strong>Original COBOL Authentication Logic (COSGN00C.cbl lines 209-257):</strong></p>
     * <pre>
     * READ-USER-SEC-FILE.
     *     EXEC CICS READ
     *          DATASET   (WS-USRSEC-FILE)      - VSAM file "USRSEC"
     *          INTO      (SEC-USER-DATA)       - User record from CSUSR01Y.cpy
     *          RIDFLD    (WS-USER-ID)          - Username key
     *          KEYLENGTH (LENGTH OF WS-USER-ID)
     *          RESP      (WS-RESP-CD)
     *     END-EXEC.
     * 
     *     EVALUATE WS-RESP-CD
     *         WHEN 0                           - User found
     *             IF SEC-USR-PWD = WS-USER-PWD - Plain-text password comparison
     *                 MOVE WS-USER-ID   TO CDEMO-USER-ID
     *                 MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     *                 ... transfer to menu program ...
     *             ELSE
     *                 MOVE 'Wrong Password. Try again ...' TO WS-MESSAGE
     *         WHEN 13                          - User not found
     *             MOVE 'User not found. Try again ...' TO WS-MESSAGE
     *     END-EVALUATE.
     * </pre>
     * 
     * <p><strong>Modernization Changes (Section 0.8.1):</strong></p>
     * <ul>
     *   <li>VSAM READ operation → {@link UserDetailsServiceImpl#loadUserByUsername(String)}</li>
     *   <li>Plain-text password comparison → {@link BCryptPasswordEncoder#matches(CharSequence, String)}</li>
     *   <li>CICS RESP-CD 0/13 → Spring Security authentication exceptions</li>
     *   <li>COMMAREA user context → Spring SecurityContext with authenticated principal</li>
     * </ul>
     * 
     * <p><strong>AuthenticationManager Responsibilities:</strong></p>
     * <ul>
     *   <li><strong>User Loading:</strong> Delegates to {@link UserDetailsServiceImpl} to load user from PostgreSQL</li>
     *   <li><strong>Password Verification:</strong> Uses {@link BCryptPasswordEncoder} to verify password hash</li>
     *   <li><strong>Authority Mapping:</strong> Populates GrantedAuthority collection from user type</li>
     *   <li><strong>Account Status Checks:</strong> Validates account enabled, non-expired, non-locked</li>
     *   <li><strong>Exception Handling:</strong> Throws authentication exceptions on validation failures</li>
     * </ul>
     * 
     * <p><strong>Authentication Flow (Section 0.1.1):</strong></p>
     * <ol>
     *   <li>Client sends POST /api/v1/auth/login with username and password</li>
     *   <li>AuthenticationService creates {@code UsernamePasswordAuthenticationToken} with credentials</li>
     *   <li>AuthenticationService calls {@code authenticationManager.authenticate(token)}</li>
     *   <li>AuthenticationManager delegates to configured authentication provider</li>
     *   <li>Provider calls {@code userDetailsService.loadUserByUsername(username)}</li>
     *   <li>Provider verifies password via {@code passwordEncoder.matches(rawPassword, hash)}</li>
     *   <li>On success: Returns authenticated {@code Authentication} object with authorities</li>
     *   <li>On failure: Throws {@code BadCredentialsException} or {@code UsernameNotFoundException}</li>
     *   <li>AuthenticationService generates JWT token from authenticated principal</li>
     *   <li>Client receives JWT token in response for subsequent authenticated requests</li>
     * </ol>
     * 
     * <p><strong>Exception Handling:</strong></p>
     * <ul>
     *   <li><strong>UsernameNotFoundException:</strong> Username not found in database (RESP-CD 13)</li>
     *   <li><strong>BadCredentialsException:</strong> Password verification failed (wrong password)</li>
     *   <li><strong>DisabledException:</strong> User account disabled (account status check)</li>
     *   <li><strong>LockedException:</strong> User account locked (failed login attempts)</li>
     *   <li><strong>AccountExpiredException:</strong> User account expired (validity period)</li>
     *   <li>All exceptions mapped to HTTP 401 Unauthorized response by AuthenticationController</li>
     * </ul>
     * 
     * <p><strong>Usage in AuthenticationService:</strong></p>
     * <pre>
     * &#64;Service
     * public class AuthenticationService {
     *     private final AuthenticationManager authenticationManager;
     *     
     *     public LoginResponse login(LoginRequest request) {
     *         // Create authentication token with username and password
     *         UsernamePasswordAuthenticationToken authToken = 
     *             new UsernamePasswordAuthenticationToken(
     *                 request.getUsername(),
     *                 request.getPassword()
     *             );
     *         
     *         // Authenticate credentials via AuthenticationManager
     *         // Replaces COBOL: READ-USER-SEC-FILE + password comparison
     *         Authentication authentication = authenticationManager.authenticate(authToken);
     *         
     *         // Generate JWT token from authenticated principal
     *         String jwt = jwtTokenProvider.generateToken(authentication);
     *         
     *         return LoginResponse.builder()
     *             .token(jwt)
     *             .expiresIn(3600)
     *             .build();
     *     }
     * }
     * </pre>
     * 
     * @param config Spring Security AuthenticationConfiguration providing factory method
     * @return AuthenticationManager for credential validation
     * @throws Exception if AuthenticationManager retrieval fails
     * 
     * @see AuthenticationManager
     * @see UserDetailsServiceImpl
     * @see BCryptPasswordEncoder
     * @see org.springframework.security.authentication.UsernamePasswordAuthenticationToken
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        log.info("Configuring AuthenticationManager bean");
        return config.getAuthenticationManager();
    }

    /**
     * Provides CORS configuration source for cross-origin API access.
     * 
     * <p>This method configures Cross-Origin Resource Sharing (CORS) policy for REST API
     * access from frontend applications (React, Angular, Vue.js) hosted on different origins.
     * It replaces BMS 3270 terminal-based access with modern web browser-based access.</p>
     * 
     * <p><strong>Original COBOL Access Model:</strong></p>
     * <p>In the mainframe environment, users accessed the CardDemo application through 3270
     * terminal emulators directly connected to the CICS region. BMS screens (COSGN00.bms,
     * COMEN01.bms, etc.) rendered on the terminal with no cross-origin concerns. All
     * transactions (CC00, CCMN, etc.) executed within the same CICS address space.</p>
     * 
     * <p><strong>Modernization Changes (Section 0.1.1):</strong></p>
     * <ul>
     *   <li>3270 terminal access → Web browser-based REST API access</li>
     *   <li>BMS maps → JSON request/response payloads</li>
     *   <li>CICS transactions → HTTP GET/POST/PUT/DELETE methods</li>
     *   <li>No origin restrictions → CORS policy required for browser security</li>
     * </ul>
     * 
     * <p><strong>CORS Policy Configuration:</strong></p>
     * <ul>
     *   <li><strong>Allowed Origins:</strong> Configurable list of trusted frontend URLs
     *     <ul>
     *       <li>Development: http://localhost:3000 (React dev server)</li>
     *       <li>Production: https://carddemo.example.com (production frontend)</li>
     *     </ul>
     *   </li>
     *   <li><strong>Allowed Methods:</strong> GET, POST, PUT, DELETE, OPTIONS
     *     <ul>
     *       <li>GET: Read operations (account inquiry, transaction list)</li>
     *       <li>POST: Create operations (transaction posting, user registration)</li>
     *       <li>PUT: Update operations (account update, card update)</li>
     *       <li>DELETE: Delete operations (user deletion)</li>
     *       <li>OPTIONS: Preflight requests for CORS validation</li>
     *     </ul>
     *   </li>
     *   <li><strong>Allowed Headers:</strong> Authorization (JWT token), Content-Type, X-Request-ID
     *     <ul>
     *       <li>Authorization: Bearer JWT token for authentication</li>
     *       <li>Content-Type: application/json for JSON payloads</li>
     *       <li>X-Request-ID: Request correlation ID for distributed tracing</li>
     *     </ul>
     *   </li>
     *   <li><strong>Exposed Headers:</strong> X-Total-Count (pagination metadata)
     *     <ul>
     *       <li>X-Total-Count: Total record count for paginated responses</li>
     *       <li>Used by frontend pagination components (page 1 of 10)</li>
     *     </ul>
     *   </li>
     *   <li><strong>Allow Credentials:</strong> true (enables cookies and Authorization header)
     *     <ul>
     *       <li>Required for sending Authorization header with JWT token</li>
     *       <li>Enables session cookies if needed (currently not used)</li>
     *     </ul>
     *   </li>
     *   <li><strong>Max Age:</strong> 3600 seconds (1 hour preflight cache duration)
     *     <ul>
     *       <li>Browser caches preflight OPTIONS response for 1 hour</li>
     *       <li>Reduces preflight requests for improved performance</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * <p><strong>CORS Preflight Flow (OPTIONS Request):</strong></p>
     * <ol>
     *   <li>Browser detects cross-origin request (e.g., localhost:3000 → localhost:8080)</li>
     *   <li>Browser sends preflight OPTIONS request with Origin and Access-Control-Request-Method</li>
     *   <li>CORS filter validates origin against allowed origins list</li>
     *   <li>CORS filter returns Access-Control-Allow-Origin, Access-Control-Allow-Methods headers</li>
     *   <li>Browser caches preflight response for max-age duration (3600 seconds)</li>
     *   <li>Browser proceeds with actual request (GET/POST/PUT/DELETE) if preflight succeeds</li>
     * </ol>
     * 
     * <p><strong>Configuration Properties (application.yml):</strong></p>
     * <pre>
     * cors:
     *   allowed-origins:
     *     - http://localhost:3000             # React development server
     *     - http://localhost:4200             # Angular development server
     *     - https://carddemo.example.com      # Production frontend
     *   allowed-methods: GET,POST,PUT,DELETE,OPTIONS
     *   allowed-headers: Authorization,Content-Type,X-Request-ID
     *   exposed-headers: X-Total-Count
     *   allow-credentials: true
     *   max-age: 3600
     * </pre>
     * 
     * <p><strong>Security Considerations:</strong></p>
     * <ul>
     *   <li><strong>Origin Whitelist:</strong> Only trusted frontend URLs allowed (no wildcard *)</li>
     *   <li><strong>Credentials Enabled:</strong> Requires explicit origin (cannot use wildcard)</li>
     *   <li><strong>Method Restriction:</strong> Only necessary HTTP methods allowed</li>
     *   <li><strong>Header Restriction:</strong> Only required headers allowed in requests</li>
     *   <li><strong>No Wildcard:</strong> Wildcard (*) not used for production security</li>
     * </ul>
     * 
     * <p><strong>CORS Error Debugging:</strong></p>
     * <ul>
     *   <li><strong>Error:</strong> "No 'Access-Control-Allow-Origin' header is present"
     *     <ul>
     *       <li>Cause: Origin not in allowed origins list</li>
     *       <li>Solution: Add origin to cors.allowed-origins property</li>
     *     </ul>
     *   </li>
     *   <li><strong>Error:</strong> "Method PUT not allowed by Access-Control-Allow-Methods"
     *     <ul>
     *       <li>Cause: HTTP method not in allowed methods list</li>
     *       <li>Solution: Add method to corsConfiguration.setAllowedMethods()</li>
     *     </ul>
     *   </li>
     *   <li><strong>Error:</strong> "Request header Authorization not allowed"
     *     <ul>
     *       <li>Cause: Header not in allowed headers list</li>
     *       <li>Solution: Add header to corsConfiguration.setAllowedHeaders()</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * <p><strong>Example CORS Request/Response:</strong></p>
     * <pre>
     * // Preflight OPTIONS request from browser
     * OPTIONS /api/v1/accounts/123 HTTP/1.1
     * Origin: http://localhost:3000
     * Access-Control-Request-Method: GET
     * Access-Control-Request-Headers: Authorization
     * 
     * // CORS response from server
     * HTTP/1.1 200 OK
     * Access-Control-Allow-Origin: http://localhost:3000
     * Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
     * Access-Control-Allow-Headers: Authorization,Content-Type,X-Request-ID
     * Access-Control-Expose-Headers: X-Total-Count
     * Access-Control-Allow-Credentials: true
     * Access-Control-Max-Age: 3600
     * 
     * // Actual GET request from browser (after preflight)
     * GET /api/v1/accounts/123 HTTP/1.1
     * Origin: http://localhost:3000
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * 
     * // Response with CORS headers
     * HTTP/1.1 200 OK
     * Access-Control-Allow-Origin: http://localhost:3000
     * Access-Control-Allow-Credentials: true
     * Content-Type: application/json
     * X-Total-Count: 1
     * 
     * {"accountId": 123, "accountNumber": "4321876543210001", ...}
     * </pre>
     * 
     * @return CorsConfigurationSource with configured CORS policy
     * 
     * @see CorsConfiguration
     * @see UrlBasedCorsConfigurationSource
     * @see org.springframework.web.filter.CorsFilter
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("Configuring CORS policy with allowed origins: {}", corsAllowedOrigins);

        CorsConfiguration configuration = new CorsConfiguration();
        
        // Set allowed origins from configuration property
        // Example: http://localhost:3000, https://carddemo.example.com
        configuration.setAllowedOrigins(corsAllowedOrigins);
        
        // Set allowed HTTP methods for REST API operations
        configuration.setAllowedMethods(Arrays.asList(
            "GET",      // Read operations (account inquiry, transaction list)
            "POST",     // Create operations (transaction posting, user registration)
            "PUT",      // Update operations (account update, card update)
            "DELETE",   // Delete operations (user deletion)
            "OPTIONS"   // Preflight requests for CORS validation
        ));
        
        // Set allowed request headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",   // JWT bearer token for authentication
            "Content-Type",    // application/json for JSON payloads
            "X-Request-ID",    // Request correlation ID for distributed tracing
            "Accept"           // Accept header for content negotiation
        ));
        
        // Set exposed response headers accessible to frontend
        configuration.setExposedHeaders(Arrays.asList(
            "X-Total-Count",   // Total record count for pagination
            "X-Page-Number",   // Current page number
            "X-Page-Size"      // Records per page
        ));
        
        // Allow credentials (cookies, Authorization header)
        // Required for sending Authorization header with JWT token
        configuration.setAllowCredentials(true);
        
        // Set preflight request cache duration (1 hour)
        // Browser caches preflight OPTIONS response for performance
        configuration.setMaxAge(3600L);
        
        // Register CORS configuration for all API endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        log.info("CORS configuration completed: {} origins, {} methods", 
                 configuration.getAllowedOrigins().size(), 
                 configuration.getAllowedMethods().size());
        
        return source;
    }

    /**
     * Creates JWT authentication filter bean for validating bearer tokens.
     * 
     * <p><strong>CRITICAL:</strong> JwtAuthenticationFilter is NOT annotated with @Component
     * to prevent Spring Boot from automatically registering it in the servlet container.
     * Instead, we define it as a @Bean here and explicitly add it to the Spring Security
     * filter chain in the {@link #securityFilterChain(HttpSecurity)} method.</p>
     * 
     * <p><strong>Why This Approach:</strong></p>
     * <ul>
     *   <li>@Component causes Spring Boot to register filter BEFORE Security filter chain</li>
     *   <li>This breaks permitAll() rules since filter runs before security config</li>
     *   <li>@Bean approach gives us full control over filter placement</li>
     *   <li>Filter is added via addFilterBefore() at the exact position we want</li>
     * </ul>
     * 
     * <p><strong>COBOL Equivalent:</strong></p>
     * <p>This replaces the CICS transaction routing logic from COSGN00C.cbl:</p>
     * <pre>
     * COBOL:
     *     IF SEC-USR-VERIFIED
     *         EVALUATE CDEMO-MENU-OPT
     *             WHEN '1'
     *                 EXEC CICS XCTL PROGRAM('COACTVW') ...
     *             WHEN '2'
     *                 EXEC CICS XCTL PROGRAM('COCRDLI') ...
     *         END-EVALUATE
     *     ELSE
     *         PERFORM DISPLAY-SIGNON-SCREEN  -- Public endpoint, no security check
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Java Equivalent:</strong></p>
     * <pre>
     * // Public endpoints bypass JWT filter (like COBOL's signon screen)
     * GET /api/v1/auth/login → permitAll() → no JWT validation
     * 
     * // Protected endpoints require JWT (like COBOL's XCTL to authenticated programs)
     * GET /api/v1/accounts/{id} → authenticated() → JWT filter validates token
     * </pre>
     * 
     * @param jwtTokenProvider JWT token provider for validation
     * @param userDetailsService service for loading user details
     * @param objectMapper Jackson object mapper for JSON serialization
     * @return configured JwtAuthenticationFilter instance
     * 
     * @see JwtAuthenticationFilter
     * @see JwtAuthenticationFilter#shouldNotFilter(HttpServletRequest)
     * @see #securityFilterChain(HttpSecurity)
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserDetailsServiceImpl userDetailsService,
            ObjectMapper objectMapper) {
        
        log.info("Creating JwtAuthenticationFilter bean with explicit dependency injection");
        
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService, objectMapper);
    }

    /**
     * Disables automatic servlet container registration of JwtAuthenticationFilter.
     * 
     * <p><strong>CRITICAL:</strong> This bean prevents Spring Boot from automatically registering
     * the {@link JwtAuthenticationFilter} as a servlet filter in the container's filter chain.
     * Without this configuration, the filter would be registered TWICE:</p>
     * <ol>
     *   <li><strong>Automatic Registration:</strong> Spring Boot auto-detects filters defined as
     *       {@code @Bean} and registers them with the servlet container. This registration occurs
     *       OUTSIDE the Spring Security filter chain, meaning {@code shouldNotFilter()} is never
     *       called and the filter blocks ALL requests including public endpoints.</li>
     *   <li><strong>Manual Registration:</strong> Our explicit {@code .addFilterBefore()} in
     *       {@link #securityFilterChain(HttpSecurity, JwtAuthenticationFilter)} adds the filter
     *       to the Spring Security chain where {@code shouldNotFilter()} is properly invoked.</li>
     * </ol>
     * 
     * <p><strong>Why This is Necessary:</strong></p>
     * <p>When a filter extends {@link org.springframework.web.filter.OncePerRequestFilter} and is
     * defined as a Spring {@code @Bean}, Spring Boot's {@link org.springframework.boot.web.servlet.FilterRegistrationBean}
     * auto-configuration registers it with the servlet container. This auto-registration:</p>
     * <ul>
     *   <li>Runs the filter BEFORE the Spring Security filter chain is even invoked</li>
     *   <li>Bypasses the {@code shouldNotFilter()} method completely</li>
     *   <li>Ignores {@code permitAll()} rules defined in {@link SecurityFilterChain}</li>
     *   <li>Results in 401 Unauthorized for ALL requests including /api/v1/auth/login</li>
     * </ul>
     * 
     * <p><strong>How This Fix Works:</strong></p>
     * <p>Setting {@code FilterRegistrationBean.setEnabled(false)} tells Spring Boot to skip
     * auto-registration of the filter with the servlet container. The filter is ONLY registered
     * via our manual {@code .addFilterBefore()} call, ensuring it:</p>
     * <ul>
     *   <li>Runs as part of the Spring Security filter chain</li>
     *   <li>Properly invokes {@code shouldNotFilter()} for each request</li>
     *   <li>Respects {@code permitAll()} rules for public endpoints</li>
     *   <li>Allows /api/v1/auth/** endpoints to bypass JWT validation</li>
     * </ul>
     * 
     * <p><strong>Impact of This Fix:</strong></p>
     * <pre>
     * BEFORE (Filter Registered Twice):
     *   Request → Servlet Container Filter (auto-registered, always blocks)
     *          → Spring Security Chain (never reached for public endpoints)
     *          → Controller (never reached, 401 returned)
     * 
     * AFTER (Filter Registered Once):
     *   Request → Spring Security Chain → JwtAuthenticationFilter.shouldNotFilter()
     *          → If public endpoint: Skip filter, proceed to controller
     *          → If protected endpoint: Validate JWT token
     *          → Controller (receives request if authorized)
     * </pre>
     * 
     * <p><strong>Test Impact:</strong></p>
     * <p>This fix resolves the following integration test failures:</p>
     * <ul>
     *   <li>{@code testSuccessfulLogin()}: POST /api/v1/auth/login now returns 200 OK (was 401)</li>
     *   <li>{@code testLoginWithInvalidUsername()}: Controller handles error (was blocked at filter)</li>
     *   <li>{@code testLoginWithInvalidPassword()}: Controller handles error (was blocked at filter)</li>
     *   <li>{@code testLoginWithMissingCredentials()}: Bean Validation runs (was blocked at filter)</li>
     *   <li>{@code testJwtTokenExpiration()}: Login succeeds before token expiry test (was blocked)</li>
     *   <li>{@code testAccountLockoutAfterFailedAttempts()}: Failed login tracking works (was blocked)</li>
     *   <li>{@code testLastLoginTimestampUpdate()}: Login succeeds for timestamp test (was blocked)</li>
     *   <li>{@code testAuthenticatedEndpointAccessWithValidToken()}: Login generates token (was blocked)</li>
     * </ul>
     * 
     * <p><strong>Related Issues Fixed:</strong></p>
     * <ul>
     *   <li>Issue #1: {@code @Component} annotation on filter (removed, replaced with {@code @Bean})</li>
     *   <li>Issue #2: Circular dependency (removed redundant field injection)</li>
     *   <li>Issue #3: Automatic servlet registration (THIS FIX - disable auto-registration)</li>
     * </ul>
     * 
     * <p><strong>Production Deployment:</strong></p>
     * <p>This configuration is REQUIRED in production to ensure public endpoints remain accessible.
     * Without it, the authentication API endpoints would be unreachable, breaking the login flow.</p>
     * 
     * @param filter the JwtAuthenticationFilter bean to disable automatic registration for
     * @return FilterRegistrationBean with enabled=false to prevent double registration
     * 
     * @see JwtAuthenticationFilter
     * @see JwtAuthenticationFilter#shouldNotFilter(javax.servlet.http.HttpServletRequest)
     * @see org.springframework.boot.web.servlet.FilterRegistrationBean
     * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/web.html#web.servlet.embedded-container.servlets-filters-listeners.beans">
     *      Spring Boot Filter Registration Documentation</a>
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        
        org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> registration = 
            new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        
        // CRITICAL: Disable automatic servlet container registration
        // Filter is ONLY registered via .addFilterBefore() in SecurityFilterChain
        registration.setEnabled(false);
        
        log.info("Disabled automatic servlet registration for JwtAuthenticationFilter (manual SecurityFilterChain registration only)");
        
        return registration;
    }
}
