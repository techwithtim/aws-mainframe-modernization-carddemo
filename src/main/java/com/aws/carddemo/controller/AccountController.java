/*
 * AccountController.java
 *
 * REST API controller for account management operations
 * 
 * Migrated from: app/cbl/COACTVWC.cbl (Account View CICS program)
 *                app/cbl/COACTUPC.cbl (Account Update CICS program)
 * Replaces BMS maps: app/bms/COACTVW.bms (Account View screen)
 *                    app/bms/COACTUP.bms (Account Update screen)
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
 * ============================================================================
 * COBOL TO JAVA MIGRATION NOTES
 * ============================================================================
 * 
 * This controller replaces two COBOL CICS online transaction programs:
 * 
 * 1. COACTVWC.cbl - Account View Program
 *    - Transaction: CVAC (Card View Account)
 *    - Function: Display account details including customer information
 *    - BMS Map: COACTVW / CACTVWA
 *    - File Access: READ ACCTDAT (Account Master), READ CUSTDAT (Customer Master)
 *    - Migration: GET /api/v1/accounts/{id} endpoint
 * 
 * 2. COACTUPC.cbl - Account Update Program
 *    - Transaction: CUAC (Card Update Account)
 *    - Function: Modify account and customer profile information
 *    - BMS Map: COACTUP / CACTUPA
 *    - File Access: READ UPDATE ACCTDAT, REWRITE ACCTDAT
 *    - Migration: PUT /api/v1/accounts/{id} endpoint
 * 
 * COBOL PROGRAM FLOW MAPPING:
 * ----------------------------
 * COBOL COACTVWC.cbl:
 *   EXEC CICS RECEIVE MAP('CACTVWA')
 *   PERFORM 9000-READ-ACCT                → AccountService.getAccountById()
 *     EXEC CICS READ DATASET('CXACAIX')    → CardXrefRepository.findByCardNumber()
 *     EXEC CICS READ DATASET('ACCTDAT')    → AccountRepository.findById()
 *     EXEC CICS READ DATASET('CUSTDAT')    → Eager fetch via @ManyToOne(fetch=EAGER)
 *   PERFORM 9100-SETUP-SCREEN              → AccountMapper.toResponse()
 *   EXEC CICS SEND MAP('CACTVWA')          → ResponseEntity<AccountResponse>
 * 
 * COBOL COACTUPC.cbl:
 *   EXEC CICS RECEIVE MAP('CACTUPA')       → @RequestBody @Valid AccountUpdateRequest
 *   PERFORM 1000-VALIDATE-INPUT            → Bean Validation annotations
 *   PERFORM 9000-READ-FOR-UPDATE           → AccountService.updateAccount() with pessimistic lock
 *     EXEC CICS READ UPDATE DATASET('ACCTDAT') → findByIdWithLock()
 *   PERFORM 1100-APPLY-UPDATES             → Account entity setters
 *   EXEC CICS REWRITE DATASET('ACCTDAT')   → accountRepository.save()
 *   EXEC CICS SYNCPOINT                    → @Transactional commit
 *   EXEC CICS SEND MAP('CACTUPA')          → ResponseEntity<AccountResponse>
 * 
 * KEY COBOL TO SPRING FRAMEWORK MAPPINGS:
 * ----------------------------------------
 * COBOL EXEC CICS RECEIVE MAP       → @RequestBody with Jackson JSON deserialization
 * COBOL EXEC CICS SEND MAP          → ResponseEntity<T> with @ResponseBody
 * COBOL EXEC CICS READ              → JPA repository findById() / findByXxx()
 * COBOL EXEC CICS READ UPDATE       → JPA pessimistic locking (@Lock annotation)
 * COBOL EXEC CICS REWRITE           → JPA repository save() on existing entity
 * COBOL EXEC CICS SYNCPOINT         → Spring @Transactional commit
 * COBOL EXEC CICS SYNCPOINT ROLLBACK → Spring transaction rollback on exception
 * COBOL FILE STATUS checks          → Optional.orElseThrow() → ResourceNotFoundException
 * COBOL validation logic            → Bean Validation (@Valid, @NotNull, @Pattern, etc.)
 * COBOL error messages              → GlobalExceptionHandler @ControllerAdvice
 * COBOL 88-level conditions         → Enum or boolean checks
 * COBOL EVALUATE/IF-ELSE            → Java if-else or switch expressions
 * 
 * REST API DESIGN:
 * ----------------
 * Base Path: /api/v1/accounts
 * 
 * Endpoints:
 *   GET    /api/v1/accounts/{id}    - Retrieve account details (COACTVWC.cbl equivalent)
 *   PUT    /api/v1/accounts/{id}    - Update account information (COACTUPC.cbl equivalent)
 * 
 * HTTP Status Codes:
 *   200 OK              - Successful retrieval or update
 *   400 BAD REQUEST     - Validation failure (invalid input data)
 *   404 NOT FOUND       - Account not found (FILE STATUS '23' equivalent)
 *   500 INTERNAL ERROR  - System error (ABEND-PROGRAM equivalent)
 * 
 * FUNCTIONAL EQUIVALENCE GUARANTEES:
 * -----------------------------------
 * 1. Data Integrity: All COBOL PIC clause precision preserved in BigDecimal types
 * 2. Business Logic: Account status transitions, credit limit validations identical
 * 3. Error Handling: All COBOL FILE STATUS conditions mapped to exceptions
 * 4. Transaction Semantics: ACID properties maintained via @Transactional
 * 5. Security: PCI-DSS compliance for sensitive data handling
 * 6. Audit Trail: All operations logged with user context (like COBOL transaction logging)
 * 
 * PERFORMANCE REQUIREMENTS:
 * -------------------------
 * - Account inquiry (GET): <200ms response time at 95th percentile
 * - Account update (PUT): <500ms response time at 95th percentile
 * - Support 1,000+ concurrent users
 * - Database connection pooling (HikariCP with 20 max connections)
 * 
 * SECURITY:
 * ---------
 * - JWT authentication required for all endpoints (via Spring Security)
 * - Role-based access control: ROLE_USER or ROLE_ADMIN
 * - Account numbers masked in logs (PCI-DSS requirement 3.4)
 * - Input validation prevents SQL injection and XSS attacks
 * - Rate limiting applied at API Gateway level
 * 
 * TESTING:
 * --------
 * - Unit tests: src/test/java/.../controller/AccountControllerTest.java
 * - Integration tests: src/test/java/.../integration/AccountIntegrationTest.java
 * - Test coverage target: ≥80% line coverage, ≥70% branch coverage
 * - Functional equivalence validated against COBOL test data
 */
package com.aws.carddemo.controller;

import com.aws.carddemo.dto.request.AccountUpdateRequest;
import com.aws.carddemo.dto.response.AccountResponse;
import com.aws.carddemo.mapper.AccountMapper;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for account management operations.
 * 
 * Provides RESTful API endpoints for account inquiry and updates, replacing
 * COBOL CICS programs COACTVWC.cbl and COACTUPC.cbl with modern HTTP/JSON interfaces.
 * 
 * All business logic is delegated to AccountService, following the controller-service-repository
 * layered architecture pattern per Spring Boot best practices.
 * 
 * @see AccountService
 * @see AccountResponse
 * @see AccountUpdateRequest
 * @see com.aws.carddemo.model.Account
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    /**
     * Account service for business logic operations.
     * Injected via constructor dependency injection (Lombok @RequiredArgsConstructor).
     * 
     * This service encapsulates all account-related business rules migrated from
     * COACTVWC.cbl and COACTUPC.cbl COBOL programs.
     */
    private final AccountService accountService;
    
    /**
     * Account mapper for entity to DTO conversions.
     * Injected via constructor dependency injection (Lombok @RequiredArgsConstructor).
     * 
     * This mapper converts Account entities to AccountResponse DTOs, flattening
     * the Customer relationship and formatting data for JSON serialization.
     */
    private final AccountMapper accountMapper;

    /**
     * Retrieve account details by account ID.
     * 
     * Endpoint: GET /api/v1/accounts/{id}
     * 
     * Migrated from: COACTVWC.cbl (Account View CICS program)
     * BMS Map: COACTVW.bms / CACTVWA
     * Transaction: CVAC
     * 
     * COBOL Equivalent Logic:
     * <pre>
     * 9000-READ-ACCT.
     *     EXEC CICS READ DATASET('ACCTDAT')
     *         RIDFLD(WS-ACCT-ID)
     *         INTO(ACCOUNT-RECORD)
     *         LENGTH(LENGTH OF ACCOUNT-RECORD)
     *         RESP(WS-RESP-CD)
     *         RESP2(WS-REAS-CD)
     *     END-EXEC.
     *     
     *     IF WS-RESP-CD NOT = DFHRESP(NORMAL)
     *         MOVE 'Account not found' TO ERROR-MESSAGE
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF.
     *     
     *     EXEC CICS READ DATASET('CUSTDAT')
     *         RIDFLD(ACCT-CUST-ID)
     *         INTO(CUSTOMER-RECORD)
     *         LENGTH(LENGTH OF CUSTOMER-RECORD)
     *         RESP(WS-RESP-CD)
     *     END-EXEC.
     * </pre>
     * 
     * Java Equivalent:
     * - JPA repository findById() replaces EXEC CICS READ DATASET('ACCTDAT')
     * - Eager fetch on Account.customer replaces EXEC CICS READ DATASET('CUSTDAT')
     * - Optional.orElseThrow() replaces COBOL FILE STATUS checks
     * - AccountMapper converts entity to DTO, replacing COBOL field-by-field mapping
     * 
     * Request Parameters:
     * @param id Account ID (COBOL: ACCT-ID PIC 9(11), Java: Long)
     *           Must be a positive integer from 1 to 99999999999
     *           Example: 1000000001 (11-digit account number)
     * 
     * Response:
     * @return ResponseEntity<AccountResponse> containing account details with HTTP 200 OK
     *         - accountId: Unique account identifier
     *         - accountNumber: 11-digit account number string
     *         - activeStatus: "Y" (active) or "N" (inactive)
     *         - currentBalance: Current account balance (BigDecimal with 2 decimal places)
     *         - creditLimit: Total credit limit
     *         - cashCreditLimit: Cash advance limit
     *         - currentCycleCredit: Credits in current billing cycle
     *         - currentCycleDebit: Debits in current billing cycle
     *         - openDate: Account opening date (ISO 8601 format)
     *         - expirationDate: Account expiration date
     *         - reissueDate: Card reissue date (nullable)
     *         - addressZip: ZIP code from account address
     *         - groupId: Account group identifier
     *         - customerId: Associated customer ID
     *         - customerFirstName: Customer first name (denormalized)
     *         - customerLastName: Customer last name (denormalized)
     *         - createdAt: Record creation timestamp
     *         - updatedAt: Last update timestamp
     * 
     * Error Responses:
     * - 404 NOT FOUND: Account with specified ID does not exist
     *   (COBOL: FILE STATUS '23' or RESP(NOTFND))
     * - 500 INTERNAL SERVER ERROR: Unexpected system error
     *   (COBOL: 9999-ABEND-PROGRAM paragraph)
     * 
     * PCI-DSS Compliance:
     * - Account numbers are included but masked in application logs
     * - Sensitive customer data (SSN, full DOB) excluded from response
     * - All API calls require JWT authentication with valid user role
     * 
     * Performance:
     * - Expected response time: <200ms at 95th percentile
     * - Database query optimized with eager fetch strategy
     * - Result cacheable at CDN/API Gateway level
     * 
     * Example Request:
     * <pre>
     * GET /api/v1/accounts/1000000001 HTTP/1.1
     * Host: api.carddemo.aws.com
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * Accept: application/json
     * </pre>
     * 
     * Example Response:
     * <pre>
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * 
     * {
     *   "accountId": 1000000001,
     *   "accountNumber": "10000000001",
     *   "activeStatus": "Y",
     *   "currentBalance": "1500.50",
     *   "creditLimit": "5000.00",
     *   "cashCreditLimit": "1000.00",
     *   "currentCycleCredit": "200.00",
     *   "currentCycleDebit": "350.75",
     *   "openDate": "2020-01-15",
     *   "expirationDate": "2025-01-31",
     *   "reissueDate": null,
     *   "addressZip": "75001",
     *   "groupId": "DEFAULT",
     *   "customerId": 2000000001,
     *   "customerFirstName": "John",
     *   "customerLastName": "Doe",
     *   "createdAt": "2020-01-15T10:30:00",
     *   "updatedAt": "2024-03-20T14:25:10"
     * }
     * </pre>
     * 
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if account not found
     * @see AccountService#getAccountById(Long)
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
        log.info("Account inquiry request received for account ID: {}", id);
        
        // Delegate to service layer for business logic execution
        // This replaces COBOL PERFORM 9000-READ-ACCT paragraph
        Account account = accountService.getAccountById(id);
        
        // Convert Account entity to AccountResponse DTO
        // This replaces COBOL PERFORM 9100-SETUP-SCREEN field-by-field mapping
        AccountResponse accountResponse = accountMapper.toResponse(account);
        
        // Log successful retrieval (account number masked in logs via Logback converter)
        log.info("Account inquiry completed successfully for account ID: {}, account number: {}", 
                 id, accountResponse.getAccountNumber());
        
        // Return HTTP 200 OK with account data
        // This replaces COBOL EXEC CICS SEND MAP('CACTVWA')
        return ResponseEntity.ok(accountResponse);
    }

    /**
     * Update account information.
     * 
     * Endpoint: PUT /api/v1/accounts/{id}
     * 
     * Migrated from: COACTUPC.cbl (Account Update CICS program)
     * BMS Map: COACTUP.bms / CACTUPA
     * Transaction: CUAC
     * 
     * COBOL Equivalent Logic:
     * <pre>
     * 1000-VALIDATE-INPUT.
     *     IF ACSTTUS NOT = 'A' AND NOT = 'C' AND NOT = 'S'
     *         MOVE 'Invalid account status' TO ERROR-MESSAGE
     *         GO TO 9999-SEND-ERROR-MAP
     *     END-IF.
     *     
     *     IF ACRDLIM NOT NUMERIC OR ACRDLIM < 0
     *         MOVE 'Invalid credit limit' TO ERROR-MESSAGE
     *         GO TO 9999-SEND-ERROR-MAP
     *     END-IF.
     *     
     * 9000-READ-FOR-UPDATE.
     *     EXEC CICS READ UPDATE DATASET('ACCTDAT')
     *         RIDFLD(WS-ACCT-ID)
     *         INTO(ACCOUNT-RECORD)
     *         LENGTH(LENGTH OF ACCOUNT-RECORD)
     *         RESP(WS-RESP-CD)
     *     END-EXEC.
     *     
     *     IF WS-RESP-CD NOT = DFHRESP(NORMAL)
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF.
     *     
     * 1100-APPLY-UPDATES.
     *     MOVE ACSTTUS TO ACCT-ACTIVE-STATUS.
     *     MOVE ACRDLIM TO ACCT-CREDIT-LIMIT.
     *     MOVE ACSHLIM TO ACCT-CASH-CREDIT-LIMIT.
     *     MOVE ADTOPEN TO ACCT-OPEN-DATE.
     *     ... (30+ field updates)
     *     
     *     EXEC CICS REWRITE DATASET('ACCTDAT')
     *         FROM(ACCOUNT-RECORD)
     *         LENGTH(LENGTH OF ACCOUNT-RECORD)
     *     END-EXEC.
     *     
     *     EXEC CICS SYNCPOINT.
     * </pre>
     * 
     * Java Equivalent:
     * - Bean Validation replaces COBOL 1000-VALIDATE-INPUT paragraph
     * - @Valid annotation triggers automatic validation before method execution
     * - JPA pessimistic locking replaces EXEC CICS READ UPDATE
     * - Account entity setters replace COBOL MOVE statements
     * - accountRepository.save() replaces EXEC CICS REWRITE
     * - @Transactional replaces EXEC CICS SYNCPOINT
     * 
     * Request Parameters:
     * @param id Account ID to update (path variable)
     * @param request AccountUpdateRequest DTO with updated values (request body)
     *                Validated by Bean Validation constraints before method invocation
     * 
     * Request Body Fields (from COACTUP.bms screen):
     * - accountStatus: Account status code ("A", "C", or "S") [REQUIRED]
     * - statusReason: Reason for status change (max 100 chars)
     * - creditLimit: Total credit limit (0.00 to 999,999.99) [REQUIRED]
     * - cashCreditLimit: Cash advance limit (0.00 to 999,999.99) [REQUIRED]
     * - accountOpenDate: Account opening date (yyyy-MM-dd) [REQUIRED]
     * - accountExpirationDate: Account expiration date (yyyy-MM-dd) [REQUIRED]
     * - reissueDate: Card reissue date (yyyy-MM-dd) [OPTIONAL]
     * - firstName: Customer first name (max 25 chars) [REQUIRED]
     * - middleName: Customer middle name (max 25 chars) [OPTIONAL]
     * - lastName: Customer last name (max 25 chars) [REQUIRED]
     * - ssnLastFour: Last 4 digits of SSN (4 digits)
     * - ficoScore: FICO credit score (300-850)
     * - dateOfBirth: Customer date of birth (yyyy-MM-dd) [REQUIRED]
     * - phoneNumber: Phone number (10 digits)
     * - addressLine1: Street address (max 50 chars) [REQUIRED]
     * - addressLine2: Apt/Suite (max 50 chars) [OPTIONAL]
     * - city: City name (max 50 chars) [REQUIRED]
     * - state: State code (2 uppercase letters) [REQUIRED]
     * - zipCode: ZIP code (5 or 9 digits with hyphen) [REQUIRED]
     * - email: Email address (max 100 chars, RFC 5322 format)
     * - governmentIssuedId: Driver's license or passport (max 20 chars)
     * - governmentIdState: Issuing state (2 uppercase letters)
     * - eftRoutingNumber: Bank routing number (9 digits)
     * - eftAccountNumber: Bank account number (4-17 digits)
     * 
     * Response:
     * @return ResponseEntity<AccountResponse> containing updated account with HTTP 200 OK
     * 
     * Error Responses:
     * - 400 BAD REQUEST: Validation failure (constraint violation)
     *   Examples:
     *   - Invalid account status (not A/C/S)
     *   - Credit limit out of range or cash limit > total limit
     *   - Invalid date format or future account open date
     *   - Invalid phone/email/ZIP format
     *   (COBOL: 1000-VALIDATE-INPUT validation failures)
     * 
     * - 404 NOT FOUND: Account with specified ID does not exist
     *   (COBOL: FILE STATUS '23' on READ UPDATE)
     * 
     * - 409 CONFLICT: Optimistic locking failure (concurrent update detected)
     *   (COBOL: ILLOGIC condition, requires retry)
     * 
     * - 500 INTERNAL SERVER ERROR: Unexpected system error
     *   (COBOL: 9999-ABEND-PROGRAM paragraph)
     * 
     * Business Rules Enforced:
     * 1. Account status transitions:
     *    - Active (A) → Closed (C), Suspended (S)
     *    - Suspended (S) → Active (A), Closed (C)
     *    - Closed (C) → Terminal state (no further transitions)
     * 
     * 2. Credit limits:
     *    - cashCreditLimit must be <= creditLimit
     *    - Both limits must be non-negative
     *    - Maximum limit: $999,999.99
     * 
     * 3. Date validations:
     *    - accountOpenDate must be in the past or present
     *    - accountExpirationDate must be in the future
     *    - accountExpirationDate must be > accountOpenDate + 1 year
     *    - reissueDate must be in the past if provided
     * 
     * 4. Customer age:
     *    - Customer must be 18-120 years old (calculated from dateOfBirth)
     * 
     * PCI-DSS Compliance:
     * - Only last 4 digits of SSN accepted (PCI-DSS requirement 3.4)
     * - Full SSN never accepted or stored
     * - Sensitive fields masked in logs
     * - Update operations logged with user context for audit trail
     * 
     * Performance:
     * - Expected response time: <500ms at 95th percentile
     * - Pessimistic locking prevents concurrent update conflicts
     * - Single database transaction for all updates (ACID properties)
     * 
     * Example Request:
     * <pre>
     * PUT /api/v1/accounts/1000000001 HTTP/1.1
     * Host: api.carddemo.aws.com
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * Content-Type: application/json
     * 
     * {
     *   "account_status": "A",
     *   "status_reason": "Customer requested limit increase",
     *   "credit_limit": "10000.00",
     *   "cash_credit_limit": "2000.00",
     *   "account_open_date": "2020-01-15",
     *   "account_expiration_date": "2026-01-31",
     *   "first_name": "John",
     *   "last_name": "Doe",
     *   "date_of_birth": "1985-03-20",
     *   "phone_number": "5551234567",
     *   "address_line1": "123 Main Street",
     *   "city": "Dallas",
     *   "state": "TX",
     *   "zip_code": "75001"
     * }
     * </pre>
     * 
     * Example Response:
     * <pre>
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * 
     * {
     *   "accountId": 1000000001,
     *   "accountNumber": "10000000001",
     *   "activeStatus": "Y",
     *   "currentBalance": "1500.50",
     *   "creditLimit": "10000.00",
     *   "cashCreditLimit": "2000.00",
     *   "currentCycleCredit": "200.00",
     *   "currentCycleDebit": "350.75",
     *   "openDate": "2020-01-15",
     *   "expirationDate": "2026-01-31",
     *   "reissueDate": null,
     *   "addressZip": "75001",
     *   "groupId": "DEFAULT",
     *   "customerId": 2000000001,
     *   "customerFirstName": "John",
     *   "customerLastName": "Doe",
     *   "createdAt": "2020-01-15T10:30:00",
     *   "updatedAt": "2024-11-20T09:15:30"
     * }
     * </pre>
     * 
     * @throws com.aws.carddemo.exception.ResourceNotFoundException if account not found
     * @throws com.aws.carddemo.exception.InvalidInputException if validation fails
     * @throws com.aws.carddemo.exception.InsufficientFundsException if balance exceeds new limit
     * @see AccountService#updateAccount(Long, AccountUpdateRequest)
     */
    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountUpdateRequest request) {
        
        log.info("Account update request received for account ID: {}", id);
        
        // Log key update fields (sensitive data excluded per PCI-DSS)
        log.debug("Account update details - Status: {}, Credit Limit: {}, Cash Limit: {}", 
                  request.getAccountStatus(), 
                  request.getCreditLimit(), 
                  request.getCashCreditLimit());
        
        // Delegate to service layer for business logic execution
        // Bean Validation (@Valid) has already validated the request object
        // This replaces COBOL PERFORM 1000-VALIDATE-INPUT + PERFORM 9000-READ-FOR-UPDATE
        // + PERFORM 1100-APPLY-UPDATES + EXEC CICS REWRITE + EXEC CICS SYNCPOINT
        Account updatedAccount = accountService.updateAccount(id, request);
        
        // Convert updated Account entity to AccountResponse DTO
        // This replaces COBOL PERFORM 9100-SETUP-SCREEN response field mapping
        AccountResponse accountResponse = accountMapper.toResponse(updatedAccount);
        
        // Log successful update (account number masked in logs)
        log.info("Account update completed successfully for account ID: {}, account number: {}", 
                 id, accountResponse.getAccountNumber());
        
        // Return HTTP 200 OK with updated account data
        // This replaces COBOL EXEC CICS SEND MAP('CACTUPA') with success message
        return ResponseEntity.ok(accountResponse);
    }
}
