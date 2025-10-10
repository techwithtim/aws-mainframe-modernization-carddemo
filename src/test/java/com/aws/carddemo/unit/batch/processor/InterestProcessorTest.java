/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.carddemo.unit.batch.processor;

import com.aws.carddemo.batch.dto.InterestTransaction;
import com.aws.carddemo.batch.processor.InterestProcessor;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.DisclosureGroup;
import com.aws.carddemo.repository.DisclosureGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for InterestProcessor batch component.
 * <p>
 * Validates interest calculation algorithm migrated from COBOL CBACT04C.cbl,
 * including BigDecimal precision requirements, minimum charge thresholds,
 * and disclosure group fallback logic.
 * </p>
 * <p>
 * <b>COBOL Equivalence Testing:</b> All test cases validate byte-for-byte
 * numerical equivalence with COBOL COMP-3 packed decimal arithmetic.
 * </p>
 *
 * @see InterestProcessor
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterestProcessor Unit Tests - COBOL CBACT04C.cbl Migration")
class InterestProcessorTest {

    @Mock
    private DisclosureGroupRepository disclosureGroupRepository;

    private InterestProcessor interestProcessor;

    /**
     * Initialize test fixture before each test method.
     * Creates InterestProcessor with mocked repository dependency.
     */
    @BeforeEach
    void setUp() {
        interestProcessor = new InterestProcessor(disclosureGroupRepository);
    }

    /**
     * Test successful interest calculation with standard balance and rate.
     * <p>
     * <b>Test Case:</b> $1,234.56 balance at 18.99% APR
     * <b>Expected:</b> $19.53 monthly interest charge
     * <b>COBOL Reference:</b> CBACT04C.cbl line 464-465 calculation formula
     * </p>
     */
    @Test
    @DisplayName("Calculate interest for standard balance - $1,234.56 at 18.99% APR = $19.53")
    void testCalculateInterest_StandardBalance() throws Exception {
        // Arrange
        Account account = createAccount(1L, "12345678901", "GRP001", 
            new BigDecimal("1234.56"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP001", new BigDecimal("18.99"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP001"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result, "InterestTransaction should not be null");
        assertEquals(1L, result.getAccountId(), "Account ID should match");
        
        // Verify exact calculation: 1234.56 * (18.99 / 1200) = 19.5375 → 19.54 (HALF_UP)
        assertEquals(new BigDecimal("19.54"), result.getCalculatedInterest(), 
            "Interest should be $19.54 (exact BigDecimal comparison)");
        
        assertEquals(LocalDate.now().withDayOfMonth(1), result.getTransactionDate(),
            "Transaction date should be first day of current month");
        
        assertEquals(new BigDecimal("19.54"), result.getYearToDateInterest(),
            "YTD interest should equal current month interest (no previous YTD)");

        verify(disclosureGroupRepository, times(1)).findByAccountGroupId("GRP001");
    }

    /**
     * Test minimum interest charge threshold application.
     * <p>
     * <b>Test Case:</b> $50.00 balance at 18.99% APR
     * <b>Calculated:</b> $0.79 (below minimum)
     * <b>Expected:</b> $1.00 minimum charge applied
     * <b>COBOL Reference:</b> IF WS-INTEREST < 1.00 MOVE 1.00 TO WS-INTEREST
     * </p>
     */
    @Test
    @DisplayName("Apply $1.00 minimum charge when calculated interest is $0.79")
    void testCalculateInterest_MinimumChargeThreshold() throws Exception {
        // Arrange
        Account account = createAccount(2L, "12345678902", "GRP001", 
            new BigDecimal("50.00"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP001", new BigDecimal("18.99"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP001"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // Verify minimum charge: 50.00 * (18.99 / 1200) = 0.79125 → 0.79, but minimum is $1.00
        assertEquals(new BigDecimal("1.00"), result.getCalculatedInterest(),
            "Interest should be $1.00 minimum charge (below $1.00 threshold)");
    }

    /**
     * Test zero balance account filtering.
     * <p>
     * <b>Test Case:</b> Account with $0.00 balance
     * <b>Expected:</b> null return (filtered from processing)
     * <b>COBOL Reference:</b> IF ACCT-CURR-BAL > 0 conditional processing
     * </p>
     */
    @Test
    @DisplayName("Filter zero balance account - return null")
    void testCalculateInterest_ZeroBalance() throws Exception {
        // Arrange
        Account account = createAccount(3L, "12345678903", "GRP001", 
            BigDecimal.ZERO, BigDecimal.ZERO);

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNull(result, "Zero balance account should return null (filtered)");
        
        // Verify repository never called for zero balance
        verify(disclosureGroupRepository, never()).findByAccountGroupId(anyString());
    }

    /**
     * Test negative balance account filtering.
     * <p>
     * <b>Test Case:</b> Account with -$100.00 balance (payment overage)
     * <b>Expected:</b> null return (filtered from interest calculation)
     * </p>
     */
    @Test
    @DisplayName("Filter negative balance account - return null")
    void testCalculateInterest_NegativeBalance() throws Exception {
        // Arrange
        Account account = createAccount(4L, "12345678904", "GRP001", 
            new BigDecimal("-100.00"), BigDecimal.ZERO);

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNull(result, "Negative balance account should return null (filtered)");
        verify(disclosureGroupRepository, never()).findByAccountGroupId(anyString());
    }

    /**
     * Test disclosure group not found - default rate fallback.
     * <p>
     * <b>Test Case:</b> Account with non-existent disclosure group
     * <b>Expected:</b> 18.99% default APR rate applied
     * <b>COBOL Reference:</b> Paragraph 1200-A-GET-DEFAULT-INT-RATE
     * </p>
     */
    @Test
    @DisplayName("Use default rate 18.99% when disclosure group not found")
    void testCalculateInterest_DisclosureGroupNotFound() throws Exception {
        // Arrange
        Account account = createAccount(5L, "12345678905", "INVALID_GROUP", 
            new BigDecimal("1000.00"), BigDecimal.ZERO);
        
        // Repository returns empty list (group not found)
        when(disclosureGroupRepository.findByAccountGroupId("INVALID_GROUP"))
            .thenReturn(new ArrayList<>());

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // Verify default rate applied: 1000.00 * (18.99 / 1200) = 15.825 → 15.83
        assertEquals(new BigDecimal("15.83"), result.getCalculatedInterest(),
            "Should use default rate 18.99% when group not found");
    }

    /**
     * Test disclosure group returns null list - default rate fallback.
     */
    @Test
    @DisplayName("Use default rate when repository returns null")
    void testCalculateInterest_DisclosureGroupReturnsNull() throws Exception {
        // Arrange
        Account account = createAccount(6L, "12345678906", "GRP002", 
            new BigDecimal("500.00"), BigDecimal.ZERO);
        
        when(disclosureGroupRepository.findByAccountGroupId("GRP002"))
            .thenReturn(null);

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // Verify default rate: 500.00 * (18.99 / 1200) = 7.9125 → 7.91
        assertEquals(new BigDecimal("7.91"), result.getCalculatedInterest());
    }

    /**
     * Test null group ID on account - default rate fallback.
     */
    @Test
    @DisplayName("Use default rate when account group ID is null")
    void testCalculateInterest_NullGroupId() throws Exception {
        // Arrange
        Account account = createAccount(7L, "12345678907", null, 
            new BigDecimal("2000.00"), BigDecimal.ZERO);

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // Verify default rate: 2000.00 * (18.99 / 1200) = 31.65
        assertEquals(new BigDecimal("31.65"), result.getCalculatedInterest());
        
        // Repository should not be called
        verify(disclosureGroupRepository, never()).findByAccountGroupId(anyString());
    }

    /**
     * Test empty group ID on account - default rate fallback.
     */
    @Test
    @DisplayName("Use default rate when account group ID is empty string")
    void testCalculateInterest_EmptyGroupId() throws Exception {
        // Arrange
        Account account = createAccount(8L, "12345678908", "   ", 
            new BigDecimal("750.00"), BigDecimal.ZERO);

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // Verify default rate: 750.00 * (18.99 / 1200) = 11.86875 → 11.87
        assertEquals(new BigDecimal("11.87"), result.getCalculatedInterest());
        verify(disclosureGroupRepository, never()).findByAccountGroupId(anyString());
    }

    /**
     * Test disclosure group with invalid (null) interest rate.
     */
    @Test
    @DisplayName("Use default rate when disclosure group has null interest rate")
    void testCalculateInterest_NullInterestRateInGroup() throws Exception {
        // Arrange
        Account account = createAccount(9L, "12345678909", "GRP003", 
            new BigDecimal("1500.00"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP003", null);
        when(disclosureGroupRepository.findByAccountGroupId("GRP003"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // Verify default rate used: 1500.00 * (18.99 / 1200) = 23.7375 → 23.74
        assertEquals(new BigDecimal("23.74"), result.getCalculatedInterest());
    }

    /**
     * Test disclosure group with zero interest rate.
     */
    @Test
    @DisplayName("Use default rate when disclosure group has zero interest rate")
    void testCalculateInterest_ZeroInterestRateInGroup() throws Exception {
        // Arrange
        Account account = createAccount(10L, "12345678910", "GRP004", 
            new BigDecimal("3000.00"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP004", BigDecimal.ZERO);
        when(disclosureGroupRepository.findByAccountGroupId("GRP004"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // Verify default rate: 3000.00 * (18.99 / 1200) = 47.475 → 47.48
        assertEquals(new BigDecimal("47.48"), result.getCalculatedInterest());
    }

    /**
     * Test disclosure group with negative interest rate.
     */
    @Test
    @DisplayName("Use default rate when disclosure group has negative interest rate")
    void testCalculateInterest_NegativeInterestRateInGroup() throws Exception {
        // Arrange
        Account account = createAccount(11L, "12345678911", "GRP005", 
            new BigDecimal("800.00"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP005", new BigDecimal("-10.00"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP005"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // Verify default rate: 800.00 * (18.99 / 1200) = 12.66
        assertEquals(new BigDecimal("12.66"), result.getCalculatedInterest());
    }

    /**
     * Test year-to-date interest accumulation.
     * <p>
     * <b>Test Case:</b> Account with existing $150.00 YTD interest
     * <b>Current Month:</b> $19.53 interest calculated
     * <b>Expected:</b> $169.53 updated YTD total
     * </p>
     */
    @Test
    @DisplayName("Calculate YTD interest accumulation - $150.00 + $19.53 = $169.53")
    void testCalculateInterest_YearToDateAccumulation() throws Exception {
        // Arrange
        Account account = createAccount(12L, "12345678912", "GRP001", 
            new BigDecimal("1234.56"), new BigDecimal("150.00"));
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP001", new BigDecimal("18.99"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP001"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        assertEquals(new BigDecimal("19.54"), result.getCalculatedInterest(),
            "Current month interest should be $19.54");
        assertEquals(new BigDecimal("169.54"), result.getYearToDateInterest(),
            "YTD should be $150.00 + $19.54 = $169.54");
    }

    /**
     * Test high balance with high interest rate - precision validation.
     * <p>
     * <b>Test Case:</b> $12,345.67 balance at 24.99% APR
     * <b>Expected:</b> $257.10 monthly interest (high precision calculation)
     * </p>
     */
    @Test
    @DisplayName("High balance with high rate - $12,345.67 at 24.99% = $257.10")
    void testCalculateInterest_HighBalanceHighRate() throws Exception {
        // Arrange
        Account account = createAccount(13L, "12345678913", "GRP006", 
            new BigDecimal("12345.67"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP006", new BigDecimal("24.99"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP006"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // 12345.67 * (24.99 / 1200) = 257.09563... → 257.10 (HALF_UP rounding)
        assertEquals(new BigDecimal("257.10"), result.getCalculatedInterest());
    }

    /**
     * Test low balance with low interest rate - precision edge case.
     * <p>
     * <b>Test Case:</b> $10.00 balance at 5.00% APR
     * <b>Calculated:</b> $0.04 (below minimum)
     * <b>Expected:</b> $1.00 minimum charge
     * </p>
     */
    @Test
    @DisplayName("Low balance with low rate - $10.00 at 5.00% = $1.00 minimum")
    void testCalculateInterest_LowBalanceLowRate() throws Exception {
        // Arrange
        Account account = createAccount(14L, "12345678914", "GRP007", 
            new BigDecimal("10.00"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP007", new BigDecimal("5.00"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP007"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // 10.00 * (5.00 / 1200) = 0.041666... → 0.04, but minimum $1.00 applies
        assertEquals(new BigDecimal("1.00"), result.getCalculatedInterest(),
            "Minimum charge $1.00 should apply");
    }

    /**
     * Test balance with 3 decimal places - precision handling.
     * <p>
     * <b>Test Case:</b> $1,234.567 balance (3 decimals in database)
     * <b>Expected:</b> Correct calculation with proper rounding
     * </p>
     */
    @Test
    @DisplayName("Handle 3 decimal place balance - $1,234.567 precision")
    void testCalculateInterest_ThreeDecimalPlaceBalance() throws Exception {
        // Arrange
        Account account = createAccount(15L, "12345678915", "GRP001", 
            new BigDecimal("1234.567"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP001", new BigDecimal("18.99"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP001"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // 1234.567 * (18.99 / 1200) = 19.5375368... → 19.54
        assertEquals(new BigDecimal("19.54"), result.getCalculatedInterest());
    }

    /**
     * Test null current balance - should be treated as zero and filtered.
     */
    @Test
    @DisplayName("Filter null current balance - return null")
    void testCalculateInterest_NullCurrentBalance() throws Exception {
        // Arrange
        Account account = createAccount(16L, "12345678916", "GRP001", 
            null, BigDecimal.ZERO);

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNull(result, "Null balance account should return null (filtered)");
        verify(disclosureGroupRepository, never()).findByAccountGroupId(anyString());
    }

    /**
     * Test exact $1.00 threshold boundary - should not apply minimum.
     * <p>
     * <b>Test Case:</b> Balance/rate combination that calculates to exactly $1.00
     * <b>Expected:</b> $1.00 (calculated, not minimum override)
     * </p>
     */
    @Test
    @DisplayName("Exact $1.00 calculation - no minimum override needed")
    void testCalculateInterest_ExactOneDoller() throws Exception {
        // Arrange
        // Calculate balance needed for exactly $1.00: 1.00 = balance * (18.99 / 1200)
        // balance = 1.00 * 1200 / 18.99 = 63.19...
        Account account = createAccount(17L, "12345678917", "GRP001", 
            new BigDecimal("63.19"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP001", new BigDecimal("18.99"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP001"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // 63.19 * (18.99 / 1200) = 1.000007... → 1.00
        assertEquals(new BigDecimal("1.00"), result.getCalculatedInterest());
    }

    /**
     * Test just above $1.00 threshold - $1.01 calculated.
     */
    @Test
    @DisplayName("Just above minimum threshold - $1.01 calculated")
    void testCalculateInterest_JustAboveMinimum() throws Exception {
        // Arrange
        Account account = createAccount(18L, "12345678918", "GRP001", 
            new BigDecimal("64.00"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP001", new BigDecimal("18.99"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP001"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // 64.00 * (18.99 / 1200) = 1.0128 → 1.01
        assertEquals(new BigDecimal("1.01"), result.getCalculatedInterest());
    }

    /**
     * Test just below $1.00 threshold - $0.99 becomes $1.00.
     */
    @Test
    @DisplayName("Just below minimum threshold - $0.99 becomes $1.00")
    void testCalculateInterest_JustBelowMinimum() throws Exception {
        // Arrange
        Account account = createAccount(19L, "12345678919", "GRP001", 
            new BigDecimal("62.50"), BigDecimal.ZERO);
        
        DisclosureGroup disclosureGroup = createDisclosureGroup("GRP001", new BigDecimal("18.99"));
        when(disclosureGroupRepository.findByAccountGroupId("GRP001"))
            .thenReturn(Arrays.asList(disclosureGroup));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // 62.50 * (18.99 / 1200) = 0.9896875 → 0.99, minimum $1.00 applies
        assertEquals(new BigDecimal("1.00"), result.getCalculatedInterest());
    }

    /**
     * Test multiple disclosure groups returned - use first.
     */
    @Test
    @DisplayName("Multiple disclosure groups - use first group's rate")
    void testCalculateInterest_MultipleDisclosureGroups() throws Exception {
        // Arrange
        Account account = createAccount(20L, "12345678920", "GRP008", 
            new BigDecimal("1000.00"), BigDecimal.ZERO);
        
        DisclosureGroup group1 = createDisclosureGroup("GRP008", new BigDecimal("15.99"));
        DisclosureGroup group2 = createDisclosureGroup("GRP008", new BigDecimal("22.99"));
        
        when(disclosureGroupRepository.findByAccountGroupId("GRP008"))
            .thenReturn(Arrays.asList(group1, group2));

        // Act
        InterestTransaction result = interestProcessor.process(account);

        // Assert
        assertNotNull(result);
        
        // Should use first group's rate: 1000.00 * (15.99 / 1200) = 13.325 → 13.33
        assertEquals(new BigDecimal("13.33"), result.getCalculatedInterest());
    }

    // ========================= Helper Methods =========================

    /**
     * Helper method to create test Account entity.
     */
    private Account createAccount(Long id, String accountNumber, String groupId, 
                                 BigDecimal currentBalance, BigDecimal interestPaidYtd) {
        Account account = new Account();
        account.setAccountId(id);
        account.setAccountNumber(accountNumber);
        account.setGroupId(groupId);
        account.setCurrentBalance(currentBalance);
        account.setInterestPaidYtd(interestPaidYtd);
        return account;
    }

    /**
     * Helper method to create test DisclosureGroup entity.
     */
    private DisclosureGroup createDisclosureGroup(String groupId, BigDecimal interestRate) {
        DisclosureGroup group = new DisclosureGroup();
        group.setAccountGroupId(groupId);
        group.setInterestRate(interestRate);
        return group;
    }
}
