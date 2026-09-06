package com.billing.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "BLOOMBUDDY_SALES")
public class Sales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SALES_ID", nullable = false, updatable = false)
    private Long salesId;

    @Column(name = "CLIENT_ID", nullable = false)
    private Long clientId;

    @Column(name = "CLIENT_USERNAME", nullable = false, length = 50)
    private String clientUsername;

    @Column(name = "FARMER_ID", nullable = false, length = 60)
    private String farmerId;

    @Column(name = "FARMER_NAME", nullable = false, length = 100)
    private String farmerName;

    @Column(name = "SALES_DATE", nullable = false)
    private LocalDate salesDate;

    @Column(name = "FLOWER_TYPE", length = 50)
    private String flowerType;

    @Column(name = "TOTAL_WEIGHT", precision = 10, scale = 2)
    private BigDecimal totalWeight;

    @Column(name = "PRICE", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "BUYER_ID", length = 60)
    private String buyerId;

    @Column(name = "PERKG_RATE", precision = 10, scale = 2)
    private BigDecimal perKgRate;

    @Column(name = "CUST_NAME", length = 100)
    private String custName;

    @Column(name = "DEBIT_CREDIT_FLAG", length = 1)
    private String debitCreditFlag;

    @Column(name = "SALE_SLOT_ID", length = 50)
    private String saleSlotId;

    @Column(name = "FLOWER_ID", length = 60)
    private String flowerId;

    @Column(name = "BAG_COUNT")
    private Integer bagCount;

    public Sales() {
    }

    public Long getSalesId() {
        return salesId;
    }

    public void setSalesId(Long salesId) {
        this.salesId = salesId;
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

    public String getFarmerId() {
        return farmerId;
    }

    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }

    public String getFarmerName() {
        return farmerName;
    }

    public void setFarmerName(String farmerName) {
        this.farmerName = farmerName;
    }

    public LocalDate getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(LocalDate salesDate) {
        this.salesDate = salesDate;
    }

    public String getFlowerType() {
        return flowerType;
    }

    public void setFlowerType(String flowerType) {
        this.flowerType = flowerType;
    }

    public BigDecimal getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(BigDecimal totalWeight) {
        this.totalWeight = totalWeight;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCustName() {
        return custName;
    }

    public void setCustName(String custName) {
        this.custName = custName;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public BigDecimal getPerKgRate() {
        return perKgRate;
    }

    public void setPerKgRate(BigDecimal perKgRate) {
        this.perKgRate = perKgRate;
    }

    public String getDebitCreditFlag() {
        return debitCreditFlag;
    }

    public void setDebitCreditFlag(String debitCreditFlag) {
        this.debitCreditFlag = debitCreditFlag;
    }

    public String getSaleSlotId() {
        return saleSlotId;
    }

    public void setSaleSlotId(String saleSlotId) {
        this.saleSlotId = saleSlotId;
    }

    public String getFlowerId() {
        return flowerId;
    }

    public void setFlowerId(String flowerId) {
        this.flowerId = flowerId;
    }

    public Integer getBagCount() {
        return bagCount;
    }

    public void setBagCount(Integer bagCount) {
        this.bagCount = bagCount;
    }
}
