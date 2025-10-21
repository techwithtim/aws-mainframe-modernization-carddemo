package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "cards")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    @Id
    @Column(name = "card_num", length = 16)
    @Size(max = 16)
    private String cardNumber;

    @NotNull
    @Column(name = "card_acct_id", nullable = false)
    private Long accountId;

    @Column(name = "card_cvv_cd")
    private Integer cvvCode;

    @Size(max = 50)
    @Column(name = "card_embossed_name", length = 50)
    private String embossedName;

    @Column(name = "card_expiration_date")
    private LocalDate expirationDate;

    @NotNull
    @Size(max = 1)
    @Column(name = "card_active_status", length = 1, nullable = false)
    private String activeStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_acct_id", referencedColumnName = "acct_id", insertable = false, updatable = false)
    private Account account;
}
