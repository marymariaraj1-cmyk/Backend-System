package com.billing.service;

import java.util.List;
import java.util.Map;

public interface CurrentDayProfitService {

    List<Map<String, Object>> getTodaySummary(Long clientId);

    List<Map<String, Object>> getTodaySales(Long clientId, String farmerId);
}
