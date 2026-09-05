package com.billing.dao.impl;

import com.billing.dao.FarmerTransactionDao;
import com.billing.entity.FarmerTransaction;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class FarmerTransactionDaoImpl implements FarmerTransactionDao {

    private static final Logger logger = LoggerFactory.getLogger(FarmerTransactionDaoImpl.class);

    private final DataSource dataSource;

    public FarmerTransactionDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_FARMER_TRANSACTION (CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, TRANSACTION_DATE, CASH_PAID_AMT, EXCESS_DEBIT_AMT, DEB_AMT, PAYMENT_MODE) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_FARMER_DATE_SQL =
            "SELECT FARMER_TRANSACTION_ID, CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, TRANSACTION_DATE, CASH_PAID_AMT, EXCESS_DEBIT_AMT, DEB_AMT, PAYMENT_MODE "
                    + "FROM BLOOMBUDDY_FARMER_TRANSACTION "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND TRANSACTION_DATE BETWEEN ? AND ? "
                    + "ORDER BY TRANSACTION_DATE DESC, FARMER_TRANSACTION_ID DESC";

    private static final String SUM_DEB_AMT_SQL =
            "SELECT COALESCE(SUM(DEB_AMT), 0) FROM BLOOMBUDDY_FARMER_TRANSACTION "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND TRANSACTION_DATE = ?";

    private static final String SELECT_DEB_AMTS_SQL =
            "SELECT DEB_AMT FROM BLOOMBUDDY_FARMER_TRANSACTION "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND TRANSACTION_DATE = ? AND DEB_AMT > 0 "
                    + "ORDER BY FARMER_TRANSACTION_ID ASC";

    private static final String SELECT_LATEST_CASH_PAID_SQL =
            "SELECT CASH_PAID_AMT FROM BLOOMBUDDY_FARMER_TRANSACTION "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND TRANSACTION_DATE = ? AND CASH_PAID_AMT > 0 "
                    + "ORDER BY FARMER_TRANSACTION_ID DESC LIMIT 1";

    @Override
    public FarmerTransaction insert(FarmerTransaction txn, Connection conn) {
        logger.info("insert: farmerId={}, clientId={}, excessDebitAmt={}", txn.getFarmerId(), txn.getClientId(), txn.getExcessDebitAmt());
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, new String[]{"FARMER_TRANSACTION_ID"})) {
            ps.setLong(1, txn.getClientId());
            ps.setString(2, txn.getClientUsername());
            ps.setString(3, txn.getFarmerId());
            ps.setString(4, txn.getFarmerName());
            ps.setDate(5, Date.valueOf(txn.getTransactionDate()));
            ps.setBigDecimal(6, txn.getCashPaidAmt());
            ps.setBigDecimal(7, txn.getExcessDebitAmt());
            ps.setBigDecimal(8, txn.getDebAmt());
            ps.setString(9, txn.getPaymentMode() != null ? txn.getPaymentMode() : "C");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    txn.setFarmerTransactionId(keys.getLong(1));
                }
            }
            logger.info("insert: farmer transaction inserted, id={}", txn.getFarmerTransactionId());
        } catch (SQLException e) {
            logger.error("insert: SQL exception while inserting farmer transaction", e);
            throw new RuntimeException("Failed to insert farmer transaction", e);
        }
        return txn;
    }

    @Override
    public List<FarmerTransaction> findByFarmerAndDateRange(Long clientId, String farmerId, LocalDate fromDate, LocalDate toDate) {
        logger.info("findByFarmerAndDateRange: clientId={}, farmerId={}, from={}, to={}", clientId, farmerId, fromDate, toDate);
        List<FarmerTransaction> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_FARMER_DATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, Date.valueOf(fromDate));
            ps.setDate(4, Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findByFarmerAndDateRange: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer transactions", e);
        }
        logger.info("findByFarmerAndDateRange: count={}", result.size());
        return result;
    }

    @Override
    public java.math.BigDecimal sumDebAmt(Long clientId, String farmerId, LocalDate transactionDate, Connection conn) {
        logger.info("sumDebAmt: clientId={}, farmerId={}, date={}", clientId, farmerId, transactionDate);
        try (PreparedStatement ps = conn.prepareStatement(SUM_DEB_AMT_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, Date.valueOf(transactionDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.math.BigDecimal sum = rs.getBigDecimal(1);
                    return sum != null ? sum : java.math.BigDecimal.ZERO;
                }
            }
        } catch (SQLException e) {
            logger.error("sumDebAmt: SQL exception", e);
            throw new RuntimeException("Failed to sum farmer transaction debit", e);
        }
        return java.math.BigDecimal.ZERO;
    }

    @Override
    public List<java.math.BigDecimal> findDebAmts(Long clientId, String farmerId, LocalDate transactionDate) {
        logger.info("findDebAmts: clientId={}, farmerId={}, date={}", clientId, farmerId, transactionDate);
        List<java.math.BigDecimal> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_DEB_AMTS_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, Date.valueOf(transactionDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.math.BigDecimal val = rs.getBigDecimal(1);
                    if (val != null && val.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        result.add(RoundOffUtil.round(val));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("findDebAmts: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer transaction debits", e);
        }
        return result;
    }

    @Override
    public java.math.BigDecimal findLatestCashPaid(Long clientId, String farmerId, LocalDate transactionDate) {
        logger.info("findLatestCashPaid: clientId={}, farmerId={}, date={}", clientId, farmerId, transactionDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_LATEST_CASH_PAID_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, Date.valueOf(transactionDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.math.BigDecimal val = rs.getBigDecimal(1);
                    return val != null ? RoundOffUtil.round(val) : null;
                }
            }
        } catch (SQLException e) {
            logger.error("findLatestCashPaid: SQL exception", e);
            throw new RuntimeException("Failed to fetch latest farmer cash paid amount", e);
        }
        return null;
    }

    private FarmerTransaction mapRow(ResultSet rs) throws SQLException {
        FarmerTransaction txn = new FarmerTransaction();
        txn.setFarmerTransactionId(rs.getLong("FARMER_TRANSACTION_ID"));
        txn.setClientId(rs.getLong("CLIENT_ID"));
        txn.setClientUsername(rs.getString("CLIENT_USERNAME"));
        txn.setFarmerId(rs.getString("FARMER_ID"));
        txn.setFarmerName(rs.getString("FARMER_NAME"));
        txn.setTransactionDate(rs.getDate("TRANSACTION_DATE").toLocalDate());
        txn.setCashPaidAmt(rs.getBigDecimal("CASH_PAID_AMT"));
        txn.setExcessDebitAmt(rs.getBigDecimal("EXCESS_DEBIT_AMT"));
        txn.setDebAmt(rs.getBigDecimal("DEB_AMT"));
        try { txn.setPaymentMode(rs.getString("PAYMENT_MODE")); } catch (SQLException ignored) { txn.setPaymentMode("C"); }
        if (txn.getPaymentMode() == null) txn.setPaymentMode("C");
        return txn;
    }
}
