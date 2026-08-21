package com.billing.service.impl;

import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerMasterDao;
import com.billing.dao.FarmerTransactionDao;
import com.billing.dao.SalesTotalSummaryDao;
import com.billing.entity.FarmerLedger;
import com.billing.entity.FarmerTransaction;
import com.billing.service.FarmerTransactionService;
import com.billing.service.LedgerSettlementService;
import com.billing.util.RoundOffUtil;
import com.billing.util.SalesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class FarmerTransactionServiceImpl implements FarmerTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(FarmerTransactionServiceImpl.class);

    private final DataSource dataSource;
    private final FarmerMasterDao farmerMasterDao;
    private final FarmerTransactionDao farmerTransactionDao;
    private final FarmerLedgerDao farmerLedgerDao;
    private final SalesTotalSummaryDao salesTotalSummaryDao;
    private final LedgerSettlementService ledgerSettlementService;

    @Autowired
    public FarmerTransactionServiceImpl(DataSource dataSource,
                                        FarmerMasterDao farmerMasterDao,
                                        FarmerTransactionDao farmerTransactionDao,
                                        FarmerLedgerDao farmerLedgerDao,
                                        SalesTotalSummaryDao salesTotalSummaryDao,
                                        LedgerSettlementService ledgerSettlementService) {
        this.dataSource = dataSource;
        this.farmerMasterDao = farmerMasterDao;
        this.farmerTransactionDao = farmerTransactionDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.salesTotalSummaryDao = salesTotalSummaryDao;
        this.ledgerSettlementService = ledgerSettlementService;
    }

    @Override
    public List<String> getFarmerNames(Long clientId) {
        return farmerMasterDao.findNamesByClientId(clientId);
    }

    @Override
    public FarmerTransaction saveTransaction(String farmerName, String transactionDate, String excessDebitAmt,
                                             String debitAmt, Long clientId, String clientUsername) {
        logger.info("saveTransaction: farmer={}, date={}, excessDebitAmt={}, debitAmt={}", farmerName, transactionDate, excessDebitAmt, debitAmt);

        if (farmerName == null || farmerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Farmer name is required");
        }
        if (transactionDate == null || transactionDate.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction date is required");
        }

        boolean hasExcessDebit = excessDebitAmt != null && !excessDebitAmt.trim().isEmpty();
        boolean hasDebit = debitAmt != null && !debitAmt.trim().isEmpty();

        if (!hasExcessDebit && !hasDebit) {
            throw new IllegalArgumentException("Please enter either Excess Debit or Debit Amount to proceed.");
        }

        BigDecimal excessDebitAmount = null;
        if (hasExcessDebit) {
            if (!isValidAmount(excessDebitAmt.trim())) {
                throw new IllegalArgumentException("Excess Debit Amount must be a valid amount (digits, up to 2 decimal places, no negative values)");
            }
            excessDebitAmount = RoundOffUtil.round(new BigDecimal(excessDebitAmt.trim()));
            if (excessDebitAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Excess Debit Amount must be greater than zero");
            }
        }

        BigDecimal debitAmount = null;
        if (hasDebit) {
            String d = debitAmt.trim();
            if (!isValidAmount(d)) {
                throw new IllegalArgumentException("Debit Amount must be a valid amount (digits, up to 2 decimal places, no negative values)");
            }
            debitAmount = RoundOffUtil.round(new BigDecimal(d));
            if (debitAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Debit Amount must be greater than zero");
            }
        }

        String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, farmerName.trim());
        if (farmerId == null) {
            throw new IllegalArgumentException("Farmer '" + farmerName + "' not found in Farmer Master.");
        }

        LocalDate date = SalesUtil.parseDate(transactionDate);

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            FarmerLedger debitTarget = null;
            if (debitAmount != null && debitAmount.compareTo(BigDecimal.ZERO) > 0) {
                debitTarget = farmerLedgerDao.findRow(clientId, farmerId, date, conn);
            }

            FarmerTransaction txn = new FarmerTransaction();
            txn.setClientId(clientId);
            txn.setClientUsername(clientUsername);
            txn.setFarmerId(farmerId);
            txn.setFarmerName(farmerName.trim());
            txn.setTransactionDate(date);
            txn.setCashPaidAmt(BigDecimal.ZERO);
            txn.setExcessDebitAmt(excessDebitAmount != null ? excessDebitAmount : BigDecimal.ZERO);
            txn.setDebAmt(debitAmount != null ? debitAmount : BigDecimal.ZERO);
            farmerTransactionDao.insert(txn, conn);

            if (excessDebitAmount != null && excessDebitAmount.compareTo(BigDecimal.ZERO) > 0) {
                FarmerLedger farmerLedger = new FarmerLedger();
                farmerLedger.setClientId(clientId);
                farmerLedger.setClientUsername(clientUsername);
                farmerLedger.setFarmerId(farmerId);
                farmerLedger.setFarmerName(farmerName.trim());
                farmerLedger.setSalesDate(date);
                farmerLedger.setDebitAmt(excessDebitAmount);
                farmerLedger.setCreditAmt(BigDecimal.ZERO);
                farmerLedgerDao.insert(farmerLedger, conn);
            }

            if (debitAmount != null && debitAmount.compareTo(BigDecimal.ZERO) > 0) {
                if (debitTarget != null) {
                    farmerLedgerDao.decreaseCreditAmt(clientId, farmerId, date, debitAmount, conn);
                } else {
                    logger.warn("saveTransaction: no farmer ledger entry for farmerId={}; inserting fresh ledger row with negative credit", farmerId);
                    FarmerLedger freshLedger = new FarmerLedger();
                    freshLedger.setClientId(clientId);
                    freshLedger.setClientUsername(clientUsername);
                    freshLedger.setFarmerId(farmerId);
                    freshLedger.setFarmerName(farmerName.trim());
                    freshLedger.setSalesDate(date);
                    freshLedger.setDebitAmt(BigDecimal.ZERO);
                    freshLedger.setCreditAmt(debitAmount.negate());
                    farmerLedgerDao.insert(freshLedger, conn);
                }

                Map<String, Object> summaryRow = salesTotalSummaryDao.findRow(clientId, farmerId, date);
                if (summaryRow != null) {
                    salesTotalSummaryDao.adjustDebit(clientId, farmerId, date, debitAmount, conn);
                } else {
                    logger.warn("saveTransaction: no sales total summary row for farmerId={}; skipping summary debit adjustment", farmerId);
                }
            }

            ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, date, conn);

            conn.commit();
            logger.info("saveTransaction: committed, txnId={}", txn.getFarmerTransactionId());
            return txn;

        } catch (Exception e) {
            logger.error("saveTransaction: transaction failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("rollback failed", rollbackEx);
                }
            }
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to save farmer transaction", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception closeEx) {
                    logger.error("close failed", closeEx);
                }
            }
        }
    }

    @Override
    public List<FarmerTransaction> getTransactionHistory(String farmerName, String fromDate, String toDate, Long clientId) {
        if (farmerName == null || farmerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Farmer name is required");
        }
        String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, farmerName.trim());
        if (farmerId == null) {
            throw new IllegalArgumentException("Farmer '" + farmerName + "' not found.");
        }
        LocalDate from = SalesUtil.parseDate(fromDate);
        LocalDate to = SalesUtil.parseDate(toDate);
        List<FarmerTransaction> history = farmerTransactionDao.findByFarmerAndDateRange(clientId, farmerId, from, to);
        for (FarmerTransaction txn : history) {
            txn.setExcessDebitAmt(RoundOffUtil.round(txn.getExcessDebitAmt()));
            txn.setCashPaidAmt(RoundOffUtil.round(txn.getCashPaidAmt()));
            txn.setDebAmt(RoundOffUtil.round(txn.getDebAmt()));
        }
        return history;
    }

    private boolean isValidAmount(String value) {
        return value != null && value.matches("^\\d+(\\.\\d{1,2})?$");
    }
}
