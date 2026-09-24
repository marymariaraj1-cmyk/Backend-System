package com.billing.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface DailyCashBookDao {

    // ---------- System-calculated blocks (all scoped by CLIENT_ID + date) ----------

    /** BLOCK 1 — raw purchase total for the day (never netted off). */
    BigDecimal sumBuyerPurchaseTotal(Long clientId, LocalDate bookDate);

    /** BLOCK 2 — per-farmer excess-debit totals via CASH mode only. */
    List<Map<String, Object>> findFarmerExcessDebitCashList(Long clientId, LocalDate bookDate);

    /** BLOCK 3 — total cash received from buyers (cash payment mode only). */
    BigDecimal sumBuyerReceivedTotal(Long clientId, LocalDate bookDate);

    /** BLOCK 4 — total commission for the day. */
    BigDecimal sumCommissionTotal(Long clientId, LocalDate bookDate);

    /** BLOCK 5 — SUM(FINAL_AMT) of sales total summary for the day. */
    BigDecimal sumInstallmentTotal(Long clientId, LocalDate bookDate);

    /** BLOCK 6 — per-farmer excess-debit totals via non-cash modes (record only). */
    List<Map<String, Object>> findFarmerExcessDebitNonCashList(Long clientId, LocalDate bookDate);

    // ---------- Saved snapshot ----------

    Map<String, Object> findHeaderByClientAndDate(Long clientId, LocalDate bookDate);

    List<Map<String, Object>> findDetailsByHeaderId(Long headerId);

    // ---------- Save (single transaction, connection supplied by service) ----------

    Long upsertHeader(Map<String, Object> header, Connection conn);

    void deleteDetailsByHeaderId(Long headerId, Connection conn);

    void insertDetail(Long headerId, Long clientId, String clientUsername, LocalDate bookDate,
                      String entryType, String entryLabel, String farmerId, BigDecimal amount,
                      int displayOrder, Connection conn);
}
