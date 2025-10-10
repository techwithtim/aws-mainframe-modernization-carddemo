package com.aws.carddemo.dto.response;

import com.aws.carddemo.model.Transaction;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Response DTO for paginated transaction list operations from GET /api/v1/accounts/{id}/transactions.
 * Migrated from: app/bms/COTRN00.bms (Transaction List Browse Screen)
 * 
 * <p>This DTO replaces the COBOL VSAM KSDS STARTBR/READNEXT/ENDBR browse pattern with modern
 * Spring Data pagination. The COBOL screen displayed 10 transactions per page with forward/backward
 * navigation (F7/F8 keys), which is replaced by REST pagination with page number parameters and
 * HATEOAS navigation links.
 * 
 * <p><b>COBOL Browse Pattern → Spring Data Pagination Mapping:</b>
 * <ul>
 *   <li><b>STARTBR (start browse):</b> Spring Data Pageable with account ID filter</li>
 *   <li><b>READNEXT (read next record):</b> Automatic via Page.getContent() iteration</li>
 *   <li><b>ENDBR (end browse):</b> Automatic when Page boundary reached</li>
 *   <li><b>F7 (backward):</b> Decrement page number in HTTP query param</li>
 *   <li><b>F8 (forward):</b> Increment page number in HTTP query param</li>
 *   <li><b>PAGENUM field:</b> Replaced by pageNumber, totalPages metadata</li>
 * </ul>
 * 
 * <p><b>BMS Screen Field Mappings:</b>
 * <pre>
 * COBOL BMS Field (COTRN00.bms)    Java Field/Property              REST API JSON Field
 * ================================  ===============================  =================================
 * PAGENUM (page display)            pageNumber, totalPages           "pageNumber": 0, "totalPages": 5
 * TRNID01-10 (transaction IDs)      transactions[].transactionNumber "transactions[].transactionNumber"
 * TDATE01-10 (dates)                transactions[].transactionDate   "transactions[].transactionDate"
 * TDESC01-10 (descriptions)         transactions[].description       "transactions[].description"
 * TAMT001-10 (amounts)              transactions[].amount            "transactions[].amount"
 * F7=Backward                       links.previous                   "links": {"previous": "?page=0"}
 * F8=Forward                        links.next                       "links": {"next": "?page=2"}
 * (implicit) Total records          totalElements                    "totalElements": 125
 * (implicit) Records per page       pageSize                         "pageSize": 20
 * </pre>
 * 
 * <p><b>Pagination Metadata:</b>
 * <ul>
 *   <li><b>pageNumber:</b> Current page number (0-indexed for backend, often 1-indexed for UI)</li>
 *   <li><b>pageSize:</b> Number of records per page (typically 20 for transaction lists)</li>
 *   <li><b>totalElements:</b> Total number of transactions across all pages</li>
 *   <li><b>totalPages:</b> Total number of pages (calculated: totalElements / pageSize)</li>
 *   <li><b>hasNext:</b> Boolean indicating if next page exists (for UI button state)</li>
 *   <li><b>hasPrevious:</b> Boolean indicating if previous page exists (for UI button state)</li>
 *   <li><b>isFirst:</b> Boolean indicating if this is the first page (pageNumber == 0)</li>
 *   <li><b>isLast:</b> Boolean indicating if this is the last page (pageNumber == totalPages - 1)</li>
 * </ul>
 * 
 * <p><b>HATEOAS Links:</b>
 * The optional {@code links} object provides hypermedia navigation following REST best practices:
 * <ul>
 *   <li><b>self:</b> Current page URL</li>
 *   <li><b>first:</b> First page URL (page=0)</li>
 *   <li><b>previous:</b> Previous page URL (if hasPrevious is true)</li>
 *   <li><b>next:</b> Next page URL (if hasNext is true)</li>
 *   <li><b>last:</b> Last page URL (page=totalPages-1)</li>
 * </ul>
 * 
 * <p><b>Sorting Support:</b>
 * <ul>
 *   <li><b>sortBy:</b> Field name used for sorting (e.g., "processingTimestamp", "amount")</li>
 *   <li><b>sortDirection:</b> Sort direction "ASC" or "DESC" (default: "DESC" for timestamps)</li>
 * </ul>
 * 
 * <p><b>Factory Method Pattern:</b>
 * The static factory method {@code fromPage(Page<Transaction> page, String baseUrl)} converts
 * Spring Data {@code Page<Transaction>} repository results to this response DTO:
 * <pre>
 * Page&lt;Transaction&gt; page = transactionRepository.findByAccountAccountId(
 *     accountId,
 *     PageRequest.of(0, 20, Sort.by("processingTimestamp").descending())
 * );
 * TransactionListResponse response = TransactionListResponse.fromPage(page, 
 *     "/api/v1/accounts/" + accountId + "/transactions");
 * </pre>
 * 
 * <p><b>REST API Usage:</b>
 * <pre>
 * GET /api/v1/accounts/123/transactions?page=0&size=20&sort=processingTimestamp,desc
 * 
 * Response:
 * {
 *   "transactions": [
 *     {
 *       "transactionId": 5001,
 *       "transactionNumber": "TXN20240101001234",
 *       "transactionDate": "2024-01-01T10:15:30",
 *       "amount": 125.50,
 *       "description": "PURCHASE AT ACME STORE",
 *       "merchantName": "ACME STORE #123",
 *       "cardNumberMasked": "************1234"
 *     },
 *     // ... 19 more transactions
 *   ],
 *   "pageNumber": 0,
 *   "pageSize": 20,
 *   "totalElements": 125,
 *   "totalPages": 7,
 *   "hasNext": true,
 *   "hasPrevious": false,
 *   "isFirst": true,
 *   "isLast": false,
 *   "sortBy": "processingTimestamp",
 *   "sortDirection": "DESC",
 *   "links": {
 *     "self": "/api/v1/accounts/123/transactions?page=0&size=20",
 *     "first": "/api/v1/accounts/123/transactions?page=0&size=20",
 *     "next": "/api/v1/accounts/123/transactions?page=1&size=20",
 *     "last": "/api/v1/accounts/123/transactions?page=6&size=20"
 *   }
 * }
 * </pre>
 * 
 * <p><b>TransactionSummary Nested Class:</b>
 * Contains a subset of {@link Transaction} entity fields optimized for list display.
 * Full transaction details are available via GET /api/v1/transactions/{id}.
 * 
 * <p><b>JSON Serialization:</b>
 * <ul>
 *   <li>{@code @JsonProperty} annotations ensure consistent camelCase field naming</li>
 *   <li>{@code @JsonInclude(NON_NULL)} excludes null links object when not applicable</li>
 *   <li>Amount serialized with 2 decimal places precision via BigDecimal</li>
 *   <li>Timestamps serialized in ISO 8601 format (e.g., "2024-01-01T10:15:30")</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li>Use index: idx_transaction_account_date (account_id, processing_timestamp DESC)</li>
 *   <li>Default page size: 20 transactions balances response size vs. number of requests</li>
 *   <li>Stream API for entity-to-DTO mapping provides memory efficiency</li>
 *   <li>Lazy loading of Account relationship avoids N+1 query problem</li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 0.4.1: COTRN00.bms → TransactionListResponse transformation</li>
 *   <li>Section 0.8.2: Critical Design Pattern #1 - DTO Pattern for separation of concerns</li>
 *   <li>Section 6.1.3: REST API Design - Pagination and HATEOAS compliance</li>
 * </ul>
 * 
 * @see Transaction for full entity documentation
 * @see TransactionResponse for single transaction detail response
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionListResponse implements Serializable {

    /**
     * Serialization version UID for cache compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * List of transaction summaries for the current page.
     * Contains subset of transaction fields optimized for list display.
     * 
     * <p>Typical page size: 20 transactions (configurable via size query parameter).
     * Replaces COBOL BMS screen fields TRNID01-10, TDATE01-10, TDESC01-10, TAMT001-10.
     * 
     * @return list of transaction summaries, never null (empty list if no results)
     */
    @JsonProperty("transactions")
    private List<TransactionSummary> transactions;

    /**
     * Current page number (0-indexed).
     * 
     * <p>First page is 0, matching Spring Data conventions. UI layer may display
     * as 1-indexed by adding 1 for user-facing display.
     * 
     * <p>Replaces COBOL PAGENUM field showing current page position.
     * 
     * @return current page number, 0 for first page
     */
    @JsonProperty("pageNumber")
    private int pageNumber;

    /**
     * Number of records per page.
     * 
     * <p>Default: 20 transactions per page for REST API responses.
     * COBOL BMS screen showed 10 transactions; REST API increases to 20 for
     * better mobile/desktop UX reducing number of page requests.
     * 
     * @return records per page, typically 20
     */
    @JsonProperty("pageSize")
    private int pageSize;

    /**
     * Total number of transactions across all pages.
     * 
     * <p>Used to calculate totalPages and display "Showing 1-20 of 125" messages.
     * 
     * @return total transaction count, may be 0 for empty result sets
     */
    @JsonProperty("totalElements")
    private long totalElements;

    /**
     * Total number of pages.
     * 
     * <p>Calculated as: {@code (totalElements + pageSize - 1) / pageSize}
     * 
     * @return total page count, minimum 1 if any results exist
     */
    @JsonProperty("totalPages")
    private int totalPages;

    /**
     * Indicates if a next page exists.
     * 
     * <p>True if {@code pageNumber < totalPages - 1}.
     * Used to enable/disable "Next" button in UI and include "next" link.
     * 
     * <p>Replaces COBOL F8=Forward key availability.
     * 
     * @return true if next page exists, false if on last page
     */
    @JsonProperty("hasNext")
    private boolean hasNext;

    /**
     * Indicates if a previous page exists.
     * 
     * <p>True if {@code pageNumber > 0}.
     * Used to enable/disable "Previous" button in UI and include "previous" link.
     * 
     * <p>Replaces COBOL F7=Backward key availability.
     * 
     * @return true if previous page exists, false if on first page
     */
    @JsonProperty("hasPrevious")
    private boolean hasPrevious;

    /**
     * Indicates if this is the first page.
     * 
     * <p>True if {@code pageNumber == 0}.
     * 
     * @return true if first page, false otherwise
     */
    @JsonProperty("isFirst")
    private boolean isFirst;

    /**
     * Indicates if this is the last page.
     * 
     * <p>True if {@code pageNumber == totalPages - 1}.
     * 
     * @return true if last page, false otherwise
     */
    @JsonProperty("isLast")
    private boolean isLast;

    /**
     * Field name used for sorting.
     * 
     * <p>Common values: "processingTimestamp" (default), "amount", "merchantName".
     * Extracted from Spring Data Sort parameter.
     * 
     * @return sort field name, may be null if no explicit sort specified
     */
    @JsonProperty("sortBy")
    private String sortBy;

    /**
     * Sort direction: "ASC" or "DESC".
     * 
     * <p>Default: "DESC" for processingTimestamp (most recent transactions first).
     * 
     * @return sort direction, may be null if no explicit sort specified
     */
    @JsonProperty("sortDirection")
    private String sortDirection;

    /**
     * HATEOAS hypermedia links for pagination navigation.
     * 
     * <p>Optional map containing navigation URLs following REST HATEOAS principles:
     * <ul>
     *   <li><b>self:</b> Current page URL</li>
     *   <li><b>first:</b> First page URL (always present)</li>
     *   <li><b>previous:</b> Previous page URL (only if hasPrevious is true)</li>
     *   <li><b>next:</b> Next page URL (only if hasNext is true)</li>
     *   <li><b>last:</b> Last page URL (always present)</li>
     * </ul>
     * 
     * <p>Marked {@code @JsonInclude(NON_NULL)} so this field is omitted entirely
     * from JSON response if null (simplified mode without HATEOAS).
     * 
     * @return map of link relations to URLs, may be null
     */
    @JsonProperty("links")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> links;

    /**
     * Factory method converting Spring Data Page to TransactionListResponse.
     * 
     * <p>This method bridges the repository layer (returning {@code Page<Transaction>})
     * and the REST API layer (returning {@code TransactionListResponse}). It extracts
     * pagination metadata from the Spring Data Page object and converts entity instances
     * to TransactionSummary DTOs.
     * 
     * <p><b>Conversion Process:</b>
     * <ol>
     *   <li>Extract pagination metadata: page number, size, total elements, total pages</li>
     *   <li>Calculate boolean indicators: hasNext, hasPrevious, isFirst, isLast</li>
     *   <li>Map Transaction entities to TransactionSummary DTOs via stream pipeline</li>
     *   <li>Extract sort information if Sort is present in Pageable</li>
     *   <li>Build HATEOAS links using baseUrl and pagination metadata</li>
     * </ol>
     * 
     * <p><b>Usage Example:</b>
     * <pre>
     * // In TransactionController
     * Page&lt;Transaction&gt; page = transactionRepository.findByAccountAccountId(
     *     accountId,
     *     PageRequest.of(pageNum, pageSize, Sort.by("processingTimestamp").descending())
     * );
     * 
     * String baseUrl = "/api/v1/accounts/" + accountId + "/transactions";
     * TransactionListResponse response = TransactionListResponse.fromPage(page, baseUrl);
     * 
     * return ResponseEntity.ok(response);
     * </pre>
     * 
     * <p><b>HATEOAS Link Construction:</b>
     * Links are built with format: {@code baseUrl + "?page=" + pageNumber + "&size=" + pageSize}
     * <ul>
     *   <li><b>self:</b> Current page link</li>
     *   <li><b>first:</b> Always page=0</li>
     *   <li><b>previous:</b> Only if pageNumber > 0</li>
     *   <li><b>next:</b> Only if pageNumber < totalPages - 1</li>
     *   <li><b>last:</b> Always page=totalPages-1</li>
     * </ul>
     * 
     * <p><b>Sort Parameter Extraction:</b>
     * If the Page's Pageable contains a Sort, the first Sort.Order is extracted
     * to populate sortBy and sortDirection fields for client transparency.
     * 
     * <p><b>Performance Note:</b>
     * Stream API with {@code map()} and {@code collect()} provides memory-efficient
     * transformation without creating intermediate collections. For large pages
     * (e.g., 100+ records), this prevents excessive object allocation.
     * 
     * @param page Spring Data Page containing Transaction entities from repository query
     * @param baseUrl base REST API URL for link construction (e.g., "/api/v1/accounts/123/transactions")
     * @return fully populated TransactionListResponse with transactions and pagination metadata
     * @throws NullPointerException if page or baseUrl is null
     */
    public static TransactionListResponse fromPage(Page<Transaction> page, String baseUrl) {
        // Extract pagination metadata from Spring Data Page
        int pageNumber = page.getNumber();
        int pageSize = page.getSize();
        long totalElements = page.getTotalElements();
        int totalPages = page.getTotalPages();

        // Convert Transaction entities to TransactionSummary DTOs
        List<TransactionSummary> summaries = page.getContent().stream()
                .map(transaction -> TransactionSummary.builder()
                        .transactionId(transaction.getTransactionId())
                        .transactionNumber(transaction.getTransactionNumber())
                        .transactionDate(transaction.getProcessingTimestamp())
                        .amount(transaction.getAmount())
                        .description(transaction.getDescription())
                        .merchantName(transaction.getMerchantName())
                        .cardNumberMasked(transaction.getCardNumberMasked())
                        .build())
                .collect(Collectors.toList());

        // Extract sort information if present
        String sortBy = null;
        String sortDirection = null;
        if (page.getSort().isSorted()) {
            org.springframework.data.domain.Sort.Order order = page.getSort().iterator().next();
            sortBy = order.getProperty();
            sortDirection = order.getDirection().name(); // "ASC" or "DESC"
        }

        // Build HATEOAS navigation links
        Map<String, String> links = new HashMap<>();
        String selfLink = baseUrl + "?page=" + pageNumber + "&size=" + pageSize;
        links.put("self", selfLink);
        links.put("first", baseUrl + "?page=0&size=" + pageSize);
        
        if (page.hasPrevious()) {
            links.put("previous", baseUrl + "?page=" + (pageNumber - 1) + "&size=" + pageSize);
        }
        
        if (page.hasNext()) {
            links.put("next", baseUrl + "?page=" + (pageNumber + 1) + "&size=" + pageSize);
        }
        
        if (totalPages > 0) {
            links.put("last", baseUrl + "?page=" + (totalPages - 1) + "&size=" + pageSize);
        }

        // Build and return the response DTO
        return TransactionListResponse.builder()
                .transactions(summaries)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .links(links)
                .build();
    }

    /**
     * Nested DTO representing a transaction summary for list display.
     * 
     * <p>This class contains a subset of {@link Transaction} entity fields optimized
     * for transaction list views. It excludes detailed fields (merchant city, ZIP, etc.)
     * that are only needed in the full transaction detail view.
     * 
     * <p><b>Field Selection Rationale:</b>
     * <ul>
     *   <li><b>Included:</b> Transaction ID, number, date, amount, description, merchant name, masked card</li>
     *   <li><b>Excluded:</b> Type code, category code, source, merchant ID, city, ZIP, timestamps</li>
     *   <li><b>Reason:</b> List view prioritizes readability; full details available via detail endpoint</li>
     * </ul>
     * 
     * <p><b>BMS Screen Mapping:</b>
     * <pre>
     * COBOL BMS Field (COTRN00.bms)    TransactionSummary Field
     * ================================  =================================
     * TRNID01-10 (16 chars)            transactionNumber (String)
     * TDATE01-10 (8 chars mm/dd/yy)    transactionDate (LocalDateTime ISO-8601)
     * TDESC01-10 (26 chars)            description (truncated to 26 for display)
     * TAMT001-10 (12 chars $9,999.99)  amount (BigDecimal formatted with 2 decimals)
     * (not displayed)                  merchantName (additional context)
     * (not displayed)                  cardNumberMasked (PCI-DSS compliant display)
     * </pre>
     * 
     * <p><b>PCI-DSS Compliance:</b>
     * Uses {@code cardNumberMasked} field which returns "************1234" format,
     * never the full card number. This ensures list responses comply with PCI-DSS
     * requirement to mask card numbers in all displays.
     * 
     * <p><b>Immutability:</b>
     * Lombok @Builder pattern encourages immutable construction. Once built, the
     * summary should not be modified, ensuring thread-safety and cache-ability.
     * 
     * <p><b>JSON Serialization:</b>
     * <ul>
     *   <li>transactionDate serialized in ISO 8601 format: "2024-01-01T10:15:30"</li>
     *   <li>amount serialized with 2 decimal places precision via BigDecimal</li>
     *   <li>All fields use camelCase naming via @JsonProperty annotations</li>
     * </ul>
     * 
     * <p><b>Usage in Controller:</b>
     * TransactionSummary instances are created automatically by the {@code fromPage()}
     * factory method. Controllers should not manually construct summary objects; instead,
     * use the factory method for consistent transformation.
     * 
     * @see Transaction for full entity with all fields
     * @see TransactionListResponse#fromPage(Page, String) for automatic construction
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionSummary implements Serializable {

        /**
         * Serialization version UID for cache compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Transaction surrogate primary key.
         * 
         * <p>Used for constructing detail view links: {@code /api/v1/transactions/{transactionId}}
         * 
         * @return transaction ID, never null for persisted transactions
         */
        @JsonProperty("transactionId")
        private Long transactionId;

        /**
         * Business transaction number - the original COBOL TRAN-ID.
         * 
         * <p>Format: 16-character alphanumeric like "TXN20240101001234".
         * 
         * <p>Replaces COBOL BMS fields TRNID01-10.
         * 
         * @return transaction number, never null
         */
        @JsonProperty("transactionNumber")
        private String transactionNumber;

        /**
         * Transaction processing timestamp for display in list.
         * 
         * <p>This is the system posting time ({@code processingTimestamp}), not the
         * merchant authorization time ({@code originalTimestamp}). Lists are sorted
         * by this field in descending order (most recent first).
         * 
         * <p>Replaces COBOL BMS fields TDATE01-10 (8-char mm/dd/yy format).
         * REST API uses ISO 8601 format for clarity and timezone support.
         * 
         * @return processing timestamp in ISO 8601 format, never null
         */
        @JsonProperty("transactionDate")
        private LocalDateTime transactionDate;

        /**
         * Transaction amount in US dollars.
         * 
         * <p>BigDecimal with 2 decimal places precision (e.g., 125.50).
         * 
         * <p>Replaces COBOL BMS fields TAMT001-10 (12-char formatted as $9,999.99).
         * 
         * @return transaction amount, never null, always positive
         */
        @JsonProperty("amount")
        private BigDecimal amount;

        /**
         * Transaction description for customer-facing display.
         * 
         * <p>Truncated to 26 characters in COBOL BMS screen; full description (100 chars)
         * available via detail endpoint.
         * 
         * <p>Replaces COBOL BMS fields TDESC01-10 (26-char display).
         * 
         * @return transaction description, never null
         */
        @JsonProperty("description")
        private String description;

        /**
         * Merchant business name.
         * 
         * <p>Provides additional context for transaction identification. Not displayed
         * in original COBOL BMS screen but included in REST API for richer UX.
         * 
         * @return merchant name, may be null for non-merchant transactions
         */
        @JsonProperty("merchantName")
        private String merchantName;

        /**
         * PCI-DSS compliant masked card number: "************1234".
         * 
         * <p>Shows only last 4 digits for security compliance. Full card number
         * never appears in list responses.
         * 
         * <p><b>Security:</b> Complies with Section 0.8.1 Critical Directive #3 -
         * PCI-DSS card masking requirement.
         * 
         * @return masked card number safe for display, never full card number
         */
        @JsonProperty("cardNumberMasked")
        private String cardNumberMasked;
    }
}
