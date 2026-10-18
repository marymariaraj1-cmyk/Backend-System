package com.billing.service;

import com.billing.dto.CashBookFieldConfigRequest;

import java.util.List;
import java.util.Map;

public interface CashBookFieldConfigService {

    /** Client's configured fields (DEBIT then CREDIT, display order), each with an inUse flag. */
    List<Map<String, Object>> listFields(Long clientId);

    /** Create a field on a side. Throws IllegalArgumentException for validation failures. */
    Map<String, Object> create(Long clientId, String clientUsername, CashBookFieldConfigRequest request);

    /** Update label / Tamil label on an owned field. */
    Map<String, Object> update(Long clientId, String clientUsername, CashBookFieldConfigRequest request);

    /** Hard-delete an owned field that is not in use. Throws FieldInUseException when rejected. */
    void delete(Long clientId, Long fieldConfigId);

    /** Rewrite DISPLAY_ORDER for one side from an ordered id list. */
    void reorder(Long clientId, String fieldType, List<Long> orderedIds);
}