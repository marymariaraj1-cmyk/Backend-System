package com.billing.dao.impl;

import com.billing.dao.InactiveListDao;
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
public class InactiveListDaoImpl implements InactiveListDao {

    private static final Logger logger = LoggerFactory.getLogger(InactiveListDaoImpl.class);

    private static final String FIND_INACTIVE_FARMERS =
            "SELECT f.FARMER_ID, f.FARMER_NAME, " +
            "(COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.CREDIT_AMT ELSE 0 END), 0) " +
            " - COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.DEBIT_AMT ELSE 0 END), 0) " +
            " + COALESCE((SELECT ob.OPENING_BALANCE FROM BLOOMBUDDY_FARMER_OPENING_BALANCE ob " +
            "             WHERE ob.CLIENT_ID = f.CLIENT_ID AND ob.FARMER_ID = f.FARMER_ID " +
            "               AND EXISTS (SELECT 1 FROM BLOOMBUDDY_FARMER_LEDGER y " +
            "                           WHERE y.CLIENT_ID = ob.CLIENT_ID AND y.FARMER_ID = ob.FARMER_ID " +
            "                             AND y.SALES_DATE = ob.OPENING_BALANCE_DATE AND y.LEDGER_ACTIVE = 'Y')), 0)) AS OUTSTANDING " +
            "FROM BLOOMBUDDY_FARMER_MASTER f " +
            "LEFT JOIN BLOOMBUDDY_FARMER_LEDGER fl " +
            "  ON fl.CLIENT_ID = f.CLIENT_ID AND fl.FARMER_ID = f.FARMER_ID " +
            "WHERE f.CLIENT_ID = ? " +
            "  AND NOT EXISTS (SELECT 1 FROM BLOOMBUDDY_SALES s " +
            "                  WHERE s.CLIENT_ID = f.CLIENT_ID AND s.FARMER_ID = f.FARMER_ID " +
            "                    AND s.SALES_DATE = ?) " +
            "GROUP BY f.FARMER_ID, f.FARMER_NAME " +
            "ORDER BY f.FARMER_NAME ASC";

    private static final String FIND_INACTIVE_BUYERS =
            "SELECT b.BUYER_ID, b.BUYER_NAME, " +
            "(COALESCE(SUM(CASE WHEN bl.LEDGER_ACTIVE = 'Y' THEN bl.DEBIT_AMT ELSE 0 END), 0) " +
            " - COALESCE(SUM(CASE WHEN bl.LEDGER_ACTIVE = 'Y' THEN bl.CREDIT_AMT ELSE 0 END), 0) " +
            " + COALESCE((SELECT ob.OPENING_BALANCE FROM BLOOMBUDDY_BUYER_OPENING_BALANCE ob " +
            "             WHERE ob.CLIENT_ID = b.CLIENT_ID AND ob.BUYER_ID = b.BUYER_ID " +
            "               AND EXISTS (SELECT 1 FROM BLOOMBUDDY_BUYER_LEDGER y " +
            "                           WHERE y.CLIENT_ID = ob.CLIENT_ID AND y.BUYER_ID = ob.BUYER_ID " +
            "                             AND y.SALES_DATE = ob.OPENING_BALANCE_DATE AND y.LEDGER_ACTIVE = 'Y')), 0)) AS OUTSTANDING " +
            "FROM BLOOMBUDDY_BUYER_MASTER b " +
            "LEFT JOIN BLOOMBUDDY_BUYER_LEDGER bl " +
            "  ON bl.CLIENT_ID = b.CLIENT_ID AND bl.BUYER_ID = b.BUYER_ID " +
            "WHERE b.CLIENT_ID = ? " +
            "  AND NOT EXISTS (SELECT 1 FROM BLOOMBUDDY_SALES s " +
            "                  WHERE s.CLIENT_ID = b.CLIENT_ID AND s.BUYER_ID = b.BUYER_ID " +
            "                    AND s.SALES_DATE = ?) " +
            "GROUP BY b.BUYER_ID, b.BUYER_NAME " +
            "ORDER BY b.BUYER_NAME ASC";

    private final DataSource dataSource;

    public InactiveListDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> findInactiveFarmers(Long clientId, LocalDate date) {
        logger.info("findInactiveFarmers: clientId={}, date={}", clientId, date);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_INACTIVE_FARMERS)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    row.put("outstandingBalance", toAmount(rs.getBigDecimal("OUTSTANDING")));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findInactiveFarmers: SQL exception", e);
            throw new RuntimeException("Failed to fetch inactive farmer list", e);
        }
        logger.info("findInactiveFarmers: count={}", results.size());
        return results;
    }

    @Override
    public List<Map<String, Object>> findInactiveBuyers(Long clientId, LocalDate date) {
        logger.info("findInactiveBuyers: clientId={}, date={}", clientId, date);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_INACTIVE_BUYERS)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    row.put("buyerName", rs.getString("BUYER_NAME"));
                    row.put("outstandingBalance", toAmount(rs.getBigDecimal("OUTSTANDING")));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findInactiveBuyers: SQL exception", e);
            throw new RuntimeException("Failed to fetch inactive buyer list", e);
        }
        logger.info("findInactiveBuyers: count={}", results.size());
        return results;
    }

    private BigDecimal toAmount(BigDecimal value) {
        return RoundOffUtil.round(value == null ? BigDecimal.ZERO : value);
    }
}