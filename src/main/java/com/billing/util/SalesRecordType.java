package com.billing.util;

/**
 * Classifies a BLOOMBUDDY_SALES row into one of five types.
 *
 * Checked in this exact order, because the new marker rows would otherwise be
 * misread as Flower Loot rows when only the IDs are inspected:
 *   1. FLOWER_INTAKE_SALE : farmer side present, no buyer, CUST_NAME is the intake marker
 *   2. BUYER_DIRECT_SALE  : buyer side present, no farmer, FARMER_NAME is the direct marker
 *   3. REGULAR_SALE       : both a farmer and a buyer
 *   4. FLS_BUYER_PURCHASE : no farmer at all (id AND name null), buyer present
 *   5. FLS_FARMER_SALE    : no buyer at all (id AND name null), farmer present
 *
 * The marker strings are stored in English in the database. Screens and prints
 * resolve them through the i18n keys (see messages.properties: intake.sale.label
 * and buyer.direct.sale.label).
 */
public enum SalesRecordType {

    FLOWER_INTAKE_SALE,
    BUYER_DIRECT_SALE,
    REGULAR_SALE,
    FLS_BUYER_PURCHASE,
    FLS_FARMER_SALE;

    /** Value written to BLOOMBUDDY_SALES.CUST_NAME for Flower Intake rows. */
    public static final String FLOWER_INTAKE_SALE_MARKER = "Flower Intake Sale";

    /** Value written to BLOOMBUDDY_SALES.FARMER_NAME for Buyer Direct rows. */
    public static final String BUYER_DIRECT_SALE_MARKER = "Buyer Direct Sale";

    public static SalesRecordType classify(String farmerId, String farmerName,
                                           String buyerId, String custName) {
        boolean hasFarmerId = isPresent(farmerId);
        boolean hasFarmerName = isPresent(farmerName);
        boolean hasBuyerId = isPresent(buyerId);
        boolean hasCustName = isPresent(custName);

        if (hasCustName && !hasBuyerId && hasFarmerId
                && FLOWER_INTAKE_SALE_MARKER.equals(custName.trim())) {
            return FLOWER_INTAKE_SALE;
        }
        if (hasFarmerName && !hasFarmerId && hasBuyerId
                && BUYER_DIRECT_SALE_MARKER.equals(farmerName.trim())) {
            return BUYER_DIRECT_SALE;
        }
        if (hasFarmerId && hasBuyerId) {
            return REGULAR_SALE;
        }
        if (!hasFarmerId && !hasFarmerName && hasBuyerId) {
            return FLS_BUYER_PURCHASE;
        }
        if (!hasBuyerId && !hasCustName && hasFarmerId) {
            return FLS_FARMER_SALE;
        }
        throw new IllegalArgumentException(
                "Sales row does not match any known record type (farmerId=" + farmerId
                        + ", farmerName=" + farmerName + ", buyerId=" + buyerId
                        + ", custName=" + custName + ")");
    }

    public static boolean isFlowerIntakeSale(String custName, String buyerId, String farmerId) {
        return isPresent(custName) && !isPresent(buyerId) && isPresent(farmerId)
                && FLOWER_INTAKE_SALE_MARKER.equals(custName.trim());
    }

    public static boolean isBuyerDirectSale(String farmerName, String farmerId, String buyerId) {
        return isPresent(farmerName) && !isPresent(farmerId) && isPresent(buyerId)
                && BUYER_DIRECT_SALE_MARKER.equals(farmerName.trim());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
