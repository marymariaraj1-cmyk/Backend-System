package com.billing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FlowerPriceConfigService {

    List<Map<String, Object>> getFlowers(Long clientId);

    List<Map<String, Object>> getPrices(Long clientId, LocalDate priceDate);

    Map<String, Object> savePrices(Long clientId, String clientUsername, LocalDate priceDate,
                                   List<Map<String, Object>> items);

    void updatePrice(Long clientId, String updatedBy, Long priceConfigId, BigDecimal price);

    void deletePrice(Long clientId, Long priceConfigId);

    List<Map<String, Object>> getTickerData(Long clientId);

    List<Map<String, Object>> getPriceHistory(Long clientId, String flowerId, LocalDate fromDate, LocalDate toDate);
}