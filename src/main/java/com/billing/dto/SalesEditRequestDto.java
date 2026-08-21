package com.billing.dto;

import java.util.List;

public class SalesEditRequestDto {

    private String farmerName;
    private String salesDate;
    private boolean debitEdited;
    private String debitAmount;
    private List<SalesEditRowDto> rows;

    public String getFarmerName() {
        return farmerName;
    }

    public void setFarmerName(String farmerName) {
        this.farmerName = farmerName;
    }

    public String getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(String salesDate) {
        this.salesDate = salesDate;
    }

    public boolean isDebitEdited() {
        return debitEdited;
    }

    public void setDebitEdited(boolean debitEdited) {
        this.debitEdited = debitEdited;
    }

    public String getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(String debitAmount) {
        this.debitAmount = debitAmount;
    }

    public List<SalesEditRowDto> getRows() {
        return rows;
    }

    public void setRows(List<SalesEditRowDto> rows) {
        this.rows = rows;
    }
}
