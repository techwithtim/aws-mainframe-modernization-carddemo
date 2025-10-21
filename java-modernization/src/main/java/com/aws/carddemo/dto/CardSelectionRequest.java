package com.aws.carddemo.dto;

public class CardSelectionRequest {
    
    private String accountId;
    private String customerId;

    public CardSelectionRequest() {
    }

    public CardSelectionRequest(String accountId, String customerId) {
        this.accountId = accountId;
        this.customerId = customerId;
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
