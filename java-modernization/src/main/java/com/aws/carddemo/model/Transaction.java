package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @Column(name = "tran_id", length = 16)
    @Size(max = 16)
    private String transactionId;

    @NotNull
    @Size(max = 2)
    @Column(name = "tran_type_cd", length = 2, nullable = false)
    private String transactionTypeCode;

    @NotNull
    @Column(name = "tran_cat_cd", nullable = false)
    private Integer transactionCategoryCode;

    @Size(max = 10)
    @Column(name = "tran_source", length = 10)
    private String transactionSource;

    @Size(max = 100)
    @Column(name = "tran_desc", length = 100)
    private String description;

    @Column(name = "tran_amt", precision = 11, scale = 2)
    private BigDecimal amount;

    @Column(name = "tran_merchant_id")
    private Long merchantId;

    @Size(max = 50)
    @Column(name = "tran_merchant_name", length = 50)
    private String merchantName;

    @Size(max = 50)
    @Column(name = "tran_merchant_city", length = 50)
    private String merchantCity;

    @Size(max = 10)
    @Column(name = "tran_merchant_zip", length = 10)
    private String merchantZip;

    @NotNull
    @Size(max = 16)
    @Column(name = "tran_card_num", length = 16, nullable = false)
    private String cardNumber;

    @Column(name = "tran_orig_ts")
    private LocalDateTime originTimestamp;

    @Column(name = "tran_proc_ts")
    private LocalDateTime processTimestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tran_card_num", referencedColumnName = "card_num", insertable = false, updatable = false)
    private Card card;
}
