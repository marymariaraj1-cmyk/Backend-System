package com.billing.dao;

import com.billing.entity.Sales;

import java.sql.Connection;
import java.util.List;

public interface SalesDao {

    List<Sales> saveBatch(List<Sales> salesList, Connection conn);

    Sales updateSales(Sales sales);

    List<Sales> findByFarmerAndDate(Long clientId, String farmerName, java.time.LocalDate salesDate, int limit);
}
