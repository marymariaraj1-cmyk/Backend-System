package com.billing.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BuyerLedger {

    private Long buyerLedgerId;
    private Long clientId;
    private String clientUsername;
    private String buyerId;
    private String buyerName;
    private LocalDate salesDate;
    private BigDecimal creditAmt;
    private BigDecimal debitAmt;
    private BigDecimal disAmt;
    private String ledgerActive;
    private String salesIds;

    public BuyerLedger() {
    }

    public Long getBuyerLedgerId() {
        return buyerLedgerId;
    }

    public void setBuyerLedgerId(Long buyerLedgerId) {
        this.buyerLedgerId = buyerLedgerId;
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

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
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

    public BigDecimal getDisAmt() {
        return disAmt;
    }

    public void setDisAmt(BigDecimal disAmt) {
        this.disAmt = disAmt;
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
