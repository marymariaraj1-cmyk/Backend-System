package com.billing.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BagCountConfigDao {

    List<Map<String, Object>> findAllFlowers(Long clientId);

    List<Map<String, Object>> findAllFarmers(Long clientId);

    List<Map<String, Object>> findAll(Long clientId);

    Map<String, Object> findByFarmerFlowerAndDate(Long clientId, String farmerId, String flowerId, LocalDate salesDate);

    List<Map<String, Object>> findForReport(Long clientId, String farmerId, LocalDate fromDate, LocalDate toDate);

    void upsert(Long clientId, String clientUsername, String farmerId, String farmerName,
                String flowerId, String flowerName, LocalDate salesDate, Integer bagCount);

    void delete(Long clientId, String farmerId, String flowerId, LocalDate salesDate);

    Integer sumBagCountForFarmerFlowerAndDate(Long clientId, String farmerId, String flowerId, LocalDate salesDate);
}