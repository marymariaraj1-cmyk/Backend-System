package com.billing.service.impl;

import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerLedgerReportDao;
import com.billing.dao.OpeningBalanceConfigDao;
import com.billing.entity.FarmerLedger;
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

    public FarmerAccountCheckServiceImpl(DataSource dataSource,
                                          FarmerLedgerDao farmerLedgerDao,
                                          FarmerLedgerReportDao farmerLedgerReportDao,
                                          OpeningBalanceConfigDao openingBalanceConfigDao,
                                          LedgerSettlementService ledgerSettlementService) {
        this.dataSource = dataSource;
        this.farmerLedgerDao = farmerLedgerDao;
        this.farmerLedgerReportDao = farmerLedgerReportDao;
        this.openingBalanceConfigDao = openingBalanceConfigDao;
        this.ledgerSettlementService = ledgerSettlementService;
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
        LocalDate today = LocalDate.now();
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            farmerLedgerDao.setDebitAmt(clientId, clientUsername, farmerId, farmerName, today, finalAmount, conn);

            ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, today, conn);

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
