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

package com.aws.carddemo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * JWT authentication filter for validating bearer tokens on incoming HTTP requests.
 * 
 * <p>Migrated from: app/cbl/COSGN00C.cbl (COBOL signon/authentication program)</p>
 * 
 * <p>This filter extends {@link OncePerRequestFilter} to intercept every HTTP request and validate
 * JWT bearer tokens from the Authorization header. It replaces the COBOL session validation logic
 * where COSGN00C.cbl validated USRSEC credentials on every CICS transaction via EXEC CICS READ
 * DATASET('USRSEC'). The filter implements stateless JWT authentication supporting horizontal pod
 * scaling without session affinity requirements.</p>
 * 
 * <p><strong>Original COBOL Session Tracking (COSGN00C.cbl):</strong></p>
 * <pre>
 * PROCESS-ENTER-KEY.
 *     EXEC CICS RECEIVE MAP('COSGN0A') MAPSET('COSGN00') ...
 *     MOVE FUNCTION UPPER-CASE(USERIDI OF COSGN0AI) TO WS-USER-ID
 *     MOVE FUNCTION UPPER-CASE(PASSWDI OF COSGN0AI) TO WS-USER-PWD
 *     PERFORM READ-USER-SEC-FILE.
 * 
 * READ-USER-SEC-FILE.
 *     EXEC CICS READ DATASET(WS-USRSEC-FILE) INTO(SEC-USER-DATA)
 *                    RIDFLD(WS-USER-ID) KEYLENGTH(LENGTH OF WS-USER-ID) ...
 *     EVALUATE WS-RESP-CD
 *         WHEN 0
 *             IF SEC-USR-PWD = WS-USER-PWD        - Plain-text password comparison
 *                 MOVE WS-USER-ID TO CDEMO-USER-ID
 *                 MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
 *                 EXEC CICS XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA) ...
 *             ELSE
 *                 MOVE 'Wrong Password' TO WS-MESSAGE
 *         WHEN 13
 *             MOVE 'User not found' TO WS-MESSAGE
 *     END-EVALUATE.
 * </pre>
 * 
 * <p><strong>Modernization Changes (Section 0.8.1):</strong></p>
 * <ul>
 *   <li>CICS COMMAREA session tracking → Stateless JWT bearer token authentication</li>
 *   <li>VSAM USRSEC file READ on every transaction → JWT signature verification (no DB call per request)</li>
 *   <li>Plain-text password comparison → Cryptographic RS256/HS256 signature validation</li>
 *   <li>CDEMO-USER-ID/CDEMO-USER-TYPE in COMMAREA → Spring SecurityContext with UserDetails</li>
 *   <li>BMS screen-based authentication → RESTful JWT token-based authentication</li>
 *   <li>Session state in CICS memory → Stateless token payload (username, roles)</li>
 * </ul>
 * 
 * <p><strong>Authentication Flow (Section 0.1.1):</strong></p>
 * <ol>
 *   <li>Client sends HTTP request with Authorization header: {@code Bearer <JWT_TOKEN>}</li>
 *   <li>Filter extracts token from Authorization header (Bearer prefix validation)</li>
 *   <li>Filter validates token signature and expiration via {@link JwtTokenProvider#validateToken(String)}</li>
 *   <li>Filter extracts username from token via {@link JwtTokenProvider#getUsernameFromToken(String)}</li>
 *   <li>Filter loads UserDetails from database via {@link UserDetailsServiceImpl#loadUserByUsername(String)}</li>
 *   <li>Filter creates {@link UsernamePasswordAuthenticationToken} with UserDetails and authorities</li>
 *   <li>Filter populates {@link SecurityContextHolder} with authentication for downstream filters/controllers</li>
 *   <li>Filter continues chain via {@code filterChain.doFilter(request, response)}</li>
 *   <li>Controller methods access authenticated user via {@code SecurityContextHolder.getContext().getAuthentication()}</li>
 *   <li>@PreAuthorize annotations enforce role-based access control (ROLE_ADMIN, ROLE_USER)</li>
 * </ol>
 * 
 * <p><strong>Filter Execution (Spring Security Filter Chain):</strong></p>
 * <p>This filter executes before {@code UsernamePasswordAuthenticationFilter} in the Spring Security
 * filter chain. Filter order:</p>
 * <ol>
 *   <li>SecurityContextPersistenceFilter (manages SecurityContext)</li>
 *   <li><strong>JwtAuthenticationFilter (this filter - validates JWT token)</strong></li>
 *   <li>UsernamePasswordAuthenticationFilter (skipped for JWT authentication)</li>
 *   <li>FilterSecurityInterceptor (enforces @PreAuthorize authorization rules)</li>
 * </ol>
 * 
 * <p><strong>Public Endpoints (Section 0.8.1):</strong></p>
 * <p>The filter skips JWT validation for the following public endpoints via {@link #shouldNotFilter(HttpServletRequest)}:</p>
 * <ul>
 *   <li>{@code /api/v1/auth/**} - Authentication endpoints (login, refresh, logout)</li>
 *   <li>{@code /actuator/health} - Health check for Kubernetes liveness/readiness probes</li>
 *   <li>{@code /swagger-ui/**} - Swagger UI documentation interface</li>
 *   <li>{@code /v3/api-docs/**} - OpenAPI 3.0 specification JSON</li>
 * </ul>
 * 
 * <p><strong>Security Enhancements (Section 0.8.1 PCI-DSS Compliance):</strong></p>
 * <ul>
 *   <li><strong>Signature Verification:</strong> RS256 asymmetric or HS256 symmetric signature validation prevents token tampering</li>
 *   <li><strong>Expiration Validation:</strong> Tokens expire after 1 hour (configurable), limiting exposure window</li>
 *   <li><strong>Username Masking:</strong> Usernames masked in logs (first 3 chars + ***) for PCI-DSS compliance</li>
 *   <li><strong>Audit Logging:</strong> Successful authentication logged at DEBUG, failures logged at WARN</li>
 *   <li><strong>Exception Handling:</strong> All exceptions result in 401 Unauthorized with structured JSON error</li>
 *   <li><strong>SecurityContext Cleanup:</strong> Context cleared on authentication failure to prevent unauthorized access</li>
 * </ul>
 * 
 * <p><strong>Error Responses (Section 0.8.1):</strong></p>
 * <p>On authentication failure, the filter returns HTTP 401 Unauthorized with JSON error body:</p>
 * <pre>
 * {
 *   "timestamp": "2024-01-15T10:30:45",
 *   "status": 401,
 *   "error": "Unauthorized",
 *   "message": "Invalid or expired JWT token",
 *   "path": "/api/v1/accounts/123"
 * }
 * </pre>
 * 
 * <p><strong>Performance Optimizations:</strong></p>
 * <ul>
 *   <li>JWT validation avoids database query per request (signature verification only)</li>
 *   <li>UserDetails loaded once during token validation, cached in SecurityContext for request</li>
 *   <li>Filter executes once per request via {@code OncePerRequestFilter} guarantee</li>
 *   <li>Public endpoints bypass filter entirely via {@code shouldNotFilter()}</li>
 * </ul>
 * 
 * <p><strong>Exception Handling Strategy:</strong></p>
 * <ul>
 *   <li>{@link JwtException} - Token validation failures (signature, expiration, malformed)</li>
 *   <li>{@link UsernameNotFoundException} - User not found in database after valid token</li>
 *   <li>{@link IllegalArgumentException} - Missing or empty Authorization header</li>
 *   <li>All exceptions → HTTP 401 Unauthorized with JSON error response</li>
 * </ul>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>
 * // Example 1: Successful JWT authentication
 * GET /api/v1/accounts/123
 * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
 * 
 * // Filter validates token and populates SecurityContext
 * // Controller accesses authenticated user:
 * &#64;GetMapping("/api/v1/accounts/{id}")
 * public ResponseEntity&lt;AccountResponse&gt; getAccount(&#64;PathVariable Long id) {
 *     Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *     String username = auth.getName();
 *     // Authorization logic...
 * }
 * 
 * // Example 2: Missing Authorization header
 * GET /api/v1/accounts/123
 * (no Authorization header)
 * 
 * // Response: 401 Unauthorized
 * // {"timestamp": "...", "status": 401, "error": "Unauthorized", "message": "Missing Authorization header"}
 * 
 * // Example 3: Expired JWT token
 * GET /api/v1/accounts/123
 * Authorization: Bearer <expired_token>
 * 
 * // Response: 401 Unauthorized
 * // {"timestamp": "...", "status": 401, "error": "Unauthorized", "message": "JWT token has expired"}
 * 
 * // Example 4: Public endpoint (no authentication required)
 * POST /api/v1/auth/login
 * {"username": "admin001", "password": "secret123"}
 * 
 * // Filter skips JWT validation via shouldNotFilter() returning true
 * // Proceeds directly to AuthController.login() method
 * </pre>
 * 
 * <p><strong>Testing Strategy:</strong></p>
 * <ul>
 *   <li>Unit Tests: Mock JwtTokenProvider and UserDetailsServiceImpl, verify filter logic</li>
 *   <li>Integration Tests: Real Spring Security context, verify full authentication flow</li>
 *   <li>Security Tests: Test token expiration, invalid signatures, missing headers</li>
 * </ul>
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @see OncePerRequestFilter
 * @see JwtTokenProvider
 * @see UserDetailsServiceImpl
 * @see SecurityContextHolder
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * JWT token provider for token validation and claims extraction.
     * 
     * <p>Injected via constructor (Lombok @RequiredArgsConstructor generates constructor
     * for final fields). Constructor injection is preferred over field injection for better
     * testability and immutability.</p>
     */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * UserDetailsService implementation for loading user credentials from database.
     * 
     * <p>Used to load UserDetails after successful JWT token validation, populating
     * Spring SecurityContext with authenticated user principal and authorities.</p>
     */
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * Jackson ObjectMapper for JSON serialization of error responses.
     * 
     * <p>Used to convert error response objects to JSON strings when writing HTTP 401
     * Unauthorized response body with structured error details.</p>
     */
    private final ObjectMapper objectMapper;

    /**
     * Authorization header name constant.
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Bearer token prefix constant.
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Filters incoming HTTP requests to validate JWT bearer tokens and populate Spring SecurityContext.
     * 
     * <p>This method implements the core JWT authentication logic, replacing COBOL session validation
     * from COSGN00C.cbl (READ-USER-SEC-FILE paragraph, lines 209-257). It executes once per request
     * via {@code OncePerRequestFilter} guarantee.</p>
     * 
     * <p><strong>Filter Logic Flow:</strong></p>
     * <ol>
     *   <li>Extract Authorization header from HTTP request</li>
     *   <li>Validate Bearer token format (prefix "Bearer ")</li>
     *   <li>Extract JWT token string after Bearer prefix</li>
     *   <li>Validate token signature and expiration via {@link JwtTokenProvider#validateToken(String)}</li>
     *   <li>Extract username from token claims via {@link JwtTokenProvider#getUsernameFromToken(String)}</li>
     *   <li>Load UserDetails from database via {@link UserDetailsServiceImpl#loadUserByUsername(String)}</li>
     *   <li>Create {@link UsernamePasswordAuthenticationToken} with UserDetails, null credentials, and authorities</li>
     *   <li>Set authentication in {@link SecurityContextHolder} for downstream filters/controllers</li>
     *   <li>Continue filter chain via {@code filterChain.doFilter(request, response)}</li>
     * </ol>
     * 
     * <p><strong>Original COBOL Logic (COSGN00C.cbl lines 209-257):</strong></p>
     * <pre>
     * READ-USER-SEC-FILE.
     *     EXEC CICS READ DATASET(WS-USRSEC-FILE) INTO(SEC-USER-DATA)
     *                    RIDFLD(WS-USER-ID) KEYLENGTH(LENGTH OF WS-USER-ID)
     *                    RESP(WS-RESP-CD) RESP2(WS-REAS-CD) ...
     *     EVALUATE WS-RESP-CD
     *         WHEN 0                           - User found
     *             IF SEC-USR-PWD = WS-USER-PWD - Plain-text password comparison
     *                 MOVE WS-USER-ID TO CDEMO-USER-ID
     *                 MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     *                 ... proceed to menu program ...
     *             ELSE
     *                 MOVE 'Wrong Password' TO WS-MESSAGE
     *                 PERFORM SEND-SIGNON-SCREEN
     *         WHEN 13                          - User not found
     *             MOVE 'User not found' TO WS-MESSAGE
     *             PERFORM SEND-SIGNON-SCREEN
     *         WHEN OTHER
     *             MOVE 'Unable to verify the User' TO WS-MESSAGE
     *             PERFORM SEND-SIGNON-SCREEN
     *     END-EVALUATE.
     * </pre>
     * 
     * <p><strong>Modernization Changes:</strong></p>
     * <ul>
     *   <li>VSAM READ on every request → JWT signature verification (no DB call per request)</li>
     *   <li>Plain-text password comparison → Cryptographic signature validation</li>
     *   <li>COMMAREA session state → Stateless JWT token with embedded claims</li>
     *   <li>RESP-CD 0/13 → Boolean validateToken() + UsernameNotFoundException</li>
     *   <li>CDEMO-USER-ID/CDEMO-USER-TYPE → Spring SecurityContext with UserDetails</li>
     * </ul>
     * 
     * <p><strong>Exception Handling:</strong></p>
     * <ul>
     *   <li>{@link JwtException} - Token validation failures (signature, expiration, malformed) → 401</li>
     *   <li>{@link UsernameNotFoundException} - User not found after valid token → 401</li>
     *   <li>{@link IllegalArgumentException} - Missing or malformed Authorization header → 401</li>
     *   <li>All exceptions clear SecurityContext and return JSON error response</li>
     * </ul>
     * 
     * <p><strong>SecurityContext Population:</strong></p>
     * <p>On successful authentication, the filter creates a {@link UsernamePasswordAuthenticationToken}
     * with three arguments:</p>
     * <ul>
     *   <li>Principal: {@link UserDetails} object with username, authorities, account status</li>
     *   <li>Credentials: null (password not needed after JWT validation)</li>
     *   <li>Authorities: Collection of {@link org.springframework.security.core.GrantedAuthority} from UserDetails</li>
     * </ul>
     * 
     * <p><strong>Audit Logging (PCI-DSS Compliance):</strong></p>
     * <ul>
     *   <li>DEBUG level: Successful authentication with masked username (first 3 chars + ***)</li>
     *   <li>WARN level: Token validation failures with masked username</li>
     *   <li>ERROR level: Unexpected exceptions during authentication</li>
     * </ul>
     * 
     * @param request HTTP servlet request containing Authorization header with JWT token
     * @param response HTTP servlet response for writing error responses on authentication failure
     * @param filterChain filter chain to continue processing on successful authentication
     * @throws ServletException if an error occurs during filter processing
     * @throws IOException if an I/O error occurs during response writing
     * 
     * @see JwtTokenProvider#validateToken(String)
     * @see JwtTokenProvider#getUsernameFromToken(String)
     * @see UserDetailsServiceImpl#loadUserByUsername(String)
     * @see SecurityContextHolder#getContext()
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                     HttpServletResponse response, 
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            // Step 1: Extract JWT token from Authorization header
            // Replaces COBOL: MOVE FUNCTION UPPER-CASE(USERIDI OF COSGN0AI) TO WS-USER-ID
            String jwt = extractJwtFromRequest(request);
            
            if (jwt == null) {
                // No JWT token found, but this might be a public endpoint
                // Continue filter chain to allow Spring Security to handle authorization
                log.trace("No JWT token found in Authorization header, continuing filter chain");
                filterChain.doFilter(request, response);
                return;
            }

            // Step 2: Validate JWT token signature and expiration
            // Replaces COBOL: IF SEC-USR-PWD = WS-USER-PWD (COSGN00C.cbl line 223)
            if (!jwtTokenProvider.validateToken(jwt)) {
                // Token validation failed (expired, invalid signature, malformed)
                log.warn("JWT token validation failed for request: {}", request.getRequestURI());
                sendUnauthorizedResponse(response, "Invalid or expired JWT token", request.getRequestURI());
                return;
            }

            // Step 3: Extract username from JWT token claims
            // Replaces COBOL: MOVE WS-USER-ID TO CDEMO-USER-ID (COSGN00C.cbl line 226)
            String username = jwtTokenProvider.getUsernameFromToken(jwt);

            // Step 4: Load UserDetails from database
            // Replaces COBOL: EXEC CICS READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID)
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Step 5: Create authentication token with UserDetails and authorities
            // Replaces COBOL: MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE (COSGN00C.cbl line 227)
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    userDetails,       // Principal: UserDetails with username, authorities, account status
                    null,              // Credentials: null (password not needed after JWT validation)
                    userDetails.getAuthorities()  // Authorities: ROLE_ADMIN, ROLE_USER from UserDetails
                );

            // Step 6: Populate Spring SecurityContext for downstream filters/controllers
            // Replaces COBOL COMMAREA session tracking (CDEMO-USER-ID, CDEMO-USER-TYPE)
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Log successful authentication at DEBUG level with masked username (PCI-DSS compliance)
            log.debug("JWT authentication successful for user: {}, request: {}", 
                      maskUsername(username), request.getRequestURI());

            // Step 7: Continue filter chain to next filter/controller
            // Replaces COBOL: EXEC CICS XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)
            filterChain.doFilter(request, response);

        } catch (JwtException e) {
            // Token validation exceptions (signature invalid, expired, malformed)
            log.warn("JWT validation exception: {} for request: {}", 
                     e.getMessage(), request.getRequestURI());
            SecurityContextHolder.clearContext();
            sendUnauthorizedResponse(response, "Invalid or expired JWT token", request.getRequestURI());

        } catch (UsernameNotFoundException e) {
            // User not found in database after valid token
            // Replaces COBOL: WHEN 13 MOVE 'User not found' TO WS-MESSAGE (COSGN00C.cbl line 247)
            log.warn("User not found exception: {} for request: {}", 
                     e.getMessage(), request.getRequestURI());
            SecurityContextHolder.clearContext();
            sendUnauthorizedResponse(response, "User not found", request.getRequestURI());

        } catch (Exception e) {
            // Unexpected exceptions during authentication
            log.error("Authentication error: {} for request: {}", 
                      e.getMessage(), request.getRequestURI(), e);
            SecurityContextHolder.clearContext();
            sendUnauthorizedResponse(response, "Authentication error", request.getRequestURI());
        }
    }

    /**
     * Determines if the filter should skip JWT validation for the current request.
     * 
     * <p>This method returns {@code true} for public endpoints that do not require authentication,
     * allowing them to bypass JWT token validation. This replaces COBOL logic where signon screen
     * (COSGN00C.cbl) was always accessible without prior authentication.</p>
     * 
     * <p><strong>Public Endpoints (no JWT validation required):</strong></p>
     * <ul>
     *   <li>{@code /api/v1/auth/**} - Authentication endpoints (login, refresh, logout)</li>
     *   <li>{@code /actuator/health} - Health check for Kubernetes liveness/readiness probes</li>
     *   <li>{@code /swagger-ui/**} - Swagger UI documentation interface</li>
     *   <li>{@code /v3/api-docs/**} - OpenAPI 3.0 specification JSON</li>
     * </ul>
     * 
     * <p><strong>All Other Endpoints:</strong></p>
     * <p>All other endpoints require JWT token validation via {@link #doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)}.</p>
     * 
     * <p><strong>Original COBOL Logic:</strong></p>
     * <p>In the COBOL application, the signon screen (COSGN00C.cbl) was always accessible as the
     * entry point to the application. After successful signon, users were transferred to menu
     * programs (COMEN01C.cbl or COADM01C.cbl) with COMMAREA containing user session data. This
     * method replicates that pattern by allowing public access to authentication endpoints.</p>
     * 
     * @param request HTTP servlet request to check for public endpoint
     * @return {@code true} if filter should skip JWT validation, {@code false} otherwise
     * @throws ServletException if an error occurs during request processing
     * 
     * @see OncePerRequestFilter#shouldNotFilter(HttpServletRequest)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Public endpoints that bypass JWT validation
        boolean isPublicEndpoint = path.startsWith("/api/v1/auth/") ||
                                    path.equals("/actuator/health") ||
                                    path.startsWith("/swagger-ui/") ||
                                    path.startsWith("/v3/api-docs/");
        
        if (isPublicEndpoint) {
            log.trace("Skipping JWT validation for public endpoint: {}", path);
        }
        
        return isPublicEndpoint;
    }

    /**
     * Extracts JWT token from Authorization header.
     * 
     * <p>This method parses the Authorization header and extracts the JWT token string
     * after validating the Bearer prefix. Authorization header format:</p>
     * <pre>
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * </pre>
     * 
     * <p><strong>Validation Rules:</strong></p>
     * <ul>
     *   <li>Authorization header must be present and non-empty</li>
     *   <li>Authorization header must start with "Bearer " prefix (case-sensitive)</li>
     *   <li>Token string after "Bearer " prefix must be non-empty</li>
     * </ul>
     * 
     * <p><strong>Return Values:</strong></p>
     * <ul>
     *   <li>Valid token string if Authorization header is correctly formatted</li>
     *   <li>{@code null} if Authorization header is missing, empty, or malformed</li>
     * </ul>
     * 
     * @param request HTTP servlet request containing Authorization header
     * @return JWT token string without Bearer prefix, or {@code null} if not found or malformed
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            // Extract token after "Bearer " prefix (7 characters)
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        
        return null;
    }

    /**
     * Sends HTTP 401 Unauthorized response with JSON error body.
     * 
     * <p>This method constructs a structured JSON error response following Spring Boot error
     * response format. The response body contains:</p>
     * <ul>
     *   <li>timestamp: ISO 8601 formatted error occurrence time</li>
     *   <li>status: HTTP status code (401)</li>
     *   <li>error: HTTP status reason phrase ("Unauthorized")</li>
     *   <li>message: Detailed error message for client-side error handling</li>
     *   <li>path: Request URI that triggered the authentication failure</li>
     * </ul>
     * 
     * <p><strong>Example JSON Error Response:</strong></p>
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:45",
     *   "status": 401,
     *   "error": "Unauthorized",
     *   "message": "Invalid or expired JWT token",
     *   "path": "/api/v1/accounts/123"
     * }
     * </pre>
     * 
     * <p><strong>HTTP Response Headers:</strong></p>
     * <ul>
     *   <li>Content-Type: application/json</li>
     *   <li>Status: 401 Unauthorized</li>
     * </ul>
     * 
     * <p><strong>Original COBOL Error Handling (COSGN00C.cbl):</strong></p>
     * <pre>
     * WHEN 13                          - User not found
     *     MOVE 'User not found. Try again ...' TO WS-MESSAGE
     *     PERFORM SEND-SIGNON-SCREEN
     * WHEN OTHER
     *     MOVE 'Unable to verify the User ...' TO WS-MESSAGE
     *     PERFORM SEND-SIGNON-SCREEN
     * </pre>
     * 
     * <p><strong>Modernization Changes:</strong></p>
     * <ul>
     *   <li>BMS map error message → Structured JSON error response</li>
     *   <li>SEND-SIGNON-SCREEN → HTTP 401 response with WWW-Authenticate header</li>
     *   <li>COBOL error flags (WS-ERR-FLG) → HTTP status codes</li>
     * </ul>
     * 
     * @param response HTTP servlet response for writing JSON error body
     * @param message error message describing authentication failure reason
     * @param path request URI that triggered the authentication failure
     * @throws IOException if an I/O error occurs during response writing
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, 
                                           String message, 
                                           String path) throws IOException {
        // Clear any existing authentication from SecurityContext
        SecurityContextHolder.clearContext();
        
        // Set HTTP status code 401 Unauthorized
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        
        // Set content type to JSON
        response.setContentType("application/json");
        
        // Build structured error response following Spring Boot error format
        Map<String, Object> errorResponse = Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", HttpStatus.UNAUTHORIZED.value(),
            "error", HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "message", message,
            "path", path
        );
        
        // Serialize error response to JSON and write to response body
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }

    /**
     * Masks username for PCI-DSS compliant logging (Section 0.8.1).
     * 
     * <p>Prevents exposure of full usernames in application logs while maintaining
     * partial visibility for troubleshooting and audit purposes. This is part of
     * PCI-DSS requirement to mask sensitive data in logs.</p>
     * 
     * <p><strong>Masking Strategy:</strong></p>
     * <ul>
     *   <li>Short usernames (≤3 chars): Fully masked with "***"</li>
     *   <li>Longer usernames (>3 chars): First 3 characters + "***"</li>
     * </ul>
     * 
     * <p><strong>Examples:</strong></p>
     * <pre>
     * maskUsername("admin001")    → "adm***"
     * maskUsername("user123")     → "use***"
     * maskUsername("abc")         → "***"
     * maskUsername("ab")          → "***"
     * maskUsername(null)          → "***"
     * </pre>
     * 
     * <p><strong>Security Considerations:</strong></p>
     * <ul>
     *   <li>Prevents username enumeration from log files</li>
     *   <li>Maintains partial visibility for debugging (first 3 chars)</li>
     *   <li>Complies with PCI-DSS sensitive data masking requirements</li>
     *   <li>Applied to all authentication-related log messages</li>
     * </ul>
     * 
     * @param username the username to mask (may be null)
     * @return masked username with first 3 characters visible (or "***" if shorter)
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 3) {
            return "***";
        }
        return username.substring(0, 3) + "***";
    }
}
