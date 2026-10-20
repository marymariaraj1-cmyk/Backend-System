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

    private static final String SELECT_COLUMNS =
            "FARMER_TRANSACTION_ID, CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, TRANSACTION_DATE, CASH_PAID_AMT, EXCESS_DEBIT_AMT, DEB_AMT, PAYMENT_MODE";

    private static final String SELECT_BY_ID_SQL =
            "SELECT " + SELECT_COLUMNS + " FROM BLOOMBUDDY_FARMER_TRANSACTION "
                    + "WHERE CLIENT_ID = ? AND FARMER_TRANSACTION_ID = ?";

    private static final String UPDATE_AMOUNTS_SQL =
            "UPDATE BLOOMBUDDY_FARMER_TRANSACTION SET EXCESS_DEBIT_AMT = ?, DEB_AMT = ?, PAYMENT_MODE = ? "
                    + "WHERE CLIENT_ID = ? AND FARMER_TRANSACTION_ID = ?";

    private static final String DELETE_BY_ID_SQL =
            "DELETE FROM BLOOMBUDDY_FARMER_TRANSACTION "
                    + "WHERE CLIENT_ID = ? AND FARMER_TRANSACTION_ID = ?";

    private static final String DELETE_DEBITS_FOR_DATE_SQL =
            "DELETE FROM BLOOMBUDDY_FARMER_TRANSACTION "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND TRANSACTION_DATE = ? AND DEB_AMT > 0";

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

    @Override
    public FarmerTransaction findById(Long clientId, Long farmerTransactionId, Connection conn) {
        logger.info("findById: clientId={}, farmerTransactionId={}", clientId, farmerTransactionId);
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setLong(1, clientId);
            ps.setLong(2, farmerTransactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("findById: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer transaction", e);
        }
        return null;
    }

    @Override
    public int updateAmounts(Long clientId, Long farmerTransactionId, java.math.BigDecimal excessDebitAmt,
                             java.math.BigDecimal debAmt, String paymentMode, Connection conn) {
        logger.info("updateAmounts: clientId={}, farmerTransactionId={}, excessDebitAmt={}, debAmt={}, paymentMode={}",
                clientId, farmerTransactionId, excessDebitAmt, debAmt, paymentMode);
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_AMOUNTS_SQL)) {
            ps.setBigDecimal(1, excessDebitAmt);
            ps.setBigDecimal(2, debAmt);
            ps.setString(3, paymentMode != null ? paymentMode : "C");
            ps.setLong(4, clientId);
            ps.setLong(5, farmerTransactionId);
            int updated = ps.executeUpdate();
            logger.info("updateAmounts: updated rows={}", updated);
            return updated;
        } catch (SQLException e) {
            logger.error("updateAmounts: SQL exception", e);
            throw new RuntimeException("Failed to update farmer transaction", e);
        }
    }

    @Override
    public int deleteById(Long clientId, Long farmerTransactionId, Connection conn) {
        logger.info("deleteById: clientId={}, farmerTransactionId={}", clientId, farmerTransactionId);
        try (PreparedStatement ps = conn.prepareStatement(DELETE_BY_ID_SQL)) {
            ps.setLong(1, clientId);
            ps.setLong(2, farmerTransactionId);
            int deleted = ps.executeUpdate();
            logger.info("deleteById: deleted rows={}", deleted);
            return deleted;
        } catch (SQLException e) {
            logger.error("deleteById: SQL exception", e);
            throw new RuntimeException("Failed to delete farmer transaction", e);
        }
    }

    @Override
    public void setTotalDebitForDate(Long clientId, String clientUsername, String farmerId, String farmerName,
                                    LocalDate transactionDate, java.math.BigDecimal newTotalDebit, Connection conn) {
        logger.info("setTotalDebitForDate: clientId={}, farmerId={}, date={}, newTotalDebit={}",
                clientId, farmerId, transactionDate, newTotalDebit);
        try {
            // First delete existing debit rows for this date
            try (PreparedStatement ps = conn.prepareStatement(DELETE_DEBITS_FOR_DATE_SQL)) {
                ps.setLong(1, clientId);
                ps.setString(2, farmerId);
                ps.setDate(3, Date.valueOf(transactionDate));
                ps.executeUpdate();
            }

            // If new total debit is greater than 0, insert a single debit transaction
            if (newTotalDebit != null && newTotalDebit.compareTo(java.math.BigDecimal.ZERO) > 0) {
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, new String[]{"FARMER_TRANSACTION_ID"})) {
                    ps.setLong(1, clientId);
                    ps.setString(2, clientUsername);
                    ps.setString(3, farmerId);
                    ps.setString(4, farmerName);
                    ps.setDate(5, Date.valueOf(transactionDate));
                    ps.setBigDecimal(6, java.math.BigDecimal.ZERO); // cashPaidAmt
                    ps.setBigDecimal(7, java.math.BigDecimal.ZERO); // excessDebitAmt
                    ps.setBigDecimal(8, RoundOffUtil.round(newTotalDebit)); // debAmt
                    ps.setString(9, "C"); // paymentMode
                    ps.executeUpdate();
                }
            }
            logger.info("setTotalDebitForDate: completed successfully");
        } catch (SQLException e) {
            logger.error("setTotalDebitForDate: SQL exception", e);
            throw new RuntimeException("Failed to set total debit for date", e);
        }
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
