package com.billing.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface DashboardDao {

    Map<String, Object> findTodayKpi(Long clientId);

    Map<String, Object> findMasterCounts(Long clientId);

    List<Map<String, Object>> findSalesTrend(Long clientId, LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findTopFlowersByWeight(Long clientId, LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findTopFarmersBySales(Long clientId, LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findTopOutstandingFarmers(Long clientId);

    List<Map<String, Object>> findTopOutstandingBuyers(Long clientId);

    Map<String, Object> findDirectPayments(Long clientId, LocalDate fromDate, LocalDate toDate);
}
