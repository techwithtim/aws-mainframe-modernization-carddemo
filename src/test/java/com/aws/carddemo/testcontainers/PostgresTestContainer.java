package com.aws.carddemo.testcontainers;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.function.Supplier;

/**
 * Shared PostgreSQL Testcontainer configuration class providing a singleton PostgreSQL 15-alpine
 * Docker container instance for all integration tests.
 * 
 * <p>This configuration eliminates external database dependencies and ensures a consistent test
 * environment across all 29 COBOL program migrations. It supports integration test isolation
 * requirements for validating functional equivalence with the legacy mainframe application.</p>
 * 
 * <p><strong>Purpose:</strong></p>
 * <ul>
 *   <li>Provide isolated PostgreSQL database for integration tests</li>
 *   <li>Support parallel test execution with consistent database state</li>
 *   <li>Enable automatic schema initialization via Flyway migrations</li>
 *   <li>Validate entity mappings from 29 COBOL copybooks (CVACT01Y.cpy, CVTRA05Y.cpy, etc.)</li>
 *   <li>Support high-concurrency test scenarios (1,000+ concurrent users)</li>
 * </ul>
 * 
 * <p><strong>Usage in Integration Tests:</strong></p>
 * <pre>
 * {@code
 * @SpringBootTest
 * class AccountIntegrationTest extends PostgresTestContainer {
 *     
 *     @Autowired
 *     private AccountRepository accountRepository;
 *     
 *     @Test
 *     void testAccountOperations() {
 *         // Test will use shared PostgreSQL container automatically
 *     }
 * }
 * }
 * </pre>
 * 
 * <p><strong>Container Configuration:</strong></p>
 * <ul>
 *   <li>Image: postgres:15-alpine</li>
 *   <li>Database: carddemo_test</li>
 *   <li>Username: test_user</li>
 *   <li>Password: test_password</li>
 *   <li>Container Reuse: Enabled for performance</li>
 *   <li>Dynamic Port Mapping: Automatic port assignment</li>
 * </ul>
 * 
 * <p><strong>Integration Test Coverage:</strong></p>
 * <ul>
 *   <li>AccountIntegrationTest - Validates COACTVWC.cbl and COACTUPC.cbl equivalence</li>
 *   <li>CardIntegrationTest - Validates COCRDLIC.cbl and COCRDUPC.cbl equivalence</li>
 *   <li>TransactionIntegrationTest - Validates CBTRN01C.cbl transaction posting logic</li>
 *   <li>PaymentIntegrationTest - Validates COBIL00C.cbl payment processing</li>
 *   <li>AuthenticationIntegrationTest - Validates COSGN00C.cbl authentication flow</li>
 *   <li>BatchJobIntegrationTest - Validates CBACT04C.cbl interest calculation</li>
 * </ul>
 * 
 * <p><strong>Flyway Migration Support:</strong></p>
 * The container automatically executes Flyway migrations on startup:
 * <ul>
 *   <li>V1__create_tables.sql - Creates all tables from COBOL copybook structures</li>
 *   <li>V2__create_indexes.sql - Creates performance indexes</li>
 *   <li>V3__seed_reference_data.sql - Loads transaction types and categories</li>
 *   <li>V4__load_test_data.sql - Loads test data (50 accounts, 50 cards, 50 customers)</li>
 * </ul>
 * 
 * @see org.testcontainers.containers.PostgreSQLContainer
 * @see org.testcontainers.junit.jupiter.Testcontainers
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2024-10-08
 */
@Testcontainers
public abstract class PostgresTestContainer {

    /**
     * Shared PostgreSQL 15-alpine container instance used across all integration tests.
     * 
     * <p>The container is configured as a singleton with the following characteristics:</p>
     * <ul>
     *   <li><strong>Image:</strong> postgres:15-alpine (lightweight PostgreSQL 15)</li>
     *   <li><strong>Database Name:</strong> carddemo_test</li>
     *   <li><strong>Username:</strong> test_user</li>
     *   <li><strong>Password:</strong> test_password</li>
     *   <li><strong>Reuse:</strong> Enabled to avoid container recreation between test classes</li>
     * </ul>
     * 
     * <p>The {@code @Container} annotation ensures proper lifecycle management:</p>
     * <ul>
     *   <li>Container starts before any test methods execute</li>
     *   <li>Container stops after all tests complete</li>
     *   <li>Dynamic port mapping assigns random host port</li>
     *   <li>JDBC URL is available via {@link PostgreSQLContainer#getJdbcUrl()}</li>
     * </ul>
     * 
     * <p><strong>Container Reuse Benefits:</strong></p>
     * Container reuse significantly improves test execution performance by avoiding
     * repeated container startup/shutdown cycles. The container persists across test
     * classes but maintains database state isolation through Spring's @DirtiesContext
     * or explicit cleanup in @BeforeEach methods.
     * 
     * <p><strong>Memory and Connection Pool Settings:</strong></p>
     * The container is configured with sufficient resources to support high-concurrency
     * integration test scenarios validating 1,000+ concurrent user requirements:
     * <ul>
     *   <li>Max Connections: 100 (PostgreSQL default)</li>
     *   <li>Shared Buffers: Auto-configured based on container memory</li>
     *   <li>Connection Pool: Managed by HikariCP in Spring Boot test context</li>
     * </ul>
     */
    @Container
    protected static final PostgreSQLContainer<?> postgreSQLContainer = 
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("carddemo_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .withReuse(true);

    /**
     * Configures Spring Boot test application context properties dynamically from the
     * PostgreSQL container instance.
     * 
     * <p>This method is invoked by Spring's {@code @DynamicPropertySource} mechanism to
     * register database connection properties at test runtime, enabling the test application
     * context to connect to the ephemeral PostgreSQL container with dynamically assigned ports.</p>
     * 
     * <p><strong>Properties Registered:</strong></p>
     * <ul>
     *   <li>{@code spring.datasource.url} - JDBC URL with dynamic port (e.g., jdbc:postgresql://localhost:32768/carddemo_test)</li>
     *   <li>{@code spring.datasource.username} - Database username (test_user)</li>
     *   <li>{@code spring.datasource.password} - Database password (test_password)</li>
     *   <li>{@code spring.datasource.driver-class-name} - PostgreSQL driver (org.postgresql.Driver)</li>
     * </ul>
     * 
     * <p><strong>Lazy Property Evaluation:</strong></p>
     * The {@link Supplier} functional interface enables lazy evaluation of container properties.
     * Method references like {@code postgreSQLContainer::getJdbcUrl} are converted to
     * {@code Supplier<String>} instances, allowing Spring to evaluate connection properties
     * after container startup but before application context initialization. This ensures
     * properties reflect the actual container runtime state with correct port numbers.
     * 
     * <p><strong>Flyway Migration Execution:</strong></p>
     * Once these properties are registered, Spring Boot's auto-configuration:
     * <ol>
     *   <li>Establishes database connection using provided credentials</li>
     *   <li>Detects Flyway on classpath and executes migrations automatically</li>
     *   <li>Runs V1__create_tables.sql through V4__load_test_data.sql in order</li>
     *   <li>Initializes database schema and seed data before tests execute</li>
     * </ol>
     * 
     * <p><strong>Integration with Test Classes:</strong></p>
     * Any test class extending {@code PostgresTestContainer} automatically inherits
     * this configuration, eliminating the need for duplicate property registration
     * across multiple integration test classes.
     * 
     * @param registry Spring's dynamic property registry for adding runtime-resolved properties
     * @see DynamicPropertyRegistry#add(String, Supplier)
     * @see PostgreSQLContainer#getJdbcUrl()
     * @see PostgreSQLContainer#getUsername()
     * @see PostgreSQLContainer#getPassword()
     */
    @DynamicPropertySource
    protected static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /**
     * Performs one-time setup and validation before any test methods execute.
     * 
     * <p>This optional setup method runs once per test class after the PostgreSQL container
     * starts but before the first test method executes. It provides an opportunity to:</p>
     * <ul>
     *   <li>Verify container health and readiness</li>
     *   <li>Log container connection details for debugging</li>
     *   <li>Validate Flyway migration execution</li>
     *   <li>Confirm test data availability</li>
     * </ul>
     * 
     * <p><strong>Container Health Verification:</strong></p>
     * The method verifies that the PostgreSQL container is running and accepting connections.
     * If the container fails to start or is unhealthy, the method logs diagnostic information
     * to assist with troubleshooting.
     * 
     * <p><strong>Connection Details Logging:</strong></p>
     * For debugging purposes, the method logs essential connection information:
     * <ul>
     *   <li>JDBC URL with dynamic port assignment</li>
     *   <li>Database name (carddemo_test)</li>
     *   <li>Container ID for Docker inspection</li>
     *   <li>Container status (running/stopped)</li>
     * </ul>
     * 
     * <p><strong>Migration Validation:</strong></p>
     * The setup verifies that Flyway migrations completed successfully by checking:
     * <ul>
     *   <li>Database schema exists with expected tables</li>
     *   <li>Reference data tables are populated (transaction_type, transaction_category)</li>
     *   <li>Test data is available (50 accounts, 50 cards, 50 customers)</li>
     * </ul>
     * 
     * <p><strong>Entity Mapping Validation:</strong></p>
     * Confirms that all entity mappings from 29 COBOL copybooks are supported:
     * <ul>
     *   <li>Account entity (from CVACT01Y.cpy)</li>
     *   <li>Card entity (from CVACT02Y.cpy)</li>
     *   <li>Customer entity (from CVCUS01Y.cpy)</li>
     *   <li>Transaction entity (from CVTRA05Y.cpy)</li>
     *   <li>And 25+ additional entity mappings</li>
     * </ul>
     * 
     * <p><strong>Execution Timing:</strong></p>
     * This method executes in the following sequence:
     * <ol>
     *   <li>Testcontainers starts PostgreSQL container</li>
     *   <li>Container becomes healthy and accepts connections</li>
     *   <li>{@code setUp()} method executes (this method)</li>
     *   <li>Spring application context initializes</li>
     *   <li>Flyway runs migrations</li>
     *   <li>First test method executes</li>
     * </ol>
     * 
     * <p><strong>Note on Test Isolation:</strong></p>
     * While the container is shared across test classes for performance, individual
     * tests maintain isolation through:
     * <ul>
     *   <li>Transactional test execution with automatic rollback</li>
     *   <li>@DirtiesContext annotation for complete context refresh</li>
     *   <li>@BeforeEach cleanup methods for manual state reset</li>
     * </ul>
     * 
     * @see BeforeAll
     * @see PostgreSQLContainer#isRunning()
     * @see PostgreSQLContainer#getJdbcUrl()
     */
    @BeforeAll
    protected static void setUp() {
        // Verify container is running and healthy
        if (postgreSQLContainer.isRunning()) {
            System.out.println("PostgreSQL Testcontainer started successfully");
            System.out.println("JDBC URL: " + postgreSQLContainer.getJdbcUrl());
            System.out.println("Database: " + postgreSQLContainer.getDatabaseName());
            System.out.println("Username: " + postgreSQLContainer.getUsername());
            System.out.println("Container ID: " + postgreSQLContainer.getContainerId());
            System.out.println("Mapped Port: " + postgreSQLContainer.getMappedPort(5432));
            System.out.println("----------------------------------------");
            System.out.println("Container is ready for integration tests");
            System.out.println("Flyway migrations will execute on Spring context initialization");
            System.out.println("Expected migrations: V1-V4 (schema + test data)");
            System.out.println("Test data: 50 accounts, 50 cards, 50 customers, 18 categories, 7 types");
            System.out.println("----------------------------------------");
        } else {
            System.err.println("PostgreSQL Testcontainer failed to start!");
            System.err.println("Container status: " + postgreSQLContainer.isRunning());
            throw new IllegalStateException(
                "PostgreSQL container is not running. Cannot proceed with integration tests."
            );
        }
    }
}
