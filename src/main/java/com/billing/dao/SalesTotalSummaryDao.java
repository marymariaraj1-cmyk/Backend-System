package com.billing.dao;

import com.billing.entity.SalesTotalSummary;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SalesTotalSummaryDao {

    void upsert(SalesTotalSummary summary, Connection conn);

    List<Map<String, Object>> findSummaryByDateRange(Long clientId, LocalDate fromDate, LocalDate toDate);

    List<Map<String, Object>> findSummaryForDate(Long clientId, LocalDate salesDate);

    Map<String, Object> findRow(Long clientId, String farmerId, LocalDate salesDate);

    Map<String, Object> findLatestBefore(Long clientId, String farmerId, LocalDate date);

    void adjustDebit(Long clientId, String farmerId, LocalDate salesDate, java.math.BigDecimal debitAmount, Connection conn);
}
