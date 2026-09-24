package com.billing.dto;

import java.util.List;

public class VayalAgroPriceData {

    private String date;
    private List<FlowerPriceRow> rows;

    public VayalAgroPriceData() {
    }

    public VayalAgroPriceData(String date, List<FlowerPriceRow> rows) {
        this.date = date;
        this.rows = rows;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<FlowerPriceRow> getRows() {
        return rows;
    }

    public void setRows(List<FlowerPriceRow> rows) {
        this.rows = rows;
    }
}