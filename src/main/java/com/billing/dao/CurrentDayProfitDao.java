package com.billing.dao;

import java.util.List;
import java.util.Map;

public interface CurrentDayProfitDao {

    List<Map<String, Object>> findTodaySummary(Long clientId);

    List<Map<String, Object>> findTodaySales(Long clientId, String farmerId);
}
