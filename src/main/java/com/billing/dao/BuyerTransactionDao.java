package com.billing.dao;

import com.billing.entity.BuyerTransaction;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

public interface BuyerTransactionDao {

    BuyerTransaction insert(BuyerTransaction txn, Connection conn);

    List<BuyerTransaction> findByBuyerAndDateRange(Long clientId, String buyerId, LocalDate fromDate, LocalDate toDate);
}
