package com.billing.dao.impl;

import com.billing.dao.IntakeDirectSaleDao;
import com.billing.util.SalesRecordType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class IntakeDirectSaleDaoImpl implements IntakeDirectSaleDao {

    private static final Logger logger = LoggerFactory.getLogger(IntakeDirectSaleDaoImpl.class);

    private static final String FIND_FARMER_DAY_ROWS_ALL_SQL =
            "SELECT SALES_ID, FARMER_ID, FARMER_NAME, BUYER_ID, CUST_NAME, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, BAG_COUNT "
                    + "FROM BLOOMBUDDY_SALES "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? "
                    + "ORDER BY SALES_ID";

    private static final String FIND_FLOWER_INTAKE_ROWS_SQL =
            "SELECT SALES_ID, FARMER_ID, FARMER_NAME, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, BAG_COUNT "
                    + "FROM BLOOMBUDDY_SALES "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? "
                    + "AND BUYER_ID IS NULL AND CUST_NAME = ? "
                    + "ORDER BY SALES_ID";

    private static final String FIND_BUYER_DIRECT_ROWS_SQL =
            "SELECT SALES_ID, BUYER_ID, CUST_NAME, FARMER_NAME, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, BAG_COUNT "
                    + "FROM BLOOMBUDDY_SALES "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE = ? "
                    + "AND FARMER_ID IS NULL AND FARMER_NAME = ? "
                    + "ORDER BY SALES_ID";

    private static final String FIND_BUYER_DAY_ROWS_ALL_SQL =
            "SELECT SALES_ID, BUYER_ID, CUST_NAME, FARMER_ID, PRICE "
                    + "FROM BLOOMBUDDY_SALES "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE = ? "
                    + "ORDER BY SALES_ID";

    private static final String UPDATE_SALES_ROW_SQL =
            "UPDATE BLOOMBUDDY_SALES SET FLOWER_TYPE = ?, TOTAL_WEIGHT = ?, PERKG_RATE = ?, PRICE = ?, "
                    + "FLOWER_ID = ?, BAG_COUNT = ? WHERE SALES_ID = ? AND CLIENT_ID = ?";

    private final DataSource dataSource;

    @Autowired
    public IntakeDirectSaleDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> findFarmerDayRowsAll(Long clientId, String farmerId, LocalDate salesDate, Connection conn) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_FARMER_DAY_ROWS_ALL_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapSalesRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findFarmerDayRowsAll: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer day rows", e);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> findFlowerIntakeRows(Long clientId, String farmerId, LocalDate salesDate) {
        logger.info("findFlowerIntakeRows: clientId={}, farmerId={}, date={}", clientId, farmerId, salesDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_FLOWER_INTAKE_ROWS_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            ps.setString(4, SalesRecordType.FLOWER_INTAKE_SALE_MARKER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapSalesRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findFlowerIntakeRows: SQL exception", e);
            throw new RuntimeException("Failed to fetch flower intake rows", e);
        }
        logger.info("findFlowerIntakeRows: rows={}", rows.size());
        return rows;
    }

    @Override
    public List<Map<String, Object>> findBuyerDirectRows(Long clientId, String buyerId, LocalDate salesDate) {
        logger.info("findBuyerDirectRows: clientId={}, buyerId={}, date={}", clientId, buyerId, salesDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BUYER_DIRECT_ROWS_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            ps.setString(4, SalesRecordType.BUYER_DIRECT_SALE_MARKER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapSalesRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findBuyerDirectRows: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer direct rows", e);
        }
        logger.info("findBuyerDirectRows: rows={}", rows.size());
        return rows;
    }

    @Override
    public List<Map<String, Object>> findBuyerDayRowsAll(Long clientId, String buyerId, LocalDate salesDate, Connection conn) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_BUYER_DAY_ROWS_ALL_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesId", rs.getLong("SALES_ID"));
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    row.put("customerName", rs.getString("CUST_NAME"));
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("price", rs.getBigDecimal("PRICE"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findBuyerDayRowsAll: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer day rows", e);
        }
        return rows;
    }

    @Override
    public void updateSalesRow(Long salesId, Long clientId, String flowerType, BigDecimal totalWeight,
                               BigDecimal perKgRate, BigDecimal price, String flowerId, Integer bagCount,
                               Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SALES_ROW_SQL)) {
            ps.setString(1, flowerType);
            ps.setBigDecimal(2, totalWeight);
            ps.setBigDecimal(3, perKgRate);
            ps.setBigDecimal(4, price);
            ps.setString(5, flowerId);
            if (bagCount == null) {
                ps.setNull(6, java.sql.Types.INTEGER);
            } else {
                ps.setInt(6, bagCount);
            }
            ps.setLong(7, salesId);
            ps.setLong(8, clientId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("No record found with SALES_ID=" + salesId);
            }
        } catch (SQLException e) {
            logger.error("updateSalesRow: SQL exception for salesId={}", salesId, e);
            throw new RuntimeException("Failed to update sales row", e);
        }
    }

    private Map<String, Object> mapSalesRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("salesId", rs.getLong("SALES_ID"));
        if (hasColumn(rs, "FARMER_ID")) {
            row.put("farmerId", rs.getString("FARMER_ID"));
        }
        if (hasColumn(rs, "FARMER_NAME")) {
            row.put("farmerName", rs.getString("FARMER_NAME"));
        }
        if (hasColumn(rs, "BUYER_ID")) {
            row.put("buyerId", rs.getString("BUYER_ID"));
        }
        if (hasColumn(rs, "CUST_NAME")) {
            row.put("customerName", rs.getString("CUST_NAME"));
        }
        if (hasColumn(rs, "FLOWER_TYPE")) {
            row.put("flowerType", rs.getString("FLOWER_TYPE"));
        }
        BigDecimal totalWeight = rs.getBigDecimal("TOTAL_WEIGHT");
        BigDecimal perKgRate = rs.getBigDecimal("PERKG_RATE");
        BigDecimal price = rs.getBigDecimal("PRICE");
        row.put("totalWeight", totalWeight);
        row.put("perKgRate", perKgRate);
        row.put("price", price);
        row.put("rate", perKgRate);
        row.put("amount", price);
        int bag = rs.getInt("BAG_COUNT");
        row.put("bagCount", rs.wasNull() ? null : bag);
        row.put("bag", rs.wasNull() ? null : bag);
        return row;
    }

    private boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
