package com.billing.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FarmerLedgerReportService {

    List<Map<String, Object>> getFarmerLedgerReport(Long clientId, String clientUsername, String farmerId,
                                                    LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> getFarmerList(Long clientId);

    List<Map<String, Object>> getFarmerLedgerDetail(Long clientId, String clientUsername, String farmerId,
                                                    LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> getFarmerLedgerDetailAll(Long clientId, String clientUsername, String farmerId);

    List<Map<String, Object>> getFarmerLedgerReportDetail(Long clientId, String clientUsername, String farmerId,
                                                          LocalDate fromDate, LocalDate toDate);
}
