package com.aws.carddemo.controller;

import com.aws.carddemo.dto.CardDTO;
import com.aws.carddemo.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Slf4j
public class CardController {

    private final CardService cardService;

    @GetMapping("/{cardNumber}")
    public ResponseEntity<CardDTO> getCardByNumber(@PathVariable String cardNumber) {
        log.info("GET /api/v1/cards/{}", cardNumber);
        CardDTO card = cardService.getCardByNumber(cardNumber);
        return ResponseEntity.ok(card);
    }

    @GetMapping
    public ResponseEntity<List<CardDTO>> getAllCards(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String status) {
        log.info("GET /api/v1/cards with accountId: {} and status: {}", accountId, status);
        List<CardDTO> cards;
        if (accountId != null) {
            cards = cardService.getCardsByAccountId(accountId);
        } else if (status != null && !status.isEmpty()) {
            cards = cardService.getCardsByStatus(status);
        } else {
            cards = cardService.getAllCards();
        }
        return ResponseEntity.ok(cards);
    }

    @PostMapping
    public ResponseEntity<CardDTO> createCard(@Valid @RequestBody CardDTO cardDTO) {
        log.info("POST /api/v1/cards");
        CardDTO createdCard = cardService.createCard(cardDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCard);
    }

    @PutMapping("/{cardNumber}")
    public ResponseEntity<CardDTO> updateCard(
            @PathVariable String cardNumber,
            @Valid @RequestBody CardDTO cardDTO) {
        log.info("PUT /api/v1/cards/{}", cardNumber);
        CardDTO updatedCard = cardService.updateCard(cardNumber, cardDTO);
        return ResponseEntity.ok(updatedCard);
    }

    @DeleteMapping("/{cardNumber}")
    public ResponseEntity<Void> deleteCard(@PathVariable String cardNumber) {
        log.info("DELETE /api/v1/cards/{}", cardNumber);
        cardService.deleteCard(cardNumber);
        return ResponseEntity.noContent().build();
    }
}
