package com.billing.dto;

import java.util.List;

public class FlowerLootSaleDto {

    private String flowerName;
    private String salesDate;
    private List<FlowerLootRowDto> buyerRows;
    private List<FlowerLootRowDto> farmerRows;

    public String getFlowerName() {
        return flowerName;
    }

    public void setFlowerName(String flowerName) {
        this.flowerName = flowerName;
    }

    public String getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(String salesDate) {
        this.salesDate = salesDate;
    }

    public List<FlowerLootRowDto> getBuyerRows() {
        return buyerRows;
    }

    public void setBuyerRows(List<FlowerLootRowDto> buyerRows) {
        this.buyerRows = buyerRows;
    }

    public List<FlowerLootRowDto> getFarmerRows() {
        return farmerRows;
    }

    public void setFarmerRows(List<FlowerLootRowDto> farmerRows) {
        this.farmerRows = farmerRows;
    }

    public static class FlowerLootRowDto {
        private String bag;
        private String name;
        private String qty;
        private String rate;
        private String amount;

        public String getBag() {
            return bag;
        }

        public void setBag(String bag) {
            this.bag = bag;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getQty() {
            return qty;
        }

        public void setQty(String qty) {
            this.qty = qty;
        }

        public String getRate() {
            return rate;
        }

        public void setRate(String rate) {
            this.rate = rate;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }
    }
}