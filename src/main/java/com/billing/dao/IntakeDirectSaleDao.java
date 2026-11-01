package com.billing.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DAO backing Flower Intake Sale / Buyer Direct Sale and their edit screens.
 * Read-only where marked with {@code Connection} semantics of the caller's transaction.
 */
public interface IntakeDirectSaleDao {

    /** All of a farmer's sales rows for a date regardless of type (farmer-side aggregate basis). */
    List<Map<String, Object>> findFarmerDayRowsAll(Long clientId, String farmerId, LocalDate salesDate, Connection conn);

    /** Flower Intake rows for a farmer/date: FARMER_ID = ?, BUYER_ID IS NULL, CUST_NAME = marker. */
    List<Map<String, Object>> findFlowerIntakeRows(Long clientId, String farmerId, LocalDate salesDate);

    /** Buyer Direct rows for a buyer/date: BUYER_ID = ?, FARMER_ID IS NULL, FARMER_NAME = marker. */
    List<Map<String, Object>> findBuyerDirectRows(Long clientId, String buyerId, LocalDate salesDate);

    /** All of a buyer's sales rows for a date (buyer-side aggregate basis). */
    List<Map<String, Object>> findBuyerDayRowsAll(Long clientId, String buyerId, LocalDate salesDate, Connection conn);

    /** Updates a sales row's editable fields (flower, weight, rate, price, flowerId, bagCount). */
    void updateSalesRow(Long salesId, Long clientId, String flowerType, BigDecimal totalWeight,
                        BigDecimal perKgRate, BigDecimal price, String flowerId, Integer bagCount,
                        Connection conn);
}
