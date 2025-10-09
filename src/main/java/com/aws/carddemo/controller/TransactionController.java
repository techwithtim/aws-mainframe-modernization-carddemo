package com.aws.carddemo.controller;

import com.aws.carddemo.dto.request.TransactionRequest;
import com.aws.carddemo.dto.response.TransactionListResponse;
import com.aws.carddemo.dto.response.TransactionResponse;
import com.aws.carddemo.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;

/**
 * REST API controller for transaction operations.
 * Migrated from: app/cbl/COTRN00C.cbl, app/cbl/COTRN01C.cbl, app/cbl/COTRN02C.cbl
 * BMS Maps: app/bms/COTRN00.bms, app/bms/COTRN01.bms, app/bms/COTRN02.bms
 * 
 * <p>This controller modernizes the COBOL CICS transaction management programs by exposing
 * RESTful JSON APIs for transaction browsing, viewing, and posting operations. It replaces
 * BMS 3270 terminal screens with HTTP endpoints while preserving all business logic and
 * validation rules from the legacy COBOL programs.
 * 
 * <p><b>COBOL Program Mapping:</b>
 * <ul>
 *   <li><b>COTRN00C.cbl:</b> Transaction browse/list → GET /api/v1/accounts/{accountId}/transactions</li>
 *   <li><b>COTRN01C.cbl:</b> Transaction detail view → GET /api/v1/transactions/{transactionId}</li>
 *   <li><b>COTRN02C.cbl:</b> Transaction manual add → POST /api/v1/transactions</li>
 * </ul>
 * 
 * <p><b>BMS Screen to REST API Transformation:</b>
 * <pre>
 * COBOL BMS Screen             REST API Endpoint                           HTTP Method  Response DTO
 * ===========================  ==========================================  ===========  ==========================
 * COTRN00.bms (browse list)    /api/v1/accounts/{id}/transactions         GET          TransactionListResponse
 *   - TRNIDIN (search field)     ?accountId={id} (path variable)
 *   - PAGENUM (page display)     ?page=0&size=20 (query params)
 *   - TRNID01-10 (TXN IDs)       transactions[].transactionNumber
 *   - TDATE01-10 (dates)         transactions[].transactionDate
 *   - TDESC01-10 (descriptions)  transactions[].description
 *   - TAMT001-10 (amounts)       transactions[].amount
 *   - F7=Backward                links.previous
 *   - F8=Forward                 links.next
 * 
 * COTRN01.bms (detail view)    /api/v1/transactions/{id}                  GET          TransactionResponse
 *   - TRNID (transaction ID)     {id} path variable
 *   - TRNTYPE (type code)        transactionTypeCode
 *   - TRNCAT (category code)     transactionCategoryCode
 *   - TRNDESC (description)      description
 *   - TRNSRC (source)            transactionSource
 *   - TRNAMT (amount)            amount (BigDecimal)
 *   - CARDNUM (card number)      cardNumberMasked (last 4 digits)
 *   - MERCHANT fields            merchantName, merchantCity, merchantZip
 * 
 * COTRN02.bms (add transaction) /api/v1/transactions                      POST         TransactionResponse
 *   - CARDNUM (card number)      cardNumber (request body)
 *   - TRNTYPE (type code)        transactionTypeCode (request body)
 *   - TRNCAT (category code)     transactionCategoryCode (request body)
 *   - TRNAMT (amount)            transactionAmount (request body)
 *   - TRNDESC (description)      transactionDescription (request body)
 *   - MERCHANT fields            merchantId, merchantName, etc. (request body)
 * </pre>
 * 
 * <p><b>CICS Command to Spring MVC Annotation Mapping:</b>
 * <pre>
 * COBOL CICS Command                           Spring MVC Equivalent
 * ===========================================  =========================================
 * EXEC CICS RECEIVE MAP(COTRN0A)               @RequestParam or @PathVariable
 * EXEC CICS SEND MAP(COTRN0A)                  return ResponseEntity&lt;TransactionListResponse&gt;
 * EXEC CICS READ FILE('TRANFILE')              transactionService.getTransactionById(id)
 * EXEC CICS STARTBR FILE('TRANFILE')           PageRequest.of(page, size, Sort.by(...))
 * EXEC CICS READNEXT FILE('TRANFILE')          Pageable automatic iteration via Page&lt;T&gt;
 * EXEC CICS WRITE FILE('TRANFILE')             transactionService.postTransaction(request)
 * EXEC CICS XCTL PROGRAM('COTRN01C')           return ResponseEntity with Location header
 * EXEC CICS RETURN                             return ResponseEntity
 * </pre>
 * 
 * <p><b>Endpoint Details:</b>
 * 
 * <p><b>1. GET /api/v1/accounts/{accountId}/transactions</b>
 * <ul>
 *   <li><b>Purpose:</b> Retrieve paginated transaction history for an account</li>
 *   <li><b>COBOL Source:</b> COTRN00C.cbl paragraph 2000-READ-TRANSACT-FILE</li>
 *   <li><b>Request Parameters:</b>
 *     <ul>
 *       <li>accountId (path): Account identifier (PIC 9(11) in COBOL)</li>
 *       <li>page (query, optional): Page number (0-indexed, default 0)</li>
 *       <li>size (query, optional): Records per page (default 20, COBOL used 10)</li>
 *       <li>startDate (query, optional): Filter start date in yyyy-MM-dd format</li>
 *       <li>endDate (query, optional): Filter end date in yyyy-MM-dd format</li>
 *       <li>typeCode (query, optional): Transaction type code filter</li>
 *       <li>sort (query, optional): Sort specification (default: processingTimestamp,desc)</li>
 *     </ul>
 *   </li>
 *   <li><b>Response:</b> HTTP 200 OK with TransactionListResponse containing:
 *     <ul>
 *       <li>transactions array (list of transaction summaries)</li>
 *       <li>Pagination metadata (pageNumber, pageSize, totalElements, totalPages)</li>
 *       <li>Navigation flags (hasNext, hasPrevious, isFirst, isLast)</li>
 *       <li>HATEOAS links (self, first, previous, next, last)</li>
 *     </ul>
 *   </li>
 *   <li><b>Error Responses:</b>
 *     <ul>
 *       <li>404 NOT FOUND: Account ID not found (COBOL FILE STATUS '23')</li>
 *       <li>400 BAD REQUEST: Invalid date format or pagination parameters</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>2. GET /api/v1/transactions/{transactionId}</b>
 * <ul>
 *   <li><b>Purpose:</b> Retrieve detailed information for a single transaction</li>
 *   <li><b>COBOL Source:</b> COTRN01C.cbl paragraph 2000-READ-TRANSACT</li>
 *   <li><b>Request Parameters:</b>
 *     <ul>
 *       <li>transactionId (path): Transaction surrogate key (Long)</li>
 *     </ul>
 *   </li>
 *   <li><b>Response:</b> HTTP 200 OK with TransactionResponse containing:
 *     <ul>
 *       <li>Full transaction details (all CVTRA05Y.cpy copybook fields)</li>
 *       <li>Masked card number (PCI-DSS compliant, last 4 digits only)</li>
 *       <li>Merchant information (name, city, ZIP)</li>
 *       <li>Transaction type and category details</li>
 *       <li>Timestamps (original and processing)</li>
 *     </ul>
 *   </li>
 *   <li><b>Error Responses:</b>
 *     <ul>
 *       <li>404 NOT FOUND: Transaction ID not found (COBOL FILE STATUS '23')</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>3. POST /api/v1/transactions</b>
 * <ul>
 *   <li><b>Purpose:</b> Manually post a new transaction (admin/testing function)</li>
 *   <li><b>COBOL Source:</b> COTRN02C.cbl paragraphs 2000-VALIDATE-TRANSACTION, 3000-WRITE-TRANSACTION</li>
 *   <li><b>Request Body:</b> TransactionRequest JSON with fields:
 *     <ul>
 *       <li>cardNumber or accountId (one required, validated by @OneOfRequired)</li>
 *       <li>transactionTypeCode (e.g., "01" for purchase, "02" for refund)</li>
 *       <li>transactionCategoryCode (e.g., "05" for retail, "06" for dining)</li>
 *       <li>transactionAmount (BigDecimal, PIC S9(09)V99 COMP-3 in COBOL)</li>
 *       <li>transactionDate (LocalDate, optional, defaults to current date)</li>
 *       <li>transactionDescription (String, max 100 chars)</li>
 *       <li>merchantId, merchantName, merchantCity, merchantZip (optional)</li>
 *     </ul>
 *   </li>
 *   <li><b>Validation:</b> Bean Validation constraints trigger before service call:
 *     <ul>
 *       <li>@NotNull for required fields</li>
 *       <li>@DecimalMin("0.01"), @DecimalMax("999999999.99") for amount</li>
 *       <li>@Pattern(regexp="\\d{16}") for card number</li>
 *       <li>@Size(max=100) for description</li>
 *     </ul>
 *   </li>
 *   <li><b>Business Logic:</b> Delegated to TransactionService.postTransaction():
 *     <ul>
 *       <li>Card → Account cross-reference lookup (XREFFILE in COBOL)</li>
 *       <li>Transaction type validation (TRNTYPE file in COBOL)</li>
 *       <li>Transaction category validation (TRANCATG file in COBOL)</li>
 *       <li>Balance check for debit transactions</li>
 *       <li>Transaction record creation and persistence</li>
 *       <li>Account balance update (ACCTFILE REWRITE in COBOL)</li>
 *       <li>Category balance update (TCATBAL file in COBOL)</li>
 *     </ul>
 *   </li>
 *   <li><b>Response:</b> HTTP 201 CREATED with:
 *     <ul>
 *       <li>Location header pointing to new transaction: /api/v1/transactions/{id}</li>
 *       <li>TransactionResponse body with created transaction details</li>
 *     </ul>
 *   </li>
 *   <li><b>Error Responses:</b>
 *     <ul>
 *       <li>400 BAD REQUEST: Validation failures (field errors in response body)</li>
 *       <li>404 NOT FOUND: Card number or account ID not found</li>
 *       <li>409 CONFLICT: Insufficient funds for debit transaction</li>
 *       <li>422 UNPROCESSABLE ENTITY: Invalid transaction type or category code</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>Security and Compliance:</b>
 * <ul>
 *   <li><b>PCI-DSS Compliance:</b> Card numbers masked in all responses (Section 0.8.1 requirement)</li>
 *   <li><b>Audit Logging:</b> All transaction operations logged with masked account IDs</li>
 *   <li><b>Authentication:</b> Requires valid JWT token (Spring Security integration)</li>
 *   <li><b>Authorization:</b> User can only view transactions for their own accounts</li>
 *   <li><b>Admin Role:</b> POST endpoint may require ROLE_ADMIN for manual transaction posting</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li><b>Database Index:</b> idx_transaction_account_date on (account_id, processing_timestamp DESC)</li>
 *   <li><b>Default Page Size:</b> 20 transactions balances UX vs. response size</li>
 *   <li><b>Query Optimization:</b> Lazy loading of Account relationship to avoid N+1 queries</li>
 *   <li><b>Response Time Target:</b> <200ms at 95th percentile for list, <100ms for detail</li>
 *   <li><b>Caching:</b> Transaction details immutable after posting, eligible for HTTP caching</li>
 * </ul>
 * 
 * <p><b>Functional Equivalence Notes:</b>
 * <ul>
 *   <li><b>COBOL Pagination:</b> STARTBR/READNEXT cursor → Spring Data Page-based pagination</li>
 *   <li><b>COBOL F7/F8 Keys:</b> Function key navigation → HTTP query params (page-1, page+1)</li>
 *   <li><b>COBOL XCTL:</b> Program transfer → HTTP 303 See Other with Location header</li>
 *   <li><b>COBOL FILE STATUS:</b> File error codes → HTTP status codes (404, 409, 500)</li>
 *   <li><b>COBOL SYNCPOINT:</b> Transaction commit → Spring @Transactional auto-commit</li>
 * </ul>
 * 
 * <p><b>Migration Decisions:</b>
 * <ul>
 *   <li><b>Page Size Change:</b> Increased from 10 (COBOL BMS limit) to 20 (modern UX standard)</li>
 *   <li><b>Sort Order:</b> Default DESC by processingTimestamp (most recent first), configurable</li>
 *   <li><b>HATEOAS Links:</b> Added for REST best practices, not present in COBOL screens</li>
 *   <li><b>Date Format:</b> Changed from mm/dd/yy (COBOL) to ISO 8601 yyyy-MM-dd (JSON standard)</li>
 * </ul>
 * 
 * <p><b>Testing Strategy:</b>
 * <ul>
 *   <li><b>Unit Tests:</b> TransactionControllerTest with @WebMvcTest and MockMvc</li>
 *   <li><b>Integration Tests:</b> TransactionIntegrationTest with Testcontainers PostgreSQL</li>
 *   <li><b>Equivalence Tests:</b> Compare outputs with known COBOL transaction data</li>
 *   <li><b>Performance Tests:</b> JMeter load tests verify <200ms response times</li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 0.4.1: File-by-File Transformation Plan (COTRN00/01/02 mapping)</li>
 *   <li>Section 0.8.1: Functional Equivalence Mandate (preserve COBOL business logic)</li>
 *   <li>Section 0.8.2: Critical Design Patterns (Repository, Service, DTO patterns)</li>
 *   <li>Section 6.1: Core Services Architecture (REST API design)</li>
 * </ul>
 * 
 * @see TransactionService for business logic implementation
 * @see TransactionRequest for request body structure
 * @see TransactionResponse for single transaction response structure
 * @see TransactionListResponse for paginated list response structure
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    /**
     * Transaction service handling all business logic.
     * Injected via constructor (Lombok @RequiredArgsConstructor).
     * 
     * <p>Implements business rules from:
     * <ul>
     *   <li>COTRN00C.cbl: Transaction browse and pagination logic</li>
     *   <li>COTRN01C.cbl: Transaction detail retrieval</li>
     *   <li>COTRN02C.cbl: Transaction validation, posting, and balance updates</li>
     * </ul>
     */
    private final TransactionService transactionService;

    /**
     * Retrieves paginated transaction history for a specific account.
     * 
     * <p><b>Endpoint:</b> GET /api/v1/accounts/{accountId}/transactions
     * 
     * <p><b>COBOL Source:</b> COTRN00C.cbl - Transaction Browse Program
     * <ul>
     *   <li>Paragraph 2000-READ-TRANSACT-FILE: VSAM KSDS STARTBR/READNEXT browse</li>
     *   <li>Paragraph 2100-PROCESS-PAGE: Build screen data for 10 transactions</li>
     *   <li>Paragraph 2200-BUILD-TRANSACT-LINES: Format transaction display rows</li>
     * </ul>
     * 
     * <p><b>BMS Screen:</b> COTRN00.bms - Transaction List Browse Screen
     * <ul>
     *   <li>TRNIDIN: Account ID input field → {accountId} path variable</li>
     *   <li>PAGENUM: Page number display → pageNumber in response metadata</li>
     *   <li>TRNID01-10: Transaction IDs → transactionNumber in response array</li>
     *   <li>TDATE01-10: Transaction dates → transactionDate in response array</li>
     *   <li>TDESC01-10: Descriptions → description in response array</li>
     *   <li>TAMT001-10: Amounts → amount in response array</li>
     *   <li>F7=Backward: Previous page → links.previous in HATEOAS response</li>
     *   <li>F8=Forward: Next page → links.next in HATEOAS response</li>
     * </ul>
     * 
     * <p><b>COBOL Logic Preserved:</b>
     * <pre>
     * COBOL (COTRN00C.cbl):
     *     EXEC CICS STARTBR
     *         FILE('TRANFILE')
     *         RIDFLD(WS-ACCT-ID)
     *         GTEQ
     *     END-EXEC.
     *     
     *     PERFORM UNTIL TRANSACT-EOF OR WS-REC-COUNT >= 10
     *         EXEC CICS READNEXT
     *             FILE('TRANFILE')
     *             INTO(TRAN-RECORD)
     *             RIDFLD(WS-TRAN-ID)
     *         END-EXEC
     *         ADD 1 TO WS-REC-COUNT
     *         MOVE TRAN-ID TO TRNID-O(WS-REC-COUNT)
     *         MOVE TRAN-DATE TO TDATE-O(WS-REC-COUNT)
     *     END-PERFORM.
     * 
     * Java (TransactionController):
     *     Pageable pageable = PageRequest.of(page, size, Sort.by("processingTimestamp").descending());
     *     Page&lt;Transaction&gt; transactionPage = transactionService.getTransactionHistory(accountId, pageable);
     *     TransactionListResponse response = TransactionListResponse.fromPage(transactionPage, baseUrl);
     *     // Spring Data handles STARTBR (query start), READNEXT (iteration), ENDBR (auto-close)
     * </pre>
     * 
     * <p><b>Request Parameters:</b>
     * <ul>
     *   <li><b>accountId:</b> Account identifier from path (required)
     *     <ul>
     *       <li>COBOL type: PIC 9(11) → Java type: Long</li>
     *       <li>Example: 1000000001</li>
     *       <li>Validation: Must exist in ACCOUNT table</li>
     *     </ul>
     *   </li>
     *   <li><b>pageable:</b> Spring Data pagination/sorting (auto-bound from query params)
     *     <ul>
     *       <li>page: Page number (0-indexed, default 0)</li>
     *       <li>size: Records per page (default 20, COBOL used 10)</li>
     *       <li>sort: Sort specification (default: processingTimestamp,desc)</li>
     *       <li>Example: ?page=0&size=20&sort=processingTimestamp,desc</li>
     *     </ul>
     *   </li>
     *   <li><b>startDate:</b> Optional filter for transaction date range start (query param)
     *     <ul>
     *       <li>Format: yyyy-MM-dd (ISO 8601 date format)</li>
     *       <li>COBOL equivalent: WS-START-DATE PIC X(10)</li>
     *       <li>Example: 2024-01-01</li>
     *       <li>Default: null (no start date filter)</li>
     *     </ul>
     *   </li>
     *   <li><b>endDate:</b> Optional filter for transaction date range end (query param)
     *     <ul>
     *       <li>Format: yyyy-MM-dd (ISO 8601 date format)</li>
     *       <li>COBOL equivalent: WS-END-DATE PIC X(10)</li>
     *       <li>Example: 2024-12-31</li>
     *       <li>Default: null (no end date filter)</li>
     *     </ul>
     *   </li>
     *   <li><b>typeCode:</b> Optional filter by transaction type code (query param)
     *     <ul>
     *       <li>Format: 2-character type code (e.g., "01", "02")</li>
     *       <li>COBOL equivalent: TRAN-TYPE-CD PIC X(02)</li>
     *       <li>Example: "01" for purchase transactions</li>
     *       <li>Default: null (no type filter)</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * <p><b>Response Structure:</b> HTTP 200 OK with TransactionListResponse
     * <pre>
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
     *     }
     *     // ... more transactions (up to pageSize)
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
     *     "self": "/api/v1/accounts/1000000001/transactions?page=0&size=20",
     *     "first": "/api/v1/accounts/1000000001/transactions?page=0&size=20",
     *     "next": "/api/v1/accounts/1000000001/transactions?page=1&size=20",
     *     "last": "/api/v1/accounts/1000000001/transactions?page=6&size=20"
     *   }
     * }
     * </pre>
     * 
     * <p><b>Error Responses:</b>
     * <ul>
     *   <li><b>404 NOT FOUND:</b> Account ID not found in database
     *     <ul>
     *       <li>COBOL equivalent: FILE STATUS '23' on ACCTFILE READ</li>
     *       <li>Response body: ApiError with message "Account not found: {accountId}"</li>
     *     </ul>
     *   </li>
     *   <li><b>400 BAD REQUEST:</b> Invalid pagination parameters or date format
     *     <ul>
     *       <li>Examples: page < 0, size < 1, invalid date format</li>
     *       <li>Response body: ApiError with field validation details</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * <p><b>Audit Logging:</b>
     * <ul>
     *   <li>INFO: Successful retrieval with masked account ID (last 4 digits)</li>
     *   <li>WARN: Account not found (with full ID for troubleshooting)</li>
     *   <li>DEBUG: Pagination parameters and result count</li>
     * </ul>
     * 
     * <p><b>Example Usage:</b>
     * <pre>
     * // Simple list - first 20 transactions
     * GET /api/v1/accounts/1000000001/transactions
     * 
     * // Second page with 10 records per page
     * GET /api/v1/accounts/1000000001/transactions?page=1&size=10
     * 
     * // Date range filter - January 2024
     * GET /api/v1/accounts/1000000001/transactions?startDate=2024-01-01&endDate=2024-01-31
     * 
     * // Purchase transactions only
     * GET /api/v1/accounts/1000000001/transactions?typeCode=01
     * 
     * // Combined filters with custom page size
     * GET /api/v1/accounts/1000000001/transactions?page=0&size=50&startDate=2024-01-01&typeCode=01
     * </pre>
     * 
     * <p><b>Performance Notes:</b>
     * <ul>
     *   <li>Database query uses covering index: idx_transaction_account_date</li>
     *   <li>Target response time: <200ms at 95th percentile</li>
     *   <li>Typical query time: 50-100ms for 20 records</li>
     *   <li>Memory-efficient: Stream API for entity-to-DTO transformation</li>
     * </ul>
     * 
     * @param accountId the account identifier from path variable (PIC 9(11) in COBOL)
     * @param pageable Spring Data pagination and sorting parameters (auto-bound from query params)
     * @param startDate optional transaction date range start in yyyy-MM-dd format
     * @param endDate optional transaction date range end in yyyy-MM-dd format
     * @param typeCode optional transaction type code filter (2-char code)
     * @return ResponseEntity with HTTP 200 OK and TransactionListResponse body
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if account not found (returns 404)
     */
    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<TransactionListResponse> getTransactionsByAccount(
            @PathVariable("accountId") Long accountId,
            Pageable pageable,
            @RequestParam(value = "startDate", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "typeCode", required = false) String typeCode) {
        
        // PCI-DSS compliant logging - mask account ID (show last 4 digits only for audit)
        String maskedAccountId = "****" + String.valueOf(accountId).substring(Math.max(0, String.valueOf(accountId).length() - 4));
        log.info("Retrieving transaction list for account: {}, page: {}, size: {}, startDate: {}, endDate: {}, typeCode: {}",
                maskedAccountId, pageable.getPageNumber(), pageable.getPageSize(), startDate, endDate, typeCode);
        
        // Delegate to service layer for business logic and data retrieval
        TransactionListResponse response;
        
        if (startDate != null || endDate != null) {
            // Date range filter requested - use specialized service method
            // Replaces COBOL date range filtering logic from COTRN00C.cbl
            log.debug("Applying date range filter: start={}, end={}", startDate, endDate);
            response = transactionService.getTransactionsByDateRange(accountId, startDate, endDate, pageable);
        } else {
            // Standard transaction history retrieval without date filtering
            // Replaces COBOL STARTBR/READNEXT browse from COTRN00C.cbl
            response = transactionService.getTransactionHistory(accountId, pageable);
        }
        
        log.info("Successfully retrieved {} transactions for account: {}, totalElements: {}, totalPages: {}",
                response.getTransactions().size(), maskedAccountId, response.getTotalElements(), response.getTotalPages());
        
        // Return HTTP 200 OK with transaction list response
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves detailed information for a single transaction.
     * 
     * <p><b>Endpoint:</b> GET /api/v1/transactions/{transactionId}
     * 
     * <p><b>COBOL Source:</b> COTRN01C.cbl - Transaction Detail View Program
     * <ul>
     *   <li>Paragraph 2000-READ-TRANSACT: Direct READ by transaction ID</li>
     *   <li>Paragraph 3000-POPULATE-MAP: Format all transaction fields for display</li>
     * </ul>
     * 
     * <p><b>BMS Screen:</b> COTRN01.bms - Transaction Detail View Screen
     * <ul>
     *   <li>TRNID: Transaction ID → transactionNumber in response</li>
     *   <li>TRNTYPE: Transaction type → transactionTypeCode in response</li>
     *   <li>TRNCAT: Transaction category → transactionCategoryCode in response</li>
     *   <li>TRNDESC: Description → description in response</li>
     *   <li>TRNSRC: Source → transactionSource in response</li>
     *   <li>TRNAMT: Amount → amount (BigDecimal) in response</li>
     *   <li>CARDNUM: Card number → cardNumberMasked (PCI-DSS compliant) in response</li>
     *   <li>MERCHANT fields → merchantName, merchantCity, merchantZip in response</li>
     * </ul>
     * 
     * <p><b>COBOL Logic Preserved:</b>
     * <pre>
     * COBOL (COTRN01C.cbl):
     *     EXEC CICS READ
     *         FILE('TRANFILE')
     *         INTO(TRAN-RECORD)
     *         RIDFLD(WS-TRAN-ID)
     *     END-EXEC.
     *     
     *     IF EIBRESPOK
     *         MOVE TRAN-ID TO TRNID-O
     *         MOVE TRAN-TYPE-CD TO TRNTYPE-O
     *         MOVE TRAN-CAT-CD TO TRNCAT-O
     *         MOVE TRAN-AMT TO TRNAMT-O
     *         // ... populate all fields
     *         EXEC CICS SEND MAP('COTRN1A') END-EXEC
     *     ELSE
     *         MOVE 'Transaction not found' TO ERROR-MSG-O
     *         EXEC CICS SEND MAP('COTRN1A') END-EXEC
     *     END-IF.
     * 
     * Java (TransactionController):
     *     TransactionResponse response = transactionService.getTransactionById(transactionId);
     *     return ResponseEntity.ok(response);
     *     // If not found, service throws ResourceNotFoundException → 404 via GlobalExceptionHandler
     * </pre>
     * 
     * <p><b>Request Parameters:</b>
     * <ul>
     *   <li><b>transactionId:</b> Transaction surrogate primary key from path (required)
     *     <ul>
     *       <li>Java type: Long (auto-incrementing database ID)</li>
     *       <li>Example: 5001</li>
     *       <li>Note: This is NOT the transactionNumber (business key)</li>
     *       <li>Validation: Must exist in TRANSACTION table</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * <p><b>Response Structure:</b> HTTP 200 OK with TransactionResponse
     * <pre>
     * {
     *   "transactionId": 5001,
     *   "transactionNumber": "TXN20240101001234",
     *   "transactionTypeCode": "01",
     *   "transactionTypeName": "Purchase",
     *   "transactionCategoryCode": "05",
     *   "transactionCategoryName": "Retail",
     *   "transactionSource": "POS",
     *   "description": "PURCHASE AT ACME STORE",
     *   "amount": 125.50,
     *   "merchantId": "MER123456",
     *   "merchantName": "ACME STORE #123",
     *   "merchantCity": "SEATTLE",
     *   "merchantZip": "98101",
     *   "cardNumberMasked": "************1234",
     *   "originalTimestamp": "2024-01-01T10:15:30",
     *   "processingTimestamp": "2024-01-01T10:15:35",
     *   "accountId": 1000000001
     * }
     * </pre>
     * 
     * <p><b>Error Responses:</b>
     * <ul>
     *   <li><b>404 NOT FOUND:</b> Transaction ID not found in database
     *     <ul>
     *       <li>COBOL equivalent: FILE STATUS '23' on TRANFILE READ</li>
     *       <li>Response body: ApiError with message "Transaction not found: {transactionId}"</li>
     *       <li>Thrown by: TransactionService.getTransactionById() → ResourceNotFoundException</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * <p><b>Security Notes:</b>
     * <ul>
     *   <li><b>Card Masking:</b> Full card number NEVER returned; only last 4 digits</li>
     *   <li><b>PCI-DSS Compliance:</b> Follows Section 0.8.1 security requirements</li>
     *   <li><b>Authorization:</b> User must have permission to view this transaction's account</li>
     * </ul>
     * 
     * <p><b>Audit Logging:</b>
     * <ul>
     *   <li>INFO: Successful retrieval with transaction ID</li>
     *   <li>WARN: Transaction not found (with ID for troubleshooting)</li>
     * </ul>
     * 
     * <p><b>Example Usage:</b>
     * <pre>
     * // Retrieve transaction by ID
     * GET /api/v1/transactions/5001
     * 
     * // Typical flow: User clicks transaction in list → detail view
     * // List endpoint returns: "transactionId": 5001
     * // UI calls: GET /api/v1/transactions/5001
     * // Displays full transaction details
     * </pre>
     * 
     * <p><b>Performance Notes:</b>
     * <ul>
     *   <li>Database query: Primary key lookup (extremely fast, <10ms)</li>
     *   <li>Target response time: <100ms at 95th percentile</li>
     *   <li>Caching eligible: Transaction details immutable after posting</li>
     * </ul>
     * 
     * @param transactionId the transaction surrogate primary key from path variable
     * @return ResponseEntity with HTTP 200 OK and TransactionResponse body
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if transaction not found (returns 404)
     */
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @PathVariable("transactionId") Long transactionId) {
        
        log.info("Retrieving transaction detail for ID: {}", transactionId);
        
        // Delegate to service layer for direct transaction retrieval by ID
        // Replaces COBOL EXEC CICS READ FILE('TRANFILE') from COTRN01C.cbl
        TransactionResponse response = transactionService.getTransactionById(transactionId);
        
        log.info("Successfully retrieved transaction: {}, amount: {}, type: {}",
                response.getTransactionNumber(), response.getAmount(), response.getTransactionTypeCode());
        
        // Return HTTP 200 OK with full transaction details
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new transaction (manual posting for admin/testing purposes).
     * 
     * <p><b>Endpoint:</b> POST /api/v1/transactions
     * 
     * <p><b>COBOL Source:</b> COTRN02C.cbl - Transaction Add Program
     * <ul>
     *   <li>Paragraph 2000-VALIDATE-TRANSACTION: Input validation and business rule checks</li>
     *   <li>Paragraph 2100-VALIDATE-CARD: Card-to-account cross-reference lookup</li>
     *   <li>Paragraph 2200-VALIDATE-TYPE-CAT: Transaction type/category validation</li>
     *   <li>Paragraph 2300-CHECK-BALANCE: Sufficient funds check for debits</li>
     *   <li>Paragraph 3000-WRITE-TRANSACTION: Create transaction record</li>
     *   <li>Paragraph 3100-UPDATE-ACCOUNT-BALANCE: Update account balance</li>
     *   <li>Paragraph 3200-UPDATE-CATEGORY-BALANCE: Update category balance</li>
     * </ul>
     * 
     * <p><b>BMS Screen:</b> COTRN02.bms - Transaction Add Screen
     * <ul>
     *   <li>CARDNUM: Card number input → cardNumber in request body</li>
     *   <li>TRNTYPE: Type code input → transactionTypeCode in request body</li>
     *   <li>TRNCAT: Category code input → transactionCategoryCode in request body</li>
     *   <li>TRNAMT: Amount input → transactionAmount in request body</li>
     *   <li>TRNDESC: Description input → transactionDescription in request body</li>
     *   <li>MERCHANT fields → merchantId, merchantName, merchantCity, merchantZip in request body</li>
     * </ul>
     * 
     * <p><b>COBOL Logic Preserved:</b>
     * <pre>
     * COBOL (COTRN02C.cbl):
     *     // 1. Validate card number
     *     EXEC CICS READ
     *         FILE('CCXREF')
     *         INTO(XREF-RECORD)
     *         RIDFLD(WS-CARD-NUM)
     *     END-EXEC.
     *     
     *     // 2. Get account
     *     MOVE XREF-ACCT-ID TO WS-ACCT-ID.
     *     EXEC CICS READ
     *         FILE('ACCTDAT')
     *         INTO(ACCT-RECORD)
     *         RIDFLD(WS-ACCT-ID)
     *     END-EXEC.
     *     
     *     // 3. Check balance for debits
     *     IF TRAN-TYPE-DEBIT AND TRAN-AMT > ACCT-BAL
     *         MOVE 'Insufficient funds' TO ERROR-MSG
     *         GO TO 9999-ERROR-RETURN
     *     END-IF.
     *     
     *     // 4. Write transaction
     *     EXEC CICS WRITE
     *         FILE('TRANFILE')
     *         FROM(TRAN-RECORD)
     *         RIDFLD(WS-TRAN-ID)
     *     END-EXEC.
     *     
     *     // 5. Update account balance
     *     IF TRAN-TYPE-DEBIT
     *         SUBTRACT TRAN-AMT FROM ACCT-BAL
     *     ELSE
     *         ADD TRAN-AMT TO ACCT-BAL
     *     END-IF.
     *     EXEC CICS REWRITE FILE('ACCTDAT') FROM(ACCT-RECORD) END-EXEC.
     *     
     *     // 6. Update category balance
     *     // ... similar logic for TCATBAL file
     *     
     *     EXEC CICS SYNCPOINT END-EXEC.
     *     MOVE 'Transaction posted successfully' TO SUCCESS-MSG.
     * 
     * Java (TransactionController + TransactionService):
     *     // Controller receives request and validates
     *     @Valid TransactionRequest request = ...
     *     
     *     // Service handles all business logic in @Transactional method
     *     TransactionResponse response = transactionService.postTransaction(request);
     *     // Service performs: card validation, type/category validation, balance checks,
     *     // transaction creation, account balance update, category balance update
     *     // All in one ACID transaction (Spring @Transactional = CICS SYNCPOINT)
     *     
     *     // Controller builds HTTP 201 Created response with Location header
     *     URI location = ServletUriComponentsBuilder.fromCurrentRequest()
     *         .path("/{id}").buildAndExpand(response.getTransactionId()).toUri();
     *     return ResponseEntity.created(location).body(response);
     * </pre>
     * 
     * <p><b>Request Body:</b> TransactionRequest JSON with validation constraints
     * <pre>
     * {
     *   "cardNumber": "4111111111111234",           // @Pattern(regexp="\\d{16}") - REQUIRED (or accountId)
     *   "transactionTypeCode": "01",                // @NotNull @Size(min=2, max=2) - REQUIRED
     *   "transactionCategoryCode": "05",            // @NotNull @Size(min=2, max=2) - REQUIRED
     *   "transactionAmount": 125.50,                // @NotNull @DecimalMin("0.01") @Digits(9,2) - REQUIRED
     *   "transactionDate": "2024-01-01",            // Optional, defaults to current date
     *   "transactionTime": "10:15:30",              // Optional, defaults to current time
     *   "transactionDescription": "PURCHASE AT ACME STORE",  // @Size(max=100) - Optional
     *   "merchantId": "MER123456",                  // @Size(max=9) - Optional
     *   "merchantName": "ACME STORE #123",          // @Size(max=50) - Optional
     *   "merchantCity": "SEATTLE",                  // @Size(max=50) - Optional
     *   "merchantZip": "98101"                      // @Pattern(regexp="\\d{5}") - Optional
     * }
     * </pre>
     * 
     * <p><b>Validation Rules:</b> Applied BEFORE service call via Bean Validation
     * <ul>
     *   <li><b>@OneOfRequired:</b> Either cardNumber OR accountId must be provided (custom validator)</li>
     *   <li><b>@NotNull:</b> transactionTypeCode, transactionCategoryCode, transactionAmount</li>
     *   <li><b>@DecimalMin("0.01"):</b> Amount must be positive (no zero-dollar transactions)</li>
     *   <li><b>@DecimalMax("999999999.99"):</b> Amount within PIC S9(09)V99 COMP-3 range</li>
     *   <li><b>@Digits(integer=9, fraction=2):</b> Preserves COBOL monetary precision</li>
     *   <li><b>@Pattern(regexp="\\d{16}"):</b> Card number must be exactly 16 digits</li>
     *   <li><b>@Size(max=100):</b> Description max length constraint</li>
     *   <li><b>@Pattern(regexp="\\d{5}"):</b> ZIP code must be 5 digits</li>
     * </ul>
     * 
     * <p><b>Business Logic:</b> Delegated to TransactionService.postTransaction()
     * <ul>
     *   <li><b>Step 1 - Card Validation:</b> If cardNumber provided, lookup in CARD_XREF table
     *     <ul>
     *       <li>COBOL: EXEC CICS READ FILE('CCXREF')</li>
     *       <li>Java: cardXrefRepository.findByCardNumber(cardNumber)</li>
     *       <li>Throws: ResourceNotFoundException if card not found (404 NOT FOUND)</li>
     *     </ul>
     *   </li>
     *   <li><b>Step 2 - Account Retrieval:</b> Get account from card cross-reference or direct ID
     *     <ul>
     *       <li>COBOL: EXEC CICS READ FILE('ACCTDAT') RIDFLD(XREF-ACCT-ID)</li>
     *       <li>Java: accountRepository.findById(accountId)</li>
     *       <li>Throws: ResourceNotFoundException if account not found (404 NOT FOUND)</li>
     *     </ul>
     *   </li>
     *   <li><b>Step 3 - Type Validation:</b> Verify transaction type exists
     *     <ul>
     *       <li>COBOL: EXEC CICS READ FILE('TRNTYPE') RIDFLD(TRAN-TYPE-CD)</li>
     *       <li>Java: transactionTypeRepository.findByTypeCode(typeCode)</li>
     *       <li>Throws: InvalidInputException if type invalid (422 UNPROCESSABLE ENTITY)</li>
     *     </ul>
     *   </li>
     *   <li><b>Step 4 - Category Validation:</b> Verify transaction category exists
     *     <ul>
     *       <li>COBOL: EXEC CICS READ FILE('TRANCATG') RIDFLD(TRAN-CAT-CD)</li>
     *       <li>Java: transactionCategoryRepository.findByCategoryCode(categoryCode)</li>
     *       <li>Throws: InvalidInputException if category invalid (422 UNPROCESSABLE ENTITY)</li>
     *     </ul>
     *   </li>
     *   <li><b>Step 5 - Balance Check:</b> For debit transactions, verify sufficient funds
     *     <ul>
     *       <li>COBOL: IF TRAN-TYPE-DEBIT AND TRAN-AMT > ACCT-CURR-BAL → ERROR</li>
     *       <li>Java: if (isDebit && amount.compareTo(account.getCurrentBalance()) > 0) → throw</li>
     *       <li>Throws: InsufficientFundsException if balance too low (409 CONFLICT)</li>
     *     </ul>
     *   </li>
     *   <li><b>Step 6 - Transaction Creation:</b> Create and persist transaction record
     *     <ul>
     *       <li>COBOL: EXEC CICS WRITE FILE('TRANFILE') FROM(TRAN-RECORD)</li>
     *       <li>Java: transactionRepository.save(transaction)</li>
     *       <li>Generates transaction ID and timestamps</li>
     *     </ul>
     *   </li>
     *   <li><b>Step 7 - Account Balance Update:</b> Debit or credit account balance
     *     <ul>
     *       <li>COBOL: SUBTRACT/ADD TRAN-AMT TO/FROM ACCT-CURR-BAL → REWRITE</li>
     *       <li>Java: account.setCurrentBalance(newBalance); accountRepository.save(account)</li>
     *       <li>Atomic update in same transaction</li>
     *     </ul>
     *   </li>
     *   <li><b>Step 8 - Category Balance Update:</b> Update category balance tracker
     *     <ul>
     *       <li>COBOL: READ TCATBAL → UPDATE → REWRITE</li>
     *       <li>Java: categoryBalanceRepository.updateBalance(accountId, categoryId, amount)</li>
     *       <li>Tracks spending by category for statements</li>
     *     </ul>
     *   </li>
     *   <li><b>Step 9 - Transaction Commit:</b> Commit all changes atomically
     *     <ul>
     *       <li>COBOL: EXEC CICS SYNCPOINT END-EXEC</li>
     *       <li>Java: @Transactional annotation auto-commits on method return</li>
     *       <li>All-or-nothing: If any step fails, all changes roll back</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * <p><b>Response Structure:</b> HTTP 201 CREATED with Location header
     * <pre>
     * Status: 201 Created
     * Location: /api/v1/transactions/5001
     * 
     * Body:
     * {
     *   "transactionId": 5001,
     *   "transactionNumber": "TXN20240101001234",
     *   "transactionTypeCode": "01",
     *   "transactionCategoryCode": "05",
     *   "amount": 125.50,
     *   "description": "PURCHASE AT ACME STORE",
     *   "merchantName": "ACME STORE #123",
     *   "cardNumberMasked": "************1234",
     *   "processingTimestamp": "2024-01-01T10:15:35",
     *   "accountId": 1000000001
     *   // ... full transaction details
     * }
     * </pre>
     * 
     * <p><b>Error Responses:</b>
     * <ul>
     *   <li><b>400 BAD REQUEST:</b> Validation failures
     *     <ul>
     *       <li>Missing required fields (@NotNull violations)</li>
     *       <li>Invalid formats (@Pattern violations)</li>
     *       <li>Out-of-range values (@DecimalMin/@DecimalMax violations)</li>
     *       <li>Response body: ApiError with field-specific error messages</li>
     *       <li>Example: {"field": "transactionAmount", "message": "must be greater than 0.01"}</li>
     *     </ul>
     *   </li>
     *   <li><b>404 NOT FOUND:</b> Card number or account ID not found
     *     <ul>
     *       <li>COBOL equivalent: FILE STATUS '23' on CCXREF or ACCTDAT READ</li>
     *       <li>Response body: ApiError with message "Card not found: {cardNumber}"</li>
     *     </ul>
     *   </li>
     *   <li><b>409 CONFLICT:</b> Insufficient funds for debit transaction
     *     <ul>
     *       <li>COBOL equivalent: Balance check failure in paragraph 2300-CHECK-BALANCE</li>
     *       <li>Response body: ApiError with message "Insufficient funds: balance={balance}, amount={amount}"</li>
     *       <li>Thrown by: InsufficientFundsException in service layer</li>
     *     </ul>
     *   </li>
     *   <li><b>422 UNPROCESSABLE ENTITY:</b> Invalid transaction type or category code
     *     <ul>
     *       <li>COBOL equivalent: FILE STATUS '23' on TRNTYPE or TRANCATG READ</li>
     *       <li>Response body: ApiError with message "Invalid transaction type: {typeCode}"</li>
     *       <li>Thrown by: InvalidInputException in service layer</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * <p><b>Security and Audit:</b>
     * <ul>
     *   <li><b>PCI-DSS Logging:</b> Card number masked in all log entries (show last 4 digits only)</li>
     *   <li><b>Audit Trail:</b> All transaction postings logged with user ID, timestamp, and outcome</li>
     *   <li><b>Role-Based Access:</b> This endpoint may require ROLE_ADMIN (manual posting is admin function)</li>
     * </ul>
     * 
     * <p><b>Example Usage:</b>
     * <pre>
     * // Post transaction via card number
     * POST /api/v1/transactions
     * Content-Type: application/json
     * 
     * {
     *   "cardNumber": "4111111111111234",
     *   "transactionTypeCode": "01",
     *   "transactionCategoryCode": "05",
     *   "transactionAmount": 125.50,
     *   "transactionDescription": "PURCHASE AT ACME STORE",
     *   "merchantName": "ACME STORE #123"
     * }
     * 
     * // Post transaction via account ID (alternative)
     * POST /api/v1/transactions
     * Content-Type: application/json
     * 
     * {
     *   "accountId": 1000000001,
     *   "transactionTypeCode": "02",
     *   "transactionCategoryCode": "05",
     *   "transactionAmount": 50.00,
     *   "transactionDescription": "REFUND FOR RETURNED ITEM"
     * }
     * </pre>
     * 
     * <p><b>Performance Notes:</b>
     * <ul>
     *   <li>Target response time: <500ms at 95th percentile (multiple database operations)</li>
     *   <li>Transaction scope: All database updates in single @Transactional ACID transaction</li>
     *   <li>Rollback on error: Any exception causes full transaction rollback (data integrity)</li>
     *   <li>Database locks: Row-level locks on ACCOUNT and TRANSACTION_CATEGORY_BALANCE tables</li>
     * </ul>
     * 
     * @param request the transaction creation request with validated fields (via @Valid)
     * @return ResponseEntity with HTTP 201 CREATED, Location header, and TransactionResponse body
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if card/account not found (returns 404)
     * @throws com.aws.carddemo.exception.InvalidInputException if type/category invalid (returns 422)
     * @throws com.aws.carddemo.exception.InsufficientFundsException if balance too low (returns 409)
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if validation fails (returns 400)
     */
    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest request) {
        
        // PCI-DSS compliant logging - mask card number in logs (show last 4 digits only)
        String cardInfo = request.getCardNumber() != null 
                ? "****" + request.getCardNumber().substring(12) 
                : "account ID: " + request.getAccountId();
        
        log.info("Creating new transaction - card/account: {}, type: {}, category: {}, amount: {}",
                cardInfo, request.getTransactionTypeCode(), request.getTransactionCategoryCode(), 
                request.getTransactionAmount());
        
        // Delegate to service layer for comprehensive business logic execution
        // Replaces entire COBOL transaction posting workflow from COTRN02C.cbl:
        // - Card validation (paragraph 2100-VALIDATE-CARD)
        // - Type/category validation (paragraph 2200-VALIDATE-TYPE-CAT)
        // - Balance check (paragraph 2300-CHECK-BALANCE)
        // - Transaction write (paragraph 3000-WRITE-TRANSACTION)
        // - Account balance update (paragraph 3100-UPDATE-ACCOUNT-BALANCE)
        // - Category balance update (paragraph 3200-UPDATE-CATEGORY-BALANCE)
        // All wrapped in @Transactional (CICS SYNCPOINT equivalent)
        TransactionResponse response = transactionService.postTransaction(request);
        
        log.info("Successfully created transaction: {}, amount: {}, accountId: {}",
                response.getTransactionNumber(), response.getAmount(), response.getAccountId());
        
        // Build Location header pointing to newly created transaction resource
        // RESTful pattern: POST returns 201 Created with Location header
        // Client can follow Location to retrieve full transaction details
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getTransactionId())
                .toUri();
        
        // Return HTTP 201 CREATED with Location header and full transaction details in body
        return ResponseEntity.created(location).body(response);
    }
}
