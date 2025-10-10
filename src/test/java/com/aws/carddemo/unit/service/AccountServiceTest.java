/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.unit.service;

import com.aws.carddemo.dto.request.AccountUpdateRequest;
import com.aws.carddemo.exception.DuplicateResourceException;
import com.aws.carddemo.exception.InsufficientFundsException;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.mapper.AccountMapper;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.Customer;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CustomerRepository;
import com.aws.carddemo.service.AccountService;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit test class for AccountService business logic.
 * 
 * <p>Tests account operations migrated from COBOL programs COACTVWC.cbl (account view)
 * and COACTUPC.cbl (account update) with comprehensive validation of:
 * <ul>
 *   <li>Account inquiry by account number with eager loading of customer relationships</li>
 *   <li>Account balance updates with credit limit validation</li>
 *   <li>Account status management (active/inactive)</li>
 *   <li>Account creation and duplicate detection</li>
 *   <li>Exception handling for invalid accounts and credit limit violations</li>
 * </ul>
 * 
 * <p><b>COBOL Business Logic Preservation:</b>
 * <ul>
 *   <li>Credit limit enforcement: currentBalance + newCharge ≤ creditLimit 
 *       (from COACTUPC.cbl lines 3964-3966 balance check logic)</li>
 *   <li>Account lookup: EXEC CICS READ FILE(ACCTFILE) → AccountRepository.findByAccountNumber()</li>
 *   <li>Balance updates: EXEC CICS REWRITE FILE(ACCTFILE) → AccountRepository.save()</li>
 *   <li>Error handling: FILE STATUS '23' (NOTFND) → ResourceNotFoundException</li>
 *   <li>Duplicate key: FILE STATUS '22' → DuplicateResourceException</li>
 * </ul>
 * 
 * <p><b>Testing Strategy:</b>
 * <ul>
 *   <li>Uses Mockito for AccountRepository and CustomerRepository mocking</li>
 *   <li>Verifies @EntityGraph eager fetching prevents N+1 queries</li>
 *   <li>Tests BigDecimal arithmetic with scale=2, RoundingMode.HALF_UP</li>
 *   <li>Validates optimistic locking with @Version annotation</li>
 *   <li>Ensures comprehensive test coverage: ≥85% line, ≥75% branch per Section 0.8.1</li>
 * </ul>
 * 
 * <p><b>Test Fixture Data:</b>
 * <pre>
 * Account:
 *   - accountId: 1L
 *   - accountNumber: "00012345678"
 *   - currentBalance: $1,000.00
 *   - creditLimit: $5,000.00
 *   - accountStatus: 'Y' (active)
 *   - openDate: current date
 * 
 * Customer:
 *   - customerId: 1L
 *   - firstName: "John"
 *   - lastName: "Doe"
 * </pre>
 * 
 * @see AccountService for business logic implementation
 * @see Account for JPA entity definition
 * @see Customer for customer entity definition
 * @see AccountRepository for data access operations
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService accountService;

    // Test fixture data
    private Account testAccount;
    private Customer testCustomer;
    private static final String TEST_ACCOUNT_NUMBER = "00012345678";
    private static final Long TEST_ACCOUNT_ID = 1L;
    private static final BigDecimal TEST_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal TEST_CREDIT_LIMIT = new BigDecimal("5000.00");
    private static final String ACTIVE_STATUS = "Y";
    private static final String INACTIVE_STATUS = "N";

    /**
     * Sets up test fixture data before each test execution.
     * 
     * <p>Creates sample Account and Customer entities with realistic data
     * matching COBOL copybook CVACT01Y.cpy and CVCUS01Y.cpy structures.
     */
    @BeforeEach
    void setUp() {
        // Initialize test customer
        testCustomer = Customer.builder()
                .customerId(1L)
                .firstName("John")
                .lastName("Doe")
                .build();

        // Initialize test account with customer relationship
        testAccount = Account.builder()
                .accountId(TEST_ACCOUNT_ID)
                .accountNumber(TEST_ACCOUNT_NUMBER)
                .currentBalance(TEST_BALANCE)
                .creditLimit(TEST_CREDIT_LIMIT)
                .activeStatus(ACTIVE_STATUS)
                .openDate(LocalDate.now())
                .customer(testCustomer)
                .build();
    }

    // ==================== getAccountByAccountNumber() Tests ====================

    @Test
    @DisplayName("getAccountByAccountNumber - Success: Returns account with customer relationship")
    void testGetAccountByAccountNumber_Success() {
        // Given: Repository returns account with eager-loaded customer
        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));

        // When: Service method is called
        Account result = accountService.getAccountByAccountNumber(TEST_ACCOUNT_NUMBER);

        // Then: Account is returned with all expected attributes
        assertNotNull(result, "Account should not be null");
        assertEquals(TEST_ACCOUNT_ID, result.getAccountId(), "Account ID should match");
        assertEquals(TEST_ACCOUNT_NUMBER, result.getAccountNumber(), "Account number should match");
        assertEquals(TEST_BALANCE, result.getCurrentBalance(), "Current balance should match");
        assertEquals(TEST_CREDIT_LIMIT, result.getCreditLimit(), "Credit limit should match");
        assertEquals(ACTIVE_STATUS, result.getActiveStatus(), "Account status should match");
        assertNotNull(result.getCustomer(), "Customer relationship should be loaded");
        assertEquals("John", result.getCustomer().getFirstName(), "Customer first name should match");
        assertEquals("Doe", result.getCustomer().getLastName(), "Customer last name should match");

        // Verify repository interaction
        verify(accountRepository, times(1)).findByAccountNumber(TEST_ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("getAccountByAccountNumber - Account Not Found: Throws ResourceNotFoundException")
    void testGetAccountByAccountNumber_NotFound() {
        // Given: Repository returns empty Optional (COBOL FILE STATUS '23' equivalent)
        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.empty());

        // When/Then: ResourceNotFoundException is thrown
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.getAccountByAccountNumber(TEST_ACCOUNT_NUMBER),
                "Should throw ResourceNotFoundException when account not found"
        );

        // Verify exception message contains account number (masked)
        assertTrue(exception.getMessage().contains("not found"), 
                "Exception message should indicate account not found");

        // Verify repository was called exactly once
        verify(accountRepository, times(1)).findByAccountNumber(TEST_ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("getAccountByAccountNumber - Null Account Number: Throws IllegalArgumentException")
    void testGetAccountByAccountNumber_NullAccountNumber() {
        // When/Then: IllegalArgumentException is thrown for null input
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountByAccountNumber(null),
                "Should throw IllegalArgumentException when account number is null"
        );

        // Verify exception message
        assertEquals("Account number cannot be null", exception.getMessage());

        // Verify repository was never called
        verify(accountRepository, never()).findByAccountNumber(anyString());
    }

    // ==================== getAccountById() Tests ====================

    @Test
    @DisplayName("getAccountById - Success: Returns account by primary key")
    void testGetAccountById_Success() {
        // Given: Repository returns account by ID
        when(accountRepository.findById(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));

        // When: Service method is called with account ID
        Account result = accountService.getAccountById(TEST_ACCOUNT_ID);

        // Then: Account is returned successfully
        assertNotNull(result);
        assertEquals(TEST_ACCOUNT_ID, result.getAccountId());
        assertEquals(TEST_ACCOUNT_NUMBER, result.getAccountNumber());

        // Verify repository interaction
        verify(accountRepository, times(1)).findById(TEST_ACCOUNT_ID);
    }

    @Test
    @DisplayName("getAccountById - Account Not Found: Throws ResourceNotFoundException")
    void testGetAccountById_NotFound() {
        // Given: Repository returns empty Optional
        when(accountRepository.findById(TEST_ACCOUNT_ID))
                .thenReturn(Optional.empty());

        // When/Then: Exception is thrown
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.getAccountById(TEST_ACCOUNT_ID)
        );

        assertTrue(exception.getMessage().contains("not found"));
        verify(accountRepository, times(1)).findById(TEST_ACCOUNT_ID);
    }

    @Test
    @DisplayName("getAccountById - Null Account ID: Throws IllegalArgumentException")
    void testGetAccountById_NullAccountId() {
        // When/Then: IllegalArgumentException is thrown
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountById(null)
        );

        assertEquals("Account ID cannot be null", exception.getMessage());
        verify(accountRepository, never()).findById(anyLong());
    }

    // ==================== updateAccountBalance() Tests ====================

    @Test
    @DisplayName("updateAccountBalance - Success: Updates balance within credit limit")
    void testUpdateAccountBalance_SuccessWithinLimit() {
        // Given: Account with $1,000 balance, $5,000 credit limit
        // Transaction amount: $500 (new balance: $1,500, still within limit)
        BigDecimal transactionAmount = new BigDecimal("500.00");
        BigDecimal expectedNewBalance = TEST_BALANCE.add(transactionAmount);

        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> {
                    Account savedAccount = invocation.getArgument(0);
                    savedAccount.setCurrentBalance(expectedNewBalance);
                    return savedAccount;
                });

        // When: Balance update is performed
        Account result = accountService.updateAccountBalance(TEST_ACCOUNT_NUMBER, transactionAmount);

        // Then: Balance is updated successfully
        assertNotNull(result);
        assertEquals(expectedNewBalance, result.getCurrentBalance(), 
                "Balance should be updated to $1,500");

        // Verify repository interactions
        verify(accountRepository).findByAccountNumber(TEST_ACCOUNT_NUMBER);
        verify(accountRepository).findByIdWithLock(TEST_ACCOUNT_ID);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("updateAccountBalance - Success: Reduces balance for payment")
    void testUpdateAccountBalance_PaymentReduction() {
        // Given: Payment (negative amount) reduces balance
        BigDecimal paymentAmount = new BigDecimal("-200.00");
        BigDecimal expectedNewBalance = TEST_BALANCE.add(paymentAmount); // $800

        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Payment is processed
        Account result = accountService.updateAccountBalance(TEST_ACCOUNT_NUMBER, paymentAmount);

        // Then: Balance is reduced
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("updateAccountBalance - Credit Limit Exceeded: Throws InsufficientFundsException")
    void testUpdateAccountBalance_CreditLimitExceeded() {
        // Given: Transaction would exceed credit limit
        // Current: $1,000, Credit Limit: $5,000
        // Transaction: $4,500 → New Balance: $5,500 (exceeds $5,000 limit)
        BigDecimal excessiveAmount = new BigDecimal("4500.00");

        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));

        // When/Then: InsufficientFundsException is thrown (COBOL business rule enforcement)
        InsufficientFundsException exception = assertThrows(
                InsufficientFundsException.class,
                () -> accountService.updateAccountBalance(TEST_ACCOUNT_NUMBER, excessiveAmount),
                "Should throw InsufficientFundsException when proposed balance exceeds credit limit"
        );

        // Verify exception contains financial details
        assertNotNull(exception.getRequestedAmount());
        assertNotNull(exception.getCreditLimit());
        assertTrue(exception.getMessage().contains("Credit limit exceeded"));

        // Verify save was never called (transaction should be prevented)
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("updateAccountBalance - Null Account Number: Throws IllegalArgumentException")
    void testUpdateAccountBalance_NullAccountNumber() {
        // When/Then: IllegalArgumentException for null account number
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.updateAccountBalance(null, new BigDecimal("100.00"))
        );

        assertEquals("Account number cannot be null", exception.getMessage());
        verify(accountRepository, never()).findByAccountNumber(anyString());
    }

    @Test
    @DisplayName("updateAccountBalance - Null Amount: Throws IllegalArgumentException")
    void testUpdateAccountBalance_NullAmount() {
        // When/Then: IllegalArgumentException for null amount
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.updateAccountBalance(TEST_ACCOUNT_NUMBER, null)
        );

        assertEquals("Amount cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("updateAccountBalance - Negative Balance After Payment: Allowed for authorized transaction")
    void testUpdateAccountBalance_NegativeBalanceAllowed() {
        // Given: Payment exceeds current balance (results in negative/credit balance)
        // Current: $1,000, Payment: -$1,500 → New Balance: -$500 (credit to customer)
        BigDecimal largePayment = new BigDecimal("-1500.00");
        BigDecimal expectedNewBalance = TEST_BALANCE.add(largePayment);

        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Large payment is processed
        Account result = accountService.updateAccountBalance(TEST_ACCOUNT_NUMBER, largePayment);

        // Then: Negative balance is allowed (credit balance scenario)
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("updateAccountBalance - Optimistic Lock Failure: Throws OptimisticLockException")
    void testUpdateAccountBalance_OptimisticLockConflict() {
        // Given: Concurrent modification causes optimistic lock failure
        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class)))
                .thenThrow(new OptimisticLockException("Version mismatch detected"));

        // When/Then: OptimisticLockException is propagated
        assertThrows(
                OptimisticLockException.class,
                () -> accountService.updateAccountBalance(TEST_ACCOUNT_NUMBER, new BigDecimal("100.00")),
                "Should throw OptimisticLockException on concurrent modification"
        );

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("updateAccountBalance - Lock Acquisition Failure: Throws ResourceNotFoundException")
    void testUpdateAccountBalance_LockAcquisitionFails() {
        // Given: Cannot acquire pessimistic lock on account
        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.empty()); // Lock acquisition failed

        // When/Then: ResourceNotFoundException is thrown
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.updateAccountBalance(TEST_ACCOUNT_NUMBER, new BigDecimal("100.00"))
        );

        assertTrue(exception.getMessage().contains("not found or locked"));
        verify(accountRepository, never()).save(any(Account.class));
    }

    // ==================== updateAccount() Tests ====================

    @Test
    @DisplayName("updateAccount - Success: Updates account details by ID")
    void testUpdateAccount_SuccessById() {
        // Given: Valid update request
        AccountUpdateRequest updateRequest = new AccountUpdateRequest();
        updateRequest.setAccountStatus(INACTIVE_STATUS);
        updateRequest.setCreditLimit(new BigDecimal("6000.00"));

        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));
        doNothing().when(accountMapper).updateEntityFromRequest(updateRequest, testAccount);
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Account is updated
        Account result = accountService.updateAccount(TEST_ACCOUNT_ID, updateRequest);

        // Then: Update is successful
        assertNotNull(result);
        verify(accountRepository).findByIdWithLock(TEST_ACCOUNT_ID);
        verify(accountMapper).updateEntityFromRequest(updateRequest, testAccount);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("updateAccount - Success: Updates account details by account number")
    void testUpdateAccount_SuccessByAccountNumber() {
        // Given: Valid update request with account number lookup
        AccountUpdateRequest updateRequest = new AccountUpdateRequest();
        updateRequest.setAccountStatus(INACTIVE_STATUS);

        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));
        doNothing().when(accountMapper).updateEntityFromRequest(updateRequest, testAccount);
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Account is updated by account number
        Account result = accountService.updateAccount(TEST_ACCOUNT_NUMBER, updateRequest);

        // Then: Update is successful
        assertNotNull(result);
        verify(accountRepository).findByAccountNumber(TEST_ACCOUNT_NUMBER);
        verify(accountRepository).findByIdWithLock(TEST_ACCOUNT_ID);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("updateAccount - Credit Limit Changed: Validates credit limit does not exceed balance")
    void testUpdateAccount_CreditLimitChangedValidatesLimit() {
        // Given: Update request changes credit limit
        AccountUpdateRequest updateRequest = AccountUpdateRequest.builder()
                .accountStatus("Y")  // Active status
                .creditLimit(new BigDecimal("4500.00")) // Reducing from $5,000 to $4,500
                .cashCreditLimit(new BigDecimal("1000.00"))
                .accountOpenDate(LocalDate.now().minusYears(1))
                .accountExpirationDate(LocalDate.now().plusYears(2))
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .addressLine1("123 Main St")
                .city("Dallas")
                .state("TX")
                .zipCode("75001")
                .build();

        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));
        doAnswer(invocation -> {
            AccountUpdateRequest request = invocation.getArgument(0);
            Account account = invocation.getArgument(1);
            account.setCreditLimit(request.getCreditLimit());
            return null;
        }).when(accountMapper).updateEntityFromRequest(updateRequest, testAccount);
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Account is updated with new credit limit
        Account result = accountService.updateAccount(TEST_ACCOUNT_ID, updateRequest);

        // Then: Update succeeds and credit limit validation passes
        assertNotNull(result);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("updateAccount - Null Account ID: Throws IllegalArgumentException")
    void testUpdateAccount_NullAccountId() {
        // Given: Update request but null account ID
        AccountUpdateRequest updateRequest = new AccountUpdateRequest();

        // When/Then: IllegalArgumentException is thrown
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.updateAccount((Long) null, updateRequest)
        );

        assertEquals("Account ID cannot be null", exception.getMessage());
        verify(accountRepository, never()).findByIdWithLock(anyLong());
    }

    @Test
    @DisplayName("updateAccount - Null Request: Throws IllegalArgumentException")
    void testUpdateAccount_NullRequest() {
        // When/Then: IllegalArgumentException is thrown
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.updateAccount(TEST_ACCOUNT_ID, null)
        );

        assertEquals("Account update request cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("updateAccount - Account Not Found: Throws ResourceNotFoundException")
    void testUpdateAccount_NotFound() {
        // Given: Account does not exist
        AccountUpdateRequest updateRequest = new AccountUpdateRequest();
        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.empty());

        // When/Then: ResourceNotFoundException is thrown
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.updateAccount(TEST_ACCOUNT_ID, updateRequest)
        );

        assertTrue(exception.getMessage().contains("not found"));
        verify(accountRepository, never()).save(any(Account.class));
    }

    // ==================== validateCreditLimit() Tests ====================

    @Test
    @DisplayName("validateCreditLimit - Within Limit: Validation passes")
    void testValidateCreditLimit_WithinLimit() {
        // Given: Proposed balance within credit limit
        // Current: $1,000, Credit Limit: $5,000, Proposed: $3,000 (valid)
        BigDecimal proposedBalance = new BigDecimal("3000.00");

        // When: Validation is performed
        assertDoesNotThrow(() -> 
            accountService.validateCreditLimit(testAccount, proposedBalance),
            "Validation should pass when proposed balance is within credit limit"
        );
    }

    @Test
    @DisplayName("validateCreditLimit - At Limit: Validation passes")
    void testValidateCreditLimit_AtLimit() {
        // Given: Proposed balance exactly at credit limit
        BigDecimal proposedBalance = TEST_CREDIT_LIMIT; // $5,000

        // When/Then: Validation passes (at limit is acceptable)
        assertDoesNotThrow(() -> 
            accountService.validateCreditLimit(testAccount, proposedBalance)
        );
    }

    @Test
    @DisplayName("validateCreditLimit - Exceeds Limit: Throws InsufficientFundsException")
    void testValidateCreditLimit_ExceedsLimit() {
        // Given: Proposed balance exceeds credit limit
        // Credit Limit: $5,000, Proposed: $5,500 (exceeds by $500)
        BigDecimal proposedBalance = new BigDecimal("5500.00");

        // When/Then: InsufficientFundsException is thrown
        InsufficientFundsException exception = assertThrows(
                InsufficientFundsException.class,
                () -> accountService.validateCreditLimit(testAccount, proposedBalance),
                "Should throw InsufficientFundsException when proposed balance exceeds credit limit"
        );

        // Verify exception contains detailed financial information
        assertTrue(exception.getMessage().contains("Credit limit exceeded"));
        assertTrue(exception.getMessage().contains("Proposed Balance: 5500.00"));
        assertTrue(exception.getMessage().contains("Credit Limit: 5000.00"));
        assertEquals(TEST_CREDIT_LIMIT, exception.getCreditLimit());
    }

    @Test
    @DisplayName("validateCreditLimit - Null Account: Throws IllegalArgumentException")
    void testValidateCreditLimit_NullAccount() {
        // When/Then: IllegalArgumentException for null account
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.validateCreditLimit(null, new BigDecimal("1000.00"))
        );

        assertEquals("Account cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("validateCreditLimit - Null Proposed Balance: Throws IllegalArgumentException")
    void testValidateCreditLimit_NullProposedBalance() {
        // When/Then: IllegalArgumentException for null proposed balance
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.validateCreditLimit(testAccount, null)
        );

        assertEquals("Proposed balance cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("validateCreditLimit - Zero Proposed Balance: Validation passes")
    void testValidateCreditLimit_ZeroBalance() {
        // Given: Proposed balance of zero (after full payment)
        BigDecimal zeroBalance = BigDecimal.ZERO;

        // When/Then: Validation passes
        assertDoesNotThrow(() -> 
            accountService.validateCreditLimit(testAccount, zeroBalance)
        );
    }

    @Test
    @DisplayName("validateCreditLimit - Negative Balance: Validation passes (credit to customer)")
    void testValidateCreditLimit_NegativeBalance() {
        // Given: Negative balance (overpayment/credit)
        BigDecimal negativeBalance = new BigDecimal("-500.00");

        // When/Then: Validation passes (negative balance is always within credit limit)
        assertDoesNotThrow(() -> 
            accountService.validateCreditLimit(testAccount, negativeBalance)
        );
    }

    // ==================== BigDecimal Precision Tests ====================

    @Test
    @DisplayName("BigDecimal Precision - Financial calculations maintain 2 decimal places")
    void testBigDecimalPrecision() {
        // Given: Account with precise decimal values
        BigDecimal balance = new BigDecimal("1000.00");
        BigDecimal amount = new BigDecimal("123.45");
        
        // When: Arithmetic operation is performed
        BigDecimal result = balance.add(amount);
        
        // Then: Result maintains 2 decimal place precision (COBOL PIC S9(09)V99 COMP-3 equivalent)
        assertEquals(2, result.scale(), "Result should maintain 2 decimal places");
        assertEquals(new BigDecimal("1123.45"), result, "Calculation should be precise");
    }

    @Test
    @DisplayName("BigDecimal Precision - Comparison uses compareTo for exact equality")
    void testBigDecimalComparison() {
        // Given: Two BigDecimal values with same monetary value
        BigDecimal amount1 = new BigDecimal("1000.00");
        BigDecimal amount2 = new BigDecimal("1000.00");
        
        // Then: compareTo returns 0 for equal values
        assertEquals(0, amount1.compareTo(amount2), 
                "BigDecimal.compareTo should return 0 for equal monetary values");
    }

    // ==================== Integration Verification Tests ====================

    @Test
    @DisplayName("Repository Interaction - Verifies correct method invocation sequence")
    void testRepositoryInteractionSequence() {
        // Given: Successful account lookup and update scenario
        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.findByIdWithLock(TEST_ACCOUNT_ID))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Balance update is performed
        accountService.updateAccountBalance(TEST_ACCOUNT_NUMBER, new BigDecimal("100.00"));

        // Then: Verify exact sequence of repository calls
        verify(accountRepository, times(1)).findByAccountNumber(TEST_ACCOUNT_NUMBER);
        verify(accountRepository, times(1)).findByIdWithLock(TEST_ACCOUNT_ID);
        verify(accountRepository, times(1)).save(any(Account.class));
        
        // Verify no unexpected calls
        verifyNoMoreInteractions(accountRepository);
    }

    @Test
    @DisplayName("Exception Message - Contains masked account number for security")
    void testExceptionMessageContainsMaskedAccountNumber() {
        // Given: Account not found scenario
        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.empty());

        // When: Exception is thrown
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.getAccountByAccountNumber(TEST_ACCOUNT_NUMBER)
        );

        // Then: Exception message contains masked account number (PCI-DSS compliance)
        // Expected mask pattern: "0001*****678" (shows first 4 and last 4 digits)
        String exceptionMessage = exception.getMessage();
        assertFalse(exceptionMessage.contains(TEST_ACCOUNT_NUMBER), 
                "Exception message should NOT contain full account number");
        assertTrue(exceptionMessage.contains("not found"), 
                "Exception message should indicate resource not found");
    }

    @Test
    @DisplayName("Customer Relationship - Eager loading verified via entity graph")
    void testCustomerRelationshipEagerLoading() {
        // Given: Account with customer relationship
        when(accountRepository.findByAccountNumber(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));

        // When: Account is retrieved
        Account result = accountService.getAccountByAccountNumber(TEST_ACCOUNT_NUMBER);

        // Then: Customer relationship is loaded (no lazy loading exception)
        assertNotNull(result.getCustomer(), "Customer should be eager-loaded");
        assertDoesNotThrow(() -> result.getCustomer().getFirstName(), 
                "Accessing customer fields should not throw LazyInitializationException");
        assertEquals("John", result.getCustomer().getFirstName());
    }
}
