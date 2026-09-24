package com.billing.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FlowerPriceConfigDao {

    List<Map<String, Object>> findAllFlowers(Long clientId);

    List<Map<String, Object>> findByClientAndDate(Long clientId, LocalDate priceDate);

    Long findIdByClientFlowerDate(Long clientId, String flowerId, LocalDate priceDate);

    void insert(Long clientId, String clientUsername, String flowerId, String flowerName,
                LocalDate priceDate, BigDecimal price, String createdBy);

    void updateByClientFlowerDate(Long clientId, String flowerName, LocalDate priceDate,
                                  BigDecimal price, String updatedBy, String flowerId);

    void updatePrice(Long clientId, Long priceConfigId, BigDecimal price, String updatedBy);

    void delete(Long clientId, Long priceConfigId);

    List<Map<String, Object>> findTickerData(Long clientId, LocalDate today);

    List<Map<String, Object>> findPriceHistory(Long clientId, String flowerId, LocalDate fromDate, LocalDate toDate);
}