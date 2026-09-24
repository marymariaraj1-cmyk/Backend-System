package com.billing.service;

import com.billing.dto.FlowerLootSaleEditRequestDto;

import java.util.List;
import java.util.Map;

public interface FlowerLootSaleEditService {

    Map<String, List<String>> getMasterNames(Long clientId);

    Map<String, Object> fetchFlowerLootSaleData(String flowerName, String salesDate, Long clientId);

    Map<String, Object> saveEdits(FlowerLootSaleEditRequestDto request, Long clientId, String clientUsername);

    Map<String, Object> deleteSalesEntry(Long salesId, Long clientId, String clientUsername);
}