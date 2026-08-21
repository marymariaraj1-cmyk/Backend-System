package com.billing.dto;

import java.util.List;

public class MultiSalesEntryDto {

    private List<MultiSalesLineDto> rows;

    public List<MultiSalesLineDto> getRows() {
        return rows;
    }

    public void setRows(List<MultiSalesLineDto> rows) {
        this.rows = rows;
    }

    public static class MultiSalesLineDto {
        private String farmerName;
        private String flowerType;
        private String totalWeight;
        private String price;
        private String amount;
        private String customerName;

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
    }
}
