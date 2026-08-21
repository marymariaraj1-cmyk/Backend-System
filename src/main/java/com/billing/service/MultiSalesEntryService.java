package com.billing.service;

import com.billing.entity.Sales;

import java.util.List;
import java.util.Map;

public interface MultiSalesEntryService {

    List<Sales> saveMultiSales(List<MultiSalesLine> lines, Long clientId, String clientUsername);

    List<Map<String, Object>> getTodayEntries(Long clientId);

    Map<String, List<String>> getMasterNames(Long clientId);

    class MultiSalesLine {
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
