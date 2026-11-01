package com.billing.dto;

public class SalesEditRowDto {

    private Long salesId;
    private String flowerType;
    private String totalWeight;
    private String price;
    private String rate;
    private String amount;
    private Integer bagCount;
    private String customerName;

    public Long getSalesId() {
        return salesId;
    }

    public void setSalesId(Long salesId) {
        this.salesId = salesId;
    }

    public String getFlowerType() {
        return flowerType;
    }

    public void setFlowerType(String flowerType) {
        this.flowerType = flowerType;
    }

    public String getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(String totalWeight) {
        this.totalWeight = totalWeight;
    }

    public String getPrice() {
        if (price != null && !price.trim().isEmpty()) {
            return price;
        }
        return rate;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getRate() {
        if (rate != null && !rate.trim().isEmpty()) {
            return rate;
        }
        return price;
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

    public Integer getBagCount() {
        return bagCount;
    }

    public void setBagCount(Integer bagCount) {
        this.bagCount = bagCount;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
}
