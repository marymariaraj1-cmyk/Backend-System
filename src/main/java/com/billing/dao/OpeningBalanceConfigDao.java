package com.billing.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface OpeningBalanceConfigDao {

    List<Map<String, Object>> findAllFarmers(Long clientId);

    List<Map<String, Object>> findAllBuyers(Long clientId);

    Map<String, BigDecimal> findFarmerOpeningBalances(Long clientId);

    Map<String, BigDecimal> findBuyerOpeningBalances(Long clientId);

    Map<String, LocalDate> findFarmerOpeningBalanceDates(Long clientId);

    Map<String, LocalDate> findBuyerOpeningBalanceDates(Long clientId);

    BigDecimal findFarmerOpeningBalance(Long clientId, String farmerId);

    BigDecimal findBuyerOpeningBalance(Long clientId, String buyerId);

    LocalDate findFarmerOpeningBalanceDate(Long clientId, String farmerId);

    LocalDate findBuyerOpeningBalanceDate(Long clientId, String buyerId);

    BigDecimal findFarmerOpeningBalance(Long clientId, String farmerId, Connection conn);

    BigDecimal findBuyerOpeningBalance(Long clientId, String buyerId, Connection conn);

    LocalDate findFarmerOpeningBalanceDate(Long clientId, String farmerId, Connection conn);

    LocalDate findBuyerOpeningBalanceDate(Long clientId, String buyerId, Connection conn);

    boolean isFarmerLedgerSettled(Long clientId, String farmerId, LocalDate date);

    boolean isBuyerLedgerSettled(Long clientId, String buyerId, LocalDate date);

    boolean isFarmerLedgerSettled(Connection conn, Long clientId, String farmerId, LocalDate date);

    boolean isBuyerLedgerSettled(Connection conn, Long clientId, String buyerId, LocalDate date);

    void upsertFarmerOpeningBalance(Long clientId, String clientUsername, String farmerId, BigDecimal openingBalance, LocalDate openingBalanceDate);

    void upsertBuyerOpeningBalance(Long clientId, String clientUsername, String buyerId, BigDecimal openingBalance, LocalDate openingBalanceDate);

    void upsertFarmerOpeningBalance(Connection conn, Long clientId, String clientUsername, String farmerId, BigDecimal openingBalance, LocalDate openingBalanceDate);

    void upsertBuyerOpeningBalance(Connection conn, Long clientId, String clientUsername, String buyerId, BigDecimal openingBalance, LocalDate openingBalanceDate);

    void clearFarmerOpeningBalance(Long clientId, String farmerId, Connection conn);

    void clearBuyerOpeningBalance(Long clientId, String buyerId, Connection conn);
}
