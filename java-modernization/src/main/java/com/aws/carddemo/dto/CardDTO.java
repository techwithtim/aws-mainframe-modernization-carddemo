package com.aws.carddemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardDTO {
    private String cardNumber;
    private Long accountId;
    private Integer cvvCode;
    private String embossedName;
    private LocalDate expirationDate;
    private String activeStatus;
}
