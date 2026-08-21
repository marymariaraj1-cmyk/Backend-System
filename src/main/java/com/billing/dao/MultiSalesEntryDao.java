package com.billing.dao;

import com.billing.entity.Sales;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

public interface MultiSalesEntryDao {

    List<Sales> findByClientIdAndDate(Long clientId, LocalDate salesDate);

    List<Sales> saveBatch(List<Sales> salesList, Connection conn);
}
