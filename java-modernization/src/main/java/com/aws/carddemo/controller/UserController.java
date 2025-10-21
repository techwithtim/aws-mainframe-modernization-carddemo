package com.aws.carddemo.controller;

import com.aws.carddemo.dto.UserDTO;
import com.aws.carddemo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable String userId) {
        UserDTO user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/type/{userType}")
    public ResponseEntity<List<UserDTO>> getUsersByType(@PathVariable String userType) {
        List<UserDTO> users = userService.getUsersByType(userType);
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody Map<String, String> request) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(request.get("userId"));
        userDTO.setFirstName(request.get("firstName"));
        userDTO.setLastName(request.get("lastName"));
        userDTO.setUserType(request.get("userType"));
        
        String password = request.get("password");
        UserDTO createdUser = userService.createUser(userDTO, password);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable String userId, @RequestBody UserDTO userDTO) {
        UserDTO updatedUser = userService.updateUser(userId, userDTO);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/password")
    public ResponseEntity<Void> updatePassword(@PathVariable String userId, @RequestBody Map<String, String> request) {
        String newPassword = request.get("newPassword");
        userService.updatePassword(userId, newPassword);
        return ResponseEntity.ok().build();
    }
}
