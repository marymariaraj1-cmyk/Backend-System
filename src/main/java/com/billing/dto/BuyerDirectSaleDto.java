package com.billing.dto;

import java.util.List;

/**
 * Buyer Direct Sale (save): no farmer, no commission.
 * Rows: buyerId resolved server-side from buyerName; each row becomes one SALES row
 * with FARMER_ID NULL and FARMER_NAME = Buyer Direct Sale marker.
 */
public class BuyerDirectSaleDto {

    private String buyerName;
    private String salesDate;
    private List<BuyerDirectSaleLineDto> rows;

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(String salesDate) {
        this.salesDate = salesDate;
    }

    public List<BuyerDirectSaleLineDto> getRows() {
        return rows;
    }

    public void setRows(List<BuyerDirectSaleLineDto> rows) {
        this.rows = rows;
    }
}
