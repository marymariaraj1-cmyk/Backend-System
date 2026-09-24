package com.billing.dao.impl;

import com.billing.dao.FlowerLootSaleEditDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
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
public class FlowerLootSaleEditDaoImpl implements FlowerLootSaleEditDao {

    private static final Logger logger = LoggerFactory.getLogger(FlowerLootSaleEditDaoImpl.class);

    private static final String FETCH_FLOWER_LOOT_SALE_ROWS_SQL =
            "SELECT SALES_ID, FARMER_ID, FARMER_NAME, BUYER_ID, CUST_NAME, TOTAL_WEIGHT, PERKG_RATE, PRICE, BAG_COUNT "
                    + "FROM BLOOMBUDDY_SALES "
                    + "WHERE CLIENT_ID = ? AND FLOWER_TYPE = ? AND SALES_DATE = ? "
                    + "AND ((FARMER_ID IS NULL AND FARMER_NAME IS NULL) "
                    + "     OR (BUYER_ID IS NULL AND CUST_NAME IS NULL)) "
                    + "ORDER BY SALES_ID";

    private static final String FIND_FARMER_DAY_ROWS_SQL =
            "SELECT SALES_ID, FARMER_NAME, PRICE "
                    + "FROM BLOOMBUDDY_SALES "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? "
                    + "AND BUYER_ID IS NULL AND CUST_NAME IS NULL "
                    + "ORDER BY SALES_ID";

    private static final String UPDATE_SALES_ROW_FARMER_NAME_SQL =
            "UPDATE BLOOMBUDDY_SALES SET FARMER_NAME = ?, FARMER_ID = ? "
                    + "WHERE SALES_ID = ? AND CLIENT_ID = ?";

    private final DataSource dataSource;

    @Autowired
    public FlowerLootSaleEditDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> fetchFlowerLootSaleRows(Long clientId, String flowerType, LocalDate salesDate) {
        logger.info("fetchFlowerLootSaleRows: clientId={}, flowerType={}, date={}", clientId, flowerType, salesDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FETCH_FLOWER_LOOT_SALE_ROWS_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, flowerType);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesId", rs.getLong("SALES_ID"));
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    row.put("customerName", rs.getString("CUST_NAME"));
                    row.put("totalWeight", rs.getBigDecimal("TOTAL_WEIGHT"));
                    row.put("perKgRate", rs.getBigDecimal("PERKG_RATE"));
                    row.put("price", rs.getBigDecimal("PRICE"));
                    int bag = rs.getInt("BAG_COUNT");
                    row.put("bagCount", rs.wasNull() ? null : bag);
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("fetchFlowerLootSaleRows: SQL exception while fetching flower loot sale rows", e);
            throw new RuntimeException("Failed to fetch flower loot sale rows", e);
        }
        logger.info("fetchFlowerLootSaleRows: rows={}", rows.size());
        return rows;
    }

    @Override
    public List<Map<String, Object>> findFarmerDayRows(Long clientId, String farmerId, LocalDate salesDate, Connection conn) {
        logger.info("findFarmerDayRows: clientId={}, farmerId={}, date={}", clientId, farmerId, salesDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_FARMER_DAY_ROWS_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesId", rs.getLong("SALES_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    row.put("price", rs.getBigDecimal("PRICE"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findFarmerDayRows: SQL exception while fetching farmer day rows", e);
            throw new RuntimeException("Failed to fetch farmer day rows", e);
        }
        return rows;
    }

    @Override
    public void updateSalesRowFarmerName(Long salesId, Long clientId, String farmerName, String farmerId, Connection conn) {
        logger.info("updateSalesRowFarmerName: salesId={}, clientId={}, farmerName={}, farmerId={}",
                salesId, clientId, farmerName, farmerId);
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SALES_ROW_FARMER_NAME_SQL)) {
            ps.setString(1, farmerName);
            ps.setString(2, farmerId);
            ps.setLong(3, salesId);
            ps.setLong(4, clientId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("No record found with SALES_ID=" + salesId);
            }
        } catch (SQLException e) {
            logger.error("updateSalesRowFarmerName: SQL exception while updating farmer name for salesId={}", salesId, e);
            throw new RuntimeException("Failed to update sales row farmer name", e);
        }
    }
}