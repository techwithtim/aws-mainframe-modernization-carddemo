/*
 * OpenApiConfig.java
 *
 * SpringDoc OpenAPI 3.0 Configuration
 * 
 * Migrated from: BMS screen definitions (app/bms/*.bms)
 * - COSGN00.bms: Login screen → /api/v1/auth/login
 * - COMEN01.bms: Main menu → /api/v1/menu
 * - COACTVW.bms: Account view → /api/v1/accounts/{id}
 * 
 * This configuration enables automated REST API documentation generation with
 * Swagger UI, replacing the 17 BMS 3270 terminal screens with self-documenting
 * RESTful API endpoints.
 *
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aws.carddemo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * OpenAPI 3.0 Configuration for AWS CardDemo Modernized API.
 * 
 * <p>This configuration class provides automated REST API documentation generation
 * using SpringDoc OpenAPI 3.0. It replaces the legacy BMS 3270 terminal screens
 * with modern, self-documenting RESTful API endpoints accessible via Swagger UI.</p>
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Interactive Swagger UI at /swagger-ui.html for API testing</li>
 *   <li>JWT Bearer Token authentication scheme with "Authorize" button</li>
 *   <li>Multiple server environments (dev, test, prod) configuration</li>
 *   <li>Global API response definitions for common HTTP status codes</li>
 *   <li>OpenAPI 3.0 specification endpoint at /v3/api-docs</li>
 * </ul>
 * 
 * <h2>Legacy Screen Mapping:</h2>
 * <ul>
 *   <li>COSGN00.bms (Login) → POST /api/v1/auth/login</li>
 *   <li>COMEN01.bms (Main Menu) → GET /api/v1/menu</li>
 *   <li>COADM01.bms (Admin Menu) → GET /api/v1/admin/menu</li>
 *   <li>COACTVW.bms (Account View) → GET /api/v1/accounts/{id}</li>
 *   <li>COACTUP.bms (Account Update) → PUT /api/v1/accounts/{id}</li>
 *   <li>COCRDLI.bms (Card List) → GET /api/v1/accounts/{id}/cards</li>
 *   <li>COCRDSL.bms (Card Select) → GET /api/v1/cards/{cardNumber}</li>
 *   <li>COCRDUP.bms (Card Update) → PUT /api/v1/cards/{id}</li>
 *   <li>COTRN00.bms (Transaction List) → GET /api/v1/accounts/{id}/transactions</li>
 *   <li>COTRN01.bms (Transaction View) → GET /api/v1/transactions/{id}</li>
 *   <li>COTRN02.bms (Transaction Add) → POST /api/v1/transactions</li>
 *   <li>COBIL00.bms (Bill Payment) → POST /api/v1/accounts/{id}/payments</li>
 *   <li>COUSR00.bms (User List) → GET /api/v1/admin/users</li>
 *   <li>COUSR01.bms (User Add) → POST /api/v1/admin/users</li>
 *   <li>COUSR02.bms (User Update) → PUT /api/v1/admin/users/{id}</li>
 *   <li>COUSR03.bms (User Delete) → DELETE /api/v1/admin/users/{id}</li>
 *   <li>CORPT00.bms (Reports) → GET /api/v1/reports</li>
 * </ul>
 * 
 * <h2>Security:</h2>
 * <p>All endpoints except /api/v1/auth/** require JWT bearer token authentication.
 * The Swagger UI includes an "Authorize" button for entering the JWT token obtained
 * from the login endpoint.</p>
 * 
 * <h2>Usage:</h2>
 * <pre>
 * 1. Start the application: mvn spring-boot:run
 * 2. Open Swagger UI: http://localhost:8080/swagger-ui.html
 * 3. Click "Authorize" and enter JWT token from /api/v1/auth/login
 * 4. Test API endpoints interactively through the browser
 * </pre>
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configures the OpenAPI 3.0 specification for the CardDemo Modernized API.
     * 
     * <p>This bean is automatically picked up by SpringDoc and used to generate
     * the Swagger UI documentation and OpenAPI JSON/YAML specifications.</p>
     * 
     * <h3>Configuration Includes:</h3>
     * <ul>
     *   <li><b>API Metadata:</b> Title, version, description, contact, license</li>
     *   <li><b>Security Schemes:</b> JWT Bearer Token authentication</li>
     *   <li><b>Server URLs:</b> Development, test, and production environments</li>
     *   <li><b>Global Responses:</b> Common error responses (400, 401, 403, 404, 500)</li>
     * </ul>
     * 
     * <h3>JWT Authentication:</h3>
     * <p>The security scheme is configured with:
     * <ul>
     *   <li>Type: HTTP</li>
     *   <li>Scheme: bearer</li>
     *   <li>Bearer Format: JWT</li>
     *   <li>Description: Instructions for obtaining and using JWT token</li>
     * </ul>
     * 
     * <h3>Server Environments:</h3>
     * <ul>
     *   <li><b>Development:</b> http://localhost:8080 - Local development environment</li>
     *   <li><b>Test:</b> https://test-carddemo.aws.example.com - Test environment</li>
     *   <li><b>Production:</b> https://carddemo.aws.example.com - Production environment</li>
     * </ul>
     * 
     * <h3>Global Error Responses:</h3>
     * <p>Standardized error responses are defined for:
     * <ul>
     *   <li><b>400 Bad Request:</b> Invalid input validation failure</li>
     *   <li><b>401 Unauthorized:</b> Missing or invalid JWT token</li>
     *   <li><b>403 Forbidden:</b> Insufficient permissions for operation</li>
     *   <li><b>404 Not Found:</b> Requested resource does not exist</li>
     *   <li><b>500 Internal Server Error:</b> Unexpected server-side error</li>
     * </ul>
     * 
     * @return OpenAPI configuration object with complete API documentation setup
     */
    @Bean
    public OpenAPI openApi() {
        // API Metadata - describes the modernized CardDemo API
        Info apiInfo = new Info()
                .title("AWS CardDemo Modernized API")
                .version("1.0.0")
                .description(
                    "RESTful API for the modernized AWS CardDemo credit card management application. " +
                    "This API replaces the legacy COBOL/CICS 3270 terminal interface with modern REST endpoints, " +
                    "maintaining complete functional equivalence with the original mainframe application.\n\n" +
                    "**Legacy System:** COBOL/CICS with BMS 3270 screens\n" +
                    "**Modernized Stack:** Java 21 + Spring Boot 3.3 + PostgreSQL\n\n" +
                    "**Key Features:**\n" +
                    "- Account management (view, update, list)\n" +
                    "- Card operations (list, view, update)\n" +
                    "- Transaction processing (view, add, list)\n" +
                    "- Payment processing\n" +
                    "- User administration (CRUD operations)\n" +
                    "- Report generation\n" +
                    "- JWT-based authentication and authorization\n\n" +
                    "**Authentication:**\n" +
                    "All endpoints (except /api/v1/auth/login) require a valid JWT bearer token. " +
                    "Obtain a token by authenticating via POST /api/v1/auth/login with valid credentials."
                )
                .contact(new Contact()
                        .name("AWS CardDemo Modernization Team")
                        .email("carddemo-support@aws.example.com")
                        .url("https://github.com/aws-samples/aws-card-demo-modernized")
                )
                .license(new License()
                        .name("Apache License 2.0")
                        .url("http://www.apache.org/licenses/LICENSE-2.0")
                );

        // Security Scheme - JWT Bearer Token Authentication
        SecurityScheme jwtSecurityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description(
                    "JWT Bearer Token Authentication\n\n" +
                    "**How to obtain a JWT token:**\n" +
                    "1. Send POST request to /api/v1/auth/login with username and password\n" +
                    "2. Copy the 'token' value from the response\n" +
                    "3. Click the 'Authorize' button above\n" +
                    "4. Enter the token in the format: <token_value> (no 'Bearer' prefix needed)\n" +
                    "5. Click 'Authorize' to apply the token to all requests\n\n" +
                    "**Token Expiration:**\n" +
                    "JWT tokens expire after 1 hour. If you receive a 401 Unauthorized error, " +
                    "obtain a new token by logging in again.\n\n" +
                    "**Example:**\n" +
                    "```\n" +
                    "POST /api/v1/auth/login\n" +
                    "{\n" +
                    "  \"username\": \"testuser\",\n" +
                    "  \"password\": \"password123\"\n" +
                    "}\n" +
                    "```\n" +
                    "Response:\n" +
                    "```\n" +
                    "{\n" +
                    "  \"token\": \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\",\n" +
                    "  \"expiresIn\": 3600,\n" +
                    "  \"username\": \"testuser\"\n" +
                    "}\n" +
                    "```"
                );

        // Components - reusable security schemes and response schemas
        Components components = new Components()
                .addSecuritySchemes("bearerAuth", jwtSecurityScheme);

        // Add common error response schemas
        addCommonResponseSchemas(components);

        // Security Requirement - apply JWT authentication globally
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        // Server URLs - multiple deployment environments
        Server devServer = new Server()
                .url("http://localhost:8080")
                .description("Development Server - Local development environment");

        Server testServer = new Server()
                .url("https://test-carddemo.aws.example.com")
                .description("Test Server - Integration testing environment");

        Server prodServer = new Server()
                .url("https://carddemo.aws.example.com")
                .description("Production Server - Live production environment");

        // Build and return the complete OpenAPI configuration
        return new OpenAPI()
                .info(apiInfo)
                .components(components)
                .addSecurityItem(securityRequirement)
                .servers(Arrays.asList(devServer, testServer, prodServer));
    }

    /**
     * Adds common error response schemas to the OpenAPI components.
     * 
     * <p>This method defines reusable response schemas for standard HTTP error codes
     * that can be referenced by controller methods using @ApiResponse annotations.</p>
     * 
     * <h3>Response Schemas:</h3>
     * <ul>
     *   <li><b>BadRequestResponse:</b> 400 - Invalid input or validation failure</li>
     *   <li><b>UnauthorizedResponse:</b> 401 - Missing or invalid authentication</li>
     *   <li><b>ForbiddenResponse:</b> 403 - Insufficient permissions</li>
     *   <li><b>NotFoundResponse:</b> 404 - Resource not found</li>
     *   <li><b>InternalServerErrorResponse:</b> 500 - Unexpected server error</li>
     * </ul>
     * 
     * <h3>Error Response Structure:</h3>
     * <p>All error responses follow the ApiError schema:
     * <pre>
     * {
     *   "timestamp": "2024-01-15T10:30:00Z",
     *   "status": 400,
     *   "error": "Bad Request",
     *   "message": "Validation failed for field 'accountNumber'",
     *   "path": "/api/v1/accounts/123",
     *   "errors": [
     *     {
     *       "field": "accountNumber",
     *       "rejectedValue": "ABC",
     *       "message": "must be a valid 11-digit account number"
     *     }
     *   ]
     * }
     * </pre>
     * 
     * <h3>Usage in Controllers:</h3>
     * <pre>
     * &#64;Operation(summary = "Get account by ID")
     * &#64;ApiResponses(value = {
     *     &#64;ApiResponse(responseCode = "200", description = "Account found"),
     *     &#64;ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestResponse"),
     *     &#64;ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedResponse"),
     *     &#64;ApiResponse(responseCode = "404", ref = "#/components/responses/NotFoundResponse")
     * })
     * &#64;GetMapping("/api/v1/accounts/{id}")
     * public ResponseEntity&lt;AccountResponse&gt; getAccount(&#64;PathVariable Long id) {
     *     // Implementation
     * }
     * </pre>
     * 
     * @param components the OpenAPI Components object to add response schemas to
     */
    private void addCommonResponseSchemas(Components components) {
        // Define the ApiError schema structure
        Schema<?> apiErrorSchema = new Schema<>()
                .type("object")
                .description("Standard error response structure used across all API error responses")
                .addProperty("timestamp", new Schema<>()
                        .type("string")
                        .format("date-time")
                        .description("ISO 8601 timestamp when the error occurred")
                        .example("2024-01-15T10:30:00Z"))
                .addProperty("status", new Schema<>()
                        .type("integer")
                        .description("HTTP status code")
                        .example(400))
                .addProperty("error", new Schema<>()
                        .type("string")
                        .description("HTTP status reason phrase")
                        .example("Bad Request"))
                .addProperty("message", new Schema<>()
                        .type("string")
                        .description("Detailed error message explaining what went wrong")
                        .example("Validation failed for account number field"))
                .addProperty("path", new Schema<>()
                        .type("string")
                        .description("API endpoint path where the error occurred")
                        .example("/api/v1/accounts/123"))
                .addProperty("errors", new Schema<>()
                        .type("array")
                        .description("List of detailed validation errors (for 400 Bad Request)")
                        .items(new Schema<>()
                                .type("object")
                                .addProperty("field", new Schema<>()
                                        .type("string")
                                        .description("Name of the field that failed validation")
                                        .example("accountNumber"))
                                .addProperty("rejectedValue", new Schema<>()
                                        .type("string")
                                        .description("The invalid value that was rejected")
                                        .example("ABC123"))
                                .addProperty("message", new Schema<>()
                                        .type("string")
                                        .description("Validation error message")
                                        .example("must be a valid 11-digit account number"))));

        // Add the ApiError schema to components for reuse
        components.addSchemas("ApiError", apiErrorSchema);

        // Create MediaType for JSON error responses
        MediaType jsonMediaType = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"));
        Content jsonContent = new Content().addMediaType("application/json", jsonMediaType);

        // 400 Bad Request - Invalid input or validation failure
        ApiResponse badRequestResponse = new ApiResponse()
                .description(
                    "**Bad Request**\n\n" +
                    "The request contains invalid input or failed validation checks.\n\n" +
                    "**Common Causes:**\n" +
                    "- Invalid field values (e.g., non-numeric account ID)\n" +
                    "- Missing required fields\n" +
                    "- Field length constraints violated\n" +
                    "- Invalid date formats\n" +
                    "- Business rule violations\n\n" +
                    "**Example:**\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"timestamp\": \"2024-01-15T10:30:00Z\",\n" +
                    "  \"status\": 400,\n" +
                    "  \"error\": \"Bad Request\",\n" +
                    "  \"message\": \"Validation failed\",\n" +
                    "  \"path\": \"/api/v1/accounts\",\n" +
                    "  \"errors\": [\n" +
                    "    {\n" +
                    "      \"field\": \"accountNumber\",\n" +
                    "      \"rejectedValue\": \"ABC\",\n" +
                    "      \"message\": \"must be a valid 11-digit account number\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n" +
                    "```"
                )
                .content(jsonContent);

        // 401 Unauthorized - Missing or invalid authentication
        ApiResponse unauthorizedResponse = new ApiResponse()
                .description(
                    "**Unauthorized**\n\n" +
                    "Authentication is required but was not provided or is invalid.\n\n" +
                    "**Common Causes:**\n" +
                    "- Missing Authorization header\n" +
                    "- Invalid JWT token format\n" +
                    "- Expired JWT token (tokens expire after 1 hour)\n" +
                    "- Malformed JWT token\n" +
                    "- Token signature verification failed\n\n" +
                    "**Resolution:**\n" +
                    "1. Obtain a new JWT token via POST /api/v1/auth/login\n" +
                    "2. Include the token in the Authorization header: `Authorization: Bearer <token>`\n" +
                    "3. Ensure the token has not expired\n\n" +
                    "**Example:**\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"timestamp\": \"2024-01-15T10:30:00Z\",\n" +
                    "  \"status\": 401,\n" +
                    "  \"error\": \"Unauthorized\",\n" +
                    "  \"message\": \"JWT token has expired\",\n" +
                    "  \"path\": \"/api/v1/accounts/123\"\n" +
                    "}\n" +
                    "```"
                )
                .content(jsonContent);

        // 403 Forbidden - Insufficient permissions
        ApiResponse forbiddenResponse = new ApiResponse()
                .description(
                    "**Forbidden**\n\n" +
                    "The authenticated user does not have sufficient permissions for this operation.\n\n" +
                    "**Common Causes:**\n" +
                    "- Attempting to access admin endpoints without ROLE_ADMIN\n" +
                    "- Trying to modify another user's resources\n" +
                    "- Insufficient privileges for the requested operation\n\n" +
                    "**User Roles:**\n" +
                    "- ROLE_USER: Can access account/card/transaction endpoints\n" +
                    "- ROLE_ADMIN: Can access all endpoints including user management\n\n" +
                    "**Example:**\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"timestamp\": \"2024-01-15T10:30:00Z\",\n" +
                    "  \"status\": 403,\n" +
                    "  \"error\": \"Forbidden\",\n" +
                    "  \"message\": \"Access denied. ROLE_ADMIN required.\",\n" +
                    "  \"path\": \"/api/v1/admin/users\"\n" +
                    "}\n" +
                    "```"
                )
                .content(jsonContent);

        // 404 Not Found - Resource does not exist
        ApiResponse notFoundResponse = new ApiResponse()
                .description(
                    "**Not Found**\n\n" +
                    "The requested resource does not exist in the system.\n\n" +
                    "**Common Causes:**\n" +
                    "- Invalid account ID, card number, or transaction ID\n" +
                    "- Resource has been deleted\n" +
                    "- Incorrect endpoint URL\n\n" +
                    "**Affected Resources:**\n" +
                    "- Accounts (11-digit account numbers)\n" +
                    "- Cards (16-digit card numbers)\n" +
                    "- Transactions (numeric transaction IDs)\n" +
                    "- Customers (9-digit customer IDs)\n" +
                    "- Users (username strings)\n\n" +
                    "**Example:**\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"timestamp\": \"2024-01-15T10:30:00Z\",\n" +
                    "  \"status\": 404,\n" +
                    "  \"error\": \"Not Found\",\n" +
                    "  \"message\": \"Account not found with ID: 12345678901\",\n" +
                    "  \"path\": \"/api/v1/accounts/12345678901\"\n" +
                    "}\n" +
                    "```"
                )
                .content(jsonContent);

        // 500 Internal Server Error - Unexpected server-side error
        ApiResponse internalServerErrorResponse = new ApiResponse()
                .description(
                    "**Internal Server Error**\n\n" +
                    "An unexpected error occurred on the server while processing the request.\n\n" +
                    "**Common Causes:**\n" +
                    "- Database connection failures\n" +
                    "- Unhandled application exceptions\n" +
                    "- Data integrity constraint violations\n" +
                    "- External service timeouts\n\n" +
                    "**What to do:**\n" +
                    "1. Check application logs for detailed error information\n" +
                    "2. Verify database connectivity\n" +
                    "3. Retry the request after a brief delay\n" +
                    "4. Contact support if the error persists\n\n" +
                    "**Example:**\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"timestamp\": \"2024-01-15T10:30:00Z\",\n" +
                    "  \"status\": 500,\n" +
                    "  \"error\": \"Internal Server Error\",\n" +
                    "  \"message\": \"An unexpected error occurred. Please try again later.\",\n" +
                    "  \"path\": \"/api/v1/accounts/123\"\n" +
                    "}\n" +
                    "```\n\n" +
                    "**Note:** Detailed error information is logged server-side but not exposed " +
                    "to clients for security reasons."
                )
                .content(jsonContent);

        // Add all common responses to components for reuse
        components.addResponses("BadRequestResponse", badRequestResponse);
        components.addResponses("UnauthorizedResponse", unauthorizedResponse);
        components.addResponses("ForbiddenResponse", forbiddenResponse);
        components.addResponses("NotFoundResponse", notFoundResponse);
        components.addResponses("InternalServerErrorResponse", internalServerErrorResponse);
    }
}
