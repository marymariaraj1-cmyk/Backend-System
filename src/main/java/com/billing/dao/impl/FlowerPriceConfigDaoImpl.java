package com.billing.dao.impl;

import com.billing.dao.FlowerPriceConfigDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

@Component
public class FlowerPriceConfigDaoImpl implements FlowerPriceConfigDao {

    private static final Logger logger = LoggerFactory.getLogger(FlowerPriceConfigDaoImpl.class);

    private static final String FIND_ALL_FLOWERS =
            "SELECT FLOWER_ID, FLOWER_NAME FROM BLOOMBUDDY_FLOWER_MASTER " +
            "WHERE CLIENT_ID = ? ORDER BY FLOWER_NAME ASC";

    private static final String FIND_BY_CLIENT_DATE =
            "SELECT FLOWER_PRICE_CONFIG_ID, FLOWER_ID, FLOWER_NAME, PRICE_DATE, PRICE " +
            "FROM BLOOMBUDDY_FLOWER_PRICE_CONFIG WHERE CLIENT_ID = ? AND PRICE_DATE = ? " +
            "ORDER BY FLOWER_NAME ASC";

    private static final String FIND_ID_BY_CLIENT_FLOWER_DATE =
            "SELECT FLOWER_PRICE_CONFIG_ID FROM BLOOMBUDDY_FLOWER_PRICE_CONFIG " +
            "WHERE CLIENT_ID = ? AND FLOWER_ID = ? AND PRICE_DATE = ? LIMIT 1";

    private static final String INSERT =
            "INSERT INTO BLOOMBUDDY_FLOWER_PRICE_CONFIG " +
            "(CLIENT_ID, CLIENT_USERNAME, FLOWER_ID, FLOWER_NAME, PRICE_DATE, PRICE, CREATED_BY) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_BY_CLIENT_FLOWER_DATE =
            "UPDATE BLOOMBUDDY_FLOWER_PRICE_CONFIG " +
            "SET FLOWER_NAME = ?, PRICE = ?, UPDATED_BY = ?, UPDATED_DATE = NOW() " +
            "WHERE CLIENT_ID = ? AND FLOWER_ID = ? AND PRICE_DATE = ?";

    private static final String UPDATE_PRICE_BY_ID =
            "UPDATE BLOOMBUDDY_FLOWER_PRICE_CONFIG " +
            "SET PRICE = ?, UPDATED_BY = ?, UPDATED_DATE = NOW() " +
            "WHERE CLIENT_ID = ? AND FLOWER_PRICE_CONFIG_ID = ?";

    private static final String DELETE_BY_ID =
            "DELETE FROM BLOOMBUDDY_FLOWER_PRICE_CONFIG WHERE CLIENT_ID = ? AND FLOWER_PRICE_CONFIG_ID = ?";

    private static final String FIND_TICKER_RAW =
            "SELECT FLOWER_ID, FLOWER_NAME, PRICE_DATE, PRICE FROM BLOOMBUDDY_FLOWER_PRICE_CONFIG " +
            "WHERE CLIENT_ID = ? AND PRICE_DATE <= ? ORDER BY FLOWER_ID ASC, PRICE_DATE DESC";

    private static final String FIND_HISTORY =
            "SELECT FLOWER_PRICE_CONFIG_ID, FLOWER_ID, FLOWER_NAME, PRICE_DATE, PRICE " +
            "FROM BLOOMBUDDY_FLOWER_PRICE_CONFIG WHERE CLIENT_ID = ? AND FLOWER_ID = ? " +
            "AND PRICE_DATE BETWEEN ? AND ? ORDER BY PRICE_DATE ASC";

    private final DataSource dataSource;

    public FlowerPriceConfigDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

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
    public List<Map<String, Object>> findByClientAndDate(Long clientId, LocalDate priceDate) {
        logger.info("findByClientAndDate: clientId={}, priceDate={}", clientId, priceDate);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_CLIENT_DATE)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(priceDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findByClientAndDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch flower prices", e);
        }
        return results;
    }

    @Override
    public Long findIdByClientFlowerDate(Long clientId, String flowerId, LocalDate priceDate) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ID_BY_CLIENT_FLOWER_DATE)) {
            ps.setLong(1, clientId);
            ps.setString(2, flowerId);
            ps.setDate(3, java.sql.Date.valueOf(priceDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("FLOWER_PRICE_CONFIG_ID");
                }
            }
        } catch (SQLException e) {
            logger.error("findIdByClientFlowerDate: SQL exception", e);
            throw new RuntimeException("Failed to check flower price record", e);
        }
        return null;
    }

    @Override
    public void insert(Long clientId, String clientUsername, String flowerId, String flowerName,
                       LocalDate priceDate, BigDecimal price, String createdBy) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, flowerId);
            ps.setString(4, flowerName);
            ps.setDate(5, java.sql.Date.valueOf(priceDate));
            ps.setBigDecimal(6, price);
            ps.setString(7, createdBy);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("insert: SQL exception", e);
            throw new RuntimeException("Failed to save flower price", e);
        }
    }

    @Override
    public void updateByClientFlowerDate(Long clientId, String flowerName, LocalDate priceDate,
                                         BigDecimal price, String updatedBy, String flowerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_BY_CLIENT_FLOWER_DATE)) {
            ps.setString(1, flowerName);
            ps.setBigDecimal(2, price);
            ps.setString(3, updatedBy);
            ps.setLong(4, clientId);
            ps.setString(5, flowerId);
            ps.setDate(6, java.sql.Date.valueOf(priceDate));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("updateByClientFlowerDate: SQL exception", e);
            throw new RuntimeException("Failed to update flower price", e);
        }
    }

    @Override
    public void updatePrice(Long clientId, Long priceConfigId, BigDecimal price, String updatedBy) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_PRICE_BY_ID)) {
            ps.setBigDecimal(1, price);
            ps.setString(2, updatedBy);
            ps.setLong(3, clientId);
            ps.setLong(4, priceConfigId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("Flower price record not found");
            }
        } catch (SQLException e) {
            logger.error("updatePrice: SQL exception", e);
            throw new RuntimeException("Failed to update flower price", e);
        }
    }

    @Override
    public void delete(Long clientId, Long priceConfigId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_ID)) {
            ps.setLong(1, clientId);
            ps.setLong(2, priceConfigId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("Flower price record not found");
            }
        } catch (SQLException e) {
            logger.error("delete: SQL exception", e);
            throw new RuntimeException("Failed to delete flower price", e);
        }
    }

    @Override
    public List<Map<String, Object>> findTickerData(Long clientId, LocalDate today) {
        logger.info("findTickerData: clientId={}, today={}", clientId, today);
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_TICKER_RAW)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(today));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String flowerId = rs.getString("FLOWER_ID");
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("flowerId", flowerId);
                    entry.put("flowerName", rs.getString("FLOWER_NAME"));
                    java.sql.Date d = rs.getDate("PRICE_DATE");
                    LocalDate priceDate = d == null ? null : d.toLocalDate();
                    entry.put("priceDate", priceDate);
                    entry.put("price", rs.getBigDecimal("PRICE"));
                    grouped.computeIfAbsent(flowerId, k -> new ArrayList<>()).add(entry);
                }
            }
        } catch (SQLException e) {
            logger.error("findTickerData: SQL exception", e);
            throw new RuntimeException("Failed to fetch ticker data", e);
        }
        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            List<Map<String, Object>> list = e.getValue();
            if (list.isEmpty()) continue;
            Map<String, Object> latest = list.get(0);
            Map<String, Object> previous = list.size() > 1 ? list.get(1) : null;
            BigDecimal latestPrice = (BigDecimal) latest.get("price");
            BigDecimal prevPrice = previous == null ? null : (BigDecimal) previous.get("price");
            String trend = "flat";
            if (prevPrice != null && latestPrice != null) {
                int cmp = latestPrice.compareTo(prevPrice);
                if (cmp > 0) trend = "up";
                else if (cmp < 0) trend = "down";
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("flowerId", latest.get("flowerId"));
            out.put("flowerName", latest.get("flowerName"));
            out.put("price", latestPrice);
            out.put("priceDate", latest.get("priceDate"));
            out.put("previousPrice", prevPrice);
            out.put("previousPriceDate", previous == null ? null : previous.get("priceDate"));
            out.put("trend", trend);
            results.add(out);
        }
        results.sort((a, b) -> String.valueOf(a.get("flowerName")).compareToIgnoreCase(String.valueOf(b.get("flowerName"))));
        return results;
    }

    @Override
    public List<Map<String, Object>> findPriceHistory(Long clientId, String flowerId, LocalDate fromDate, LocalDate toDate) {
        logger.info("findPriceHistory: clientId={}, flowerId={}, from={}, to={}", clientId, flowerId, fromDate, toDate);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_HISTORY)) {
            ps.setLong(1, clientId);
            ps.setString(2, flowerId);
            ps.setDate(3, java.sql.Date.valueOf(fromDate));
            ps.setDate(4, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findPriceHistory: SQL exception", e);
            throw new RuntimeException("Failed to fetch price history", e);
        }
        return results;
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("priceConfigId", rs.getLong("FLOWER_PRICE_CONFIG_ID"));
        row.put("flowerId", rs.getString("FLOWER_ID"));
        row.put("flowerName", rs.getString("FLOWER_NAME"));
        java.sql.Date date = rs.getDate("PRICE_DATE");
        row.put("priceDate", date == null ? null : date.toLocalDate());
        row.put("price", rs.getBigDecimal("PRICE"));
        return row;
    }
}