package com.aws.carddemo.service;

import com.aws.carddemo.dto.CardDTO;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Card;
import com.aws.carddemo.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CardService {

    private final CardRepository cardRepository;

    public CardDTO getCardByNumber(String cardNumber) {
        log.info("Fetching card with number: {}", cardNumber);
        Card card = cardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with number: " + cardNumber));
        return mapToDTO(card);
    }

    public List<CardDTO> getCardsByAccountId(Long accountId) {
        log.info("Fetching cards for account ID: {}", accountId);
        return cardRepository.findByAccountId(accountId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<CardDTO> getAllCards() {
        log.info("Fetching all cards");
        return cardRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<CardDTO> getCardsByStatus(String status) {
        log.info("Fetching cards with status: {}", status);
        return cardRepository.findByActiveStatus(status).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public CardDTO createCard(CardDTO cardDTO) {
        log.info("Creating new card with number: {}", cardDTO.getCardNumber());
        if (cardRepository.existsByCardNumber(cardDTO.getCardNumber())) {
            throw new IllegalArgumentException("Card already exists with number: " + cardDTO.getCardNumber());
        }
        Card card = mapToEntity(cardDTO);
        Card savedCard = cardRepository.save(card);
        return mapToDTO(savedCard);
    }

    public CardDTO updateCard(String cardNumber, CardDTO cardDTO) {
        log.info("Updating card with number: {}", cardNumber);
        Card existingCard = cardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with number: " + cardNumber));

        existingCard.setAccountId(cardDTO.getAccountId());
        existingCard.setCvvCode(cardDTO.getCvvCode());
        existingCard.setEmbossedName(cardDTO.getEmbossedName());
        existingCard.setExpirationDate(cardDTO.getExpirationDate());
        existingCard.setActiveStatus(cardDTO.getActiveStatus());

        Card updatedCard = cardRepository.save(existingCard);
        return mapToDTO(updatedCard);
    }

    public void deleteCard(String cardNumber) {
        log.info("Deleting card with number: {}", cardNumber);
        if (!cardRepository.existsByCardNumber(cardNumber)) {
            throw new ResourceNotFoundException("Card not found with number: " + cardNumber);
        }
        cardRepository.deleteById(cardNumber);
    }

    private CardDTO mapToDTO(Card card) {
        CardDTO dto = new CardDTO();
        dto.setCardNumber(card.getCardNumber());
        dto.setAccountId(card.getAccountId());
        dto.setCvvCode(card.getCvvCode());
        dto.setEmbossedName(card.getEmbossedName());
        dto.setExpirationDate(card.getExpirationDate());
        dto.setActiveStatus(card.getActiveStatus());
        return dto;
    }

    private Card mapToEntity(CardDTO dto) {
        Card card = new Card();
        card.setCardNumber(dto.getCardNumber());
        card.setAccountId(dto.getAccountId());
        card.setCvvCode(dto.getCvvCode());
        card.setEmbossedName(dto.getEmbossedName());
        card.setExpirationDate(dto.getExpirationDate());
        card.setActiveStatus(dto.getActiveStatus());
        return card;
    }
}
