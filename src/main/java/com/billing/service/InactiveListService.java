package com.billing.service;

import java.util.List;
import java.util.Map;

public interface InactiveListService {

    List<Map<String, Object>> getInactiveFarmers(Long clientId);

    List<Map<String, Object>> getInactiveBuyers(Long clientId);
}