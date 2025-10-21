package com.aws.carddemo.service;

import com.aws.carddemo.dto.LoginRequest;
import com.aws.carddemo.dto.LoginResponse;
import com.aws.carddemo.model.User;
import com.aws.carddemo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    public LoginResponse authenticate(LoginRequest loginRequest) {
        String userId = loginRequest.getUserId().toUpperCase();
        String password = loginRequest.getPassword().toUpperCase();

        Optional<User> userOpt = userRepository.findByUserId(userId);

        if (userOpt.isEmpty()) {
            return new LoginResponse(false, "User not found. Try again...");
        }

        User user = userOpt.get();

        if (!user.getPassword().equals(password)) {
            return new LoginResponse(false, "Wrong Password. Try again...");
        }

        return new LoginResponse(
                user.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getUserType(),
                true,
                "Login successful"
        );
    }

    public boolean validateUser(String userId, String password) {
        Optional<User> userOpt = userRepository.findByUserIdAndPassword(
                userId.toUpperCase(),
                password.toUpperCase()
        );
        return userOpt.isPresent();
    }
}
