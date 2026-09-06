package com.billing.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BagCountConfigDao {

    List<Map<String, Object>> findAllFlowers(Long clientId);

    List<Map<String, Object>> findAll(Long clientId);

    Map<String, Object> findByFlowerAndDate(Long clientId, String flowerId, LocalDate salesDate);

    Map<String, Object> findConfig(Long clientId, String flowerId, LocalDate salesDate);

    void upsert(Long clientId, String clientUsername, String flowerId, String flowerName,
                LocalDate salesDate, Integer bagCount, String bagCheck);

    void delete(Long clientId, String flowerId, LocalDate salesDate);

    Integer sumBagCountForFlowerAndDate(Long clientId, String flowerId, LocalDate salesDate);
}
