package com.aws.carddemo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Test-specific JPA Auditing Configuration for Integration Tests.
 * 
 * <p>This configuration enables JPA auditing support specifically for integration tests
 * that use @SpringBootTest with full application context. It addresses the issue where
 * the main JpaAuditingConfig is disabled for "test" profile to avoid Metamodel issues
 * in @WebMvcTest controller unit tests.</p>
 * 
 * <h2>Problem Statement</h2>
 * <p>The production {@link JpaAuditingConfig} is annotated with {@code @Profile("!test")} 
 * to prevent JPA Metamodel initialization errors in @WebMvcTest controller unit tests 
 * that don't need full JPA infrastructure. However, this causes audit fields 
 * (@CreatedDate, @LastModifiedDate, @Version) to remain null in @SpringBootTest 
 * integration tests, resulting in ConstraintViolationException when persisting entities 
 * with NOT NULL audit columns.</p>
 * 
 * <h2>Solution Approach</h2>
 * <p>This test configuration is ONLY active when the "test" profile is active, enabling 
 * JPA auditing for integration tests that create new entities at runtime. This ensures:</p>
 * <ul>
 *   <li><strong>@SpringBootTest Integration Tests:</strong> JPA auditing is enabled, 
 *       automatically populating createdAt, updatedAt, and version fields when entities 
 *       are persisted.</li>
 *   <li><strong>@WebMvcTest Controller Unit Tests:</strong> JPA auditing remains disabled 
 *       because @WebMvcTest doesn't load full application context, avoiding Metamodel 
 *       initialization issues.</li>
 * </ul>
 * 
 * <h2>Technical Details</h2>
 * <p>The configuration split works as follows:</p>
 * <table border="1">
 *   <tr>
 *     <th>Test Type</th>
 *     <th>Annotation</th>
 *     <th>Profile</th>
 *     <th>JPA Auditing Status</th>
 *   </tr>
 *   <tr>
 *     <td>Controller Unit Test</td>
 *     <td>@WebMvcTest</td>
 *     <td>test (implicit)</td>
 *     <td>DISABLED (Metamodel not loaded)</td>
 *   </tr>
 *   <tr>
 *     <td>Integration Test</td>
 *     <td>@SpringBootTest + @ActiveProfiles("test")</td>
 *     <td>test (explicit)</td>
 *     <td>ENABLED (via TestJpaAuditingConfig)</td>
 *   </tr>
 *   <tr>
 *     <td>Production</td>
 *     <td>N/A</td>
 *     <td>dev/prod</td>
 *     <td>ENABLED (via JpaAuditingConfig)</td>
 *   </tr>
 * </table>
 * 
 * <h2>Usage in Integration Tests</h2>
 * <p>Integration tests must explicitly activate the "test" profile for this configuration
 * to be automatically scanned and loaded by Spring Boot (via @SpringBootTest component scanning):</p>
 * <pre>
 * {@code @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)}
 * {@code @ActiveProfiles("test")}
 * {@code @Testcontainers}
 * public class PaymentIntegrationTest extends PostgresTestContainer {
 *     // JPA auditing automatically populates audit fields when saving entities
 *     Account account = new Account();
 *     account.setAccountNumber("12345678901");
 *     // No need to manually set createdAt, updatedAt, version
 *     accountRepository.save(account); // Audit fields auto-populated by TestJpaAuditingConfig
 * }
 * </pre>
 * 
 * <h2>Migration Context</h2>
 * <p>This addresses a gap in the COBOL-to-Java migration where COBOL programs manually
 * populated audit fields before file REWRITE operations, while Java/JPA relies on 
 * automatic auditing infrastructure. The test environment must mirror production behavior 
 * to prove functional equivalence.</p>
 * 
 * @see JpaAuditingConfig Main production JPA auditing configuration
 * @see com.aws.carddemo.model.BaseEntity Entity superclass with audit fields
 * @see com.aws.carddemo.integration.PaymentIntegrationTest Integration test requiring auditing
 */
@Configuration
@EnableJpaAuditing
@Profile("test")
public class TestJpaAuditingConfig {
    // No additional configuration needed - @EnableJpaAuditing activates auditing infrastructure
    // Only active when in test profile for @SpringBootTest integration tests
}
