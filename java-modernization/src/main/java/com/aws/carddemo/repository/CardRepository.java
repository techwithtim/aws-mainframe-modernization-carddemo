package com.aws.carddemo.repository;

import com.aws.carddemo.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, String> {

    Optional<Card> findByCardNumber(String cardNumber);

    List<Card> findByAccountId(Long accountId);

    List<Card> findByActiveStatus(String activeStatus);

    @Query("SELECT c FROM Card c WHERE c.accountId = :accountId AND c.activeStatus = :status")
    List<Card> findByAccountIdAndStatus(@Param("accountId") Long accountId, @Param("status") String status);

    boolean existsByCardNumber(String cardNumber);
}
