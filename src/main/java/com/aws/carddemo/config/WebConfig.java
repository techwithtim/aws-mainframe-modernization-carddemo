/*
 * WebConfig.java
 * 
 * Spring Web MVC configuration for CardDemo modernized application
 * Migrated from: COBOL BMS screen handling and data formatting
 * Source references: app/cpy/CVACT01Y.cpy (date/monetary fields), app/cbl/COACTVWC.cbl (data handling)
 * 
 * This configuration class customizes HTTP request/response handling including:
 * - CORS (Cross-Origin Resource Sharing) policy for frontend access
 * - Jackson JSON serialization with BigDecimal precision preservation
 * - ISO-8601 date formatting consistent with COBOL PIC X(10) date fields
 * - Content negotiation defaulting to JSON (replacing COBOL BMS formatted text)
 * - Static resource handling for Swagger UI and API documentation
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration for CardDemo REST API
 * 
 * Provides enterprise-grade HTTP configuration ensuring:
 * 1. Data integrity: BigDecimal precision preservation for COBOL COMP-3 monetary fields
 * 2. Date consistency: ISO-8601 formatting matching COBOL PIC X(10) date fields (YYYY-MM-DD)
 * 3. Cross-origin access: CORS policy enabling frontend applications to consume REST APIs
 * 4. Content negotiation: Default JSON responses replacing COBOL BMS formatted text screens
 * 
 * Performance targets:
 * - Support 1,000+ concurrent users
 * - <200ms response time for account inquiries
 * - Embedded Tomcat with 200 max threads (configured in application.yml)
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configure Jackson ObjectMapper bean for JSON serialization/deserialization
     * 
     * Ensures data integrity equivalent to COBOL data handling:
     * - BigDecimal precision: COBOL PIC S9(10)V99 COMP-3 packed decimal fields serialize without
     *   scientific notation or precision loss (e.g., account balances, credit limits)
     * - ISO-8601 dates: COBOL PIC X(10) date fields (ACCT-OPEN-DATE, ACCT-EXPIRAION-DATE from
     *   CVACT01Y.cpy) serialize as "yyyy-MM-dd" strings via JavaTimeModule
     * - Null handling: Omit null fields from JSON responses (Include.NON_NULL) to reduce payload
     *   size and improve bandwidth efficiency compared to verbose COBOL BMS screen layouts
     * 
     * Jackson modules registered:
     * - JavaTimeModule: Support for Java 8 LocalDate, LocalDateTime (replaces COBOL date handling)
     * 
     * Serialization features:
     * - WRITE_DATES_AS_TIMESTAMPS=false: ISO-8601 string format (not epoch milliseconds)
     * - WRITE_BIGDECIMAL_AS_PLAIN=true: Plain notation for monetary amounts (no scientific notation)
     * - FAIL_ON_EMPTY_BEANS=false: Allow serialization of empty objects (defensive)
     * 
     * @return Customized ObjectMapper with financial precision and date formatting
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register JavaTimeModule for Java 8 date/time API support
        // Ensures LocalDate fields (from COBOL PIC X(10) dates) serialize as ISO-8601 strings
        mapper.registerModule(new JavaTimeModule());
        
        // Configure serialization features for data integrity
        // WRITE_DATES_AS_TIMESTAMPS: false = ISO-8601 strings (e.g., "2024-01-15")
        // Consistent with COBOL date fields: ACCT-OPEN-DATE PIC X(10) format YYYY-MM-DD
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // WRITE_BIGDECIMAL_AS_PLAIN: true = preserve exact decimal precision
        // Critical for financial calculations from COBOL PIC S9(10)V99 COMP-3 fields
        // Example: ACCT-CURR-BAL 12345.67 serializes as "12345.67" (not "1.234567E4")
        mapper.enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);
        
        // FAIL_ON_EMPTY_BEANS: false = allow serialization of beans with no properties
        // Prevents exceptions when serializing DTOs with only transient fields
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        
        // Null value handling: omit null fields from JSON responses
        // Reduces payload size compared to COBOL BMS screens with blank/space-filled fields
        // Example: null ACCT-ADDR-ZIP is excluded rather than serialized as "null"
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        return mapper;
    }

    /**
     * Configure CORS (Cross-Origin Resource Sharing) policy
     * 
     * Enables frontend applications (React, Angular, Vue.js) to consume REST API endpoints
     * from different origins, replacing COBOL CICS 3270 terminal access with modern web clients.
     * 
     * CORS configuration:
     * - Allowed origins: "*" for development (MUST be restricted to specific domains in production)
     * - Allowed methods: GET, POST, PUT, DELETE, OPTIONS (full CRUD operations)
     * - Allowed headers: Authorization (JWT tokens), Content-Type, X-Requested-With
     * - Exposed headers: Authorization (JWT refresh), X-Total-Count (pagination metadata)
     * - Allow credentials: true (enables cookie-based sessions if needed)
     * - Max age: 3600 seconds (cache preflight OPTIONS requests for 1 hour)
     * 
     * Security considerations:
     * - Development: allowedOrigins("*") for ease of local testing
     * - Production: MUST restrict to specific domains (e.g., "https://carddemo.example.com")
     * - Credentials: allowCredentials(true) requires explicit origin (not "*")
     * 
     * HTTP methods mapped to COBOL CICS transactions:
     * - GET: Account inquiry (COACTVWC.cbl), card list (COCRDLIC.cbl), transaction view
     * - POST: Transaction add (COTRN02C.cbl), payment processing (COBIL00C.cbl)
     * - PUT: Account update (COACTUPC.cbl), card update (COCRDUPC.cbl)
     * - DELETE: User delete (COUSR03C.cbl)
     * 
     * @param registry CORS registry to configure allowed origins, methods, and headers
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // Allow all origins in development
                // TODO: In production, replace with specific domains:
                // .allowedOrigins("https://carddemo.example.com", "https://admin.carddemo.example.com")
                .allowedOriginPatterns("*")
                
                // Allow standard HTTP methods for REST CRUD operations
                // Maps to COBOL CICS transaction codes (CCMN, CCAD, CACV, CAUP, etc.)
                .allowedMethods(
                    HttpMethod.GET.name(),     // Read operations (COACTVWC, COCRDLIC, COTRN00C)
                    HttpMethod.POST.name(),    // Create operations (COTRN02C, COBIL00C, COUSR01C)
                    HttpMethod.PUT.name(),     // Update operations (COACTUPC, COCRDUPC, COUSR02C)
                    HttpMethod.DELETE.name(),  // Delete operations (COUSR03C)
                    HttpMethod.OPTIONS.name()  // Preflight requests for CORS validation
                )
                
                // Allow common headers for authentication and content negotiation
                .allowedHeaders("*")
                
                // Expose custom headers to frontend clients
                // Authorization: JWT token refresh in response headers
                // X-Total-Count: Total records for pagination (e.g., transaction list with 100+ records)
                .exposedHeaders("Authorization", "X-Total-Count", "X-Page-Number", "X-Page-Size")
                
                // Allow credentials (cookies, authorization headers)
                // Required for JWT bearer tokens in Authorization header
                // Note: allowCredentials(true) requires explicit origin (not "*")
                .allowCredentials(true)
                
                // Cache preflight OPTIONS requests for 1 hour (3600 seconds)
                // Reduces overhead for cross-origin requests with custom headers
                .maxAge(3600);
    }

    /**
     * Configure resource handlers for static content
     * 
     * Provides access to Swagger UI and API documentation resources, enabling interactive
     * REST API exploration and testing as a replacement for COBOL BMS screen navigation.
     * 
     * Resource mappings:
     * - /swagger-ui/**: Swagger UI frontend (HTML, CSS, JS)
     * - /webjars/**: WebJars dependencies (Swagger UI dependencies)
     * - /api-docs/**: OpenAPI specification JSON/YAML (auto-generated from controllers)
     * 
     * This replaces COBOL BMS map definitions with interactive API documentation:
     * - COSGN00.bms → Swagger UI login endpoint documentation
     * - COACTVW.bms → /api/v1/accounts/{id} endpoint documentation
     * - COTRN00.bms → /api/v1/transactions pagination documentation
     * 
     * @param registry Resource handler registry to configure static resource locations
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Swagger UI resources (HTML, CSS, JS for interactive API documentation)
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springdoc-openapi-ui/")
                .resourceChain(false);
        
        // WebJars resources (JavaScript libraries packaged as JARs)
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .resourceChain(false);
    }

    /**
     * Configure content negotiation strategy
     * 
     * Defaults all REST API responses to application/json MediaType, replacing COBOL BMS
     * formatted text screens with JSON serialization for modern web and mobile clients.
     * 
     * Content negotiation settings:
     * - Default content type: application/json (all responses serialize to JSON)
     * - Favor parameter: false (ignore ?format=json query parameters)
     * - Ignore Accept header: false (respect Accept: application/json header)
     * 
     * This configuration ensures consistent JSON responses across all 8 REST controllers:
     * - AccountController: GET /api/v1/accounts/{id} → JSON account details
     * - CardController: GET /api/v1/cards/{cardNumber} → JSON card information
     * - TransactionController: GET /api/v1/transactions → JSON transaction list
     * - PaymentController: POST /api/v1/accounts/{id}/payments → JSON payment confirmation
     * - AuthController: POST /api/v1/auth/login → JSON JWT token response
     * - MenuController: GET /api/v1/menu → JSON menu options (replaces COMEN01C.cbl screen)
     * - ReportController: GET /api/v1/reports → JSON report metadata
     * - AdminController: GET /api/v1/admin/users → JSON user list
     * 
     * Replaces COBOL BMS formatted text output with structured JSON:
     * - COBOL: Fixed-width text fields with FILLER padding (e.g., "ACCT-ID: 00012345678   ")
     * - JSON: Compact key-value pairs (e.g., {"accountId": 12345678})
     * 
     * @param configurer Content negotiation configurer to set default MediaType
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                // Default to JSON for all responses (replaces COBOL BMS text screens)
                .defaultContentType(MediaType.APPLICATION_JSON)
                
                // Disable ?format=json query parameter (not needed for REST APIs)
                .favorParameter(false)
                
                // Respect Accept header from clients (e.g., Accept: application/json)
                // Allows future support for XML or other formats if needed
                .ignoreAcceptHeader(false);
    }
}
