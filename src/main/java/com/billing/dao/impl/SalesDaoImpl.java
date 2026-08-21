package com.billing.dao.impl;

import com.billing.dao.SalesDao;
import com.billing.entity.Sales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SalesDaoImpl implements SalesDao {

    private static final Logger logger = LoggerFactory.getLogger(SalesDaoImpl.class);

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_SALES (CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PRICE, BUYER_ID, PERKG_RATE, CUST_NAME, DEBIT_CREDIT_FLAG, SALE_SLOT_ID) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE BLOOMBUDDY_SALES SET CLIENT_ID=?, CLIENT_USERNAME=?, FARMER_ID=?, FARMER_NAME=?, SALES_DATE=?, FLOWER_TYPE=?, TOTAL_WEIGHT=?, PRICE=?, BUYER_ID=?, PERKG_RATE=?, CUST_NAME=?, DEBIT_CREDIT_FLAG=?, SALE_SLOT_ID=? "
                    + "WHERE SALES_ID=? AND CLIENT_ID=?";

    private static final String SELECT_BY_ID_SQL =
            "SELECT SALES_ID, CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PRICE, BUYER_ID, PERKG_RATE, CUST_NAME, DEBIT_CREDIT_FLAG, SALE_SLOT_ID "
                    + "FROM BLOOMBUDDY_SALES WHERE SALES_ID=? AND CLIENT_ID=?";

    private static final String SELECT_SQL =
            "SELECT SALES_ID, CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PRICE, BUYER_ID, PERKG_RATE, CUST_NAME, DEBIT_CREDIT_FLAG, SALE_SLOT_ID "
                    + "FROM BLOOMBUDDY_SALES WHERE CLIENT_ID = ? AND FARMER_NAME = ? AND SALES_DATE = ? "
                    + "ORDER BY SALES_ID DESC";

    private final DataSource dataSource;

    @Autowired
    public SalesDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Sales> saveBatch(List<Sales> salesList, Connection conn) {
        logger.info("saveBatch: entering, rows to insert = {}", salesList == null ? 0 : salesList.size());
        if (salesList == null || salesList.isEmpty()) {
            logger.warn("saveBatch: empty list, nothing to insert");
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
            logger.info("saveBatch: executed batch, generated IDs = {}", generatedIds);
        } catch (SQLException e) {
            logger.error("saveBatch: SQL exception while inserting sales rows", e);
            throw new RuntimeException("Failed to save sales records", e);
        }
        List<Sales> saved = new ArrayList<>();
        for (int i = 0; i < generatedIds.size(); i++) {
            Sales original = salesList.get(i);
            original.setSalesId(generatedIds.get(i));
            saved.add(original);
        }
        logger.info("saveBatch: exiting, mapped saved rows = {}", saved.size());
        return saved;
    }

    @Override
    public Sales updateSales(Sales sales) {
        logger.info("updateSales: entering for salesId={}", sales.getSalesId());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_SQL)) {
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
            ps.setLong(14, sales.getSalesId());
            ps.setLong(15, sales.getClientId());
            int rowsAffected = ps.executeUpdate();
            logger.info("updateSales: rows affected = {}", rowsAffected);
            if (rowsAffected == 0) {
                throw new RuntimeException("No record found with SALES_ID=" + sales.getSalesId());
            }
        } catch (SQLException e) {
            logger.error("updateSales: SQL exception while updating sales row id={}", sales.getSalesId(), e);
            throw new RuntimeException("Failed to update sales record", e);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setLong(1, sales.getSalesId());
            ps.setLong(2, sales.getClientId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Sales updated = mapRow(rs);
                    logger.info("updateSales: exiting, updated record returned");
                    return updated;
                }
            }
        } catch (SQLException e) {
            logger.error("updateSales: SQL exception while fetching updated row id={}", sales.getSalesId(), e);
            throw new RuntimeException("Failed to fetch updated sales record", e);
        }
        return sales;
    }

    @Override
    public List<Sales> findByFarmerAndDate(Long clientId, String farmerName, java.time.LocalDate salesDate, int limit) {
        logger.info("findByFarmerAndDate: entering for clientId={}, farmer={}, date={}, limit={}",
                clientId, farmerName, salesDate, limit);
        List<Sales> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerName);
            ps.setDate(3, Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next() && count < limit) {
                    result.add(mapRow(rs));
                    count++;
                }
            }
        } catch (SQLException e) {
            logger.error("findByFarmerAndDate: SQL exception while fetching sales rows", e);
            throw new RuntimeException("Failed to fetch saved sales records", e);
        }
        logger.info("findByFarmerAndDate: exiting, rows found = {}", result.size());
        return result;
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
