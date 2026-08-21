package com.billing.dao.impl;

import com.billing.dao.FarmerSalesReportDao;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class FarmerSalesReportDaoImpl implements FarmerSalesReportDao {

    private static final Logger logger = LoggerFactory.getLogger(FarmerSalesReportDaoImpl.class);

    private static final DateTimeFormatter SALES_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final String SELECT_BY_FARMER_DATE_SQL =
            "SELECT FARMER_ID, FARMER_NAME, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, CUST_NAME " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND CLIENT_USERNAME = ? AND FARMER_ID = ? AND SALES_DATE = ? " +
            "ORDER BY SALES_DATE ASC";

    private static final String SELECT_RANGE_SALES_SQL =
            "SELECT FARMER_ID, FARMER_NAME, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, CUST_NAME " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND CLIENT_USERNAME = ? AND SALES_DATE BETWEEN ? AND ? " +
            "ORDER BY SALES_DATE ASC";

    private static final String SELECT_TODAY_SALES_SQL =
            "SELECT FARMER_ID, FARMER_NAME, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, CUST_NAME " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND CLIENT_USERNAME = ? AND SALES_DATE = ? " +
            "ORDER BY SALES_DATE ASC";

    private final DataSource dataSource;

    public FarmerSalesReportDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> findSalesByFarmerAndDate(Long clientId, String clientUsername, String farmerId, LocalDate date) {
        logger.info("findSalesByFarmerAndDate: clientId={}, farmerId={}, date={}", clientId, farmerId, date);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_FARMER_DATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, farmerId);
            ps.setDate(4, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapSalesRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findSalesByFarmerAndDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer sales for date", e);
        }
        logger.info("findSalesByFarmerAndDate: rows={}", rows.size());
        return rows;
    }

    @Override
    public List<Map<String, Object>> findSalesByDateRange(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate) {
        logger.info("findSalesByDateRange: clientId={}, fromDate={}, toDate={}", clientId, fromDate, toDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_RANGE_SALES_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setDate(3, java.sql.Date.valueOf(fromDate));
            ps.setDate(4, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapSalesRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findSalesByDateRange: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer sales report", e);
        }
        logger.info("findSalesByDateRange: rows={}", rows.size());
        return rows;
    }

    @Override
    public List<Map<String, Object>> findTodaySales(Long clientId, String clientUsername) {
        logger.info("findTodaySales: clientId={}", clientId);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_TODAY_SALES_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapSalesRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findTodaySales: SQL exception", e);
            throw new RuntimeException("Failed to fetch today's farmer sales", e);
        }
        logger.info("findTodaySales: rows={}", rows.size());
        return rows;
    }

    private Map<String, Object> mapSalesRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("farmerId", rs.getString("FARMER_ID"));
        row.put("farmerName", rs.getString("FARMER_NAME"));
        java.sql.Date salesDate = rs.getDate("SALES_DATE");
        row.put("salesDate", salesDate == null ? "" : SALES_DATE_FMT.format(salesDate.toLocalDate()));
        row.put("flowerType", rs.getString("FLOWER_TYPE"));
        row.put("totalWeight", rs.getBigDecimal("TOTAL_WEIGHT"));
        row.put("perKgRate", RoundOffUtil.round(rs.getBigDecimal("PERKG_RATE")));
        row.put("price", rs.getBigDecimal("PRICE"));
        row.put("custName", rs.getString("CUST_NAME"));
        return row;
    }

    @Override
    public List<Map<String, Object>> findSalesByIds(Long clientId, List<Long> salesIds) {
        logger.info("findSalesByIds: clientId={}, ids={}", clientId, salesIds.size());
        if (salesIds == null || salesIds.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = salesIds.stream().map(id -> "?").reduce((a, b) -> a + "," + b).orElse("");
        String sql = "SELECT FARMER_ID, FARMER_NAME, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, CUST_NAME "
                + "FROM BLOOMBUDDY_SALES WHERE CLIENT_ID = ? AND SALES_ID IN (" + placeholders + ") ORDER BY SALES_ID ASC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            for (int i = 0; i < salesIds.size(); i++) {
                ps.setLong(i + 2, salesIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapSalesRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findSalesByIds: SQL exception", e);
            throw new RuntimeException("Failed to fetch sales by IDs", e);
        }
        return rows;
    }
}
