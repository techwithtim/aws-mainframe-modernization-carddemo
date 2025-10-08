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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * JPA entity representing physical credit card information.
 * 
 * <p><b>Legacy Mapping:</b> Migrated from COBOL copybook {@code app/cpy/CVACT02Y.cpy}
 * (CARD-RECORD structure, 150-byte fixed-length record).
 * 
 * @see BaseEntity
 * @see Account
 */
@Entity
@Table(
    name = "card",
    indexes = {
        @Index(name = "idx_card_number", columnList = "card_number", unique = true),
        @Index(name = "idx_card_account", columnList = "account_id")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Card extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @NotBlank(message = "Card number is required")
    @Size(min = 16, max = 16, message = "Card number must be exactly 16 digits")
    @Pattern(regexp = "\\d{16}", message = "Card number must contain only digits")
    @Column(name = "card_number", length = 16, unique = true, nullable = false)
    @ToString.Exclude
    private String cardNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    @NotNull(message = "Account is required")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Account account;

    @Pattern(regexp = "[YN]", message = "Active status must be 'Y' or 'N'")
    @Size(min = 1, max = 1, message = "Active status must be exactly 1 character")
    @Column(name = "active_status", length = 1)
    private String activeStatus;

    @Pattern(regexp = "\\d{3}", message = "CVV must be exactly 3 digits")
    @Size(min = 3, max = 3, message = "CVV must be exactly 3 digits")
    @Column(name = "cvv", length = 3)
    @ToString.Exclude
    private String cvv;

    /**
     * Returns masked card number for safe display (shows last 4 digits only).
     * 
     * @return masked card number in format "************1234"
     */
    public String getCardNumberMasked() {
        if (cardNumber == null || cardNumber.length() != 16) {
            return "****************";
        }
        return "************" + cardNumber.substring(12);
    }
}
