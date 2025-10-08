package com.aws.carddemo.repository;

import com.aws.carddemo.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for Account entity operations.
 * 
 * <p>Provides data access methods for account records,
 * supporting account lookup by ID and account number.
 * 
 * <p>Replaces COBOL VSAM file operations on ACCTFILE.
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    
    /**
     * Find account by account number.
     * 
     * @param accountNumber the account number to lookup
     * @return Optional containing the account if found
     */
    Optional<Account> findByAccountNumber(String accountNumber);
}
