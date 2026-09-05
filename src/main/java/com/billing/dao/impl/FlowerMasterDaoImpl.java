package com.billing.dao.impl;

import com.billing.entity.FlowerMaster;
import com.billing.dao.ClientEntitySequenceDao;
import com.billing.dao.FlowerMasterDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class FlowerMasterDaoImpl implements FlowerMasterDao {

    private static final Logger logger = LoggerFactory.getLogger(FlowerMasterDaoImpl.class);

    private final DataSource dataSource;
    private final ClientEntitySequenceDao clientEntitySequenceDao;

    @Autowired
    public FlowerMasterDaoImpl(DataSource dataSource, ClientEntitySequenceDao clientEntitySequenceDao) {
        this.dataSource = dataSource;
        this.clientEntitySequenceDao = clientEntitySequenceDao;
    }

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_FLOWER_MASTER (FLOWER_ID, CLIENT_ID, CLIENT_USERNAME, FLOWER_NAME) VALUES (?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE BLOOMBUDDY_FLOWER_MASTER SET FLOWER_NAME = ? WHERE FLOWER_ID = ? AND CLIENT_ID = ?";

    private static final String SELECT_BY_ID_SQL =
            "SELECT FLOWER_ID, CLIENT_ID, CLIENT_USERNAME, FLOWER_NAME FROM BLOOMBUDDY_FLOWER_MASTER WHERE FLOWER_ID = ?";

    private static final String SELECT_BY_CLIENT_SQL =
            "SELECT FLOWER_ID, CLIENT_ID, CLIENT_USERNAME, FLOWER_NAME FROM BLOOMBUDDY_FLOWER_MASTER WHERE CLIENT_ID = ? ORDER BY FLOWER_NAME";

    private static final String CHECK_DUPLICATE_SQL =
            "SELECT COUNT(*) FROM BLOOMBUDDY_FLOWER_MASTER WHERE CLIENT_ID = ? AND FLOWER_NAME = ?";

    private static final String CHECK_DUPLICATE_EXCLUDE_ID_SQL =
            "SELECT COUNT(*) FROM BLOOMBUDDY_FLOWER_MASTER WHERE CLIENT_ID = ? AND FLOWER_NAME = ? AND FLOWER_ID != ?";

    private static final String DELETE_SQL =
            "DELETE FROM BLOOMBUDDY_FLOWER_MASTER WHERE FLOWER_ID = ? AND CLIENT_ID = ?";

    private static final String SELECT_NAME_BY_ID_SQL =
            "SELECT FLOWER_NAME FROM BLOOMBUDDY_FLOWER_MASTER WHERE FLOWER_ID = ? AND CLIENT_ID = ?";

    private static final String CASCADE_FLOWER_NAME_SALES_SQL =
            "UPDATE BLOOMBUDDY_SALES SET FLOWER_TYPE = ? WHERE FLOWER_TYPE = ? AND CLIENT_ID = ?";

    @Override
    public FlowerMaster save(FlowerMaster flowerMaster) {
        logger.info("save: flowerName={}, clientId={}, flowerId={}", flowerMaster.getFlowerName(), flowerMaster.getClientId(), flowerMaster.getFlowerId());
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            if (flowerMaster.getFlowerId() != null && !flowerMaster.getFlowerId().isEmpty()) {
                String oldName = null;
                try (PreparedStatement ps = conn.prepareStatement(SELECT_NAME_BY_ID_SQL)) {
                    ps.setString(1, flowerMaster.getFlowerId());
                    ps.setLong(2, flowerMaster.getClientId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            oldName = rs.getString("FLOWER_NAME");
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
                    ps.setString(1, flowerMaster.getFlowerName());
                    ps.setString(2, flowerMaster.getFlowerId());
                    ps.setLong(3, flowerMaster.getClientId());
                    ps.executeUpdate();
                }

                if (oldName != null && !oldName.equals(flowerMaster.getFlowerName())) {
                    logger.info("save: flower name changed from '{}' to '{}', cascading", oldName, flowerMaster.getFlowerName());
                    try (PreparedStatement ps = conn.prepareStatement(CASCADE_FLOWER_NAME_SALES_SQL)) {
                        ps.setString(1, flowerMaster.getFlowerName());
                        ps.setString(2, oldName);
                        ps.setLong(3, flowerMaster.getClientId());
                        int rows = ps.executeUpdate();
                        logger.debug("save: cascaded flower name to {} rows in SALES", rows);
                    }
                }

                logger.info("save: flower updated, flowerId={}", flowerMaster.getFlowerId());
            } else {
                String newFlowerId = generateNextFlowerId(conn, flowerMaster.getClientId(), flowerMaster.getClientUsername());
                flowerMaster.setFlowerId(newFlowerId);
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                    ps.setString(1, newFlowerId);
                    ps.setLong(2, flowerMaster.getClientId());
                    ps.setString(3, flowerMaster.getClientUsername());
                    ps.setString(4, flowerMaster.getFlowerName());
                    ps.executeUpdate();
                    logger.info("save: flower inserted, flowerId={}", newFlowerId);
                }
            }

            conn.commit();
        } catch (SQLException e) {
            logger.error("save: SQL exception, rolling back", e);
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ex) { logger.error("save: rollback failed", ex); }
            }
            throw new RuntimeException("Failed to save flower", e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ex) { logger.error("save: close failed", ex); }
            }
        }
        return flowerMaster;
    }

    private String generateNextFlowerId(Connection conn, Long clientId, String clientUsername) throws SQLException {
        int seqNo = clientEntitySequenceDao.nextSeqNumber(clientId, clientUsername, "Flower", conn);
        return clientUsername + "Flower" + seqNo;
    }

    @Override
    public FlowerMaster findById(String flowerId) {
        logger.info("findById: flowerId={}", flowerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setString(1, flowerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("findById: SQL exception", e);
            throw new RuntimeException("Failed to find flower", e);
        }
        return null;
    }

    @Override
    public List<FlowerMaster> findByClientId(Long clientId) {
        logger.info("findByClientId: clientId={}", clientId);
        List<FlowerMaster> list = new ArrayList<>();
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
            throw new RuntimeException("Failed to find flowers by client", e);
        }
        logger.info("findByClientId: count={}", list.size());
        return list;
    }

    @Override
    public boolean existsByClientIdAndFlowerName(Long clientId, String flowerName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CHECK_DUPLICATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, flowerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("existsByClientIdAndFlowerName: SQL exception", e);
            throw new RuntimeException("Failed to check flower duplicate", e);
        }
        return false;
    }

    @Override
    public boolean existsByClientIdAndFlowerNameExcludingId(Long clientId, String flowerName, String flowerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CHECK_DUPLICATE_EXCLUDE_ID_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, flowerName);
            ps.setString(3, flowerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("existsByClientIdAndFlowerNameExcludingId: SQL exception", e);
            throw new RuntimeException("Failed to check flower duplicate", e);
        }
        return false;
    }

    @Override
    public void deleteById(String flowerId, Long clientId) {
        logger.info("deleteById: flowerId={}, clientId={}", flowerId, clientId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, flowerId);
            ps.setLong(2, clientId);
            int rows = ps.executeUpdate();
            logger.info("deleteById: rowsAffected={}", rows);
        } catch (SQLException e) {
            logger.error("deleteById: SQL exception", e);
            throw new RuntimeException("Failed to delete flower", e);
        }
    }

    @Override
    public List<String> findNamesByClientId(Long clientId) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT FLOWER_NAME FROM BLOOMBUDDY_FLOWER_MASTER WHERE CLIENT_ID = ? ORDER BY FLOWER_NAME";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("FLOWER_NAME"));
                }
            }
        } catch (SQLException e) {
            logger.error("findNamesByClientId: SQL exception", e);
            throw new RuntimeException("Failed to fetch flower names", e);
        }
        return names;
    }

    private static final String SELECT_ID_BY_NAME_SQL =
            "SELECT FLOWER_ID FROM BLOOMBUDDY_FLOWER_MASTER WHERE CLIENT_ID = ? AND FLOWER_NAME = ? LIMIT 1";

    @Override
    public String findIdByNameAndClientId(Long clientId, String flowerName) {
        logger.info("findIdByNameAndClientId: clientId={}, flowerName={}", clientId, flowerName);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ID_BY_NAME_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, flowerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("FLOWER_ID");
                }
            }
        } catch (SQLException e) {
            logger.error("findIdByNameAndClientId: SQL exception", e);
            throw new RuntimeException("Failed to fetch flower id by name", e);
        }
        return null;
    }

    private FlowerMaster mapRow(ResultSet rs) throws SQLException {
        FlowerMaster f = new FlowerMaster();
        f.setFlowerId(rs.getString("FLOWER_ID"));
        f.setClientId(rs.getLong("CLIENT_ID"));
        f.setClientUsername(rs.getString("CLIENT_USERNAME"));
        f.setFlowerName(rs.getString("FLOWER_NAME"));
        return f;
    }
}
