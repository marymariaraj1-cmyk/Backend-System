package com.billing.dao.impl;

import com.billing.dao.BuyerLedgerReportDao;
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
public class BuyerLedgerReportDaoImpl implements BuyerLedgerReportDao {

    private static final Logger logger = LoggerFactory.getLogger(BuyerLedgerReportDaoImpl.class);

    private static final String FIND_BY_DATE_RANGE =
            "SELECT SALES_DATE, CREDIT_AMT, DEBIT_AMT, DIS_AMT, LEDGER_ACTIVE, OPENING_BALANCE " +
            "FROM BLOOMBUDDY_BUYER_LEDGER " +
            "WHERE CLIENT_ID = ? AND BUYER_ID = ? " +
            "AND SALES_DATE BETWEEN ? AND ? " +
            "ORDER BY SALES_DATE ASC, BUYER_LEDGER_ID ASC";

    private static final String FIND_ALL =
            "SELECT SALES_DATE, CREDIT_AMT, DEBIT_AMT, DIS_AMT, LEDGER_ACTIVE, OPENING_BALANCE " +
            "FROM BLOOMBUDDY_BUYER_LEDGER " +
            "WHERE CLIENT_ID = ? AND BUYER_ID = ? " +
            "ORDER BY SALES_DATE ASC, BUYER_LEDGER_ID ASC";

    private static final String FIND_BUYER_LIST =
            "SELECT fl.BUYER_ID, fl.BUYER_NAME, " +
            "(COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.DEBIT_AMT ELSE 0 END), 0) " +
            " - COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.CREDIT_AMT ELSE 0 END), 0) " +
            " + COALESCE((SELECT ob.OPENING_BALANCE FROM BLOOMBUDDY_BUYER_OPENING_BALANCE ob " +
            "             WHERE ob.CLIENT_ID = fl.CLIENT_ID AND ob.BUYER_ID = fl.BUYER_ID " +
            "               AND EXISTS (SELECT 1 FROM BLOOMBUDDY_BUYER_LEDGER y " +
            "                           WHERE y.CLIENT_ID = ob.CLIENT_ID AND y.BUYER_ID = ob.BUYER_ID " +
            "                             AND y.SALES_DATE = ob.OPENING_BALANCE_DATE AND y.LEDGER_ACTIVE = 'Y')), 0)) AS OUTSTANDING " +
            "FROM BLOOMBUDDY_BUYER_LEDGER fl " +
            "WHERE fl.CLIENT_ID = ? " +
            "GROUP BY fl.BUYER_ID, fl.BUYER_NAME " +
            "ORDER BY fl.BUYER_NAME ASC";

    private static final String FIND_CONFIGURED_OPENING_BALANCE =
            "SELECT OPENING_BALANCE " +
            "FROM BLOOMBUDDY_BUYER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ? AND BUYER_ID = ?";

    private static final String FIND_CONFIGURED_OPENING_BALANCE_DATE =
            "SELECT OPENING_BALANCE_DATE " +
            "FROM BLOOMBUDDY_BUYER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ? AND BUYER_ID = ?";

    private final DataSource dataSource;

    public BuyerLedgerReportDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> findByDateRange(Long clientId, String clientUsername, String buyerId,
                                                      LocalDate fromDate, LocalDate toDate) {
        logger.info("findByDateRange: clientId={}, buyerId={}, fromDate={}, toDate={}", clientId, buyerId, fromDate, toDate);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_DATE_RANGE)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, java.sql.Date.valueOf(fromDate));
            ps.setDate(4, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesDate", rs.getDate("SALES_DATE").toLocalDate().toString());
                    row.put("creditAmt", RoundOffUtil.round(rs.getBigDecimal("CREDIT_AMT")));
                    row.put("debitAmt", RoundOffUtil.round(rs.getBigDecimal("DEBIT_AMT")));
                    row.put("disAmt", RoundOffUtil.round(rs.getBigDecimal("DIS_AMT")));
                    row.put("ledgerActive", rs.getString("LEDGER_ACTIVE"));
                    row.put("openingBalanceStored", rs.getBigDecimal("OPENING_BALANCE"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findByDateRange: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer ledger report", e);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> findAll(Long clientId, String clientUsername, String buyerId) {
        logger.info("findAll: clientId={}, buyerId={}", clientId, buyerId);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesDate", rs.getDate("SALES_DATE").toLocalDate().toString());
                    row.put("creditAmt", RoundOffUtil.round(rs.getBigDecimal("CREDIT_AMT")));
                    row.put("debitAmt", RoundOffUtil.round(rs.getBigDecimal("DEBIT_AMT")));
                    row.put("disAmt", RoundOffUtil.round(rs.getBigDecimal("DIS_AMT")));
                    row.put("ledgerActive", rs.getString("LEDGER_ACTIVE"));
                    row.put("openingBalanceStored", rs.getBigDecimal("OPENING_BALANCE"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findAll: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer ledger", e);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> findBuyerList(Long clientId) {
        logger.info("findBuyerList: clientId={}", clientId);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BUYER_LIST)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    row.put("buyerName", rs.getString("BUYER_NAME"));
                    row.put("outstandingBalance", RoundOffUtil.round(rs.getBigDecimal("OUTSTANDING")));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findBuyerList: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer list", e);
        }
        logger.info("findBuyerList: count={}", results.size());
        return results;
    }

    @Override
    public BigDecimal findConfiguredOpeningBalance(Long clientId, String buyerId) {
        logger.info("findConfiguredOpeningBalance: clientId={}, buyerId={}", clientId, buyerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_CONFIGURED_OPENING_BALANCE)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
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
    public LocalDate findConfiguredOpeningBalanceDate(Long clientId, String buyerId) {
        logger.info("findConfiguredOpeningBalanceDate: clientId={}, buyerId={}", clientId, buyerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_CONFIGURED_OPENING_BALANCE_DATE)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
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
