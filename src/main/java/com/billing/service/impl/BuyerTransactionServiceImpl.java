package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.BuyerMasterDao;
import com.billing.dao.BuyerTransactionDao;
import com.billing.dao.OpeningBalanceConfigDao;
import com.billing.entity.BuyerLedger;
import com.billing.entity.BuyerTransaction;
import com.billing.service.BuyerTransactionService;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Service
public class BuyerTransactionServiceImpl implements BuyerTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(BuyerTransactionServiceImpl.class);

    private static final java.util.Set<String> EXCLUDED_BUYER_NAMES = java.util.Set.of(
            "cash", "upi", "google pay", "gpay", "phonepe", "paytm", "online", "card", "neft", "rtgs", "imps"
    );

    private final DataSource dataSource;
    private final BuyerMasterDao buyerMasterDao;
    private final BuyerTransactionDao buyerTransactionDao;
    private final BuyerLedgerDao buyerLedgerDao;
    private final OpeningBalanceConfigDao openingBalanceConfigDao;
    private final LedgerSettlementService ledgerSettlementService;

    @Autowired
    public BuyerTransactionServiceImpl(DataSource dataSource,
                                       BuyerMasterDao buyerMasterDao,
                                       BuyerTransactionDao buyerTransactionDao,
                                       BuyerLedgerDao buyerLedgerDao,
                                       OpeningBalanceConfigDao openingBalanceConfigDao,
                                       LedgerSettlementService ledgerSettlementService) {
        this.dataSource = dataSource;
        this.buyerMasterDao = buyerMasterDao;
        this.buyerTransactionDao = buyerTransactionDao;
        this.buyerLedgerDao = buyerLedgerDao;
        this.openingBalanceConfigDao = openingBalanceConfigDao;
        this.ledgerSettlementService = ledgerSettlementService;
    }

    @Override
    public List<String> getBuyerNames(Long clientId) {
        List<String> all = buyerMasterDao.findNamesByClientId(clientId);
        return all.stream()
                .filter(name -> EXCLUDED_BUYER_NAMES.stream()
                        .noneMatch(ex -> name.trim().toLowerCase().contains(ex)))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public BuyerTransaction saveTransaction(String buyerName, String transactionDate, String cashPaidAmt, String disAmt, String paymentMode,
                                            Long clientId, String clientUsername) {
        logger.info("saveTransaction: buyer={}, date={}, cashPaidAmt={}, disAmt={}, paymentMode={}", buyerName, transactionDate, cashPaidAmt, disAmt, paymentMode);

        if (buyerName == null || buyerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Buyer name is required");
        }
        if (transactionDate == null || transactionDate.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction date is required");
        }

        boolean hasCash = cashPaidAmt != null && !cashPaidAmt.trim().isEmpty();
        boolean hasDiscount = disAmt != null && !disAmt.trim().isEmpty();

        if (!hasCash && !hasDiscount) {
            throw new IllegalArgumentException("Please enter either Amount Received or Discount Amount to proceed.");
        }

        BigDecimal amountReceived = null;
        if (hasCash) {
            if (!isValidAmount(cashPaidAmt.trim())) {
                throw new IllegalArgumentException("Amount Received must be a valid amount (digits, up to 2 decimal places, no negative values)");
            }
            amountReceived = RoundOffUtil.round(new BigDecimal(cashPaidAmt.trim()));
            if (amountReceived.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount Received must be greater than zero");
            }
        }

        BigDecimal discount = null;
        if (hasDiscount) {
            if (!isValidAmount(disAmt.trim())) {
                throw new IllegalArgumentException("Discount Amount must be a valid amount (digits, up to 2 decimal places, no negative values)");
            }
            discount = RoundOffUtil.round(new BigDecimal(disAmt.trim()));
            if (discount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Discount Amount must be greater than zero");
            }
        }

        String normalizedMode = paymentMode != null ? paymentMode.trim().toUpperCase() : "C";
        if (!"C".equals(normalizedMode) && !"U".equals(normalizedMode)) {
            normalizedMode = "C";
        }

        String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, buyerName.trim());
        if (buyerId == null) {
            throw new IllegalArgumentException("Buyer '" + buyerName + "' not found in Buyer Master.");
        }

        LocalDate date = SalesUtil.parseDate(transactionDate);

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            BuyerTransaction txn = new BuyerTransaction();
            txn.setClientId(clientId);
            txn.setClientUsername(clientUsername);
            txn.setBuyerId(buyerId);
            txn.setBuyerName(buyerName.trim());
            txn.setTransactionDate(date);
            txn.setCashPaidAmt(amountReceived != null ? amountReceived : BigDecimal.ZERO);
            txn.setDisAmt(discount != null ? discount : BigDecimal.ZERO);
            txn.setPaymentMode(normalizedMode);
            buyerTransactionDao.insert(txn, conn);

            if (amountReceived != null && amountReceived.compareTo(BigDecimal.ZERO) > 0) {
                applyCashPayment(clientId, clientUsername, buyerId, buyerName.trim(), date, amountReceived, conn);
            }

            if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
                applyDiscount(clientId, clientUsername, buyerId, buyerName.trim(), date, discount, conn);
            }

            ledgerSettlementService.settleBuyerIfClosed(clientId, buyerId, date, conn);

            conn.commit();
            logger.info("saveTransaction: committed, txnId={}", txn.getBuyerTransactionId());
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
            throw new RuntimeException("Failed to save buyer transaction", e);
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

    private void applyCashPayment(Long clientId, String clientUsername, String buyerId, String buyerName,
                                  LocalDate date, BigDecimal amount, Connection conn) {
        BuyerLedger ledger = new BuyerLedger();
        ledger.setClientId(clientId);
        ledger.setClientUsername(clientUsername);
        ledger.setBuyerId(buyerId);
        ledger.setBuyerName(buyerName);
        ledger.setSalesDate(date);
        ledger.setDebitAmt(BigDecimal.ZERO);
        ledger.setCreditAmt(amount);
        ledger.setDisAmt(BigDecimal.ZERO);
        buyerLedgerDao.insert(ledger, conn);
    }

    private void applyDiscount(Long clientId, String clientUsername, String buyerId, String buyerName,
                               LocalDate date, BigDecimal discount, Connection conn) {
        BuyerLedger target = buyerLedgerDao.findRow(clientId, buyerId, date, conn);
        if (target != null && target.getDebitAmt() != null && target.getDebitAmt().compareTo(BigDecimal.ZERO) > 0) {
            buyerLedgerDao.decreaseDebitAmt(clientId, buyerId, date, discount, conn);
            buyerLedgerDao.addDiscountAmt(clientId, buyerId, date, discount, conn);
            return;
        }
        logger.warn("applyDiscount: no buyer ledger entry with debit on transaction date for buyerId={}; recording discount as fresh row with negative debit", buyerId);
        BuyerLedger freshLedger = new BuyerLedger();
        freshLedger.setClientId(clientId);
        freshLedger.setClientUsername(clientUsername);
        freshLedger.setBuyerId(buyerId);
        freshLedger.setBuyerName(buyerName);
        freshLedger.setSalesDate(date);
        freshLedger.setDebitAmt(discount.negate());
        freshLedger.setCreditAmt(BigDecimal.ZERO);
        freshLedger.setDisAmt(discount);
        buyerLedgerDao.insert(freshLedger, conn);
    }

    @Override
    public List<BuyerTransaction> getTransactionHistory(String buyerName, String fromDate, String toDate, Long clientId) {
        if (buyerName == null || buyerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Buyer name is required");
        }
        String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, buyerName.trim());
        if (buyerId == null) {
            throw new IllegalArgumentException("Buyer '" + buyerName + "' not found.");
        }
        LocalDate from = SalesUtil.parseDate(fromDate);
        LocalDate to = SalesUtil.parseDate(toDate);
        List<BuyerTransaction> history = buyerTransactionDao.findByBuyerAndDateRange(clientId, buyerId, from, to);
        for (BuyerTransaction txn : history) {
            txn.setCashPaidAmt(RoundOffUtil.round(txn.getCashPaidAmt()));
            txn.setDisAmt(RoundOffUtil.round(txn.getDisAmt()));
        }
        return history;
    }

    @Override
    public BigDecimal getOpeningBalance(String buyerName, Long clientId) {
        if (buyerName == null || buyerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Buyer name is required");
        }
        String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, buyerName.trim());
        if (buyerId == null) {
            throw new IllegalArgumentException("Buyer '" + buyerName + "' not found.");
        }
        BigDecimal balance = buyerLedgerDao.getOpeningBalance(clientId, buyerId);
        BigDecimal obValue = openingBalanceConfigDao.findBuyerOpeningBalance(clientId, buyerId);
        LocalDate obDate = openingBalanceConfigDao.findBuyerOpeningBalanceDate(clientId, buyerId);
        if (obValue != null && obDate != null) {
            try (Connection conn = dataSource.getConnection()) {
                String active = buyerLedgerDao.findLedgerActive(clientId, buyerId, obDate, conn);
                if ("Y".equalsIgnoreCase(active != null ? active.trim() : "")) {
                    balance = balance.add(obValue);
                }
            } catch (SQLException e) {
                logger.error("getOpeningBalance: SQL exception while checking opening balance date", e);
                throw new RuntimeException("Failed to fetch buyer opening balance", e);
            }
        }
        return balance;
    }

    private boolean isValidAmount(String value) {
        return value != null && value.matches("^\\d+(\\.\\d{1,2})?$");
    }
}
