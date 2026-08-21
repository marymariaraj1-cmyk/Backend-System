package com.billing.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesTotalSummary {

    private Long clientTotSumm;
    private Long clientId;
    private String clientUsername;
    private String farmerId;
    private String farmerName;
    private LocalDate salesDate;
    private BigDecimal totalSalesAmt;
    private BigDecimal commissionAmt;
    private BigDecimal totalNetAmt;
    private BigDecimal debitAmt;
    private BigDecimal finalAmt;

    public SalesTotalSummary() {
    }

    public Long getClientTotSumm() {
        return clientTotSumm;
    }

    public void setClientTotSumm(Long clientTotSumm) {
        this.clientTotSumm = clientTotSumm;
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

    public BigDecimal getTotalSalesAmt() {
        return totalSalesAmt;
    }

    public void setTotalSalesAmt(BigDecimal totalSalesAmt) {
        this.totalSalesAmt = totalSalesAmt;
    }

    public BigDecimal getCommissionAmt() {
        return commissionAmt;
    }

    public void setCommissionAmt(BigDecimal commissionAmt) {
        this.commissionAmt = commissionAmt;
    }

    public BigDecimal getTotalNetAmt() {
        return totalNetAmt;
    }

    public void setTotalNetAmt(BigDecimal totalNetAmt) {
        this.totalNetAmt = totalNetAmt;
    }

    public BigDecimal getDebitAmt() {
        return debitAmt;
    }

    public void setDebitAmt(BigDecimal debitAmt) {
        this.debitAmt = debitAmt;
    }

    public BigDecimal getFinalAmt() {
        return finalAmt;
    }

    public void setFinalAmt(BigDecimal finalAmt) {
        this.finalAmt = finalAmt;
    }
}
