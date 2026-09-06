package com.billing.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface InactiveListDao {

    List<Map<String, Object>> findInactiveFarmers(Long clientId, LocalDate date);

    List<Map<String, Object>> findInactiveBuyers(Long clientId, LocalDate date);
}