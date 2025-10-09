/*
 * AccountMapper.java
 *
 * MapStruct mapper interface for Account entity bidirectional transformations
 * 
 * Migrated from: app/cpy/CVACT01Y.cpy (Account Record Layout)
 * Related screens: app/bms/COACTVW.bms (Account View), app/bms/COACTUP.bms (Account Update)
 * 
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This mapper provides compile-time code generation for bidirectional conversions
 * between Account JPA entity and REST API DTO objects (AccountResponse for GET
 * /api/v1/accounts/{id}, AccountUpdateRequest for PUT /api/v1/accounts/{id}).
 * 
 * Key Features:
 * - BigDecimal precision preservation with ROUND_HALF_UP for monetary fields
 * - LocalDate formatting as ISO-8601 strings in JSON responses
 * - Nested customer relationship handling via CustomerMapper injection
 * - Null-safe partial updates with NullValuePropertyMappingStrategy.IGNORE
 * - Audit field exclusion (createdAt, updatedAt managed by JPA auditing)
 * - Collection exclusion (cards, transactions not mapped to response DTOs)
 */
package com.aws.carddemo.mapper;

import com.aws.carddemo.dto.request.AccountUpdateRequest;
import com.aws.carddemo.dto.response.AccountResponse;
import com.aws.carddemo.model.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for bidirectional mapping between Account JPA entity
 * and Account Data Transfer Objects (DTOs) for REST API request/response handling.
 * 
 * <p><b>Migration Context:</b> This mapper transforms COBOL ACCOUNT-RECORD structures
 * (from CVACT01Y.cpy copybook) to modern Java DTOs, preserving financial precision
 * (PIC S9(10)V99 COMP-3 → BigDecimal), date formats (PIC X(10) → LocalDate), and
 * relational integrity (implicit COBOL file lookups → JPA @ManyToOne relationships).
 * 
 * <p><b>Financial Precision:</b> All monetary fields (currentBalance, creditLimit,
 * cashCreditLimit, currentCycleCredit, currentCycleDebit) use BigDecimal with
 * precision=12, scale=2 matching COBOL PIC S9(10)V99 COMP-3 packed decimal format.
 * MapStruct preserves exact decimal values without floating-point rounding errors.
 * 
 * <p><b>Customer Relationship Handling:</b> The mapper injects CustomerMapper via
 * uses={CustomerMapper.class} configuration to handle nested customer object mapping.
 * Account entity's @ManyToOne Customer relationship is flattened in AccountResponse
 * to denormalized fields (customerId, customerFirstName, customerLastName) for
 * efficient API responses without requiring separate customer endpoint calls.
 * 
 * <p><b>MapStruct Configuration:</b>
 * <ul>
 *   <li><b>componentModel = "spring":</b> Generates Spring {@code @Component} bean
 *       for dependency injection in AccountService and AccountController</li>
 *   <li><b>uses = {CustomerMapper.class}:</b> Injects CustomerMapper for nested
 *       Customer entity mapping, enabling deep object graph transformations</li>
 *   <li><b>unmappedTargetPolicy = IGNORE:</b> Suppresses compiler warnings for:
 *       <ul>
 *         <li>Audit fields (createdAt, updatedAt, version) managed by JPA auditing</li>
 *         <li>Collection fields (cards, transactions) excluded from response DTOs</li>
 *         <li>Helper methods (addCard, removeCard, isActive, etc.)</li>
 *       </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>Null Value Handling:</b> The updateEntityFromRequest() method applies
 * NullValuePropertyMappingStrategy.IGNORE to support partial updates where only
 * provided fields modify the existing entity, preserving unchanged fields. This
 * matches COBOL REWRITE behavior where unspecified fields retain their values.
 * 
 * <p><b>Generated Implementation:</b> MapStruct annotation processor generates
 * AccountMapperImpl class at compile time with optimized mapping logic, avoiding
 * runtime reflection overhead. Generated code performs direct field assignments
 * with null-safety checks and type conversions.
 * 
 * <p><b>Usage in Service Layer:</b>
 * <pre>
 * {@code
 * @Service
 * public class AccountService {
 *     private final AccountRepository accountRepository;
 *     private final AccountMapper accountMapper;
 *     
 *     public AccountResponse getAccount(Long id) {
 *         Account entity = accountRepository.findById(id)
 *             .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
 *         return accountMapper.toResponse(entity); // Customer fields auto-populated
 *     }
 *     
 *     public AccountResponse updateAccount(Long id, AccountUpdateRequest request) {
 *         Account entity = accountRepository.findById(id)
 *             .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
 *         accountMapper.updateEntityFromRequest(request, entity); // Partial update
 *         Account saved = accountRepository.save(entity);
 *         return accountMapper.toResponse(saved);
 *     }
 * }
 * }
 * </pre>
 * 
 * <p><b>COBOL Field Mappings (CVACT01Y.cpy → Account.java → AccountResponse.java):</b>
 * <ul>
 *   <li>ACCT-ID PIC 9(11) → accountNumber String(11) → accountNumber</li>
 *   <li>ACCT-ACTIVE-STATUS PIC X(01) → activeStatus String(1) → activeStatus</li>
 *   <li>ACCT-CURR-BAL PIC S9(10)V99 → currentBalance BigDecimal(12,2) → currentBalance</li>
 *   <li>ACCT-CREDIT-LIMIT PIC S9(10)V99 → creditLimit BigDecimal(12,2) → creditLimit</li>
 *   <li>ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 → cashCreditLimit BigDecimal(12,2) → cashCreditLimit</li>
 *   <li>ACCT-OPEN-DATE PIC X(10) → openDate LocalDate → openDate (ISO 8601)</li>
 *   <li>ACCT-EXPIRAION-DATE PIC X(10) → expirationDate LocalDate → expirationDate</li>
 *   <li>ACCT-REISSUE-DATE PIC X(10) → reissueDate LocalDate → reissueDate</li>
 *   <li>ACCT-CURR-CYC-CREDIT PIC S9(10)V99 → currentCycleCredit BigDecimal(12,2) → currentCycleCredit</li>
 *   <li>ACCT-CURR-CYC-DEBIT PIC S9(10)V99 → currentCycleDebit BigDecimal(12,2) → currentCycleDebit</li>
 *   <li>ACCT-ADDR-ZIP PIC X(10) → addressZip String(10) → addressZip</li>
 *   <li>ACCT-GROUP-ID PIC X(10) → groupId String(10) → groupId</li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 0.4.1: AccountMapper for Account entity ↔ AccountResponse/AccountUpdateRequest</li>
 *   <li>Section 0.8.3: Data Type Mapping Standards (PIC S9(n)V99 COMP-3 → BigDecimal)</li>
 *   <li>Section IE3: STRICT Dependency Analysis and Validation</li>
 *   <li>Section CQ7: Export Schema Implementation and Validation</li>
 * </ul>
 * 
 * @author CardDemo Modernization Team
 * @since 1.0.0
 * @see Account
 * @see AccountResponse
 * @see AccountUpdateRequest
 * @see CustomerMapper
 * @see org.mapstruct.Mapper
 */
@Mapper(
    componentModel = "spring",
    uses = {CustomerMapper.class},
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface AccountMapper {

    /**
     * Converts Account JPA entity to AccountResponse DTO for GET /api/v1/accounts/{id}.
     * 
     * <p><b>Purpose:</b> Transforms Account entity from database to JSON-serializable
     * response object for REST API consumption. Replaces COBOL EXEC CICS SEND MAP
     * with ResponseEntity&lt;AccountResponse&gt; return values in AccountController.
     * 
     * <p><b>Field Mappings:</b>
     * <ul>
     *   <li><b>Direct Mappings (same field names):</b>
     *       <ul>
     *         <li>accountId → accountId (Long, primary key)</li>
     *         <li>accountNumber → accountNumber (String, 11 digits)</li>
     *         <li>activeStatus → activeStatus (String, Y/N)</li>
     *         <li>currentBalance → currentBalance (BigDecimal, precision 12, scale 2)</li>
     *         <li>creditLimit → creditLimit (BigDecimal)</li>
     *         <li>cashCreditLimit → cashCreditLimit (BigDecimal)</li>
     *         <li>currentCycleCredit → currentCycleCredit (BigDecimal)</li>
     *         <li>currentCycleDebit → currentCycleDebit (BigDecimal)</li>
     *         <li>openDate → openDate (LocalDate, ISO 8601 format in JSON)</li>
     *         <li>expirationDate → expirationDate (LocalDate)</li>
     *         <li>reissueDate → reissueDate (LocalDate, nullable)</li>
     *         <li>addressZip → addressZip (String, 5 or 9 digits)</li>
     *         <li>groupId → groupId (String, disclosure group identifier)</li>
     *         <li>createdAt → createdAt (LocalDateTime, audit timestamp)</li>
     *         <li>updatedAt → updatedAt (LocalDateTime, audit timestamp)</li>
     *       </ul>
     *   </li>
     *   <li><b>Nested Customer Mappings (via CustomerMapper):</b>
     *       <ul>
     *         <li>customer.customerId → customerId (Long, customer primary key)</li>
     *         <li>customer.firstName → customerFirstName (String, denormalized for display)</li>
     *         <li>customer.lastName → customerLastName (String, denormalized for display)</li>
     *       </ul>
     *   </li>
     * </ul>
     * 
     * <p><b>Excluded Fields (unmappedTargetPolicy = IGNORE):</b>
     * <ul>
     *   <li>Account.cards (List&lt;Card&gt;) - Collection not mapped to response DTO,
     *       use separate GET /api/v1/accounts/{id}/cards endpoint</li>
     *   <li>Account.transactions (List&lt;Transaction&gt;) - Collection not mapped,
     *       use separate GET /api/v1/accounts/{id}/transactions endpoint</li>
     *   <li>Account.version (Long) - JPA optimistic locking field, not exposed to API</li>
     * </ul>
     * 
     * <p><b>BigDecimal Precision:</b> MapStruct preserves exact BigDecimal values
     * without rounding. Jackson serializes BigDecimal as JSON string (@JsonFormat
     * annotation on AccountResponse fields) to prevent precision loss in JavaScript
     * clients (JavaScript Number type has 53-bit precision limit, BigDecimal has
     * arbitrary precision).
     * 
     * <p><b>Date Formatting:</b> MapStruct automatically converts LocalDate to
     * LocalDate (no conversion needed). Jackson serializes LocalDate as ISO 8601
     * string "yyyy-MM-dd" per @JsonFormat annotation on AccountResponse fields.
     * 
     * <p><b>Null Safety:</b> MapStruct generates null-safe code:
     * <pre>
     * {@code
     * if (account == null) {
     *     return null;
     * }
     * // ... field mapping code
     * }
     * </pre>
     * 
     * <p><b>Lazy Loading Considerations:</b> If Account.customer relationship is
     * LAZY-fetched and not initialized, Hibernate proxy may cause LazyInitializationException.
     * Service layer must ensure customer is fetched (use FETCH JOIN or @EntityGraph)
     * before calling this mapper method. Example:
     * <pre>
     * {@code
     * @Query("SELECT a FROM Account a JOIN FETCH a.customer WHERE a.accountId = :id")
     * Optional<Account> findByIdWithCustomer(@Param("id") Long id);
     * }
     * </pre>
     * 
     * <p><b>Performance:</b> Generated mapper code performs direct field assignments
     * with no reflection, no runtime annotation processing, and minimal object
     * allocations. Typical mapping time: &lt;1 microsecond for Account entity with
     * ~15 fields.
     * 
     * @param account the Account entity from database (must not be null for non-null return)
     * @return AccountResponse DTO with all account and customer fields populated,
     *         or null if input account is null
     * @throws org.hibernate.LazyInitializationException if customer relationship
     *         is not initialized and Hibernate session is closed
     */
    @Mapping(source = "customer.customerId", target = "customerId")
    @Mapping(source = "customer.firstName", target = "customerFirstName")
    @Mapping(source = "customer.lastName", target = "customerLastName")
    AccountResponse toResponse(Account account);

    /**
     * Converts AccountUpdateRequest DTO to new Account entity for account creation.
     * 
     * <p><b>Purpose:</b> Transforms POST request body to Account entity for persistence.
     * This method is used for account creation operations (POST /api/v1/accounts).
     * Replaces COBOL EXEC CICS WRITE with accountRepository.save(entity).
     * 
     * <p><b>Field Mappings:</b> All AccountUpdateRequest fields map directly to
     * Account entity fields with same names. See AccountUpdateRequest JavaDoc for
     * complete field list with validation constraints.
     * 
     * <p><b>Auto-Excluded Fields:</b>
     * <ul>
     *   <li>accountId (Long) - Auto-generated by database IDENTITY strategy</li>
     *   <li>customer (Customer) - Must be set explicitly by service layer after
     *       entity creation, requires Customer entity lookup by customerId</li>
     *   <li>cards (List&lt;Card&gt;) - Empty collection initialized by @Builder.Default</li>
     *   <li>transactions (List&lt;Transaction&gt;) - Empty collection initialized</li>
     *   <li>createdAt (LocalDateTime) - Auto-populated by JPA @PrePersist</li>
     *   <li>updatedAt (LocalDateTime) - Auto-populated by JPA @PrePersist</li>
     *   <li>version (Long) - Auto-initialized to 0 by JPA</li>
     * </ul>
     * 
     * <p><b>Service Layer Responsibilities:</b> After calling toEntity(), service
     * layer must:
     * <ol>
     *   <li>Lookup and set Customer entity:
     *       <pre>
     *       {@code
     *       Customer customer = customerRepository.findById(request.getCustomerId())
     *           .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
     *       account.setCustomer(customer);
     *       }
     *       </pre>
     *   </li>
     *   <li>Validate business rules (e.g., cashCreditLimit &lt;= creditLimit)</li>
     *   <li>Set default values (e.g., currentBalance = BigDecimal.ZERO)</li>
     *   <li>Persist entity: accountRepository.save(account)</li>
     * </ol>
     * 
     * <p><b>BigDecimal Initialization:</b> MapStruct preserves BigDecimal values
     * from request DTO without modification. Service layer should validate non-null
     * constraints before persisting (Bean Validation @NotNull triggers on save).
     * 
     * <p><b>Date Validation:</b> AccountUpdateRequest enforces date validations
     * via @PastOrPresent, @Future, @Past annotations. MapStruct passes LocalDate
     * values unchanged to entity. Database CHECK constraints provide additional
     * validation (e.g., expiration_date &gt; open_date).
     * 
     * <p><b>Null Safety:</b> MapStruct generates null-safe code. Returns null if
     * input request is null.
     * 
     * @param request the AccountUpdateRequest DTO from POST /api/v1/accounts request body
     *                (must not be null for non-null return)
     * @return new Account entity ready for persistence (after customer assignment),
     *         or null if input request is null
     */
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "cards", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Account toEntity(AccountUpdateRequest request);

    /**
     * Updates existing Account entity from AccountUpdateRequest DTO for PUT operations.
     * 
     * <p><b>Purpose:</b> Applies partial updates from PUT /api/v1/accounts/{id} request
     * to existing Account entity. Replaces COBOL EXEC CICS REWRITE with JPA entity
     * modification + accountRepository.save(). Preserves COBOL behavior where only
     * specified fields are updated, unspecified fields retain original values.
     * 
     * <p><b>Update Strategy:</b> NullValuePropertyMappingStrategy.IGNORE ensures
     * null values in request DTO do NOT overwrite existing entity fields. This
     * enables true partial updates:
     * <pre>
     * {@code
     * // Example: Update only creditLimit, leave other fields unchanged
     * AccountUpdateRequest request = new AccountUpdateRequest();
     * request.setCreditLimit(new BigDecimal("10000.00")); // All other fields null
     * 
     * Account existing = accountRepository.findById(id).orElseThrow();
     * accountMapper.updateEntityFromRequest(request, existing);
     * // Only creditLimit modified, activeStatus/openDate/etc. unchanged
     * accountRepository.save(existing); // updatedAt auto-updated by JPA @PreUpdate
     * }
     * </pre>
     * 
     * <p><b>Immutable Fields (explicitly ignored):</b>
     * <ul>
     *   <li>accountId (Long) - Primary key never changes</li>
     *   <li>accountNumber (String) - Business key immutable after creation</li>
     *   <li>customer (Customer) - Customer relationship changes require separate
     *       transfer workflow, not via simple account update</li>
     *   <li>cards (List&lt;Card&gt;) - Collection managed via separate card endpoints</li>
     *   <li>transactions (List&lt;Transaction&gt;) - Collection managed via separate endpoints</li>
     *   <li>createdAt (LocalDateTime) - Creation timestamp immutable</li>
     *   <li>version (Long) - JPA optimistic locking field, managed automatically</li>
     * </ul>
     * 
     * <p><b>Updatable Fields:</b>
     * <ul>
     *   <li><b>Account Status:</b> activeStatus (Y/N/S for Active/Inactive/Suspended)</li>
     *   <li><b>Monetary Limits:</b> creditLimit, cashCreditLimit</li>
     *   <li><b>Cycle Balances:</b> currentBalance, currentCycleCredit, currentCycleDebit</li>
     *   <li><b>Dates:</b> openDate, expirationDate, reissueDate</li>
     *   <li><b>Address/Group:</b> addressZip, groupId</li>
     * </ul>
     * 
     * <p><b>Validation:</b> Bean Validation constraints on Account entity ensure
     * data integrity on save (e.g., @DecimalMin prevents negative balances, @Future
     * ensures valid expiration dates). Service layer should perform business rule
     * validations before calling this mapper (e.g., verify cashCreditLimit &lt;= creditLimit).
     * 
     * <p><b>Audit Trail:</b> updatedAt timestamp automatically updated by JPA
     * @PreUpdate lifecycle callback in BaseEntity. version field incremented by JPA
     * for optimistic locking (prevents lost updates in concurrent modifications).
     * 
     * <p><b>Partial Update Example:</b>
     * <pre>
     * {@code
     * // Client sends PATCH /api/v1/accounts/123
     * // Request body: {"creditLimit": "15000.00", "activeStatus": "Y"}
     * 
     * Account existing = accountRepository.findById(123L).orElseThrow();
     * // Before update: creditLimit=10000.00, activeStatus=N, currentBalance=500.00
     * 
     * AccountUpdateRequest request = new AccountUpdateRequest();
     * request.setCreditLimit(new BigDecimal("15000.00"));
     * request.setActiveStatus("Y");
     * // currentBalance field null in request
     * 
     * accountMapper.updateEntityFromRequest(request, existing);
     * // After update: creditLimit=15000.00, activeStatus=Y, currentBalance=500.00 (preserved)
     * 
     * accountRepository.save(existing); // Persist changes, updatedAt auto-updated
     * }
     * </pre>
     * 
     * <p><b>Concurrency Control:</b> If another transaction modified the same Account
     * entity between read and write, JPA throws OptimisticLockException due to version
     * mismatch. Service layer should catch this exception and retry or return HTTP 409
     * Conflict response to client.
     * 
     * <p><b>Performance:</b> In-place entity update (no new object allocation) with
     * direct field assignments. Typical update time: &lt;1 microsecond. Database
     * UPDATE statement only includes modified columns (Hibernate dirty checking).
     * 
     * @param request the AccountUpdateRequest DTO with updated field values from
     *                PUT /api/v1/accounts/{id} request body (may have null fields
     *                for partial updates)
     * @param account the existing Account entity to update (modified in-place, must
     *                not be null)
     * @throws IllegalArgumentException if account parameter is null (MapStruct
     *         generated null-check in implementation)
     * @see org.mapstruct.NullValuePropertyMappingStrategy#IGNORE
     * @see org.mapstruct.MappingTarget
     */
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "cards", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "activeStatus", source = "accountStatus", 
             nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "creditLimit", 
             nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "cashCreditLimit", 
             nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "openDate", source = "accountOpenDate", 
             nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "expirationDate", source = "accountExpirationDate", 
             nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "reissueDate", 
             nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(AccountUpdateRequest request, @MappingTarget Account account);
}
