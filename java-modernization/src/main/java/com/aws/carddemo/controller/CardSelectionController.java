package com.aws.carddemo.controller;

import com.aws.carddemo.dto.CardSelectionRequest;
import com.aws.carddemo.dto.CardSelectionResponse;
import com.aws.carddemo.service.CardSelectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/card-selection")
public class CardSelectionController {

    @Autowired
    private CardSelectionService cardSelectionService;

    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<CardSelectionResponse> getCardsByAccount(@PathVariable String accountId) {
        CardSelectionResponse response = cardSelectionService.getCardsByAccount(accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-customer/{customerId}")
    public ResponseEntity<CardSelectionResponse> getCardsByCustomer(@PathVariable String customerId) {
        CardSelectionResponse response = cardSelectionService.getCardsByCustomer(customerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/select")
    public ResponseEntity<CardSelectionResponse> selectCards(@RequestBody CardSelectionRequest request) {
        CardSelectionResponse response = cardSelectionService.selectCards(request);
        return ResponseEntity.ok(response);
    }
}
