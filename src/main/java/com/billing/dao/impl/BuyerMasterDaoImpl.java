package com.billing.dao.impl;

import com.billing.entity.BuyerMaster;
import com.billing.dao.ClientEntitySequenceDao;
import com.billing.dao.BuyerMasterDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BuyerMasterDaoImpl implements BuyerMasterDao {

    private static final Logger logger = LoggerFactory.getLogger(BuyerMasterDaoImpl.class);

    private final DataSource dataSource;
    private final ClientEntitySequenceDao clientEntitySequenceDao;

    @Autowired
    public BuyerMasterDaoImpl(DataSource dataSource, ClientEntitySequenceDao clientEntitySequenceDao) {
        this.dataSource = dataSource;
        this.clientEntitySequenceDao = clientEntitySequenceDao;
    }

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_BUYER_MASTER (BUYER_ID, CLIENT_ID, CLIENT_USERNAME, BUYER_NAME, BUYER_CONTACT_NO, BUYER_ADDRESS) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE BLOOMBUDDY_BUYER_MASTER SET BUYER_NAME = ?, BUYER_CONTACT_NO = ?, BUYER_ADDRESS = ? WHERE BUYER_ID = ?";

    private static final String SELECT_BY_ID_SQL =
            "SELECT BUYER_ID, CLIENT_ID, CLIENT_USERNAME, BUYER_NAME, BUYER_CONTACT_NO, BUYER_ADDRESS FROM BLOOMBUDDY_BUYER_MASTER WHERE BUYER_ID = ?";

    private static final String SELECT_BY_CLIENT_SQL =
            "SELECT BUYER_ID, CLIENT_ID, CLIENT_USERNAME, BUYER_NAME, BUYER_CONTACT_NO, BUYER_ADDRESS FROM BLOOMBUDDY_BUYER_MASTER WHERE CLIENT_ID = ? ORDER BY BUYER_NAME";

    private static final String CHECK_DUPLICATE_SQL =
            "SELECT COUNT(*) FROM BLOOMBUDDY_BUYER_MASTER WHERE CLIENT_ID = ? AND BUYER_NAME = ?";

    private static final String CHECK_DUPLICATE_EXCLUDE_ID_SQL =
            "SELECT COUNT(*) FROM BLOOMBUDDY_BUYER_MASTER WHERE CLIENT_ID = ? AND BUYER_NAME = ? AND BUYER_ID != ?";

    private static final String DELETE_SQL =
            "DELETE FROM BLOOMBUDDY_BUYER_MASTER WHERE BUYER_ID = ? AND CLIENT_ID = ?";

    private static final String SELECT_NAME_BY_ID_SQL =
            "SELECT BUYER_NAME FROM BLOOMBUDDY_BUYER_MASTER WHERE BUYER_ID = ?";

    private static final String CASCADE_BUYER_NAME_LEDGER_SQL =
            "UPDATE BLOOMBUDDY_BUYER_LEDGER SET BUYER_NAME = ? WHERE BUYER_ID = ? AND CLIENT_ID = ?";

    private static final String CASCADE_BUYER_NAME_TXN_SQL =
            "UPDATE BLOOMBUDDY_BUYER_TRANSACTION SET BUYER_NAME = ? WHERE BUYER_ID = ? AND CLIENT_ID = ?";

    @Override
    public BuyerMaster save(BuyerMaster buyerMaster) {
        logger.info("save: buyerName={}, clientId={}, buyerId={}", buyerMaster.getBuyerName(), buyerMaster.getClientId(), buyerMaster.getBuyerId());
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            if (buyerMaster.getBuyerId() != null && !buyerMaster.getBuyerId().isEmpty()) {
                String oldName = null;
                try (PreparedStatement ps = conn.prepareStatement(SELECT_NAME_BY_ID_SQL)) {
                    ps.setString(1, buyerMaster.getBuyerId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            oldName = rs.getString("BUYER_NAME");
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
                    ps.setString(1, buyerMaster.getBuyerName());
                    ps.setString(2, buyerMaster.getBuyerContactNo());
                    ps.setString(3, buyerMaster.getBuyerAddress());
                    ps.setString(4, buyerMaster.getBuyerId());
                    ps.executeUpdate();
                }

                if (oldName != null && !oldName.equals(buyerMaster.getBuyerName())) {
                    logger.info("save: buyer name changed from '{}' to '{}', cascading", oldName, buyerMaster.getBuyerName());
                    String newName = buyerMaster.getBuyerName();
                    String buyerId = buyerMaster.getBuyerId();
                    Long clientId = buyerMaster.getClientId();

                    String[] cascadeSqls = {CASCADE_BUYER_NAME_LEDGER_SQL, CASCADE_BUYER_NAME_TXN_SQL};
                    for (String sql : cascadeSqls) {
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setString(1, newName);
                            ps.setString(2, buyerId);
                            ps.setLong(3, clientId);
                            int rows = ps.executeUpdate();
                            logger.debug("save: cascaded buyer name to {} rows", rows);
                        }
                    }
                }

                logger.info("save: buyer updated, buyerId={}", buyerMaster.getBuyerId());
            } else {
                String newBuyerId = generateNextBuyerId(conn, buyerMaster.getClientId(), buyerMaster.getClientUsername());
                buyerMaster.setBuyerId(newBuyerId);
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                    ps.setString(1, newBuyerId);
                    ps.setLong(2, buyerMaster.getClientId());
                    ps.setString(3, buyerMaster.getClientUsername());
                    ps.setString(4, buyerMaster.getBuyerName());
                    ps.setString(5, buyerMaster.getBuyerContactNo());
                    ps.setString(6, buyerMaster.getBuyerAddress());
                    ps.executeUpdate();
                    logger.info("save: buyer inserted successfully, buyerId={}", newBuyerId);
                }
            }

            conn.commit();
        } catch (SQLException e) {
            logger.error("save: SQL exception, rolling back", e);
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ex) { logger.error("save: rollback failed", ex); }
            }
            throw new RuntimeException("Failed to save buyer", e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ex) { logger.error("save: close failed", ex); }
            }
        }
        return buyerMaster;
    }

    private String generateNextBuyerId(Connection conn, Long clientId, String clientUsername) throws SQLException {
        int seqNo = clientEntitySequenceDao.nextSeqNumber(clientId, clientUsername, "Buyer", conn);
        return clientUsername + "Buyer" + seqNo;
    }

    @Override
    public BuyerMaster findById(String buyerId) {
        logger.info("findById: buyerId={}", buyerId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setString(1, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("findById: SQL exception", e);
            throw new RuntimeException("Failed to find buyer", e);
        }
        return null;
    }

    @Override
    public List<BuyerMaster> findByClientId(Long clientId) {
        logger.info("findByClientId: clientId={}", clientId);
        List<BuyerMaster> list = new ArrayList<>();
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
            throw new RuntimeException("Failed to find buyers by client", e);
        }
        logger.info("findByClientId: count={}", list.size());
        return list;
    }

    @Override
    public boolean existsByClientIdAndBuyerName(Long clientId, String buyerName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CHECK_DUPLICATE_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("existsByClientIdAndBuyerName: SQL exception", e);
            throw new RuntimeException("Failed to check buyer duplicate", e);
        }
        return false;
    }

    @Override
    public boolean existsByClientIdAndBuyerNameExcludingId(Long clientId, String buyerName, String buyerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CHECK_DUPLICATE_EXCLUDE_ID_SQL)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerName);
            ps.setString(3, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("existsByClientIdAndBuyerNameExcludingId: SQL exception", e);
            throw new RuntimeException("Failed to check buyer duplicate", e);
        }
        return false;
    }

    @Override
    public void deleteById(String buyerId, Long clientId) {
        logger.info("deleteById: buyerId={}, clientId={}", buyerId, clientId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, buyerId);
            ps.setLong(2, clientId);
            int rows = ps.executeUpdate();
            logger.info("deleteById: rowsAffected={}", rows);
        } catch (SQLException e) {
            logger.error("deleteById: SQL exception", e);
            throw new RuntimeException("Failed to delete buyer", e);
        }
    }

    @Override
    public List<String> findNamesByClientId(Long clientId) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT BUYER_NAME FROM BLOOMBUDDY_BUYER_MASTER WHERE CLIENT_ID = ? ORDER BY BUYER_NAME";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("BUYER_NAME"));
                }
            }
        } catch (SQLException e) {
            logger.error("findNamesByClientId: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer names", e);
        }
        return names;
    }

    @Override
    public String findIdByNameAndClientId(Long clientId, String buyerName) {
        String sql = "SELECT BUYER_ID FROM BLOOMBUDDY_BUYER_MASTER WHERE CLIENT_ID = ? AND BUYER_NAME = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            ps.setString(2, buyerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("BUYER_ID");
                }
            }
        } catch (SQLException e) {
            logger.error("findIdByNameAndClientId: SQL exception", e);
            throw new RuntimeException("Failed to fetch buyer ID by name", e);
        }
        return null;
    }

    private BuyerMaster mapRow(ResultSet rs) throws SQLException {
        BuyerMaster b = new BuyerMaster();
        b.setBuyerId(rs.getString("BUYER_ID"));
        b.setClientId(rs.getLong("CLIENT_ID"));
        b.setClientUsername(rs.getString("CLIENT_USERNAME"));
        b.setBuyerName(rs.getString("BUYER_NAME"));
        b.setBuyerContactNo(rs.getString("BUYER_CONTACT_NO"));
        b.setBuyerAddress(rs.getString("BUYER_ADDRESS"));
        return b;
    }
}
