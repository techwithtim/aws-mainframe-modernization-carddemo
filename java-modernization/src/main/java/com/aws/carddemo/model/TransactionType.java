package com.aws.carddemo.model;

public enum TransactionType {
    PURCHASE("01", "Purchase"),
    PAYMENT("02", "Payment"),
    CASH_ADVANCE("03", "Cash Advance"),
    REFUND("04", "Refund"),
    FEE("05", "Fee"),
    INTEREST("06", "Interest");

    private final String code;
    private final String description;

    TransactionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static TransactionType fromCode(String code) {
        for (TransactionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown transaction type code: " + code);
    }
}
