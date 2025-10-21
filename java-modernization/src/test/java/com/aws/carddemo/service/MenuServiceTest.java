package com.aws.carddemo.service;

import com.aws.carddemo.dto.MenuResponse;
import com.aws.carddemo.model.User;
import com.aws.carddemo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MenuService menuService;

    private User regularUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        regularUser = new User();
        regularUser.setUserId("USER001");
        regularUser.setFirstName("John");
        regularUser.setLastName("Doe");
        regularUser.setUserType("U");

        adminUser = new User();
        adminUser.setUserId("ADMIN001");
        adminUser.setFirstName("Admin");
        adminUser.setLastName("User");
        adminUser.setUserType("A");
    }

    @Test
    void testGetMainMenuForRegularUser() {
        when(userRepository.findByUserId("USER001")).thenReturn(Optional.of(regularUser));

        MenuResponse menu = menuService.getMainMenu("USER001");

        assertNotNull(menu);
        assertEquals("CardDemo Main Menu", menu.getMenuTitle());
        assertEquals("MAIN", menu.getMenuType());
        assertEquals("U", menu.getUserType());
        assertTrue(menu.getOptions().size() >= 4);
        verify(userRepository, times(1)).findByUserId("USER001");
    }

    @Test
    void testGetMainMenuForAdminUser() {
        when(userRepository.findByUserId("ADMIN001")).thenReturn(Optional.of(adminUser));

        MenuResponse menu = menuService.getMainMenu("ADMIN001");

        assertNotNull(menu);
        assertEquals("CardDemo Main Menu", menu.getMenuTitle());
        assertEquals("A", menu.getUserType());
        assertTrue(menu.getOptions().size() > 4);
        verify(userRepository, times(1)).findByUserId("ADMIN001");
    }

    @Test
    void testGetAdminMenu() {
        when(userRepository.findByUserId("ADMIN001")).thenReturn(Optional.of(adminUser));

        MenuResponse menu = menuService.getAdminMenu("ADMIN001");

        assertNotNull(menu);
        assertEquals("CardDemo Admin Menu", menu.getMenuTitle());
        assertEquals("ADMIN", menu.getMenuType());
        assertTrue(menu.getOptions().size() >= 7);
        verify(userRepository, times(1)).findByUserId("ADMIN001");
    }

    @Test
    void testGetAdminMenuThrowsExceptionForRegularUser() {
        when(userRepository.findByUserId("USER001")).thenReturn(Optional.of(regularUser));

        assertThrows(RuntimeException.class, () -> {
            menuService.getAdminMenu("USER001");
        });
    }

    @Test
    void testGetTransactionMenu() {
        when(userRepository.findByUserId("USER001")).thenReturn(Optional.of(regularUser));

        MenuResponse menu = menuService.getTransactionMenu("USER001");

        assertNotNull(menu);
        assertEquals("Transaction Menu", menu.getMenuTitle());
        assertEquals("TRANSACTION", menu.getMenuType());
        assertTrue(menu.getOptions().size() >= 4);
        verify(userRepository, times(1)).findByUserId("USER001");
    }
}
