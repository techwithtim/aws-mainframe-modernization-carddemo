package com.aws.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing credit card transaction records.
 * 
 * <p><b>Legacy Mapping:</b> Migrated from COBOL copybook {@code app/cpy/CVTRA05Y.cpy}
 * (TRANSACTION-RECORD structure, 350-byte fixed-length record).
 * 
 * @see BaseEntity
 * @see Account
 */
@Entity
@Table(
    name = "transaction",
    indexes = {
        @Index(name = "idx_transaction_account", columnList = "account_id"),
        @Index(name = "idx_transaction_date", columnList = "transaction_date"),
        @Index(name = "idx_transaction_type", columnList = "transaction_type")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Transaction extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    @NotNull(message = "Account is required")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Account account;

    @NotBlank(message = "Transaction type is required")
    @Size(max = 50, message = "Transaction type cannot exceed 50 characters")
    @Column(name = "transaction_type", length = 50, nullable = false)
    private String transactionType;

    @NotNull(message = "Transaction amount is required")
    @Digits(integer = 10, fraction = 2, message = "Transaction amount must have at most 10 integer digits and 2 fraction digits")
    @Column(name = "transaction_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal transactionAmount;

    @NotNull(message = "Transaction date is required")
    @PastOrPresent(message = "Transaction date must be in the past or present")
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Size(max = 200, message = "Transaction description cannot exceed 200 characters")
    @Column(name = "transaction_description", length = 200)
    private String transactionDescription;

    @Size(max = 50, message = "Merchant name cannot exceed 50 characters")
    @Column(name = "merchant_name", length = 50)
    private String merchantName;

    @Size(max = 100, message = "Merchant category cannot exceed 100 characters")
    @Column(name = "merchant_category", length = 100)
    private String merchantCategory;
}
