package com.billing.dao.impl;

import com.billing.entity.ClientMaster;
import com.billing.dao.ClientMasterDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ClientMasterDaoImpl implements ClientMasterDao {

    private static final Logger logger = LoggerFactory.getLogger(ClientMasterDaoImpl.class);

    private final DataSource dataSource;

    @Autowired
    public ClientMasterDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String INSERT_SQL =
            "INSERT INTO BLOOMBUDDY_CLIENT_MASTER (CLIENT_USERNAME, CLIENT_PASSWORD, CLIENT_SHOP_NAME, CLIENT_SHOP_ADDRESS, CLIENT_CONTACT_NO, CLIENT_MAIL_ID) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_ID_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, CLIENT_PASSWORD, CLIENT_SHOP_NAME, CLIENT_SHOP_ADDRESS, CLIENT_CONTACT_NO, CLIENT_MAIL_ID FROM BLOOMBUDDY_CLIENT_MASTER WHERE CLIENT_ID = ?";

    private static final String SELECT_BY_USERNAME_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, CLIENT_PASSWORD, CLIENT_SHOP_NAME, CLIENT_SHOP_ADDRESS, CLIENT_CONTACT_NO, CLIENT_MAIL_ID FROM BLOOMBUDDY_CLIENT_MASTER WHERE CLIENT_USERNAME = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT CLIENT_ID, CLIENT_USERNAME, CLIENT_PASSWORD, CLIENT_SHOP_NAME, CLIENT_SHOP_ADDRESS, CLIENT_CONTACT_NO, CLIENT_MAIL_ID FROM BLOOMBUDDY_CLIENT_MASTER ORDER BY CLIENT_SHOP_NAME";

    private static final String UPDATE_SQL =
            "UPDATE BLOOMBUDDY_CLIENT_MASTER SET CLIENT_USERNAME=?, CLIENT_PASSWORD=?, CLIENT_SHOP_NAME=?, CLIENT_SHOP_ADDRESS=?, CLIENT_CONTACT_NO=?, CLIENT_MAIL_ID=? WHERE CLIENT_ID=?";

    private static final String DELETE_SQL =
            "DELETE FROM BLOOMBUDDY_CLIENT_MASTER WHERE CLIENT_ID=?";

    private static final String CASCADING_TABLES_SQL =
            "UPDATE BLOOMBUDDY_FARMER_MASTER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?; "
            + "UPDATE BLOOMBUDDY_BUYER_MASTER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?; "
            + "UPDATE BLOOMBUDDY_FLOWER_MASTER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?; "
            + "UPDATE BLOOMBUDDY_SALES SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?; "
            + "UPDATE BLOOMBUDDY_FARMER_LEDGER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?; "
            + "UPDATE BLOOMBUDDY_BUYER_LEDGER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?; "
            + "UPDATE BLOOMBUDDY_FARMER_TRANSACTION SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?; "
            + "UPDATE BLOOMBUDDY_BUYER_TRANSACTION SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?; "
            + "UPDATE BLOOMBUDDY_CLIENT_SLOT_SEQUENCE SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?; "
            + "UPDATE BLOOMBUDDY_CLIENT_REPORT SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?";

    private static final String[] CASCADING_UPDATE_SQL = {
            "UPDATE BLOOMBUDDY_FARMER_MASTER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?",
            "UPDATE BLOOMBUDDY_BUYER_MASTER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?",
            "UPDATE BLOOMBUDDY_FLOWER_MASTER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?",
            "UPDATE BLOOMBUDDY_SALES SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?",
            "UPDATE BLOOMBUDDY_FARMER_LEDGER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?",
            "UPDATE BLOOMBUDDY_BUYER_LEDGER SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?",
            "UPDATE BLOOMBUDDY_FARMER_TRANSACTION SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?",
            "UPDATE BLOOMBUDDY_BUYER_TRANSACTION SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?",
            "UPDATE BLOOMBUDDY_CLIENT_SLOT_SEQUENCE SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?",
            "UPDATE BLOOMBUDDY_CLIENT_REPORT SET CLIENT_USERNAME = ? WHERE CLIENT_ID = ?"
    };

    @Override
    public ClientMaster save(ClientMaster clientMaster) {
        logger.info("save: clientUsername={}", clientMaster.getClientUsername());
        if (clientMaster.getClientId() == null) {
            return insert(clientMaster);
        } else {
            return updateWithCascade(clientMaster);
        }
    }

    private ClientMaster insert(ClientMaster clientMaster) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL, new String[]{"CLIENT_ID"})) {
            ps.setString(1, clientMaster.getClientUsername());
            ps.setString(2, clientMaster.getClientPassword());
            ps.setString(3, clientMaster.getClientShopName());
            ps.setString(4, clientMaster.getClientShopAddress());
            ps.setString(5, clientMaster.getClientContactNo());
            ps.setString(6, clientMaster.getClientMailId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    clientMaster.setClientId(keys.getLong(1));
                }
            }
            logger.info("insert: clientId={}", clientMaster.getClientId());
        } catch (SQLException e) {
            logger.error("insert: SQL exception", e);
            throw new RuntimeException("Failed to save client", e);
        }
        return clientMaster;
    }

    private ClientMaster updateWithCascade(ClientMaster clientMaster) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            String oldUsername = null;
            try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                ps.setLong(1, clientMaster.getClientId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        oldUsername = rs.getString("CLIENT_USERNAME");
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
                ps.setString(1, clientMaster.getClientUsername());
                ps.setString(2, clientMaster.getClientPassword());
                ps.setString(3, clientMaster.getClientShopName());
                ps.setString(4, clientMaster.getClientShopAddress());
                ps.setString(5, clientMaster.getClientContactNo());
                ps.setString(6, clientMaster.getClientMailId());
                ps.setLong(7, clientMaster.getClientId());
                ps.executeUpdate();
            }

            if (oldUsername != null && !oldUsername.equals(clientMaster.getClientUsername())) {
                logger.info("updateWithCascade: username changed from '{}' to '{}', cascading to dependent tables",
                        oldUsername, clientMaster.getClientUsername());
                String newUsername = clientMaster.getClientUsername();
                Long clientId = clientMaster.getClientId();
                for (String sql : CASCADING_UPDATE_SQL) {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, newUsername);
                        ps.setLong(2, clientId);
                        int rows = ps.executeUpdate();
                        logger.debug("updateWithCascade: {} rows updated in {}", rows, sql.substring(sql.indexOf("UPDATE ") + 7, sql.indexOf(" SET ")));
                    }
                }
            }

            conn.commit();
            logger.info("updateWithCascade: committed for clientId={}", clientMaster.getClientId());
        } catch (SQLException e) {
            logger.error("updateWithCascade: SQL exception, rolling back", e);
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ex) { logger.error("updateWithCascade: rollback failed", ex); }
            }
            throw new RuntimeException("Failed to update client", e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ex) { logger.error("updateWithCascade: close failed", ex); }
            }
        }
        return clientMaster;
    }

    @Override
    public ClientMaster findById(Long clientId) {
        logger.info("findById: clientId={}", clientId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("findById: SQL exception", e);
            throw new RuntimeException("Failed to find client", e);
        }
        return null;
    }

    @Override
    public ClientMaster findByUsername(String username) {
        logger.info("findByUsername: username={}", username);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_USERNAME_SQL)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("findByUsername: SQL exception", e);
            throw new RuntimeException("Failed to find client by username", e);
        }
        return null;
    }

    @Override
    public List<ClientMaster> findAll() {
        logger.info("findAll");
        List<ClientMaster> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("findAll: SQL exception", e);
            throw new RuntimeException("Failed to find all clients", e);
        }
        logger.info("findAll: count={}", list.size());
        return list;
    }

    @Override
    public void deleteById(Long clientId) {
        logger.info("deleteById: clientId={}", clientId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setLong(1, clientId);
            int rows = ps.executeUpdate();
            logger.info("deleteById: rowsAffected={}", rows);
        } catch (SQLException e) {
            logger.error("deleteById: SQL exception", e);
            throw new RuntimeException("Failed to delete client", e);
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    private ClientMaster mapRow(ResultSet rs) throws SQLException {
        ClientMaster c = new ClientMaster();
        c.setClientId(rs.getLong("CLIENT_ID"));
        c.setClientUsername(rs.getString("CLIENT_USERNAME"));
        c.setClientPassword(rs.getString("CLIENT_PASSWORD"));
        c.setClientShopName(rs.getString("CLIENT_SHOP_NAME"));
        c.setClientShopAddress(rs.getString("CLIENT_SHOP_ADDRESS"));
        c.setClientContactNo(rs.getString("CLIENT_CONTACT_NO"));
        c.setClientMailId(rs.getString("CLIENT_MAIL_ID"));
        return c;
    }
}
