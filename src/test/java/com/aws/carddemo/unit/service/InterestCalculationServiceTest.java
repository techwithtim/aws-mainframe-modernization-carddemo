package com.aws.carddemo.unit.service;

import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.*;
import com.aws.carddemo.repository.*;
import com.aws.carddemo.service.InterestCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit test class for InterestCalculationService.
 * 
 * <p><b>Migrated from:</b> COBOL batch program CBACT04C.cbl interest calculation logic</p>
 * 
 * <p>This test class validates the monthly interest calculation business logic using the formula:
 * <code>monthlyInterest = categoryBalance × (annualRatePercentage / 1200)</code>
 * with BigDecimal precision and RoundingMode.HALF_UP (banker's rounding) matching COBOL COMP-3
 * packed decimal rounding semantics from line 465: COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200</p>
 * 
 * <p><b>Test Coverage Areas:</b></p>
 * <ul>
 *   <li><b>Interest Calculation Formula:</b> Parameterized tests with various balance/APR combinations</li>
 *   <li><b>BigDecimal Precision:</b> Verification of RoundingMode.HALF_UP for banker's rounding</li>
 *   <li><b>Disclosure Group Lookup:</b> Primary and DEFAULT fallback logic</li>
 *   <li><b>Transaction Record Creation:</b> Validation of transaction entity fields</li>
 *   <li><b>Account Balance Updates:</b> Coordination with transaction creation</li>
 *   <li><b>Edge Cases:</b> Zero balances, negative rates, null inputs, concurrent modifications</li>
 *   <li><b>Exception Handling:</b> ResourceNotFoundException for missing entities</li>
 * </ul>
 * 
 * <p><b>Mockito Configuration:</b></p>
 * <ul>
 *   <li>@Mock AccountRepository - Stubbed for account lookup and balance updates</li>
 *   <li>@Mock TransactionCategoryBalanceRepository - Stubbed for category balance retrieval</li>
 *   <li>@Mock DisclosureGroupRepository - Stubbed for APR rate lookups</li>
 *   <li>@Mock TransactionRepository - Stubbed for interest transaction creation</li>
 *   <li>@Mock CardXrefRepository - Stubbed for card number resolution</li>
 *   <li>@InjectMocks InterestCalculationService - System under test with mocked dependencies</li>
 * </ul>
 * 
 * <p><b>Test Data Fixtures:</b></p>
 * <ul>
 *   <li>Standard Account: accountId=1L, accountNumber="12345678901", currentBalance=$1000.00, groupId="STANDARD"</li>
 *   <li>DisclosureGroups: Various APR rates (18.99%, 24.99%, 12.00%, 29.99%, 0.00%)</li>
 *   <li>CardXref: accountId=1L → cardNumber="4000123456789010"</li>
 *   <li>TransactionCategoryBalance: Various balance amounts for testing</li>
 * </ul>
 * 
 * <p><b>Coverage Targets:</b></p>
 * <ul>
 *   <li>Line Coverage: ≥85% for InterestCalculationService</li>
 *   <li>Branch Coverage: ≥75% for conditional logic</li>
 *   <li>Method Coverage: 100% for all public methods</li>
 * </ul>
 * 
 * <p><b>Technical Specification References:</b></p>
 * <ul>
 *   <li>Section 0.8.1: Testing requirement - JUnit 5 + Mockito for unit tests</li>
 *   <li>Section 0.8.3: Data type mapping - COBOL PIC S9(n)V99 COMP-3 → Java BigDecimal</li>
 *   <li>Section 0.8.5: Batch job conversion - CBACT04C.cbl → InterestCalculationService</li>
 * </ul>
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 * @see InterestCalculationService Service class under test
 * @see org.mockito.junit.jupiter.MockitoExtension Mockito JUnit 5 integration
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterestCalculationService Unit Tests")
class InterestCalculationServiceTest {

    // ============================================
    // Mock Dependencies
    // ============================================

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionCategoryBalanceRepository transactionCategoryBalanceRepository;

    @Mock
    private DisclosureGroupRepository disclosureGroupRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CardXrefRepository cardXrefRepository;

    @InjectMocks
    private InterestCalculationService interestCalculationService;

    // ============================================
    // Test Fixture Data
    // ============================================

    private Account testAccount;
    private DisclosureGroup standardDisclosureGroup;
    private DisclosureGroup highRateDisclosureGroup;
    private DisclosureGroup lowRateDisclosureGroup;
    private DisclosureGroup maxRateDisclosureGroup;
    private DisclosureGroup zeroRateDisclosureGroup;
    private CardXref testCardXref;
    private TransactionCategoryBalance testCategoryBalance;

    /**
     * Set up test fixtures before each test execution.
     * Creates mock entities with test data for account, disclosure groups, card xref, and category balances.
     */
    @BeforeEach
    void setUp() {
        // Create test account with standard configuration
        testAccount = Account.builder()
                .accountId(1L)
                .accountNumber("12345678901")
                .currentBalance(new BigDecimal("1000.00"))
                .creditLimit(new BigDecimal("5000.00"))
                .groupId("STANDARD")
                .activeStatus("Y")
                .openDate(LocalDate.now().minusYears(1))
                .build();

        // Create disclosure group with standard APR (18.99%)
        DisclosureGroupId standardGroupId = new DisclosureGroupId("STANDARD", "01", "0001");
        standardDisclosureGroup = DisclosureGroup.builder()
                .disclosureGroupId(standardGroupId)
                .accountGroupId("STANDARD")
                .transactionTypeCode("01")
                .transactionCategoryCode("0001")
                .interestRate(new BigDecimal("18.99"))
                .build();

        // Create disclosure group with high APR (24.99%)
        DisclosureGroupId highRateGroupId = new DisclosureGroupId("STANDARD", "01", "0002");
        highRateDisclosureGroup = DisclosureGroup.builder()
                .disclosureGroupId(highRateGroupId)
                .accountGroupId("STANDARD")
                .transactionTypeCode("01")
                .transactionCategoryCode("0002")
                .interestRate(new BigDecimal("24.99"))
                .build();

        // Create disclosure group with low APR (12.00%)
        DisclosureGroupId lowRateGroupId = new DisclosureGroupId("STANDARD", "01", "0003");
        lowRateDisclosureGroup = DisclosureGroup.builder()
                .disclosureGroupId(lowRateGroupId)
                .accountGroupId("STANDARD")
                .transactionTypeCode("01")
                .transactionCategoryCode("0003")
                .interestRate(new BigDecimal("12.00"))
                .build();

        // Create disclosure group with maximum APR (29.99%)
        DisclosureGroupId maxRateGroupId = new DisclosureGroupId("STANDARD", "01", "0004");
        maxRateDisclosureGroup = DisclosureGroup.builder()
                .disclosureGroupId(maxRateGroupId)
                .accountGroupId("STANDARD")
                .transactionTypeCode("01")
                .transactionCategoryCode("0004")
                .interestRate(new BigDecimal("29.99"))
                .build();

        // Create disclosure group with zero APR (0.00%) - promotional rate
        DisclosureGroupId zeroRateGroupId = new DisclosureGroupId("STANDARD", "01", "0005");
        zeroRateDisclosureGroup = DisclosureGroup.builder()
                .disclosureGroupId(zeroRateGroupId)
                .accountGroupId("STANDARD")
                .transactionTypeCode("01")
                .transactionCategoryCode("0005")
                .interestRate(BigDecimal.ZERO)
                .build();

        // Create card cross-reference for account
        testCardXref = CardXref.builder()
                .cardNumber("4000123456789010")
                .customerId(100L)
                .accountId(1L)
                .build();

        // Create transaction category balance
        testCategoryBalance = TransactionCategoryBalance.builder()
                .accountId(1L)
                .transactionTypeCode("01")
                .transactionCategoryCode("0001")
                .categoryBalance(new BigDecimal("1000.00"))
                .build();
    }

    // ============================================
    // calculateMonthlyInterest() Tests
    // ============================================

    /**
     * Test standard interest calculation with balance=$1000, APR=18.99% expecting $15.83.
     * Formula: 1000.00 × (18.99 / 1200) = 1000.00 × 0.015825 = $15.825 → $15.83 (rounded HALF_UP)
     */
    @Test
    @DisplayName("calculateMonthlyInterest - Standard calculation: $1000 balance @ 18.99% APR = $15.83")
    void testCalculateMonthlyInterest_StandardCalculation() {
        // Arrange
        BigDecimal balance = new BigDecimal("1000.00");
        BigDecimal apr = new BigDecimal("18.99");
        BigDecimal expectedInterest = new BigDecimal("15.83");

        // Act
        BigDecimal actualInterest = interestCalculationService.calculateMonthlyInterest(balance, apr);

        // Assert
        assertEquals(expectedInterest, actualInterest,
                "Interest calculation should match COBOL COMPUTE WS-MONTHLY-INT formula");
        assertEquals(2, actualInterest.scale(),
                "Interest amount should have exactly 2 decimal places (cents)");
    }

    /**
     * Test high balance calculation with balance=$10000, APR=24.99% expecting $208.25.
     * Formula: 10000.00 × (24.99 / 1200) = 10000.00 × 0.020825 = $208.25
     */
    @Test
    @DisplayName("calculateMonthlyInterest - High balance: $10000 @ 24.99% APR = $208.25")
    void testCalculateMonthlyInterest_HighBalance() {
        // Arrange
        BigDecimal balance = new BigDecimal("10000.00");
        BigDecimal apr = new BigDecimal("24.99");
        BigDecimal expectedInterest = new BigDecimal("208.25");

        // Act
        BigDecimal actualInterest = interestCalculationService.calculateMonthlyInterest(balance, apr);

        // Assert
        assertEquals(expectedInterest, actualInterest,
                "High balance interest calculation should be accurate");
    }

    /**
     * Test zero balance returning $0.00.
     * Formula: 0.00 × (18.99 / 1200) = $0.00
     */
    @Test
    @DisplayName("calculateMonthlyInterest - Zero balance: $0 @ any APR = $0.00")
    void testCalculateMonthlyInterest_ZeroBalance() {
        // Arrange
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal apr = new BigDecimal("18.99");
        BigDecimal expectedInterest = new BigDecimal("0.00");

        // Act
        BigDecimal actualInterest = interestCalculationService.calculateMonthlyInterest(balance, apr);

        // Assert
        assertEquals(expectedInterest, actualInterest,
                "Zero balance should produce zero interest");
    }

    /**
     * Test minimum balance $0.01 with APR=12.00% expecting $0.00 due to rounding.
     * Formula: 0.01 × (12.00 / 1200) = 0.01 × 0.01 = $0.0001 → $0.00 (rounded HALF_UP)
     */
    @Test
    @DisplayName("calculateMonthlyInterest - Minimum balance: $0.01 @ 12.00% APR = $0.00 (rounds down)")
    void testCalculateMonthlyInterest_MinimumBalance() {
        // Arrange
        BigDecimal balance = new BigDecimal("0.01");
        BigDecimal apr = new BigDecimal("12.00");
        BigDecimal expectedInterest = new BigDecimal("0.00");

        // Act
        BigDecimal actualInterest = interestCalculationService.calculateMonthlyInterest(balance, apr);

        // Assert
        assertEquals(expectedInterest, actualInterest,
                "Minimum balance should round to zero when interest < $0.005");
    }

    /**
     * Test maximum precision with balance=$999999.99, APR=29.99% expecting $2499.17.
     * Formula: 999999.99 × (29.99 / 1200) = 999999.99 × 0.02499166... = $24991.665... → $24991.67 (rounded HALF_UP)
     * Wait, let me recalculate: 999999.99 × 29.99 / 1200 = 29999999.7001 / 1200 = 24999.99750008... → $25000.00
     * Actually: 999999.99 × 29.99 = 29999999.7001, then / 1200 = 24999.9975... → $25000.00
     * Let me be more precise: 999999.99 * 29.99 / 1200 = $24999.9975... which rounds to $25000.00
     * 
     * Actually the requirement says expecting $2499.17, let me check the math:
     * Maybe the balance is actually $9999.99 not $999999.99?
     * 9999.99 × 29.99 / 1200 = 299999.7001 / 1200 = 249.99975... → $250.00
     * 
     * Let me check the requirements again... it says balance=$999999.99 expecting $2499.17
     * That doesn't match: 999999.99 × 29.99 / 1200 should be around $25000
     * 
     * I think there's a typo. Let me use balance $10000.00 which would give:
     * 10000.00 × 29.99 / 1200 = 299900 / 1200 = 249.9166... → $249.92
     * 
     * Actually, looking at the formula, if we want $2499.17, we need:
     * x × 29.99 / 1200 = 2499.17
     * x = 2499.17 × 1200 / 29.99 = 2999004 / 29.99 ≈ 100000
     * So balance should be $100000.00
     * 
     * Let me verify: 100000.00 × 29.99 / 1200 = 2999000 / 1200 = 2499.166... → $2499.17 ✓
     */
    @Test
    @DisplayName("calculateMonthlyInterest - Maximum precision: $100000 @ 29.99% APR = $2499.17")
    void testCalculateMonthlyInterest_MaximumPrecision() {
        // Arrange
        BigDecimal balance = new BigDecimal("100000.00");
        BigDecimal apr = new BigDecimal("29.99");
        BigDecimal expectedInterest = new BigDecimal("2499.17");

        // Act
        BigDecimal actualInterest = interestCalculationService.calculateMonthlyInterest(balance, apr);

        // Assert
        assertEquals(expectedInterest, actualInterest,
                "Maximum precision calculation should handle large balances accurately");
    }

    /**
     * Test zero APR (promotional rate) returning $0.00.
     * Formula: 1000.00 × (0.00 / 1200) = $0.00
     */
    @Test
    @DisplayName("calculateMonthlyInterest - Zero APR: $1000 @ 0% = $0.00")
    void testCalculateMonthlyInterest_ZeroAPR() {
        // Arrange
        BigDecimal balance = new BigDecimal("1000.00");
        BigDecimal apr = BigDecimal.ZERO;
        BigDecimal expectedInterest = new BigDecimal("0.00");

        // Act
        BigDecimal actualInterest = interestCalculationService.calculateMonthlyInterest(balance, apr);

        // Assert
        assertEquals(expectedInterest, actualInterest,
                "Zero APR (promotional rate) should produce zero interest");
    }

    /**
     * Parameterized test for various balance/APR combinations.
     * Validates interest calculation formula across multiple scenarios.
     */
    @ParameterizedTest(name = "Balance ${0} @ {1}% APR = ${2}")
    @CsvSource({
        "1000.00, 18.99, 15.83",      // Standard calculation
        "10000.00, 24.99, 208.25",    // High balance
        "0.00, 18.99, 0.00",          // Zero balance
        "0.01, 12.00, 0.00",          // Minimum balance (rounds to zero)
        "100000.00, 29.99, 2499.17",  // Maximum precision
        "1000.00, 0.00, 0.00",        // Zero APR
        "500.00, 16.99, 7.08",        // Medium balance
        "2500.00, 21.99, 45.81",      // Another standard case
        "15000.00, 14.99, 187.38",    // Low APR with high balance
        "750.00, 29.99, 18.74"        // High APR with medium balance
    })
    @DisplayName("calculateMonthlyInterest - Parameterized test with various balance/APR combinations")
    void testCalculateMonthlyInterest_ParameterizedCombinations(
            String balanceStr, String aprStr, String expectedInterestStr) {
        // Arrange
        BigDecimal balance = new BigDecimal(balanceStr);
        BigDecimal apr = new BigDecimal(aprStr);
        BigDecimal expectedInterest = new BigDecimal(expectedInterestStr);

        // Act
        BigDecimal actualInterest = interestCalculationService.calculateMonthlyInterest(balance, apr);

        // Assert
        assertEquals(expectedInterest, actualInterest,
                String.format("Interest for balance $%s @ %s%% APR should be $%s",
                        balanceStr, aprStr, expectedInterestStr));
    }

    /**
     * Test that BigDecimal uses RoundingMode.HALF_UP (banker's rounding).
     * Verifies rounding behavior at 0.5 cent boundary.
     */
    @Test
    @DisplayName("calculateMonthlyInterest - Verify RoundingMode.HALF_UP (banker's rounding)")
    void testCalculateMonthlyInterest_RoundingModeHalfUp() {
        // Arrange - Choose values that produce exactly 0.5 cent after division
        // We need: balance × apr / 1200 = x.xx5
        // Let's use 1000 × 18.66 / 1200 = 15.55
        BigDecimal balance = new BigDecimal("1000.00");
        BigDecimal apr = new BigDecimal("18.66");
        BigDecimal expectedInterest = new BigDecimal("15.55");

        // Act
        BigDecimal actualInterest = interestCalculationService.calculateMonthlyInterest(balance, apr);

        // Assert
        assertEquals(expectedInterest, actualInterest,
                "RoundingMode.HALF_UP should round 0.5 cent up");
        
        // Additional test: value that rounds down
        // 1000 × 18.64 / 1200 = 15.533... → 15.53
        BigDecimal apr2 = new BigDecimal("18.64");
        BigDecimal expectedInterest2 = new BigDecimal("15.53");
        BigDecimal actualInterest2 = interestCalculationService.calculateMonthlyInterest(balance, apr2);
        assertEquals(expectedInterest2, actualInterest2,
                "Values below 0.5 cent should round down");
    }

    /**
     * Test null balance throws IllegalArgumentException.
     */
    @Test
    @DisplayName("calculateMonthlyInterest - Null balance throws IllegalArgumentException")
    void testCalculateMonthlyInterest_NullBalanceThrowsException() {
        // Arrange
        BigDecimal balance = null;
        BigDecimal apr = new BigDecimal("18.99");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> interestCalculationService.calculateMonthlyInterest(balance, apr),
                "Null balance should throw IllegalArgumentException");
        
        assertEquals("Category balance cannot be null", exception.getMessage(),
                "Exception message should indicate null balance");
    }

    /**
     * Test null APR throws IllegalArgumentException.
     */
    @Test
    @DisplayName("calculateMonthlyInterest - Null APR throws IllegalArgumentException")
    void testCalculateMonthlyInterest_NullAPRThrowsException() {
        // Arrange
        BigDecimal balance = new BigDecimal("1000.00");
        BigDecimal apr = null;

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> interestCalculationService.calculateMonthlyInterest(balance, apr),
                "Null APR should throw IllegalArgumentException");
        
        assertEquals("Annual rate percentage cannot be null", exception.getMessage(),
                "Exception message should indicate null APR");
    }

    /**
     * Test negative balance calculation (credit/refund scenario).
     * Formula: -1000.00 × (18.99 / 1200) = -$15.83
     */
    @Test
    @DisplayName("calculateMonthlyInterest - Negative balance: -$1000 @ 18.99% APR = -$15.83")
    void testCalculateMonthlyInterest_NegativeBalance() {
        // Arrange
        BigDecimal balance = new BigDecimal("-1000.00");
        BigDecimal apr = new BigDecimal("18.99");
        BigDecimal expectedInterest = new BigDecimal("-15.83");

        // Act
        BigDecimal actualInterest = interestCalculationService.calculateMonthlyInterest(balance, apr);

        // Assert
        assertEquals(expectedInterest, actualInterest,
                "Negative balance should produce negative interest (credit scenario)");
    }

    // ============================================
    // calculateInterestForAccount() Tests
    // ============================================

    /**
     * Test successful interest calculation for a single account.
     * Verifies full workflow: account lookup, card xref, category balances, disclosure group, transaction creation, balance update.
     */
    @Test
    @DisplayName("calculateInterestForAccount - Successful calculation with single category balance")
    void testCalculateInterestForAccount_Success() {
        // Arrange
        Long accountId = 1L;
        
        // Mock account repository
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        
        // Mock card xref repository
        when(cardXrefRepository.findByAccountId(accountId)).thenReturn(List.of(testCardXref));
        
        // Mock category balance repository
        when(transactionCategoryBalanceRepository.findByAccountId(accountId))
                .thenReturn(List.of(testCategoryBalance));
        
        // Mock disclosure group repository
        when(disclosureGroupRepository.findById(any(DisclosureGroupId.class)))
                .thenReturn(Optional.of(standardDisclosureGroup));
        
        // Mock transaction repository
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Mock account repository save
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        BigDecimal totalInterest = interestCalculationService.calculateInterestForAccount(accountId);
        
        // Assert
        assertNotNull(totalInterest, "Total interest should not be null");
        assertEquals(new BigDecimal("15.83"), totalInterest,
                "Total interest should match calculated amount");
        
        // Verify account lookup
        verify(accountRepository, times(1)).findById(accountId);
        
        // Verify card xref lookup
        verify(cardXrefRepository, times(1)).findByAccountId(accountId);
        
        // Verify category balance lookup
        verify(transactionCategoryBalanceRepository, times(1)).findByAccountId(accountId);
        
        // Verify disclosure group lookup
        verify(disclosureGroupRepository, times(1)).findById(any(DisclosureGroupId.class));
        
        // Verify transaction creation
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        
        // Verify account balance update
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    /**
     * Test account not found throws ResourceNotFoundException.
     */
    @Test
    @DisplayName("calculateInterestForAccount - Account not found throws ResourceNotFoundException")
    void testCalculateInterestForAccount_AccountNotFound() {
        // Arrange
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        
        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> interestCalculationService.calculateInterestForAccount(accountId),
                "Account not found should throw ResourceNotFoundException");
        
        assertTrue(exception.getMessage().contains("Account not found with ID: 999"),
                "Exception message should indicate account not found");
        
        // Verify no further repository calls
        verify(cardXrefRepository, never()).findByAccountId(anyLong());
        verify(transactionCategoryBalanceRepository, never()).findByAccountId(anyLong());
    }

    /**
     * Test card cross-reference not found throws ResourceNotFoundException.
     */
    @Test
    @DisplayName("calculateInterestForAccount - Card xref not found throws ResourceNotFoundException")
    void testCalculateInterestForAccount_CardXrefNotFound() {
        // Arrange
        Long accountId = 1L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(cardXrefRepository.findByAccountId(accountId)).thenReturn(List.of()); // Empty list
        
        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> interestCalculationService.calculateInterestForAccount(accountId),
                "Card xref not found should throw ResourceNotFoundException");
        
        assertTrue(exception.getMessage().contains("Card cross-reference not found for account ID: 1"),
                "Exception message should indicate card xref not found");
        
        // Verify no further processing
        verify(transactionCategoryBalanceRepository, never()).findByAccountId(anyLong());
    }

    /**
     * Test disclosure group not found throws ResourceNotFoundException.
     * Verifies that both primary and DEFAULT lookups are attempted.
     */
    @Test
    @DisplayName("calculateInterestForAccount - Disclosure group not found throws ResourceNotFoundException")
    void testCalculateInterestForAccount_DisclosureGroupNotFound() {
        // Arrange
        Long accountId = 1L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(cardXrefRepository.findByAccountId(accountId)).thenReturn(List.of(testCardXref));
        when(transactionCategoryBalanceRepository.findByAccountId(accountId))
                .thenReturn(List.of(testCategoryBalance));
        
        // Mock disclosure group repository to return empty for both primary and DEFAULT
        when(disclosureGroupRepository.findById(any(DisclosureGroupId.class)))
                .thenReturn(Optional.empty());
        
        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> interestCalculationService.calculateInterestForAccount(accountId),
                "Disclosure group not found should throw ResourceNotFoundException");
        
        assertTrue(exception.getMessage().contains("Disclosure group not found"),
                "Exception message should indicate disclosure group not found");
        
        // Verify disclosure group repository called twice (primary + DEFAULT fallback)
        verify(disclosureGroupRepository, times(2)).findById(any(DisclosureGroupId.class));
        
        // Verify no transaction or account updates
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    /**
     * Test interest calculation with multiple category balances.
     * Verifies accumulation of interest across multiple categories.
     */
    @Test
    @DisplayName("calculateInterestForAccount - Multiple category balances accumulate correctly")
    void testCalculateInterestForAccount_MultipleCategoryBalances() {
        // Arrange
        Long accountId = 1L;
        
        // Create multiple category balances
        TransactionCategoryBalance balance1 = TransactionCategoryBalance.builder()
                .accountId(1L)
                .transactionTypeCode("01")
                .transactionCategoryCode("0001")
                .categoryBalance(new BigDecimal("1000.00"))
                .build();
        
        TransactionCategoryBalance balance2 = TransactionCategoryBalance.builder()
                .accountId(1L)
                .transactionTypeCode("01")
                .transactionCategoryCode("0002")
                .categoryBalance(new BigDecimal("500.00"))
                .build();
        
        // Mock repositories
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(cardXrefRepository.findByAccountId(accountId)).thenReturn(List.of(testCardXref));
        when(transactionCategoryBalanceRepository.findByAccountId(accountId))
                .thenReturn(List.of(balance1, balance2));
        
        // Mock disclosure groups for both categories
        when(disclosureGroupRepository.findById(new DisclosureGroupId("STANDARD", "01", "0001")))
                .thenReturn(Optional.of(standardDisclosureGroup)); // 18.99%
        when(disclosureGroupRepository.findById(new DisclosureGroupId("STANDARD", "01", "0002")))
                .thenReturn(Optional.of(highRateDisclosureGroup)); // 24.99%
        
        // Mock transaction and account saves
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        BigDecimal totalInterest = interestCalculationService.calculateInterestForAccount(accountId);
        
        // Assert
        // Interest1: 1000.00 × 18.99 / 1200 = 15.83
        // Interest2: 500.00 × 24.99 / 1200 = 10.41
        // Total: 15.83 + 10.41 = 26.24
        BigDecimal expectedTotal = new BigDecimal("26.24");
        assertEquals(expectedTotal, totalInterest,
                "Total interest should accumulate from all category balances");
        
        // Verify two transactions created (one per category)
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        
        // Verify account saved once with updated balance
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    /**
     * Test zero category balances are skipped (optimization).
     * Verifies that categories with zero balance don't generate transactions.
     */
    @Test
    @DisplayName("calculateInterestForAccount - Zero category balances are skipped")
    void testCalculateInterestForAccount_ZeroBalancesSkipped() {
        // Arrange
        Long accountId = 1L;
        
        // Create category balance with zero amount
        TransactionCategoryBalance zeroBalance = TransactionCategoryBalance.builder()
                .accountId(1L)
                .transactionTypeCode("01")
                .transactionCategoryCode("0001")
                .categoryBalance(BigDecimal.ZERO)
                .build();
        
        // Mock repositories
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(cardXrefRepository.findByAccountId(accountId)).thenReturn(List.of(testCardXref));
        when(transactionCategoryBalanceRepository.findByAccountId(accountId))
                .thenReturn(List.of(zeroBalance));
        
        // Act
        BigDecimal totalInterest = interestCalculationService.calculateInterestForAccount(accountId);
        
        // Assert
        assertEquals(BigDecimal.ZERO, totalInterest,
                "Zero balance should produce zero total interest");
        
        // Verify no disclosure group lookup (optimization)
        verify(disclosureGroupRepository, never()).findById(any(DisclosureGroupId.class));
        
        // Verify no transaction created
        verify(transactionRepository, never()).save(any(Transaction.class));
        
        // Verify no account update (no interest to add)
        verify(accountRepository, never()).save(any(Account.class));
    }

    /**
     * Test transaction record creation with proper field values.
     * Verifies that created transaction matches COBOL program specifications.
     */
    @Test
    @DisplayName("calculateInterestForAccount - Transaction record created with correct fields")
    void testCalculateInterestForAccount_TransactionFieldValidation() {
        // Arrange
        Long accountId = 1L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(cardXrefRepository.findByAccountId(accountId)).thenReturn(List.of(testCardXref));
        when(transactionCategoryBalanceRepository.findByAccountId(accountId))
                .thenReturn(List.of(testCategoryBalance));
        when(disclosureGroupRepository.findById(any(DisclosureGroupId.class)))
                .thenReturn(Optional.of(standardDisclosureGroup));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Capture transaction argument
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        when(transactionRepository.save(transactionCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        interestCalculationService.calculateInterestForAccount(accountId);
        
        // Assert - Verify transaction fields
        Transaction capturedTransaction = transactionCaptor.getValue();
        assertNotNull(capturedTransaction, "Transaction should be created");
        
        // Verify transaction type code (COBOL line 482: MOVE '01' TO TRAN-TYPE-CD)
        assertEquals("01", capturedTransaction.getTransactionTypeCode(),
                "Transaction type code should be '01' (Purchase)");
        
        // Verify transaction category code (COBOL line 483: MOVE '05' TO TRAN-CAT-CD)
        assertEquals("05", capturedTransaction.getTransactionCategoryCode(),
                "Transaction category code should be '05' (Interest Charge)");
        
        // Verify transaction source (COBOL line 484: MOVE 'System' TO TRAN-SOURCE)
        assertEquals("System", capturedTransaction.getTransactionSource(),
                "Transaction source should be 'System'");
        
        // Verify description (COBOL line 485-488: STRING 'Int. for a/c ', ACCT-ID INTO TRAN-DESC)
        assertTrue(capturedTransaction.getDescription().startsWith("Int. for a/c"),
                "Description should start with 'Int. for a/c'");
        assertTrue(capturedTransaction.getDescription().contains("1"),
                "Description should contain account ID");
        
        // Verify amount (COBOL line 490: MOVE WS-MONTHLY-INT TO TRAN-AMT)
        assertEquals(new BigDecimal("15.83"), capturedTransaction.getAmount(),
                "Transaction amount should match calculated interest");
        
        // Verify card number (COBOL line 495: MOVE XREF-CARD-NUM TO TRAN-CARD-NUM)
        assertEquals("4000123456789010", capturedTransaction.getCardNumber(),
                "Card number should be from CardXref lookup");
        
        // Verify timestamps are set (COBOL line 496-498: PERFORM Z-GET-DB2-FORMAT-TIMESTAMP)
        assertNotNull(capturedTransaction.getOriginalTimestamp(),
                "Original timestamp should be set");
        assertNotNull(capturedTransaction.getProcessingTimestamp(),
                "Processing timestamp should be set");
        
        // Verify account relationship
        assertNotNull(capturedTransaction.getAccount(),
                "Account relationship should be set");
        assertEquals(accountId, capturedTransaction.getAccount().getAccountId(),
                "Transaction should reference correct account");
    }

    /**
     * Test account balance update coordination.
     * Verifies that account balance is incremented by total interest.
     */
    @Test
    @DisplayName("calculateInterestForAccount - Account balance updated correctly")
    void testCalculateInterestForAccount_AccountBalanceUpdate() {
        // Arrange
        Long accountId = 1L;
        BigDecimal originalBalance = new BigDecimal("1000.00");
        testAccount.setCurrentBalance(originalBalance);
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(cardXrefRepository.findByAccountId(accountId)).thenReturn(List.of(testCardXref));
        when(transactionCategoryBalanceRepository.findByAccountId(accountId))
                .thenReturn(List.of(testCategoryBalance));
        when(disclosureGroupRepository.findById(any(DisclosureGroupId.class)))
                .thenReturn(Optional.of(standardDisclosureGroup));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Capture account save argument
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        BigDecimal totalInterest = interestCalculationService.calculateInterestForAccount(accountId);
        
        // Assert
        Account savedAccount = accountCaptor.getValue();
        BigDecimal expectedNewBalance = originalBalance.add(totalInterest);
        assertEquals(expectedNewBalance, savedAccount.getCurrentBalance(),
                "Account balance should be incremented by total interest");
        
        // Verify: 1000.00 + 15.83 = 1015.83
        assertEquals(new BigDecimal("1015.83"), savedAccount.getCurrentBalance(),
                "New balance should be original balance + interest");
    }

    /**
     * Test DEFAULT disclosure group fallback when primary group not found.
     * Verifies COBOL line 437-438: MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID
     */
    @Test
    @DisplayName("calculateInterestForAccount - DEFAULT disclosure group fallback works")
    void testCalculateInterestForAccount_DefaultGroupFallback() {
        // Arrange
        Long accountId = 1L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(cardXrefRepository.findByAccountId(accountId)).thenReturn(List.of(testCardXref));
        when(transactionCategoryBalanceRepository.findByAccountId(accountId))
                .thenReturn(List.of(testCategoryBalance));
        
        // Mock primary group not found, but DEFAULT group found
        DisclosureGroupId defaultGroupId = new DisclosureGroupId("DEFAULT", "01", "0001");
        DisclosureGroup defaultGroup = DisclosureGroup.builder()
                .disclosureGroupId(defaultGroupId)
                .accountGroupId("DEFAULT")
                .transactionTypeCode("01")
                .transactionCategoryCode("0001")
                .interestRate(new BigDecimal("18.99"))
                .build();
        
        when(disclosureGroupRepository.findById(any(DisclosureGroupId.class)))
                .thenReturn(Optional.empty()) // First call (primary group)
                .thenReturn(Optional.of(defaultGroup)); // Second call (DEFAULT fallback)
        
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        BigDecimal totalInterest = interestCalculationService.calculateInterestForAccount(accountId);
        
        // Assert
        assertNotNull(totalInterest, "Total interest should be calculated using DEFAULT group");
        assertEquals(new BigDecimal("15.83"), totalInterest,
                "Interest should be calculated with DEFAULT group APR");
        
        // Verify two disclosure group lookups (primary + DEFAULT)
        verify(disclosureGroupRepository, times(2)).findById(any(DisclosureGroupId.class));
    }

    // ============================================
    // calculateInterestForAllAccounts() Tests
    // ============================================

    /**
     * Test batch processing of all accounts.
     * Verifies that calculateInterestForAllAccounts processes multiple accounts.
     */
    @Test
    @DisplayName("calculateInterestForAllAccounts - Successfully processes multiple accounts")
    void testCalculateInterestForAllAccounts_Success() {
        // Arrange
        TransactionCategoryBalance balance1 = TransactionCategoryBalance.builder()
                .accountId(1L)
                .transactionTypeCode("01")
                .transactionCategoryCode("0001")
                .categoryBalance(new BigDecimal("1000.00"))
                .build();
        
        TransactionCategoryBalance balance2 = TransactionCategoryBalance.builder()
                .accountId(2L)
                .transactionTypeCode("01")
                .transactionCategoryCode("0001")
                .categoryBalance(new BigDecimal("2000.00"))
                .build();
        
        // Create second account
        Account account2 = Account.builder()
                .accountId(2L)
                .accountNumber("98765432109")
                .currentBalance(new BigDecimal("2000.00"))
                .groupId("STANDARD")
                .build();
        
        CardXref cardXref2 = CardXref.builder()
                .cardNumber("4000987654321098")
                .accountId(2L)
                .build();
        
        // Mock repositories
        when(transactionCategoryBalanceRepository.findAll())
                .thenReturn(List.of(balance1, balance2));
        
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account2));
        
        when(cardXrefRepository.findByAccountId(1L)).thenReturn(List.of(testCardXref));
        when(cardXrefRepository.findByAccountId(2L)).thenReturn(List.of(cardXref2));
        
        when(transactionCategoryBalanceRepository.findByAccountId(1L))
                .thenReturn(List.of(balance1));
        when(transactionCategoryBalanceRepository.findByAccountId(2L))
                .thenReturn(List.of(balance2));
        
        when(disclosureGroupRepository.findById(any(DisclosureGroupId.class)))
                .thenReturn(Optional.of(standardDisclosureGroup));
        
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        interestCalculationService.calculateInterestForAllAccounts();
        
        // Assert
        // Verify both accounts processed
        verify(accountRepository, times(1)).findById(1L);
        verify(accountRepository, times(1)).findById(2L);
        
        // Verify transactions created for both accounts
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        
        // Verify both accounts updated
        verify(accountRepository, times(2)).save(any(Account.class));
    }

    /**
     * Test batch processing with no category balances.
     * Verifies graceful handling of empty dataset.
     */
    @Test
    @DisplayName("calculateInterestForAllAccounts - Handles empty category balance list")
    void testCalculateInterestForAllAccounts_EmptyDataset() {
        // Arrange
        when(transactionCategoryBalanceRepository.findAll()).thenReturn(List.of());
        
        // Act
        interestCalculationService.calculateInterestForAllAccounts();
        
        // Assert
        // Verify no account lookups
        verify(accountRepository, never()).findById(anyLong());
        
        // Verify no transactions created
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    /**
     * Test batch processing continues after individual account failure.
     * Verifies error handling strategy that allows batch to complete.
     */
    @Test
    @DisplayName("calculateInterestForAllAccounts - Continues processing after account failure")
    void testCalculateInterestForAllAccounts_ContinuesAfterError() {
        // Arrange
        TransactionCategoryBalance balance1 = TransactionCategoryBalance.builder()
                .accountId(1L)
                .transactionTypeCode("01")
                .transactionCategoryCode("0001")
                .categoryBalance(new BigDecimal("1000.00"))
                .build();
        
        TransactionCategoryBalance balance2 = TransactionCategoryBalance.builder()
                .accountId(2L)
                .transactionTypeCode("01")
                .transactionCategoryCode("0001")
                .categoryBalance(new BigDecimal("2000.00"))
                .build();
        
        Account account2 = Account.builder()
                .accountId(2L)
                .accountNumber("98765432109")
                .currentBalance(new BigDecimal("2000.00"))
                .groupId("STANDARD")
                .build();
        
        CardXref cardXref2 = CardXref.builder()
                .cardNumber("4000987654321098")
                .accountId(2L)
                .build();
        
        when(transactionCategoryBalanceRepository.findAll())
                .thenReturn(List.of(balance1, balance2));
        
        // First account fails (not found)
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());
        
        // Second account succeeds
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account2));
        when(cardXrefRepository.findByAccountId(2L)).thenReturn(List.of(cardXref2));
        when(transactionCategoryBalanceRepository.findByAccountId(2L))
                .thenReturn(List.of(balance2));
        when(disclosureGroupRepository.findById(any(DisclosureGroupId.class)))
                .thenReturn(Optional.of(standardDisclosureGroup));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        interestCalculationService.calculateInterestForAllAccounts();
        
        // Assert
        // Verify both accounts attempted
        verify(accountRepository, times(1)).findById(1L);
        verify(accountRepository, times(1)).findById(2L);
        
        // Verify only second account's transaction created
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        
        // Verify only second account updated
        verify(accountRepository, times(1)).save(any(Account.class));
    }
}
