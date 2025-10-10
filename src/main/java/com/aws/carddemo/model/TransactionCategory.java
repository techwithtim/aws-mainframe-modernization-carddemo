package com.aws.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * JPA entity class representing transaction category reference data with composite primary key.
 * 
 * <p>Migrated from: app/cpy/CVTRA04Y.cpy (TRAN-CAT-RECORD structure)</p>
 * 
 * <p>This entity represents the hierarchical type-category classification system for transactions,
 * supporting 18 predefined categories loaded via Flyway seed script. Each category belongs to a
 * specific transaction type, enabling detailed transaction classification.</p>
 * 
 * <p><b>Hierarchical Classification Examples:</b></p>
 * <ul>
 *   <li>Purchase (Type 01) → Grocery (Category 0001), Gas (Category 0002), Restaurant (Category 0003)</li>
 *   <li>Cash Advance (Type 02) → ATM (Category 0100), Bank Teller (Category 0101)</li>
 *   <li>Payment (Type 04) → Online Payment (Category 0200), Mail Payment (Category 0201)</li>
 *   <li>Fee (Type 06) → Late Fee (Category 0300), Over Limit Fee (Category 0301)</li>
 * </ul>
 * 
 * <p><b>COBOL Structure Mapping:</b></p>
 * <pre>
 * COBOL (60-byte record):
 *   01  TRAN-CAT-RECORD.
 *       05  TRAN-CAT-KEY.
 *          10  TRAN-TYPE-CD         PIC X(02)  → String transactionTypeCode
 *          10  TRAN-CAT-CD          PIC 9(04)  → String categoryCode
 *       05  TRAN-CAT-TYPE-DESC      PIC X(50)  → String categoryDescription
 *       05  FILLER                  PIC X(04)  → (not migrated - padding)
 * </pre>
 * 
 * <p><b>Composite Primary Key Pattern:</b></p>
 * <ul>
 *   <li>Uses @IdClass(TransactionCategoryId.class) for JPA composite key support</li>
 *   <li>Two @Id fields: transactionTypeCode and categoryCode</li>
 *   <li>Enables repository queries: findById(new TransactionCategoryId("01", "0001"))</li>
 *   <li>Database constraint: PRIMARY KEY (transaction_type_code, category_code)</li>
 * </ul>
 * 
 * <p><b>Foreign Key Relationship:</b></p>
 * <ul>
 *   <li>@ManyToOne to TransactionType entity with LAZY fetch strategy</li>
 *   <li>Read-only relationship: insertable=false, updatable=false on @JoinColumn</li>
 *   <li>Rationale: transactionTypeCode is part of composite PK, managed by JPA via @Id</li>
 *   <li>Index: idx_category_type on transaction_type_code for optimized foreign key lookups</li>
 * </ul>
 * 
 * <p><b>Data Type Mapping Rationale:</b></p>
 * <ul>
 *   <li>TRAN-TYPE-CD PIC X(02) → String: Alphanumeric code preserving leading zeros</li>
 *   <li>TRAN-CAT-CD PIC 9(04) → String: Numeric code stored as String to preserve leading zeros
 *       (e.g., "0001", "0100", not integer 1 or 100)</li>
 *   <li>TRAN-CAT-TYPE-DESC PIC X(50) → String: Human-readable category description</li>
 * </ul>
 * 
 * <p><b>Data Validation:</b></p>
 * <ul>
 *   <li>transactionTypeCode: Must match pattern \d{2} (2-digit format, e.g., "01", "06")</li>
 *   <li>categoryCode: Must match pattern \d{4} (4-digit format with leading zeros, e.g., "0001", "0300")</li>
 *   <li>categoryDescription: Cannot be blank, maximum 50 characters</li>
 * </ul>
 * 
 * <p><b>Usage in Transaction Processing:</b></p>
 * <ul>
 *   <li>Batch jobs (CBTRN01C.cbl → TransactionPostingService) validate category codes</li>
 *   <li>Online transactions (COTRN02C.cbl → TransactionController) allow category selection</li>
 *   <li>Reports (CBTRN03C.cbl → ReportService) group transactions by category</li>
 *   <li>Interest calculation (CBACT04C.cbl → InterestCalculationService) applies category-specific rates</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>LAZY fetch for transactionType relationship avoids unnecessary joins</li>
 *   <li>Composite index on primary key enables efficient category lookups</li>
 *   <li>Secondary index on transaction_type_code optimizes type-based category filtering</li>
 *   <li>Cacheable at repository layer for frequently accessed category lookups</li>
 * </ul>
 * 
 * @see TransactionCategoryId
 * @see TransactionType
 * @see Transaction
 * @see DailyTransaction
 * @author CardDemo Modernization Team
 * @version 1.0.0
 */
@Entity
@Table(name = "transaction_category")
@IdClass(TransactionCategoryId.class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCategory implements Serializable {

    /**
     * Serial version UID for Serializable interface.
     * Required for distributed cache compatibility (Redis session storage),
     * JPA detached state management, and Spring Batch chunk serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Transaction type code (first part of composite primary key).
     * 
     * <p>Migrated from COBOL field: TRAN-TYPE-CD PIC X(02)</p>
     * 
     * <p>Valid values: "01" through "07" (matches TransactionType.typeCode)</p>
     * <p>Format: Two-digit numeric string (enforced by @Pattern validation)</p>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: type_code VARCHAR(2) NOT NULL (FIXED: corrected from transaction_type_code)</li>
     *   <li>Primary Key: Part 1 of composite PRIMARY KEY (type_code, category_code)</li>
     *   <li>Foreign Key: REFERENCES transaction_type(type_code)</li>
     *   <li>Index: idx_category_type for foreign key lookups</li>
     * </ul>
     * 
     * <p><b>Composite Key Pattern:</b></p>
     * <ul>
     *   <li>Annotated with @Id to indicate part of composite primary key</li>
     *   <li>Must match corresponding field in TransactionCategoryId class</li>
     *   <li>Field name must be identical to TransactionCategoryId.transactionTypeCode</li>
     * </ul>
     * 
     * @see TransactionCategoryId#transactionTypeCode
     * @see TransactionType#typeCode
     */
    @Id
    @Column(name = "type_code", length = 2, nullable = false)
    @NotBlank(message = "Transaction type code cannot be blank")
    @Pattern(regexp = "\\d{2}", message = "Transaction type code must be a 2-digit numeric string (e.g., '01', '07')")
    @EqualsAndHashCode.Include
    private String transactionTypeCode;

    /**
     * Transaction category code (second part of composite primary key).
     * 
     * <p>Migrated from COBOL field: TRAN-CAT-CD PIC 9(04)</p>
     * 
     * <p>Valid range: "0001" through "0300" (18 predefined categories)</p>
     * <p>Format: Four-digit numeric string with leading zeros (e.g., "0001", "0100", "0300")</p>
     * 
     * <p><b>Leading Zero Preservation Rationale:</b></p>
     * <ul>
     *   <li>COBOL PIC 9(04) stores as numeric but displays with leading zeros</li>
     *   <li>Java String type preserves exact COBOL display format</li>
     *   <li>Prevents data loss: "0001" != 1 in business context</li>
     *   <li>Maintains sort order: "0001", "0002", "0100" (not 1, 2, 100)</li>
     * </ul>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: category_code VARCHAR(4) NOT NULL</li>
     *   <li>Primary Key: Part 2 of composite PRIMARY KEY (transaction_type_code, category_code)</li>
     *   <li>Constraint: CHECK (category_code ~ '^\d{4}$') for 4-digit format validation</li>
     * </ul>
     * 
     * <p><b>Composite Key Pattern:</b></p>
     * <ul>
     *   <li>Annotated with @Id to indicate part of composite primary key</li>
     *   <li>Must match corresponding field in TransactionCategoryId class</li>
     *   <li>Field name must be identical to TransactionCategoryId.categoryCode</li>
     * </ul>
     * 
     * @see TransactionCategoryId#categoryCode
     */
    @Id
    @Column(name = "category_code", length = 4, nullable = false)
    @NotBlank(message = "Category code cannot be blank")
    @Pattern(regexp = "\\d{4}", message = "Category code must be a 4-digit numeric string with leading zeros (e.g., '0001', '0100')")
    @EqualsAndHashCode.Include
    private String categoryCode;

    /**
     * Transaction category description (human-readable category name).
     * 
     * <p>Migrated from COBOL field: TRAN-CAT-TYPE-DESC PIC X(50)</p>
     * 
     * <p>Examples: "Grocery", "Gas Station", "Restaurant", "ATM Withdrawal", "Late Fee"</p>
     * 
     * <p><b>Database Mapping:</b></p>
     * <ul>
     *   <li>Column: category_description VARCHAR(50) NOT NULL</li>
     *   <li>Constraint: Maximum 50 characters (enforced by @Size validation)</li>
     * </ul>
     * 
     * <p><b>Usage in Application:</b></p>
     * <ul>
     *   <li>Displayed in transaction lists and detail screens</li>
     *   <li>Used for transaction categorization in reports</li>
     *   <li>Helps users understand transaction classification</li>
     *   <li>Enables category-based filtering and analytics</li>
     * </ul>
     * 
     * @see #transactionTypeCode
     * @see #categoryCode
     */
    @Column(name = "category_description", length = 50, nullable = false)
    @NotBlank(message = "Category description cannot be blank")
    @Size(max = 50, message = "Category description cannot exceed 50 characters")
    private String categoryDescription;

    /**
     * Foreign key relationship to TransactionType entity (parent type).
     * 
     * <p>Enables navigation from category to parent transaction type for hierarchical queries.</p>
     * 
     * <p><b>Relationship Configuration:</b></p>
     * <ul>
     *   <li>Fetch Strategy: LAZY (avoids unnecessary joins, loads only when accessed)</li>
     *   <li>Join Column: transaction_type_code (matches @Id field above)</li>
     *   <li>insertable=false: Prevents duplicate management of transactionTypeCode</li>
     *   <li>updatable=false: Primary key field managed by @Id annotation, not relationship</li>
     * </ul>
     * 
     * <p><b>Read-Only Relationship Rationale:</b></p>
     * <ul>
     *   <li>transactionTypeCode is part of composite primary key (@Id field)</li>
     *   <li>JPA manages primary key fields through @Id annotation</li>
     *   <li>@JoinColumn with insertable=false, updatable=false prevents duplicate management</li>
     *   <li>Relationship provides read-only navigation: category → type</li>
     * </ul>
     * 
     * <p><b>Usage Examples:</b></p>
     * <pre>{@code
     * TransactionCategory category = categoryRepository.findById(
     *     new TransactionCategoryId("01", "0001")
     * ).orElseThrow();
     * 
     * // Access parent type (triggers LAZY load if not in persistence context)
     * TransactionType type = category.getTransactionType();
     * String typeDesc = type.getTypeDescription(); // "Purchase"
     * }</pre>
     * 
     * <p><b>Performance Considerations:</b></p>
     * <ul>
     *   <li>LAZY fetch avoids N+1 queries when loading multiple categories</li>
     *   <li>Use JOIN FETCH in repository queries when type data is always needed</li>
     *   <li>Index idx_category_type optimizes foreign key constraint validation</li>
     * </ul>
     * 
     * @see TransactionType
     * @see #transactionTypeCode
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", referencedColumnName = "type_code", insertable = false, updatable = false)
    private TransactionType transactionType;

    /**
     * Returns the transaction type code (part 1 of composite primary key).
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * @return the 2-digit transaction type code (e.g., "01", "06")
     * @see #transactionTypeCode
     */
    // Lombok-generated: public String getTransactionTypeCode()

    /**
     * Sets the transaction type code (part 1 of composite primary key).
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * <p><b>Warning:</b> Modifying primary key fields after entity persistence
     * is not recommended. Use this setter only during entity construction.</p>
     * 
     * @param transactionTypeCode the 2-digit transaction type code to set
     * @see #transactionTypeCode
     */
    // Lombok-generated: public void setTransactionTypeCode(String transactionTypeCode)

    /**
     * Returns the category code (part 2 of composite primary key).
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * @return the 4-digit category code with leading zeros (e.g., "0001", "0300")
     * @see #categoryCode
     */
    // Lombok-generated: public String getCategoryCode()

    /**
     * Sets the category code (part 2 of composite primary key).
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * <p><b>Warning:</b> Modifying primary key fields after entity persistence
     * is not recommended. Use this setter only during entity construction.</p>
     * 
     * @param categoryCode the 4-digit category code to set (must include leading zeros)
     * @see #categoryCode
     */
    // Lombok-generated: public void setCategoryCode(String categoryCode)

    /**
     * Returns the category description.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * @return the human-readable category description (e.g., "Grocery", "ATM Withdrawal")
     * @see #categoryDescription
     */
    // Lombok-generated: public String getCategoryDescription()

    /**
     * Sets the category description.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * @param categoryDescription the category description to set (max 50 characters)
     * @see #categoryDescription
     */
    // Lombok-generated: public void setCategoryDescription(String categoryDescription)

    /**
     * Returns the parent TransactionType entity.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * <p><b>LAZY Loading Note:</b> If the transactionType has not been loaded yet,
     * accessing this method will trigger a database query to fetch the related
     * TransactionType entity.</p>
     * 
     * @return the parent TransactionType entity, or null if not set
     * @see #transactionType
     */
    // Lombok-generated: public TransactionType getTransactionType()

    /**
     * Sets the parent TransactionType entity.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * <p><b>Note:</b> This setter manages only the object reference, not the
     * database foreign key value. The foreign key (transaction_type_code) is
     * managed by the @Id field due to insertable=false, updatable=false configuration.</p>
     * 
     * @param transactionType the parent TransactionType entity to set
     * @see #transactionType
     */
    // Lombok-generated: public void setTransactionType(TransactionType transactionType)

    /**
     * Compares this transaction category with another object for equality.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * <p>Two TransactionCategory entities are equal if they have the same composite
     * primary key (transactionTypeCode + categoryCode). This matches the JPA specification
     * requirement that entities are equal if they have the same identifier.</p>
     * 
     * <p><b>Composite Key Equality:</b></p>
     * <ul>
     *   <li>Compares transactionTypeCode field (part 1 of composite key)</li>
     *   <li>Compares categoryCode field (part 2 of composite key)</li>
     *   <li>Other fields (categoryDescription, transactionType) are NOT considered</li>
     * </ul>
     * 
     * @param o the object to compare with
     * @return true if the objects have the same composite primary key, false otherwise
     * @see TransactionCategoryId#equals(Object)
     */
    // Lombok-generated: public boolean equals(Object o)

    /**
     * Returns the hash code for this transaction category.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * <p>Hash code is computed based on the composite primary key fields
     * (transactionTypeCode + categoryCode), ensuring consistency with equals()
     * and proper behavior in hash-based collections (HashMap, HashSet).</p>
     * 
     * <p><b>Hash Code Contract:</b></p>
     * <ul>
     *   <li>If equals() returns true, hashCode() must return the same value</li>
     *   <li>Based on immutable composite key fields for stability</li>
     *   <li>Consistent with TransactionCategoryId.hashCode() for JPA compatibility</li>
     * </ul>
     * 
     * @return the hash code value based on composite primary key
     * @see TransactionCategoryId#hashCode()
     */
    // Lombok-generated: public int hashCode()

    /**
     * Returns a string representation of this transaction category.
     * 
     * <p>Generated by Lombok @Data annotation.</p>
     * 
     * <p>Format example:</p>
     * <pre>
     * TransactionCategory(transactionTypeCode=01, categoryCode=0001, 
     *                     categoryDescription=Grocery, transactionType=TransactionType(...))
     * </pre>
     * 
     * <p><b>Usage:</b></p>
     * <ul>
     *   <li>Debugging: Inspect entity state in logs</li>
     *   <li>Logging: Record category details in audit trails</li>
     *   <li>Testing: Assert entity field values in unit tests</li>
     * </ul>
     * 
     * <p><b>Note:</b> The transactionType field will display as the full TransactionType
     * entity representation if loaded, or as a proxy object if LAZY-loaded but not accessed.</p>
     * 
     * @return string representation of the entity with all field values
     */
    // Lombok-generated: public String toString()

    /**
     * Returns a builder for constructing TransactionCategory instances.
     * 
     * <p>Generated by Lombok @Builder annotation.</p>
     * 
     * <p><b>Usage example:</b></p>
     * <pre>{@code
     * TransactionCategory category = TransactionCategory.builder()
     *     .transactionTypeCode("01")
     *     .categoryCode("0001")
     *     .categoryDescription("Grocery")
     *     .build();
     * }</pre>
     * 
     * <p><b>Builder Pattern Benefits:</b></p>
     * <ul>
     *   <li>Fluent, readable API for entity construction</li>
     *   <li>Optional field specification (omitted fields default to null)</li>
     *   <li>Immutable construction pattern (all fields set before object creation)</li>
     *   <li>Type-safe field assignment with compile-time validation</li>
     * </ul>
     * 
     * @return a new TransactionCategoryBuilder instance
     */
    // Lombok-generated: public static TransactionCategoryBuilder builder()
}
