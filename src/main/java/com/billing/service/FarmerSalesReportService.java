package com.billing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FarmerSalesReportService {

    Map<String, Object> getFarmerSalesByDate(Long clientId, String clientUsername, String farmerId, LocalDate date, String ledgerActive, BigDecimal creditAmt);

    Map<String, Object> getFarmerSummaryByDate(Long clientId, String farmerId, LocalDate date);

    List<Map<String, Object>> getFarmerSalesReport(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> getTodayFarmerSales(Long clientId, String clientUsername);
}
