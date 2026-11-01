package com.billing.dao.impl;
import com.billing.dao.FarmerCommissionConfigDao;
import com.billing.entity.FarmerCommissionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Repository
public class FarmerCommissionConfigDaoImpl implements FarmerCommissionConfigDao {
    private static final Logger logger = LoggerFactory.getLogger(FarmerCommissionConfigDaoImpl.class);
    private final DataSource dataSource;
    private static final String FIND_BY_CLIENT_SQL = "SELECT COMMISSION_CONFIG_ID, CLIENT_ID, CLIENT_USERNAME, COMMISSION_PERCENT, DESCRIPTION, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE FROM BLOOMBUDDY_FARMER_COMMISSION_PERCENTAGE WHERE CLIENT_ID = ?";
    private static final String FIND_ALL_SQL = "SELECT COMMISSION_CONFIG_ID, CLIENT_ID, CLIENT_USERNAME, COMMISSION_PERCENT, DESCRIPTION, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE FROM BLOOMBUDDY_FARMER_COMMISSION_PERCENTAGE WHERE CLIENT_ID = ? ORDER BY COMMISSION_CONFIG_ID DESC";
    private static final String UPSERT_SQL = "INSERT INTO BLOOMBUDDY_FARMER_COMMISSION_PERCENTAGE (CLIENT_ID, CLIENT_USERNAME, COMMISSION_PERCENT, DESCRIPTION, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE CLIENT_USERNAME=VALUES(CLIENT_USERNAME), COMMISSION_PERCENT=VALUES(COMMISSION_PERCENT), DESCRIPTION=VALUES(DESCRIPTION), UPDATED_BY=VALUES(UPDATED_BY), UPDATED_DATE=VALUES(UPDATED_DATE)";
    @Autowired
    public FarmerCommissionConfigDaoImpl(DataSource dataSource) { this.dataSource = dataSource; }
    @Override public FarmerCommissionConfig findByClientId(Long clientId, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_CLIENT_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return mapRow(rs); }
        } catch (SQLException e) { logger.error("findByClientId: SQL exception", e); throw new RuntimeException("Failed", e); }
        return null;
    }
    @Override public FarmerCommissionConfig findByClientId(Long clientId) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(FIND_BY_CLIENT_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return mapRow(rs); }
        } catch (SQLException e) { logger.error("findByClientId: SQL exception", e); throw new RuntimeException("Failed", e); }
        return null;
    }
    @Override public List<FarmerCommissionConfig> findAllByClientId(Long clientId) {
        List<FarmerCommissionConfig> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        } catch (SQLException e) { logger.error("findAllByClientId: SQL exception", e); throw new RuntimeException("Failed", e); }
        return list;
    }
    @Override public void saveOrUpdate(FarmerCommissionConfig config, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            ps.setLong(1, config.getClientId());
            ps.setString(2, config.getClientUsername());
            ps.setBigDecimal(3, config.getCommissionPercent());
            ps.setString(4, config.getDescription());
            ps.setString(5, config.getCreatedBy() != null ? config.getCreatedBy() : config.getClientUsername());
            ps.setTimestamp(6, config.getCreatedDate() != null ? Timestamp.valueOf(config.getCreatedDate()) : now);
            ps.setString(7, config.getUpdatedBy() != null ? config.getUpdatedBy() : config.getClientUsername());
            ps.setTimestamp(8, now);
            ps.executeUpdate();
        } catch (SQLException e) { logger.error("saveOrUpdate: SQL exception", e); throw new RuntimeException("Failed", e); }
    }
    @Override public void saveOrUpdate(FarmerCommissionConfig config) {
        try (Connection conn = dataSource.getConnection()) { saveOrUpdate(config, conn); } catch (SQLException e) { logger.error("saveOrUpdate: SQL exception", e); throw new RuntimeException("Failed", e); }
    }
    private FarmerCommissionConfig mapRow(ResultSet rs) throws SQLException {
        FarmerCommissionConfig c = new FarmerCommissionConfig();
        c.setCommissionConfigId(rs.getLong("COMMISSION_CONFIG_ID"));
        c.setClientId(rs.getLong("CLIENT_ID"));
        c.setClientUsername(rs.getString("CLIENT_USERNAME"));
        c.setCommissionPercent(rs.getBigDecimal("COMMISSION_PERCENT"));
        c.setDescription(rs.getString("DESCRIPTION"));
        c.setCreatedBy(rs.getString("CREATED_BY"));
        if (rs.getTimestamp("CREATED_DATE") != null) c.setCreatedDate(rs.getTimestamp("CREATED_DATE").toLocalDateTime());
        c.setUpdatedBy(rs.getString("UPDATED_BY"));
        if (rs.getTimestamp("UPDATED_DATE") != null) c.setUpdatedDate(rs.getTimestamp("UPDATED_DATE").toLocalDateTime());
        return c;
    }
}