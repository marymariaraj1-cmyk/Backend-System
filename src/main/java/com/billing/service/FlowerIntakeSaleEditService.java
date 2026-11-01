package com.billing.service;

import com.billing.dto.SalesEditRequestDto;

import java.util.Map;

/**
 * Flower Intake Sale Edit: fetch / save edits / delete for Flower Intake rows only.
 * Farmer aggregate (ledger + summary) is recalculated from ALL of the farmer's rows of the day
 * after every change. Buyer ledger is never touched.
 */
public interface FlowerIntakeSaleEditService {

    Map<String, Object> fetch(String farmerName, String salesDate, Long clientId);

    Map<String, Object> saveEdits(SalesEditRequestDto request, Long clientId, String clientUsername);

    Map<String, Object> delete(Long salesId, Long clientId, String clientUsername);
}
