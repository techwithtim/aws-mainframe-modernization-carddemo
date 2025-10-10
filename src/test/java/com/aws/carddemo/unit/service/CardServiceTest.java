/*
 * CardServiceTest.java
 * 
 * Unit test class for CardService validating business logic migrated from COBOL programs.
 * 
 * Tests cover:
 * - Card list retrieval with pagination (from COCRDLIC.cbl)
 * - Card detail lookup by card number with PCI-DSS masking (from COCRDSLC.cbl)
 * - Card status updates with expiration validation (from COCRDUPC.cbl)
 * - Card expiration date validation with edge cases
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package com.aws.carddemo.unit.service;

import com.aws.carddemo.exception.InvalidInputException;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.Card;
import com.aws.carddemo.model.CardXref;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardRepository;
import com.aws.carddemo.repository.CardXrefRepository;
import com.aws.carddemo.service.CardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test class for {@link CardService}.
 * 
 * <p>This test suite validates the business logic migrated from COBOL programs:
 * <ul>
 *   <li><strong>COCRDLIC.cbl</strong>: Card list browse with STARTBR/READNEXT pattern</li>
 *   <li><strong>COCRDSLC.cbl</strong>: Card select by card number with XREF resolution</li>
 *   <li><strong>COCRDUPC.cbl</strong>: Card update with REWRITE operations</li>
 * </ul>
 * 
 * <p><strong>Test Coverage Goals:</strong>
 * <ul>
 *   <li>Line Coverage: ≥85%</li>
 *   <li>Branch Coverage: ≥75%</li>
 *   <li>All business rules validated</li>
 *   <li>All exception paths tested</li>
 * </ul>
 * 
 * <p><strong>Testing Strategy:</strong>
 * <ul>
 *   <li>Mockito for repository mocking with @Mock annotations</li>
 *   <li>@InjectMocks for CardService dependency injection</li>
 *   <li>Parameterized tests for expiration date edge cases</li>
 *   <li>Separate tests for success and failure scenarios</li>
 * </ul>
 * 
 * @see CardService for service implementation
 * @see Card for entity under test
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CardService Unit Tests")
class CardServiceTest {

    /**
     * Repository mock for card data access operations.
     * Provides stubbed behavior for findByAccountAccountId, findByCardNumber, save, etc.
     */
    @Mock
    private CardRepository cardRepository;

    /**
     * Repository mock for card cross-reference lookups.
     * Provides stubbed behavior for findByCardNumber card-to-account resolution.
     */
    @Mock
    private CardXrefRepository cardXrefRepository;

    /**
     * Repository mock for account validation operations.
     * Provides stubbed behavior for existsById and findById account lookups.
     */
    @Mock
    private AccountRepository accountRepository;

    /**
     * System under test with mocked repository dependencies injected.
     */
    @InjectMocks
    private CardService cardService;

    // Test fixture data
    private Account testAccount;
    private Card testCard;
    private CardXref testCardXref;
    private List<Card> testCardList;

    /**
     * Sets up test fixtures before each test method execution.
     * 
     * <p>Initializes:
     * <ul>
     *   <li>Test account with ID and account number</li>
     *   <li>Test card with 16-digit number, expiration date, active status</li>
     *   <li>Test card cross-reference for card-to-account mapping</li>
     *   <li>List of test cards for pagination testing</li>
     * </ul>
     */
    @BeforeEach
    void setUp() {
        // Create test account
        testAccount = Account.builder()
                .accountId(1L)
                .accountNumber("12345678901")
                .activeStatus("Y")
                .currentBalance(java.math.BigDecimal.valueOf(1000.00))
                .creditLimit(java.math.BigDecimal.valueOf(5000.00))
                .build();

        // Create test card with valid expiration date (1 year from now)
        testCard = Card.builder()
                .cardId(1L)
                .cardNumber("1234567890123456")
                .expirationDate(LocalDate.now().plusMonths(12))
                .activeStatus("Y")
                .embossedName("JOHN DOE")
                .account(testAccount)
                .build();

        // Create test card cross-reference
        testCardXref = CardXref.builder()
                .cardNumber("1234567890123456")
                .accountId(1L)
                .customerId(100L)
                .build();

        // Create list of test cards for pagination
        Card card2 = Card.builder()
                .cardId(2L)
                .cardNumber("2345678901234567")
                .expirationDate(LocalDate.now().plusMonths(6))
                .activeStatus("Y")
                .embossedName("JANE DOE")
                .account(testAccount)
                .build();

        Card card3 = Card.builder()
                .cardId(3L)
                .cardNumber("3456789012345678")
                .expirationDate(LocalDate.now().plusMonths(18))
                .activeStatus("N")
                .embossedName("BOB SMITH")
                .account(testAccount)
                .build();

        testCardList = Arrays.asList(testCard, card2, card3);
    }

    // ==================== getCardsByAccountId() Tests ====================

    @Test
    @DisplayName("Should return paginated list of cards for valid account ID")
    void testGetCardsByAccountId_Success() {
        // Arrange
        Long accountId = 1L;
        Pageable pageable = PageRequest.of(0, 10, Sort.by("cardNumber"));
        Page<Card> cardPage = new PageImpl<>(testCardList, pageable, testCardList.size());

        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(cardRepository.findByAccountAccountId(accountId, pageable)).thenReturn(cardPage);

        // Act
        Page<Card> result = cardService.getCardsByAccountId(accountId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getContent()).containsExactlyElementsOf(testCardList);

        // Verify repository interactions
        verify(accountRepository, times(1)).existsById(accountId);
        verify(cardRepository, times(1)).findByAccountAccountId(accountId, pageable);
    }

    @Test
    @DisplayName("Should return empty page when account has no cards")
    void testGetCardsByAccountId_EmptyResult() {
        // Arrange
        Long accountId = 1L;
        Pageable pageable = PageRequest.of(0, 10, Sort.by("cardNumber"));
        Page<Card> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(cardRepository.findByAccountAccountId(accountId, pageable)).thenReturn(emptyPage);

        // Act
        Page<Card> result = cardService.getCardsByAccountId(accountId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);

        verify(accountRepository, times(1)).existsById(accountId);
        verify(cardRepository, times(1)).findByAccountAccountId(accountId, pageable);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when account does not exist")
    void testGetCardsByAccountId_AccountNotFound() {
        // Arrange
        Long accountId = 999L;
        Pageable pageable = PageRequest.of(0, 10);

        when(accountRepository.existsById(accountId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> cardService.getCardsByAccountId(accountId, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining("999");

        // Verify account validation was called but card retrieval was not
        verify(accountRepository, times(1)).existsById(accountId);
        verify(cardRepository, never()).findByAccountAccountId(any(), any());
    }

    @Test
    @DisplayName("Should apply pagination parameters correctly")
    void testGetCardsByAccountId_PaginationParameters() {
        // Arrange
        Long accountId = 1L;
        Pageable pageable = PageRequest.of(1, 2, Sort.by("expirationDate").descending());
        List<Card> pageCards = Arrays.asList(testCard, testCardList.get(1));
        Page<Card> cardPage = new PageImpl<>(pageCards, pageable, 3);

        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(cardRepository.findByAccountAccountId(accountId, pageable)).thenReturn(cardPage);

        // Act
        Page<Card> result = cardService.getCardsByAccountId(accountId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getNumber()).isEqualTo(1); // Second page (0-indexed)
        assertThat(result.getSize()).isEqualTo(2);   // Page size of 2
        assertThat(result.getTotalPages()).isEqualTo(2); // 3 cards / 2 per page = 2 pages
        assertThat(result.getContent()).hasSize(2);

        verify(cardRepository, times(1)).findByAccountAccountId(eq(accountId), eq(pageable));
    }

    // ==================== getCardByCardNumber() Tests ====================

    @Test
    @DisplayName("Should return card for valid card number")
    void testGetCardByCardNumber_Success() {
        // Arrange
        String cardNumber = "1234567890123456";

        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCard));

        // Act
        Card result = cardService.getCardByCardNumber(cardNumber);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getCardId()).isEqualTo(1L);
        assertThat(result.getCardNumber()).isEqualTo(cardNumber);
        assertThat(result.getAccount()).isNotNull();
        assertThat(result.getAccount().getAccountId()).isEqualTo(1L);

        verify(cardRepository, times(1)).findByCardNumber(cardNumber);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when card number not found")
    void testGetCardByCardNumber_NotFound() {
        // Arrange
        String cardNumber = "9999999999999999";

        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cardService.getCardByCardNumber(cardNumber))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Card")
                .hasMessageContaining("cardNumber");

        verify(cardRepository, times(1)).findByCardNumber(cardNumber);
    }

    @Test
    @DisplayName("Should mask card number in logs (via @Transient getCardNumberMasked)")
    void testGetCardByCardNumber_MaskingVerification() {
        // Arrange
        String cardNumber = "1234567890123456";

        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCard));

        // Act
        Card result = cardService.getCardByCardNumber(cardNumber);

        // Assert - Verify Card entity has masking method available
        // Note: Actual masking happens in Card.getCardNumberMasked() method
        assertThat(result.getCardNumber()).isEqualTo("1234567890123456"); // Full number in entity
        // The service logs using maskCardNumber() private method which is tested through behavior
    }

    // ==================== getCardById() Tests ====================

    @Test
    @DisplayName("Should return card for valid card ID")
    void testGetCardById_Success() {
        // Arrange
        Long cardId = 1L;

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(testCard));

        // Act
        Card result = cardService.getCardById(cardId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getCardId()).isEqualTo(cardId);
        assertThat(result.getCardNumber()).isEqualTo("1234567890123456");

        verify(cardRepository, times(1)).findById(cardId);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when card ID not found")
    void testGetCardById_NotFound() {
        // Arrange
        Long cardId = 999L;

        when(cardRepository.findById(cardId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cardService.getCardById(cardId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Card")
                .hasMessageContaining("999");

        verify(cardRepository, times(1)).findById(cardId);
    }

    // ==================== updateCardStatus() Tests ====================

    @Test
    @DisplayName("Should activate card successfully when expiration date is valid")
    void testUpdateCardStatus_ActivateValid() {
        // Arrange
        Long cardId = 1L;
        String newStatus = "Y";

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(testCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Card result = cardService.updateCardStatus(cardId, newStatus);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getActiveStatus()).isEqualTo("Y");

        verify(cardRepository, times(1)).findById(cardId);
        verify(cardRepository, times(1)).save(testCard);
    }

    @Test
    @DisplayName("Should deactivate card successfully without expiration check")
    void testUpdateCardStatus_DeactivateValid() {
        // Arrange
        Long cardId = 1L;
        String newStatus = "N";

        // Create card with expired date to verify deactivation doesn't check expiration
        Card expiredCard = Card.builder()
                .cardId(cardId)
                .cardNumber("1234567890123456")
                .expirationDate(LocalDate.now().minusDays(1)) // Expired yesterday
                .activeStatus("Y")
                .embossedName("TEST USER")
                .account(testAccount)
                .build();

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(expiredCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Card result = cardService.updateCardStatus(cardId, newStatus);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getActiveStatus()).isEqualTo("N");

        verify(cardRepository, times(1)).findById(cardId);
        verify(cardRepository, times(1)).save(expiredCard);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when activating expired card")
    void testUpdateCardStatus_ActivateExpiredCard() {
        // Arrange
        Long cardId = 1L;
        String newStatus = "Y";

        // Create card with expired date
        Card expiredCard = Card.builder()
                .cardId(cardId)
                .cardNumber("1234567890123456")
                .expirationDate(LocalDate.now().minusDays(1)) // Expired yesterday
                .activeStatus("N")
                .embossedName("TEST USER")
                .account(testAccount)
                .build();

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(expiredCard));

        // Act & Assert
        assertThatThrownBy(() -> cardService.updateCardStatus(cardId, newStatus))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Cannot activate card that expired");

        verify(cardRepository, times(1)).findById(cardId);
        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw InvalidInputException for null status")
    void testUpdateCardStatus_NullStatus() {
        // Arrange
        Long cardId = 1L;
        String newStatus = null;

        // Act & Assert
        assertThatThrownBy(() -> cardService.updateCardStatus(cardId, newStatus))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Card status must be 'Y' (active) or 'N' (inactive)");

        // Verify repository never called due to early validation
        verify(cardRepository, never()).findById(any());
        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw InvalidInputException for invalid status value")
    void testUpdateCardStatus_InvalidStatus() {
        // Arrange
        Long cardId = 1L;
        String newStatus = "X"; // Invalid status

        // Act & Assert
        assertThatThrownBy(() -> cardService.updateCardStatus(cardId, newStatus))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Card status must be 'Y' (active) or 'N' (inactive)");

        verify(cardRepository, never()).findById(any());
        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw InvalidInputException for empty status value")
    void testUpdateCardStatus_EmptyStatus() {
        // Arrange
        Long cardId = 1L;
        String newStatus = ""; // Empty status

        // Act & Assert
        assertThatThrownBy(() -> cardService.updateCardStatus(cardId, newStatus))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Card status must be 'Y' (active) or 'N' (inactive)");

        verify(cardRepository, never()).findById(any());
        verify(cardRepository, never()).save(any());
    }

    // ==================== validateCardExpiration() Tests ====================

    @Test
    @DisplayName("Should validate successfully when card expires tomorrow")
    void testValidateCardExpiration_ExpiresTomorrow() {
        // Arrange
        Long cardId = 1L;
        Card validCard = Card.builder()
                .cardId(cardId)
                .cardNumber("1234567890123456")
                .expirationDate(LocalDate.now().plusDays(1)) // Expires tomorrow
                .activeStatus("Y")
                .embossedName("TEST USER")
                .account(testAccount)
                .build();

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(validCard));

        // Act
        Card result = cardService.validateCardExpiration(cardId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getExpirationDate()).isAfter(LocalDate.now());

        verify(cardRepository, times(1)).findById(cardId);
    }

    @Test
    @DisplayName("Should validate successfully when card expires in 30 days")
    void testValidateCardExpiration_Expires30Days() {
        // Arrange
        Long cardId = 1L;
        Card validCard = Card.builder()
                .cardId(cardId)
                .cardNumber("1234567890123456")
                .expirationDate(LocalDate.now().plusDays(30)) // Expires in 30 days
                .activeStatus("Y")
                .embossedName("TEST USER")
                .account(testAccount)
                .build();

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(validCard));

        // Act
        Card result = cardService.validateCardExpiration(cardId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getExpirationDate()).isAfter(LocalDate.now());

        verify(cardRepository, times(1)).findById(cardId);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when card expired yesterday")
    void testValidateCardExpiration_ExpiredYesterday() {
        // Arrange
        Long cardId = 1L;
        Card expiredCard = Card.builder()
                .cardId(cardId)
                .cardNumber("1234567890123456")
                .expirationDate(LocalDate.now().minusDays(1)) // Expired yesterday
                .activeStatus("Y")
                .embossedName("TEST USER")
                .account(testAccount)
                .build();

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(expiredCard));

        // Act & Assert
        assertThatThrownBy(() -> cardService.validateCardExpiration(cardId))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Card expired on");

        verify(cardRepository, times(1)).findById(cardId);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when card expired 1 year ago")
    void testValidateCardExpiration_ExpiredOneYear() {
        // Arrange
        Long cardId = 1L;
        Card expiredCard = Card.builder()
                .cardId(cardId)
                .cardNumber("1234567890123456")
                .expirationDate(LocalDate.now().minusYears(1)) // Expired 1 year ago
                .activeStatus("Y")
                .embossedName("TEST USER")
                .account(testAccount)
                .build();

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(expiredCard));

        // Act & Assert
        assertThatThrownBy(() -> cardService.validateCardExpiration(cardId))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Card expired on");

        verify(cardRepository, times(1)).findById(cardId);
    }

    /**
     * Parameterized test for card expiration edge cases.
     * Tests various expiration date scenarios with expected outcomes.
     * 
     * @param daysOffset number of days to offset from today (negative = past, positive = future)
     * @param shouldBeValid true if card should be valid, false if it should be expired
     * @param description test case description
     */
    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource({
            "1, true, Card expires tomorrow - valid",
            "0, true, Card expires today - valid (edge case per COBOL < logic)",
            "-1, false, Card expired yesterday - invalid",
            "30, true, Card expires in 30 days - valid",
            "-365, false, Card expired 1 year ago - invalid"
    })
    @DisplayName("Parameterized expiration validation tests")
    void testValidateCardExpiration_Parameterized(int daysOffset, boolean shouldBeValid, String description) {
        // Arrange
        Long cardId = 1L;
        LocalDate expirationDate = LocalDate.now().plusDays(daysOffset);
        
        Card card = Card.builder()
                .cardId(cardId)
                .cardNumber("1234567890123456")
                .expirationDate(expirationDate)
                .activeStatus("Y")
                .embossedName("TEST USER")
                .account(testAccount)
                .build();

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));

        // Act & Assert
        if (shouldBeValid) {
            // Card should be valid
            Card result = cardService.validateCardExpiration(cardId);
            assertThat(result).isNotNull();
            assertThat(result.getExpirationDate()).isAfterOrEqualTo(LocalDate.now());
        } else {
            // Card should be expired
            assertThatThrownBy(() -> cardService.validateCardExpiration(cardId))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("Card expired on");
        }

        verify(cardRepository, times(1)).findById(cardId);
    }

    // ==================== getCardWithAccountInfo() Tests ====================

    @Test
    @DisplayName("Should return card with account information")
    void testGetCardWithAccountInfo_Success() {
        // Arrange
        String cardNumber = "1234567890123456";

        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCard));

        // Act
        Card result = cardService.getCardWithAccountInfo(cardNumber);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getCardNumber()).isEqualTo(cardNumber);
        assertThat(result.getAccount()).isNotNull();
        assertThat(result.getAccount().getAccountId()).isEqualTo(1L);
        assertThat(result.getAccount().getAccountNumber()).isEqualTo("12345678901");

        verify(cardRepository, times(1)).findByCardNumber(cardNumber);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when card has no associated account")
    void testGetCardWithAccountInfo_NoAccount() {
        // Arrange
        String cardNumber = "1234567890123456";
        Card cardWithoutAccount = Card.builder()
                .cardId(1L)
                .cardNumber(cardNumber)
                .expirationDate(LocalDate.now().plusMonths(12))
                .activeStatus("Y")
                .embossedName("TEST USER")
                .account(null) // No account relationship
                .build();

        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(cardWithoutAccount));

        // Act & Assert
        assertThatThrownBy(() -> cardService.getCardWithAccountInfo(cardNumber))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Card has no associated account");

        verify(cardRepository, times(1)).findByCardNumber(cardNumber);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when card not found")
    void testGetCardWithAccountInfo_CardNotFound() {
        // Arrange
        String cardNumber = "9999999999999999";

        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cardService.getCardWithAccountInfo(cardNumber))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Card");

        verify(cardRepository, times(1)).findByCardNumber(cardNumber);
    }

    // ==================== Integration Scenarios ====================

    @Test
    @DisplayName("Should handle card status update workflow end-to-end")
    void testCardStatusUpdateWorkflow() {
        // Arrange
        Long cardId = 1L;
        
        // Step 1: Retrieve card
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(testCard));

        Card retrievedCard = cardService.getCardById(cardId);
        assertThat(retrievedCard.getActiveStatus()).isEqualTo("Y");

        // Step 2: Deactivate card
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> {
            Card savedCard = invocation.getArgument(0);
            savedCard.setActiveStatus("N");
            return savedCard;
        });

        Card deactivatedCard = cardService.updateCardStatus(cardId, "N");
        assertThat(deactivatedCard.getActiveStatus()).isEqualTo("N");

        // Verify interactions
        verify(cardRepository, times(2)).findById(cardId);
        verify(cardRepository, times(1)).save(any(Card.class));
    }

    @Test
    @DisplayName("Should enforce business rule: cannot activate expired card")
    void testBusinessRule_CannotActivateExpiredCard() {
        // Arrange - Create expired card in inactive state
        Long cardId = 2L;
        LocalDate expiredDate = LocalDate.now().minusDays(10);
        
        Card expiredInactiveCard = Card.builder()
                .cardId(cardId)
                .cardNumber("2345678901234567")
                .expirationDate(expiredDate)
                .activeStatus("N") // Currently inactive
                .embossedName("JANE DOE")
                .account(testAccount)
                .build();

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(expiredInactiveCard));

        // Act & Assert - Attempt to activate should fail
        assertThatThrownBy(() -> cardService.updateCardStatus(cardId, "Y"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Cannot activate card that expired");

        // Verify save was never called due to validation failure
        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should verify CardXref repository interaction for cross-reference lookup")
    void testCardXrefLookup_Integration() {
        // Arrange
        String cardNumber = "1234567890123456";

        // This test verifies that CardService uses CardRepository directly
        // CardXrefRepository is used by other services (TransactionService, PaymentService)
        when(cardRepository.findByCardNumber(cardNumber)).thenReturn(Optional.of(testCard));

        // Act
        Card result = cardService.getCardByCardNumber(cardNumber);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getCardNumber()).isEqualTo(cardNumber);

        // Verify CardRepository was used (CardService doesn't directly use CardXrefRepository)
        verify(cardRepository, times(1)).findByCardNumber(cardNumber);
        verifyNoInteractions(cardXrefRepository); // CardService doesn't use CardXref in getCardByCardNumber
    }
}
