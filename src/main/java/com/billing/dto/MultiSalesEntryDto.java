package com.billing.dto;

import java.util.List;

public class MultiSalesEntryDto {

    private List<MultiSalesLineDto> rows;
    private String totalSalesAmt;
    private String commissionAmt;
    private String netAmount;
    private String finalTotal;
    private String debitAmount;

    public List<MultiSalesLineDto> getRows() {
        return rows;
    }

    public void setRows(List<MultiSalesLineDto> rows) {
        this.rows = rows;
    }

    public String getTotalSalesAmt() { return totalSalesAmt; }
    public void setTotalSalesAmt(String totalSalesAmt) { this.totalSalesAmt = totalSalesAmt; }
    public String getCommissionAmt() { return commissionAmt; }
    public void setCommissionAmt(String commissionAmt) { this.commissionAmt = commissionAmt; }
    public String getNetAmount() { return netAmount; }
    public void setNetAmount(String netAmount) { this.netAmount = netAmount; }
    public String getFinalTotal() { return finalTotal; }
    public void setFinalTotal(String finalTotal) { this.finalTotal = finalTotal; }
    public String getDebitAmount() { return debitAmount; }
    public void setDebitAmount(String debitAmount) { this.debitAmount = debitAmount; }

    public static class MultiSalesLineDto {
        private String farmerName;
        private String flowerType;
        private String totalWeight;
        private String price;
        private String amount;
        private String customerName;
        private Integer bagCount;

        public String getFarmerName() { return farmerName; }
        public void setFarmerName(String farmerName) { this.farmerName = farmerName; }
        public String getFlowerType() { return flowerType; }
        public void setFlowerType(String flowerType) { this.flowerType = flowerType; }
        public String getTotalWeight() { return totalWeight; }
        public void setTotalWeight(String totalWeight) { this.totalWeight = totalWeight; }
        public String getPrice() { return price; }
        public void setPrice(String price) { this.price = price; }
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public Integer getBagCount() { return bagCount; }
        public void setBagCount(Integer bagCount) { this.bagCount = bagCount; }
    }
}
