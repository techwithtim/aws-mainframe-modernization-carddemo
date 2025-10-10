package com.aws.carddemo;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Spring Boot Main Application Entry Point for AWS CardDemo Modernized.
 * 
 * <p>This class serves as the bootstrap entry point for the modernized credit card 
 * management system, replacing the legacy mainframe CICS transaction server startup 
 * process. It initializes the Spring IoC container, configures the embedded Tomcat 
 * web server, and activates all Spring Boot auto-configuration features.</p>
 * 
 * <h2>Migration Context</h2>
 * <p><strong>Migrated from:</strong> Mainframe CICS region startup and transaction 
 * server initialization</p>
 * <p><strong>Original System:</strong> COBOL/CICS/VSAM mainframe application</p>
 * <p><strong>Target System:</strong> Java 21 with Spring Boot 3.3.0, PostgreSQL, 
 * Docker, and Kubernetes</p>
 * 
 * <h2>Enabled Features</h2>
 * <ul>
 *   <li><strong>@SpringBootApplication:</strong> Combines @Configuration, 
 *       @EnableAutoConfiguration, and @ComponentScan to enable Spring Boot's 
 *       auto-configuration, component discovery, and Spring Boot features</li>
 *   <li><strong>@EnableBatchProcessing:</strong> Activates Spring Batch infrastructure 
 *       including JobRepository, JobLauncher, and batch processing capabilities for 
 *       scheduled jobs migrated from COBOL batch programs (transaction posting via 
 *       CBTRN01C/02C, interest calculation via CBACT04C, statement generation via 
 *       CBSTM03A)</li>
 *   <li><strong>@EnableTransactionManagement:</strong> Enables Spring's annotation-driven 
 *       transaction management, supporting @Transactional annotations on service layer 
 *       methods to replicate COBOL EXEC CICS SYNCPOINT (commit) and SYNCPOINT ROLLBACK 
 *       (rollback) behavior</li>
 *   <li><strong>@ComponentScan:</strong> Explicitly configures component scanning across 
 *       the com.aws.carddemo package to ensure all Spring-managed components (REST 
 *       controllers, business services, JPA repositories, configuration classes) are 
 *       discovered and registered in the application context</li>
 *   <li><strong>JPA Auditing:</strong> Automatic auditing of entity creation and 
 *       modification timestamps is enabled via {@link com.aws.carddemo.config.JpaAuditingConfig},
 *       which provides the @EnableJpaAuditing annotation to activate auditing infrastructure 
 *       for {@link org.springframework.data.annotation.CreatedDate} and 
 *       {@link org.springframework.data.annotation.LastModifiedDate} fields in 
 *       {@link com.aws.carddemo.model.BaseEntity}</li>
 * </ul>
 * 
 * <h2>System Initialization</h2>
 * <p>When executed, this class performs the following startup sequence:</p>
 * <ol>
 *   <li>Initialize Spring Boot application context</li>
 *   <li>Load configuration from application.yml (profile-specific: dev/test/prod)</li>
 *   <li>Configure PostgreSQL datasource with HikariCP connection pooling</li>
 *   <li>Initialize JPA/Hibernate ORM for database entity management</li>
 *   <li>Set up Spring Security with JWT authentication (replacing RACF security)</li>
 *   <li>Register Spring Batch jobs for scheduled execution</li>
 *   <li>Start embedded Tomcat server on port 8080</li>
 *   <li>Expose Spring Actuator endpoints for health checks and monitoring</li>
 *   <li>Begin accepting REST API requests at /api/v1/* endpoints</li>
 * </ol>
 * 
 * <h2>Deployment Architecture</h2>
 * <p>This application is designed for container-first deployment:</p>
 * <ul>
 *   <li><strong>Docker:</strong> Packaged as a multi-stage Docker image with Eclipse 
 *       Temurin 21 JRE runtime</li>
 *   <li><strong>Kubernetes:</strong> Deployed as a Deployment resource with 3 replicas, 
 *       LoadBalancer service, ConfigMap for externalized configuration, and Secrets for 
 *       database credentials</li>
 *   <li><strong>Health Checks:</strong> Liveness and readiness probes configured via 
 *       /actuator/health endpoint</li>
 *   <li><strong>Auto-Scaling:</strong> Horizontal Pod Autoscaler (HPA) based on CPU 
 *       and memory metrics</li>
 * </ul>
 * 
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><strong>Concurrent Users:</strong> Supports 1,000+ concurrent users</li>
 *   <li><strong>API Response Time:</strong> &lt;200ms at 95th percentile for account 
 *       inquiry operations</li>
 *   <li><strong>Batch Throughput:</strong> 1,000 transactions/second for transaction 
 *       posting jobs</li>
 *   <li><strong>Resource Limits:</strong> 1Gi memory max, 1000m CPU max per pod</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <p><strong>Local Development:</strong></p>
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.profiles=dev
 * </pre>
 * 
 * <p><strong>Docker Execution:</strong></p>
 * <pre>
 * docker build -t carddemo:latest .
 * docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod carddemo:latest
 * </pre>
 * 
 * <p><strong>Kubernetes Deployment:</strong></p>
 * <pre>
 * kubectl apply -f k8s/namespace.yml
 * kubectl apply -f k8s/
 * </pre>
 * 
 * <h2>Configuration Profiles</h2>
 * <ul>
 *   <li><strong>dev:</strong> Local development with H2 in-memory database or local 
 *       PostgreSQL</li>
 *   <li><strong>test:</strong> Integration testing with Testcontainers PostgreSQL</li>
 *   <li><strong>prod:</strong> Production deployment with AWS RDS PostgreSQL, AWS 
 *       Secrets Manager for credentials, and CloudWatch logging</li>
 * </ul>
 * 
 * <h2>Security Compliance</h2>
 * <p>This application implements PCI-DSS compliance requirements including:</p>
 * <ul>
 *   <li>Sensitive data masking in logs (card numbers, SSN, CVV codes)</li>
 *   <li>BCrypt password hashing (10 rounds minimum)</li>
 *   <li>JWT-based authentication with 1-hour token expiration</li>
 *   <li>Role-based access control (ROLE_USER, ROLE_ADMIN)</li>
 *   <li>TLS 1.3 for data in transit (HTTPS only)</li>
 *   <li>Database encryption at rest (AWS RDS encryption)</li>
 * </ul>
 * 
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 1.0.0
 * @see org.springframework.boot.SpringApplication
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 */
@SpringBootApplication
@EnableBatchProcessing
@EnableTransactionManagement
@ComponentScan(basePackages = "com.aws.carddemo")
public class CardDemoApplication {

    /**
     * Main method that bootstraps the Spring Boot application.
     * 
     * <p>This method serves as the entry point for the JVM and delegates to 
     * {@link SpringApplication#run(Class, String...)} to initialize the Spring 
     * application context, configure all beans, start the embedded Tomcat server, 
     * and begin accepting HTTP requests.</p>
     * 
     * <h3>Startup Behavior</h3>
     * <p>The SpringApplication.run() method performs the following operations:</p>
     * <ol>
     *   <li>Create a new ApplicationContext instance</li>
     *   <li>Register CommandLinePropertySource to expose command line arguments as 
     *       properties</li>
     *   <li>Refresh the application context to load all bean definitions</li>
     *   <li>Trigger any CommandLineRunner and ApplicationRunner beans</li>
     *   <li>Indicate the application is ready to accept requests</li>
     * </ol>
     * 
     * <h3>Environment Variables</h3>
     * <p>The application respects the following environment variables:</p>
     * <ul>
     *   <li><strong>SPRING_PROFILES_ACTIVE:</strong> Active Spring profile 
     *       (dev/test/prod)</li>
     *   <li><strong>DB_HOST:</strong> PostgreSQL database hostname</li>
     *   <li><strong>DB_NAME:</strong> Database name (default: carddemo)</li>
     *   <li><strong>DB_USER:</strong> Database username</li>
     *   <li><strong>DB_PASS:</strong> Database password</li>
     *   <li><strong>JWT_SECRET:</strong> Secret key for JWT token signing</li>
     *   <li><strong>SERVER_PORT:</strong> HTTP server port (default: 8080)</li>
     * </ul>
     * 
     * <h3>Command Line Arguments</h3>
     * <p>Supports standard Spring Boot command line arguments:</p>
     * <ul>
     *   <li><code>--spring.profiles.active=prod</code> - Activate production profile</li>
     *   <li><code>--server.port=9090</code> - Override server port</li>
     *   <li><code>--debug</code> - Enable debug logging</li>
     *   <li><code>--spring.batch.job.enabled=false</code> - Disable automatic batch 
     *       job execution on startup</li>
     * </ul>
     * 
     * <h3>Graceful Shutdown</h3>
     * <p>The application supports graceful shutdown via SIGTERM signal handling, 
     * ensuring:</p>
     * <ul>
     *   <li>Existing HTTP requests complete processing (30-second grace period)</li>
     *   <li>Active batch jobs are allowed to finish or checkpoint</li>
     *   <li>Database connections are properly closed</li>
     *   <li>Resource cleanup is performed before JVM exit</li>
     * </ul>
     * 
     * <h3>Exit Codes</h3>
     * <ul>
     *   <li><strong>0:</strong> Successful startup and shutdown</li>
     *   <li><strong>1:</strong> General application error or startup failure</li>
     *   <li><strong>137:</strong> Killed by Kubernetes (SIGKILL) - typically indicates 
     *       OOMKilled or exceeded liveness probe failure threshold</li>
     * </ul>
     * 
     * @param args command line arguments passed to the application, which can include 
     *             Spring Boot configuration properties, profile activation flags, and 
     *             application-specific parameters
     * 
     * @throws IllegalArgumentException if required configuration properties are missing
     * @throws IllegalStateException if application context initialization fails (e.g., 
     *         database connection failure, bean creation error)
     * 
     * @see SpringApplication#run(Class, String...)
     * @see org.springframework.context.ConfigurableApplicationContext
     */
    public static void main(String[] args) {
        // Log startup initiation for operational visibility
        System.out.println("=============================================================");
        System.out.println("  AWS CardDemo Modernized - Starting Application");
        System.out.println("  Migrated from: COBOL/CICS/VSAM Mainframe Application");
        System.out.println("  Target Stack: Java 21 + Spring Boot 3.3.0 + PostgreSQL");
        System.out.println("  Deployment: Docker + Kubernetes");
        System.out.println("=============================================================");
        
        // Bootstrap the Spring Boot application
        // This replaces the mainframe CICS region startup process
        SpringApplication.run(CardDemoApplication.class, args);
        
        // Log successful startup confirmation
        // Note: This line executes after the application context is fully initialized
        // and the embedded Tomcat server has started accepting connections
        System.out.println("=============================================================");
        System.out.println("  AWS CardDemo Application Started Successfully");
        System.out.println("  Application is ready to accept requests");
        System.out.println("  Health Check: http://localhost:8080/actuator/health");
        System.out.println("  API Documentation: http://localhost:8080/swagger-ui.html");
        System.out.println("=============================================================");
    }
}
