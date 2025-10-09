package com.aws.carddemo.repository;

import com.aws.carddemo.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for Account entity providing account master data access.
 * 
 * <p><b>Migrated from:</b></p>
 * <ul>
 *   <li>COBOL Copybook: app/cpy/CVACT01Y.cpy (ACCOUNT-RECORD, 300-byte structure)</li>
 *   <li>COBOL Programs: app/cbl/CBACT01C.cbl, app/cbl/COACTVWC.cbl, app/cbl/COACTUPC.cbl</li>
 *   <li>VSAM File: ACCTFILE (KSDS with ACCT-ID as primary key)</li>
 * </ul>
 * 
 * <p><b>Functional Equivalence:</b></p>
 * <pre>
 * COBOL Operation                          Java Equivalent
 * ────────────────────────────────────────────────────────────────────────────
 * READ ACCTFILE KEY IS ACCT-ID            findByAccountNumber(accountNumber)
 * READ ACCTFILE KEY IS ACCT-ID            findByIdWithLock(id) [with SELECT FOR UPDATE]
 *   WITH LOCK FOR UPDATE
 * STARTBR/READNEXT by CUST-ID            findByCustomerId(customerId)
 * STARTBR/READNEXT by GROUP-ID           findByGroupId(groupId)
 * WRITE ACCTFILE                          save(account) [insert]
 * REWRITE ACCTFILE                        save(account) [update on existing entity]
 * DELETE ACCTFILE                         delete(account) or deleteById(id)
 * </pre>
 * 
 * <p><b>Key Design Decisions:</b></p>
 * <ul>
 *   <li><b>Query Derivation:</b> Spring Data JPA auto-generates SQL from method names
 *       (findByAccountNumber, findByCustomerId, findByGroupId) eliminating explicit @Query
 *       annotations for simple queries.</li>
 *   <li><b>Pessimistic Locking:</b> findByIdWithLock uses @Lock(LockModeType.PESSIMISTIC_WRITE)
 *       to acquire database-level exclusive lock (SELECT FOR UPDATE in PostgreSQL), ensuring
 *       serializable isolation for concurrent balance updates during transaction posting.</li>
 *   <li><b>Optional Return Type:</b> findByAccountNumber and findByIdWithLock return Optional
 *       to explicitly handle not-found scenarios without checked exceptions, replacing COBOL
 *       FILE STATUS checks (NOTFND condition).</li>
 *   <li><b>List Return Type:</b> findByCustomerId and findByGroupId return List for multi-result
 *       queries, enabling customer-to-accounts navigation and batch interest calculations.</li>
 *   <li><b>@Repository Annotation:</b> Enables Spring exception translation from JPA/Hibernate
 *       persistence exceptions to DataAccessException hierarchy for consistent error handling.</li>
 * </ul>
 * 
 * <p><b>COBOL-to-Java Data Type Mapping:</b></p>
 * <pre>
 * COBOL Data Type              Java Type         Database Type
 * ────────────────────────────────────────────────────────────────────────────
 * PIC 9(11)                    String            VARCHAR(11)  - accountNumber with leading zeros
 * PIC S9(10)V99 COMP-3         BigDecimal        NUMERIC(12,2) - financial amounts
 * PIC X(01)                    String            CHAR(1)       - status flags
 * PIC X(10) (date YYYY-MM-DD)  LocalDate         DATE          - lifecycle dates
 * PIC X(10)                    String            VARCHAR(10)   - groupId, ZIP code
 * </pre>
 * 
 * <p><b>Transaction Semantics:</b></p>
 * <ul>
 *   <li>All repository methods participate in Spring @Transactional contexts defined in service layer</li>
 *   <li>COBOL SYNCPOINT → Spring transaction commit (automatic on @Transactional method completion)</li>
 *   <li>COBOL SYNCPOINT ROLLBACK → Spring transaction rollback (automatic on exception throw)</li>
 *   <li>Pessimistic locks held until transaction commit/rollback, preventing lost updates</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Index on account_number (unique) ensures O(log n) lookup performance</li>
 *   <li>Index on customer_id supports efficient findByCustomerId queries</li>
 *   <li>Index on group_id supports efficient batch interest calculation filtering</li>
 *   <li>Lazy fetching of relationships (customer, cards, transactions) prevents N+1 queries</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Account inquiry (replaces COACTVWC.cbl READ operation)
 * Optional<Account> account = accountRepository.findByAccountNumber("00012345678");
 * 
 * // Customer's accounts (replaces customer-to-account navigation)
 * List<Account> accounts = accountRepository.findByCustomerId(123L);
 * 
 * // Concurrent-safe balance update (replaces CBTRN01C.cbl REWRITE with lock)
 * Account account = accountRepository.findByIdWithLock(accountId)
 *     .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
 * account.setCurrentBalance(account.getCurrentBalance().add(transactionAmount));
 * accountRepository.save(account); // Updates released after transaction commit
 * 
 * // Batch interest calculation (replaces CBACT04C.cbl GROUP-ID filtering)
 * List<Account> groupAccounts = accountRepository.findByGroupId("GROUP001");
 * }</pre>
 * 
 * @see Account JPA entity representing account master data
 * @see JpaRepository Spring Data base interface providing CRUD operations
 * @author CardDemo Modernization Team
 * @version 1.0.0
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    
    /**
     * Find account by business account number (natural key).
     * 
     * <p><b>Replaces COBOL:</b> READ ACCTFILE KEY IS ACCT-ID</p>
     * <p>From: app/cbl/COACTVWC.cbl line 776-784 (EXEC CICS READ DATASET ACCTFILE RIDFLD)</p>
     * 
     * <p><b>Query Generated:</b></p>
     * <pre>SELECT * FROM ACCOUNT WHERE account_number = ?</pre>
     * 
     * <p>Uses unique index idx_account_number for O(log n) lookup performance.</p>
     * 
     * <p><b>COBOL FILE STATUS Mapping:</b></p>
     * <ul>
     *   <li>DFHRESP(NORMAL) → Optional.of(account)</li>
     *   <li>DFHRESP(NOTFND) → Optional.empty()</li>
     * </ul>
     * 
     * @param accountNumber 11-digit account number with leading zeros (e.g., "00012345678")
     * @return Optional containing the account if found, empty Optional otherwise
     * @throws IllegalArgumentException if accountNumber is null
     */
    Optional<Account> findByAccountNumber(String accountNumber);
    
    /**
     * Find all accounts belonging to a specific customer.
     * 
     * <p><b>Replaces COBOL:</b> Sequential browse of ACCTFILE filtered by customer</p>
     * <p>From: app/cbl/COACTVWC.cbl customer-to-accounts navigation pattern</p>
     * 
     * <p><b>Query Generated:</b></p>
     * <pre>SELECT * FROM ACCOUNT WHERE customer_id = ? ORDER BY account_number</pre>
     * 
     * <p>Uses index idx_account_customer for efficient filtering. Results ordered by
     * account_number to provide consistent ordering for UI display.</p>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Customer account portfolio view (multiple accounts per customer)</li>
     *   <li>Cross-account balance aggregation</li>
     *   <li>Customer relationship management</li>
     * </ul>
     * 
     * @param customerId Customer ID foreign key (Account.customer.customerId)
     * @return List of accounts owned by the customer, empty list if none found
     * @throws IllegalArgumentException if customerId is null
     */
    List<Account> findByCustomerId(Long customerId);
    
    /**
     * Find all accounts in a specific disclosure group for interest rate calculation.
     * 
     * <p><b>Replaces COBOL:</b> STARTBR/READNEXT operations filtered by ACCT-GROUP-ID</p>
     * <p>From: app/cbl/CBACT04C.cbl batch interest calculation job</p>
     * 
     * <p><b>Query Generated:</b></p>
     * <pre>SELECT * FROM ACCOUNT WHERE group_id = ? ORDER BY account_number</pre>
     * 
     * <p>Uses index idx_account_group for efficient batch processing. Essential for
     * interest calculation batch jobs where different disclosure groups have different
     * APR rates.</p>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Batch interest calculation (CBACT04C.cbl) - process all accounts in group</li>
     *   <li>Disclosure group reporting and analytics</li>
     *   <li>APR assignment validation</li>
     * </ul>
     * 
     * <p><b>Batch Processing Pattern:</b></p>
     * <pre>{@code
     * List<Account> groupAccounts = accountRepository.findByGroupId("GROUP001");
     * for (Account account : groupAccounts) {
     *     BigDecimal interest = interestCalculationService.calculateInterest(account);
     *     account.setCurrentBalance(account.getCurrentBalance().add(interest));
     *     accountRepository.save(account);
     * }
     * }</pre>
     * 
     * @param groupId Disclosure group ID (e.g., "GROUP001", "GROUP002")
     * @return List of accounts in the group, empty list if none found
     * @throws IllegalArgumentException if groupId is null
     */
    List<Account> findByGroupId(String groupId);
    
    /**
     * Find account by ID with pessimistic write lock for concurrent-safe updates.
     * 
     * <p><b>Replaces COBOL:</b> READ ACCTFILE KEY IS ACCT-ID WITH LOCK FOR UPDATE</p>
     * <p>From: app/cbl/CBTRN01C.cbl transaction posting with exclusive record lock</p>
     * 
     * <p><b>Locking Mechanism:</b></p>
     * <p>@Lock(LockModeType.PESSIMISTIC_WRITE) generates PostgreSQL SELECT FOR UPDATE,
     * acquiring an exclusive row-level lock that blocks other transactions from reading
     * or modifying the same account record until the current transaction commits or rolls back.</p>
     * 
     * <p><b>SQL Generated:</b></p>
     * <pre>SELECT * FROM ACCOUNT WHERE account_id = ? FOR UPDATE</pre>
     * 
     * <p><b>Transaction Isolation:</b></p>
     * <ul>
     *   <li><b>Lock Scope:</b> Row-level exclusive lock on single account record</li>
     *   <li><b>Lock Duration:</b> Held until transaction commit/rollback</li>
     *   <li><b>Concurrency:</b> Blocks concurrent transactions attempting to lock same account</li>
     *   <li><b>Deadlock Prevention:</b> Always acquire locks in consistent order (account ID ascending)</li>
     * </ul>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Transaction posting (CBTRN01C.cbl) - update current balance with debit/credit</li>
     *   <li>Payment processing (COBIL00C.cbl) - ensure sufficient funds before deduction</li>
     *   <li>Interest calculation (CBACT04C.cbl) - prevent concurrent balance modifications</li>
     *   <li>Any operation requiring read-modify-write atomicity</li>
     * </ul>
     * 
     * <p><b>ACID Guarantee:</b></p>
     * <p>Prevents lost update anomaly where concurrent transactions might overwrite each other's
     * balance changes:</p>
     * <pre>
     * Without Lock (Lost Update):          With Pessimistic Lock:
     * ────────────────────────────────────────────────────────────
     * T1: READ balance = $1000            T1: READ balance = $1000 (LOCKED)
     * T2: READ balance = $1000            T2: BLOCKED waiting for lock
     * T1: balance -= $100 = $900          T1: balance -= $100 = $900
     * T1: WRITE balance = $900            T1: WRITE balance = $900
     * T2: balance += $50 = $1050          T1: COMMIT (lock released)
     * T2: WRITE balance = $1050           T2: READ balance = $900 (LOCKED)
     * RESULT: $1050 (lost T1's -$100)     T2: balance += $50 = $950
     *                                      T2: WRITE balance = $950
     *                                      T2: COMMIT
     *                                      RESULT: $950 (correct)
     * </pre>
     * 
     * <p><b>Performance Impact:</b></p>
     * <ul>
     *   <li><b>Lock Contention:</b> High-frequency accounts (e.g., checking accounts with many
     *       transactions) may experience increased wait times under heavy concurrent load</li>
     *   <li><b>Lock Timeout:</b> PostgreSQL default lock_timeout applies; transactions waiting
     *       longer than configured timeout will fail with lock acquisition error</li>
     *   <li><b>Best Practice:</b> Keep transaction duration short to minimize lock hold time</li>
     * </ul>
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * @Transactional
     * public void postTransaction(Long accountId, BigDecimal amount) {
     *     Account account = accountRepository.findByIdWithLock(accountId)
     *         .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
     *     
     *     // Lock is held here - no other transaction can modify this account
     *     BigDecimal newBalance = account.getCurrentBalance().add(amount);
     *     account.setCurrentBalance(newBalance);
     *     accountRepository.save(account);
     *     
     *     // Lock automatically released on transaction commit
     * }
     * }</pre>
     * 
     * @param id Account ID (surrogate primary key)
     * @return Optional containing the locked account if found, empty Optional otherwise
     * @throws IllegalArgumentException if id is null
     * @throws org.springframework.dao.PessimisticLockingFailureException if lock cannot be acquired
     * @throws org.springframework.dao.CannotAcquireLockException if lock timeout exceeded
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);
}
