package com.billing.service.impl;

import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerLedgerReportDao;
import com.billing.dao.FarmerTransactionDao;
import com.billing.dao.OpeningBalanceConfigDao;
import com.billing.entity.FarmerLedger;
import com.billing.entity.FarmerTransaction;
import com.billing.service.FarmerAccountCheckService;
import com.billing.service.LedgerSettlementService;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FarmerAccountCheckServiceImpl implements FarmerAccountCheckService {

    private static final Logger logger = LoggerFactory.getLogger(FarmerAccountCheckServiceImpl.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final DataSource dataSource;
    private final FarmerLedgerDao farmerLedgerDao;
    private final FarmerLedgerReportDao farmerLedgerReportDao;
    private final OpeningBalanceConfigDao openingBalanceConfigDao;
    private final LedgerSettlementService ledgerSettlementService;
    private final FarmerTransactionDao farmerTransactionDao;

    public FarmerAccountCheckServiceImpl(DataSource dataSource,
                                          FarmerLedgerDao farmerLedgerDao,
                                          FarmerLedgerReportDao farmerLedgerReportDao,
                                          OpeningBalanceConfigDao openingBalanceConfigDao,
                                          LedgerSettlementService ledgerSettlementService,
                                          FarmerTransactionDao farmerTransactionDao) {
        this.dataSource = dataSource;
        this.farmerLedgerDao = farmerLedgerDao;
        this.farmerLedgerReportDao = farmerLedgerReportDao;
        this.openingBalanceConfigDao = openingBalanceConfigDao;
        this.ledgerSettlementService = ledgerSettlementService;
        this.farmerTransactionDao = farmerTransactionDao;
    }

    @Override
    public List<Map<String, Object>> getActiveLedgerRows(Long clientId, String farmerId) {
        logger.info("getActiveLedgerRows: clientId={}, farmerId={}", clientId, farmerId);
        List<Map<String, Object>> allRows = farmerLedgerReportDao.findAll(clientId, "", farmerId);
        List<Map<String, Object>> activeRows = new ArrayList<>();
        for (Map<String, Object> row : allRows) {
            String la = row.get("ledgerActive") == null ? "Y" : ((String) row.get("ledgerActive")).trim();
            if ("Y".equalsIgnoreCase(la)) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("salesDate", row.get("salesDate"));
                out.put("creditAmt", row.get("creditAmt"));
                out.put("debitAmt", row.get("debitAmt"));
                activeRows.add(out);
            }
        }
        return activeRows;
    }

    @Override
    public BigDecimal getLastActiveClosingBalance(Long clientId, String farmerId) {
        logger.info("getLastActiveClosingBalance: clientId={}, farmerId={}", clientId, farmerId);
        LocalDate currentMonthStart = LocalDate.now().withDayOfMonth(1);
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            List<FarmerLedger> allRows = farmerLedgerDao.findAll(clientId, farmerId, conn);
            BigDecimal obValue = openingBalanceConfigDao.findFarmerOpeningBalance(clientId, farmerId, conn);
            LocalDate obDate = openingBalanceConfigDao.findFarmerOpeningBalanceDate(clientId, farmerId, conn);

            BigDecimal activeRunning = ZERO;
            boolean haveActive = false;
            BigDecimal running = ZERO;

            for (FarmerLedger row : allRows) {
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
                running = closing;
                if (active && date.isBefore(currentMonthStart)) {
                    activeRunning = closing;
                    haveActive = true;
                }
            }
            return haveActive ? activeRunning : null;
        } catch (Exception e) {
            logger.error("getLastActiveClosingBalance: error", e);
            throw new RuntimeException("Failed to fetch closing balance", e);
        } finally {
            closeConn(conn);
        }
    }

    @Override
    public boolean previewWillCauseZeroClose(Long clientId, String clientUsername, String farmerId,
                                               String farmerName, BigDecimal finalAmount) {
        logger.info("previewWillCauseZeroClose: clientId={}, farmerId={}, finalAmount={}", clientId, farmerId, finalAmount);
        LocalDate today = LocalDate.now();

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            List<FarmerLedger> allRows = farmerLedgerDao.findAll(clientId, farmerId, conn);
            BigDecimal obValue = openingBalanceConfigDao.findFarmerOpeningBalance(clientId, farmerId, conn);
            LocalDate obDate = openingBalanceConfigDao.findFarmerOpeningBalanceDate(clientId, farmerId, conn);

            BigDecimal running = ZERO;
            BigDecimal activeRunning = ZERO;
            boolean haveActive = false;
            BigDecimal todayClosing = null;

            boolean todayRowExists = false;
            for (FarmerLedger row : allRows) {
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
                BigDecimal debit;
                if (date.equals(today)) {
                    todayRowExists = true;
                    debit = finalAmount;
                } else {
                    debit = row.getDebitAmt() == null ? ZERO : row.getDebitAmt();
                }
                BigDecimal closing = RoundOffUtil.round(opening.add(credit).subtract(debit));
                if (date.equals(today)) {
                    todayClosing = closing;
                }
                running = closing;
                if (active) {
                    activeRunning = closing;
                    haveActive = true;
                }
            }

            if (!todayRowExists) {
                BigDecimal opening = haveActive ? activeRunning : ZERO;
                if (obValue != null && obDate != null && obDate.equals(today)) {
                    opening = opening.add(obValue);
                }
                todayClosing = RoundOffUtil.round(opening.subtract(finalAmount));
            }

            return todayClosing != null && todayClosing.compareTo(ZERO) == 0;
        } catch (Exception e) {
            logger.error("previewWillCauseZeroClose: error", e);
            throw new RuntimeException("Failed to preview ledger close", e);
        } finally {
            closeConn(conn);
        }
    }

    @Override
    public void commitDebitWrite(Long clientId, String clientUsername, String farmerId,
                                  String farmerName, BigDecimal finalAmount) {
        logger.info("commitDebitWrite: clientId={}, farmerId={}, finalAmount={}", clientId, farmerId, finalAmount);
        if (finalAmount == null || finalAmount.signum() == 0) {
            logger.warn("commitDebitWrite: final amount is zero or empty, skipping ledger update");
            return;
        }
        LocalDate today = LocalDate.now();
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            deactivateSameDayRealRow(clientId, farmerId, today, conn);

            if (finalAmount.signum() < 0) {
                BigDecimal amount = finalAmount.abs();
                farmerLedgerDao.setSettlementDebitAmt(clientId, clientUsername, farmerId, farmerName, today, amount, conn);
                insertAccountCheckTransaction(clientId, clientUsername, farmerId, farmerName, today, ZERO, amount, conn);
            } else if (finalAmount.signum() > 0) {
                BigDecimal amount = finalAmount.abs();
                farmerLedgerDao.setSettlementCreditAmt(clientId, clientUsername, farmerId, farmerName, today, amount, conn);
                insertAccountCheckTransaction(clientId, clientUsername, farmerId, farmerName, today, amount, ZERO, conn);
            }

            ledgerSettlementService.inactivateFarmerLedger(clientId, farmerId, today, conn);

            conn.commit();
            logger.info("commitDebitWrite: committed for farmerId={}, date={}", farmerId, today);
        } catch (Exception e) {
            logger.error("commitDebitWrite: transaction failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("rollback failed", rollbackEx);
                }
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to commit debit write", e);
        } finally {
            closeConn(conn);
        }
    }

    private void deactivateSameDayRealRow(Long clientId, String farmerId, LocalDate today, Connection conn) {
        FarmerLedger todayRow = farmerLedgerDao.findRow(clientId, farmerId, today, conn);
        if (todayRow == null || !isActive(todayRow.getLedgerActive())) {
            return;
        }
        String existingSalesIds = todayRow.getSalesIds();
        boolean isSettlement = existingSalesIds != null && "0".equals(existingSalesIds.trim());
        if (isSettlement) {
            return;
        }
        logger.info("deactivateSameDayRealRow: deactivating same-day real ledger row for farmerId={}, date={}", farmerId, today);
        farmerLedgerDao.deactivateLedgerRows(clientId, farmerId, today, conn);
        farmerLedgerDao.mergeDeactivatedRows(clientId, farmerId, today, conn);
    }

    private void insertAccountCheckTransaction(Long clientId, String clientUsername, String farmerId, String farmerName,
                                           LocalDate transactionDate, BigDecimal cashPaidAmt, BigDecimal excessDebitAmt,
                                           Connection conn) {
        FarmerTransaction txn = new FarmerTransaction();
        txn.setClientId(clientId);
        txn.setClientUsername(clientUsername);
        txn.setFarmerId(farmerId);
        txn.setFarmerName(farmerName);
        txn.setTransactionDate(transactionDate);
        txn.setCashPaidAmt(cashPaidAmt);
        txn.setExcessDebitAmt(excessDebitAmt);
        txn.setDebAmt(ZERO);
        txn.setPaymentMode("C");
        farmerTransactionDao.insert(txn, conn);
        logger.info("insertAccountCheckTransaction: farmer transaction inserted for farmerId={}, date={}, cashPaidAmt={}, excessDebitAmt={}",
                farmerId, transactionDate, cashPaidAmt, excessDebitAmt);
    }

    private boolean isActive(String ledgerActive) {
        return ledgerActive == null || "Y".equalsIgnoreCase(ledgerActive.trim());
    }

    private void closeConn(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception closeEx) {
                logger.error("close failed", closeEx);
            }
        }
    }
}
