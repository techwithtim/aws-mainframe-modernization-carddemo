package com.aws.carddemo.model;

public enum TransactionCategory {
    RETAIL(1001, "Retail Purchase"),
    GROCERY(1002, "Grocery"),
    GAS(1003, "Gas Station"),
    RESTAURANT(1004, "Restaurant"),
    ONLINE(1005, "Online Purchase"),
    PAYMENT(2001, "Payment"),
    CASH_ADVANCE(3001, "Cash Advance"),
    ATM_WITHDRAWAL(3002, "ATM Withdrawal"),
    REFUND(4001, "Refund"),
    CHARGEBACK(4002, "Chargeback"),
    ANNUAL_FEE(5001, "Annual Fee"),
    LATE_FEE(5002, "Late Fee"),
    OVERLIMIT_FEE(5003, "Over Limit Fee"),
    INTEREST_CHARGE(6001, "Interest Charge"),
    FINANCE_CHARGE(6002, "Finance Charge");

    private final int code;
    private final String description;

    TransactionCategory(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static TransactionCategory fromCode(int code) {
        for (TransactionCategory category : values()) {
            if (category.code == code) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown transaction category code: " + code);
    }
}
