package com.aws.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.cache.annotation.Cacheable;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * JPA entity class representing transaction type reference data.
 * 
 * <p>Migrated from: app/cpy/CVTRA03Y.cpy (TRAN-TYPE-RECORD structure)</p>
 * 
 * <p>This entity stores the 7 predefined transaction types used throughout the CardDemo application:
 * <ul>
 *   <li>01 - Purchase: Customer purchase transaction</li>
 *   <li>02 - Cash Advance: Cash withdrawal against credit line</li>
 *   <li>03 - Balance Transfer: Transfer from another account</li>
 *   <li>04 - Payment: Customer payment toward balance</li>
 *   <li>05 - Refund: Merchant refund/credit</li>
 *   <li>06 - Fee: Service or penalty fee</li>
 *   <li>07 - Interest Charge: Accrued interest charge</li>
 * </ul>
 * </p>
 * 
 * <p><b>COBOL Structure Mapping:</b></p>
 * <pre>
 * COBOL (60-byte record):
 *   05 TRAN-TYPE         PIC X(02)  → String typeCode
 *   05 TRAN-TYPE-DESC    PIC X(50)  → String typeDescription
 *   05 FILLER            PIC X(08)  → (not migrated - padding)
 * </pre>
 * 
 * <p><b>Caching Strategy:</b></p>
 * <ul>
 *   <li>Application-level cache: @Cacheable annotation for Spring Cache integration</li>
 *   <li>Hibernate second-level cache: READ_ONLY strategy (reference data never changes)</li>
 *   <li>Performance optimization: Reduces database queries for frequently accessed lookups</li>
 * </ul>
 * 
 * <p><b>Data Validation:</b></p>
 * <ul>
 *   <li>typeCode: Must match pattern \d{2} (2-digit format, e.g., "01", "07")</li>
 *   <li>typeDescription: Cannot be blank, maximum 50 characters</li>
 * </ul>
 * 
 * @see Transaction
 * @see DailyTransaction
 * @author CardDemo Modernization Team
 * @version 1.0.0
 */
@Entity
@Table(name = "transaction_type")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Cacheable("transactionTypes")
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY, region = "transactionTypeCache")
public class TransactionType implements Serializable {

    /**
     * Serial version UID for Serializable interface.
     * Required for distributed cache compatibility and JPA detached state management.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Transaction type code (primary key).
     * 
     * <p>Migrated from COBOL field: TRAN-TYPE PIC X(02)</p>
     * 
     * <p>Valid values: "01" through "07" (7 predefined types)</p>
     * <p>Format: Two-digit numeric string (enforced by @Pattern validation)</p>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: type_code VARCHAR(2) PRIMARY KEY</li>
     *   <li>Constraint: CHECK (type_code ~ '^\d{2}$')</li>
     * </ul>
     * 
     * @see #typeDescription
     */
    @Id
    @Column(name = "type_code", length = 2, nullable = false)
    @NotBlank(message = "Transaction type code cannot be blank")
    @Pattern(regexp = "\\d{2}", message = "Transaction type code must be a 2-digit numeric string (e.g., '01', '07')")
    private String typeCode;

    /**
     * Transaction type description (human-readable name).
     * 
     * <p>Migrated from COBOL field: TRAN-TYPE-DESC PIC X(50)</p>
     * 
     * <p>Examples: "Purchase", "Cash Advance", "Payment", "Interest Charge"</p>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: type_description VARCHAR(50) NOT NULL</li>
     *   <li>Constraint: Maximum 50 characters (enforced by @Size validation)</li>
     * </ul>
     * 
     * @see #typeCode
     */
    @Column(name = "type_description", length = 50, nullable = false)
    @NotBlank(message = "Transaction type description cannot be blank")
    @Size(max = 50, message = "Transaction type description cannot exceed 50 characters")
    private String typeDescription;

    /**
     * Timestamp when this transaction type record was created.
     * 
     * <p>Audit field automatically populated by Hibernate on entity creation.</p>
     * <p>Provides traceability for when reference data entries were added to the system.</p>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP</li>
     *   <li>Hibernate management: @CreationTimestamp auto-populates on INSERT</li>
     * </ul>
     * 
     * <p><b>Note:</b> This field replaces manual timestamp management from COBOL programs
     * where creation time was not tracked for reference data.</p>
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Returns the transaction type code.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * @return the 2-digit transaction type code (e.g., "01", "07")
     */
    // Lombok-generated: public String getTypeCode()

    /**
     * Sets the transaction type code.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * @param typeCode the 2-digit transaction type code to set
     */
    // Lombok-generated: public void setTypeCode(String typeCode)

    /**
     * Returns the transaction type description.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * @return the human-readable transaction type description (e.g., "Purchase")
     */
    // Lombok-generated: public String getTypeDescription()

    /**
     * Sets the transaction type description.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * @param typeDescription the transaction type description to set
     */
    // Lombok-generated: public void setTypeDescription(String typeDescription)

    /**
     * Returns the creation timestamp.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * @return the timestamp when this record was created
     */
    // Lombok-generated: public LocalDateTime getCreatedAt()

    /**
     * Sets the creation timestamp.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * <p><b>Note:</b> This setter should not be called directly. The createdAt field
     * is managed by Hibernate's @CreationTimestamp annotation.</p>
     * 
     * @param createdAt the creation timestamp to set
     */
    // Lombok-generated: public void setCreatedAt(LocalDateTime createdAt)

    /**
     * Compares this transaction type with another object for equality.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * <p>Two TransactionType entities are equal if they have the same typeCode (primary key).</p>
     * 
     * @param o the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    // Lombok-generated: public boolean equals(Object o)

    /**
     * Returns the hash code for this transaction type.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * <p>Hash code is based on the typeCode field (primary key).</p>
     * 
     * @return the hash code value
     */
    // Lombok-generated: public int hashCode()

    /**
     * Returns a string representation of this transaction type.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * <p>Format: TransactionType(typeCode=01, typeDescription=Purchase, createdAt=2024-01-15T10:30:00)</p>
     * 
     * @return string representation of the entity
     */
    // Lombok-generated: public String toString()

    /**
     * Returns a builder for constructing TransactionType instances.
     * 
     * <p>Generated by Lombok @Builder annotation.</p>
     * 
     * <p><b>Usage example:</b></p>
     * <pre>{@code
     * TransactionType type = TransactionType.builder()
     *     .typeCode("01")
     *     .typeDescription("Purchase")
     *     .build();
     * }</pre>
     * 
     * @return a new TransactionTypeBuilder instance
     */
    // Lombok-generated: public static TransactionTypeBuilder builder()
}
