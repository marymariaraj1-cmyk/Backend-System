package com.billing.service;

import com.billing.dto.DailyCashBookSaveRequest;

import java.time.LocalDate;
import java.util.Map;

public interface DailyCashBookService {

    /** Live system-calculated blocks for a date (Blocks 1–6). */
    Map<String, Object> computeBlocks(Long clientId, LocalDate bookDate);

    /**
     * Per-party breakdowns for the auto-calculated cash book fields, so the UI can
     * open a popup and show how Balance / Commission / Installment were calculated:
     * buyer purchase list (for Balance), farmer commission list and farmer installment list.
     */
    Map<String, Object> computeDetails(Long clientId, LocalDate bookDate);

    /** Saved snapshot (header + details) for a date, or null when never saved. */
    Map<String, Object> loadBook(Long clientId, LocalDate bookDate);

    /**
     * Upsert header + replace details in a single transaction. Today's date and
     * past dates can be saved once the user has explicitly recalculated the live
     * values in the UI; the system totals are always re-computed at save time.
     */
    Map<String, Object> saveBook(Long clientId, String clientUsername, DailyCashBookSaveRequest request);
}
