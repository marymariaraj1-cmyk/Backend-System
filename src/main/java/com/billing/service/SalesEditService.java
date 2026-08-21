package com.billing.service;

import com.billing.dto.SalesEditRequestDto;

import java.util.List;
import java.util.Map;

public interface SalesEditService {

    Map<String, List<String>> getMasterNames(Long clientId);

    Map<String, Object> fetchSalesData(String farmerName, String salesDate, Long clientId);

    Map<String, Object> saveEdits(SalesEditRequestDto request, Long clientId, String clientUsername);
}
