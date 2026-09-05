package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.OpeningBalanceConfigDao;
import com.billing.entity.BuyerLedger;
import com.billing.entity.FarmerLedger;
import com.billing.service.LedgerSettlementService;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LedgerSettlementServiceImpl implements LedgerSettlementService {

    private static final Logger logger = LoggerFactory.getLogger(LedgerSettlementServiceImpl.class);

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final FarmerLedgerDao farmerLedgerDao;
    private final BuyerLedgerDao buyerLedgerDao;
    private final OpeningBalanceConfigDao openingBalanceConfigDao;

    public LedgerSettlementServiceImpl(FarmerLedgerDao farmerLedgerDao,
                                       BuyerLedgerDao buyerLedgerDao,
                                       OpeningBalanceConfigDao openingBalanceConfigDao) {
        this.farmerLedgerDao = farmerLedgerDao;
        this.buyerLedgerDao = buyerLedgerDao;
        this.openingBalanceConfigDao = openingBalanceConfigDao;
    }

    @Override
    public void settleFarmerIfClosed(Long clientId, String farmerId, LocalDate writtenDate, Connection conn) {
        List<FarmerLedger> rows = farmerLedgerDao.findAll(clientId, farmerId, conn);
        if (rows.isEmpty()) {
            return;
        }
        BigDecimal obValue = openingBalanceConfigDao.findFarmerOpeningBalance(clientId, farmerId, conn);
        LocalDate obDate = openingBalanceConfigDao.findFarmerOpeningBalanceDate(clientId, farmerId, conn);
        SettleResult result = findLatestFarmerZeroDate(rows, writtenDate, obValue, obDate);
        if (result.zeroDate != null) {
            farmerLedgerDao.deactivateLedgerRows(clientId, farmerId, result.zeroDate, conn);
            farmerLedgerDao.mergeDeactivatedRows(clientId, farmerId, result.zeroDate, conn);
            stampFarmerOpenings(clientId, farmerId, result, conn);
            openingBalanceConfigDao.clearFarmerOpeningBalance(clientId, farmerId, conn);
        }
    }

    @Override
    public void inactivateFarmerLedger(Long clientId, String farmerId, LocalDate writtenDate, Connection conn) {
        List<FarmerLedger> rows = farmerLedgerDao.findAll(clientId, farmerId, conn);
        if (rows.isEmpty()) {
            return;
        }
        BigDecimal obValue = openingBalanceConfigDao.findFarmerOpeningBalance(clientId, farmerId, conn);
        LocalDate obDate = openingBalanceConfigDao.findFarmerOpeningBalanceDate(clientId, farmerId, conn);
        SettleResult result = findLatestFarmerZeroDate(rows, writtenDate, obValue, obDate);
        LocalDate inactivationDate = writtenDate.minusDays(1);
        farmerLedgerDao.deactivateLedgerRows(clientId, farmerId, inactivationDate, conn);
        farmerLedgerDao.mergeDeactivatedRows(clientId, farmerId, inactivationDate, conn);
        stampFarmerOpenings(clientId, farmerId, result, conn);
        openingBalanceConfigDao.clearFarmerOpeningBalance(clientId, farmerId, conn);
    }

    @Override
    public void settleBuyerIfClosed(Long clientId, String buyerId, LocalDate writtenDate, Connection conn) {
        List<BuyerLedger> rows = buyerLedgerDao.findAll(clientId, buyerId, conn);
        if (rows.isEmpty()) {
            return;
        }
        BigDecimal obValue = openingBalanceConfigDao.findBuyerOpeningBalance(clientId, buyerId, conn);
        LocalDate obDate = openingBalanceConfigDao.findBuyerOpeningBalanceDate(clientId, buyerId, conn);
        SettleResult result = findLatestBuyerZeroDate(rows, writtenDate, obValue, obDate);
        if (result.zeroDate != null) {
            buyerLedgerDao.deactivateLedgerRows(clientId, buyerId, result.zeroDate, conn);
            buyerLedgerDao.mergeDeactivatedRows(clientId, buyerId, result.zeroDate, conn);
            stampBuyerOpenings(clientId, buyerId, result, conn);
            openingBalanceConfigDao.clearBuyerOpeningBalance(clientId, buyerId, conn);
        }
    }

    private void stampFarmerOpenings(Long clientId, String farmerId, SettleResult result, Connection conn) {
        for (Map.Entry<LocalDate, BigDecimal> entry : result.openings.entrySet()) {
            if (result.zeroDate == null || !entry.getKey().isAfter(result.zeroDate)) {
                farmerLedgerDao.updateOpeningBalance(clientId, farmerId, entry.getKey(), entry.getValue(), conn);
            }
        }
    }

    private void stampBuyerOpenings(Long clientId, String buyerId, SettleResult result, Connection conn) {
        for (Map.Entry<LocalDate, BigDecimal> entry : result.openings.entrySet()) {
            if (!entry.getKey().isAfter(result.zeroDate)) {
                buyerLedgerDao.updateOpeningBalance(clientId, buyerId, entry.getKey(), entry.getValue(), conn);
            }
        }
    }

    private SettleResult findLatestFarmerZeroDate(List<FarmerLedger> rows, LocalDate writtenDate,
                                                  BigDecimal obValue, LocalDate obDate) {
        BigDecimal running = ZERO;
        BigDecimal activeRunning = ZERO;
        boolean haveActive = false;
        LocalDate latestZero = null;
        Map<LocalDate, BigDecimal> openings = new LinkedHashMap<>();
        for (FarmerLedger row : rows) {
            LocalDate date = row.getSalesDate();
            boolean active = isActive(row.getLedgerActive());
            BigDecimal opening;
            if (active) {
                opening = haveActive ? activeRunning : ZERO;
                if (obValue != null && obDate != null && obDate.equals(date)) {
                    opening = opening.add(obValue);
                }
            } else {
                opening = running;
            }
            BigDecimal credit = row.getCreditAmt() == null ? ZERO : row.getCreditAmt();
            BigDecimal debit = row.getDebitAmt() == null ? ZERO : row.getDebitAmt();
            BigDecimal closing = RoundOffUtil.round(opening.add(credit).subtract(debit));
            if (active && closing.compareTo(ZERO) == 0) {
                latestZero = date;
            }
            openings.putIfAbsent(date, opening);
            running = closing;
            if (active) {
                activeRunning = closing;
                haveActive = true;
            }
        }
        return new SettleResult(latestZero, openings);
    }

    private SettleResult findLatestBuyerZeroDate(List<BuyerLedger> rows, LocalDate writtenDate,
                                                 BigDecimal obValue, LocalDate obDate) {
        BigDecimal running = ZERO;
        BigDecimal activeRunning = ZERO;
        boolean haveActive = false;
        LocalDate latestZero = null;
        Map<LocalDate, BigDecimal> openings = new LinkedHashMap<>();
        for (BuyerLedger row : rows) {
            LocalDate date = row.getSalesDate();
            boolean active = isActive(row.getLedgerActive());
            BigDecimal opening;
            if (active) {
                opening = haveActive ? activeRunning : ZERO;
                if (obValue != null && obDate != null && obDate.equals(date)) {
                    opening = opening.add(obValue);
                }
            } else {
                opening = running;
            }
            BigDecimal debit = row.getDebitAmt() == null ? ZERO : row.getDebitAmt();
            BigDecimal credit = row.getCreditAmt() == null ? ZERO : row.getCreditAmt();
            BigDecimal closing = RoundOffUtil.round(opening.add(debit).subtract(credit));
            if (active && closing.compareTo(ZERO) == 0) {
                latestZero = date;
            }
            openings.putIfAbsent(date, opening);
            running = closing;
            if (active) {
                activeRunning = closing;
                haveActive = true;
            }
        }
        return new SettleResult(latestZero, openings);
    }

    private boolean isActive(String ledgerActive) {
        return ledgerActive == null || "Y".equalsIgnoreCase(ledgerActive.trim());
    }

    private static class SettleResult {
        private final LocalDate zeroDate;
        private final Map<LocalDate, BigDecimal> openings;

        private SettleResult(LocalDate zeroDate, Map<LocalDate, BigDecimal> openings) {
            this.zeroDate = zeroDate;
            this.openings = openings;
        }
    }
}
