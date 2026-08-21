package com.billing.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SalesEditDao {

    List<Map<String, Object>> fetchSales(Long clientId, String farmerId, LocalDate salesDate);

    Map<String, Object> fetchSummary(Long clientId, String farmerId, LocalDate salesDate);

    Map<String, Object> findSalesRow(Long clientId, Long salesId);

    void updateSalesRow(Long salesId, Long clientId, String flowerType,
                        BigDecimal totalWeight, BigDecimal perKgRate, BigDecimal price,
                        Connection conn);

    void adjustSummarySalesAmounts(Long clientId, String farmerId, LocalDate salesDate,
                                   BigDecimal totalDelta, BigDecimal commissionDelta,
                                   BigDecimal netDelta, BigDecimal finalDelta,
                                   Connection conn);

    void reconcileSummary(Long clientId, String clientUsername, String farmerId, String farmerName,
                          LocalDate salesDate, BigDecimal totalSalesAmt, BigDecimal commissionAmt,
                          BigDecimal totalNetAmt, BigDecimal debitAmt, BigDecimal finalAmt,
                          Connection conn);
}
