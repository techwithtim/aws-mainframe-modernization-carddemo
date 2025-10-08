package com.aws.carddemo.repository;

import com.aws.carddemo.model.CardXref;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for CardXref entity operations.
 * 
 * <p>Provides data access methods for the card cross-reference table,
 * supporting card-to-account and customer-to-cards lookups.
 * 
 * <p>Replaces COBOL VSAM file operations on XREFFILE with indexed queries.
 * 
 * @author CardDemo Modernization Team
 * @version 1.0.0
 */
@Repository
public interface CardXrefRepository extends JpaRepository<CardXref, String> {
    
    /**
     * Find all card cross-references for a specific account.
     * 
     * <p>Uses idx_xref_account B-tree index for efficient lookup.
     * Replaces COBOL VSAM AIX (alternate index) on XREF-ACCT-ID.
     * 
     * @param accountId the account ID to lookup
     * @return list of card cross-references for the account
     */
    List<CardXref> findByAccountId(Long accountId);
    
    /**
     * Find all card cross-references for a specific customer.
     * 
     * <p>Uses idx_xref_customer B-tree index for efficient lookup.
     * Replaces COBOL VSAM AIX (alternate index) on XREF-CUST-ID.
     * 
     * @param customerId the customer ID to lookup
     * @return list of card cross-references for the customer
     */
    List<CardXref> findByCustomerId(Long customerId);
}
