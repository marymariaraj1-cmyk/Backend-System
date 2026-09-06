package com.billing.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "BLOOMBUDDY_BAG_COUNT_CONFIG")
public class BagCountConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONFIG_ID", nullable = false, updatable = false)
    private Long configId;

    @Column(name = "CLIENT_ID", nullable = false)
    private Long clientId;

    @Column(name = "CLIENT_USERNAME", nullable = false, length = 50)
    private String clientUsername;

    @Column(name = "FLOWER_ID", nullable = false, length = 60)
    private String flowerId;

    @Column(name = "FLOWER_NAME", nullable = false, length = 100)
    private String flowerName;

    @Column(name = "SALES_DATE", nullable = false)
    private LocalDate salesDate;

    @Column(name = "BAG_COUNT", nullable = false)
    private Integer bagCount;

    @Column(name = "BAG_CHECK", nullable = false, length = 1)
    private String bagCheck;

    public BagCountConfig() {
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
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

    public String getFlowerId() {
        return flowerId;
    }

    public void setFlowerId(String flowerId) {
        this.flowerId = flowerId;
    }

    public String getFlowerName() {
        return flowerName;
    }

    public void setFlowerName(String flowerName) {
        this.flowerName = flowerName;
    }

    public LocalDate getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(LocalDate salesDate) {
        this.salesDate = salesDate;
    }

    public Integer getBagCount() {
        return bagCount;
    }

    public void setBagCount(Integer bagCount) {
        this.bagCount = bagCount;
    }

    public String getBagCheck() {
        return bagCheck;
    }

    public void setBagCheck(String bagCheck) {
        this.bagCheck = bagCheck;
    }
}
