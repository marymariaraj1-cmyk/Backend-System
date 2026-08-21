package com.billing.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BuyerSalesReportDao {

    List<Map<String, Object>> findSalesByBuyerAndDate(Long clientId, String clientUsername, String buyerId, LocalDate date);

    List<Map<String, Object>> findBuyerSummaryByDateRange(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findDiscountByDateRange(Long clientId, LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findSalesDetailByDateRange(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findSalesByIds(Long clientId, List<Long> salesIds);
}
