package com.aws.carddemo.model;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "card_xref")
public class CardXref implements Serializable {

    @Id
    @Column(name = "card_num", length = 16)
    private String cardNum;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    public CardXref() {
    }

    public CardXref(String cardNum, Long custId, Long acctId) {
        this.cardNum = cardNum;
        this.custId = custId;
        this.acctId = acctId;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }
}
