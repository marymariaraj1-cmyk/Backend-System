package com.billing.dao.impl;

import com.billing.dao.CashBookFieldConfigDao;
import com.billing.entity.CashBookFieldConfig;
import com.billing.util.SalesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CashBookFieldConfigDaoImpl implements CashBookFieldConfigDao {

    private static final Logger logger = LoggerFactory.getLogger(CashBookFieldConfigDaoImpl.class);

    private static final String FIND_BY_CLIENT_SQL =
            "SELECT FIELD_CONFIG_ID, CLIENT_ID, CLIENT_USERNAME, FIELD_LABEL, FIELD_LABEL_TA, " +
            "FIELD_TYPE, DISPLAY_ORDER, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE " +
            "FROM BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "WHERE CLIENT_ID = ? ORDER BY FIELD_TYPE ASC, DISPLAY_ORDER ASC";

    private static final String FIND_BY_CLIENT_AND_TYPE_SQL =
            "SELECT FIELD_CONFIG_ID, CLIENT_ID, CLIENT_USERNAME, FIELD_LABEL, FIELD_LABEL_TA, " +
            "FIELD_TYPE, DISPLAY_ORDER, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE " +
            "FROM BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "WHERE CLIENT_ID = ? AND FIELD_TYPE = ? ORDER BY DISPLAY_ORDER ASC";

    private static final String FIND_BY_ID_AND_CLIENT_SQL =
            "SELECT FIELD_CONFIG_ID, CLIENT_ID, CLIENT_USERNAME, FIELD_LABEL, FIELD_LABEL_TA, " +
            "FIELD_TYPE, DISPLAY_ORDER, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE " +
            "FROM BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "WHERE FIELD_CONFIG_ID = ? AND CLIENT_ID = ?";

    private static final String FIND_IDS_BY_CLIENT_AND_TYPE_SQL =
            "SELECT FIELD_CONFIG_ID FROM BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "WHERE CLIENT_ID = ? AND FIELD_TYPE = ?";

    private static final String COUNT_BY_CLIENT_AND_TYPE_SQL =
            "SELECT COUNT(*) FROM BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "WHERE CLIENT_ID = ? AND FIELD_TYPE = ?";

    private static final String COUNT_BY_CLIENT_AND_TYPE_AND_LABEL_SQL =
            "SELECT COUNT(*) FROM BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "WHERE CLIENT_ID = ? AND FIELD_TYPE = ? AND LOWER(FIELD_LABEL) = LOWER(?)";

    private static final String MAX_DISPLAY_ORDER_SQL =
            "SELECT COALESCE(MAX(DISPLAY_ORDER), 0) FROM BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "WHERE CLIENT_ID = ? AND FIELD_TYPE = ?";

    private static final String MANUAL_VALUE_ROWS_SQL =
            "SELECT BOOK_DATE, MANUAL_DEBIT_VALUES, MANUAL_CREDIT_VALUES " +
            "FROM BLOOMBUDDY_DAILY_CASH_BOOK " +
            "WHERE CLIENT_ID = ? AND (MANUAL_DEBIT_VALUES IS NOT NULL OR MANUAL_CREDIT_VALUES IS NOT NULL)";

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "(CLIENT_ID, CLIENT_USERNAME, FIELD_LABEL, FIELD_LABEL_TA, FIELD_TYPE, DISPLAY_ORDER, " +
            "CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "SET FIELD_LABEL = ?, FIELD_LABEL_TA = ?, UPDATED_BY = ?, UPDATED_DATE = ? " +
            "WHERE FIELD_CONFIG_ID = ? AND CLIENT_ID = ?";

    private static final String DELETE_SQL =
            "DELETE FROM BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG " +
            "WHERE FIELD_CONFIG_ID = ? AND CLIENT_ID = ?";

    private static final String UPDATE_DISPLAY_ORDER_SQL =
            "UPDATE BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG SET DISPLAY_ORDER = ? " +
            "WHERE FIELD_CONFIG_ID = ? AND CLIENT_ID = ?";

    private final DataSource dataSource;

    public CashBookFieldConfigDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<CashBookFieldConfig> findByClientId(Long clientId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_CLIENT_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CashBookFieldConfig> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig findByClientId: SQL exception", e);
            throw new RuntimeException("Failed to load field configuration", e);
        }
    }

    @Override
    public List<CashBookFieldConfig> findByClientIdAndType(Long clientId, String fieldType) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_CLIENT_AND_TYPE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, fieldType);
            try (ResultSet rs = ps.executeQuery()) {
                List<CashBookFieldConfig> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig findByClientIdAndType: SQL exception", e);
            throw new RuntimeException("Failed to load field configuration", e);
        }
    }

    @Override
    public CashBookFieldConfig findByIdAndClientId(Long fieldConfigId, Long clientId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_AND_CLIENT_SQL)) {
            ps.setLong(1, fieldConfigId);
            ps.setLong(2, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig findByIdAndClientId: SQL exception", e);
            throw new RuntimeException("Failed to load field configuration", e);
        }
        return null;
    }

    @Override
    public CashBookFieldConfig findByIdAndClientId(Long fieldConfigId, Long clientId, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_AND_CLIENT_SQL)) {
            ps.setLong(1, fieldConfigId);
            ps.setLong(2, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig findByIdAndClientId (conn): SQL exception", e);
            throw new RuntimeException("Failed to load field configuration", e);
        }
        return null;
    }

    @Override
    public List<Long> findIdsByClientIdAndType(Long clientId, String fieldType) {
        List<Long> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_IDS_BY_CLIENT_AND_TYPE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, fieldType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("FIELD_CONFIG_ID"));
                }
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig findIdsByClientIdAndType: SQL exception", e);
            throw new RuntimeException("Failed to load field configuration", e);
        }
        return ids;
    }

    @Override
    public int countByClientIdAndType(Long clientId, String fieldType) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_BY_CLIENT_AND_TYPE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, fieldType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig countByClientIdAndType: SQL exception", e);
            throw new RuntimeException("Failed to load field configuration", e);
        }
        return 0;
    }

    @Override
    public int countByClientIdAndTypeAndLabel(Long clientId, String fieldType, String label) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_BY_CLIENT_AND_TYPE_AND_LABEL_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, fieldType);
            ps.setString(3, label);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig countByClientIdAndTypeAndLabel: SQL exception", e);
            throw new RuntimeException("Failed to load field configuration", e);
        }
        return 0;
    }

    @Override
    public Integer maxDisplayOrder(Long clientId, String fieldType) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(MAX_DISPLAY_ORDER_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, fieldType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig maxDisplayOrder: SQL exception", e);
            throw new RuntimeException("Failed to load field configuration", e);
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> findManualValueRowsByClient(Long clientId) {
        try (Connection conn = dataSource.getConnection()) {
            return findManualValueRowsByClient(clientId, conn);
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig findManualValueRowsByClient: SQL exception", e);
            throw new RuntimeException("Failed to scan saved cash book values", e);
        }
    }

    @Override
    public List<Map<String, Object>> findManualValueRowsByClient(Long clientId, Connection conn) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(MANUAL_VALUE_ROWS_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bookDate", rs.getDate("BOOK_DATE").toLocalDate());
                    row.put("manualDebitValues", rs.getString("MANUAL_DEBIT_VALUES"));
                    row.put("manualCreditValues", rs.getString("MANUAL_CREDIT_VALUES"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig findManualValueRowsByClient (conn): SQL exception", e);
            throw new RuntimeException("Failed to scan saved cash book values", e);
        }
        return rows;
    }

    @Override
    public Long insert(CashBookFieldConfig config, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, config.getClientId());
            ps.setString(2, config.getClientUsername());
            ps.setString(3, config.getFieldLabel());
            ps.setString(4, config.getFieldLabelTa());
            ps.setString(5, config.getFieldType());
            ps.setInt(6, config.getDisplayOrder());
            ps.setString(7, config.getCreatedBy());
            ps.setTimestamp(8, java.sql.Timestamp.valueOf(config.getCreatedDate()));
            ps.setString(9, config.getCreatedBy());
            ps.setTimestamp(10, java.sql.Timestamp.valueOf(config.getCreatedDate()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig insert: SQL exception", e);
            throw new RuntimeException("Failed to save field configuration", e);
        }
        throw new RuntimeException("Failed to save field configuration");
    }

    @Override
    public int update(CashBookFieldConfig config, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, config.getFieldLabel());
            ps.setString(2, config.getFieldLabelTa());
            ps.setString(3, config.getUpdatedBy());
            ps.setTimestamp(4, java.sql.Timestamp.valueOf(config.getUpdatedDate()));
            ps.setLong(5, config.getFieldConfigId());
            ps.setLong(6, config.getClientId());
            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig update: SQL exception", e);
            throw new RuntimeException("Failed to update field configuration", e);
        }
    }

    @Override
    public int deleteByIdAndClientId(Long fieldConfigId, Long clientId, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setLong(1, fieldConfigId);
            ps.setLong(2, clientId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig deleteByIdAndClientId: SQL exception", e);
            throw new RuntimeException("Failed to delete field configuration", e);
        }
    }

    @Override
    public void updateDisplayOrder(Long fieldConfigId, Long clientId, int displayOrder, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_DISPLAY_ORDER_SQL)) {
            ps.setInt(1, displayOrder);
            ps.setLong(2, fieldConfigId);
            ps.setLong(3, clientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("CashBookFieldConfig updateDisplayOrder: SQL exception", e);
            throw new RuntimeException("Failed to reorder field configuration", e);
        }
    }

    private CashBookFieldConfig mapRow(ResultSet rs) throws SQLException {
        CashBookFieldConfig config = new CashBookFieldConfig();
        config.setFieldConfigId(rs.getLong("FIELD_CONFIG_ID"));
        config.setClientId(rs.getLong("CLIENT_ID"));
        config.setClientUsername(rs.getString("CLIENT_USERNAME"));
        config.setFieldLabel(rs.getString("FIELD_LABEL"));
        config.setFieldLabelTa(rs.getString("FIELD_LABEL_TA"));
        config.setFieldType(rs.getString("FIELD_TYPE"));
        config.setDisplayOrder(rs.getInt("DISPLAY_ORDER"));
        config.setCreatedBy(rs.getString("CREATED_BY"));
        java.sql.Timestamp created = rs.getTimestamp("CREATED_DATE");
        config.setCreatedDate(created != null ? created.toLocalDateTime() : null);
        config.setUpdatedBy(rs.getString("UPDATED_BY"));
        java.sql.Timestamp updated = rs.getTimestamp("UPDATED_DATE");
        config.setUpdatedDate(updated != null ? updated.toLocalDateTime() : SalesUtil.now());
        return config;
    }
}