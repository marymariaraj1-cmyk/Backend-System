package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.OpeningBalanceConfigDao;
import com.billing.service.LedgerSettlementService;
import com.billing.service.OpeningBalanceConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpeningBalanceConfigServiceImpl implements OpeningBalanceConfigService {

    private static final Logger logger = LoggerFactory.getLogger(OpeningBalanceConfigServiceImpl.class);

    private static final String SETTLED_PERIOD_MESSAGE =
            "This date belongs to a settled ledger period. Please choose a date within the current active period.";

    private final OpeningBalanceConfigDao openingBalanceConfigDao;
    private final FarmerLedgerDao farmerLedgerDao;
    private final BuyerLedgerDao buyerLedgerDao;
    private final LedgerSettlementService ledgerSettlementService;
    private final DataSource dataSource;

    public OpeningBalanceConfigServiceImpl(OpeningBalanceConfigDao openingBalanceConfigDao,
                                           FarmerLedgerDao farmerLedgerDao,
                                           BuyerLedgerDao buyerLedgerDao,
                                           LedgerSettlementService ledgerSettlementService,
                                           DataSource dataSource) {
        this.openingBalanceConfigDao = openingBalanceConfigDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.buyerLedgerDao = buyerLedgerDao;
        this.ledgerSettlementService = ledgerSettlementService;
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> getFarmersWithOpeningBalance(Long clientId) {
        List<Map<String, Object>> farmers = openingBalanceConfigDao.findAllFarmers(clientId);
        Map<String, BigDecimal> balances = openingBalanceConfigDao.findFarmerOpeningBalances(clientId);
        Map<String, LocalDate> dates = openingBalanceConfigDao.findFarmerOpeningBalanceDates(clientId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> farmer : farmers) {
            Map<String, Object> out = new LinkedHashMap<>(farmer);
            String farmerId = (String) farmer.get("farmerId");
            BigDecimal ob = balances.get(farmerId);
            out.put("openingBalance", ob == null ? BigDecimal.ZERO : ob);
            out.put("openingBalanceDate", dates.get(farmerId));
            result.add(out);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getBuyersWithOpeningBalance(Long clientId) {
        List<Map<String, Object>> buyers = openingBalanceConfigDao.findAllBuyers(clientId);
        Map<String, BigDecimal> balances = openingBalanceConfigDao.findBuyerOpeningBalances(clientId);
        Map<String, LocalDate> dates = openingBalanceConfigDao.findBuyerOpeningBalanceDates(clientId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> buyer : buyers) {
            Map<String, Object> out = new LinkedHashMap<>(buyer);
            String buyerId = (String) buyer.get("buyerId");
            BigDecimal ob = balances.get(buyerId);
            out.put("openingBalance", ob == null ? BigDecimal.ZERO : ob);
            out.put("openingBalanceDate", dates.get(buyerId));
            result.add(out);
        }
        return result;
    }

    @Override
    public void saveFarmerOpeningBalance(Long clientId, String clientUsername, String farmerId, BigDecimal openingBalance, LocalDate openingBalanceDate) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (openingBalanceDate != null && openingBalanceConfigDao.isFarmerLedgerSettled(conn, clientId, farmerId, openingBalanceDate)) {
                    throw new IllegalArgumentException(SETTLED_PERIOD_MESSAGE);
                }
                openingBalanceConfigDao.upsertFarmerOpeningBalance(conn, clientId, clientUsername, farmerId, openingBalance, openingBalanceDate);
                LocalDate latestDate = farmerLedgerDao.findLatestDate(clientId, farmerId, conn);
                if (latestDate != null) {
                    ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, latestDate, conn);
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("saveFarmerOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to save farmer opening balance", e);
        }
    }

    @Override
    public void saveBuyerOpeningBalance(Long clientId, String clientUsername, String buyerId, BigDecimal openingBalance, LocalDate openingBalanceDate) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (openingBalanceDate != null && openingBalanceConfigDao.isBuyerLedgerSettled(conn, clientId, buyerId, openingBalanceDate)) {
                    throw new IllegalArgumentException(SETTLED_PERIOD_MESSAGE);
                }
                openingBalanceConfigDao.upsertBuyerOpeningBalance(conn, clientId, clientUsername, buyerId, openingBalance, openingBalanceDate);
                LocalDate latestDate = buyerLedgerDao.findLatestDate(clientId, buyerId, conn);
                if (latestDate != null) {
                    ledgerSettlementService.settleBuyerIfClosed(clientId, buyerId, latestDate, conn);
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("saveBuyerOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to save buyer opening balance", e);
        }
    }
}
