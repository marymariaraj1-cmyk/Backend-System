package com.billing.dao.impl;

import com.billing.dao.OpeningBalanceConfigDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpeningBalanceConfigDaoImpl implements OpeningBalanceConfigDao {

    private static final Logger logger = LoggerFactory.getLogger(OpeningBalanceConfigDaoImpl.class);

    private final DataSource dataSource;

    public OpeningBalanceConfigDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String FIND_ALL_FARMERS =
            "SELECT FARMER_ID, FARMER_NAME " +
            "FROM BLOOMBUDDY_FARMER_MASTER " +
            "WHERE CLIENT_ID = ? " +
            "ORDER BY FARMER_NAME ASC";

    private static final String FIND_ALL_BUYERS =
            "SELECT BUYER_ID, BUYER_NAME " +
            "FROM BLOOMBUDDY_BUYER_MASTER " +
            "WHERE CLIENT_ID = ? " +
            "ORDER BY BUYER_NAME ASC";

    private static final String FIND_FARMER_OB_MAP =
            "SELECT FARMER_ID, OPENING_BALANCE " +
            "FROM BLOOMBUDDY_FARMER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ?";

    private static final String FIND_BUYER_OB_MAP =
            "SELECT BUYER_ID, OPENING_BALANCE " +
            "FROM BLOOMBUDDY_BUYER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ?";

    private static final String FIND_FARMER_OB_DATE_MAP =
            "SELECT FARMER_ID, OPENING_BALANCE_DATE " +
            "FROM BLOOMBUDDY_FARMER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ?";

    private static final String FIND_BUYER_OB_DATE_MAP =
            "SELECT BUYER_ID, OPENING_BALANCE_DATE " +
            "FROM BLOOMBUDDY_BUYER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ?";

    private static final String FIND_FARMER_OB =
            "SELECT OPENING_BALANCE " +
            "FROM BLOOMBUDDY_FARMER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ?";

    private static final String FIND_BUYER_OB =
            "SELECT OPENING_BALANCE " +
            "FROM BLOOMBUDDY_BUYER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ? AND BUYER_ID = ?";

    private static final String FIND_FARMER_OB_DATE =
            "SELECT OPENING_BALANCE_DATE " +
            "FROM BLOOMBUDDY_FARMER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ?";

    private static final String FIND_BUYER_OB_DATE =
            "SELECT OPENING_BALANCE_DATE " +
            "FROM BLOOMBUDDY_BUYER_OPENING_BALANCE " +
            "WHERE CLIENT_ID = ? AND BUYER_ID = ?";

    private static final String FARMER_LEDGER_SETTLED_CHECK =
            "SELECT 1 FROM BLOOMBUDDY_FARMER_LEDGER n " +
            "WHERE n.CLIENT_ID = ? AND n.FARMER_ID = ? AND n.LEDGER_ACTIVE = 'N' AND n.SALES_DATE >= ? " +
            "  AND NOT EXISTS (SELECT 1 FROM BLOOMBUDDY_FARMER_LEDGER y " +
            "                  WHERE y.CLIENT_ID = n.CLIENT_ID AND y.FARMER_ID = n.FARMER_ID " +
            "                    AND y.LEDGER_ACTIVE = 'Y' AND y.SALES_DATE <= ?) LIMIT 1";

    private static final String BUYER_LEDGER_SETTLED_CHECK =
            "SELECT 1 FROM BLOOMBUDDY_BUYER_LEDGER n " +
            "WHERE n.CLIENT_ID = ? AND n.BUYER_ID = ? AND n.LEDGER_ACTIVE = 'N' AND n.SALES_DATE >= ? " +
            "  AND NOT EXISTS (SELECT 1 FROM BLOOMBUDDY_BUYER_LEDGER y " +
            "                  WHERE y.CLIENT_ID = n.CLIENT_ID AND y.BUYER_ID = n.BUYER_ID " +
            "                    AND y.LEDGER_ACTIVE = 'Y' AND y.SALES_DATE <= ?) LIMIT 1";

    private static final String UPSERT_FARMER_OB =
            "INSERT INTO BLOOMBUDDY_FARMER_OPENING_BALANCE (CLIENT_ID, CLIENT_USERNAME, FARMER_ID, FARMER_NAME, OPENING_BALANCE, OPENING_BALANCE_DATE) "
                    + "SELECT ?, ?, ?, FARMER_NAME, ?, ? FROM BLOOMBUDDY_FARMER_MASTER WHERE CLIENT_ID = ? AND FARMER_ID = ? "
                    + "ON DUPLICATE KEY UPDATE FARMER_NAME = VALUES(FARMER_NAME), OPENING_BALANCE = VALUES(OPENING_BALANCE), OPENING_BALANCE_DATE = VALUES(OPENING_BALANCE_DATE)";

    private static final String UPSERT_BUYER_OB =
            "INSERT INTO BLOOMBUDDY_BUYER_OPENING_BALANCE (CLIENT_ID, CLIENT_USERNAME, BUYER_ID, BUYER_NAME, OPENING_BALANCE, OPENING_BALANCE_DATE) "
                    + "SELECT ?, ?, ?, BUYER_NAME, ?, ? FROM BLOOMBUDDY_BUYER_MASTER WHERE CLIENT_ID = ? AND BUYER_ID = ? "
                    + "ON DUPLICATE KEY UPDATE BUYER_NAME = VALUES(BUYER_NAME), OPENING_BALANCE = VALUES(OPENING_BALANCE), OPENING_BALANCE_DATE = VALUES(OPENING_BALANCE_DATE)";

    private static final String CLEAR_FARMER_OB =
            "UPDATE BLOOMBUDDY_FARMER_OPENING_BALANCE " +
            "SET OPENING_BALANCE = 0, OPENING_BALANCE_DATE = NULL " +
            "WHERE CLIENT_ID = ? AND FARMER_ID = ?";

    private static final String CLEAR_BUYER_OB =
            "UPDATE BLOOMBUDDY_BUYER_OPENING_BALANCE " +
            "SET OPENING_BALANCE = 0, OPENING_BALANCE_DATE = NULL " +
            "WHERE CLIENT_ID = ? AND BUYER_ID = ?";

    @Override
    public List<Map<String, Object>> findAllFarmers(Long clientId) {
        logger.info("findAllFarmers: clientId={}", clientId);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL_FARMERS)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findAllFarmers: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmers", e);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> findAllBuyers(Long clientId) {
        logger.info("findAllBuyers: clientId={}", clientId);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL_BUYERS)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    row.put("buyerName", rs.getString("BUYER_NAME"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findAllBuyers: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyers", e);
        }
        return results;
    }

    @Override
    public Map<String, BigDecimal> findFarmerOpeningBalances(Long clientId) {
        logger.info("findFarmerOpeningBalances: clientId={}", clientId);
        Map<String, BigDecimal> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_FARMER_OB_MAP)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("FARMER_ID"), rs.getBigDecimal("OPENING_BALANCE"));
                }
            }
        } catch (SQLException e) {
            logger.error("findFarmerOpeningBalances: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer opening balances", e);
        }
        return result;
    }

    @Override
    public Map<String, BigDecimal> findBuyerOpeningBalances(Long clientId) {
        logger.info("findBuyerOpeningBalances: clientId={}", clientId);
        Map<String, BigDecimal> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BUYER_OB_MAP)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("BUYER_ID"), rs.getBigDecimal("OPENING_BALANCE"));
                }
            }
        } catch (SQLException e) {
            logger.error("findBuyerOpeningBalances: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer opening balances", e);
        }
        return result;
    }

    @Override
    public Map<String, LocalDate> findFarmerOpeningBalanceDates(Long clientId) {
        logger.info("findFarmerOpeningBalanceDates: clientId={}", clientId);
        Map<String, LocalDate> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_FARMER_OB_DATE_MAP)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date date = rs.getDate("OPENING_BALANCE_DATE");
                    result.put(rs.getString("FARMER_ID"), date == null ? null : date.toLocalDate());
                }
            }
        } catch (SQLException e) {
            logger.error("findFarmerOpeningBalanceDates: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer opening balance dates", e);
        }
        return result;
    }

    @Override
    public Map<String, LocalDate> findBuyerOpeningBalanceDates(Long clientId) {
        logger.info("findBuyerOpeningBalanceDates: clientId={}", clientId);
        Map<String, LocalDate> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BUYER_OB_DATE_MAP)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date date = rs.getDate("OPENING_BALANCE_DATE");
                    result.put(rs.getString("BUYER_ID"), date == null ? null : date.toLocalDate());
                }
            }
        } catch (SQLException e) {
            logger.error("findBuyerOpeningBalanceDates: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer opening balance dates", e);
        }
        return result;
    }

    @Override
    public BigDecimal findFarmerOpeningBalance(Long clientId, String farmerId) {
        logger.info("findFarmerOpeningBalance: clientId={}, farmerId={}", clientId, farmerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_FARMER_OB)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("OPENING_BALANCE");
                }
            }
        } catch (SQLException e) {
            logger.error("findFarmerOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer opening balance", e);
        }
        return null;
    }

    @Override
    public BigDecimal findBuyerOpeningBalance(Long clientId, String buyerId) {
        logger.info("findBuyerOpeningBalance: clientId={}, buyerId={}", clientId, buyerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BUYER_OB)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("OPENING_BALANCE");
                }
            }
        } catch (SQLException e) {
            logger.error("findBuyerOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer opening balance", e);
        }
        return null;
    }

    @Override
    public LocalDate findFarmerOpeningBalanceDate(Long clientId, String farmerId) {
        logger.info("findFarmerOpeningBalanceDate: clientId={}, farmerId={}", clientId, farmerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_FARMER_OB_DATE)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Date date = rs.getDate("OPENING_BALANCE_DATE");
                    return date == null ? null : date.toLocalDate();
                }
            }
        } catch (SQLException e) {
            logger.error("findFarmerOpeningBalanceDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer opening balance date", e);
        }
        return null;
    }

    @Override
    public LocalDate findBuyerOpeningBalanceDate(Long clientId, String buyerId) {
        logger.info("findBuyerOpeningBalanceDate: clientId={}, buyerId={}", clientId, buyerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BUYER_OB_DATE)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Date date = rs.getDate("OPENING_BALANCE_DATE");
                    return date == null ? null : date.toLocalDate();
                }
            }
        } catch (SQLException e) {
            logger.error("findBuyerOpeningBalanceDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer opening balance date", e);
        }
        return null;
    }

    @Override
    public BigDecimal findFarmerOpeningBalance(Long clientId, String farmerId, Connection conn) {
        return queryOpeningBalance(conn, FIND_FARMER_OB, clientId, farmerId);
    }

    @Override
    public BigDecimal findBuyerOpeningBalance(Long clientId, String buyerId, Connection conn) {
        return queryOpeningBalance(conn, FIND_BUYER_OB, clientId, buyerId);
    }

    @Override
    public LocalDate findFarmerOpeningBalanceDate(Long clientId, String farmerId, Connection conn) {
        return queryOpeningBalanceDate(conn, FIND_FARMER_OB_DATE, clientId, farmerId);
    }

    @Override
    public LocalDate findBuyerOpeningBalanceDate(Long clientId, String buyerId, Connection conn) {
        return queryOpeningBalanceDate(conn, FIND_BUYER_OB_DATE, clientId, buyerId);
    }

    @Override
    public boolean isFarmerLedgerSettled(Long clientId, String farmerId, LocalDate date) {
        logger.info("isFarmerLedgerSettled: clientId={}, farmerId={}, date={}", clientId, farmerId, date);
        try (Connection conn = dataSource.getConnection()) {
            return isLedgerRowSettled(conn, FARMER_LEDGER_SETTLED_CHECK, clientId, farmerId, date);
        } catch (SQLException e) {
            logger.error("isFarmerLedgerSettled: SQL exception", e);
            throw new RuntimeException("Failed to check settled ledger period", e);
        }
    }

    @Override
    public boolean isBuyerLedgerSettled(Long clientId, String buyerId, LocalDate date) {
        logger.info("isBuyerLedgerSettled: clientId={}, buyerId={}, date={}", clientId, buyerId, date);
        try (Connection conn = dataSource.getConnection()) {
            return isLedgerRowSettled(conn, BUYER_LEDGER_SETTLED_CHECK, clientId, buyerId, date);
        } catch (SQLException e) {
            logger.error("isBuyerLedgerSettled: SQL exception", e);
            throw new RuntimeException("Failed to check settled ledger period", e);
        }
    }

    @Override
    public boolean isFarmerLedgerSettled(Connection conn, Long clientId, String farmerId, LocalDate date) {
        logger.info("isFarmerLedgerSettled(conn): clientId={}, farmerId={}, date={}", clientId, farmerId, date);
        return isLedgerRowSettled(conn, FARMER_LEDGER_SETTLED_CHECK, clientId, farmerId, date);
    }

    @Override
    public boolean isBuyerLedgerSettled(Connection conn, Long clientId, String buyerId, LocalDate date) {
        logger.info("isBuyerLedgerSettled(conn): clientId={}, buyerId={}, date={}", clientId, buyerId, date);
        return isLedgerRowSettled(conn, BUYER_LEDGER_SETTLED_CHECK, clientId, buyerId, date);
    }

    @Override
    public void upsertFarmerOpeningBalance(Long clientId, String clientUsername, String farmerId, BigDecimal openingBalance, LocalDate openingBalanceDate) {
        logger.info("upsertFarmerOpeningBalance: clientId={}, farmerId={}, openingBalance={}, openingBalanceDate={}", clientId, farmerId, openingBalance, openingBalanceDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_FARMER_OB)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, farmerId);
            ps.setBigDecimal(4, openingBalance);
            ps.setDate(5, toSqlDate(openingBalanceDate));
            ps.setLong(6, clientId);
            ps.setString(7, farmerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("upsertFarmerOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to save farmer opening balance", e);
        }
    }

    @Override
    public void upsertBuyerOpeningBalance(Long clientId, String clientUsername, String buyerId, BigDecimal openingBalance, LocalDate openingBalanceDate) {
        logger.info("upsertBuyerOpeningBalance: clientId={}, buyerId={}, openingBalance={}, openingBalanceDate={}", clientId, buyerId, openingBalance, openingBalanceDate);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_BUYER_OB)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, buyerId);
            ps.setBigDecimal(4, openingBalance);
            ps.setDate(5, toSqlDate(openingBalanceDate));
            ps.setLong(6, clientId);
            ps.setString(7, buyerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("upsertBuyerOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to save buyer opening balance", e);
        }
    }

    @Override
    public void upsertFarmerOpeningBalance(Connection conn, Long clientId, String clientUsername, String farmerId,
                                           BigDecimal openingBalance, LocalDate openingBalanceDate) {
        logger.info("upsertFarmerOpeningBalance(conn): clientId={}, farmerId={}, openingBalance={}, openingBalanceDate={}", clientId, farmerId, openingBalance, openingBalanceDate);
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_FARMER_OB)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, farmerId);
            ps.setBigDecimal(4, openingBalance);
            ps.setDate(5, toSqlDate(openingBalanceDate));
            ps.setLong(6, clientId);
            ps.setString(7, farmerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("upsertFarmerOpeningBalance(conn): SQL exception", e);
            throw new RuntimeException("Failed to save farmer opening balance", e);
        }
    }

    @Override
    public void upsertBuyerOpeningBalance(Connection conn, Long clientId, String clientUsername, String buyerId,
                                          BigDecimal openingBalance, LocalDate openingBalanceDate) {
        logger.info("upsertBuyerOpeningBalance(conn): clientId={}, buyerId={}, openingBalance={}, openingBalanceDate={}", clientId, buyerId, openingBalance, openingBalanceDate);
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_BUYER_OB)) {
            ps.setLong(1, clientId);
            ps.setString(2, clientUsername);
            ps.setString(3, buyerId);
            ps.setBigDecimal(4, openingBalance);
            ps.setDate(5, toSqlDate(openingBalanceDate));
            ps.setLong(6, clientId);
            ps.setString(7, buyerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("upsertBuyerOpeningBalance(conn): SQL exception", e);
            throw new RuntimeException("Failed to save buyer opening balance", e);
        }
    }

    @Override
    public void clearFarmerOpeningBalance(Long clientId, String farmerId, Connection conn) {
        logger.info("clearFarmerOpeningBalance: clientId={}, farmerId={}", clientId, farmerId);
        try (PreparedStatement ps = conn.prepareStatement(CLEAR_FARMER_OB)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerId);
            int updated = ps.executeUpdate();
            logger.info("clearFarmerOpeningBalance: updated rows={}", updated);
        } catch (SQLException e) {
            logger.error("clearFarmerOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to clear farmer opening balance", e);
        }
    }

    @Override
    public void clearBuyerOpeningBalance(Long clientId, String buyerId, Connection conn) {
        logger.info("clearBuyerOpeningBalance: clientId={}, buyerId={}", clientId, buyerId);
        try (PreparedStatement ps = conn.prepareStatement(CLEAR_BUYER_OB)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerId);
            int updated = ps.executeUpdate();
            logger.info("clearBuyerOpeningBalance: updated rows={}", updated);
        } catch (SQLException e) {
            logger.error("clearBuyerOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to clear buyer opening balance", e);
        }
    }

    private boolean isLedgerRowSettled(Connection conn, String sql, Long clientId, String entityId, LocalDate date) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            ps.setString(2, entityId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            ps.setDate(4, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("isLedgerRowSettled: SQL exception", e);
            throw new RuntimeException("Failed to check settled ledger period", e);
        }
    }

    private BigDecimal queryOpeningBalance(Connection conn, String sql, Long clientId, String entityId) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            ps.setString(2, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("OPENING_BALANCE");
                }
            }
        } catch (SQLException e) {
            logger.error("queryOpeningBalance: SQL exception", e);
            throw new RuntimeException("Failed to fetch opening balance", e);
        }
        return null;
    }

    private LocalDate queryOpeningBalanceDate(Connection conn, String sql, Long clientId, String entityId) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            ps.setString(2, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Date date = rs.getDate("OPENING_BALANCE_DATE");
                    return date == null ? null : date.toLocalDate();
                }
            }
        } catch (SQLException e) {
            logger.error("queryOpeningBalanceDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch opening balance date", e);
        }
        return null;
    }

    private java.sql.Date toSqlDate(LocalDate date) {
        return date == null ? null : java.sql.Date.valueOf(date);
    }
}
