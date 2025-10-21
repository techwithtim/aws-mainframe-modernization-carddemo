package com.aws.carddemo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @Column(name = "cust_id")
    private Long customerId;

    @Size(max = 25)
    @Column(name = "cust_first_name", length = 25)
    private String firstName;

    @Size(max = 25)
    @Column(name = "cust_middle_name", length = 25)
    private String middleName;

    @Size(max = 25)
    @Column(name = "cust_last_name", length = 25)
    private String lastName;

    @Size(max = 50)
    @Column(name = "cust_addr_line_1", length = 50)
    private String addressLine1;

    @Size(max = 50)
    @Column(name = "cust_addr_line_2", length = 50)
    private String addressLine2;

    @Size(max = 50)
    @Column(name = "cust_addr_line_3", length = 50)
    private String addressLine3;

    @Size(max = 2)
    @Column(name = "cust_addr_state_cd", length = 2)
    private String addressStateCode;

    @Size(max = 3)
    @Column(name = "cust_addr_country_cd", length = 3)
    private String addressCountryCode;

    @Size(max = 10)
    @Column(name = "cust_addr_zip", length = 10)
    private String addressZip;

    @Size(max = 15)
    @Column(name = "cust_phone_num_1", length = 15)
    private String phoneNumber1;

    @Size(max = 15)
    @Column(name = "cust_phone_num_2", length = 15)
    private String phoneNumber2;

    @Column(name = "cust_ssn")
    private Long ssn;

    @Size(max = 20)
    @Column(name = "cust_govt_issued_id", length = 20)
    private String governmentIssuedId;

    @Column(name = "cust_dob")
    private LocalDate dateOfBirth;

    @Size(max = 10)
    @Column(name = "cust_eft_account_id", length = 10)
    private String eftAccountId;

    @Size(max = 1)
    @Column(name = "cust_pri_card_holder_ind", length = 1)
    private String primaryCardHolderIndicator;

    @Column(name = "cust_fico_credit_score")
    private Integer ficoCreditScore;
}
