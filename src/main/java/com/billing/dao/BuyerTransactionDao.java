package com.billing.dao;

import com.billing.entity.BuyerTransaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

public interface BuyerTransactionDao {

    BuyerTransaction insert(BuyerTransaction txn, Connection conn);

    List<BuyerTransaction> findByBuyerAndDateRange(Long clientId, String buyerId, LocalDate fromDate, LocalDate toDate);

    BuyerTransaction findById(Long clientId, Long buyerTransactionId, Connection conn);

    int updateAmounts(Long clientId, Long buyerTransactionId, BigDecimal cashPaidAmt, BigDecimal disAmt, String paymentMode, Connection conn);

    int deleteById(Long clientId, Long buyerTransactionId, Connection conn);
}
