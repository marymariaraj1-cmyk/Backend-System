package com.billing.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BuyerLedgerReportService {

    List<Map<String, Object>> getBuyerLedgerReport(Long clientId, String clientUsername, String buyerId,
                                                   LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> getBuyerList(Long clientId);

    List<Map<String, Object>> getBuyerLedgerDetail(Long clientId, String clientUsername, String buyerId,
                                                   LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> getBuyerLedgerDetailAll(Long clientId, String clientUsername, String buyerId);

    List<Map<String, Object>> getBuyerLedgerReportDetail(Long clientId, String clientUsername, String buyerId,
                                                         LocalDate fromDate, LocalDate toDate);
}
