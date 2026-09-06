package com.billing.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FarmerTransaction {

    private Long farmerTransactionId;
    private Long clientId;
    private String clientUsername;
    private String farmerId;
    private String farmerName;
    private LocalDate transactionDate;
    private BigDecimal cashPaidAmt;
    private BigDecimal excessDebitAmt;
    private BigDecimal debAmt;
    private String paymentMode;

    public FarmerTransaction() {
    }

    public Long getFarmerTransactionId() {
        return farmerTransactionId;
    }

    public void setFarmerTransactionId(Long farmerTransactionId) {
        this.farmerTransactionId = farmerTransactionId;
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

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public BigDecimal getCashPaidAmt() {
        return cashPaidAmt;
    }

    public void setCashPaidAmt(BigDecimal cashPaidAmt) {
        this.cashPaidAmt = cashPaidAmt;
    }

    public BigDecimal getExcessDebitAmt() {
        return excessDebitAmt;
    }

    public void setExcessDebitAmt(BigDecimal excessDebitAmt) {
        this.excessDebitAmt = excessDebitAmt;
    }

    public BigDecimal getDebAmt() {
        return debAmt;
    }

    public void setDebAmt(BigDecimal debAmt) {
        this.debAmt = debAmt;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }
}
