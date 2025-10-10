package com.aws.carddemo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing Configuration.
 * 
 * <p>This configuration enables JPA auditing support for automatic population of 
 * audit fields (@CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy) on 
 * entity classes extending BaseEntity.</p>
 * 
 * <p><strong>Extracted from CardDemoApplication</strong> to allow @WebMvcTest slices 
 * to exclude JPA auditing infrastructure when testing REST controllers in isolation. 
 * This prevents "Metamodel must not be null" errors in controller unit tests that don 
 * not require full JPA setup.</p>
 * 
 * <p><strong>Profile Configuration:</strong> This configuration is only active when 
 * the "!test" profile is active. This ensures that JPA auditing is disabled during 
 * unit tests that use @WebMvcTest and do not need full JPA infrastructure.</p>
 * 
 * <h2>Migration Context</h2>
 * <p><strong>Replaces:</strong> COBOL application-level audit tracking (user IDs and 
 * timestamps in WORKING-STORAGE sections)</p>
 * <p><strong>Original Pattern:</strong> Manual population of audit fields like 
 * LAST-UPD-USER-ID and LAST-UPD-TIMESTAMP in COBOL programs before file REWRITE 
 * operations</p>
 * 
 * <h2>Auditing Behavior</h2>
 * <p>When enabled, Spring Data JPA automatically populates:</p>
 * <ul>
 *   <li><strong>@CreatedDate:</strong> Timestamp when entity is first persisted</li>
 *   <li><strong>@LastModifiedDate:</strong> Timestamp when entity is updated</li>
 *   <li><strong>@CreatedBy:</strong> User ID from SecurityContext when entity is created</li>
 *   <li><strong>@LastModifiedBy:</strong> User ID from SecurityContext when entity is updated</li>
 * </ul>
 * 
 * <h2>Configuration Details</h2>
 * <p>This configuration is automatically discovered by Spring Boot component scanning 
 * and applied to all JPA repositories and entity classes when not in test profile. It requires:</p>
 * <ul>
 *   <li>Active JPA EntityManagerFactory (provided by DataSourceConfig)</li>
 *   <li>Entities with @EntityListeners(AuditingEntityListener.class) annotation</li>
 *   <li>SecurityContext with authenticated user for @CreatedBy/@LastModifiedBy</li>
 * </ul>
 * 
 * @see org.springframework.data.jpa.domain.support.AuditingEntityListener
 * @see com.aws.carddemo.model.BaseEntity
 * @see org.springframework.data.annotation.CreatedDate
 * @see org.springframework.data.annotation.LastModifiedDate
 * @see org.springframework.data.annotation.CreatedBy
 * @see org.springframework.data.annotation.LastModifiedBy
 */
@Configuration
@EnableJpaAuditing
@Profile("!test")
public class JpaAuditingConfig {
    // No additional configuration needed - @EnableJpaAuditing activates auditing infrastructure
    // Only active when NOT in test profile to avoid JPA Metamodel initialization in @WebMvcTest
}
