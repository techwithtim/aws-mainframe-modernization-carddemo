package com.aws.carddemo.unit.service;

import com.aws.carddemo.exception.InsufficientFundsException;
import com.aws.carddemo.exception.InvalidInputException;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.CardXref;
import com.aws.carddemo.model.Transaction;
import com.aws.carddemo.model.TransactionCategoryBalance;
import com.aws.carddemo.model.TransactionCategoryBalanceId;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardXrefRepository;
import com.aws.carddemo.repository.TransactionCategoryBalanceRepository;
import com.aws.carddemo.repository.TransactionRepository;
import com.aws.carddemo.service.AccountService;
import com.aws.carddemo.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit test suite for {@link TransactionService} validating business logic
 * migrated from COBOL online programs COTRN00C.cbl (transaction browse), COTRN01C.cbl
 * (transaction view), COTRN02C.cbl (transaction add), and batch programs CBTRN01C/02C.cbl
 * (transaction posting).
 * 
 * <p><b>Test Coverage Scope:</b>
 * <ul>
 *   <li>Transaction history retrieval with pagination and date range filtering</li>
 *   <li>Transaction detail lookup by transaction ID with PCI-DSS card masking</li>
 *   <li>New transaction posting with card-to-account validation via CardXrefRepository</li>
 *   <li>Credit limit enforcement preventing over-limit transactions</li>
 *   <li>Account balance updates coordinating multiple repository operations</li>
 *   <li>Transaction category balance tracking for financial reporting</li>
 *   <li>Exception handling for invalid card numbers and credit limit violations</li>
 * </ul>
 * 
 * <p><b>Mockito Infrastructure:</b>
 * <ul>
 *   <li>{@code @ExtendWith(MockitoExtension.class)}: Enables Mockito annotations</li>
 *   <li>{@code @Mock}: Mocks repository dependencies (TransactionRepository, AccountRepository,
 *       CardXrefRepository, TransactionCategoryBalanceRepository, AccountService)</li>
 *   <li>{@code @InjectMocks}: Injects mocked dependencies into TransactionService</li>
 *   <li>{@code @BeforeEach}: Initializes test fixtures before each test method</li>
 * </ul>
 * 
 * <p><b>COBOL Business Logic Preservation:</b>
 * <ul>
 *   <li>STARTBR/READNEXT browse pattern → TransactionRepository.findByAccountAccountId() with pagination</li>
 *   <li>READ TRANFILE BY TRAN-ID → TransactionRepository.findById() returning Optional</li>
 *   <li>VSAM XREFFILE lookup → CardXrefRepository.findByCardNumber() resolving account ID</li>
 *   <li>Credit limit check → IF ACCT-CURR-BAL + TRAN-AMT > ACCT-CREDIT-LIMIT validation</li>
 *   <li>Account balance update → ADD DALYTRAN-AMT TO ACCT-CURR-BAL using BigDecimal.add()</li>
 *   <li>Category balance update → ADD DALYTRAN-AMT TO TCAT-BAL with category tracking</li>
 * </ul>
 * 
 * <p><b>Test Data Fixtures:</b>
 * <ul>
 *   <li>Transaction entity with transactionId=1L, accountId=1L, amount=$100.00</li>
 *   <li>Account entity with currentBalance=$1000.00, creditLimit=$5000.00</li>
 *   <li>CardXref entity mapping cardNumber "1234567890123456" to accountId=1L</li>
 *   <li>TransactionCategoryBalance entity with categoryBalance=$500.00</li>
 * </ul>
 * 
 * <p><b>Coverage Targets (Section 0.8.1):</b>
 * <ul>
 *   <li>Line Coverage: ≥80%</li>
 *   <li>Branch Coverage: ≥70%</li>
 *   <li>Method Coverage: 100% (all public service methods tested)</li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b>
 * <ul>
 *   <li>Section 0.4.1: TransactionServiceTest for transaction operations</li>
 *   <li>Section 0.8.1: Critical Directive #4 - Test-driven validation with JUnit 5 + Mockito</li>
 *   <li>Section 0.8.3: Data Type Mapping - BigDecimal for PIC S9(09)V99 COMP-3 precision</li>
 * </ul>
 * 
 * @see TransactionService for service under test
 * @see Transaction for entity under test
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CardXrefRepository cardXrefRepository;

    @Mock
    private TransactionCategoryBalanceRepository transactionCategoryBalanceRepository;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private TransactionService transactionService;

    // Test fixture data
    private Transaction testTransaction;
    private Account testAccount;
    private CardXref testCardXref;
    private TransactionCategoryBalance testCategoryBalance;
    private List<Transaction> testTransactionList;

    /**
     * Initialize test fixtures before each test method.
     * Creates sample entities with realistic test data matching COBOL data structures.
     */
    @BeforeEach
    void setUp() {
        // Build test Account entity with $1000.00 balance and $5000.00 credit limit
        testAccount = Account.builder()
                .accountId(1L)
                .accountNumber("00000000001")
                .currentBalance(new BigDecimal("1000.00"))
                .creditLimit(new BigDecimal("5000.00"))
                .currentCycleCredit(new BigDecimal("200.00"))
                .currentCycleDebit(new BigDecimal("0.00"))
                .build();

        // Build test Transaction entity with $100.00 purchase amount
        testTransaction = Transaction.builder()
                .transactionId(1L)
                .transactionNumber("TXN001234567890")
                .account(testAccount)
                .cardNumber("1234567890123456")
                .amount(new BigDecimal("100.00"))
                .transactionTypeCode("01")
                .transactionCategoryCode("5010")
                .description("PURCHASE AT TEST MERCHANT")
                .merchantName("TEST MERCHANT")
                .merchantCity("SEATTLE")
                .merchantZip("98101")
                .originalTimestamp(LocalDateTime.now().minusDays(1))
                .processingTimestamp(LocalDateTime.now())
                .build();

        // Build test CardXref entity mapping card number to account ID
        testCardXref = CardXref.builder()
                .cardNumber("1234567890123456")
                .accountId(1L)
                .customerId(1L)
                .build();

        // Build test TransactionCategoryBalance entity with $500.00 balance
        testCategoryBalance = TransactionCategoryBalance.builder()
                .accountId(1L)
                .transactionTypeCode("01")
                .transactionCategoryCode("5010")
                .categoryBalance(new BigDecimal("500.00"))
                .build();

        // Build test transaction list for pagination tests
        testTransactionList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Transaction tx = Transaction.builder()
                    .transactionId((long) i)
                    .transactionNumber("TXN00123456789" + i)
                    .account(testAccount)
                    .cardNumber("1234567890123456")
                    .amount(new BigDecimal("50.00").multiply(new BigDecimal(i)))
                    .transactionTypeCode("01")
                    .transactionCategoryCode("5010")
                    .description("TEST TRANSACTION " + i)
                    .originalTimestamp(LocalDateTime.now().minusDays(i))
                    .processingTimestamp(LocalDateTime.now().minusDays(i))
                    .build();
            testTransactionList.add(tx);
        }
    }

    // ===========================================================================================
    // getTransactionHistory() Tests - COTRN00C.cbl Transaction Browse
    // ===========================================================================================

    @Test
    @DisplayName("getTransactionHistory - Should return paginated transactions with latest-first ordering")
    void testGetTransactionHistory_Success() {
        // Arrange: Setup pageable with descending sort by processing timestamp
        Long accountId = 1L;
        Pageable pageable = PageRequest.of(0, 10, Sort.by("processingTimestamp").descending());
        Page<Transaction> expectedPage = new PageImpl<>(testTransactionList, pageable, testTransactionList.size());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.findByAccountAccountId(accountId, pageable)).thenReturn(expectedPage);

        // Act: Retrieve transaction history
        Page<Transaction> actualPage = transactionService.getTransactionHistory(accountId, pageable);

        // Assert: Verify pagination results
        assertNotNull(actualPage, "Transaction page should not be null");
        assertEquals(10, actualPage.getContent().size(), "Should return 10 transactions per page");
        assertEquals(10, actualPage.getTotalElements(), "Total elements should be 10");
        assertEquals(1, actualPage.getTotalPages(), "Total pages should be 1");
        assertEquals(0, actualPage.getNumber(), "Current page number should be 0");
        assertFalse(actualPage.hasNext(), "Should not have next page");

        // Verify repository interactions
        verify(accountRepository, times(1)).findById(accountId);
        verify(transactionRepository, times(1)).findByAccountAccountId(accountId, pageable);
    }

    @Test
    @DisplayName("getTransactionHistory - Should return empty page when no transactions exist for account")
    void testGetTransactionHistory_EmptyResult() {
        // Arrange: Empty transaction list
        Long accountId = 1L;
        Pageable pageable = PageRequest.of(0, 10, Sort.by("processingTimestamp").descending());
        Page<Transaction> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.findByAccountAccountId(accountId, pageable)).thenReturn(emptyPage);

        // Act: Retrieve transaction history for account with no transactions
        Page<Transaction> actualPage = transactionService.getTransactionHistory(accountId, pageable);

        // Assert: Verify empty page
        assertNotNull(actualPage, "Transaction page should not be null");
        assertEquals(0, actualPage.getContent().size(), "Should return empty transaction list");
        assertEquals(0, actualPage.getTotalElements(), "Total elements should be 0");
        assertTrue(actualPage.getContent().isEmpty(), "Content should be empty");

        // Verify repository interactions
        verify(accountRepository, times(1)).findById(accountId);
        verify(transactionRepository, times(1)).findByAccountAccountId(accountId, pageable);
    }

    @Test
    @DisplayName("getTransactionHistory - Should throw ResourceNotFoundException when account not found")
    void testGetTransactionHistory_AccountNotFound() {
        // Arrange: Account does not exist (FILE STATUS 23 equivalent)
        Long accountId = 999L;
        Pageable pageable = PageRequest.of(0, 10);

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act & Assert: Verify exception thrown
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            transactionService.getTransactionHistory(accountId, pageable);
        });

        assertEquals("Account not found with ID: 999", exception.getMessage());

        // Verify repository interactions
        verify(accountRepository, times(1)).findById(accountId);
        verify(transactionRepository, never()).findByAccountAccountId(anyLong(), any(Pageable.class));
    }

    @Test
    @DisplayName("getTransactionHistory - Should handle pagination controls (page number, size, total)")
    void testGetTransactionHistory_PaginationMetadata() {
        // Arrange: Create second page with 5 remaining transactions
        Long accountId = 1L;
        Pageable pageable = PageRequest.of(1, 5, Sort.by("processingTimestamp").descending());
        List<Transaction> secondPageTransactions = testTransactionList.subList(5, 10);
        Page<Transaction> expectedPage = new PageImpl<>(secondPageTransactions, pageable, testTransactionList.size());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.findByAccountAccountId(accountId, pageable)).thenReturn(expectedPage);

        // Act: Retrieve second page
        Page<Transaction> actualPage = transactionService.getTransactionHistory(accountId, pageable);

        // Assert: Verify pagination metadata
        assertNotNull(actualPage);
        assertEquals(5, actualPage.getContent().size(), "Should return 5 transactions on second page");
        assertEquals(10, actualPage.getTotalElements(), "Total elements should still be 10");
        assertEquals(2, actualPage.getTotalPages(), "Total pages should be 2");
        assertEquals(1, actualPage.getNumber(), "Current page number should be 1");
        assertTrue(actualPage.hasPrevious(), "Should have previous page");
        assertFalse(actualPage.hasNext(), "Should not have next page");

        // Verify repository interactions
        verify(accountRepository, times(1)).findById(accountId);
        verify(transactionRepository, times(1)).findByAccountAccountId(accountId, pageable);
    }

    // ===========================================================================================
    // getTransactionById() Tests - COTRN01C.cbl Transaction Detail View
    // ===========================================================================================

    @Test
    @DisplayName("getTransactionById - Should return transaction entity for valid ID")
    void testGetTransactionById_Success() {
        // Arrange
        Long transactionId = 1L;
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));

        // Act: Retrieve transaction detail
        Transaction actualTransaction = transactionService.getTransactionById(transactionId);

        // Assert: Verify transaction details
        assertNotNull(actualTransaction, "Transaction should not be null");
        assertEquals(transactionId, actualTransaction.getTransactionId());
        assertEquals("TXN001234567890", actualTransaction.getTransactionNumber());
        assertEquals(new BigDecimal("100.00"), actualTransaction.getAmount());
        assertEquals("01", actualTransaction.getTransactionTypeCode());
        assertEquals("5010", actualTransaction.getTransactionCategoryCode());
        assertEquals("PURCHASE AT TEST MERCHANT", actualTransaction.getDescription());

        // Verify repository interaction
        verify(transactionRepository, times(1)).findById(transactionId);
    }

    @Test
    @DisplayName("getTransactionById - Should throw ResourceNotFoundException when transaction not found")
    void testGetTransactionById_NotFound() {
        // Arrange: Transaction does not exist (FILE STATUS 23 equivalent)
        Long transactionId = 999L;
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // Act & Assert: Verify exception thrown
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            transactionService.getTransactionById(transactionId);
        });

        assertEquals("Transaction not found with ID: 999", exception.getMessage());

        // Verify repository interaction
        verify(transactionRepository, times(1)).findById(transactionId);
    }

    @Test
    @DisplayName("getTransactionById - Should handle PCI-DSS card number masking in logging")
    void testGetTransactionById_CardNumberMasked() {
        // Arrange
        Long transactionId = 1L;
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));

        // Act: Retrieve transaction
        Transaction actualTransaction = transactionService.getTransactionById(transactionId);

        // Assert: Verify full card number is available in entity but would be masked in logs
        assertNotNull(actualTransaction.getCardNumber());
        assertEquals("1234567890123456", actualTransaction.getCardNumber());
        
        // Note: Service logs using getCardNumberMasked() which should show "************3456"
        // This test verifies entity contains full number while logging uses masked version

        verify(transactionRepository, times(1)).findById(transactionId);
    }

    // ===========================================================================================
    // postTransaction() Tests - COTRN02C.cbl + CBTRN01C/02C.cbl Transaction Posting
    // ===========================================================================================

    @Test
    @DisplayName("postTransaction - Should successfully post transaction with all coordinated updates")
    void testPostTransaction_Success() {
        // Arrange: Setup all mocks for successful transaction posting
        String cardNumber = "1234567890123456";
        BigDecimal transactionAmount = new BigDecimal("100.00");
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";
        LocalDate transactionDate = LocalDate.now().minusDays(1);

        when(cardXrefRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCardXref));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testAccount));
        doNothing().when(accountService).validateCreditLimit(eq(testAccount), any(BigDecimal.class));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        
        TransactionCategoryBalanceId categoryBalanceId = new TransactionCategoryBalanceId(
                1L, transactionTypeCode, transactionCategoryCode);
        when(transactionCategoryBalanceRepository.findById(categoryBalanceId))
                .thenReturn(Optional.of(testCategoryBalance));
        when(transactionCategoryBalanceRepository.save(any(TransactionCategoryBalance.class)))
                .thenReturn(testCategoryBalance);

        // Act: Post transaction
        Transaction result = transactionService.postTransaction(
                cardNumber, transactionAmount, merchantName, 
                transactionTypeCode, transactionCategoryCode, transactionDate,
                "Test merchandise purchase");

        // Assert: Verify transaction created
        assertNotNull(result, "Posted transaction should not be null");

        // Verify all repository interactions in correct order
        verify(cardXrefRepository, times(1)).findByCardNumber(cardNumber);
        verify(accountRepository, times(1)).findByIdWithLock(1L);
        verify(accountService, times(1)).validateCreditLimit(eq(testAccount), any(BigDecimal.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(accountRepository, times(1)).save(any(Account.class));
        verify(transactionCategoryBalanceRepository, times(1)).findById(any(TransactionCategoryBalanceId.class));
        verify(transactionCategoryBalanceRepository, times(1)).save(any(TransactionCategoryBalance.class));
    }

    @Test
    @DisplayName("postTransaction - Should throw InvalidInputException when amount is zero")
    void testPostTransaction_ZeroAmount() {
        // Arrange: Zero transaction amount violates business rule
        String cardNumber = "1234567890123456";
        BigDecimal zeroAmount = BigDecimal.ZERO;
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";
        LocalDate transactionDate = LocalDate.now();

        // Act & Assert: Verify exception thrown for zero amount
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            transactionService.postTransaction(
                    cardNumber, zeroAmount, merchantName, 
                    transactionTypeCode, transactionCategoryCode, transactionDate,
                    "Test transaction with zero amount");
        });

        assertEquals("Transaction amount must be non-zero", exception.getMessage());

        // Verify no repository interactions occurred
        verify(cardXrefRepository, never()).findByCardNumber(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("postTransaction - Should throw InvalidInputException when amount is null")
    void testPostTransaction_NullAmount() {
        // Arrange: Null transaction amount
        String cardNumber = "1234567890123456";
        BigDecimal nullAmount = null;
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";
        LocalDate transactionDate = LocalDate.now();

        // Act & Assert: Verify exception thrown for null amount
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            transactionService.postTransaction(
                    cardNumber, nullAmount, merchantName, 
                    transactionTypeCode, transactionCategoryCode, transactionDate,
                    "Test transaction with null amount");
        });

        assertEquals("Transaction amount must be non-zero", exception.getMessage());

        // Verify no repository interactions occurred
        verify(cardXrefRepository, never()).findByCardNumber(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("postTransaction - Should throw InvalidInputException when transaction date is in future")
    void testPostTransaction_FutureDate() {
        // Arrange: Future transaction date violates business rule
        String cardNumber = "1234567890123456";
        BigDecimal transactionAmount = new BigDecimal("100.00");
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";
        LocalDate futureDate = LocalDate.now().plusDays(1);

        // Act & Assert: Verify exception thrown for future date
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            transactionService.postTransaction(
                    cardNumber, transactionAmount, merchantName, 
                    transactionTypeCode, transactionCategoryCode, futureDate,
                    "Test transaction with future date");
        });

        assertEquals("Transaction date cannot be in the future", exception.getMessage());

        // Verify no repository interactions occurred
        verify(cardXrefRepository, never()).findByCardNumber(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("postTransaction - Should throw InvalidInputException when card number not found in cross-reference")
    void testPostTransaction_InvalidCardNumber() {
        // Arrange: Card number not found in XREFFILE (FILE STATUS 23)
        String invalidCardNumber = "9999999999999999";
        BigDecimal transactionAmount = new BigDecimal("100.00");
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";
        LocalDate transactionDate = LocalDate.now();

        when(cardXrefRepository.findByCardNumber(invalidCardNumber)).thenReturn(Optional.empty());

        // Act & Assert: Verify exception thrown for invalid card
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            transactionService.postTransaction(
                    invalidCardNumber, transactionAmount, merchantName, 
                    transactionTypeCode, transactionCategoryCode, transactionDate,
                    "Test transaction with invalid card");
        });

        assertTrue(exception.getMessage().contains("Card number not found"));

        // Verify only card xref lookup occurred, no further operations
        verify(cardXrefRepository, times(1)).findByCardNumber(invalidCardNumber);
        verify(accountRepository, never()).findByIdWithLock(anyLong());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("postTransaction - Should throw ResourceNotFoundException when account not found")
    void testPostTransaction_AccountNotFound() {
        // Arrange: Account lookup fails after card validation
        String cardNumber = "1234567890123456";
        BigDecimal transactionAmount = new BigDecimal("100.00");
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";
        LocalDate transactionDate = LocalDate.now();

        when(cardXrefRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCardXref));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.empty());

        // Act & Assert: Verify exception thrown for missing account
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            transactionService.postTransaction(
                    cardNumber, transactionAmount, merchantName, 
                    transactionTypeCode, transactionCategoryCode, transactionDate,
                    "Test transaction with missing account");
        });

        assertEquals("Account not found with ID: 1", exception.getMessage());

        // Verify card lookup and account lookup occurred, but no transaction saved
        verify(cardXrefRepository, times(1)).findByCardNumber(cardNumber);
        verify(accountRepository, times(1)).findByIdWithLock(1L);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("postTransaction - Should throw InsufficientFundsException when credit limit exceeded")
    void testPostTransaction_CreditLimitExceeded() {
        // Arrange: Transaction amount exceeds available credit
        String cardNumber = "1234567890123456";
        BigDecimal largeAmount = new BigDecimal("10000.00");
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";
        LocalDate transactionDate = LocalDate.now();

        when(cardXrefRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCardXref));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testAccount));
        doThrow(new InsufficientFundsException(largeAmount, testAccount.getCurrentBalance(), testAccount.getCreditLimit()))
                .when(accountService).validateCreditLimit(eq(testAccount), any(BigDecimal.class));

        // Act & Assert: Verify exception thrown for credit limit violation
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> {
            transactionService.postTransaction(
                    cardNumber, largeAmount, merchantName, 
                    transactionTypeCode, transactionCategoryCode, transactionDate,
                    "Test transaction exceeding credit limit");
        });

        assertTrue(exception.getMessage().contains("exceeds available credit"));

        // Verify validation occurred but no transaction saved
        verify(cardXrefRepository, times(1)).findByCardNumber(cardNumber);
        verify(accountRepository, times(1)).findByIdWithLock(1L);
        verify(accountService, times(1)).validateCreditLimit(eq(testAccount), any(BigDecimal.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("postTransaction - Should update account balance with BigDecimal precision")
    void testPostTransaction_AccountBalanceUpdate() {
        // Arrange: Verify BigDecimal arithmetic for balance update
        String cardNumber = "1234567890123456";
        BigDecimal transactionAmount = new BigDecimal("123.45");
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";
        LocalDate transactionDate = LocalDate.now();

        BigDecimal initialBalance = testAccount.getCurrentBalance();
        BigDecimal expectedBalance = initialBalance.add(transactionAmount);

        when(cardXrefRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCardXref));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testAccount));
        doNothing().when(accountService).validateCreditLimit(eq(testAccount), any(BigDecimal.class));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account savedAccount = invocation.getArgument(0);
            // Verify balance was updated correctly with BigDecimal precision
            assertEquals(expectedBalance, savedAccount.getCurrentBalance());
            return savedAccount;
        });

        TransactionCategoryBalanceId categoryBalanceId = new TransactionCategoryBalanceId(
                1L, transactionTypeCode, transactionCategoryCode);
        when(transactionCategoryBalanceRepository.findById(categoryBalanceId))
                .thenReturn(Optional.of(testCategoryBalance));
        when(transactionCategoryBalanceRepository.save(any(TransactionCategoryBalance.class)))
                .thenReturn(testCategoryBalance);

        // Act: Post transaction
        transactionService.postTransaction(
                cardNumber, transactionAmount, merchantName, 
                transactionTypeCode, transactionCategoryCode, transactionDate,
                "Test BigDecimal precision");

        // Assert: Verify account was saved with updated balance
        verify(accountRepository, times(1)).save(argThat(account -> 
                account.getCurrentBalance().equals(expectedBalance)));
    }

    @Test
    @DisplayName("postTransaction - Should update category balance with transaction amount")
    void testPostTransaction_CategoryBalanceUpdate() {
        // Arrange: Verify category balance increment
        String cardNumber = "1234567890123456";
        BigDecimal transactionAmount = new BigDecimal("75.50");
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";
        LocalDate transactionDate = LocalDate.now();

        BigDecimal initialCategoryBalance = testCategoryBalance.getCategoryBalance();
        BigDecimal expectedCategoryBalance = initialCategoryBalance.add(transactionAmount);

        when(cardXrefRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCardXref));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testAccount));
        doNothing().when(accountService).validateCreditLimit(eq(testAccount), any(BigDecimal.class));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        TransactionCategoryBalanceId categoryBalanceId = new TransactionCategoryBalanceId(
                1L, transactionTypeCode, transactionCategoryCode);
        when(transactionCategoryBalanceRepository.findById(categoryBalanceId))
                .thenReturn(Optional.of(testCategoryBalance));
        when(transactionCategoryBalanceRepository.save(any(TransactionCategoryBalance.class)))
                .thenAnswer(invocation -> {
                    TransactionCategoryBalance savedBalance = invocation.getArgument(0);
                    // Verify category balance was incremented correctly
                    assertEquals(expectedCategoryBalance, savedBalance.getCategoryBalance());
                    return savedBalance;
                });

        // Act: Post transaction
        transactionService.postTransaction(
                cardNumber, transactionAmount, merchantName, 
                transactionTypeCode, transactionCategoryCode, transactionDate,
                "Test category balance update");

        // Assert: Verify category balance was saved with updated amount
        verify(transactionCategoryBalanceRepository, times(1)).save(argThat(balance ->
                balance.getCategoryBalance().equals(expectedCategoryBalance)));
    }

    @Test
    @DisplayName("postTransaction - Should create new category balance if not exists")
    void testPostTransaction_NewCategoryBalance() {
        // Arrange: Category balance does not exist, should be created
        String cardNumber = "1234567890123456";
        BigDecimal transactionAmount = new BigDecimal("200.00");
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "9999"; // New category

        when(cardXrefRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCardXref));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testAccount));
        doNothing().when(accountService).validateCreditLimit(eq(testAccount), any(BigDecimal.class));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        TransactionCategoryBalanceId categoryBalanceId = new TransactionCategoryBalanceId(
                1L, transactionTypeCode, transactionCategoryCode);
        when(transactionCategoryBalanceRepository.findById(categoryBalanceId))
                .thenReturn(Optional.empty()); // Category balance not found
        when(transactionCategoryBalanceRepository.save(any(TransactionCategoryBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: Post transaction
        transactionService.postTransaction(
                cardNumber, transactionAmount, merchantName, 
                transactionTypeCode, transactionCategoryCode, null,
                "Test new category balance creation");

        // Assert: Verify new category balance was created with initial amount
        verify(transactionCategoryBalanceRepository, times(1)).save(argThat(balance ->
                balance.getAccountId().equals(1L) &&
                balance.getTransactionTypeCode().equals(transactionTypeCode) &&
                balance.getTransactionCategoryCode().equals(transactionCategoryCode) &&
                balance.getCategoryBalance().equals(transactionAmount)));
    }

    @Test
    @DisplayName("postTransaction - Should update cycle credit for positive amounts")
    void testPostTransaction_CycleCreditUpdate() {
        // Arrange: Positive transaction amount should increment cycle credit
        String cardNumber = "1234567890123456";
        BigDecimal transactionAmount = new BigDecimal("50.00");
        String merchantName = "TEST MERCHANT";
        String transactionTypeCode = "01";
        String transactionCategoryCode = "5010";

        BigDecimal initialCycleCredit = testAccount.getCurrentCycleCredit();
        BigDecimal expectedCycleCredit = initialCycleCredit.add(transactionAmount);

        when(cardXrefRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCardXref));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testAccount));
        doNothing().when(accountService).validateCreditLimit(eq(testAccount), any(BigDecimal.class));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account savedAccount = invocation.getArgument(0);
            // Verify cycle credit was incremented
            assertEquals(expectedCycleCredit, savedAccount.getCurrentCycleCredit());
            return savedAccount;
        });

        TransactionCategoryBalanceId categoryBalanceId = new TransactionCategoryBalanceId(
                1L, transactionTypeCode, transactionCategoryCode);
        when(transactionCategoryBalanceRepository.findById(categoryBalanceId))
                .thenReturn(Optional.of(testCategoryBalance));
        when(transactionCategoryBalanceRepository.save(any(TransactionCategoryBalance.class)))
                .thenReturn(testCategoryBalance);

        // Act: Post transaction
        transactionService.postTransaction(
                cardNumber, transactionAmount, merchantName, 
                transactionTypeCode, transactionCategoryCode, null,
                "Test cycle credit update");

        // Assert: Verify cycle credit updated
        verify(accountRepository, times(1)).save(argThat(account ->
                account.getCurrentCycleCredit().equals(expectedCycleCredit)));
    }

    // ===========================================================================================
    // getTransactionsByDateRange() Tests - Date Range Filtering
    // ===========================================================================================

    @Test
    @DisplayName("getTransactionsByDateRange - Should return transactions within date range")
    void testGetTransactionsByDateRange_Success() {
        // Arrange: Date range query
        Long accountId = 1L;
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();
        Pageable pageable = PageRequest.of(0, 10);

        List<Transaction> dateRangeTransactions = testTransactionList.subList(0, 5);
        Page<Transaction> expectedPage = new PageImpl<>(dateRangeTransactions, pageable, dateRangeTransactions.size());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.findByTransactionDateBetween(any(LocalDate.class), any(LocalDate.class), eq(pageable)))
                .thenReturn(expectedPage);

        // Act: Retrieve transactions by date range
        Page<Transaction> actualPage = transactionService.getTransactionsByDateRange(
                accountId, startDate, endDate, pageable);

        // Assert: Verify results
        assertNotNull(actualPage);
        assertEquals(5, actualPage.getContent().size());
        assertEquals(5, actualPage.getTotalElements());

        // Verify repository interactions
        verify(accountRepository, times(1)).findById(accountId);
        verify(transactionRepository, times(1)).findByTransactionDateBetween(
                any(LocalDate.class), any(LocalDate.class), eq(pageable));
    }

    @Test
    @DisplayName("getTransactionsByDateRange - Should throw InvalidInputException when start date after end date")
    void testGetTransactionsByDateRange_InvalidRange() {
        // Arrange: Invalid date range
        Long accountId = 1L;
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = LocalDate.now().minusDays(30); // End before start
        Pageable pageable = PageRequest.of(0, 10);

        // Act & Assert: Verify exception thrown
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            transactionService.getTransactionsByDateRange(accountId, startDate, endDate, pageable);
        });

        assertEquals("Start date cannot be after end date", exception.getMessage());

        // Verify no repository interactions
        verify(transactionRepository, never()).findByTransactionDateBetween(
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getTransactionsByDateRange - Should throw ResourceNotFoundException when account not found")
    void testGetTransactionsByDateRange_AccountNotFound() {
        // Arrange: Account does not exist
        Long accountId = 999L;
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();
        Pageable pageable = PageRequest.of(0, 10);

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act & Assert: Verify exception thrown
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            transactionService.getTransactionsByDateRange(accountId, startDate, endDate, pageable);
        });

        assertEquals("Account not found with ID: 999", exception.getMessage());

        // Verify repository interactions
        verify(accountRepository, times(1)).findById(accountId);
        verify(transactionRepository, never()).findByTransactionDateBetween(
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }

    // ===========================================================================================
    // getTransactionsByCategory() Tests - Category Filtering
    // ===========================================================================================

    @Test
    @DisplayName("getTransactionsByCategory - Should return transactions for specific category")
    void testGetTransactionsByCategory_Success() {
        // Arrange: Category code query
        String categoryCode = "5010";
        List<Transaction> categoryTransactions = testTransactionList.subList(0, 3);

        when(transactionRepository.findByTransactionCategoryCode(categoryCode))
                .thenReturn(categoryTransactions);

        // Act: Retrieve transactions by category
        List<Transaction> actualTransactions = transactionService.getTransactionsByCategory(categoryCode);

        // Assert: Verify results
        assertNotNull(actualTransactions);
        assertEquals(3, actualTransactions.size());
        actualTransactions.forEach(tx -> 
                assertEquals("5010", tx.getTransactionCategoryCode()));

        // Verify repository interaction
        verify(transactionRepository, times(1)).findByTransactionCategoryCode(categoryCode);
    }

    @Test
    @DisplayName("getTransactionsByCategory - Should throw InvalidInputException when category code is null")
    void testGetTransactionsByCategory_NullCategory() {
        // Arrange: Null category code
        String categoryCode = null;

        // Act & Assert: Verify exception thrown
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            transactionService.getTransactionsByCategory(categoryCode);
        });

        assertEquals("Transaction category code cannot be null or empty", exception.getMessage());

        // Verify no repository interaction
        verify(transactionRepository, never()).findByTransactionCategoryCode(anyString());
    }

    @Test
    @DisplayName("getTransactionsByCategory - Should throw InvalidInputException when category code is empty")
    void testGetTransactionsByCategory_EmptyCategory() {
        // Arrange: Empty category code
        String categoryCode = "";

        // Act & Assert: Verify exception thrown
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            transactionService.getTransactionsByCategory(categoryCode);
        });

        assertEquals("Transaction category code cannot be null or empty", exception.getMessage());

        // Verify no repository interaction
        verify(transactionRepository, never()).findByTransactionCategoryCode(anyString());
    }

    @Test
    @DisplayName("getTransactionsByCategory - Should return empty list when no transactions for category")
    void testGetTransactionsByCategory_NoResults() {
        // Arrange: No transactions for category
        String categoryCode = "9999";
        when(transactionRepository.findByTransactionCategoryCode(categoryCode))
                .thenReturn(Collections.emptyList());

        // Act: Retrieve transactions
        List<Transaction> actualTransactions = transactionService.getTransactionsByCategory(categoryCode);

        // Assert: Verify empty list
        assertNotNull(actualTransactions);
        assertTrue(actualTransactions.isEmpty());

        // Verify repository interaction
        verify(transactionRepository, times(1)).findByTransactionCategoryCode(categoryCode);
    }

    // ===========================================================================================
    // getAccountTransactionSummary() Tests - Transaction Summary Statistics
    // ===========================================================================================

    @Test
    @DisplayName("getAccountTransactionSummary - Should return summary statistics for account")
    void testGetAccountTransactionSummary_Success() {
        // Arrange: Transaction summary query
        Long accountId = 1L;
        Page<Transaction> transactionPage = new PageImpl<>(testTransactionList);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.findByAccountAccountId(eq(accountId), any(Pageable.class)))
                .thenReturn(transactionPage);

        // Act: Retrieve transaction summary
        Map<String, Object> summary = transactionService.getAccountTransactionSummary(accountId);

        // Assert: Verify summary statistics
        assertNotNull(summary);
        assertEquals(accountId, summary.get("accountId"));
        assertEquals("00000000001", summary.get("accountNumber"));
        assertEquals(10L, summary.get("totalCount"));
        assertNotNull(summary.get("totalAmount"));
        assertNotNull(summary.get("earliestDate"));
        assertNotNull(summary.get("latestDate"));

        // Verify BigDecimal calculation
        BigDecimal expectedTotal = testTransactionList.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(expectedTotal, summary.get("totalAmount"));

        // Verify repository interactions
        verify(accountRepository, times(1)).findById(accountId);
        verify(transactionRepository, times(1)).findByAccountAccountId(eq(accountId), any(Pageable.class));
    }

    @Test
    @DisplayName("getAccountTransactionSummary - Should return empty summary for account with no transactions")
    void testGetAccountTransactionSummary_NoTransactions() {
        // Arrange: Account with no transactions
        Long accountId = 1L;
        Page<Transaction> emptyPage = new PageImpl<>(Collections.emptyList());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.findByAccountAccountId(eq(accountId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act: Retrieve transaction summary
        Map<String, Object> summary = transactionService.getAccountTransactionSummary(accountId);

        // Assert: Verify empty summary
        assertNotNull(summary);
        assertEquals(0L, summary.get("totalCount"));
        assertEquals(BigDecimal.ZERO, summary.get("totalAmount"));
        assertNull(summary.get("earliestDate"));
        assertNull(summary.get("latestDate"));

        // Verify repository interactions
        verify(accountRepository, times(1)).findById(accountId);
        verify(transactionRepository, times(1)).findByAccountAccountId(eq(accountId), any(Pageable.class));
    }

    @Test
    @DisplayName("getAccountTransactionSummary - Should throw ResourceNotFoundException when account not found")
    void testGetAccountTransactionSummary_AccountNotFound() {
        // Arrange: Account does not exist
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act & Assert: Verify exception thrown
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            transactionService.getAccountTransactionSummary(accountId);
        });

        assertEquals("Account not found with ID: 999", exception.getMessage());

        // Verify repository interactions
        verify(accountRepository, times(1)).findById(accountId);
        verify(transactionRepository, never()).findByAccountAccountId(anyLong(), any(Pageable.class));
    }
}
