package com.billing.util;

public enum SalesRecordType {

    REGULAR_SALE,
    FLS_BUYER_PURCHASE,
    FLS_FARMER_SALE;

    public static SalesRecordType classify(String farmerId, String buyerId) {
        return classify(farmerId != null && !farmerId.trim().isEmpty(),
                buyerId != null && !buyerId.trim().isEmpty());
    }

    public static SalesRecordType classify(boolean hasFarmer, boolean hasBuyer) {
        if (hasFarmer && hasBuyer) {
            return REGULAR_SALE;
        }
        if (!hasFarmer) {
            return FLS_BUYER_PURCHASE;
        }
        return FLS_FARMER_SALE;
    }
}