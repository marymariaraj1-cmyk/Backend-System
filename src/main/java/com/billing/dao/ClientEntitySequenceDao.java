package com.billing.dao;

import java.sql.Connection;

public interface ClientEntitySequenceDao {

    void ensureTable(Connection conn);

    int nextSeqNumber(Long clientId, String clientUsername, String entityType, Connection conn);
}
