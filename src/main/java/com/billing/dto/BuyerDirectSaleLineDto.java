package com.billing.dto;

public class BuyerDirectSaleLineDto {

    private Long salesId;
    private String flowerType;
    private String totalWeight;
    private String rate;
    private String amount;
    private Integer bagCount;

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

    public Integer getBagCount() {
        return bagCount;
    }

    public void setBagCount(Integer bagCount) {
        this.bagCount = bagCount;
    }
}
