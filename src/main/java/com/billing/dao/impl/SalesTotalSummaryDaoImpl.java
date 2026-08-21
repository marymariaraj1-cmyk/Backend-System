package com.billing.dao.impl;

import com.billing.dao.SalesTotalSummaryDao;
import com.billing.entity.SalesTotalSummary;
import com.billing.util.SalesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

@Repository
public class SalesTotalSummaryDaoImpl implements SalesTotalSummaryDao {

    private static final Logger logger = LoggerFactory.getLogger(SalesTotalSummaryDaoImpl.class);

    private static final String UPSERT_SQL =
            "INSERT INTO BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "(CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, TOTAL_SALES_AMT, COMMISSION_AMT, TOTAL_NET_AMT, DEBIT_AMT, FINAL_AMT) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "FARMER_NAME = VALUES(FARMER_NAME), " +
            "TOTAL_SALES_AMT = TOTAL_SALES_AMT + VALUES(TOTAL_SALES_AMT), " +
            "COMMISSION_AMT = COMMISSION_AMT + VALUES(COMMISSION_AMT), " +
            "TOTAL_NET_AMT = TOTAL_NET_AMT + VALUES(TOTAL_NET_AMT), " +
            "DEBIT_AMT = DEBIT_AMT + VALUES(DEBIT_AMT), " +
            "FINAL_AMT = TOTAL_NET_AMT - DEBIT_AMT";

    private static final String SELECT_RANGE_SQL =
            "SELECT FARMER_ID, FARMER_NAME, TOTAL_SALES_AMT, COMMISSION_AMT, TOTAL_NET_AMT, DEBIT_AMT, FINAL_AMT " +
            "FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND SALES_DATE BETWEEN ? AND ?";

    private static final String SELECT_FOR_DATE_SQL =
            "SELECT FARMER_ID, FARMER_NAME, TOTAL_SALES_AMT, COMMISSION_AMT, TOTAL_NET_AMT, DEBIT_AMT, FINAL_AMT " +
            "FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND SALES_DATE = ?";

    private static final String FIND_ROW_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, TOTAL_SALES_AMT, COMMISSION_AMT, TOTAL_NET_AMT, DEBIT_AMT, FINAL_AMT " +
            "FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? LIMIT 1";

    private static final String FIND_LATEST_BEFORE_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, TOTAL_SALES_AMT, COMMISSION_AMT, TOTAL_NET_AMT, DEBIT_AMT, FINAL_AMT " +
            "FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE < ? " +
            "ORDER BY SALES_DATE DESC LIMIT 1";

    private static final String ADJUST_DEBIT_SQL =
            "UPDATE BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "SET DEBIT_AMT = DEBIT_AMT + ?, FINAL_AMT = FINAL_AMT - ? " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ?";

    private final DataSource dataSource;

    @Autowired
    public SalesTotalSummaryDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void upsert(SalesTotalSummary summary, Connection conn) {
        logger.info("upsert: farmerId={}, clientId={}, date={}, totalSalesAmt={}, commissionAmt={}, totalNetAmt={}, debitAmt={}, finalAmt={}",
                summary.getFarmerId(), summary.getClientId(), summary.getSalesDate(),
                summary.getTotalSalesAmt(), summary.getCommissionAmt(), summary.getTotalNetAmt(),
                summary.getDebitAmt(), summary.getFinalAmt());
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setLong(1, summary.getClientId());
            ps.setString(2, summary.getClientUsername());
            ps.setString(3, summary.getFarmerId());
            ps.setString(4, summary.getFarmerName());
            ps.setDate(5, java.sql.Date.valueOf(summary.getSalesDate()));
            ps.setBigDecimal(6, summary.getTotalSalesAmt());
            ps.setBigDecimal(7, summary.getCommissionAmt());
            ps.setBigDecimal(8, summary.getTotalNetAmt());
            ps.setBigDecimal(9, summary.getDebitAmt());
            ps.setBigDecimal(10, summary.getFinalAmt());
            ps.executeUpdate();
            logger.info("upsert: sales total summary inserted/updated for farmerId={}, date={}", summary.getFarmerId(), summary.getSalesDate());
        } catch (SQLException e) {
            logger.error("upsert: SQL exception while upserting sales total summary", e);
            throw new RuntimeException("Failed to upsert sales total summary", e);
        }
    }

    @Override
    public List<Map<String, Object>> findSummaryByDateRange(Long clientId, LocalDate fromDate, LocalDate toDate) {
        logger.info("findSummaryByDateRange: clientId={}, fromDate={}, toDate={}", clientId, fromDate, toDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_RANGE_SQL)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(fromDate));
            ps.setDate(3, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findSummaryByDateRange: SQL exception", e);
            throw new RuntimeException("Failed to fetch sales total summary range", e);
        }
        logger.info("findSummaryByDateRange: rows={}", rows.size());
        return rows;
    }

    @Override
    public List<Map<String, Object>> findSummaryForDate(Long clientId, LocalDate salesDate) {
        logger.info("findSummaryForDate: clientId={}, date={}", clientId, salesDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_FOR_DATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findSummaryForDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch sales total summary for date", e);
        }
        logger.info("findSummaryForDate: rows={}", rows.size());
        return rows;
    }

    @Override
    public Map<String, Object> findRow(Long clientId, String farmerId, LocalDate salesDate) {
        logger.info("findRow: clientId={}, farmerId={}, date={}", clientId, farmerId, salesDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ROW_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(salesDate));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            logger.error("findRow: SQL exception while fetching sales total summary row", e);
            throw new RuntimeException("Failed to fetch sales total summary row", e);
        }
    }

    @Override
    public Map<String, Object> findLatestBefore(Long clientId, String farmerId, LocalDate date) {
        logger.info("findLatestBefore: clientId={}, farmerId={}, date={}", clientId, farmerId, date);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_LATEST_BEFORE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            logger.error("findLatestBefore: SQL exception while fetching sales total summary row", e);
            throw new RuntimeException("Failed to fetch sales total summary row", e);
        }
    }

    @Override
    public void adjustDebit(Long clientId, String farmerId, LocalDate salesDate, BigDecimal debitAmount, Connection conn) {
        logger.info("adjustDebit: clientId={}, farmerId={}, date={}, debitAmount={}", clientId, farmerId, salesDate, debitAmount);
        try (PreparedStatement ps = conn.prepareStatement(ADJUST_DEBIT_SQL)) {
            ps.setBigDecimal(1, debitAmount);
            ps.setBigDecimal(2, debitAmount);
            ps.setLong(3, clientId);
            ps.setString(4, farmerId);
            ps.setDate(5, java.sql.Date.valueOf(salesDate));
            int updated = ps.executeUpdate();
            logger.info("adjustDebit: updated rows={}", updated);
        } catch (SQLException e) {
            logger.error("adjustDebit: SQL exception while updating sales total summary", e);
            throw new RuntimeException("Failed to adjust sales total summary debit", e);
        }
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        if (hasColumn(rs, "CLIENT_ID")) {
            row.put("clientId", rs.getLong("CLIENT_ID"));
        }
        if (hasColumn(rs, "CLIENT_USERNAME")) {
            row.put("clientUsername", rs.getString("CLIENT_USERNAME"));
        }
        row.put("farmerId", rs.getString("FARMER_ID"));
        row.put("farmerName", rs.getString("FARMER_NAME"));
        if (hasColumn(rs, "SALES_DATE")) {
            java.sql.Date salesDate = rs.getDate("SALES_DATE");
            row.put("salesDate", salesDate == null ? "" : SalesUtil.formatDate(salesDate.toLocalDate()));
        }
        if (hasColumn(rs, "TOTAL_SALES_AMT")) {
            row.put("totalSalesAmt", rs.getBigDecimal("TOTAL_SALES_AMT"));
        }
        if (hasColumn(rs, "COMMISSION_AMT")) {
            row.put("commissionAmt", rs.getBigDecimal("COMMISSION_AMT"));
        }
        if (hasColumn(rs, "TOTAL_NET_AMT")) {
            row.put("totalNetAmt", rs.getBigDecimal("TOTAL_NET_AMT"));
        }
        if (hasColumn(rs, "DEBIT_AMT")) {
            row.put("debitAmt", rs.getBigDecimal("DEBIT_AMT"));
        }
        if (hasColumn(rs, "FINAL_AMT")) {
            row.put("finalAmt", rs.getBigDecimal("FINAL_AMT"));
        }
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
