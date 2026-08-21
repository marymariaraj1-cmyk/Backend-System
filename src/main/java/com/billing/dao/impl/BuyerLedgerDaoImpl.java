package com.billing.dao.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.entity.BuyerLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class BuyerLedgerDaoImpl implements BuyerLedgerDao {

    private static final Logger logger = LoggerFactory.getLogger(BuyerLedgerDaoImpl.class);

    private final DataSource dataSource;

    @Autowired
    public BuyerLedgerDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_BUYER_LEDGER (CLIENT_ID, CLIENT_USERNAME, BUYER_ID, BUYER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, DIS_AMT, SALES_IDS) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "DEBIT_AMT = DEBIT_AMT + VALUES(DEBIT_AMT), "
                    + "CREDIT_AMT = CREDIT_AMT + VALUES(CREDIT_AMT), "
                    + "DIS_AMT = DIS_AMT + VALUES(DIS_AMT), "
                    + "SALES_IDS = CASE WHEN VALUES(SALES_IDS) IS NULL THEN SALES_IDS "
                    + "WHEN SALES_IDS IS NULL THEN VALUES(SALES_IDS) "
                    + "ELSE CONCAT(SALES_IDS, ',', VALUES(SALES_IDS)) END";

    private static final String FIND_ROW_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, BUYER_ID, BUYER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, DIS_AMT, LEDGER_ACTIVE, SALES_IDS "
                    + "FROM BLOOMBUDDY_BUYER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE = ? "
                    + "ORDER BY CASE WHEN LEDGER_ACTIVE = 'Y' THEN 0 ELSE 1 END ASC LIMIT 1";

    private static final String FIND_LATEST_BEFORE_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, BUYER_ID, BUYER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, DIS_AMT, LEDGER_ACTIVE, SALES_IDS "
                    + "FROM BLOOMBUDDY_BUYER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE < ? "
                    + "ORDER BY SALES_DATE DESC LIMIT 1";

    private static final String FIND_LATEST_NON_ZERO_DEBIT_BEFORE_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, BUYER_ID, BUYER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, DIS_AMT, LEDGER_ACTIVE, SALES_IDS "
                    + "FROM BLOOMBUDDY_BUYER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE < ? AND DEBIT_AMT > 0 "
                    + "ORDER BY SALES_DATE DESC LIMIT 1";

    private static final String FIND_ALL_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, BUYER_ID, BUYER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, DIS_AMT, LEDGER_ACTIVE, SALES_IDS "
                    + "FROM BLOOMBUDDY_BUYER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? "
                    + "ORDER BY SALES_DATE ASC, BUYER_LEDGER_ID ASC";

    private static final String FIND_ACTIVE_SQL =
            "SELECT LEDGER_ACTIVE "
                    + "FROM BLOOMBUDDY_BUYER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE = ? "
                    + "ORDER BY CASE WHEN LEDGER_ACTIVE = 'Y' THEN 0 ELSE 1 END ASC LIMIT 1";

    private static final String DEACTIVATE_SQL =
            "UPDATE BLOOMBUDDY_BUYER_LEDGER y "
                    + "LEFT JOIN BLOOMBUDDY_BUYER_LEDGER n "
                    + "  ON n.CLIENT_ID = y.CLIENT_ID AND n.BUYER_ID = y.BUYER_ID "
                    + "     AND n.SALES_DATE = y.SALES_DATE AND n.LEDGER_ACTIVE = 'N' "
                    + "SET y.LEDGER_ACTIVE = 'N' "
                    + "WHERE y.CLIENT_ID = ? AND y.BUYER_ID = ? AND y.SALES_DATE <= ? AND y.LEDGER_ACTIVE = 'Y' "
                    + "  AND n.BUYER_ID IS NULL";

    private static final String MERGE_DEACTIVATED_SQL =
            "UPDATE BLOOMBUDDY_BUYER_LEDGER n "
                    + "JOIN BLOOMBUDDY_BUYER_LEDGER y "
                    + "  ON n.CLIENT_ID = y.CLIENT_ID AND n.BUYER_ID = y.BUYER_ID AND n.SALES_DATE = y.SALES_DATE "
                    + "SET n.DEBIT_AMT = COALESCE(n.DEBIT_AMT, 0) + COALESCE(y.DEBIT_AMT, 0), "
                    + "    n.CREDIT_AMT = COALESCE(n.CREDIT_AMT, 0) + COALESCE(y.CREDIT_AMT, 0), "
                    + "    n.DIS_AMT = COALESCE(n.DIS_AMT, 0) + COALESCE(y.DIS_AMT, 0), "
                    + "    n.SALES_IDS = CASE WHEN y.SALES_IDS IS NULL OR y.SALES_IDS = '' THEN n.SALES_IDS "
                    + "WHEN n.SALES_IDS IS NULL OR n.SALES_IDS = '' THEN y.SALES_IDS "
                    + "ELSE CONCAT(n.SALES_IDS, ',', y.SALES_IDS) END "
                    + "WHERE n.CLIENT_ID = ? AND n.BUYER_ID = ? AND n.LEDGER_ACTIVE = 'N' "
                    + "  AND y.CLIENT_ID = ? AND y.BUYER_ID = ? AND y.LEDGER_ACTIVE = 'Y' AND y.SALES_DATE <= ?";

    private static final String DELETE_MERGED_SQL =
            "DELETE y FROM BLOOMBUDDY_BUYER_LEDGER y "
                    + "JOIN BLOOMBUDDY_BUYER_LEDGER n "
                    + "  ON n.CLIENT_ID = y.CLIENT_ID AND n.BUYER_ID = y.BUYER_ID AND n.SALES_DATE = y.SALES_DATE "
                    + "WHERE y.CLIENT_ID = ? AND y.BUYER_ID = ? AND y.LEDGER_ACTIVE = 'Y' "
                    + "  AND n.CLIENT_ID = ? AND n.BUYER_ID = ? AND n.LEDGER_ACTIVE = 'N' AND y.SALES_DATE <= ?";

    private static final String FIND_LATEST_DATE_SQL =
            "SELECT MAX(SALES_DATE) AS LATEST_DATE "
                    + "FROM BLOOMBUDDY_BUYER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ?";

    private static final String UPDATE_OPENING_BALANCE_SQL =
            "UPDATE BLOOMBUDDY_BUYER_LEDGER SET OPENING_BALANCE = ? "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE = ? AND LEDGER_ACTIVE = 'N' "
                    + "AND OPENING_BALANCE IS NULL";

    private static final String DECREASE_DEBIT_SQL =
            "UPDATE BLOOMBUDDY_BUYER_LEDGER SET DEBIT_AMT = DEBIT_AMT - ? "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE = ? AND LEDGER_ACTIVE = 'Y'";

    private static final String ADD_DISCOUNT_SQL =
            "UPDATE BLOOMBUDDY_BUYER_LEDGER SET DIS_AMT = COALESCE(DIS_AMT, 0) + ? "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE = ? AND LEDGER_ACTIVE = 'Y'";

    private static final String OPENING_BALANCE_SQL =
            "SELECT COALESCE(SUM(DEBIT_AMT), 0) - COALESCE(SUM(CREDIT_AMT), 0) AS OPENING_BALANCE "
                    + "FROM BLOOMBUDDY_BUYER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND LEDGER_ACTIVE = 'Y'";

    private static final String FIND_SALES_IDS_SQL =
            "SELECT SALES_IDS "
                    + "FROM BLOOMBUDDY_BUYER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND BUYER_ID = ? AND SALES_DATE = ? "
                    + "ORDER BY CASE WHEN LEDGER_ACTIVE = ? THEN 0 ELSE 1 END ASC LIMIT 1";

    @Override
    public BuyerLedger insert(BuyerLedger ledger, Connection conn) {
        logger.info("insert: buyerId={}, clientId={}, debitAmt={}, creditAmt={}", ledger.getBuyerId(), ledger.getClientId(), ledger.getDebitAmt(), ledger.getCreditAmt());
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setLong(1, ledger.getClientId());
            ps.setString(2, ledger.getClientUsername());
            ps.setString(3, ledger.getBuyerId());
            ps.setString(4, ledger.getBuyerName());
            ps.setDate(5, java.sql.Date.valueOf(ledger.getSalesDate()));
            ps.setBigDecimal(6, ledger.getDebitAmt() != null ? ledger.getDebitAmt() : BigDecimal.ZERO);
            ps.setBigDecimal(7, ledger.getCreditAmt() != null ? ledger.getCreditAmt() : BigDecimal.ZERO);
            ps.setBigDecimal(8, ledger.getDisAmt() != null ? ledger.getDisAmt() : BigDecimal.ZERO);
            ps.setString(9, ledger.getSalesIds());
            ps.executeUpdate();
            logger.info("insert: buyer ledger inserted/updated for buyerId={}, date={}", ledger.getBuyerId(), ledger.getSalesDate());
        } catch (SQLException e) {
            logger.error("insert: SQL exception while inserting buyer ledger", e);
            throw new RuntimeException("Failed to insert buyer ledger", e);
        }
        return ledger;
    }

    @Override
    public BuyerLedger findRow(Long clientId, String buyerId, LocalDate date, Connection conn) {
        logger.info("findRow: clientId={}, buyerId={}, date={}", clientId, buyerId, date);
        try (PreparedStatement ps = conn.prepareStatement(FIND_ROW_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            logger.error("findRow: SQL exception while fetching buyer ledger row", e);
            throw new RuntimeException("Failed to fetch buyer ledger row", e);
        }
    }

    @Override
    public BuyerLedger findLatestBefore(Long clientId, String buyerId, LocalDate date, Connection conn) {
        logger.info("findLatestBefore: clientId={}, buyerId={}, date={}", clientId, buyerId, date);
        try (PreparedStatement ps = conn.prepareStatement(FIND_LATEST_BEFORE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            logger.error("findLatestBefore: SQL exception while fetching buyer ledger row", e);
            throw new RuntimeException("Failed to fetch buyer ledger row", e);
        }
    }

    @Override
    public BuyerLedger findLatestNonZeroDebitBefore(Long clientId, String buyerId, LocalDate date, Connection conn) {
        logger.info("findLatestNonZeroDebitBefore: clientId={}, buyerId={}, date={}", clientId, buyerId, date);
        try (PreparedStatement ps = conn.prepareStatement(FIND_LATEST_NON_ZERO_DEBIT_BEFORE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            logger.error("findLatestNonZeroDebitBefore: SQL exception while fetching buyer ledger row", e);
            throw new RuntimeException("Failed to fetch buyer ledger row", e);
        }
    }

    @Override
    public List<BuyerLedger> findAll(Long clientId, String buyerId, Connection conn) {
        logger.info("findAll: clientId={}, buyerId={}", clientId, buyerId);
        List<BuyerLedger> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findAll: SQL exception while fetching buyer ledger rows", e);
            throw new RuntimeException("Failed to fetch buyer ledger rows", e);
        }
        return results;
    }

    @Override
    public String findLedgerActive(Long clientId, String buyerId, LocalDate date, Connection conn) {
        logger.info("findLedgerActive: clientId={}, buyerId={}, date={}", clientId, buyerId, date);
        try (PreparedStatement ps = conn.prepareStatement(FIND_ACTIVE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("LEDGER_ACTIVE");
                }
                return null;
            }
        } catch (SQLException e) {
            logger.error("findLedgerActive: SQL exception while fetching buyer ledger active flag", e);
            throw new RuntimeException("Failed to fetch buyer ledger active flag", e);
        }
    }

    @Override
    public void deactivateLedgerRows(Long clientId, String buyerId, LocalDate upToDate, Connection conn) {
        logger.info("deactivateLedgerRows: clientId={}, buyerId={}, upToDate={}", clientId, buyerId, upToDate);
        try (PreparedStatement ps = conn.prepareStatement(DEACTIVATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, java.sql.Date.valueOf(upToDate));
            int updated = ps.executeUpdate();
            logger.info("deactivateLedgerRows: deactivated rows={}", updated);
        } catch (SQLException e) {
            logger.error("deactivateLedgerRows: SQL exception while deactivating buyer ledger rows", e);
            throw new RuntimeException("Failed to deactivate buyer ledger rows", e);
        }
    }

    @Override
    public void mergeDeactivatedRows(Long clientId, String buyerId, LocalDate upToDate, Connection conn) {
        logger.info("mergeDeactivatedRows: clientId={}, buyerId={}, upToDate={}", clientId, buyerId, upToDate);
        try (PreparedStatement mergePs = conn.prepareStatement(MERGE_DEACTIVATED_SQL)) {
            mergePs.setLong(1, clientId);
            mergePs.setString(2, buyerId);
            mergePs.setLong(3, clientId);
            mergePs.setString(4, buyerId);
            mergePs.setDate(5, java.sql.Date.valueOf(upToDate));
            int merged = mergePs.executeUpdate();
            logger.info("mergeDeactivatedRows: merged rows={}", merged);
        } catch (SQLException e) {
            logger.error("mergeDeactivatedRows: SQL exception while merging buyer ledger rows", e);
            throw new RuntimeException("Failed to merge deactivated buyer ledger rows", e);
        }
        try (PreparedStatement deletePs = conn.prepareStatement(DELETE_MERGED_SQL)) {
            deletePs.setLong(1, clientId);
            deletePs.setString(2, buyerId);
            deletePs.setLong(3, clientId);
            deletePs.setString(4, buyerId);
            deletePs.setDate(5, java.sql.Date.valueOf(upToDate));
            int deleted = deletePs.executeUpdate();
            logger.info("mergeDeactivatedRows: deleted rows={}", deleted);
        } catch (SQLException e) {
            logger.error("mergeDeactivatedRows: SQL exception while deleting merged buyer ledger rows", e);
            throw new RuntimeException("Failed to delete merged buyer ledger rows", e);
        }
    }

    @Override
    public void updateOpeningBalance(Long clientId, String buyerId, LocalDate date, BigDecimal openingBalance, Connection conn) {
        logger.info("updateOpeningBalance: clientId={}, buyerId={}, date={}, openingBalance={}", clientId, buyerId, date, openingBalance);
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_OPENING_BALANCE_SQL)) {
            ps.setBigDecimal(1, openingBalance);
            ps.setLong(2, clientId);
            ps.setString(3, buyerId);
            ps.setDate(4, java.sql.Date.valueOf(date));
            int updated = ps.executeUpdate();
            logger.info("updateOpeningBalance: updated rows={}", updated);
        } catch (SQLException e) {
            logger.error("updateOpeningBalance: SQL exception while stamping buyer ledger opening balance", e);
            throw new RuntimeException("Failed to update buyer ledger opening balance", e);
        }
    }

    @Override
    public LocalDate findLatestDate(Long clientId, String buyerId, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(FIND_LATEST_DATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Date date = rs.getDate("LATEST_DATE");
                    return date == null ? null : date.toLocalDate();
                }
            }
        } catch (SQLException e) {
            logger.error("findLatestDate: SQL exception while fetching latest buyer ledger date", e);
            throw new RuntimeException("Failed to fetch latest buyer ledger date", e);
        }
        return null;
    }

    @Override
    public void decreaseDebitAmt(Long clientId, String buyerId, LocalDate date, BigDecimal amount, Connection conn) {
        logger.info("decreaseDebitAmt: clientId={}, buyerId={}, date={}, amount={}", clientId, buyerId, date, amount);
        try (PreparedStatement ps = conn.prepareStatement(DECREASE_DEBIT_SQL)) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, clientId);
            ps.setString(3, buyerId);
            ps.setDate(4, java.sql.Date.valueOf(date));
            int updated = ps.executeUpdate();
            logger.info("decreaseDebitAmt: updated rows={}", updated);
        } catch (SQLException e) {
            logger.error("decreaseDebitAmt: SQL exception while updating buyer ledger", e);
            throw new RuntimeException("Failed to decrease buyer ledger debit", e);
        }
    }

    @Override
    public void addDiscountAmt(Long clientId, String buyerId, LocalDate date, BigDecimal amount, Connection conn) {
        logger.info("addDiscountAmt: clientId={}, buyerId={}, date={}, amount={}", clientId, buyerId, date, amount);
        try (PreparedStatement ps = conn.prepareStatement(ADD_DISCOUNT_SQL)) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, clientId);
            ps.setString(3, buyerId);
            ps.setDate(4, java.sql.Date.valueOf(date));
            int updated = ps.executeUpdate();
            logger.info("addDiscountAmt: updated rows={}", updated);
        } catch (SQLException e) {
            logger.error("addDiscountAmt: SQL exception while updating buyer ledger", e);
            throw new RuntimeException("Failed to add discount amount to buyer ledger", e);
        }
    }

    @Override
    public BigDecimal getOpeningBalance(Long clientId, String buyerId) {
        logger.info("getOpeningBalance: clientId={}, buyerId={}", clientId, buyerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(OPENING_BALANCE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal balance = rs.getBigDecimal("OPENING_BALANCE");
                    return balance != null ? balance : BigDecimal.ZERO;
                }
                return BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            logger.error("getOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer opening balance", e);
        }
    }

    @Override
    public String findSalesIds(Long clientId, String buyerId, LocalDate date, String wantedActive) {
        logger.info("findSalesIds: clientId={}, buyerId={}, date={}, wantedActive={}", clientId, buyerId, date, wantedActive);
        String active = (wantedActive != null && !wantedActive.trim().isEmpty()) ? wantedActive.trim() : "Y";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_SALES_IDS_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            ps.setString(4, active);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("SALES_IDS");
                }
                return null;
            }
        } catch (SQLException e) {
            logger.error("findSalesIds: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer sales IDs", e);
        }
    }

    private BuyerLedger mapRow(ResultSet rs) throws SQLException {
        BuyerLedger ledger = new BuyerLedger();
        ledger.setClientId(rs.getLong("CLIENT_ID"));
        ledger.setClientUsername(rs.getString("CLIENT_USERNAME"));
        ledger.setBuyerId(rs.getString("BUYER_ID"));
        ledger.setBuyerName(rs.getString("BUYER_NAME"));
        ledger.setSalesDate(rs.getDate("SALES_DATE").toLocalDate());
        ledger.setDebitAmt(rs.getBigDecimal("DEBIT_AMT"));
        ledger.setCreditAmt(rs.getBigDecimal("CREDIT_AMT"));
        ledger.setDisAmt(rs.getBigDecimal("DIS_AMT"));
        ledger.setLedgerActive(rs.getString("LEDGER_ACTIVE"));
        ledger.setSalesIds(rs.getString("SALES_IDS"));
        return ledger;
    }
}
