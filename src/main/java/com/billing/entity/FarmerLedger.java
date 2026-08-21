package com.billing.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FarmerLedger {

    private Long farmerLedgerId;
    private Long clientId;
    private String clientUsername;
    private String farmerId;
    private String farmerName;
    private LocalDate salesDate;
    private BigDecimal creditAmt;
    private BigDecimal debitAmt;
    private String ledgerActive;
    private String salesIds;

    public FarmerLedger() {
    }

    public Long getFarmerLedgerId() {
        return farmerLedgerId;
    }

    public void setFarmerLedgerId(Long farmerLedgerId) {
        this.farmerLedgerId = farmerLedgerId;
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

    public BigDecimal getCreditAmt() {
        return creditAmt;
    }

    public void setCreditAmt(BigDecimal creditAmt) {
        this.creditAmt = creditAmt;
    }

    public BigDecimal getDebitAmt() {
        return debitAmt;
    }

    public void setDebitAmt(BigDecimal debitAmt) {
        this.debitAmt = debitAmt;
    }

    public String getLedgerActive() {
        return ledgerActive;
    }

    public void setLedgerActive(String ledgerActive) {
        this.ledgerActive = ledgerActive;
    }

    public String getSalesIds() {
        return salesIds;
    }

    public void setSalesIds(String salesIds) {
        this.salesIds = salesIds;
    }
}
