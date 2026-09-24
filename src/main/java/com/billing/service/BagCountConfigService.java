package com.billing.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BagCountConfigService {

    List<Map<String, Object>> getFlowers(Long clientId);

    List<Map<String, Object>> getFarmers(Long clientId);

    List<Map<String, Object>> getConfigs(Long clientId);

    Map<String, Object> getConfig(Long clientId, String farmerId, String flowerId, LocalDate salesDate);

    List<Map<String, Object>> getConfigReport(Long clientId, String farmerId, LocalDate fromDate, LocalDate toDate);

    void saveConfig(Long clientId, String clientUsername, String farmerId, String farmerName,
                    String flowerId, String flowerName, LocalDate salesDate, Integer bagCount);

    void deleteConfig(Long clientId, String farmerId, String flowerId, LocalDate salesDate);

    int getSavedBagTotal(Long clientId, String farmerId, String flowerId, LocalDate salesDate);
}