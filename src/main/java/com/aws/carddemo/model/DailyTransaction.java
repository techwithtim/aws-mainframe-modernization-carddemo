package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity class representing daily transaction feed input for Spring Batch processing.
 * Migrated from: app/cpy/CVTRA06Y.cpy (DALYTRAN-RECORD structure)
 * 
 * This entity serves as a staging table for batch processing, holding transaction data
 * that is read by DailyTransactionReader, validated by TransactionProcessor, and written
 * to the transaction table by TransactionWriter after successful validation.
 * 
 * Record Structure: 350-byte COBOL record with 18 fields mirroring TRAN-RECORD
 * Batch Processing: Chunk-oriented with chunk size 100
 * Processing Flow: daily_transaction → validation → transaction table
 * 
 * @see com.aws.carddemo.batch.reader.DailyTransactionReader
 * @see com.aws.carddemo.batch.processor.TransactionProcessor
 * @see com.aws.carddemo.batch.writer.TransactionWriter
 */
@Entity
@Table(name = "daily_transaction", indexes = {
    @Index(name = "idx_daily_transaction_status", columnList = "processing_status"),
    @Index(name = "idx_daily_transaction_card", columnList = "card_number")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"cardNumber"})
public class DailyTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key: Surrogate key not in COBOL copybook
     * Database: BIGINT IDENTITY
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_transaction_id", nullable = false)
    private Long dailyTransactionId;

    /**
     * Business key: DALYTRAN-ID PIC X(16)
     * Transaction identifier for tracking
     */
    @Column(name = "transaction_id", length = 16, nullable = false)
    @NotBlank(message = "Transaction ID is required")
    @Size(max = 16, message = "Transaction ID cannot exceed 16 characters")
    private String transactionId;

    /**
     * DALYTRAN-TYPE-CD PIC X(02)
     * Transaction type code (e.g., "01" = Purchase, "02" = Cash Advance)
     */
    @Column(name = "transaction_type_code", length = 2, nullable = false)
    @NotBlank(message = "Transaction type code is required")
    @Size(max = 2, message = "Transaction type code must be 2 characters")
    @Pattern(regexp = "\\d{2}", message = "Transaction type code must be 2 digits")
    private String transactionTypeCode;

    /**
     * DALYTRAN-CAT-CD PIC 9(04)
     * Transaction category code (e.g., "0001" = Grocery, "0002" = Gas)
     */
    @Column(name = "transaction_category_code", length = 4, nullable = false)
    @NotBlank(message = "Transaction category code is required")
    @Size(max = 4, message = "Transaction category code must be 4 characters")
    @Pattern(regexp = "\\d{4}", message = "Transaction category code must be 4 digits")
    private String transactionCategoryCode;

    /**
     * DALYTRAN-SOURCE PIC X(10)
     * Transaction source system identifier
     */
    @Column(name = "transaction_source", length = 10)
    @Size(max = 10, message = "Transaction source cannot exceed 10 characters")
    private String transactionSource;

    /**
     * DALYTRAN-DESC PIC X(100)
     * Transaction description (merchant transaction description)
     */
    @Column(name = "description", length = 100, nullable = false)
    @NotBlank(message = "Description is required")
    @Size(max = 100, message = "Description cannot exceed 100 characters")
    private String description;

    /**
     * DALYTRAN-AMT PIC S9(09)V99
     * Transaction amount (monetary value)
     * Mapped to BigDecimal for exact decimal precision per PIC S9(09)V99
     * Database: NUMERIC(11,2)
     */
    @Column(name = "amount", precision = 11, scale = 2, nullable = false)
    @NotNull(message = "Amount is required")
    @Digits(integer = 9, fraction = 2, message = "Amount must have at most 9 integer digits and 2 decimal places")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    /**
     * DALYTRAN-MERCHANT-ID PIC 9(09)
     * Merchant identifier (9-digit numeric merchant ID)
     */
    @Column(name = "merchant_id", length = 9)
    @Size(max = 9, message = "Merchant ID cannot exceed 9 characters")
    @Pattern(regexp = "\\d{1,9}", message = "Merchant ID must be numeric")
    private String merchantId;

    /**
     * DALYTRAN-MERCHANT-NAME PIC X(50)
     * Merchant business name
     */
    @Column(name = "merchant_name", length = 50)
    @Size(max = 50, message = "Merchant name cannot exceed 50 characters")
    private String merchantName;

    /**
     * DALYTRAN-MERCHANT-CITY PIC X(50)
     * Merchant city location
     */
    @Column(name = "merchant_city", length = 50)
    @Size(max = 50, message = "Merchant city cannot exceed 50 characters")
    private String merchantCity;

    /**
     * DALYTRAN-MERCHANT-ZIP PIC X(10)
     * Merchant ZIP code (supports both 5-digit and ZIP+4 formats)
     */
    @Column(name = "merchant_zip", length = 10)
    @Size(max = 10, message = "Merchant ZIP code cannot exceed 10 characters")
    @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "Merchant ZIP must be in format 12345 or 12345-6789")
    private String merchantZip;

    /**
     * DALYTRAN-CARD-NUM PIC X(16)
     * Card number (16-digit credit card number)
     * PCI-DSS Compliance: Excluded from toString() via @ToString.Exclude
     * Use getCardNumberMasked() for logging/display purposes
     */
    @Column(name = "card_number", length = 16, nullable = false)
    @NotBlank(message = "Card number is required")
    @Size(min = 16, max = 16, message = "Card number must be exactly 16 digits")
    @Pattern(regexp = "\\d{16}", message = "Card number must be 16 digits")
    private String cardNumber;

    /**
     * DALYTRAN-ORIG-TS PIC X(26)
     * Original timestamp (merchant authorization time)
     * Mapped from COBOL PIC X(26) timestamp string to LocalDateTime
     */
    @Column(name = "original_timestamp", nullable = false)
    @NotNull(message = "Original timestamp is required")
    @PastOrPresent(message = "Original timestamp cannot be in the future")
    private LocalDateTime originalTimestamp;

    /**
     * DALYTRAN-PROC-TS PIC X(26)
     * Processing timestamp (system processing time)
     * Mapped from COBOL PIC X(26) timestamp string to LocalDateTime
     */
    @Column(name = "processing_timestamp")
    private LocalDateTime processingTimestamp;

    /**
     * Processing status for batch job restart capability
     * Values: PENDING, PROCESSED, FAILED
     * Not in COBOL copybook - added for Spring Batch processing control
     */
    @Column(name = "processing_status", length = 10, nullable = false)
    @NotBlank(message = "Processing status is required")
    @Pattern(regexp = "PENDING|PROCESSED|FAILED", message = "Processing status must be PENDING, PROCESSED, or FAILED")
    @Builder.Default
    private String processingStatus = "PENDING";

    /**
     * PCI-DSS compliant card number display method.
     * Returns masked card number showing only last 4 digits.
     * 
     * @return Masked card number in format "************1234"
     */
    @Transient
    public String getCardNumberMasked() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "************";
        }
        return "************" + cardNumber.substring(12);
    }

    /**
     * PrePersist lifecycle callback to set default values
     */
    @PrePersist
    protected void onCreate() {
        if (processingStatus == null) {
            processingStatus = "PENDING";
        }
        if (processingTimestamp == null) {
            processingTimestamp = LocalDateTime.now();
        }
    }
}
