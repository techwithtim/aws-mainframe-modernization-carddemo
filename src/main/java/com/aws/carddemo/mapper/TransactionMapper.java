/*
 * TransactionMapper.java
 * 
 * MapStruct interface for bidirectional mapping between Transaction JPA entity
 * and Transaction DTO objects (TransactionResponse/TransactionRequest).
 * 
 * Migrated from: app/cpy/CVTRA05Y.cpy (TRAN-RECORD structure)
 * 
 * This mapper provides compile-time generated, type-safe conversion code between
 * Transaction entity and REST API DTOs, ensuring PCI-DSS compliant card number
 * masking, BigDecimal precision preservation for financial amounts, and proper
 * LocalDateTime timestamp handling.
 * 
 * Key Features:
 * - BigDecimal amount formatting with ROUND_HALF_UP (PIC S9(09)V99 COMP-3 → BigDecimal)
 * - LocalDateTime timestamp conversions (originalTimestamp, processingTimestamp to ISO-8601)
 * - PCI-DSS compliant card number masking via @AfterMapping hook calling getCardNumberMasked()
 * - Transaction type/category enum mappings (01-07 type codes, 4-char category codes)
 * - Null value strategies for optional merchant fields (merchantId, merchantName, merchantCity, merchantZip)
 * 
 * Technical Specification References:
 * - Section 0.4.1: File Transformation - CVTRA05Y.cpy → Transaction.java mapping
 * - Section 0.8.1: Critical Directive #3 - PCI-DSS card masking requirement
 * - Section 0.8.3: Data Type Mapping - COBOL PIC S9(09)V99 → BigDecimal precision
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.carddemo.mapper;

import com.aws.carddemo.dto.request.TransactionRequest;
import com.aws.carddemo.dto.response.TransactionResponse;
import com.aws.carddemo.model.Transaction;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDateTime;

/**
 * MapStruct mapper interface for Transaction entity ↔ DTO conversions.
 * 
 * <p><b>MapStruct Configuration:</b>
 * <ul>
 *   <li><b>componentModel = "spring":</b> Generates Spring-managed bean injectable via @Autowired</li>
 *   <li><b>Compile-Time Generation:</b> Mapper implementation generated during Maven compilation</li>
 *   <li><b>Type Safety:</b> Compile-time validation of field mappings ensures correctness</li>
 *   <li><b>Performance:</b> No reflection overhead - direct method calls in generated code</li>
 * </ul>
 * 
 * <p><b>Generated Bean Name:</b> {@code transactionMapperImpl} (auto-discovered by Spring)
 * 
 * <p><b>Usage Example (Service Layer):</b>
 * <pre>
 * &#64;Service
 * public class TransactionService {
 *     &#64;Autowired
 *     private TransactionMapper transactionMapper;
 *     
 *     public TransactionResponse getTransactionById(Long id) {
 *         Transaction transaction = transactionRepository.findById(id)
 *             .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
 *         return transactionMapper.toResponse(transaction);
 *     }
 *     
 *     public TransactionResponse postTransaction(TransactionRequest request) {
 *         Transaction transaction = transactionMapper.toEntity(request);
 *         // Service layer enrichment: set account, transactionNumber, processingTimestamp
 *         transaction.setAccount(accountRepository.findById(request.getAccountId()).orElseThrow());
 *         transaction.setTransactionNumber(generateTransactionNumber());
 *         transaction.setProcessingTimestamp(LocalDateTime.now());
 *         
 *         Transaction saved = transactionRepository.save(transaction);
 *         return transactionMapper.toResponse(saved);
 *     }
 * }
 * </pre>
 * 
 * <p><b>PCI-DSS Compliance:</b>
 * The {@link #afterMappingToResponse} hook ensures cardNumberMasked field is properly populated
 * with masked format (************1234) from {@link Transaction#getCardNumberMasked()}, preventing
 * full card number exposure in JSON API responses per PCI-DSS requirement 3.3.
 * 
 * <p><b>COBOL Field Mappings (CVTRA05Y.cpy → Java):</b>
 * <pre>
 * COBOL Field (PIC)                Java Entity Field             Java DTO Field
 * ========================         ========================      ==========================
 * TRAN-ID (X(16))                  transactionNumber             transactionNumber
 * TRAN-TYPE-CD (X(02))             transactionTypeCode           transactionTypeCode
 * TRAN-CAT-CD (9(04))              transactionCategoryCode       transactionCategoryCode
 * TRAN-SOURCE (X(10))              transactionSource             transactionSource
 * TRAN-DESC (X(100))               description                   description
 * TRAN-AMT (S9(09)V99)             amount (BigDecimal)           amount (BigDecimal)
 * TRAN-MERCHANT-ID (9(09))         merchantId                    merchantId
 * TRAN-MERCHANT-NAME (X(50))       merchantName                  merchantName
 * TRAN-MERCHANT-CITY (X(50))       merchantCity                  merchantCity
 * TRAN-MERCHANT-ZIP (X(10))        merchantZip                   merchantZip
 * TRAN-CARD-NUM (X(16))            cardNumber (full)             cardNumberMasked (masked)
 * TRAN-ORIG-TS (X(26))             originalTimestamp             originalTimestamp (ISO-8601)
 * TRAN-PROC-TS (X(26))             processingTimestamp           processingTimestamp (ISO-8601)
 * </pre>
 * 
 * @see Transaction for entity structure and field validations
 * @see TransactionResponse for REST API response DTO structure
 * @see TransactionRequest for REST API request DTO structure
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring")
public interface TransactionMapper {

    /**
     * Converts Transaction JPA entity to TransactionResponse DTO for JSON serialization.
     * 
     * <p><b>Use Case:</b> GET /api/v1/transactions/{id} endpoint response body generation.
     * 
     * <p><b>Field Mappings:</b>
     * <ul>
     *   <li><b>transactionId:</b> Direct mapping from entity primary key</li>
     *   <li><b>transactionNumber:</b> Business key (TRAN-ID from COBOL)</li>
     *   <li><b>transactionTypeCode:</b> Direct mapping (2-char code)</li>
     *   <li><b>transactionCategoryCode:</b> Direct mapping (4-char code)</li>
     *   <li><b>transactionSource:</b> Direct mapping (channel identifier)</li>
     *   <li><b>description:</b> Direct mapping (transaction narrative)</li>
     *   <li><b>amount:</b> BigDecimal preserved without conversion (NUMERIC 11,2 precision)</li>
     *   <li><b>merchantId:</b> Direct mapping (9-digit merchant code, nullable)</li>
     *   <li><b>merchantName:</b> Direct mapping (merchant business name, nullable)</li>
     *   <li><b>merchantCity:</b> Direct mapping (merchant location, nullable)</li>
     *   <li><b>merchantZip:</b> Direct mapping (US ZIP code, nullable)</li>
     *   <li><b>cardNumberMasked:</b> Populated in {@link #afterMappingToResponse} hook (PCI-DSS)</li>
     *   <li><b>originalTimestamp:</b> Direct mapping (merchant auth time, ISO-8601)</li>
     *   <li><b>processingTimestamp:</b> Direct mapping (system posting time, ISO-8601)</li>
     *   <li><b>accountId:</b> Nested mapping from {@code transaction.account.accountId}</li>
     * </ul>
     * 
     * <p><b>Null Handling:</b> Null merchant fields (merchantId, merchantName, merchantCity, 
     * merchantZip) are preserved as null in response DTO and excluded from JSON per 
     * {@code @JsonInclude(NON_NULL)} annotation on TransactionResponse class.
     * 
     * <p><b>BigDecimal Precision:</b> Financial amount field uses BigDecimal to preserve exact
     * decimal precision without floating-point errors. COBOL PIC S9(09)V99 COMP-3 maps directly
     * to Java BigDecimal with NUMERIC(11,2) database precision (9 integer + 2 fraction digits).
     * No rounding or conversion is performed by MapStruct - value is passed through unchanged.
     * 
     * <p><b>Timestamp Formats:</b> LocalDateTime fields (originalTimestamp, processingTimestamp)
     * are serialized to ISO-8601 format (yyyy-MM-dd'T'HH:mm:ss) by Jackson's {@code @JsonFormat}
     * annotation on TransactionResponse fields. No timezone conversion is performed - timestamps
     * are stored and transmitted in server local time.
     * 
     * <p><b>PCI-DSS Compliance:</b> Full card number is NEVER included in response. The
     * {@link #afterMappingToResponse} hook calls {@link Transaction#getCardNumberMasked()}
     * to populate {@code cardNumberMasked} field with safe display format (************1234).
     * 
     * <p><b>Performance:</b> MapStruct generates plain Java code with direct field access
     * (via getters/setters). No reflection overhead. For high-volume transaction list queries,
     * consider database-level pagination and projection queries to minimize entity loading.
     * 
     * <p><b>Validation:</b> No validation is performed during mapping - entity is assumed to
     * be valid (already persisted with JPA validation). Response DTO includes validation
     * annotations but they are for documentation only (responses are not validated).
     * 
     * <p><b>Usage Example:</b>
     * <pre>
     * Transaction transaction = transactionRepository.findById(1000000000000001L).orElseThrow();
     * TransactionResponse response = transactionMapper.toResponse(transaction);
     * 
     * // Response DTO ready for JSON serialization:
     * // {
     * //   "transaction_id": 1000000000000001,
     * //   "transaction_number": "1000000000000001",
     * //   "transaction_type_code": "DB",
     * //   "transaction_category_code": "5010",
     * //   "amount": 125.50,
     * //   "card_number_masked": "************4321",
     * //   "original_timestamp": "2024-01-15T10:30:00",
     * //   "processing_timestamp": "2024-01-15T23:00:00",
     * //   "account_id": 1
     * // }
     * </pre>
     * 
     * @param transaction the Transaction entity to convert, must not be null
     * @return TransactionResponse DTO with PCI-DSS compliant masked card number, never null
     * @throws NullPointerException if transaction parameter is null (MapStruct default behavior)
     * @see #afterMappingToResponse for post-processing hook that sets cardNumberMasked
     */
    @Mapping(target = "accountId", source = "account.accountId")
    @Mapping(target = "cardNumberMasked", expression = "java(transaction.getCardNumberMasked())")
    @Mapping(target = "transactionTypeDescription", ignore = true)  // Set by service layer if needed
    @Mapping(target = "transactionCategoryDescription", ignore = true)  // Set by service layer if needed
    TransactionResponse toResponse(Transaction transaction);

    /**
     * Converts TransactionRequest DTO to Transaction entity for persistence.
     * 
     * <p><b>Use Case:</b> POST /api/v1/transactions request body deserialization and entity
     * creation for transaction posting operations (replaces CBTRN01C.cbl batch logic).
     * 
     * <p><b>Field Mappings:</b>
     * <ul>
     *   <li><b>transactionTypeCode:</b> Direct mapping from request (validated 2-char code)</li>
     *   <li><b>transactionCategoryCode:</b> Direct mapping from request (validated 4-char code)</li>
     *   <li><b>transactionSource:</b> Direct mapping (defaults to "MANUAL" if null in request)</li>
     *   <li><b>description:</b> Mapped from {@code transactionDescription} request field</li>
     *   <li><b>amount:</b> Mapped from {@code transactionAmount} (BigDecimal preserved)</li>
     *   <li><b>merchantId:</b> Direct mapping (nullable for non-merchant transactions)</li>
     *   <li><b>merchantName:</b> Direct mapping (nullable)</li>
     *   <li><b>merchantCity:</b> Direct mapping (nullable)</li>
     *   <li><b>merchantZip:</b> Direct mapping (nullable, validated format XXXXX or XXXXX-XXXX)</li>
     *   <li><b>cardNumber:</b> Direct mapping (full 16-digit card number from request)</li>
     *   <li><b>originalTimestamp:</b> Combined from {@code transactionDate + transactionTime}</li>
     *   <li><b>account:</b> <b>NOT MAPPED</b> - must be set by service layer from accountId/cardNumber lookup</li>
     *   <li><b>transactionNumber:</b> <b>NOT MAPPED</b> - must be generated by service layer</li>
     *   <li><b>processingTimestamp:</b> <b>NOT MAPPED</b> - must be set by service layer to current time</li>
     *   <li><b>transactionId:</b> <b>NOT MAPPED</b> - auto-generated by database during INSERT</li>
     * </ul>
     * 
     * <p><b>Service Layer Responsibilities:</b> The generated entity is INCOMPLETE and requires
     * additional enrichment by service layer before persistence:
     * <pre>
     * // 1. Resolve account from accountId or cardNumber
     * if (request.getAccountId() != null) {
     *     Account account = accountRepository.findById(request.getAccountId()).orElseThrow();
     *     transaction.setAccount(account);
     * } else {
     *     CardXref xref = cardXrefRepository.findByCardNumber(request.getCardNumber()).orElseThrow();
     *     transaction.setAccount(xref.getAccount());
     * }
     * 
     * // 2. Generate unique transaction number (business key)
     * String transactionNumber = generateTransactionNumber();  // e.g., "TXN20240115123456"
     * transaction.setTransactionNumber(transactionNumber);
     * 
     * // 3. Set processing timestamp to current time
     * transaction.setProcessingTimestamp(LocalDateTime.now());
     * </pre>
     * 
     * <p><b>Date/Time Combination:</b> COBOL TRAN-ORIG-TS (PIC X(26)) timestamp is reconstructed
     * by combining {@code transactionDate} (LocalDate) and {@code transactionTime} (LocalTime)
     * from request into {@code originalTimestamp} (LocalDateTime). This preserves the merchant
     * authorization timestamp for audit trail and reconciliation purposes.
     * 
     * <p><b>BigDecimal Precision:</b> Request field {@code transactionAmount} is validated with
     * {@code @DecimalMin("0.01")} and {@code @Digits(integer=9, fraction=2)} ensuring financial
     * precision. MapStruct maps this directly to entity {@code amount} field without conversion,
     * preserving exact decimal value for database persistence as NUMERIC(11,2).
     * 
     * <p><b>Null Value Strategy:</b> Optional merchant fields (merchantId, merchantName,
     * merchantCity, merchantZip) use {@link NullValuePropertyMappingStrategy#IGNORE} strategy,
     * meaning null values in request are preserved as null in entity (appropriate for
     * non-merchant transactions like payments or transfers).
     * 
     * <p><b>Validation:</b> Input validation is performed by Spring's {@code @Valid} annotation
     * on controller {@code @RequestBody} parameter BEFORE mapper is invoked. All required fields
     * are guaranteed to be non-null and constraints (e.g., @Pattern, @Size) are enforced by
     * Bean Validation framework before mapping occurs.
     * 
     * <p><b>Card Number Security:</b> Full 16-digit card number is accepted in request and
     * mapped to entity {@code cardNumber} field. Service layer is responsible for additional
     * validation (Luhn algorithm check) and audit logging (must use masked version for logs).
     * Entity toString() method excludes cardNumber via {@code @ToString.Exclude} preventing
     * accidental exposure in logs.
     * 
     * <p><b>Usage Example:</b>
     * <pre>
     * &#64;PostMapping("/transactions")
     * public ResponseEntity&lt;TransactionResponse&gt; createTransaction(
     *         &#64;Valid &#64;RequestBody TransactionRequest request) {
     *     
     *     // MapStruct generates partial entity from request
     *     Transaction transaction = transactionMapper.toEntity(request);
     *     
     *     // Service layer enrichment (required before persistence)
     *     Account account = resolveAccount(request);
     *     transaction.setAccount(account);
     *     transaction.setTransactionNumber(generateTransactionNumber());
     *     transaction.setProcessingTimestamp(LocalDateTime.now());
     *     
     *     // Validate business rules (sufficient funds, valid card, etc.)
     *     validateTransaction(transaction);
     *     
     *     // Persist and return response
     *     Transaction saved = transactionRepository.save(transaction);
     *     return ResponseEntity.ok(transactionMapper.toResponse(saved));
     * }
     * </pre>
     * 
     * @param request the TransactionRequest DTO from HTTP POST body, validated by Bean Validation
     * @return Transaction entity with fields mapped from request, requires service layer enrichment
     * @throws NullPointerException if request parameter is null (MapStruct default behavior)
     * @see TransactionRequest.OneOfRequired for accountId/cardNumber mutual exclusivity validation
     */
    @Mapping(target = "transactionId", ignore = true)  // Auto-generated by database
    @Mapping(target = "transactionNumber", ignore = true)  // Generated by service layer
    @Mapping(target = "account", ignore = true)  // Resolved by service layer from accountId/cardNumber
    @Mapping(target = "description", source = "transactionDescription")
    @Mapping(target = "amount", source = "transactionAmount")
    @Mapping(target = "originalTimestamp", expression = "java(combineDateTime(request.getTransactionDate(), request.getTransactionTime()))")
    @Mapping(target = "processingTimestamp", ignore = true)  // Set by service layer to current time
    @Mapping(target = "cardNumber", source = "cardNumber")  // Full card number from request
    @Mapping(target = "merchantId", source = "merchantId", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "merchantName", source = "merchantName", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "merchantCity", source = "merchantCity", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "merchantZip", source = "merchantZip", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "createdAt", ignore = true)  // Auto-managed by JPA @CreatedDate
    @Mapping(target = "updatedAt", ignore = true)  // Auto-managed by JPA @LastModifiedDate
    @Mapping(target = "version", ignore = true)  // Auto-managed by JPA @Version for optimistic locking
    Transaction toEntity(TransactionRequest request);

    /**
     * Post-mapping hook to set PCI-DSS compliant masked card number in response DTO.
     * 
     * <p><b>PCI-DSS Compliance Requirement:</b> This method enforces PCI-DSS requirement 3.3
     * by ensuring full card numbers are NEVER exposed in JSON API responses. The response DTO
     * {@code cardNumberMasked} field is populated with the masked format (************1234)
     * from {@link Transaction#getCardNumberMasked()} method.
     * 
     * <p><b>Execution Order:</b> MapStruct invokes this method AFTER all field mappings in
     * {@link #toResponse} are complete, allowing post-processing of the generated DTO before
     * it is returned to the caller.
     * 
     * <p><b>Masking Format:</b>
     * <ul>
     *   <li><b>Input:</b> Full 16-digit card number "4111111111111234" in entity</li>
     *   <li><b>Output:</b> Masked format "************1234" in response DTO</li>
     *   <li><b>Null Safety:</b> Returns "****************" if card number is null/invalid</li>
     * </ul>
     * 
     * <p><b>Alternative Implementation Approaches:</b>
     * <ol>
     *   <li><b>@AfterMapping (current):</b> Centralized masking logic in mapper, reusable</li>
     *   <li><b>Custom Qualifier:</b> Could use MapStruct @Named method for explicit mapping</li>
     *   <li><b>Service Layer:</b> Could mask in service layer before returning response</li>
     * </ol>
     * The @AfterMapping approach is preferred because it keeps masking logic in the mapper
     * where all other field transformations occur, ensuring consistency across all usage sites.
     * 
     * <p><b>Performance:</b> {@link Transaction#getCardNumberMasked()} uses efficient string
     * substring operation (O(1) complexity). No regex or character iteration overhead.
     * 
     * <p><b>Security Audit:</b> This method is critical for PCI-DSS compliance. Any changes
     * must be reviewed to ensure full card numbers are never exposed. Consider adding automated
     * security tests that validate all JSON responses from transaction endpoints contain only
     * masked card numbers.
     * 
     * <p><b>Technical Specification:</b> Section 0.8.1 Critical Directive #3 mandates PCI-DSS
     * compliance with card number masking in all logs and API responses using format
     * "************1234" (12 asterisks + last 4 digits).
     * 
     * <p><b>Usage:</b> This method is automatically invoked by MapStruct-generated implementation
     * class. Developers do NOT call this method directly. It is public only because MapStruct
     * requires default (or public) visibility for generated code to invoke it.
     * 
     * @param transaction the source Transaction entity (contains full card number), must not be null
     * @param response the target TransactionResponse DTO being populated (mutable), must not be null
     * @see Transaction#getCardNumberMasked() for masking implementation
     * @see #toResponse for primary mapping method that invokes this hook
     */
    @AfterMapping
    default void afterMappingToResponse(Transaction transaction, @MappingTarget TransactionResponse response) {
        // PCI-DSS Critical: Set masked card number (************1234 format)
        // This ensures full card numbers are NEVER exposed in JSON API responses
        if (transaction != null && response != null) {
            response.setCardNumberMasked(transaction.getCardNumberMasked());
        }
    }

    /**
     * Combines LocalDate and LocalTime into LocalDateTime for original timestamp mapping.
     * 
     * <p><b>Use Case:</b> COBOL TRAN-ORIG-TS (PIC X(26)) timestamp is split into separate
     * date and time fields in TransactionRequest DTO for easier HTTP form input validation.
     * This helper method reconstructs the combined timestamp for entity persistence.
     * 
     * <p><b>Default Method:</b> MapStruct supports Java 8 default interface methods in mapper
     * interfaces, allowing helper methods for custom mapping logic. This method is invoked
     * from the {@code @Mapping} expression in {@link #toEntity}.
     * 
     * <p><b>Null Safety:</b> Returns null if either date or time parameter is null, allowing
     * validation framework to catch missing required fields rather than throwing NullPointerException.
     * 
     * <p><b>Timezone Handling:</b> LocalDateTime is timezone-agnostic. The combined timestamp
     * represents the date and time in server local time (no timezone conversion is performed).
     * For production deployments, consider using ZonedDateTime if timezone-aware timestamps
     * are required.
     * 
     * <p><b>COBOL Equivalent:</b> In COBOL batch processing (CBTRN01C.cbl), date and time
     * are often stored as separate fields (PIC X(08) date + PIC X(06) time) and combined
     * during processing. This method replicates that combination logic.
     * 
     * <p><b>Usage Example:</b>
     * <pre>
     * // Request DTO contains:
     * // transactionDate = 2024-01-15
     * // transactionTime = 10:30:45
     * 
     * // MapStruct expression invokes:
     * LocalDateTime originalTimestamp = combineDateTime(
     *     request.getTransactionDate(),  // LocalDate
     *     request.getTransactionTime()   // LocalTime
     * );
     * // Result: 2024-01-15T10:30:45
     * 
     * transaction.setOriginalTimestamp(originalTimestamp);
     * </pre>
     * 
     * @param date the transaction date from request (YYYY-MM-DD format), may be null
     * @param time the transaction time from request (HH:mm:ss format), may be null
     * @return combined LocalDateTime, or null if either parameter is null
     * @see #toEntity for usage in mapping expression
     */
    default LocalDateTime combineDateTime(java.time.LocalDate date, java.time.LocalTime time) {
        if (date == null || time == null) {
            return null;
        }
        return LocalDateTime.of(date, time);
    }
}
