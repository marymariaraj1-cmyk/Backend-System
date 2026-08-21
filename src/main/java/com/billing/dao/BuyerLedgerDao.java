package com.billing.dao;

import com.billing.entity.BuyerLedger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

public interface BuyerLedgerDao {

    BuyerLedger insert(BuyerLedger ledger, Connection conn);

    BuyerLedger findRow(Long clientId, String buyerId, LocalDate date, Connection conn);

    BuyerLedger findLatestBefore(Long clientId, String buyerId, LocalDate date, Connection conn);

    BuyerLedger findLatestNonZeroDebitBefore(Long clientId, String buyerId, LocalDate date, Connection conn);

    List<BuyerLedger> findAll(Long clientId, String buyerId, Connection conn);

    String findLedgerActive(Long clientId, String buyerId, LocalDate date, Connection conn);

    void deactivateLedgerRows(Long clientId, String buyerId, LocalDate upToDate, Connection conn);

    void mergeDeactivatedRows(Long clientId, String buyerId, LocalDate upToDate, Connection conn);

    void updateOpeningBalance(Long clientId, String buyerId, LocalDate date, BigDecimal openingBalance, Connection conn);

    LocalDate findLatestDate(Long clientId, String buyerId, Connection conn);

    void decreaseDebitAmt(Long clientId, String buyerId, LocalDate date, BigDecimal amount, Connection conn);

    void addDiscountAmt(Long clientId, String buyerId, LocalDate date, BigDecimal amount, Connection conn);

    BigDecimal getOpeningBalance(Long clientId, String buyerId);

    String findSalesIds(Long clientId, String buyerId, LocalDate date, String wantedActive);
}
