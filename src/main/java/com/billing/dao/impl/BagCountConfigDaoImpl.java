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

    private static final String FIND_ALL =
            "SELECT CONFIG_ID, FLOWER_ID, FLOWER_NAME, SALES_DATE, BAG_COUNT, BAG_CHECK " +
            "FROM BLOOMBUDDY_BAG_COUNT_CONFIG WHERE CLIENT_ID = ? ORDER BY SALES_DATE DESC, FLOWER_NAME ASC";

    private static final String FIND_BY_FLOWER_DATE =
            "SELECT CONFIG_ID, FLOWER_ID, FLOWER_NAME, SALES_DATE, BAG_COUNT, BAG_CHECK " +
            "FROM BLOOMBUDDY_BAG_COUNT_CONFIG WHERE CLIENT_ID = ? AND FLOWER_ID = ? AND SALES_DATE = ? LIMIT 1";

    private static final String UPSERT =
            "INSERT INTO BLOOMBUDDY_BAG_COUNT_CONFIG (CLIENT_ID, CLIENT_USERNAME, FLOWER_ID, FLOWER_NAME, SALES_DATE, BAG_COUNT, BAG_CHECK) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE FLOWER_NAME = VALUES(FLOWER_NAME), BAG_COUNT = VALUES(BAG_COUNT), BAG_CHECK = VALUES(BAG_CHECK)";

    private static final String DELETE =
            "DELETE FROM BLOOMBUDDY_BAG_COUNT_CONFIG WHERE CLIENT_ID = ? AND FLOWER_ID = ? AND SALES_DATE = ?";

    private static final String SUM_BAG_COUNT =
            "SELECT COALESCE(SUM(BAG_COUNT), 0) FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND FLOWER_ID = ? AND SALES_DATE = ?";

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
    public Map<String, Object> findByFlowerAndDate(Long clientId, String flowerId, LocalDate salesDate) {
        logger.info("findByFlowerAndDate: clientId={}, flowerId={}, date={}", clientId, flowerId, salesDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_FLOWER_DATE)) {
            ps.setLong(1, clientId);
            ps.setString(2, flowerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("findByFlowerAndDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch bag count config", e);
        }
        return null;
    }

    @Override
    public Map<String, Object> findConfig(Long clientId, String flowerId, LocalDate salesDate) {
        return findByFlowerAndDate(clientId, flowerId, salesDate);
    }

    @Override
    public void upsert(Long clientId, String clientUsername, String flowerId, String flowerName,
                       LocalDate salesDate, Integer bagCount, String bagCheck) {
        logger.info("upsert: clientId={}, flowerId={}, date={}, bagCount={}, bagCheck={}", clientId, flowerId, salesDate, bagCount, bagCheck);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, flowerId);
            ps.setString(4, flowerName);
            ps.setDate(5, java.sql.Date.valueOf(salesDate));
            ps.setInt(6, bagCount == null ? 0 : bagCount);
            ps.setString(7, bagCheck);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("upsert: SQL exception", e);
            throw new RuntimeException("Failed to save bag count config", e);
        }
    }

    @Override
    public void delete(Long clientId, String flowerId, LocalDate salesDate) {
        logger.info("delete: clientId={}, flowerId={}, date={}", clientId, flowerId, salesDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE)) {
            ps.setLong(1, clientId);
            ps.setString(2, flowerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("delete: SQL exception", e);
            throw new RuntimeException("Failed to delete bag count config", e);
        }
    }

    @Override
    public Integer sumBagCountForFlowerAndDate(Long clientId, String flowerId, LocalDate salesDate) {
        logger.info("sumBagCountForFlowerAndDate: clientId={}, flowerId={}, date={}", clientId, flowerId, salesDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SUM_BAG_COUNT)) {
            ps.setLong(1, clientId);
            ps.setString(2, flowerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("sumBagCountForFlowerAndDate: SQL exception", e);
            throw new RuntimeException("Failed to sum bag count", e);
        }
        return 0;
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("configId", rs.getLong("CONFIG_ID"));
        row.put("flowerId", rs.getString("FLOWER_ID"));
        row.put("flowerName", rs.getString("FLOWER_NAME"));
        java.sql.Date date = rs.getDate("SALES_DATE");
        row.put("salesDate", date == null ? null : date.toLocalDate());
        row.put("bagCount", rs.getInt("BAG_COUNT"));
        row.put("bagCheck", rs.getString("BAG_CHECK"));
        return row;
    }
}
