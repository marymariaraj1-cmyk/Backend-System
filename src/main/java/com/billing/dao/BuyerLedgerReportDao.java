package com.billing.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BuyerLedgerReportDao {

    List<Map<String, Object>> findByDateRange(Long clientId, String clientUsername, String buyerId,
                                              LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findAll(Long clientId, String clientUsername, String buyerId);

    List<Map<String, Object>> findBuyerList(Long clientId);

    BigDecimal findConfiguredOpeningBalance(Long clientId, String buyerId);

    LocalDate findConfiguredOpeningBalanceDate(Long clientId, String buyerId);
}
