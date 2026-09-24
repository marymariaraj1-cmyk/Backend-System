package com.billing.dto;

import java.util.List;

public class FlowerLootSaleEditRequestDto {

    private String flowerName;
    private String salesDate;
    private List<FlowerLootSaleEditRowDto> buyerRows;
    private List<FlowerLootSaleEditRowDto> farmerRows;

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

    public List<FlowerLootSaleEditRowDto> getBuyerRows() {
        return buyerRows;
    }

    public void setBuyerRows(List<FlowerLootSaleEditRowDto> buyerRows) {
        this.buyerRows = buyerRows;
    }

    public List<FlowerLootSaleEditRowDto> getFarmerRows() {
        return farmerRows;
    }

    public void setFarmerRows(List<FlowerLootSaleEditRowDto> farmerRows) {
        this.farmerRows = farmerRows;
    }
}