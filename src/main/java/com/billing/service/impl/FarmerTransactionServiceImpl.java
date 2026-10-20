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
                                             String debitAmt, String paymentMode, Long clientId, String clientUsername) {
        logger.info("saveTransaction: farmer={}, date={}, excessDebitAmt={}, debitAmt={}, paymentMode={}", farmerName, transactionDate, excessDebitAmt, debitAmt, paymentMode);

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

        String normalizedMode = paymentMode != null ? paymentMode.trim().toUpperCase() : "C";
        if (!"C".equals(normalizedMode) && !"U".equals(normalizedMode)) {
            normalizedMode = "C";
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
            txn.setPaymentMode(normalizedMode);
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

    private BigDecimal parsePositiveAmount(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (!isValidAmount(trimmed)) {
            throw new IllegalArgumentException(label + " must be a valid amount (digits, up to 2 decimal places, no negative values)");
        }
        BigDecimal amount = RoundOffUtil.round(new BigDecimal(trimmed));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return amount;
    }

    private String normalizeMode(String paymentMode) {
        String normalizedMode = paymentMode != null ? paymentMode.trim().toUpperCase() : "C";
        if (!"C".equals(normalizedMode) && !"U".equals(normalizedMode)) {
            normalizedMode = "C";
        }
        return normalizedMode;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Blocks any mutation of a transaction whose ledger state or origin makes it unsafe to reverse:
     * <ul>
     *   <li>no ledger row for the sales date, or the row is not {@code LEDGER_ACTIVE = 'Y'} (settled / merged away)</li>
     *   <li>rows owned by Farmer Account Check (cash paid, or settlement marker {@code SALES_IDS = '0'})</li>
     * </ul>
     * Sales Entry / Multi Sales Entry debit rows are editable: {@link #applyDelta} already moves the
     * matching debit into BLOOMBUDDY_SALES_TOTALSUMMARY and BLOOMBUDDY_FARMER_LEDGER, so the sales
     * screens and this screen stay in step in both directions.
     */
    private void assertMutable(FarmerTransaction txn, Connection conn) {
        LocalDate date = txn.getTransactionDate();
        FarmerLedger ledger = farmerLedgerDao.findRow(txn.getClientId(), txn.getFarmerId(), date, conn);

        if (ledger == null) {
            throw new IllegalArgumentException(
                    "No farmer ledger entry exists for this date. The record cannot be edited or deleted.");
        }

        String active = ledger.getLedgerActive();
        if (active == null || !"Y".equalsIgnoreCase(active.trim())) {
            throw new IllegalArgumentException(
                    "This transaction belongs to an inactive (settled) farmer ledger period and cannot be edited or deleted.");
        }

        if (txn.getCashPaidAmt() != null && txn.getCashPaidAmt().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException(
                    "This record was created by Farmer Account Check. Edit it from the Farmer Account Check screen.");
        }

        String salesIds = ledger.getSalesIds() != null ? ledger.getSalesIds().trim() : "";
        if ("0".equals(salesIds)) {
            throw new IllegalArgumentException(
                    "This record belongs to a Farmer Account Check settlement and cannot be edited or deleted here.");
        }
    }

    /**
     * Moves the ledger and summary from the transaction's stored amounts to the supplied targets.
     * Deletion passes both targets as {@code BigDecimal.ZERO}, which fully reverses both effects.
     */
    private void applyDelta(FarmerTransaction txn, BigDecimal newExcess, BigDecimal newDebit, Connection conn) {
        Long clientId = txn.getClientId();
        String farmerId = txn.getFarmerId();
        LocalDate date = txn.getTransactionDate();

        BigDecimal deltaExcess = zeroIfNull(newExcess).subtract(zeroIfNull(txn.getExcessDebitAmt()));
        if (deltaExcess.compareTo(BigDecimal.ZERO) != 0) {
            farmerLedgerDao.adjustDebitAmt(clientId, farmerId, date, deltaExcess, conn);
        }

        BigDecimal deltaDebit = zeroIfNull(newDebit).subtract(zeroIfNull(txn.getDebAmt()));
        if (deltaDebit.compareTo(BigDecimal.ZERO) != 0) {
            farmerLedgerDao.adjustCreditAmt(clientId, farmerId, date, deltaDebit.negate(), conn);

            Map<String, Object> summaryRow = salesTotalSummaryDao.findRow(clientId, farmerId, date);
            if (summaryRow != null) {
                salesTotalSummaryDao.adjustDebit(clientId, farmerId, date, deltaDebit, conn);
            } else {
                logger.warn("applyDelta: no sales total summary row for farmerId={}; skipping summary debit adjustment", farmerId);
            }
        }
    }

    @Override
    public FarmerTransaction updateTransaction(Long farmerTransactionId, String excessDebitAmt, String debitAmt,
                                               String paymentMode, Long clientId) {
        logger.info("updateTransaction: farmerTransactionId={}, clientId={}, excessDebitAmt={}, debitAmt={}",
                farmerTransactionId, clientId, excessDebitAmt, debitAmt);

        if (farmerTransactionId == null) {
            throw new IllegalArgumentException("Transaction id is required");
        }

        BigDecimal newExcess = parsePositiveAmount(excessDebitAmt, "Excess Debit Amount");
        BigDecimal newDebit = parsePositiveAmount(debitAmt, "Debit Amount");
        if (newExcess == null && newDebit == null) {
            throw new IllegalArgumentException("Please enter either Excess Debit or Debit Amount to proceed.");
        }
        String normalizedMode = normalizeMode(paymentMode);

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            FarmerTransaction txn = farmerTransactionDao.findById(clientId, farmerTransactionId, conn);
            if (txn == null) {
                throw new IllegalArgumentException("Transaction not found");
            }

            assertMutable(txn, conn);
            applyDelta(txn, newExcess, newDebit, conn);

            int updated = farmerTransactionDao.updateAmounts(clientId, farmerTransactionId,
                    zeroIfNull(newExcess), zeroIfNull(newDebit), normalizedMode, conn);
            if (updated == 0) {
                throw new IllegalArgumentException("Transaction not found");
            }

            ledgerSettlementService.settleFarmerIfClosed(clientId, txn.getFarmerId(), txn.getTransactionDate(), conn);

            conn.commit();
            logger.info("updateTransaction: committed, farmerTransactionId={}", farmerTransactionId);

            txn.setExcessDebitAmt(zeroIfNull(newExcess));
            txn.setDebAmt(zeroIfNull(newDebit));
            txn.setPaymentMode(normalizedMode);
            return txn;

        } catch (Exception e) {
            logger.error("updateTransaction: failed, rolling back", e);
            rollbackQuietly(conn);
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to update farmer transaction", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public void deleteTransaction(Long farmerTransactionId, Long clientId) {
        logger.info("deleteTransaction: farmerTransactionId={}, clientId={}", farmerTransactionId, clientId);

        if (farmerTransactionId == null) {
            throw new IllegalArgumentException("Transaction id is required");
        }

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            FarmerTransaction txn = farmerTransactionDao.findById(clientId, farmerTransactionId, conn);
            if (txn == null) {
                throw new IllegalArgumentException("Transaction not found");
            }

            assertMutable(txn, conn);
            applyDelta(txn, BigDecimal.ZERO, BigDecimal.ZERO, conn);

            int deleted = farmerTransactionDao.deleteById(clientId, farmerTransactionId, conn);
            if (deleted == 0) {
                throw new IllegalArgumentException("Transaction not found");
            }

            farmerLedgerDao.deleteRowIfZero(clientId, txn.getFarmerId(), txn.getTransactionDate(), conn);
            ledgerSettlementService.settleFarmerIfClosed(clientId, txn.getFarmerId(), txn.getTransactionDate(), conn);

            conn.commit();
            logger.info("deleteTransaction: committed, farmerTransactionId={}", farmerTransactionId);

        } catch (Exception e) {
            logger.error("deleteTransaction: failed, rolling back", e);
            rollbackQuietly(conn);
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to delete farmer transaction", e);
        } finally {
            closeQuietly(conn);
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (Exception rollbackEx) {
                logger.error("rollback failed", rollbackEx);
            }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception closeEx) {
                logger.error("close failed", closeEx);
            }
        }
    }
}
