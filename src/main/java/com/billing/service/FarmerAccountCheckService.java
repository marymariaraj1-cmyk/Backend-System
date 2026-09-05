package com.billing.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface FarmerAccountCheckService {

    List<Map<String, Object>> getActiveLedgerRows(Long clientId, String farmerId);

    BigDecimal getLastActiveClosingBalance(Long clientId, String farmerId);

    boolean previewWillCauseZeroClose(Long clientId, String clientUsername, String farmerId,
                                       String farmerName, BigDecimal finalAmount);

    void commitDebitWrite(Long clientId, String clientUsername, String farmerId,
                          String farmerName, BigDecimal finalAmount);
}
