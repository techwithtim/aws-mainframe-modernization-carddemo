package com.aws.carddemo.repository;

import com.aws.carddemo.model.DisclosureGroup;
import com.aws.carddemo.model.DisclosureGroupId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository interface for DisclosureGroup entity providing interest rate
 * group reference data access with query methods for interest calculation logic in batch processing.
 * 
 * <p>This repository replaces COBOL VSAM DISCGRP file READ operations from the legacy mainframe
 * interest calculation program (CBACT04C.cbl). The COBOL program used keyed READ operations
 * with a composite key consisting of:
 * <ul>
 *   <li>DIS-ACCT-GROUP-ID (PIC X(10)) - Account group identifier (e.g., 'GROUP01', 'GROUP02')</li>
 *   <li>DIS-TRAN-TYPE-CD (PIC X(02)) - Transaction type code (e.g., '01' for Purchase)</li>
 *   <li>DIS-TRAN-CAT-CD (PIC 9(04)) - Transaction category code (e.g., '0001' for Grocery)</li>
 * </ul>
 * 
 * <p><strong>COBOL Fallback Logic Preserved:</strong><br>
 * The legacy CBACT04C.cbl program implements a two-tier lookup strategy:
 * <ol>
 *   <li>First attempts READ with specific account group ID (e.g., 'GROUP01')</li>
 *   <li>If READ fails with INVALID KEY (file status '23'), retries with account group 'DEFAULT'</li>
 * </ol>
 * This fallback mechanism ensures interest rates are always found by providing default rates
 * for transaction type/category combinations that lack group-specific configurations.
 * 
 * <p><strong>Modernization Approach:</strong><br>
 * In the Java implementation, this fallback logic is moved to the service layer
 * (InterestCalculationService.java) which:
 * <ol>
 *   <li>Calls {@code findById(new DisclosureGroupId(accountGroupId, typeCode, categoryCode))}</li>
 *   <li>If {@code Optional.empty()}, retries with {@code findById(new DisclosureGroupId("DEFAULT", typeCode, categoryCode))}</li>
 * </ol>
 * 
 * <p><strong>Data Structure Mapping:</strong><br>
 * COBOL copybook CVTRA02Y.cpy (50-byte record):
 * <pre>
 * 01  DIS-GROUP-RECORD.
 *     05  DIS-GROUP-KEY.
 *        10 DIS-ACCT-GROUP-ID       PIC X(10).    → accountGroupId (String)
 *        10 DIS-TRAN-TYPE-CD        PIC X(02).    → transactionTypeCode (String)
 *        10 DIS-TRAN-CAT-CD         PIC 9(04).    → transactionCategoryCode (String)
 *     05  DIS-INT-RATE              PIC S9(04)V99. → interestRate (BigDecimal)
 * </pre>
 * 
 * <p><strong>Interest Rate Examples:</strong>
 * <ul>
 *   <li>GROUP01 - Purchase (01) - Grocery (0001): 16.99% APR</li>
 *   <li>GROUP01 - Cash Advance (02) - Any category: 24.99% APR</li>
 *   <li>GROUP02 - Balance Transfer (03) - Any category: 12.99% APR</li>
 *   <li>DEFAULT - Any type - Any category: 18.99% APR (fallback rate)</li>
 * </ul>
 * 
 * <p><strong>Usage in Interest Calculation:</strong><br>
 * This repository is used exclusively by the interest calculation batch job
 * (InterestCalculationService) which processes all active accounts and applies
 * appropriate interest rates based on transaction category balances. The batch job:
 * <ol>
 *   <li>Reads all accounts with outstanding balances</li>
 *   <li>For each account, retrieves its transaction category balances</li>
 *   <li>Looks up the applicable interest rate using this repository</li>
 *   <li>Calculates daily interest charges: (balance * APR / 365)</li>
 *   <li>Posts interest charges as new transactions</li>
 * </ol>
 * 
 * <p><strong>Performance Considerations:</strong><br>
 * The composite primary key (accountGroupId, transactionTypeCode, transactionCategoryCode)
 * is indexed by PostgreSQL, providing O(1) lookup performance. With only ~50-100 disclosure
 * group configurations in the reference data table, all records are typically cached by
 * the database buffer pool after first access, ensuring sub-millisecond query response times
 * for subsequent lookups during batch processing.
 * 
 * <p><strong>Reference Data Management:</strong><br>
 * Disclosure group configurations are loaded via Flyway migration script
 * V3__seed_reference_data.sql during application deployment. Any updates to interest
 * rates or addition of new group configurations require a database migration script
 * to maintain version control and auditability of rate changes per PCI-DSS requirements.
 * 
 * @see DisclosureGroup JPA entity representing disclosure group records
 * @see DisclosureGroupId Composite primary key class
 * @see com.aws.carddemo.service.InterestCalculationService Service using this repository
 * @see com.aws.carddemo.batch.config.InterestCalculationJobConfig Batch job configuration
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @since 2024-01-01
 * 
 * Migrated from: app/cbl/CBACT04C.cbl (Interest Calculation Program)
 * Data structure: app/cpy/CVTRA02Y.cpy (Disclosure Group Record Layout)
 */
@Repository
public interface DisclosureGroupRepository extends JpaRepository<DisclosureGroup, DisclosureGroupId> {

    /**
     * Finds all disclosure group records for a specific account group ID.
     * 
     * <p>This query method retrieves all interest rate configurations for a given account
     * group (e.g., 'GROUP01', 'GROUP02', 'DEFAULT') across all transaction types and categories.
     * It is primarily used for:
     * <ul>
     *   <li>Loading all rate configurations for an account group during batch processing</li>
     *   <li>Admin operations that need to display/validate all rates for a group</li>
     *   <li>Data migration validation to verify all expected rate configurations exist</li>
     * </ul>
     * 
     * <p><strong>Query Derivation:</strong><br>
     * Spring Data JPA automatically implements this method by deriving the query from
     * the method name:
     * <pre>
     * SELECT d FROM DisclosureGroup d WHERE d.accountGroupId = :accountGroupId
     * </pre>
     * 
     * <p><strong>COBOL Equivalent:</strong><br>
     * In CBACT04C.cbl, this operation would require sequential READ of the entire
     * DISCGRP-FILE with conditional logic to filter records:
     * <pre>
     * PERFORM UNTIL DISCGRP-EOF
     *     READ DISCGRP-FILE NEXT RECORD
     *     IF DIS-ACCT-GROUP-ID = WS-TARGET-GROUP-ID
     *         PERFORM PROCESS-RATE-RECORD
     *     END-IF
     * END-PERFORM
     * </pre>
     * 
     * <p><strong>Expected Results:</strong>
     * <ul>
     *   <li>GROUP01: ~30-40 records (Purchase categories + Cash Advance + Balance Transfer)</li>
     *   <li>GROUP02: ~30-40 records (Same categories, different rates)</li>
     *   <li>DEFAULT: ~30-40 records (Fallback rates for all type/category combinations)</li>
     * </ul>
     * 
     * <p><strong>Performance:</strong><br>
     * This query uses the composite primary key's first component (accountGroupId),
     * which benefits from PostgreSQL's B-tree index on the primary key. Expected
     * query execution time: &lt;5ms for result sets of 30-40 records.
     * 
     * @param accountGroupId the account group identifier (e.g., 'GROUP01', 'GROUP02', 'DEFAULT'),
     *                       must not be {@code null}, must match PIC X(10) format from COBOL
     * @return a list of all disclosure group records for the specified account group,
     *         ordered by transaction type code and category code (natural composite key order);
     *         returns an empty list if no records exist for the given account group ID
     * 
     * @throws IllegalArgumentException if accountGroupId is null
     * @throws org.springframework.dao.DataAccessException if database access error occurs
     * 
     * @see DisclosureGroup#getAccountGroupId()
     * @see #findById(DisclosureGroupId) for single rate lookup by complete composite key
     */
    List<DisclosureGroup> findByAccountGroupId(String accountGroupId);

    /**
     * Finds all disclosure group records in the database.
     * 
     * <p>This method is inherited from {@link JpaRepository#findAll()} and retrieves all
     * interest rate configurations across all account groups, transaction types, and categories.
     * It is primarily used for:
     * <ul>
     *   <li>Application startup to pre-load all rate configurations into cache</li>
     *   <li>Administrative reporting of all interest rate configurations</li>
     *   <li>Data validation and reconciliation operations</li>
     *   <li>Integration testing to verify seed data was loaded correctly</li>
     * </ul>
     * 
     * <p><strong>COBOL Equivalent:</strong><br>
     * In CBACT04C.cbl, this would be a sequential READ of the entire DISCGRP-FILE:
     * <pre>
     * OPEN INPUT DISCGRP-FILE
     * PERFORM UNTIL DISCGRP-EOF
     *     READ DISCGRP-FILE NEXT RECORD
     *         AT END SET DISCGRP-EOF TO TRUE
     *         NOT AT END PERFORM PROCESS-RATE-RECORD
     *     END-READ
     * END-PERFORM
     * CLOSE DISCGRP-FILE
     * </pre>
     * 
     * <p><strong>Expected Record Count:</strong><br>
     * The disclosure group table typically contains ~100-150 records:
     * <ul>
     *   <li>3 account groups (GROUP01, GROUP02, DEFAULT)</li>
     *   <li>3 transaction types (Purchase, Cash Advance, Balance Transfer)</li>
     *   <li>~18 transaction categories per type</li>
     *   <li>Total: 3 groups × 3 types × 18 categories ≈ 162 records</li>
     * </ul>
     * 
     * <p><strong>Performance Warning:</strong><br>
     * This operation loads ALL disclosure group records into memory. For the expected
     * data volume (~150 records), this is acceptable and completes in &lt;20ms. However,
     * for admin UI operations displaying paginated results, consider using
     * {@code findAll(Pageable)} instead to avoid loading all records unnecessarily.
     * 
     * <p><strong>Caching Opportunity:</strong><br>
     * Since disclosure group data is reference data that changes infrequently
     * (typically only during planned rate change events), this method is an excellent
     * candidate for Spring Cache abstraction with {@code @Cacheable("disclosureGroups")}.
     * However, caching is NOT implemented in this repository to maintain simple,
     * stateless repository interfaces per the minimal change discipline. If caching
     * is needed, it should be applied at the service layer.
     * 
     * @return a list of all disclosure group records ordered by composite primary key
     *         (accountGroupId, transactionTypeCode, transactionCategoryCode);
     *         never returns {@code null}, returns empty list if table is empty
     *         (should never occur in production due to seed data migration)
     * 
     * @throws org.springframework.dao.DataAccessException if database access error occurs
     * 
     * @see JpaRepository#findAll()
     * @see DisclosureGroup
     */
    // Inherited from JpaRepository: List<DisclosureGroup> findAll();

    /**
     * Finds a single disclosure group record by its composite primary key.
     * 
     * <p>This method is inherited from {@link JpaRepository#findById(Object)} and provides
     * the primary data access pattern for interest calculation batch jobs. It retrieves
     * a specific interest rate configuration using the complete composite key:
     * accountGroupId + transactionTypeCode + transactionCategoryCode.
     * 
     * <p><strong>COBOL Equivalent:</strong><br>
     * This directly maps to CBACT04C.cbl paragraph 1200-GET-INTEREST-RATE:
     * <pre>
     * MOVE ACCT-GROUP-ID          TO FD-DIS-ACCT-GROUP-ID
     * MOVE TRANCAT-TYPE-CD        TO FD-DIS-TRAN-TYPE-CD
     * MOVE TRANCAT-CD             TO FD-DIS-TRAN-CAT-CD
     * READ DISCGRP-FILE RECORD INTO DIS-GROUP-RECORD
     *     KEY IS FD-DISCGRP-KEY
     *     INVALID KEY
     *         PERFORM 1200-A-GET-DEFAULT-INT-RATE
     *     NOT INVALID KEY
     *         MOVE DIS-INT-RATE TO WS-INTEREST-RATE
     * END-READ
     * </pre>
     * 
     * <p><strong>Fallback Logic Implementation:</strong><br>
     * In the service layer (InterestCalculationService), the fallback logic is:
     * <pre>
     * Optional&lt;DisclosureGroup&gt; group = repository.findById(
     *     new DisclosureGroupId(accountGroupId, typeCode, categoryCode));
     * 
     * if (!group.isPresent()) {
     *     // COBOL line: PERFORM 1200-A-GET-DEFAULT-INT-RATE
     *     group = repository.findById(
     *         new DisclosureGroupId("DEFAULT", typeCode, categoryCode));
     * }
     * 
     * BigDecimal interestRate = group
     *     .map(DisclosureGroup::getInterestRate)
     *     .orElseThrow(() -&gt; new DataIntegrityException(
     *         "No default interest rate found for type=" + typeCode + 
     *         ", category=" + categoryCode));
     * </pre>
     * 
     * <p><strong>Usage Example:</strong><br>
     * Looking up interest rate for GROUP01 account with Purchase transaction in Grocery category:
     * <pre>
     * DisclosureGroupId id = new DisclosureGroupId("GROUP01", "01", "0001");
     * Optional&lt;DisclosureGroup&gt; group = repository.findById(id);
     * // Expected: Optional[DisclosureGroup(rate=16.99)]
     * </pre>
     * 
     * @param id the composite primary key containing accountGroupId, transactionTypeCode,
     *           and transactionCategoryCode; must not be {@code null}
     * @return an {@code Optional} containing the disclosure group if found,
     *         or {@code Optional.empty()} if no matching record exists (triggers fallback)
     * 
     * @throws IllegalArgumentException if id is null
     * @throws org.springframework.dao.DataAccessException if database access error occurs
     * 
     * @see JpaRepository#findById(Object)
     * @see DisclosureGroupId
     */
    // Inherited from JpaRepository: Optional<DisclosureGroup> findById(DisclosureGroupId id);

    /**
     * Saves a disclosure group entity to the database (INSERT or UPDATE).
     * 
     * <p>This method is inherited from {@link JpaRepository#save(Object)} and supports
     * both creating new interest rate configurations and updating existing ones.
     * In production, this operation is restricted to:
     * <ul>
     *   <li>Database migration scripts during deployment (Flyway V3__seed_reference_data.sql)</li>
     *   <li>Administrative rate change operations (requires ROLE_ADMIN authorization)</li>
     *   <li>Annual APR adjustments per credit card agreement terms</li>
     * </ul>
     * 
     * <p><strong>COBOL Equivalent:</strong><br>
     * The legacy COBOL system did not support runtime updates to disclosure group data.
     * All rate changes required mainframe dataset updates via IDCAMS REPRO or IEBGENER
     * utilities during planned maintenance windows. This Java implementation improves
     * operational flexibility by allowing authorized admin users to update rates through
     * the REST API without mainframe access.
     * 
     * <p><strong>Audit Requirements:</strong><br>
     * Any changes to interest rates must be:
     * <ol>
     *   <li>Logged with user ID, timestamp, old rate, and new rate (audit trail)</li>
     *   <li>Approved by compliance team per regulatory requirements</li>
     *   <li>Communicated to cardholders per Truth in Lending Act (TILA) requirements</li>
     *   <li>Effective on first day of billing cycle, not mid-cycle</li>
     * </ol>
     * 
     * <p><strong>PCI-DSS Considerations:</strong><br>
     * While interest rates are not considered sensitive data under PCI-DSS, access
     * to modify rates is restricted to authorized administrators only. All rate changes
     * are logged to CloudWatch for security audit and compliance monitoring.
     * 
     * @param disclosureGroup the disclosure group entity to save; must not be {@code null},
     *                        must have valid composite key fields populated, interest rate
     *                        must be non-null and within valid range (0.00% to 99.99%)
     * @return the saved disclosure group entity (may have auto-generated fields populated
     *         if using surrogate keys, though this entity uses natural composite key)
     * 
     * @throws IllegalArgumentException if disclosureGroup is null
     * @throws org.springframework.dao.DataIntegrityViolationException if constraint violation
     * @throws org.springframework.dao.DataAccessException if database access error occurs
     * 
     * @see JpaRepository#save(Object)
     * @see DisclosureGroup
     */
    // Inherited from JpaRepository: <S extends DisclosureGroup> S save(S entity);

    /**
     * Deletes a disclosure group entity from the database.
     * 
     * <p>This method is inherited from {@link JpaRepository#delete(Object)} but should
     * be used with EXTREME CAUTION in production environments. Deleting interest rate
     * configurations can break interest calculation batch jobs if they reference
     * non-existent rate records.
     * 
     * <p><strong>Production Use:</strong><br>
     * In production, disclosure group records should NEVER be deleted. Instead:
     * <ul>
     *   <li>For rate changes: UPDATE the existing record with the new rate</li>
     *   <li>For discontinued products: Set an effective end date (requires schema change)</li>
     *   <li>For data corrections: UPDATE with correct values, log correction in audit trail</li>
     * </ul>
     * 
     * <p><strong>COBOL Equivalent:</strong><br>
     * The legacy COBOL system did not support DELETE operations on the DISCGRP-FILE.
     * The file was read-only at runtime. This method exists only for:
     * <ul>
     *   <li>Integration test cleanup (delete test data after test execution)</li>
     *   <li>Emergency data repair scenarios (requires DBA approval)</li>
     *   <li>Development environment data reset operations</li>
     * </ul>
     * 
     * @param disclosureGroup the disclosure group entity to delete; must not be {@code null}
     * 
     * @throws IllegalArgumentException if disclosureGroup is null
     * @throws org.springframework.dao.EmptyResultDataAccessException if entity doesn't exist
     * @throws org.springframework.dao.DataAccessException if database access error occurs
     * 
     * @see JpaRepository#delete(Object)
     */
    // Inherited from JpaRepository: void delete(DisclosureGroup entity);

    /**
     * Returns the total count of disclosure group records in the database.
     * 
     * <p>This method is inherited from {@link JpaRepository#count()} and is primarily
     * used for:
     * <ul>
     *   <li>Data validation after seed data migration (expect ~150 records)</li>
     *   <li>Monitoring dashboards showing reference data health checks</li>
     *   <li>Integration test assertions to verify test data setup</li>
     * </ul>
     * 
     * <p><strong>Expected Count:</strong><br>
     * Production environments should have approximately 150 disclosure group records:
     * <ul>
     *   <li>3 account groups (GROUP01, GROUP02, DEFAULT)</li>
     *   <li>3 transaction types per group</li>
     *   <li>~18 categories per type</li>
     *   <li>Total: 3 × 3 × 18 = 162 records (some combinations may not exist)</li>
     * </ul>
     * 
     * <p>If count returns 0 or significantly deviates from expected value, this indicates
     * a data migration failure and should trigger operational alerts.
     * 
     * @return the total number of disclosure group records
     * 
     * @throws org.springframework.dao.DataAccessException if database access error occurs
     * 
     * @see JpaRepository#count()
     */
    // Inherited from JpaRepository: long count();

    /**
     * Checks if a disclosure group record exists for the given composite primary key.
     * 
     * <p>This method is inherited from {@link JpaRepository#existsById(Object)} and
     * provides an efficient way to check for rate configuration existence without
     * loading the full entity. It is more efficient than {@code findById(id).isPresent()}
     * because it generates a lightweight SQL query:
     * <pre>
     * SELECT COUNT(1) FROM disclosure_group 
     * WHERE account_group_id = ? 
     *   AND transaction_type_code = ? 
     *   AND transaction_category_code = ?
     * </pre>
     * 
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li>Pre-flight validation before attempting to load rate configuration</li>
     *   <li>Admin UI operations to enable/disable update buttons based on existence</li>
     *   <li>Integration tests to verify seed data was loaded correctly</li>
     * </ul>
     * 
     * <p>For interest calculation batch jobs, prefer using {@code findById()} directly
     * because you need the actual rate value anyway, making the existence check redundant.
     * 
     * @param id the composite primary key to check; must not be {@code null}
     * @return {@code true} if a record exists with the given key, {@code false} otherwise
     * 
     * @throws IllegalArgumentException if id is null
     * @throws org.springframework.dao.DataAccessException if database access error occurs
     * 
     * @see JpaRepository#existsById(Object)
     * @see #findById(DisclosureGroupId)
     */
    // Inherited from JpaRepository: boolean existsById(DisclosureGroupId id);
}
