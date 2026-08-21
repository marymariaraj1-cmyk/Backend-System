package com.billing.dto;

import java.util.List;

public class SalesRequestDto {

    private String farmerName;
    private String salesDate;
    private String totalSalesAmt;
    private String commissionAmt;
    private String netAmount;
    private String finalTotal;
    private String debitAmount;
    private List<SalesLineDto> rows;

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

    public String getTotalSalesAmt() {
        return totalSalesAmt;
    }

    public void setTotalSalesAmt(String totalSalesAmt) {
        this.totalSalesAmt = totalSalesAmt;
    }

    public String getCommissionAmt() {
        return commissionAmt;
    }

    public void setCommissionAmt(String commissionAmt) {
        this.commissionAmt = commissionAmt;
    }

    public String getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(String netAmount) {
        this.netAmount = netAmount;
    }

    public String getFinalTotal() {
        return finalTotal;
    }

    public void setFinalTotal(String finalTotal) {
        this.finalTotal = finalTotal;
    }

    public String getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(String debitAmount) {
        this.debitAmount = debitAmount;
    }

    public List<SalesLineDto> getRows() {
        return rows;
    }

    public void setRows(List<SalesLineDto> rows) {
        this.rows = rows;
    }
}
