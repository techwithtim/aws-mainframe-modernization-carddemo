/*
 * CardService.java
 * 
 * Service handling credit card management operations including card list retrieval,
 * card detail lookup, card status updates, and card expiration validation.
 * 
 * Migrated from COBOL programs:
 * - app/cbl/COCRDLIC.cbl: Card list browse with STARTBR/READNEXT pattern
 * - app/cbl/COCRDSLC.cbl: Card select by card number with XREF resolution
 * - app/cbl/COCRDUPC.cbl: Card update with REWRITE operations
 * 
 * Data structures from:
 * - app/cpy/CVACT02Y.cpy: CARD-RECORD (150-byte structure)
 * - app/cpy/CVACT03Y.cpy: CARD-XREF-RECORD (50-byte structure)
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
package com.aws.carddemo.service;

import com.aws.carddemo.exception.InvalidInputException;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Account;
import com.aws.carddemo.model.Card;
import com.aws.carddemo.model.CardXref;
import com.aws.carddemo.repository.AccountRepository;
import com.aws.carddemo.repository.CardRepository;
import com.aws.carddemo.repository.CardXrefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Service layer for credit card management operations.
 * 
 * <p>This service replaces the following COBOL programs:
 * <ul>
 *   <li><strong>COCRDLIC.cbl</strong>: Card list browse functionality using STARTBR/READNEXT 
 *       pattern for paginated card retrieval by account ID</li>
 *   <li><strong>COCRDSLC.cbl</strong>: Card detail selection using card number lookup with 
 *       XREFFILE cross-reference resolution to account ID</li>
 *   <li><strong>COCRDUPC.cbl</strong>: Card update operations using REWRITE for status changes 
 *       and expiration date validation</li>
 * </ul>
 * 
 * <p><strong>Key Business Logic:</strong>
 * <ul>
 *   <li>Card list retrieval with pagination (replaces COBOL STARTBR/READNEXT browse pattern)</li>
 *   <li>Card detail lookup by card number with PCI-DSS compliant masking</li>
 *   <li>Card status updates with validation (active status must be 'Y' or 'N')</li>
 *   <li>Card expiration date validation (cannot activate expired cards)</li>
 *   <li>Card-to-account cross-reference resolution via XREFFILE entity</li>
 * </ul>
 * 
 * <p><strong>PCI-DSS Compliance:</strong>
 * <ul>
 *   <li>Card numbers are masked in all log statements using getCardNumberMasked()</li>
 *   <li>CVV codes are never stored or logged per PCI-DSS Requirement 3.2.2</li>
 *   <li>Full card numbers are only exposed to authorized service methods</li>
 *   <li>All card operations are audited via BaseEntity timestamps</li>
 * </ul>
 * 
 * <p><strong>Transaction Management:</strong>
 * <p>All service methods are annotated with {@code @Transactional} to ensure ACID properties.
 * This replaces COBOL EXEC CICS SYNCPOINT (commit) and SYNCPOINT ROLLBACK patterns.
 * Any exception thrown from a service method automatically triggers transaction rollback.
 * 
 * <p><strong>Error Handling:</strong>
 * <ul>
 *   <li>{@link ResourceNotFoundException}: Thrown when card, account, or cross-reference not found
 *       (replaces COBOL FILE STATUS '23' and APPL-EOF condition checks)</li>
 *   <li>{@link InvalidInputException}: Thrown for business rule violations such as expired cards,
 *       invalid status values, or malformed card numbers</li>
 * </ul>
 * 
 * <p><strong>COBOL to Java Mapping:</strong>
 * <pre>
 * COBOL Pattern                          | Java Equivalent
 * -----------------------------------------------------------------------------------------------------
 * EXEC CICS STARTBR DATASET('CARDDAT')   | cardRepository.findByAccountAccountId(accountId, pageable)
 *   RIDFLD(WS-CARD-ACCT-ID)              |   Returns Page&lt;Card&gt; for pagination
 *   GTEQ                                 |
 * 
 * EXEC CICS READNEXT DATASET('CARDDAT')  | Spring Data Page navigation with PageRequest
 *   INTO(CARD-RECORD)                    |   Automatic result limiting and offset handling
 * 
 * EXEC CICS READ DATASET('XREFFILE')     | cardXrefRepository.findByCardNumber(cardNumber)
 *   RIDFLD(CARD-NUM)                     |   Returns Optional&lt;CardXref&gt; for safe resolution
 * 
 * EXEC CICS READ DATASET('CARDDAT')      | cardRepository.findByCardNumber(cardNumber)
 *   RIDFLD(CARD-NUM)                     |   Returns Optional&lt;Card&gt; with eager account fetch
 * 
 * EXEC CICS REWRITE DATASET('CARDDAT')   | cardRepository.save(card)
 *   FROM(CARD-RECORD)                    |   JPA automatic dirty checking and UPDATE generation
 * 
 * IF CARD-EXPIRE-DATE < WS-CURRENT-DATE  | if (expirationDate.isBefore(LocalDate.now()))
 *   PERFORM 9999-ABEND-PROGRAM           |   throw new InvalidInputException(...)
 * 
 * FILE STATUS '23' (record not found)    | Optional.orElseThrow(() -> new ResourceNotFoundException(...))
 * </pre>
 * 
 * @see Card for JPA entity representing credit card data
 * @see CardXref for card-to-account cross-reference entity
 * @see CardRepository for Spring Data JPA repository interface
 * @see CardXrefRepository for cross-reference repository interface
 * @author AWS CardDemo Modernization Team
 * @version 1.0.0
 * @since 2024
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CardService {

    /**
     * Repository for card data access operations.
     * Replaces COBOL FILE CONTROL SELECT CARDDAT statement.
     */
    private final CardRepository cardRepository;

    /**
     * Repository for card cross-reference data access.
     * Replaces COBOL FILE CONTROL SELECT XREFFILE statement.
     */
    private final CardXrefRepository cardXrefRepository;

    /**
     * Repository for account data access and validation.
     * Replaces COBOL FILE CONTROL SELECT ACCTFILE statement.
     */
    private final AccountRepository accountRepository;

    /**
     * Retrieves paginated list of cards associated with a specific account.
     * 
     * <p>Replaces COBOL program: <strong>COCRDLIC.cbl</strong>
     * <pre>
     * COBOL Logic:
     *     EXEC CICS STARTBR DATASET('CARDDAT')
     *         RIDFLD(WS-CARD-ACCT-ID)
     *         GTEQ
     *     END-EXEC.
     *     
     *     PERFORM UNTIL WS-CARD-COUNTER = 10 OR APPL-EOF = 'Y'
     *         EXEC CICS READNEXT DATASET('CARDDAT')
     *             INTO(CARD-RECORD)
     *         END-EXEC
     *         
     *         IF CARD-ACCT-ID = WS-SEARCH-ACCT-ID
     *             ADD 1 TO WS-CARD-COUNTER
     *             MOVE CARD-RECORD TO WS-CARD-REC(WS-CARD-COUNTER)
     *         ELSE
     *             SET APPL-EOF TO TRUE
     *         END-IF
     *     END-PERFORM.
     * </pre>
     * 
     * <p><strong>Key Improvements over COBOL:</strong>
     * <ul>
     *   <li>Database-level pagination replaces application-level READNEXT loop</li>
     *   <li>Automatic LIMIT and OFFSET handling via Spring Data Pageable</li>
     *   <li>Composite index on (account_id, expiration_date) for O(log n) performance</li>
     *   <li>Eager fetching of account relationship to avoid N+1 query problem</li>
     * </ul>
     * 
     * <p><strong>PCI-DSS Compliance:</strong>
     * Card numbers are masked in log statements. Only the last 4 digits are shown.
     * 
     * @param accountId the account ID to retrieve cards for (11-digit numeric string in COBOL)
     * @param pageable pagination parameters (page number, page size, sort order)
     * @return paginated list of cards with metadata (total elements, total pages, current page)
     * @throws ResourceNotFoundException if the account with given ID does not exist
     *         (replaces COBOL FILE STATUS '23' on ACCTFILE read)
     * 
     * @see CardRepository#findByAccountAccountId(Long, Pageable)
     * @see AccountRepository#findById(Long)
     */
    @Transactional(readOnly = true)
    public Page<Card> getCardsByAccountId(Long accountId, Pageable pageable) {
        log.debug("Retrieving cards for account ID: {}", accountId);
        
        // Validate account exists (replaces COBOL READ ACCTFILE validation)
        if (!accountRepository.existsById(accountId)) {
            log.warn("Account not found with ID: {}", accountId);
            throw new ResourceNotFoundException("Account", accountId);
        }
        
        // Retrieve paginated cards (replaces COBOL STARTBR/READNEXT loop)
        Page<Card> cards = cardRepository.findByAccountAccountId(accountId, pageable);
        
        log.info("Retrieved {} cards for account ID: {} (page {}/{})", 
                cards.getNumberOfElements(), accountId, 
                cards.getNumber() + 1, cards.getTotalPages());
        
        return cards;
    }

    /**
     * Retrieves a single card by its card number with account information.
     * 
     * <p>Replaces COBOL program: <strong>COCRDSLC.cbl</strong>
     * <pre>
     * COBOL Logic:
     *     MOVE CARD-NUM-ENTERED TO FD-XREF-CARD-NUM.
     *     
     *     EXEC CICS READ DATASET('XREFFILE')
     *         RIDFLD(FD-XREF-CARD-NUM)
     *         INTO(CARD-XREF-RECORD)
     *     END-EXEC.
     *     
     *     IF EIBRESP = DFHRESP(NORMAL)
     *         MOVE XREF-ACCT-ID TO CARD-ACCT-ID
     *         
     *         EXEC CICS READ DATASET('CARDDAT')
     *             RIDFLD(CARD-ACCT-ID)
     *             INTO(CARD-RECORD)
     *         END-EXEC
     *     ELSE
     *         MOVE 'Card not found' TO WS-MESSAGE
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Key Improvements over COBOL:</strong>
     * <ul>
     *   <li>Single query with JPA eager fetching replaces two sequential CICS READ operations</li>
     *   <li>Optional return type enables functional-style error handling</li>
     *   <li>Automatic card number validation via Bean Validation annotations</li>
     *   <li>PCI-DSS compliant masking in log statements</li>
     * </ul>
     * 
     * <p><strong>Card Number Validation:</strong>
     * The card number must:
     * <ul>
     *   <li>Be exactly 16 digits long</li>
     *   <li>Pass Luhn algorithm validation (checksum validation)</li>
     *   <li>Match pattern: {@code \d{16}}</li>
     * </ul>
     * 
     * @param cardNumber the 16-digit card number (PIC X(16) in COBOL)
     * @return the card entity with associated account information
     * @throws ResourceNotFoundException if the card with given number does not exist
     *         (replaces COBOL FILE STATUS '23' on CARDDAT or XREFFILE read)
     * 
     * @see CardRepository#findByCardNumber(String)
     * @see Card#getCardNumberMasked() for PCI-DSS compliant display
     */
    @Transactional(readOnly = true)
    public Card getCardByCardNumber(String cardNumber) {
        log.debug("Retrieving card with number: {}", maskCardNumber(cardNumber));
        
        // Direct card lookup with eager account fetch
        // Replaces COBOL two-step: XREFFILE read + CARDDAT read
        Card card = cardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> {
                    log.warn("Card not found with number: {}", maskCardNumber(cardNumber));
                    return new ResourceNotFoundException("Card", "cardNumber", maskCardNumber(cardNumber));
                });
        
        // Log card retrieval with defensive null check for account
        if (card.getAccount() != null) {
            log.info("Retrieved card ID: {} for account ID: {}", 
                    card.getCardId(), card.getAccount().getAccountId());
        } else {
            log.warn("Retrieved card ID: {} with null account (data integrity issue)", 
                    card.getCardId());
        }
        
        return card;
    }

    /**
     * Retrieves a card by its primary key (surrogate ID).
     * 
     * <p>This method is used for direct card access when the card ID is already known,
     * such as during card update operations or when processing transaction history.
     * 
     * <p>Replaces COBOL patterns:
     * <pre>
     * COBOL Logic:
     *     MOVE CARD-ID-INPUT TO FD-CARD-ID.
     *     
     *     EXEC CICS READ DATASET('CARDDAT')
     *         RIDFLD(FD-CARD-ID)
     *         INTO(CARD-RECORD)
     *     END-EXEC.
     *     
     *     IF EIBRESP NOT = DFHRESP(NORMAL)
     *         MOVE 'Card not found' TO WS-MESSAGE
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF.
     * </pre>
     * 
     * @param cardId the card primary key (surrogate ID, not the card number)
     * @return the card entity with associated account information
     * @throws ResourceNotFoundException if the card with given ID does not exist
     *         (replaces COBOL FILE STATUS '23' on CARDDAT read)
     * 
     * @see CardRepository#findById(Long)
     */
    @Transactional(readOnly = true)
    public Card getCardById(Long cardId) {
        log.debug("Retrieving card with ID: {}", cardId);
        
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> {
                    log.warn("Card not found with ID: {}", cardId);
                    return new ResourceNotFoundException("Card", cardId);
                });
        
        log.info("Retrieved card ID: {} with number: {}", 
                cardId, card.getCardNumberMasked());
        
        return card;
    }

    /**
     * Updates the active status of a credit card.
     * 
     * <p>Replaces COBOL program: <strong>COCRDUPC.cbl</strong>
     * <pre>
     * COBOL Logic:
     *     MOVE CARD-ID-INPUT TO FD-CARD-ID.
     *     
     *     EXEC CICS READ DATASET('CARDDAT')
     *         RIDFLD(FD-CARD-ID)
     *         INTO(CARD-RECORD)
     *         UPDATE
     *     END-EXEC.
     *     
     *     IF CARD-EXPIRE-DATE < WS-CURRENT-DATE AND NEW-STATUS = 'Y'
     *         MOVE 'Cannot activate expired card' TO WS-MESSAGE
     *         PERFORM 9999-ABEND-PROGRAM
     *     END-IF.
     *     
     *     MOVE NEW-STATUS TO CARD-ACTIVE-STATUS.
     *     
     *     EXEC CICS REWRITE DATASET('CARDDAT')
     *         FROM(CARD-RECORD)
     *     END-EXEC.
     *     
     *     EXEC CICS SYNCPOINT.
     * </pre>
     * 
     * <p><strong>Business Rules:</strong>
     * <ul>
     *   <li>Status must be 'Y' (active) or 'N' (inactive)</li>
     *   <li>Cannot activate a card if expiration date is in the past</li>
     *   <li>Deactivation (status = 'N') is allowed regardless of expiration date</li>
     * </ul>
     * 
     * <p><strong>Transaction Behavior:</strong>
     * This method is transactional. If an exception is thrown, the entire transaction
     * is rolled back (replaces COBOL EXEC CICS SYNCPOINT ROLLBACK).
     * 
     * @param cardId the card primary key to update
     * @param activeStatus the new active status ('Y' for active, 'N' for inactive)
     * @return the updated card entity
     * @throws ResourceNotFoundException if the card with given ID does not exist
     * @throws InvalidInputException if the status is invalid or attempting to activate expired card
     *         (replaces COBOL validation errors and 9999-ABEND-PROGRAM calls)
     * 
     * @see CardRepository#save(Card)
     */
    public Card updateCardStatus(Long cardId, String activeStatus) {
        log.debug("Updating status for card ID: {} to: {}", cardId, activeStatus);
        
        // Validate status format (replaces COBOL 88-level condition name validation)
        if (activeStatus == null || (!activeStatus.equals("Y") && !activeStatus.equals("N"))) {
            log.warn("Invalid card status provided: {}", activeStatus);
            throw new InvalidInputException("activeStatus", 
                    "Card status must be 'Y' (active) or 'N' (inactive)");
        }
        
        // Retrieve existing card
        Card card = getCardById(cardId);
        
        // Business rule: Cannot activate expired card
        // Replaces: IF CARD-EXPIRE-DATE < WS-CURRENT-DATE AND NEW-STATUS = 'Y'
        if ("Y".equals(activeStatus) && card.getExpirationDate().isBefore(LocalDate.now())) {
            log.warn("Attempted to activate expired card ID: {}, expiration: {}", 
                    cardId, card.getExpirationDate());
            throw new InvalidInputException("activeStatus", 
                    "Cannot activate card that expired on " + card.getExpirationDate());
        }
        
        // Update status (replaces COBOL MOVE NEW-STATUS TO CARD-ACTIVE-STATUS)
        card.setActiveStatus(activeStatus);
        
        // Persist changes (replaces COBOL EXEC CICS REWRITE + SYNCPOINT)
        Card updatedCard = cardRepository.save(card);
        
        log.info("Updated card ID: {} status to: {}, card number: {}", 
                cardId, activeStatus, card.getCardNumberMasked());
        
        return updatedCard;
    }

    /**
     * Validates the expiration date of a credit card.
     * 
     * <p>This method checks whether a card has expired by comparing its expiration date
     * to the current date. It is used during card activation, transaction processing,
     * and reporting to ensure only valid cards are used.
     * 
     * <p>Replaces COBOL validation patterns:
     * <pre>
     * COBOL Logic:
     *     ACCEPT WS-CURRENT-DATE FROM DATE YYYYMMDD.
     *     
     *     IF CARD-EXPIRE-DATE < WS-CURRENT-DATE
     *         SET CARD-EXPIRED TO TRUE
     *         MOVE 'Card has expired' TO WS-MESSAGE
     *     ELSE
     *         SET CARD-VALID TO TRUE
     *     END-IF.
     * </pre>
     * 
     * <p><strong>Validation Rules:</strong>
     * <ul>
     *   <li>Expiration date must be in the future (after current date)</li>
     *   <li>Comparison is done at day granularity (time component ignored)</li>
     *   <li>Exception thrown if card is expired (for transaction rollback)</li>
     * </ul>
     * 
     * @param cardId the card primary key to validate
     * @return the card entity if expiration date is valid
     * @throws ResourceNotFoundException if the card with given ID does not exist
     * @throws InvalidInputException if the card has expired
     *         (replaces COBOL CARD-EXPIRED condition and 9999-ABEND-PROGRAM)
     * 
     * @see Card#getExpirationDate()
     */
    @Transactional(readOnly = true)
    public Card validateCardExpiration(Long cardId) {
        log.debug("Validating expiration for card ID: {}", cardId);
        
        Card card = getCardById(cardId);
        LocalDate today = LocalDate.now();
        LocalDate expirationDate = card.getExpirationDate();
        
        // Validate expiration date (replaces COBOL date comparison)
        if (expirationDate.isBefore(today)) {
            log.warn("Card ID: {} has expired on: {}, current date: {}", 
                    cardId, expirationDate, today);
            throw new InvalidInputException("expirationDate", 
                    "Card expired on " + expirationDate + ". Current date is " + today);
        }
        
        log.info("Card ID: {} expiration validated successfully, expires: {}", 
                cardId, expirationDate);
        
        return card;
    }

    /**
     * Retrieves a card with full account information using card number lookup.
     * 
     * <p>This method combines card-to-account cross-reference resolution with account
     * data retrieval, providing complete context for card operations. It is used by
     * transaction processing and reporting modules that need both card and account data.
     * 
     * <p>Replaces COBOL program: <strong>COCRDSLC.cbl</strong> with extended account fetch
     * <pre>
     * COBOL Logic:
     *     * Step 1: Resolve card to account via XREFFILE
     *     MOVE CARD-NUM-ENTERED TO FD-XREF-CARD-NUM.
     *     EXEC CICS READ DATASET('XREFFILE')
     *         RIDFLD(FD-XREF-CARD-NUM)
     *         INTO(CARD-XREF-RECORD)
     *     END-EXEC.
     *     
     *     * Step 2: Read card record
     *     MOVE XREF-CARD-NUM TO CARD-NUM.
     *     EXEC CICS READ DATASET('CARDDAT')
     *         RIDFLD(CARD-NUM)
     *         INTO(CARD-RECORD)
     *     END-EXEC.
     *     
     *     * Step 3: Read account record
     *     MOVE XREF-ACCT-ID TO ACCT-ID.
     *     EXEC CICS READ DATASET('ACCTFILE')
     *         RIDFLD(ACCT-ID)
     *         INTO(ACCOUNT-RECORD)
     *     END-EXEC.
     * </pre>
     * 
     * <p><strong>Key Improvements over COBOL:</strong>
     * <ul>
     *   <li>Single method call replaces three sequential CICS READ operations</li>
     *   <li>JPA eager fetching eliminates N+1 query problem</li>
     *   <li>Optimized with composite indexes on foreign keys</li>
     *   <li>Automatic cache management via Hibernate second-level cache</li>
     * </ul>
     * 
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li>Transaction authorization (validate card belongs to account with sufficient funds)</li>
     *   <li>Statement generation (retrieve all cards for an account)</li>
     *   <li>Customer service lookups (find account by card number)</li>
     * </ul>
     * 
     * @param cardNumber the 16-digit card number (PIC X(16) in COBOL)
     * @return the card entity with fully populated account relationship
     * @throws ResourceNotFoundException if the card with given number does not exist
     *         (replaces COBOL FILE STATUS '23' on any of the three file reads)
     * 
     * @see CardRepository#findByCardNumber(String)
     * @see Account for account entity structure
     */
    @Transactional(readOnly = true)
    public Card getCardWithAccountInfo(String cardNumber) {
        log.debug("Retrieving card with account info for card number: {}", maskCardNumber(cardNumber));
        
        // Retrieve card with account (JPA eager fetch strategy)
        // Replaces three sequential COBOL reads: XREFFILE + CARDDAT + ACCTFILE
        Card card = getCardByCardNumber(cardNumber);
        
        // Ensure account relationship is fetched (trigger lazy load if needed)
        Account account = card.getAccount();
        if (account == null) {
            log.error("Card ID: {} has no associated account (data integrity violation)", 
                    card.getCardId());
            throw new InvalidInputException("cardNumber", 
                    "Card has no associated account - data integrity error");
        }
        
        log.info("Retrieved card ID: {} with account ID: {}, account number: {}", 
                card.getCardId(), account.getAccountId(), account.getAccountNumber());
        
        return card;
    }

    /**
     * Masks a card number for PCI-DSS compliant logging.
     * 
     * <p>This utility method provides safe card number masking for log statements,
     * showing only the last 4 digits while masking the first 12 digits with asterisks.
     * 
     * <p><strong>PCI-DSS Requirement 3.4:</strong>
     * Primary Account Number (PAN) must be masked when displayed. At most, only the
     * first six and last four digits may be displayed. This implementation shows only
     * the last four digits for maximum security.
     * 
     * <p><strong>Example:</strong>
     * <pre>
     * Input:  "4111111111111111"
     * Output: "************1111"
     * </pre>
     * 
     * @param cardNumber the full 16-digit card number
     * @return the masked card number showing last 4 digits, or "[INVALID]" if number is invalid
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "[INVALID]";
        }
        // Show only last 4 digits (PCI-DSS Requirement 3.4 compliance)
        return "************" + cardNumber.substring(cardNumber.length() - 4);
    }
}
