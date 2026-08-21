package com.billing.dao.impl;

import com.billing.dao.SalesEditDao;
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
public class SalesEditDaoImpl implements SalesEditDao {

    private static final Logger logger = LoggerFactory.getLogger(SalesEditDaoImpl.class);

    private static final String FETCH_SALES_SQL =
            "SELECT SALES_ID, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, CUST_NAME, BUYER_ID "
                    + "FROM BLOOMBUDDY_SALES "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? "
                    + "ORDER BY SALES_ID";

    private static final String FETCH_SUMMARY_SQL =
            "SELECT TOTAL_SALES_AMT, COMMISSION_AMT, TOTAL_NET_AMT, DEBIT_AMT, FINAL_AMT "
                    + "FROM BLOOMBUDDY_SALES_TOTALSUMMARY "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? LIMIT 1";

    private static final String FIND_SALES_ROW_SQL =
            "SELECT SALES_ID, FARMER_ID, FARMER_NAME, SALES_DATE, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE, BUYER_ID, CUST_NAME "
                    + "FROM BLOOMBUDDY_SALES "
                    + "WHERE SALES_ID = ? AND CLIENT_ID = ? LIMIT 1";

    private static final String UPDATE_SALES_ROW_SQL =
            "UPDATE BLOOMBUDDY_SALES SET FLOWER_TYPE = ?, TOTAL_WEIGHT = ?, PERKG_RATE = ?, PRICE = ? "
                    + "WHERE SALES_ID = ? AND CLIENT_ID = ?";

    private static final String ADJUST_SUMMARY_SALES_AMOUNTS_SQL =
            "UPDATE BLOOMBUDDY_SALES_TOTALSUMMARY SET "
                    + "TOTAL_SALES_AMT = TOTAL_SALES_AMT + ?, "
                    + "COMMISSION_AMT = COMMISSION_AMT + ?, "
                    + "TOTAL_NET_AMT = TOTAL_NET_AMT + ?, "
                    + "FINAL_AMT = FINAL_AMT + ? "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ?";

    private static final String RECONCILE_SUMMARY_SQL =
            "INSERT INTO BLOOMBUDDY_SALES_TOTALSUMMARY "
                    + "(CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, TOTAL_SALES_AMT, COMMISSION_AMT, TOTAL_NET_AMT, DEBIT_AMT, FINAL_AMT) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "FARMER_NAME = VALUES(FARMER_NAME), "
                    + "TOTAL_SALES_AMT = VALUES(TOTAL_SALES_AMT), "
                    + "COMMISSION_AMT = VALUES(COMMISSION_AMT), "
                    + "TOTAL_NET_AMT = VALUES(TOTAL_NET_AMT), "
                    + "DEBIT_AMT = VALUES(DEBIT_AMT), "
                    + "FINAL_AMT = VALUES(FINAL_AMT)";

    private final DataSource dataSource;

    @Autowired
    public SalesEditDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> fetchSales(Long clientId, String farmerId, LocalDate salesDate) {
        logger.info("fetchSales: clientId={}, farmerId={}, date={}", clientId, farmerId, salesDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FETCH_SALES_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesId", rs.getLong("SALES_ID"));
                    row.put("flowerType", rs.getString("FLOWER_TYPE"));
                    row.put("totalWeight", rs.getBigDecimal("TOTAL_WEIGHT"));
                    row.put("perKgRate", rs.getBigDecimal("PERKG_RATE"));
                    row.put("price", rs.getBigDecimal("PRICE"));
                    row.put("customerName", rs.getString("CUST_NAME"));
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("fetchSales: SQL exception while fetching sales rows", e);
            throw new RuntimeException("Failed to fetch sales rows", e);
        }
        logger.info("fetchSales: rows={}", rows.size());
        return rows;
    }

    @Override
    public Map<String, Object> fetchSummary(Long clientId, String farmerId, LocalDate salesDate) {
        logger.info("fetchSummary: clientId={}, farmerId={}, date={}", clientId, farmerId, salesDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FETCH_SUMMARY_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("totalSalesAmt", rs.getBigDecimal("TOTAL_SALES_AMT"));
                    row.put("commissionAmt", rs.getBigDecimal("COMMISSION_AMT"));
                    row.put("totalNetAmt", rs.getBigDecimal("TOTAL_NET_AMT"));
                    row.put("debitAmt", rs.getBigDecimal("DEBIT_AMT"));
                    row.put("finalAmt", rs.getBigDecimal("FINAL_AMT"));
                    return row;
                }
            }
        } catch (SQLException e) {
            logger.error("fetchSummary: SQL exception while fetching sales total summary", e);
            throw new RuntimeException("Failed to fetch sales total summary", e);
        }
        return null;
    }

    @Override
    public Map<String, Object> findSalesRow(Long clientId, Long salesId) {
        logger.info("findSalesRow: clientId={}, salesId={}", clientId, salesId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_SALES_ROW_SQL)) {
            ps.setLong(1, salesId);
            ps.setLong(2, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesId", rs.getLong("SALES_ID"));
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    row.put("salesDate", rs.getDate("SALES_DATE").toLocalDate());
                    row.put("flowerType", rs.getString("FLOWER_TYPE"));
                    row.put("totalWeight", rs.getBigDecimal("TOTAL_WEIGHT"));
                    row.put("perKgRate", rs.getBigDecimal("PERKG_RATE"));
                    row.put("price", rs.getBigDecimal("PRICE"));
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    row.put("customerName", rs.getString("CUST_NAME"));
                    return row;
                }
            }
        } catch (SQLException e) {
            logger.error("findSalesRow: SQL exception while fetching sales row", e);
            throw new RuntimeException("Failed to fetch sales row", e);
        }
        return null;
    }

    @Override
    public void updateSalesRow(Long salesId, Long clientId, String flowerType,
                               BigDecimal totalWeight, BigDecimal perKgRate, BigDecimal price,
                               Connection conn) {
        logger.info("updateSalesRow: salesId={}, clientId={}, flowerType={}, weight={}, rate={}, amount={}",
                salesId, clientId, flowerType, totalWeight, perKgRate, price);
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SALES_ROW_SQL)) {
            ps.setString(1, flowerType);
            ps.setBigDecimal(2, totalWeight);
            ps.setBigDecimal(3, perKgRate);
            ps.setBigDecimal(4, price);
            ps.setLong(5, salesId);
            ps.setLong(6, clientId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("No record found with SALES_ID=" + salesId);
            }
        } catch (SQLException e) {
            logger.error("updateSalesRow: SQL exception while updating sales row id={}", salesId, e);
            throw new RuntimeException("Failed to update sales row", e);
        }
    }

    @Override
    public void adjustSummarySalesAmounts(Long clientId, String farmerId, LocalDate salesDate,
                                          BigDecimal totalDelta, BigDecimal commissionDelta,
                                          BigDecimal netDelta, BigDecimal finalDelta,
                                          Connection conn) {
        logger.info("adjustSummarySalesAmounts: clientId={}, farmerId={}, date={}, totalDelta={}, commissionDelta={}, netDelta={}, finalDelta={}",
                clientId, farmerId, salesDate, totalDelta, commissionDelta, netDelta, finalDelta);
        try (PreparedStatement ps = conn.prepareStatement(ADJUST_SUMMARY_SALES_AMOUNTS_SQL)) {
            ps.setBigDecimal(1, totalDelta);
            ps.setBigDecimal(2, commissionDelta);
            ps.setBigDecimal(3, netDelta);
            ps.setBigDecimal(4, finalDelta);
            ps.setLong(5, clientId);
            ps.setString(6, farmerId);
            ps.setDate(7, java.sql.Date.valueOf(salesDate));
            int updated = ps.executeUpdate();
            if (updated == 0) {
                logger.warn("adjustSummarySalesAmounts: no sales total summary row for farmerId={}, date={}", farmerId, salesDate);
            }
        } catch (SQLException e) {
            logger.error("adjustSummarySalesAmounts: SQL exception while adjusting sales total summary", e);
            throw new RuntimeException("Failed to adjust sales total summary", e);
        }
    }

    @Override
    public void reconcileSummary(Long clientId, String clientUsername, String farmerId, String farmerName,
                                 LocalDate salesDate, BigDecimal totalSalesAmt, BigDecimal commissionAmt,
                                 BigDecimal totalNetAmt, BigDecimal debitAmt, BigDecimal finalAmt,
                                 Connection conn) {
        logger.info("reconcileSummary: clientId={}, farmerId={}, date={}, total={}, commission={}, net={}, debit={}, final={}",
                clientId, farmerId, salesDate, totalSalesAmt, commissionAmt, totalNetAmt, debitAmt, finalAmt);
        try (PreparedStatement ps = conn.prepareStatement(RECONCILE_SUMMARY_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, farmerId);
            ps.setString(4, farmerName);
            ps.setDate(5, java.sql.Date.valueOf(salesDate));
            ps.setBigDecimal(6, totalSalesAmt);
            ps.setBigDecimal(7, commissionAmt);
            ps.setBigDecimal(8, totalNetAmt);
            ps.setBigDecimal(9, debitAmt);
            ps.setBigDecimal(10, finalAmt);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("reconcileSummary: SQL exception while reconciling sales total summary", e);
            throw new RuntimeException("Failed to reconcile sales total summary", e);
        }
    }
}
