package com.billing.config;

import java.io.Serializable;

public class SessionUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long clientId;
    private String clientUsername;
    private String shopName;
    private String role;

    public SessionUser() {
    }

    public SessionUser(Long clientId, String clientUsername, String shopName, String role) {
        this.clientId = clientId;
        this.clientUsername = clientUsername;
        this.shopName = shopName;
        this.role = role;
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

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
