package com.billing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface OpeningBalanceConfigService {

    List<Map<String, Object>> getFarmersWithOpeningBalance(Long clientId);

    List<Map<String, Object>> getBuyersWithOpeningBalance(Long clientId);

    void saveFarmerOpeningBalance(Long clientId, String clientUsername, String farmerId, BigDecimal openingBalance, LocalDate openingBalanceDate);

    void saveBuyerOpeningBalance(Long clientId, String clientUsername, String buyerId, BigDecimal openingBalance, LocalDate openingBalanceDate);
}
