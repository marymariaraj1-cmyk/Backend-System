package com.billing.dao;

import com.billing.entity.FarmerTransaction;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

public interface FarmerTransactionDao {

    FarmerTransaction insert(FarmerTransaction txn, Connection conn);

    List<FarmerTransaction> findByFarmerAndDateRange(Long clientId, String farmerId, LocalDate fromDate, LocalDate toDate);
}
