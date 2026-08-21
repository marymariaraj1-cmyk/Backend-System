package com.billing.dao.impl;

import com.billing.dao.MultiSalesEntryDao;
import com.billing.entity.Sales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MultiSalesEntryDaoImpl implements MultiSalesEntryDao {

    private static final Logger logger = LoggerFactory.getLogger(MultiSalesEntryDaoImpl.class);

    private static final String SELECT_BY_DATE_SQL =
            "SELECT SALES_ID, CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, "
                    + "FLOWER_TYPE, TOTAL_WEIGHT, PRICE, BUYER_ID, PERKG_RATE, CUST_NAME, DEBIT_CREDIT_FLAG, SALE_SLOT_ID "
                    + "FROM BLOOMBUDDY_SALES WHERE CLIENT_ID = ? AND SALES_DATE = ? ORDER BY SALES_ID ASC";

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_SALES (CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, "
                    + "FLOWER_TYPE, TOTAL_WEIGHT, PRICE, BUYER_ID, PERKG_RATE, CUST_NAME, DEBIT_CREDIT_FLAG, SALE_SLOT_ID) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final DataSource dataSource;

    @Autowired
    public MultiSalesEntryDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Sales> findByClientIdAndDate(Long clientId, LocalDate salesDate) {
        logger.info("findByClientIdAndDate: clientId={}, date={}", clientId, salesDate);
        List<Sales> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_DATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setDate(2, Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findByClientIdAndDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch today's sales entries", e);
        }
        logger.info("findByClientIdAndDate: found {} rows", result.size());
        return result;
    }

    @Override
    public List<Sales> saveBatch(List<Sales> salesList, Connection conn) {
        logger.info("saveBatch: entering, rows={}", salesList == null ? 0 : salesList.size());
        if (salesList == null || salesList.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> generatedIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, new String[]{"SALES_ID"})) {
            for (Sales sales : salesList) {
                ps.setLong(1, sales.getClientId());
                ps.setString(2, sales.getClientUsername());
                ps.setString(3, sales.getFarmerId());
                ps.setString(4, sales.getFarmerName());
                ps.setDate(5, Date.valueOf(sales.getSalesDate()));
                ps.setString(6, sales.getFlowerType());
                ps.setBigDecimal(7, sales.getTotalWeight());
                ps.setBigDecimal(8, sales.getPrice());
                ps.setString(9, sales.getBuyerId());
                ps.setBigDecimal(10, sales.getPerKgRate());
                ps.setString(11, sales.getCustName());
                ps.setString(12, sales.getDebitCreditFlag());
                ps.setString(13, sales.getSaleSlotId());
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                while (keys.next()) {
                    generatedIds.add(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            logger.error("saveBatch: SQL exception", e);
            throw new RuntimeException("Failed to save multiple sales records", e);
        }
        List<Sales> saved = new ArrayList<>();
        for (int i = 0; i < generatedIds.size(); i++) {
            Sales original = salesList.get(i);
            original.setSalesId(generatedIds.get(i));
            saved.add(original);
        }
        return saved;
    }

    private Sales mapRow(ResultSet rs) throws SQLException {
        Sales sales = new Sales();
        sales.setSalesId(rs.getLong("SALES_ID"));
        sales.setClientId(rs.getLong("CLIENT_ID"));
        sales.setClientUsername(rs.getString("CLIENT_USERNAME"));
        sales.setFarmerId(rs.getString("FARMER_ID"));
        sales.setFarmerName(rs.getString("FARMER_NAME"));
        sales.setSalesDate(rs.getDate("SALES_DATE").toLocalDate());
        sales.setFlowerType(rs.getString("FLOWER_TYPE"));
        sales.setTotalWeight(rs.getBigDecimal("TOTAL_WEIGHT"));
        sales.setPrice(rs.getBigDecimal("PRICE"));
        sales.setBuyerId(rs.getString("BUYER_ID"));
        sales.setPerKgRate(rs.getBigDecimal("PERKG_RATE"));
        sales.setCustName(rs.getString("CUST_NAME"));
        sales.setDebitCreditFlag(rs.getString("DEBIT_CREDIT_FLAG"));
        sales.setSaleSlotId(rs.getString("SALE_SLOT_ID"));
        return sales;
    }
}
