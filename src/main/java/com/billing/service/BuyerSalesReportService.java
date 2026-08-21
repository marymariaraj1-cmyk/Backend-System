package com.billing.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BuyerSalesReportService {

    List<Map<String, Object>> getBuyerSalesByDate(Long clientId, String clientUsername, String buyerId, LocalDate date, String ledgerActive);

    List<Map<String, Object>> getBuyerSalesReport(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate);
}
