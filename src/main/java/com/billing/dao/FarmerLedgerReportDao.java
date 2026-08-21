package com.billing.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FarmerLedgerReportDao {

    List<Map<String, Object>> findByDateRange(Long clientId, String clientUsername, String farmerId,
                                              LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findAll(Long clientId, String clientUsername, String farmerId);

    List<Map<String, Object>> findFarmerList(Long clientId);

    BigDecimal findConfiguredOpeningBalance(Long clientId, String farmerId);

    LocalDate findConfiguredOpeningBalanceDate(Long clientId, String farmerId);
}
