package com.billing.dao.impl;

import com.billing.dao.BuyerSalesReportDao;
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
public class BuyerSalesReportDaoImpl implements BuyerSalesReportDao {

    private static final Logger logger = LoggerFactory.getLogger(BuyerSalesReportDaoImpl.class);

    private static final DateTimeFormatter SALES_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final String SELECT_BY_BUYER_DATE_SQL =
            "SELECT SALES_ID, BUYER_ID, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, CUST_NAME " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND CLIENT_USERNAME = ? AND BUYER_ID = ? AND SALES_DATE = ? " +
            "ORDER BY SALES_ID ASC";

    private static final String SELECT_BUYER_SUMMARY_SQL =
            "SELECT BUYER_ID, CUST_NAME, SUM(TOTAL_WEIGHT) AS TOTAL_WEIGHT, SUM(PRICE) AS TOTAL_AMOUNT " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND CLIENT_USERNAME = ? AND SALES_DATE BETWEEN ? AND ? " +
            "GROUP BY BUYER_ID, CUST_NAME " +
            "ORDER BY CUST_NAME ASC";

    private static final String SELECT_DISCOUNT_SQL =
            "SELECT BUYER_ID, SUM(DIS_AMT) AS DISCOUNT " +
            "FROM BLOOMBUDDY_BUYER_LEDGER " +
            "WHERE CLIENT_ID = ? AND SALES_DATE BETWEEN ? AND ? " +
            "GROUP BY BUYER_ID";

    private static final String SELECT_DETAIL_SQL =
            "SELECT SALES_ID, BUYER_ID, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, CUST_NAME " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND CLIENT_USERNAME = ? AND SALES_DATE BETWEEN ? AND ? " +
            "ORDER BY SALES_DATE ASC";

    private final DataSource dataSource;

    public BuyerSalesReportDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> findSalesByBuyerAndDate(Long clientId, String clientUsername, String buyerId, LocalDate date) {
        logger.info("findSalesByBuyerAndDate: clientId={}, buyerId={}, date={}", clientId, buyerId, date);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_BUYER_DATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, buyerId);
            ps.setDate(4, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapSalesRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findSalesByBuyerAndDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer sales for date", e);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> findBuyerSummaryByDateRange(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate) {
        logger.info("findBuyerSummaryByDateRange: clientId={}, fromDate={}, toDate={}", clientId, fromDate, toDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BUYER_SUMMARY_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setDate(3, java.sql.Date.valueOf(fromDate));
            ps.setDate(4, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    row.put("buyerName", rs.getString("CUST_NAME"));
                    row.put("totalWeight", rs.getBigDecimal("TOTAL_WEIGHT"));
                    row.put("totalAmount", rs.getBigDecimal("TOTAL_AMOUNT"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findBuyerSummaryByDateRange: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer sales report", e);
        }
        logger.info("findBuyerSummaryByDateRange: buyers={}", rows.size());
        return rows;
    }

    @Override
    public List<Map<String, Object>> findDiscountByDateRange(Long clientId, LocalDate fromDate, LocalDate toDate) {
        logger.info("findDiscountByDateRange: clientId={}, fromDate={}, toDate={}", clientId, fromDate, toDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_DISCOUNT_SQL)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(fromDate));
            ps.setDate(3, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    row.put("discount", rs.getBigDecimal("DISCOUNT"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findDiscountByDateRange: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer discount", e);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> findSalesDetailByDateRange(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate) {
        logger.info("findSalesDetailByDateRange: clientId={}, fromDate={}, toDate={}", clientId, fromDate, toDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_DETAIL_SQL)) {
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
            logger.error("findSalesDetailByDateRange: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer sales detail", e);
        }
        return rows;
    }

    private Map<String, Object> mapSalesRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("salesId", rs.getLong("SALES_ID"));
        row.put("buyerId", rs.getString("BUYER_ID"));
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
        String sql = "SELECT SALES_ID, BUYER_ID, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, CUST_NAME "
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
