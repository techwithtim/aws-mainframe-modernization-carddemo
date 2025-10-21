package com.aws.carddemo.dto;

public class BillPaymentRequest {
    
    private Long accountId;
    private boolean confirmed;

    public BillPaymentRequest() {
    }

    public BillPaymentRequest(Long accountId, boolean confirmed) {
        this.accountId = accountId;
        this.confirmed = confirmed;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
