package com.billing.service;

import com.billing.entity.Sales;

import java.util.List;
import java.util.Map;

public interface SalesService {

    List<Sales> saveSales(String farmerName, String salesDate, List<SalesLineInput> lines,
                          Long clientId, String clientUsername,
                          String totalSalesAmt, String commissionAmt, String netAmount,
                          String finalTotal, String debitAmount);

    Sales updateSales(Long salesId, Long clientId, String clientUsername, String farmerName, String salesDate,
                      String flowerType, String totalWeight, String price, String customerName);

    List<String> getAutocompleteSuggestions(String field, String query, Long clientId);

    Map<String, List<String>> getMasterNames(Long clientId);

    class SalesLineInput {
        private String flowerType;
        private String totalWeight;
        private String price;
        private String amount;
        private String customerName;

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
            return price;
        }

        public void setPrice(String price) {
            this.price = price;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }
    }
}
