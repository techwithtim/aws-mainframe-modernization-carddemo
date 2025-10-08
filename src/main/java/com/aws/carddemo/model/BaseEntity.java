package com.aws.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Abstract base entity class providing common audit fields and optimistic locking
 * support for all domain entities in the CardDemo application.
 * 
 * <p>This class is part of the COBOL-to-Java modernization effort and provides
 * enterprise-grade features that were implicit in the legacy mainframe system:
 * <ul>
 *   <li>Automatic audit timestamps (creation and last modification)</li>
 *   <li>Optimistic locking for concurrent modification prevention</li>
 *   <li>Serializable support for distributed caching</li>
 * </ul>
 * 
 * <p><b>JPA Auditing:</b> This class uses Spring Data JPA auditing features via
 * {@code @EntityListeners(AuditingEntityListener.class)}. The auditing framework
 * automatically populates {@code createdAt} and {@code updatedAt} fields during
 * entity lifecycle events. Requires {@code @EnableJpaAuditing} annotation on the
 * main application class.
 * 
 * <p><b>Optimistic Locking:</b> The {@code version} field with {@code @Version}
 * annotation enables Hibernate's optimistic locking strategy. When an entity is
 * updated, Hibernate automatically:
 * <ol>
 *   <li>Increments the version number</li>
 *   <li>Includes version in the UPDATE WHERE clause (UPDATE ... WHERE version=N)</li>
 *   <li>Throws {@code OptimisticLockException} if the version mismatch indicates
 *       concurrent modification, requiring application-level retry with refreshed entity</li>
 * </ol>
 * 
 * <p><b>Database Schema:</b> All tables inheriting from this class will have:
 * <pre>
 * created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 * updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 * version INTEGER NOT NULL DEFAULT 0
 * </pre>
 * 
 * <p><b>Builder Pattern:</b> Uses Lombok {@code @SuperBuilder} for inheritance-aware
 * fluent construction:
 * <pre>
 * Account account = Account.builder()
 *     .accountNumber("1234567890")
 *     .createdAt(LocalDateTime.now())  // Base class field
 *     .build();
 * </pre>
 * 
 * <p><b>Subclasses:</b> All 11 concrete entity classes extend this base:
 * <ul>
 *   <li>Account (from CVACT01Y.cpy)</li>
 *   <li>Card (from CVACT02Y.cpy)</li>
 *   <li>CardXref (from CVACT03Y.cpy)</li>
 *   <li>Customer (from CVCUS01Y.cpy)</li>
 *   <li>Transaction (from CVTRA05Y.cpy)</li>
 *   <li>DailyTransaction (from CVTRA06Y.cpy)</li>
 *   <li>TransactionCategoryBalance (from CVTRA01Y.cpy)</li>
 *   <li>DisclosureGroup (from CVTRA02Y.cpy)</li>
 *   <li>User (from CSUSR01Y.cpy)</li>
 *   <li>TransactionType (from CVTRA03Y.cpy)</li>
 *   <li>TransactionCategory (from CVTRA04Y.cpy)</li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 6.2.2.1: Core Entity Structures - BaseEntity provides audit fields
 *       and optimistic locking inherited by all entities</li>
 *   <li>Section 0.1.1: Primary Goal #5 - Replace CICS transaction management with
 *       Spring @Transactional and JPA @Version optimistic locking</li>
 *   <li>Section 6.2.1.1: ACID Compliance - @Version enables optimistic locking
 *       preventing lost updates in concurrent scenarios</li>
 * </ul>
 * 
 * @see AuditingEntityListener
 * @see org.springframework.data.jpa.repository.config.EnableJpaAuditing
 * @see jakarta.persistence.OptimisticLockException
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseEntity implements Serializable {

    /**
     * Serialization version UID for compatibility across distributed systems.
     * Updated when class structure changes in a non-compatible way.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Timestamp of entity creation, automatically populated by Spring Data JPA
     * auditing framework on initial persist operation.
     * 
     * <p><b>JPA Auditing:</b> The {@code @CreatedDate} annotation triggers the
     * {@code AuditingEntityListener} to populate this field with the current
     * timestamp during {@code @PrePersist} lifecycle callback.
     * 
     * <p><b>Immutability:</b> The {@code updatable=false} attribute ensures this
     * value cannot be changed after initial creation, preserving audit integrity.
     * 
     * <p><b>Database Default:</b> PostgreSQL schema includes
     * {@code DEFAULT CURRENT_TIMESTAMP} as a fallback for non-JPA inserts.
     * 
     * <p><b>Legacy Mapping:</b> Replaces implicit COBOL file creation timestamps
     * that were managed by VSAM catalog system on mainframe.
     * 
     * @see CreatedDate
     * @see AuditingEntityListener
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of last entity modification, automatically updated by Spring Data
     * JPA auditing framework on every update operation.
     * 
     * <p><b>JPA Auditing:</b> The {@code @LastModifiedDate} annotation triggers the
     * {@code AuditingEntityListener} to refresh this field with the current
     * timestamp during {@code @PreUpdate} lifecycle callback.
     * 
     * <p><b>Automatic Tracking:</b> Any change to entity fields (via JPA merge or
     * update) triggers automatic timestamp refresh, providing accurate modification
     * audit trail without explicit application code.
     * 
     * <p><b>Database Trigger:</b> PostgreSQL schema includes
     * {@code ON UPDATE CURRENT_TIMESTAMP} trigger for non-JPA updates, ensuring
     * consistency across all database modification paths.
     * 
     * <p><b>Legacy Mapping:</b> Replaces COBOL program execution timestamps that
     * were written to audit logs during CICS transaction SYNCPOINT operations.
     * 
     * @see LastModifiedDate
     * @see AuditingEntityListener
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Version number for Hibernate optimistic locking, automatically incremented
     * on each update operation to prevent lost updates in concurrent scenarios.
     * 
     * <p><b>Optimistic Locking Strategy:</b> Hibernate automatically:
     * <ol>
     *   <li>Includes version in SELECT queries to load current state</li>
     *   <li>Increments version by 1 on UPDATE operations</li>
     *   <li>Adds {@code WHERE version = ?} clause to UPDATE statement</li>
     *   <li>Throws {@code OptimisticLockException} if UPDATE affects 0 rows,
     *       indicating another transaction modified the entity</li>
     * </ol>
     * 
     * <p><b>Concurrency Control:</b> This mechanism prevents "lost update" problem
     * where two transactions read the same entity, modify different fields, and
     * both commit - without versioning, the first update would be lost. Example:
     * <pre>
     * // Transaction A reads Account with version=5, modifies balance
     * // Transaction B reads same Account with version=5, modifies status
     * // Transaction A commits: UPDATE account SET balance=1000, version=6 WHERE id=1 AND version=5 [SUCCESS]
     * // Transaction B commits: UPDATE account SET status='ACTIVE', version=6 WHERE id=1 AND version=5 [FAILURE]
     * // OptimisticLockException thrown, Transaction B must reload entity and retry
     * </pre>
     * 
     * <p><b>Application Handling:</b> Service layer methods annotated with
     * {@code @Transactional} should catch {@code OptimisticLockException} and
     * implement retry logic (typically 3 attempts) or inform user of conflict.
     * 
     * <p><b>Database Default:</b> PostgreSQL schema includes {@code DEFAULT 0}
     * for initial version value on entity creation.
     * 
     * <p><b>Legacy Mapping:</b> Replaces CICS implicit transaction serialization
     * where mainframe region locking prevented concurrent updates to same record.
     * Modern cloud-native applications require explicit optimistic locking for
     * horizontal scalability across multiple application instances.
     * 
     * <p><b>Performance Note:</b> Optimistic locking has near-zero overhead
     * compared to pessimistic locking (SELECT FOR UPDATE), making it ideal for
     * read-heavy workloads with infrequent conflicts. For high-contention scenarios,
     * consider pessimistic locking or event sourcing patterns.
     * 
     * @see Version
     * @see jakarta.persistence.OptimisticLockException
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * Returns the creation timestamp of this entity.
     * 
     * <p>This timestamp is immutable after initial entity creation and represents
     * the exact moment the entity was first persisted to the database. Useful for
     * audit reports, data retention policies, and temporal queries.
     * 
     * @return the creation timestamp, never null after persistence
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp of this entity.
     * 
     * <p><b>Warning:</b> This method should generally not be called directly by
     * application code. The {@code @CreatedDate} annotation ensures automatic
     * population during entity insertion. Manual setting is only appropriate for:
     * <ul>
     *   <li>Test data setup with specific timestamps</li>
     *   <li>Data migration scripts from legacy systems</li>
     *   <li>Backdating entities for historical record reconstruction</li>
     * </ul>
     * 
     * <p><b>JPA Note:</b> Even if set explicitly, JPA may override this value
     * during {@code @PrePersist} callback if auditing is enabled. For test
     * scenarios requiring fixed timestamps, consider disabling JPA auditing.
     * 
     * @param createdAt the creation timestamp to set, should not be null
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the last modification timestamp of this entity.
     * 
     * <p>This timestamp is automatically refreshed on every update operation,
     * providing an accurate audit trail of entity modifications. Useful for:
     * <ul>
     *   <li>Cache invalidation strategies (TTL based on staleness)</li>
     *   <li>Change detection in synchronization processes</li>
     *   <li>Debugging concurrent modification issues</li>
     *   <li>Compliance reporting for audit trails</li>
     * </ul>
     * 
     * @return the last modification timestamp, never null after first update
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last modification timestamp of this entity.
     * 
     * <p><b>Warning:</b> This method should generally not be called directly by
     * application code. The {@code @LastModifiedDate} annotation ensures automatic
     * refresh during entity updates. Manual setting is only appropriate for:
     * <ul>
     *   <li>Test data setup with specific timestamps</li>
     *   <li>Data migration scripts preserving legacy modification times</li>
     *   <li>Batch operations bypassing JPA auditing for performance</li>
     * </ul>
     * 
     * <p><b>JPA Note:</b> JPA will override this value during {@code @PreUpdate}
     * callback if auditing is enabled. For bulk update operations where auditing
     * overhead is prohibitive, use native SQL or JPQL bulk updates.
     * 
     * @param updatedAt the last modification timestamp to set, should not be null
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Returns the current version number for optimistic locking.
     * 
     * <p>The version starts at 0 for new entities and increments by 1 on each
     * successful update. This value is used internally by Hibernate to detect
     * concurrent modifications and prevent lost updates.
     * 
     * <p><b>Application Usage:</b> Applications can use this value for:
     * <ul>
     *   <li>Displaying entity version to users (e.g., "Document v5")</li>
     *   <li>Implementing custom concurrency conflict resolution UI</li>
     *   <li>Logging version changes for debugging concurrent access patterns</li>
     *   <li>Building version history tables for full audit trails</li>
     * </ul>
     * 
     * @return the current version number, null for transient entities, 0+ for persistent
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * Sets the version number for optimistic locking.
     * 
     * <p><b>Warning:</b> This method should NEVER be called by application code.
     * The {@code @Version} annotation ensures Hibernate manages this field
     * automatically. Manual manipulation will break optimistic locking and lead to:
     * <ul>
     *   <li>Concurrent updates overwriting each other (lost updates)</li>
     *   <li>Inconsistent version sequences in the database</li>
     *   <li>False positive or false negative {@code OptimisticLockException}s</li>
     * </ul>
     * 
     * <p><b>Valid Use Cases:</b> Only acceptable for:
     * <ul>
     *   <li>Test data initialization where version must be set explicitly</li>
     *   <li>Data migration from legacy systems preserving version history</li>
     *   <li>Framework-level code that understands JPA lifecycle semantics</li>
     * </ul>
     * 
     * <p><b>JPA Lifecycle:</b> Hibernate manages version as follows:
     * <ul>
     *   <li>Transient entity: version is null</li>
     *   <li>After persist: version is set to 0 or database default</li>
     *   <li>After each update: version is incremented by 1</li>
     *   <li>After merge: version matches database value or increments on flush</li>
     * </ul>
     * 
     * @param version the version number to set, typically not called directly
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /**
     * Indicates whether this entity is persisted (has been saved to database).
     * 
     * <p>Utility method for application logic that needs to distinguish between
     * transient (new) and persistent (saved) entities. Useful for:
     * <ul>
     *   <li>Conditional validation rules (e.g., some fields required only on update)</li>
     *   <li>UI logic (e.g., "Save" vs "Update" button labels)</li>
     *   <li>Service layer branching (e.g., different notification for create vs update)</li>
     * </ul>
     * 
     * <p><b>Implementation Note:</b> Uses version field as persistence indicator
     * because {@code @Version} fields are always non-null after initial persist.
     * Alternative indicators like {@code createdAt != null} would also work but
     * version is the canonical JPA persistence marker.
     * 
     * @return true if entity has been persisted to database, false if transient
     */
    public boolean isPersisted() {
        return version != null;
    }

    /**
     * Indicates whether this entity is transient (not yet saved to database).
     * 
     * <p>Convenience method providing more readable semantics than {@code !isPersisted()}.
     * Useful in conditional logic:
     * <pre>
     * if (entity.isTransient()) {
     *     // Apply creation-specific validation
     *     // Send "new record" notification
     * } else {
     *     // Apply update-specific validation
     *     // Send "record modified" notification
     * }
     * </pre>
     * 
     * @return true if entity is transient, false if persistent
     */
    public boolean isTransient() {
        return version == null;
    }
}
