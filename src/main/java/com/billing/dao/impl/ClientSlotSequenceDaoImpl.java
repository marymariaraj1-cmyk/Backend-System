package com.billing.dao.impl;

import com.billing.dao.ClientSlotSequenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ClientSlotSequenceDaoImpl implements ClientSlotSequenceDao {

    private static final Logger logger = LoggerFactory.getLogger(ClientSlotSequenceDaoImpl.class);

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS BLOOMBUDDY_CLIENT_SLOT_SEQUENCE ("
                    + "CLIENT_ID BIGINT NOT NULL, "
                    + "CLIENT_USERNAME VARCHAR(50) NOT NULL, "
                    + "LAST_SLOT_NO INT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (CLIENT_ID))";

    private static final String ALTER_USERNAME_SQL =
            "ALTER TABLE BLOOMBUDDY_CLIENT_SLOT_SEQUENCE ADD COLUMN CLIENT_USERNAME VARCHAR(50) NOT NULL DEFAULT ''";

    private static final String UPSERT_SQL =
            "INSERT INTO BLOOMBUDDY_CLIENT_SLOT_SEQUENCE (CLIENT_ID, CLIENT_USERNAME, LAST_SLOT_NO) "
                    + "VALUES (?, ?, 0) "
                    + "ON DUPLICATE KEY UPDATE CLIENT_USERNAME = VALUES(CLIENT_USERNAME)";

    private static final String INCREMENT_SQL =
            "UPDATE BLOOMBUDDY_CLIENT_SLOT_SEQUENCE SET LAST_SLOT_NO = LAST_SLOT_NO + 1 WHERE CLIENT_ID = ?";

    private static final String SELECT_SQL =
            "SELECT LAST_SLOT_NO FROM BLOOMBUDDY_CLIENT_SLOT_SEQUENCE WHERE CLIENT_ID = ?";

    @Override
    public void ensureTable(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(CREATE_TABLE_SQL)) {
            ps.executeUpdate();
            logger.info("ensureTable: BLOOMBUDDY_CLIENT_SLOT_SEQUENCE table ensured");
        } catch (SQLException e) {
            logger.error("ensureTable: failed to create BLOOMBUDDY_CLIENT_SLOT_SEQUENCE", e);
            throw new RuntimeException("Failed to create slot sequence table", e);
        }
        try (PreparedStatement ps = conn.prepareStatement(ALTER_USERNAME_SQL)) {
            ps.executeUpdate();
            logger.info("ensureTable: CLIENT_USERNAME column ensured");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1060) {
                logger.info("ensureTable: CLIENT_USERNAME column already exists, skipping");
            } else {
                logger.error("ensureTable: failed to add CLIENT_USERNAME column", e);
                throw new RuntimeException("Failed to add CLIENT_USERNAME column", e);
            }
        }
    }

    @Override
    public int nextSlotNumber(Long clientId, String clientUsername, Connection conn) {
        try {
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
                ps.setLong(1, clientId);
                ps.setString(2, clientUsername);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(INCREMENT_SQL)) {
                ps.setLong(1, clientId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
                ps.setLong(1, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int slotNo = rs.getInt("LAST_SLOT_NO");
                        logger.info("nextSlotNumber: clientId={}, slotNo={}", clientId, slotNo);
                        return slotNo;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("nextSlotNumber: failed for clientId={}", clientId, e);
            throw new RuntimeException("Failed to generate next slot number", e);
        }
        throw new RuntimeException("No slot number returned for clientId=" + clientId);
    }
}
