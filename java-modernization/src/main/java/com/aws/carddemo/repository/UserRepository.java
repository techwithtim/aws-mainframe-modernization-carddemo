package com.aws.carddemo.repository;

import com.aws.carddemo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByUserId(String userId);
    
    List<User> findByUserType(String userType);
    
    Optional<User> findByUserIdAndPassword(String userId, String password);
}
