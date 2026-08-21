package com.billing.dao;

import java.sql.Connection;

public interface ClientSlotSequenceDao {

    void ensureTable(Connection conn);

    int nextSlotNumber(Long clientId, String clientUsername, Connection conn);
}
