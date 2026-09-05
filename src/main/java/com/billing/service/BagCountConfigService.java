package com.billing.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BagCountConfigService {

    List<Map<String, Object>> getFlowers(Long clientId);

    List<Map<String, Object>> getConfigs(Long clientId);

    Map<String, Object> getConfig(Long clientId, String flowerId, LocalDate salesDate);

    void saveConfig(Long clientId, String clientUsername, String flowerId, String flowerName,
                    LocalDate salesDate, Integer bagCount, String bagCheck);

    void deleteConfig(Long clientId, String flowerId, LocalDate salesDate);

    int getSavedBagTotal(Long clientId, String flowerId, LocalDate salesDate);
}
