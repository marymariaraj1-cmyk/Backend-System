package com.billing.dao.impl;

import com.billing.dao.FarmerLedgerDao;
import com.billing.entity.FarmerLedger;
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
public class FarmerLedgerDaoImpl implements FarmerLedgerDao {

    private static final Logger logger = LoggerFactory.getLogger(FarmerLedgerDaoImpl.class);

    private final DataSource dataSource;

    @Autowired
    public FarmerLedgerDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_FARMER_LEDGER (CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, SALES_IDS) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "DEBIT_AMT = DEBIT_AMT + VALUES(DEBIT_AMT), "
                    + "CREDIT_AMT = CREDIT_AMT + VALUES(CREDIT_AMT), "
                    + "SALES_IDS = CASE WHEN VALUES(SALES_IDS) IS NULL THEN SALES_IDS "
                    + "WHEN SALES_IDS IS NULL THEN VALUES(SALES_IDS) "
                    + "ELSE CONCAT(SALES_IDS, ',', VALUES(SALES_IDS)) END";

    private static final String FIND_ROW_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, LEDGER_ACTIVE, SALES_IDS "
                    + "FROM BLOOMBUDDY_FARMER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? "
                    + "ORDER BY CASE WHEN LEDGER_ACTIVE = 'Y' THEN 0 ELSE 1 END ASC LIMIT 1";

    private static final String FIND_LATEST_BEFORE_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, LEDGER_ACTIVE, SALES_IDS "
                    + "FROM BLOOMBUDDY_FARMER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE < ? "
                    + "ORDER BY SALES_DATE DESC LIMIT 1";

    private static final String FIND_ALL_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, LEDGER_ACTIVE, SALES_IDS "
                    + "FROM BLOOMBUDDY_FARMER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? "
                    + "ORDER BY SALES_DATE ASC, FARMER_LEDGER_ID ASC";

    private static final String FIND_ACTIVE_SQL =
            "SELECT LEDGER_ACTIVE "
                    + "FROM BLOOMBUDDY_FARMER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? "
                    + "ORDER BY CASE WHEN LEDGER_ACTIVE = 'Y' THEN 0 ELSE 1 END ASC LIMIT 1";

    private static final String DEACTIVATE_SQL =
            "UPDATE BLOOMBUDDY_FARMER_LEDGER y "
                    + "LEFT JOIN BLOOMBUDDY_FARMER_LEDGER n "
                    + "  ON n.CLIENT_ID = y.CLIENT_ID AND n.FARMER_ID = y.FARMER_ID "
                    + "     AND n.SALES_DATE = y.SALES_DATE AND n.LEDGER_ACTIVE = 'N' "
                    + "SET y.LEDGER_ACTIVE = 'N' "
                    + "WHERE y.CLIENT_ID = ? AND y.FARMER_ID = ? AND y.SALES_DATE <= ? AND y.LEDGER_ACTIVE = 'Y' "
                    + "  AND n.FARMER_ID IS NULL";

    private static final String MERGE_DEACTIVATED_SQL =
            "UPDATE BLOOMBUDDY_FARMER_LEDGER n "
                    + "JOIN BLOOMBUDDY_FARMER_LEDGER y "
                    + "  ON n.CLIENT_ID = y.CLIENT_ID AND n.FARMER_ID = y.FARMER_ID AND n.SALES_DATE = y.SALES_DATE "
                    + "SET n.DEBIT_AMT = COALESCE(n.DEBIT_AMT, 0) + COALESCE(y.DEBIT_AMT, 0), "
                    + "    n.CREDIT_AMT = COALESCE(n.CREDIT_AMT, 0) + COALESCE(y.CREDIT_AMT, 0), "
                    + "    n.SALES_IDS = CASE WHEN y.SALES_IDS IS NULL OR y.SALES_IDS = '' THEN n.SALES_IDS "
                    + "WHEN n.SALES_IDS IS NULL OR n.SALES_IDS = '' THEN y.SALES_IDS "
                    + "ELSE CONCAT(n.SALES_IDS, ',', y.SALES_IDS) END "
                    + "WHERE n.CLIENT_ID = ? AND n.FARMER_ID = ? AND n.LEDGER_ACTIVE = 'N' "
                    + "  AND y.CLIENT_ID = ? AND y.FARMER_ID = ? AND y.LEDGER_ACTIVE = 'Y' AND y.SALES_DATE <= ?";

    private static final String DELETE_MERGED_SQL =
            "DELETE y FROM BLOOMBUDDY_FARMER_LEDGER y "
                    + "JOIN BLOOMBUDDY_FARMER_LEDGER n "
                    + "  ON n.CLIENT_ID = y.CLIENT_ID AND n.FARMER_ID = y.FARMER_ID AND n.SALES_DATE = y.SALES_DATE "
                    + "WHERE y.CLIENT_ID = ? AND y.FARMER_ID = ? AND y.LEDGER_ACTIVE = 'Y' "
                    + "  AND n.CLIENT_ID = ? AND n.FARMER_ID = ? AND n.LEDGER_ACTIVE = 'N' AND y.SALES_DATE <= ?";

    private static final String FIND_LATEST_DATE_SQL =
            "SELECT MAX(SALES_DATE) AS LATEST_DATE "
                    + "FROM BLOOMBUDDY_FARMER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ?";

    private static final String UPDATE_OPENING_BALANCE_SQL =
            "UPDATE BLOOMBUDDY_FARMER_LEDGER SET OPENING_BALANCE = ? "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? AND LEDGER_ACTIVE = 'N' "
                    + "AND OPENING_BALANCE IS NULL";

    private static final String DECREASE_CREDIT_SQL =
            "UPDATE BLOOMBUDDY_FARMER_LEDGER SET CREDIT_AMT = CREDIT_AMT - ? "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? AND LEDGER_ACTIVE = 'Y'";

    private static final String ADJUST_CREDIT_SQL =
            "UPDATE BLOOMBUDDY_FARMER_LEDGER SET CREDIT_AMT = CREDIT_AMT + ? "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? AND LEDGER_ACTIVE = 'Y'";

    private static final String FIND_SALES_IDS_SQL =
            "SELECT SALES_IDS "
                    + "FROM BLOOMBUDDY_FARMER_LEDGER "
                    + "WHERE CLIENT_ID = ? AND FARMER_ID = ? AND SALES_DATE = ? "
                    + "ORDER BY CASE WHEN LEDGER_ACTIVE = ? THEN 0 ELSE 1 END ASC LIMIT 1";

    private static final String SET_CREDIT_SQL =
            "INSERT INTO BLOOMBUDDY_FARMER_LEDGER (CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT) "
                    + "VALUES (?, ?, ?, ?, ?, 0, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "CREDIT_AMT = VALUES(CREDIT_AMT)";

    @Override
    public FarmerLedger insert(FarmerLedger ledger, Connection conn) {
        logger.info("insert: farmerId={}, clientId={}, creditAmt={}, debitAmt={}", ledger.getFarmerId(), ledger.getClientId(), ledger.getCreditAmt(), ledger.getDebitAmt());
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setLong(1, ledger.getClientId());
            ps.setString(2, ledger.getClientUsername());
            ps.setString(3, ledger.getFarmerId());
            ps.setString(4, ledger.getFarmerName());
            ps.setDate(5, java.sql.Date.valueOf(ledger.getSalesDate()));
            ps.setBigDecimal(6, ledger.getDebitAmt());
            ps.setBigDecimal(7, ledger.getCreditAmt());
            ps.setString(8, ledger.getSalesIds());
            ps.executeUpdate();
            logger.info("insert: farmer ledger inserted/updated for farmerId={}, date={}", ledger.getFarmerId(), ledger.getSalesDate());
        } catch (SQLException e) {
            logger.error("insert: SQL exception while inserting farmer ledger", e);
            throw new RuntimeException("Failed to insert farmer ledger", e);
        }
        return ledger;
    }

    @Override
    public FarmerLedger findRow(Long clientId, String farmerId, LocalDate date, Connection conn) {
        logger.info("findRow: clientId={}, farmerId={}, date={}", clientId, farmerId, date);
        try (PreparedStatement ps = conn.prepareStatement(FIND_ROW_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            logger.error("findRow: SQL exception while fetching farmer ledger row", e);
            throw new RuntimeException("Failed to fetch farmer ledger row", e);
        }
    }

    @Override
    public FarmerLedger findLatestBefore(Long clientId, String farmerId, LocalDate date, Connection conn) {
        logger.info("findLatestBefore: clientId={}, farmerId={}, date={}", clientId, farmerId, date);
        try (PreparedStatement ps = conn.prepareStatement(FIND_LATEST_BEFORE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            logger.error("findLatestBefore: SQL exception while fetching farmer ledger row", e);
            throw new RuntimeException("Failed to fetch farmer ledger row", e);
        }
    }

    @Override
    public List<FarmerLedger> findAll(Long clientId, String farmerId, Connection conn) {
        logger.info("findAll: clientId={}, farmerId={}", clientId, farmerId);
        List<FarmerLedger> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findAll: SQL exception while fetching farmer ledger rows", e);
            throw new RuntimeException("Failed to fetch farmer ledger rows", e);
        }
        return results;
    }

    @Override
    public String findLedgerActive(Long clientId, String farmerId, LocalDate date, Connection conn) {
        logger.info("findLedgerActive: clientId={}, farmerId={}, date={}", clientId, farmerId, date);
        try (PreparedStatement ps = conn.prepareStatement(FIND_ACTIVE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("LEDGER_ACTIVE");
                }
                return null;
            }
        } catch (SQLException e) {
            logger.error("findLedgerActive: SQL exception while fetching farmer ledger active flag", e);
            throw new RuntimeException("Failed to fetch farmer ledger active flag", e);
        }
    }

    @Override
    public void deactivateLedgerRows(Long clientId, String farmerId, LocalDate upToDate, Connection conn) {
        logger.info("deactivateLedgerRows: clientId={}, farmerId={}, upToDate={}", clientId, farmerId, upToDate);
        try (PreparedStatement ps = conn.prepareStatement(DEACTIVATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            ps.setDate(3, java.sql.Date.valueOf(upToDate));
            int updated = ps.executeUpdate();
            logger.info("deactivateLedgerRows: deactivated rows={}", updated);
        } catch (SQLException e) {
            logger.error("deactivateLedgerRows: SQL exception while deactivating farmer ledger rows", e);
            throw new RuntimeException("Failed to deactivate farmer ledger rows", e);
        }
    }

    @Override
    public void mergeDeactivatedRows(Long clientId, String farmerId, LocalDate upToDate, Connection conn) {
        logger.info("mergeDeactivatedRows: clientId={}, farmerId={}, upToDate={}", clientId, farmerId, upToDate);
        try (PreparedStatement mergePs = conn.prepareStatement(MERGE_DEACTIVATED_SQL)) {
            mergePs.setLong(1, clientId);
            mergePs.setString(2, farmerId);
            mergePs.setLong(3, clientId);
            mergePs.setString(4, farmerId);
            mergePs.setDate(5, java.sql.Date.valueOf(upToDate));
            int merged = mergePs.executeUpdate();
            logger.info("mergeDeactivatedRows: merged rows={}", merged);
        } catch (SQLException e) {
            logger.error("mergeDeactivatedRows: SQL exception while merging farmer ledger rows", e);
            throw new RuntimeException("Failed to merge deactivated farmer ledger rows", e);
        }
        try (PreparedStatement deletePs = conn.prepareStatement(DELETE_MERGED_SQL)) {
            deletePs.setLong(1, clientId);
            deletePs.setString(2, farmerId);
            deletePs.setLong(3, clientId);
            deletePs.setString(4, farmerId);
            deletePs.setDate(5, java.sql.Date.valueOf(upToDate));
            int deleted = deletePs.executeUpdate();
            logger.info("mergeDeactivatedRows: deleted rows={}", deleted);
        } catch (SQLException e) {
            logger.error("mergeDeactivatedRows: SQL exception while deleting merged farmer ledger rows", e);
            throw new RuntimeException("Failed to delete merged farmer ledger rows", e);
        }
    }

    @Override
    public void updateOpeningBalance(Long clientId, String farmerId, LocalDate date, BigDecimal openingBalance, Connection conn) {
        logger.info("updateOpeningBalance: clientId={}, farmerId={}, date={}, openingBalance={}", clientId, farmerId, date, openingBalance);
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_OPENING_BALANCE_SQL)) {
            ps.setBigDecimal(1, openingBalance);
            ps.setLong(2, clientId);
            ps.setString(3, farmerId);
            ps.setDate(4, java.sql.Date.valueOf(date));
            int updated = ps.executeUpdate();
            logger.info("updateOpeningBalance: updated rows={}", updated);
        } catch (SQLException e) {
            logger.error("updateOpeningBalance: SQL exception while stamping farmer ledger opening balance", e);
            throw new RuntimeException("Failed to update farmer ledger opening balance", e);
        }
    }

    @Override
    public LocalDate findLatestDate(Long clientId, String farmerId, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(FIND_LATEST_DATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Date date = rs.getDate("LATEST_DATE");
                    return date == null ? null : date.toLocalDate();
                }
            }
        } catch (SQLException e) {
            logger.error("findLatestDate: SQL exception while fetching latest farmer ledger date", e);
            throw new RuntimeException("Failed to fetch latest farmer ledger date", e);
        }
        return null;
    }

    @Override
    public void decreaseCreditAmt(Long clientId, String farmerId, LocalDate date, BigDecimal amount, Connection conn) {
        logger.info("decreaseCreditAmt: clientId={}, farmerId={}, date={}, amount={}", clientId, farmerId, date, amount);
        try (PreparedStatement ps = conn.prepareStatement(DECREASE_CREDIT_SQL)) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, clientId);
            ps.setString(3, farmerId);
            ps.setDate(4, java.sql.Date.valueOf(date));
            int updated = ps.executeUpdate();
            logger.info("decreaseCreditAmt: updated rows={}", updated);
        } catch (SQLException e) {
            logger.error("decreaseCreditAmt: SQL exception while updating farmer ledger", e);
            throw new RuntimeException("Failed to decrease farmer ledger credit", e);
        }
    }

    @Override
    public void adjustCreditAmt(Long clientId, String farmerId, LocalDate date, BigDecimal amount, Connection conn) {
        logger.info("adjustCreditAmt: clientId={}, farmerId={}, date={}, amount={}", clientId, farmerId, date, amount);
        try (PreparedStatement ps = conn.prepareStatement(ADJUST_CREDIT_SQL)) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, clientId);
            ps.setString(3, farmerId);
            ps.setDate(4, java.sql.Date.valueOf(date));
            int updated = ps.executeUpdate();
            logger.info("adjustCreditAmt: updated rows={}", updated);
        } catch (SQLException e) {
            logger.error("adjustCreditAmt: SQL exception while adjusting farmer ledger credit", e);
            throw new RuntimeException("Failed to adjust farmer ledger credit", e);
        }
    }

    @Override
    public void setCreditAmt(Long clientId, String clientUsername, String farmerId, String farmerName,
                             LocalDate date, BigDecimal amount, Connection conn) {
        logger.info("setCreditAmt: clientId={}, farmerId={}, date={}, amount={}", clientId, farmerId, date, amount);
        try (PreparedStatement ps = conn.prepareStatement(SET_CREDIT_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, farmerId);
            ps.setString(4, farmerName);
            ps.setDate(5, java.sql.Date.valueOf(date));
            ps.setBigDecimal(6, amount);
            ps.executeUpdate();
            logger.info("setCreditAmt: farmer ledger credit set for farmerId={}, date={}", farmerId, date);
        } catch (SQLException e) {
            logger.error("setCreditAmt: SQL exception while setting farmer ledger credit", e);
            throw new RuntimeException("Failed to set farmer ledger credit", e);
        }
    }

    private static final String SET_DEBIT_SQL =
            "INSERT INTO BLOOMBUDDY_FARMER_LEDGER (CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 0) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "DEBIT_AMT = VALUES(DEBIT_AMT)";

    @Override
    public void setDebitAmt(Long clientId, String clientUsername, String farmerId, String farmerName,
                            LocalDate date, BigDecimal amount, Connection conn) {
        logger.info("setDebitAmt: clientId={}, farmerId={}, date={}, amount={}", clientId, farmerId, date, amount);
        try (PreparedStatement ps = conn.prepareStatement(SET_DEBIT_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, farmerId);
            ps.setString(4, farmerName);
            ps.setDate(5, java.sql.Date.valueOf(date));
            ps.setBigDecimal(6, amount);
            ps.executeUpdate();
            logger.info("setDebitAmt: farmer ledger debit set for farmerId={}, date={}", farmerId, date);
        } catch (SQLException e) {
            logger.error("setDebitAmt: SQL exception while setting farmer ledger debit", e);
            throw new RuntimeException("Failed to set farmer ledger debit", e);
        }
    }

    private static final String SET_SETTLEMENT_CREDIT_SQL =
            "INSERT INTO BLOOMBUDDY_FARMER_LEDGER (CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, SALES_IDS) "
                    + "VALUES (?, ?, ?, ?, ?, 0, ?, '0') "
                    + "ON DUPLICATE KEY UPDATE "
                    + "CREDIT_AMT = VALUES(CREDIT_AMT), "
                    + "DEBIT_AMT = 0, "
                    + "SALES_IDS = '0'";

    private static final String SET_SETTLEMENT_DEBIT_SQL =
            "INSERT INTO BLOOMBUDDY_FARMER_LEDGER (CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, SALES_DATE, DEBIT_AMT, CREDIT_AMT, SALES_IDS) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 0, '0') "
                    + "ON DUPLICATE KEY UPDATE "
                    + "DEBIT_AMT = VALUES(DEBIT_AMT), "
                    + "CREDIT_AMT = 0, "
                    + "SALES_IDS = '0'";

    @Override
    public void setSettlementCreditAmt(Long clientId, String clientUsername, String farmerId, String farmerName,
                                       LocalDate date, BigDecimal amount, Connection conn) {
        logger.info("setSettlementCreditAmt: clientId={}, farmerId={}, date={}, amount={}", clientId, farmerId, date, amount);
        try (PreparedStatement ps = conn.prepareStatement(SET_SETTLEMENT_CREDIT_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, farmerId);
            ps.setString(4, farmerName);
            ps.setDate(5, java.sql.Date.valueOf(date));
            ps.setBigDecimal(6, amount);
            ps.executeUpdate();
            logger.info("setSettlementCreditAmt: farmer account check credit set for farmerId={}, date={}", farmerId, date);
        } catch (SQLException e) {
            logger.error("setSettlementCreditAmt: SQL exception while setting farmer account check credit", e);
            throw new RuntimeException("Failed to set farmer account check credit", e);
        }
    }

    @Override
    public void setSettlementDebitAmt(Long clientId, String clientUsername, String farmerId, String farmerName,
                                      LocalDate date, BigDecimal amount, Connection conn) {
        logger.info("setSettlementDebitAmt: clientId={}, farmerId={}, date={}, amount={}", clientId, farmerId, date, amount);
        try (PreparedStatement ps = conn.prepareStatement(SET_SETTLEMENT_DEBIT_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, farmerId);
            ps.setString(4, farmerName);
            ps.setDate(5, java.sql.Date.valueOf(date));
            ps.setBigDecimal(6, amount);
            ps.executeUpdate();
            logger.info("setSettlementDebitAmt: farmer account check debit set for farmerId={}, date={}", farmerId, date);
        } catch (SQLException e) {
            logger.error("setSettlementDebitAmt: SQL exception while setting farmer account check debit", e);
            throw new RuntimeException("Failed to set farmer account check debit", e);
        }
    }

    @Override
    public String findSalesIds(Long clientId, String farmerId, LocalDate date, String wantedActive) {
        logger.info("findSalesIds: clientId={}, farmerId={}, date={}, wantedActive={}", clientId, farmerId, date, wantedActive);
        String active = (wantedActive != null && !wantedActive.trim().isEmpty()) ? wantedActive.trim() : "Y";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_SALES_IDS_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
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
            throw new RuntimeException("Failed to fetch farmer sales IDs", e);
        }
    }

    private FarmerLedger mapRow(ResultSet rs) throws SQLException {
        FarmerLedger ledger = new FarmerLedger();
        ledger.setClientId(rs.getLong("CLIENT_ID"));
        ledger.setClientUsername(rs.getString("CLIENT_USERNAME"));
        ledger.setFarmerId(rs.getString("FARMER_ID"));
        ledger.setFarmerName(rs.getString("FARMER_NAME"));
        ledger.setSalesDate(rs.getDate("SALES_DATE").toLocalDate());
        ledger.setDebitAmt(rs.getBigDecimal("DEBIT_AMT"));
        ledger.setCreditAmt(rs.getBigDecimal("CREDIT_AMT"));
        ledger.setLedgerActive(rs.getString("LEDGER_ACTIVE"));
        ledger.setSalesIds(rs.getString("SALES_IDS"));
        return ledger;
    }
}
