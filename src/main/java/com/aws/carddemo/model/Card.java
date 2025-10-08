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
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Future;
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
import org.hibernate.validator.constraints.CreditCardNumber;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * JPA entity representing physical credit card information.
 * 
 * <p><b>Legacy Mapping:</b> Migrated from COBOL copybook {@code app/cpy/CVACT02Y.cpy}
 * (CARD-RECORD structure, 150-byte fixed-length record with 6 fields).
 * 
 * <p><b>COBOL to Java Field Mappings:</b>
 * <ul>
 *   <li>CARD-NUM (PIC X(16)) → cardNumber (String with @CreditCardNumber Luhn validation)</li>
 *   <li>CARD-ACCT-ID (PIC 9(11)) → account (@ManyToOne Account relationship)</li>
 *   <li>CARD-EMBOSSED-NAME (PIC X(50)) → embossedName (String @Size(max=50))</li>
 *   <li>CARD-EXPIRAION-DATE (PIC X(10)) → expirationDate (LocalDate with @Future validation)</li>
 *   <li>CARD-ACTIVE-STATUS (PIC X(01)) → activeStatus (String @Pattern("[YN]"))</li>
 *   <li>CARD-CVV-CD (PIC 9(03)) → <b>INTENTIONALLY EXCLUDED per PCI-DSS Requirement 3.2.2</b></li>
 * </ul>
 * 
 * <p><b>PCI-DSS Compliance:</b>
 * <ul>
 *   <li>Card number masked with @ToString.Exclude to prevent log exposure</li>
 *   <li>getCardNumberMasked() method provides "************1234" display format</li>
 *   <li>CVV codes MUST NOT be stored after authorization per PCI-DSS 3.2.2</li>
 * </ul>
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
    @CreditCardNumber(message = "Invalid card number per Luhn algorithm")
    @Column(name = "card_number", length = 16, unique = true, nullable = false)
    @ToString.Exclude
    private String cardNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    @NotNull(message = "Account is required")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Account account;

    @NotBlank(message = "Embossed name is required")
    @Size(max = 50, message = "Embossed name must not exceed 50 characters")
    @Column(name = "embossed_name", length = 50, nullable = false)
    private String embossedName;

    @NotNull(message = "Expiration date is required")
    @Future(message = "Expiration date must be in the future")
    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Pattern(regexp = "[YN]", message = "Active status must be 'Y' or 'N'")
    @Size(min = 1, max = 1, message = "Active status must be exactly 1 character")
    @Column(name = "active_status", length = 1)
    private String activeStatus;

    // CVV is NOT persisted per PCI-DSS Requirement 3.2.2 - field intentionally omitted
    // CARD-CVV-CD from COBOL copybook CVACT02Y.cpy is intentionally excluded from this entity

    /**
     * Returns masked card number for PCI-DSS compliant display (shows last 4 digits only).
     * 
     * <p><b>PCI-DSS Compliance:</b> This method provides a safe representation of the card number
     * that can be displayed in logs, UI, and error messages without violating PCI-DSS requirements.
     * Full card numbers must never be exposed in non-secure contexts.
     * 
     * @return masked card number in format "************1234" (12 asterisks + last 4 digits),
     *         or "****************" (16 asterisks) if card number is null or invalid
     */
    @Transient
    public String getCardNumberMasked() {
        if (cardNumber == null || cardNumber.length() != 16) {
            return "****************";
        }
        return "************" + cardNumber.substring(12);
    }
}
