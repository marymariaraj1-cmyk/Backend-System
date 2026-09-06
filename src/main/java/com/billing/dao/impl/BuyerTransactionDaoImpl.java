package com.billing.dao.impl;

import com.billing.dao.BuyerTransactionDao;
import com.billing.entity.BuyerTransaction;
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
public class BuyerTransactionDaoImpl implements BuyerTransactionDao {

    private static final Logger logger = LoggerFactory.getLogger(BuyerTransactionDaoImpl.class);

    private final DataSource dataSource;

    public BuyerTransactionDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_BUYER_TRANSACTION (CLIENT_ID, CLIENT_USERNAME, BUYER_ID, BUYER_NAME, TRANSACTION_DATE, CASH_PAID_AMT, DIS_AMT, PAYMENT_MODE) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_BUYER_DATE_SQL =
            "SELECT BUYER_TRANSACTION_ID, CLIENT_ID, CLIENT_USERNAME, BUYER_ID, BUYER_NAME, TRANSACTION_DATE, CASH_PAID_AMT, DIS_AMT, PAYMENT_MODE "
                    + "FROM BLOOMBUDDY_BUYER_TRANSACTION "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND TRANSACTION_DATE BETWEEN ? AND ? "
                    + "ORDER BY TRANSACTION_DATE DESC, BUYER_TRANSACTION_ID DESC";

    @Override
    public BuyerTransaction insert(BuyerTransaction txn, Connection conn) {
        logger.info("insert: buyerId={}, clientId={}, cashPaidAmt={}, disAmt={}", txn.getBuyerId(), txn.getClientId(), txn.getCashPaidAmt(), txn.getDisAmt());
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, new String[]{"BUYER_TRANSACTION_ID"})) {
            ps.setLong(1, txn.getClientId());
            ps.setString(2, txn.getClientUsername());
            ps.setString(3, txn.getBuyerId());
            ps.setString(4, txn.getBuyerName());
            ps.setDate(5, Date.valueOf(txn.getTransactionDate()));
            ps.setBigDecimal(6, txn.getCashPaidAmt());
            ps.setBigDecimal(7, txn.getDisAmt());
            ps.setString(8, txn.getPaymentMode() != null ? txn.getPaymentMode() : "C");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    txn.setBuyerTransactionId(keys.getLong(1));
                }
            }
            logger.info("insert: buyer transaction inserted, id={}", txn.getBuyerTransactionId());
        } catch (SQLException e) {
            logger.error("insert: SQL exception while inserting buyer transaction", e);
            throw new RuntimeException("Failed to insert buyer transaction", e);
        }
        return txn;
    }

    @Override
    public List<BuyerTransaction> findByBuyerAndDateRange(Long clientId, String buyerId, LocalDate fromDate, LocalDate toDate) {
        logger.info("findByBuyerAndDateRange: clientId={}, buyerId={}, from={}, to={}", clientId, buyerId, fromDate, toDate);
        List<BuyerTransaction> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_BUYER_DATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, Date.valueOf(fromDate));
            ps.setDate(4, Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findByBuyerAndDateRange: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer transactions", e);
        }
        logger.info("findByBuyerAndDateRange: count={}", result.size());
        return result;
    }

    private BuyerTransaction mapRow(ResultSet rs) throws SQLException {
        BuyerTransaction txn = new BuyerTransaction();
        txn.setBuyerTransactionId(rs.getLong("BUYER_TRANSACTION_ID"));
        txn.setClientId(rs.getLong("CLIENT_ID"));
        txn.setClientUsername(rs.getString("CLIENT_USERNAME"));
        txn.setBuyerId(rs.getString("BUYER_ID"));
        txn.setBuyerName(rs.getString("BUYER_NAME"));
        txn.setTransactionDate(rs.getDate("TRANSACTION_DATE").toLocalDate());
        txn.setCashPaidAmt(rs.getBigDecimal("CASH_PAID_AMT"));
        txn.setDisAmt(rs.getBigDecimal("DIS_AMT"));
        try { txn.setPaymentMode(rs.getString("PAYMENT_MODE")); } catch (SQLException ignored) { txn.setPaymentMode("C"); }
        if (txn.getPaymentMode() == null) txn.setPaymentMode("C");
        return txn;
    }
}
