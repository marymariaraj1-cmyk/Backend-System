package com.billing.dao.impl;

import com.billing.entity.FarmerMaster;
import com.billing.dao.ClientEntitySequenceDao;
import com.billing.dao.FarmerMasterDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class FarmerMasterDaoImpl implements FarmerMasterDao {

    private static final Logger logger = LoggerFactory.getLogger(FarmerMasterDaoImpl.class);

    private final DataSource dataSource;
    private final ClientEntitySequenceDao clientEntitySequenceDao;

    @Autowired
    public FarmerMasterDaoImpl(DataSource dataSource, ClientEntitySequenceDao clientEntitySequenceDao) {
        this.dataSource = dataSource;
        this.clientEntitySequenceDao = clientEntitySequenceDao;
    }

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_FARMER_MASTER (FARMER_ID, CLIENT_ID, CLIENT_USERNAME, FARMER_NAME, FARMER_CONTACT_NO, FARMER_ADDRESS) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE BLOOMBUDDY_FARMER_MASTER SET FARMER_NAME = ?, FARMER_CONTACT_NO = ?, FARMER_ADDRESS = ? WHERE FARMER_ID = ?";

    private static final String SELECT_BY_ID_SQL =
            "SELECT FARMER_ID, CLIENT_ID, CLIENT_USERNAME, FARMER_NAME, FARMER_CONTACT_NO, FARMER_ADDRESS FROM BLOOMBUDDY_FARMER_MASTER WHERE FARMER_ID = ?";

    private static final String SELECT_BY_CLIENT_SQL =
            "SELECT FARMER_ID, CLIENT_ID, CLIENT_USERNAME, FARMER_NAME, FARMER_CONTACT_NO, FARMER_ADDRESS FROM BLOOMBUDDY_FARMER_MASTER WHERE CLIENT_ID = ? ORDER BY FARMER_NAME";

    private static final String CHECK_DUPLICATE_SQL =
            "SELECT COUNT(*) FROM BLOOMBUDDY_FARMER_MASTER WHERE CLIENT_ID = ? AND FARMER_NAME = ?";

    private static final String CHECK_DUPLICATE_EXCLUDE_ID_SQL =
            "SELECT COUNT(*) FROM BLOOMBUDDY_FARMER_MASTER WHERE CLIENT_ID = ? AND FARMER_NAME = ? AND FARMER_ID != ?";

    private static final String DELETE_SQL =
            "DELETE FROM BLOOMBUDDY_FARMER_MASTER WHERE FARMER_ID = ? AND CLIENT_ID = ?";

    private static final String SELECT_NAME_BY_ID_SQL =
            "SELECT FARMER_NAME FROM BLOOMBUDDY_FARMER_MASTER WHERE FARMER_ID = ?";

    private static final String CASCADE_FARMER_NAME_SQL =
            "UPDATE BLOOMBUDDY_SALES SET FARMER_NAME = ? WHERE FARMER_ID = ? AND CLIENT_ID = ?";

    private static final String CASCADE_FARMER_NAME_LEDGER_SQL =
            "UPDATE BLOOMBUDDY_FARMER_LEDGER SET FARMER_NAME = ? WHERE FARMER_ID = ? AND CLIENT_ID = ?";

    private static final String CASCADE_FARMER_NAME_TXN_SQL =
            "UPDATE BLOOMBUDDY_FARMER_TRANSACTION SET FARMER_NAME = ? WHERE FARMER_ID = ? AND CLIENT_ID = ?";

    @Override
    public FarmerMaster save(FarmerMaster farmerMaster) {
        logger.info("save: farmerName={}, clientId={}, farmerId={}", farmerMaster.getFarmerName(), farmerMaster.getClientId(), farmerMaster.getFarmerId());
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            if (farmerMaster.getFarmerId() != null && !farmerMaster.getFarmerId().isEmpty()) {
                String oldName = null;
                try (PreparedStatement ps = conn.prepareStatement(SELECT_NAME_BY_ID_SQL)) {
                    ps.setString(1, farmerMaster.getFarmerId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            oldName = rs.getString("FARMER_NAME");
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
                    ps.setString(1, farmerMaster.getFarmerName());
                    ps.setString(2, farmerMaster.getFarmerContactNo());
                    ps.setString(3, farmerMaster.getFarmerAddress());
                    ps.setString(4, farmerMaster.getFarmerId());
                    ps.executeUpdate();
                }

                if (oldName != null && !oldName.equals(farmerMaster.getFarmerName())) {
                    logger.info("save: farmer name changed from '{}' to '{}', cascading", oldName, farmerMaster.getFarmerName());
                    String newName = farmerMaster.getFarmerName();
                    String farmerId = farmerMaster.getFarmerId();
                    Long clientId = farmerMaster.getClientId();

                    String[] cascadeSqls = {CASCADE_FARMER_NAME_SQL, CASCADE_FARMER_NAME_LEDGER_SQL, CASCADE_FARMER_NAME_TXN_SQL};
                    for (String sql : cascadeSqls) {
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setString(1, newName);
                            ps.setString(2, farmerId);
                            ps.setLong(3, clientId);
                            int rows = ps.executeUpdate();
                            logger.debug("save: cascaded farmer name to {} rows", rows);
                        }
                    }
                }

                logger.info("save: farmer updated, farmerId={}", farmerMaster.getFarmerId());
            } else {
                String newFarmerId = generateNextFarmerId(conn, farmerMaster.getClientId(), farmerMaster.getClientUsername());
                farmerMaster.setFarmerId(newFarmerId);
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                    ps.setString(1, newFarmerId);
                    ps.setLong(2, farmerMaster.getClientId());
                    ps.setString(3, farmerMaster.getClientUsername());
                    ps.setString(4, farmerMaster.getFarmerName());
                    ps.setString(5, farmerMaster.getFarmerContactNo());
                    ps.setString(6, farmerMaster.getFarmerAddress());
                    ps.executeUpdate();
                    logger.info("save: farmer inserted successfully, farmerId={}", newFarmerId);
                }
            }

            conn.commit();
        } catch (SQLException e) {
            logger.error("save: SQL exception, rolling back", e);
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ex) { logger.error("save: rollback failed", ex); }
            }
            throw new RuntimeException("Failed to save farmer", e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ex) { logger.error("save: close failed", ex); }
            }
        }
        return farmerMaster;
    }

    private String generateNextFarmerId(Connection conn, Long clientId, String clientUsername) throws SQLException {
        int seqNo = clientEntitySequenceDao.nextSeqNumber(clientId, clientUsername, "Farmer", conn);
        return clientUsername + "Farmer" + seqNo;
    }

    @Override
    public FarmerMaster findById(String farmerId) {
        logger.info("findById: farmerId={}", farmerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setString(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("findById: SQL exception", e);
            throw new RuntimeException("Failed to find farmer", e);
        }
        return null;
    }

    @Override
    public List<FarmerMaster> findByClientId(Long clientId) {
        logger.info("findByClientId: clientId={}", clientId);
        List<FarmerMaster> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_CLIENT_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findByClientId: SQL exception", e);
            throw new RuntimeException("Failed to find farmers by client", e);
        }
        logger.info("findByClientId: count={}", list.size());
        return list;
    }

    @Override
    public boolean existsByClientIdAndFarmerName(Long clientId, String farmerName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CHECK_DUPLICATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("existsByClientIdAndFarmerName: SQL exception", e);
            throw new RuntimeException("Failed to check farmer duplicate", e);
        }
        return false;
    }

    @Override
    public boolean existsByClientIdAndFarmerNameExcludingId(Long clientId, String farmerName, String farmerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CHECK_DUPLICATE_EXCLUDE_ID_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerName);
            ps.setString(3, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("existsByClientIdAndFarmerNameExcludingId: SQL exception", e);
            throw new RuntimeException("Failed to check farmer duplicate", e);
        }
        return false;
    }

    @Override
    public void deleteById(String farmerId, Long clientId) {
        logger.info("deleteById: farmerId={}, clientId={}", farmerId, clientId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, farmerId);
            ps.setLong(2, clientId);
            int rows = ps.executeUpdate();
            logger.info("deleteById: rowsAffected={}", rows);
        } catch (SQLException e) {
            logger.error("deleteById: SQL exception", e);
            throw new RuntimeException("Failed to delete farmer", e);
        }
    }

    @Override
    public List<String> findNamesByClientId(Long clientId) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT FARMER_NAME FROM BLOOMBUDDY_FARMER_MASTER WHERE CLIENT_ID = ? ORDER BY FARMER_NAME";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("FARMER_NAME"));
                }
            }
        } catch (SQLException e) {
            logger.error("findNamesByClientId: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer names", e);
        }
        return names;
    }

    @Override
    public String findIdByNameAndClientId(Long clientId, String farmerName) {
        String sql = "SELECT FARMER_ID FROM BLOOMBUDDY_FARMER_MASTER WHERE CLIENT_ID = ? AND FARMER_NAME = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            ps.setString(2, farmerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("FARMER_ID");
                }
            }
        } catch (SQLException e) {
            logger.error("findIdByNameAndClientId: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer ID by name", e);
        }
        return null;
    }

    private FarmerMaster mapRow(ResultSet rs) throws SQLException {
        FarmerMaster f = new FarmerMaster();
        f.setFarmerId(rs.getString("FARMER_ID"));
        f.setClientId(rs.getLong("CLIENT_ID"));
        f.setClientUsername(rs.getString("CLIENT_USERNAME"));
        f.setFarmerName(rs.getString("FARMER_NAME"));
        f.setFarmerContactNo(rs.getString("FARMER_CONTACT_NO"));
        f.setFarmerAddress(rs.getString("FARMER_ADDRESS"));
        return f;
    }
}
