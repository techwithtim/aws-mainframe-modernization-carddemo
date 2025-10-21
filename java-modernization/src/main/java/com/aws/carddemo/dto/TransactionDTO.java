package com.aws.carddemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {
    private String transactionId;
    private String transactionTypeCode;
    private Integer transactionCategoryCode;
    private String transactionSource;
    private String description;
    private BigDecimal amount;
    private Long merchantId;
    private String merchantName;
    private String merchantCity;
    private String merchantZip;
    private String cardNumber;
    private LocalDateTime originTimestamp;
    private LocalDateTime processTimestamp;
}
