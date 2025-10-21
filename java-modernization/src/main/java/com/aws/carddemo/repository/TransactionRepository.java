package com.aws.carddemo.repository;

import com.aws.carddemo.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByTransactionId(String transactionId);

    List<Transaction> findByCardNumber(String cardNumber);

    @Query("SELECT t FROM Transaction t WHERE t.cardNumber = :cardNumber AND t.originTimestamp BETWEEN :startDate AND :endDate")
    List<Transaction> findByCardNumberAndDateRange(
            @Param("cardNumber") String cardNumber,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT t FROM Transaction t WHERE t.transactionTypeCode = :typeCode")
    List<Transaction> findByTransactionTypeCode(@Param("typeCode") String typeCode);

    @Query("SELECT t FROM Transaction t WHERE t.transactionCategoryCode = :categoryCode")
    List<Transaction> findByTransactionCategoryCode(@Param("categoryCode") Integer categoryCode);

    boolean existsByTransactionId(String transactionId);
    
    Transaction findTopByOrderByTransactionIdDesc();
}
