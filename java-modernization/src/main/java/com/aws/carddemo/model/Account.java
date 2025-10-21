package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @Column(name = "acct_id", length = 11)
    private Long accountId;

    @NotNull
    @Size(max = 1)
    @Column(name = "acct_active_status", length = 1, nullable = false)
    private String activeStatus;

    @Column(name = "acct_curr_bal", precision = 12, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "acct_credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "acct_cash_credit_limit", precision = 12, scale = 2)
    private BigDecimal cashCreditLimit;

    @Column(name = "acct_open_date")
    private LocalDate openDate;

    @Column(name = "acct_expiration_date")
    private LocalDate expirationDate;

    @Column(name = "acct_reissue_date")
    private LocalDate reissueDate;

    @Column(name = "acct_curr_cyc_credit", precision = 12, scale = 2)
    private BigDecimal currentCycleCredit;

    @Column(name = "acct_curr_cyc_debit", precision = 12, scale = 2)
    private BigDecimal currentCycleDebit;

    @Size(max = 10)
    @Column(name = "acct_addr_zip", length = 10)
    private String addressZip;

    @Size(max = 10)
    @Column(name = "acct_group_id", length = 10)
    private String groupId;
}
