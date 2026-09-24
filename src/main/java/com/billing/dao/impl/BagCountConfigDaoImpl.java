package com.billing.dao.impl;

import com.billing.dao.BagCountConfigDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

@Component
public class BagCountConfigDaoImpl implements BagCountConfigDao {

    private static final Logger logger = LoggerFactory.getLogger(BagCountConfigDaoImpl.class);

    private final DataSource dataSource;

    public BagCountConfigDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String FIND_ALL_FLOWERS =
            "SELECT FLOWER_ID, FLOWER_NAME FROM BLOOMBUDDY_FLOWER_MASTER " +
            "WHERE CLIENT_ID = ? ORDER BY FLOWER_NAME ASC";

    private static final String FIND_ALL_FARMERS =
            "SELECT FARMER_ID, FARMER_NAME FROM BLOOMBUDDY_FARMER_MASTER " +
            "WHERE CLIENT_ID = ? ORDER BY FARMER_NAME ASC";

    private static final String FIND_ALL =
            "SELECT CONFIG_ID, FARMER_ID, FARMER_NAME, FLOWER_ID, FLOWER_NAME, SALES_DATE, BAG_COUNT " +
            "FROM BLOOMBUDDY_BAG_COUNT_CONFIG WHERE CLIENT_ID = ? " +
            "ORDER BY SALES_DATE DESC, FARMER_NAME ASC, FLOWER_NAME ASC";

    private static final String FIND_BY_FARMER_FLOWER_DATE =
            "SELECT CONFIG_ID, FARMER_ID, FARMER_NAME, FLOWER_ID, FLOWER_NAME, SALES_DATE, BAG_COUNT " +
            "FROM BLOOMBUDDY_BAG_COUNT_CONFIG " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND FLOWER_ID = ? AND SALES_DATE = ? LIMIT 1";

    private static final String FIND_FOR_REPORT =
            "SELECT CONFIG_ID, FARMER_ID, FARMER_NAME, FLOWER_ID, FLOWER_NAME, SALES_DATE, BAG_COUNT " +
            "FROM BLOOMBUDDY_BAG_COUNT_CONFIG " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE BETWEEN ? AND ? " +
            "ORDER BY SALES_DATE ASC, FLOWER_NAME ASC";

    private static final String UPSERT =
            "INSERT INTO BLOOMBUDDY_BAG_COUNT_CONFIG " +
            "(CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, FLOWER_ID, FLOWER_NAME, SALES_DATE, BAG_COUNT) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE FARMER_NAME = VALUES(FARMER_NAME), FLOWER_NAME = VALUES(FLOWER_NAME), " +
            "BAG_COUNT = BAG_COUNT + VALUES(BAG_COUNT)";

    private static final String DELETE =
            "DELETE FROM BLOOMBUDDY_BAG_COUNT_CONFIG " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND FLOWER_ID = ? AND SALES_DATE = ?";

    private static final String SUM_BAG_COUNT =
            "SELECT COALESCE(SUM(BAG_COUNT), 0) FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND FLOWER_ID = ? AND SALES_DATE = ?";

    @Override
    public List<Map<String, Object>> findAllFlowers(Long clientId) {
        logger.info("findAllFlowers: clientId={}", clientId);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL_FLOWERS)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("flowerId", rs.getString("FLOWER_ID"));
                    row.put("flowerName", rs.getString("FLOWER_NAME"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findAllFlowers: SQL exception", e);
            throw new RuntimeException("Failed to fetch flowers", e);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> findAllFarmers(Long clientId) {
        logger.info("findAllFarmers: clientId={}", clientId);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL_FARMERS)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findAllFarmers: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmers", e);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> findAll(Long clientId) {
        logger.info("findAll: clientId={}", clientId);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findAll: SQL exception", e);
            throw new RuntimeException("Failed to fetch bag count config", e);
        }
        return results;
    }

    @Override
    public Map<String, Object> findByFarmerFlowerAndDate(Long clientId, String farmerId,
                                                         String flowerId, LocalDate salesDate) {
        logger.info("findByFarmerFlowerAndDate: clientId={}, farmerId={}, flowerId={}, date={}",
                clientId, farmerId, flowerId, salesDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_FARMER_FLOWER_DATE)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setString(3, flowerId);
            ps.setDate(4, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("findByFarmerFlowerAndDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch bag count config", e);
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> findForReport(Long clientId, String farmerId,
                                                   LocalDate fromDate, LocalDate toDate) {
        logger.info("findForReport: clientId={}, farmerId={}, from={}, to={}",
                clientId, farmerId, fromDate, toDate);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_FOR_REPORT)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(fromDate));
            ps.setDate(4, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findForReport: SQL exception", e);
            throw new RuntimeException("Failed to fetch bag count config report", e);
        }
        return results;
    }

    @Override
    public void upsert(Long clientId, String clientUsername, String farmerId, String farmerName,
                       String flowerId, String flowerName, LocalDate salesDate, Integer bagCount) {
        logger.info("upsert: clientId={}, farmerId={}, flowerId={}, date={}, bagCount={}",
                clientId, farmerId, flowerId, salesDate, bagCount);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, farmerId);
            ps.setString(4, farmerName);
            ps.setString(5, flowerId);
            ps.setString(6, flowerName);
            ps.setDate(7, java.sql.Date.valueOf(salesDate));
            ps.setInt(8, bagCount == null ? 0 : bagCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("upsert: SQL exception", e);
            throw new RuntimeException("Failed to save bag count config", e);
        }
    }

    @Override
    public void delete(Long clientId, String farmerId, String flowerId, LocalDate salesDate) {
        logger.info("delete: clientId={}, farmerId={}, flowerId={}, date={}",
                clientId, farmerId, flowerId, salesDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setString(3, flowerId);
            ps.setDate(4, java.sql.Date.valueOf(salesDate));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("delete: SQL exception", e);
            throw new RuntimeException("Failed to delete bag count config", e);
        }
    }

    @Override
    public Integer sumBagCountForFarmerFlowerAndDate(Long clientId, String farmerId,
                                                     String flowerId, LocalDate salesDate) {
        logger.info("sumBagCountForFarmerFlowerAndDate: clientId={}, farmerId={}, flowerId={}, date={}",
                clientId, farmerId, flowerId, salesDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SUM_BAG_COUNT)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setString(3, flowerId);
            ps.setDate(4, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("sumBagCountForFarmerFlowerAndDate: SQL exception", e);
            throw new RuntimeException("Failed to sum bag count", e);
        }
        return 0;
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("configId", rs.getLong("CONFIG_ID"));
        row.put("farmerId", rs.getString("FARMER_ID"));
        row.put("farmerName", rs.getString("FARMER_NAME"));
        row.put("flowerId", rs.getString("FLOWER_ID"));
        row.put("flowerName", rs.getString("FLOWER_NAME"));
        java.sql.Date date = rs.getDate("SALES_DATE");
        row.put("salesDate", date == null ? null : date.toLocalDate());
        row.put("bagCount", rs.getInt("BAG_COUNT"));
        return row;
    }
}