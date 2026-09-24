package com.billing.service;

import com.billing.dto.DailyCashBookSaveRequest;

import java.time.LocalDate;
import java.util.Map;

public interface DailyCashBookService {

    /** Live system-calculated blocks for a date (Blocks 1–6). */
    Map<String, Object> computeBlocks(Long clientId, LocalDate bookDate);

    /** Saved snapshot (header + details) for a date, or null when never saved. */
    Map<String, Object> loadBook(Long clientId, LocalDate bookDate);

    /** Upsert header + replace details in a single transaction. Only today's date is editable. */
    Map<String, Object> saveBook(Long clientId, String clientUsername, DailyCashBookSaveRequest request);
}
