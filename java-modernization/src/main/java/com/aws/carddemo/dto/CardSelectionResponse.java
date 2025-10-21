package com.aws.carddemo.dto;

import java.util.List;

public class CardSelectionResponse {
    
    private List<CardDTO> cards;
    private int totalCards;
    private String accountId;
    private String customerId;

    public CardSelectionResponse() {
    }

    public CardSelectionResponse(List<CardDTO> cards, int totalCards, String accountId, String customerId) {
        this.cards = cards;
        this.totalCards = totalCards;
        this.accountId = accountId;
        this.customerId = customerId;
    }

    public List<CardDTO> getCards() {
        return cards;
    }

    public void setCards(List<CardDTO> cards) {
        this.cards = cards;
    }

    public int getTotalCards() {
        return totalCards;
    }

    public void setTotalCards(int totalCards) {
        this.totalCards = totalCards;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}
