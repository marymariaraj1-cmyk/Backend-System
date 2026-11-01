package com.billing.service;

import com.billing.dto.BuyerDirectSaleDto;

import java.util.Map;

/**
 * Buyer Direct Sale Edit: fetch / save edits / delete for Buyer Direct rows only.
 * Adjusts the buyer ledger by the difference and recalculates running balances.
 * The farmer ledger and BLOOMBUDDY_SALES_TOTALSUMMARY are never touched.
 */
public interface BuyerDirectSaleEditService {

    Map<String, Object> fetch(String buyerName, String salesDate, Long clientId);

    Map<String, Object> saveEdits(BuyerDirectSaleDto request, Long clientId, String clientUsername);

    Map<String, Object> delete(Long salesId, Long clientId, String clientUsername);
}
