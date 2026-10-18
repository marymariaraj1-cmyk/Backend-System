package com.billing.dao;

import com.billing.entity.CashBookFieldConfig;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

public interface CashBookFieldConfigDao {

    /** All of a client's fields, ordered by FIELD_TYPE then DISPLAY_ORDER. */
    List<CashBookFieldConfig> findByClientId(Long clientId);

    /** A client's fields for one side (DEBIT or CREDIT), ordered by DISPLAY_ORDER. */
    List<CashBookFieldConfig> findByClientIdAndType(Long clientId, String fieldType);

    /** Ownership-scoped single-field lookup. */
    CashBookFieldConfig findByIdAndClientId(Long fieldConfigId, Long clientId);

    /** Ownership-scoped single-field lookup on a supplied connection. */
    CashBookFieldConfig findByIdAndClientId(Long fieldConfigId, Long clientId, Connection conn);

    /** Distinct FIELD_TYPE values present for a client (used by the reorder guard). */
    List<Long> findIdsByClientIdAndType(Long clientId, String fieldType);

    /** Number of configured fields for a client + side. */
    int countByClientIdAndType(Long clientId, String fieldType);

    /** Case-insensitive duplicate check for (client, side, label). */
    int countByClientIdAndTypeAndLabel(Long clientId, String fieldType, String label);

    /** Highest DISPLAY_ORDER for a client + side, or null when the side is empty. */
    Integer maxDisplayOrder(Long clientId, String fieldType);

    /** BOOK_DATE + both manual values columns for every saved cash book row. */
    List<Map<String, Object>> findManualValueRowsByClient(Long clientId);

    /** Same scan on a supplied connection. */
    List<Map<String, Object>> findManualValueRowsByClient(Long clientId, Connection conn);

    // ---------- Writes (connection supplied by service) ----------

    Long insert(CashBookFieldConfig config, Connection conn);

    int update(CashBookFieldConfig config, Connection conn);

    int deleteByIdAndClientId(Long fieldConfigId, Long clientId, Connection conn);

    void updateDisplayOrder(Long fieldConfigId, Long clientId, int displayOrder, Connection conn);
}