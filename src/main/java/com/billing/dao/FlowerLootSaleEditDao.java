package com.billing.dao;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FlowerLootSaleEditDao {

    List<Map<String, Object>> fetchFlowerLootSaleRows(Long clientId, String flowerType, LocalDate salesDate);

    List<Map<String, Object>> findFarmerDayRows(Long clientId, String farmerId, LocalDate salesDate, Connection conn);

    void updateSalesRowFarmerName(Long salesId, Long clientId, String farmerName, String farmerId, Connection conn);
}