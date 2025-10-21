package com.aws.carddemo.repository;

import com.aws.carddemo.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountId(Long accountId);

    List<Account> findByActiveStatus(String activeStatus);

    @Query("SELECT a FROM Account a WHERE a.addressZip = :zip")
    List<Account> findByAddressZip(@Param("zip") String zip);

    @Query("SELECT a FROM Account a WHERE a.groupId = :groupId")
    List<Account> findByGroupId(@Param("groupId") String groupId);

    boolean existsByAccountId(Long accountId);
}
