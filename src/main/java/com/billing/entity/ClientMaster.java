package com.billing.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "BLOOMBUDDY_CLIENT_MASTER")
public class ClientMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CLIENT_ID")
    private Long clientId;

    @Column(name = "CLIENT_USERNAME", unique = true, nullable = false, length = 50)
    private String clientUsername;

    @Column(name = "CLIENT_PASSWORD", nullable = false, length = 100)
    private String clientPassword;

    @Column(name = "CLIENT_SHOP_NAME", nullable = false, length = 100)
    private String clientShopName;

    @Column(name = "CLIENT_SHOP_ADDRESS", length = 250)
    private String clientShopAddress;

    @Column(name = "CLIENT_CONTACT_NO", length = 15)
    private String clientContactNo;

    @Column(name = "CLIENT_MAIL_ID", length = 100)
    private String clientMailId;

    public ClientMaster() {
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

    public String getClientPassword() {
        return clientPassword;
    }

    public void setClientPassword(String clientPassword) {
        this.clientPassword = clientPassword;
    }

    public String getClientShopName() {
        return clientShopName;
    }

    public void setClientShopName(String clientShopName) {
        this.clientShopName = clientShopName;
    }

    public String getClientShopAddress() {
        return clientShopAddress;
    }

    public void setClientShopAddress(String clientShopAddress) {
        this.clientShopAddress = clientShopAddress;
    }

    public String getClientContactNo() {
        return clientContactNo;
    }

    public void setClientContactNo(String clientContactNo) {
        this.clientContactNo = clientContactNo;
    }

    public String getClientMailId() {
        return clientMailId;
    }

    public void setClientMailId(String clientMailId) {
        this.clientMailId = clientMailId;
    }
}
