package com.billing.dto;

public class FlowerPriceRow {

    private String category;
    private String categoryTn;
    private String district;
    private String districtTn;
    private String city;
    private String cityTn;
    private String price;
    private String units;
    private String unitsTn;

    public FlowerPriceRow() {
    }

    public FlowerPriceRow(String category, String categoryTn, String district, String districtTn,
                          String city, String cityTn, String price, String units, String unitsTn) {
        this.category = category;
        this.categoryTn = categoryTn;
        this.district = district;
        this.districtTn = districtTn;
        this.city = city;
        this.cityTn = cityTn;
        this.price = price;
        this.units = units;
        this.unitsTn = unitsTn;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategoryTn() {
        return categoryTn;
    }

    public void setCategoryTn(String categoryTn) {
        this.categoryTn = categoryTn;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getDistrictTn() {
        return districtTn;
    }

    public void setDistrictTn(String districtTn) {
        this.districtTn = districtTn;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCityTn() {
        return cityTn;
    }

    public void setCityTn(String cityTn) {
        this.cityTn = cityTn;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public String getUnitsTn() {
        return unitsTn;
    }

    public void setUnitsTn(String unitsTn) {
        this.unitsTn = unitsTn;
    }
}