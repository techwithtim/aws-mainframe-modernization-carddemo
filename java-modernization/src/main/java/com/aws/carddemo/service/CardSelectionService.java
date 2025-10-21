package com.aws.carddemo.service;

import com.aws.carddemo.dto.CardDTO;
import com.aws.carddemo.dto.CardSelectionRequest;
import com.aws.carddemo.dto.CardSelectionResponse;
import com.aws.carddemo.model.Card;
import com.aws.carddemo.model.CardXref;
import com.aws.carddemo.repository.CardRepository;
import com.aws.carddemo.repository.CardXrefRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CardSelectionService {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardXrefRepository cardXrefRepository;

    public CardSelectionResponse getCardsByAccount(String accountId) {
        List<CardXref> xrefs = cardXrefRepository.findByAcctId(accountId);
        List<CardDTO> cardDTOs = new ArrayList<>();

        for (CardXref xref : xrefs) {
            cardRepository.findByCardNumber(xref.getCardNum()).ifPresent(card -> {
                CardDTO dto = convertToDTO(card);
                cardDTOs.add(dto);
            });
        }

        return new CardSelectionResponse(cardDTOs, cardDTOs.size(), accountId, null);
    }

    public CardSelectionResponse getCardsByCustomer(String customerId) {
        List<CardXref> xrefs = cardXrefRepository.findByCustId(customerId);
        List<CardDTO> cardDTOs = new ArrayList<>();

        for (CardXref xref : xrefs) {
            cardRepository.findByCardNumber(xref.getCardNum()).ifPresent(card -> {
                CardDTO dto = convertToDTO(card);
                cardDTOs.add(dto);
            });
        }

        return new CardSelectionResponse(cardDTOs, cardDTOs.size(), null, customerId);
    }

    public CardSelectionResponse selectCards(CardSelectionRequest request) {
        if (request.getAccountId() != null && !request.getAccountId().isEmpty()) {
            return getCardsByAccount(request.getAccountId());
        } else if (request.getCustomerId() != null && !request.getCustomerId().isEmpty()) {
            return getCardsByCustomer(request.getCustomerId());
        } else {
            throw new IllegalArgumentException("Either accountId or customerId must be provided");
        }
    }

    private CardDTO convertToDTO(Card card) {
        CardDTO dto = new CardDTO();
        dto.setCardNumber(card.getCardNumber());
        dto.setCardType(card.getCardType());
        dto.setExpirationDate(card.getExpirationDate());
        dto.setActiveStatus(card.getActiveStatus());
        return dto;
    }
}
