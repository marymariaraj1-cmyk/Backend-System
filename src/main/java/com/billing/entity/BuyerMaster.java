package com.billing.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "BLOOMBUDDY_BUYER_MASTER")
public class BuyerMaster {

    @Id
    @Column(name = "BUYER_ID", length = 60)
    private String buyerId;

    @Column(name = "CLIENT_ID", nullable = false)
    private Long clientId;

    @Column(name = "CLIENT_USERNAME", nullable = false, length = 50)
    private String clientUsername;

    @Column(name = "BUYER_NAME", nullable = false, length = 100)
    private String buyerName;

    @Column(name = "BUYER_CONTACT_NO", length = 15)
    private String buyerContactNo;

    @Column(name = "BUYER_ADDRESS", length = 250)
    private String buyerAddress;

    public BuyerMaster() {
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getClientUsername() {
        return clientUsername;
    }

    public void setClientUsername(String clientUsername) {
        this.clientUsername = clientUsername;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getBuyerContactNo() {
        return buyerContactNo;
    }

    public void setBuyerContactNo(String buyerContactNo) {
        this.buyerContactNo = buyerContactNo;
    }

    public String getBuyerAddress() {
        return buyerAddress;
    }

    public void setBuyerAddress(String buyerAddress) {
        this.buyerAddress = buyerAddress;
    }
}
