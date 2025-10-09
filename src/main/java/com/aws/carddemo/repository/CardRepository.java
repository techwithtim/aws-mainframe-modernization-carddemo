package com.aws.carddemo.repository;

import com.aws.carddemo.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Card entity.
 * 
 * <p>Provides data access operations for credit/debit card records, replacing
 * COBOL VSAM file operations on CARDFILE (CVACT02Y.cpy data structure).
 * 
 * <p><b>COBOL Migration Context:</b>
 * <ul>
 *   <li><b>Source File:</b> app/cbl/COCRDLIC.cbl (READ CARDFILE)</li>
 *   <li><b>Copybook:</b> app/cpy/CVACT02Y.cpy (CARD-RECORD, 150-byte structure)</li>
 *   <li><b>Legacy Operation:</b> READ CARDFILE KEY IS CARD-ID → findById(cardId)</li>
 *   <li><b>Legacy Operation:</b> STARTBR CARDFILE GENERIC KEY CARD-ACCT-ID → findByAccount_AccountId()</li>
 *   <li><b>Legacy Operation:</b> READ CARDFILE KEY IS CARD-NUM → findByCardNumber()</li>
 *   <li><b>Index Usage:</b> Utilizes idx_card_account, idx_card_expiration, uk_card_number indexes</li>
 * </ul>
 * 
 * <p><b>Custom Query Methods:</b>
 * <ul>
 *   <li>{@link #findByCardNumber(String)} - Lookup by unique 16-digit card number (uses uk_card_number)</li>
 *   <li>{@link #findByAccount_AccountId(Long)} - Find all cards for an account (uses idx_card_account)</li>
 *   <li>{@link #findByActiveStatus(String)} - Find all active or inactive cards</li>
 *   <li>{@link #findByActiveStatusAndExpirationDateBefore(String, LocalDate)} - Find expiring cards (uses idx_card_expiration partial index)</li>
 * </ul>
 * 
 * <p><b>Database Table:</b> card
 * <ul>
 *   <li>Primary Key: card_id (BIGINT IDENTITY) - Surrogate key</li>
 *   <li>Unique Key: card_number (VARCHAR(16), uk_card_number) - Business key</li>
 *   <li>Foreign Key: account_id → account(account_id) RESTRICT</li>
 * </ul>
 * 
 * <p><b>PCI-DSS Compliance:</b>
 * <ul>
 *   <li>Card numbers masked in logs via @ToString.Exclude annotation on entity</li>
 *   <li>CVV codes NOT persisted (excluded from entity per Requirement 3.2.2)</li>
 *   <li>Use {@link Card#getCardNumberMasked()} for display (shows ************1234)</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b>
 * <pre>
 * // Find card by unique card number (replaces COBOL READ KEY IS CARD-NUM)
 * Optional&lt;Card&gt; card = cardRepository.findByCardNumber("4532123456789012");
 * 
 * // Find all cards for an account (replaces COBOL AIX CXACAIX browse)
 * List&lt;Card&gt; accountCards = cardRepository.findByAccount_AccountId(accountId);
 * 
 * // Find active cards expiring within 60 days (uses partial index)
 * LocalDate sixtyDaysFromNow = LocalDate.now().plusDays(60);
 * List&lt;Card&gt; expiringCards = cardRepository.findByActiveStatusAndExpirationDateBefore("Y", sixtyDaysFromNow);
 * </pre>
 * 
 * <p>This repository is part of the data access layer for the modernized CardDemo application,
 * migrated from mainframe COBOL/CICS/VSAM to Java 21 + Spring Boot 3 + PostgreSQL.
 * 
 * @see Card
 * @see com.aws.carddemo.model.Account
 * @author CardDemo Modernization Team
 * @since 1.0.0
 */
@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    /**
     * Find a card by its unique 16-digit card number.
     * 
     * <p>Replaces COBOL operation:
     * <pre>
     * MOVE CARD-NUMBER TO CARD-NUM.
     * READ CARDFILE KEY IS CARD-NUM
     *     INVALID KEY ...
     *     NOT INVALID KEY ...
     * END-READ.
     * </pre>
     * 
     * <p><b>Index Used:</b> uk_card_number (unique index on card_number column)
     * <p><b>Query Performance:</b> O(log n) - B-tree index lookup, typically <10ms
     * 
     * @param cardNumber the 16-digit card number (PIC X(16) in COBOL)
     * @return Optional containing the card if found, empty otherwise
     */
    Optional<Card> findByCardNumber(String cardNumber);

    /**
     * Find all cards associated with a specific account.
     * 
     * <p>Replaces COBOL operation using VSAM Alternate Index (AIX):
     * <pre>
     * MOVE ACCOUNT-ID TO CARD-ACCT-ID.
     * EXEC CICS STARTBR
     *     FILE('CARDFILE')
     *     RIDFLD(CARD-ACCT-ID)
     *     GENERIC
     *     ...
     * END-EXEC.
     * </pre>
     * 
     * <p><b>Index Used:</b> idx_card_account (non-unique index on account_id column)
     * <p><b>Query Performance:</b> O(log n + k) where k = number of cards for account
     * <p><b>Typical Use Case:</b> Display all cards in "Card List" screen (COCRDLIC.cbl)
     * 
     * @param accountId the account primary key (PIC 9(11) in COBOL)
     * @return List of all cards linked to the account (empty list if none)
     */
    List<Card> findByAccount_AccountId(Long accountId);

    /**
     * Find all cards with a specific active status.
     * 
     * <p>Active status values:
     * <ul>
     *   <li>"Y" - Active card (can be used for transactions)</li>
     *   <li>"N" - Inactive card (blocked, expired, or closed)</li>
     * </ul>
     * 
     * <p><b>Index Used:</b> May use idx_card_expiration (partial index WHERE active_status='Y')
     * <p><b>Typical Use Case:</b> List all active cards for administrative reports
     * 
     * @param activeStatus the active status flag ("Y" or "N", PIC X(01) in COBOL)
     * @return List of all cards matching the status
     */
    List<Card> findByActiveStatus(String activeStatus);

    /**
     * Find all active cards expiring before a specific date.
     * 
     * <p>Used for proactive card renewal notifications - identifies cards that need
     * to be renewed before they expire.
     * 
     * <p>Replaces COBOL batch report logic:
     * <pre>
     * IF CARD-ACTIVE-STATUS = 'Y'
     *    IF CARD-EXPIRAION-DATE < WS-CUTOFF-DATE
     *       PERFORM PRINT-EXPIRING-CARD
     *    END-IF
     * END-IF.
     * </pre>
     * 
     * <p><b>Index Used:</b> idx_card_expiration (partial index: expiration_date WHERE active_status='Y')
     * <p><b>Query Performance:</b> Highly optimized for "expiring soon" queries via partial index
     * <p><b>Typical Use Case:</b> Batch job to identify cards expiring within 60 days
     * 
     * @param activeStatus the active status ("Y" for active cards only)
     * @param expirationDate the cutoff date (cards expiring before this date are returned)
     * @return List of active cards expiring before the specified date
     */
    List<Card> findByActiveStatusAndExpirationDateBefore(String activeStatus, LocalDate expirationDate);
}
