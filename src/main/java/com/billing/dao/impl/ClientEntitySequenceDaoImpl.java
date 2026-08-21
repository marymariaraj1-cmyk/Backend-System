package com.billing.dao.impl;

import com.billing.dao.ClientEntitySequenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ClientEntitySequenceDaoImpl implements ClientEntitySequenceDao {

    private static final Logger logger = LoggerFactory.getLogger(ClientEntitySequenceDaoImpl.class);

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS BLOOMBUDDY_CLIENT_ENTITY_SEQUENCE ("
                    + "CLIENT_ID BIGINT NOT NULL, "
                    + "CLIENT_USERNAME VARCHAR(50) NOT NULL, "
                    + "ENTITY_TYPE VARCHAR(20) NOT NULL, "
                    + "LAST_SEQ_NO INT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (CLIENT_ID, ENTITY_TYPE))";

    private static final String UPSERT_SQL =
            "INSERT INTO BLOOMBUDDY_CLIENT_ENTITY_SEQUENCE (CLIENT_ID, CLIENT_USERNAME, ENTITY_TYPE, LAST_SEQ_NO) "
                    + "VALUES (?, ?, ?, 0) "
                    + "ON DUPLICATE KEY UPDATE CLIENT_USERNAME = VALUES(CLIENT_USERNAME)";

    private static final String INCREMENT_SQL =
            "UPDATE BLOOMBUDDY_CLIENT_ENTITY_SEQUENCE SET LAST_SEQ_NO = LAST_SEQ_NO + 1 "
                    + "WHERE CLIENT_ID = ? AND ENTITY_TYPE = ?";

    private static final String SELECT_SQL =
            "SELECT LAST_SEQ_NO FROM BLOOMBUDDY_CLIENT_ENTITY_SEQUENCE WHERE CLIENT_ID = ? AND ENTITY_TYPE = ?";

    @Override
    public void ensureTable(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(CREATE_TABLE_SQL)) {
            ps.executeUpdate();
            logger.info("ensureTable: BLOOMBUDDY_CLIENT_ENTITY_SEQUENCE table ensured");
        } catch (SQLException e) {
            logger.error("ensureTable: failed to create BLOOMBUDDY_CLIENT_ENTITY_SEQUENCE", e);
            throw new RuntimeException("Failed to create entity sequence table", e);
        }
    }

    @Override
    public int nextSeqNumber(Long clientId, String clientUsername, String entityType, Connection conn) {
        try {
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
                ps.setLong(1, clientId);
                ps.setString(2, clientUsername);
                ps.setString(3, entityType);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(INCREMENT_SQL)) {
                ps.setLong(1, clientId);
                ps.setString(2, entityType);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
                ps.setLong(1, clientId);
                ps.setString(2, entityType);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int seqNo = rs.getInt("LAST_SEQ_NO");
                        logger.info("nextSeqNumber: clientId={}, entityType={}, seqNo={}", clientId, entityType, seqNo);
                        return seqNo;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("nextSeqNumber: failed for clientId={}, entityType={}", clientId, entityType, e);
            throw new RuntimeException("Failed to generate next entity sequence number", e);
        }
        throw new RuntimeException("No sequence number returned for clientId=" + clientId + ", entityType=" + entityType);
    }
}
