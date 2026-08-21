package com.billing.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FarmerSalesReportDao {

    List<Map<String, Object>> findSalesByFarmerAndDate(Long clientId, String clientUsername, String farmerId, LocalDate date);

    List<Map<String, Object>> findSalesByDateRange(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findTodaySales(Long clientId, String clientUsername);

    List<Map<String, Object>> findSalesByIds(Long clientId, List<Long> salesIds);
}
