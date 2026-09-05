package com.billing.dao;

import com.billing.entity.FarmerLedger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

public interface FarmerLedgerDao {

    FarmerLedger insert(FarmerLedger ledger, Connection conn);

    FarmerLedger findRow(Long clientId, String farmerId, LocalDate date, Connection conn);

    FarmerLedger findLatestBefore(Long clientId, String farmerId, LocalDate date, Connection conn);

    List<FarmerLedger> findAll(Long clientId, String farmerId, Connection conn);

    String findLedgerActive(Long clientId, String farmerId, LocalDate date, Connection conn);

    void deactivateLedgerRows(Long clientId, String farmerId, LocalDate upToDate, Connection conn);

    void mergeDeactivatedRows(Long clientId, String farmerId, LocalDate upToDate, Connection conn);

    void updateOpeningBalance(Long clientId, String farmerId, LocalDate date, BigDecimal openingBalance, Connection conn);

    LocalDate findLatestDate(Long clientId, String farmerId, Connection conn);

    void decreaseCreditAmt(Long clientId, String farmerId, LocalDate date, BigDecimal amount, Connection conn);

    void adjustCreditAmt(Long clientId, String farmerId, LocalDate date, BigDecimal amount, Connection conn);

    void setCreditAmt(Long clientId, String clientUsername, String farmerId, String farmerName,
                      LocalDate date, BigDecimal amount, Connection conn);

    String findSalesIds(Long clientId, String farmerId, LocalDate date, String wantedActive);

    void setDebitAmt(Long clientId, String clientUsername, String farmerId, String farmerName,
                     LocalDate date, BigDecimal amount, Connection conn);

    void setSettlementCreditAmt(Long clientId, String clientUsername, String farmerId, String farmerName,
                                LocalDate date, BigDecimal amount, Connection conn);

    void setSettlementDebitAmt(Long clientId, String clientUsername, String farmerId, String farmerName,
                               LocalDate date, BigDecimal amount, Connection conn);
}
