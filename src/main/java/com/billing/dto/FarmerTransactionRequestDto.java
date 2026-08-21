package com.billing.dto;

public class FarmerTransactionRequestDto {

    private String farmerName;
    private String transactionDate;
    private String excessDebitAmt;
    private String debitAmt;

    public String getFarmerName() {
        return farmerName;
    }

    public void setFarmerName(String farmerName) {
        this.farmerName = farmerName;
    }

    public String getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(String transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getExcessDebitAmt() {
        return excessDebitAmt;
    }

    public void setExcessDebitAmt(String excessDebitAmt) {
        this.excessDebitAmt = excessDebitAmt;
    }

    public String getDebitAmt() {
        return debitAmt;
    }

    public void setDebitAmt(String debitAmt) {
        this.debitAmt = debitAmt;
    }
}
