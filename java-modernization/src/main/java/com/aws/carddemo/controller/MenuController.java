package com.aws.carddemo.controller;

import com.aws.carddemo.dto.MenuResponse;
import com.aws.carddemo.service.MenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/menu")
public class MenuController {

    @Autowired
    private MenuService menuService;

    @GetMapping("/main")
    public ResponseEntity<MenuResponse> getMainMenu(@RequestParam String userId) {
        MenuResponse menu = menuService.getMainMenu(userId);
        return ResponseEntity.ok(menu);
    }

    @GetMapping("/admin")
    public ResponseEntity<MenuResponse> getAdminMenu(@RequestParam String userId) {
        MenuResponse menu = menuService.getAdminMenu(userId);
        return ResponseEntity.ok(menu);
    }

    @GetMapping("/transaction")
    public ResponseEntity<MenuResponse> getTransactionMenu(@RequestParam String userId) {
        MenuResponse menu = menuService.getTransactionMenu(userId);
        return ResponseEntity.ok(menu);
    }
}
