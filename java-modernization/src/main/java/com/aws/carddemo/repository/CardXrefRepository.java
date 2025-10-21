package com.aws.carddemo.repository;

import com.aws.carddemo.model.CardXref;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardXrefRepository extends JpaRepository<CardXref, String> {
    
    Optional<CardXref> findByCardNum(String cardNum);
    
    List<CardXref> findByAcctId(Long acctId);
    
    List<CardXref> findByCustId(Long custId);
}
