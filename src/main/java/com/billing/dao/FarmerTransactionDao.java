package com.billing.dao;

import com.billing.entity.FarmerTransaction;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

public interface FarmerTransactionDao {

    FarmerTransaction insert(FarmerTransaction txn, Connection conn);

    List<FarmerTransaction> findByFarmerAndDateRange(Long clientId, String farmerId, LocalDate fromDate, LocalDate toDate);

    java.math.BigDecimal sumDebAmt(Long clientId, String farmerId, LocalDate transactionDate, Connection conn);

    List<java.math.BigDecimal> findDebAmts(Long clientId, String farmerId, LocalDate transactionDate);

    java.math.BigDecimal findLatestCashPaid(Long clientId, String farmerId, LocalDate transactionDate);

    FarmerTransaction findById(Long clientId, Long farmerTransactionId, Connection conn);

    int updateAmounts(Long clientId, Long farmerTransactionId, java.math.BigDecimal excessDebitAmt,
                      java.math.BigDecimal debAmt, String paymentMode, Connection conn);

    int deleteById(Long clientId, Long farmerTransactionId, Connection conn);

    /**
     * Sets total debit amount for the given farmer and date to match newTotalDebit.
     * This will zero out all existing debit entries for that date and insert a single
     * debit transaction entry if newTotalDebit > 0.
     */
    void setTotalDebitForDate(Long clientId, String clientUsername, String farmerId, String farmerName,
                             LocalDate transactionDate, java.math.BigDecimal newTotalDebit, Connection conn);
}
