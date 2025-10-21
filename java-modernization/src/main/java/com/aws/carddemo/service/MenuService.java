package com.aws.carddemo.service;

import com.aws.carddemo.dto.MenuOption;
import com.aws.carddemo.dto.MenuResponse;
import com.aws.carddemo.model.User;
import com.aws.carddemo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MenuService {

    @Autowired
    private UserRepository userRepository;

    public MenuResponse getMainMenu(String userId) {
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found: " + userId);
        }

        User user = userOpt.get();
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption("1", "Account Options", "/api/v1/accounts", true));
        options.add(new MenuOption("2", "Card Options", "/api/v1/cards", true));
        options.add(new MenuOption("3", "Bill Payment", "/api/v1/billpayment", true));
        options.add(new MenuOption("4", "Transaction History", "/api/v1/transactions", true));

        if (user.isAdmin()) {
            options.add(new MenuOption("5", "User Management", "/api/v1/users", true));
            options.add(new MenuOption("6", "Reports", "/api/v1/reports", true));
            options.add(new MenuOption("7", "Batch Operations", "/api/v1/batch", true));
        }

        options.add(new MenuOption("X", "Exit", "/api/v1/auth/logout", true));

        return new MenuResponse(
                "CardDemo Main Menu",
                "MAIN",
                options,
                user.getUserType(),
                user.getFirstName() + " " + user.getLastName()
        );
    }

    public MenuResponse getAdminMenu(String userId) {
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found: " + userId);
        }

        User user = userOpt.get();
        if (!user.isAdmin()) {
            throw new RuntimeException("Access denied: Admin privileges required");
        }

        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption("1", "User Management", "/api/v1/users", true));
        options.add(new MenuOption("2", "Account Management", "/api/v1/accounts", true));
        options.add(new MenuOption("3", "Card Management", "/api/v1/cards", true));
        options.add(new MenuOption("4", "Customer Management", "/api/v1/customers", true));
        options.add(new MenuOption("5", "Transaction Management", "/api/v1/transactions", true));
        options.add(new MenuOption("6", "Reports", "/api/v1/reports", true));
        options.add(new MenuOption("7", "Batch Operations", "/api/v1/batch", true));
        options.add(new MenuOption("X", "Return to Main Menu", "/api/v1/menu/main", true));

        return new MenuResponse(
                "CardDemo Admin Menu",
                "ADMIN",
                options,
                user.getUserType(),
                user.getFirstName() + " " + user.getLastName()
        );
    }

    public MenuResponse getTransactionMenu(String userId) {
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found: " + userId);
        }

        User user = userOpt.get();
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption("1", "View Transactions", "/api/v1/transactions", true));
        options.add(new MenuOption("2", "Add Transaction", "/api/v1/transactions", true));
        options.add(new MenuOption("3", "Transaction by Card", "/api/v1/transactions?cardNumber=", true));
        options.add(new MenuOption("4", "Transaction by Date Range", "/api/v1/transactions?startDate=&endDate=", true));
        options.add(new MenuOption("X", "Return to Main Menu", "/api/v1/menu/main", true));

        return new MenuResponse(
                "Transaction Menu",
                "TRANSACTION",
                options,
                user.getUserType(),
                user.getFirstName() + " " + user.getLastName()
        );
    }
}
