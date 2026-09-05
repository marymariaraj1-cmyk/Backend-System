package com.billing.dao.impl;

import com.billing.dao.FarmerLedgerReportDao;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class FarmerLedgerReportDaoImpl implements FarmerLedgerReportDao {

    private static final Logger logger = LoggerFactory.getLogger(FarmerLedgerReportDaoImpl.class);

    private static final String FIND_BY_DATE_RANGE =
            "SELECT SALES_DATE, CREDIT_AMT, DEBIT_AMT, LEDGER_ACTIVE, OPENING_BALANCE, SALES_IDS " +
            "FROM BLOOMBUDDY_FARMER_LEDGER " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ? " +
            "AND SALES_DATE BETWEEN ? AND ? " +
            "ORDER BY SALES_DATE ASC, FARMER_LEDGER_ID ASC";

    private static final String FIND_ALL =
            "SELECT SALES_DATE, CREDIT_AMT, DEBIT_AMT, LEDGER_ACTIVE, OPENING_BALANCE, SALES_IDS " +
            "FROM BLOOMBUDDY_FARMER_LEDGER " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ? " +
            "ORDER BY SALES_DATE ASC, FARMER_LEDGER_ID ASC";

    private static final String FIND_FARMER_LIST =
            "SELECT fl.FARMER_ID, fl.FARMER_NAME, " +
            "(COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.CREDIT_AMT ELSE 0 END), 0) " +
            " - COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.DEBIT_AMT ELSE 0 END), 0) " +
            " + COALESCE((SELECT ob.OPENING_BALANCE FROM BLOOMBUDDY_FARMER_OPENING_BALANCE ob " +
            "             WHERE ob.CLIENT_ID = fl.CLIENT_ID AND ob.FARMER_ID = fl.FARMER_ID " +
            "               AND EXISTS (SELECT 1 FROM BLOOMBUDDY_FARMER_LEDGER y " +
            "                           WHERE y.CLIENT_ID = ob.CLIENT_ID AND y.FARMER_ID = ob.FARMER_ID " +
            "                             AND y.SALES_DATE = ob.OPENING_BALANCE_DATE AND y.LEDGER_ACTIVE = 'Y')), 0)) AS OUTSTANDING " +
            "FROM BLOOMBUDDY_FARMER_LEDGER fl " +
            "WHERE fl.CLIENT_ID = ? " +
            "GROUP BY fl.FARMER_ID, fl.FARMER_NAME " +
            "ORDER BY fl.FARMER_NAME ASC";

    private static final String FIND_CONFIGURED_OPENING_BALANCE =
            "SELECT OPENING_BALANCE " +
            "FROM BLOOMBUDDY_FARMER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ?";

    private static final String FIND_CONFIGURED_OPENING_BALANCE_DATE =
            "SELECT OPENING_BALANCE_DATE " +
            "FROM BLOOMBUDDY_FARMER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ?";

    private final DataSource dataSource;

    public FarmerLedgerReportDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> findByDateRange(Long clientId, String clientUsername, String farmerId,
                                                      LocalDate fromDate, LocalDate toDate) {
        logger.info("findByDateRange: clientId={}, farmerId={}, fromDate={}, toDate={}", clientId, farmerId, fromDate, toDate);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_DATE_RANGE)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(fromDate));
            ps.setDate(4, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesDate", rs.getDate("SALES_DATE").toLocalDate().toString());
                    row.put("creditAmt", RoundOffUtil.round(rs.getBigDecimal("CREDIT_AMT")));
                    row.put("debitAmt", RoundOffUtil.round(rs.getBigDecimal("DEBIT_AMT")));
                    row.put("ledgerActive", rs.getString("LEDGER_ACTIVE"));
                    row.put("openingBalanceStored", rs.getBigDecimal("OPENING_BALANCE"));
                    row.put("salesIds", rs.getString("SALES_IDS"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findByDateRange: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer ledger report", e);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> findAll(Long clientId, String clientUsername, String farmerId) {
        logger.info("findAll: clientId={}, farmerId={}", clientId, farmerId);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesDate", rs.getDate("SALES_DATE").toLocalDate().toString());
                    row.put("creditAmt", RoundOffUtil.round(rs.getBigDecimal("CREDIT_AMT")));
                    row.put("debitAmt", RoundOffUtil.round(rs.getBigDecimal("DEBIT_AMT")));
                    row.put("ledgerActive", rs.getString("LEDGER_ACTIVE"));
                    row.put("openingBalanceStored", rs.getBigDecimal("OPENING_BALANCE"));
                    row.put("salesIds", rs.getString("SALES_IDS"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findAll: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer ledger", e);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> findFarmerList(Long clientId) {
        logger.info("findFarmerList: clientId={}", clientId);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_FARMER_LIST)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    row.put("outstandingBalance", RoundOffUtil.round(rs.getBigDecimal("OUTSTANDING")));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findFarmerList: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer list", e);
        }
        logger.info("findFarmerList: count={}", results.size());
        return results;
    }

    @Override
    public BigDecimal findConfiguredOpeningBalance(Long clientId, String farmerId) {
        logger.info("findConfiguredOpeningBalance: clientId={}, farmerId={}", clientId, farmerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_CONFIGURED_OPENING_BALANCE)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("OPENING_BALANCE");
                }
            }
        } catch (SQLException e) {
            logger.error("findConfiguredOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to fetch configured opening balance", e);
        }
        return null;
    }

    @Override
    public LocalDate findConfiguredOpeningBalanceDate(Long clientId, String farmerId) {
        logger.info("findConfiguredOpeningBalanceDate: clientId={}, farmerId={}", clientId, farmerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_CONFIGURED_OPENING_BALANCE_DATE)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Date date = rs.getDate("OPENING_BALANCE_DATE");
                    return date == null ? null : date.toLocalDate();
                }
            }
        } catch (SQLException e) {
            logger.error("findConfiguredOpeningBalanceDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch configured opening balance date", e);
        }
        return null;
    }
}
