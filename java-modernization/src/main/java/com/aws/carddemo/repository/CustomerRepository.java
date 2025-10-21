package com.aws.carddemo.repository;

import com.aws.carddemo.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerId(Long customerId);

    @Query("SELECT c FROM Customer c WHERE c.lastName LIKE %:lastName%")
    List<Customer> findByLastNameContaining(@Param("lastName") String lastName);

    @Query("SELECT c FROM Customer c WHERE c.ssn = :ssn")
    Optional<Customer> findBySsn(@Param("ssn") Long ssn);

    boolean existsByCustomerId(Long customerId);
}
