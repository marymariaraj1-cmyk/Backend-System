package com.billing.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BuyerTransaction {

    private Long buyerTransactionId;
    private Long clientId;
    private String clientUsername;
    private String buyerId;
    private String buyerName;
    private LocalDate transactionDate;
    private BigDecimal cashPaidAmt;
    private BigDecimal disAmt;
    private String paymentMode;

    public BuyerTransaction() {
    }

    public Long getBuyerTransactionId() {
        return buyerTransactionId;
    }

    public void setBuyerTransactionId(Long buyerTransactionId) {
        this.buyerTransactionId = buyerTransactionId;
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

    public BigDecimal getDisAmt() {
        return disAmt;
    }

    public void setDisAmt(BigDecimal disAmt) {
        this.disAmt = disAmt;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }
}
