package com.billing.service;

import java.sql.Connection;
import java.time.LocalDate;

public interface LedgerSettlementService {

    void settleFarmerIfClosed(Long clientId, String farmerId, LocalDate writtenDate, Connection conn);

    void settleBuyerIfClosed(Long clientId, String buyerId, LocalDate writtenDate, Connection conn);

    void inactivateFarmerLedger(Long clientId, String farmerId, LocalDate writtenDate, Connection conn);
}
