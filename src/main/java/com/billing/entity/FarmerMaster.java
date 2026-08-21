package com.billing.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "BLOOMBUDDY_FARMER_MASTER")
public class FarmerMaster {

    @Id
    @Column(name = "FARMER_ID", length = 60)
    private String farmerId;

    @Column(name = "CLIENT_ID", nullable = false)
    private Long clientId;

    @Column(name = "CLIENT_USERNAME", nullable = false, length = 50)
    private String clientUsername;

    @Column(name = "FARMER_NAME", nullable = false, length = 100)
    private String farmerName;

    @Column(name = "FARMER_CONTACT_NO", length = 15)
    private String farmerContactNo;

    @Column(name = "FARMER_ADDRESS", length = 250)
    private String farmerAddress;

    public FarmerMaster() {
    }

    public String getFarmerId() {
        return farmerId;
    }

    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
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

    public String getFarmerName() {
        return farmerName;
    }

    public void setFarmerName(String farmerName) {
        this.farmerName = farmerName;
    }

    public String getFarmerContactNo() {
        return farmerContactNo;
    }

    public void setFarmerContactNo(String farmerContactNo) {
        this.farmerContactNo = farmerContactNo;
    }

    public String getFarmerAddress() {
        return farmerAddress;
    }

    public void setFarmerAddress(String farmerAddress) {
        this.farmerAddress = farmerAddress;
    }
}
