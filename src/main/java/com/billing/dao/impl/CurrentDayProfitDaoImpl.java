package com.billing.dao.impl;

import com.billing.dao.CurrentDayProfitDao;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CurrentDayProfitDaoImpl implements CurrentDayProfitDao {

    private static final Logger logger = LoggerFactory.getLogger(CurrentDayProfitDaoImpl.class);

    private static final String SELECT_TODAY_SUMMARY_SQL =
            "SELECT FARMER_ID, FARMER_NAME, TOTAL_SALES_AMT, TOTAL_NET_AMT, DEBIT_AMT, FINAL_AMT, COMMISSION_AMT " +
            "FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND SALES_DATE = CURDATE() " +
            "ORDER BY FARMER_NAME";

    private static final String SELECT_TODAY_SALES_SQL =
            "SELECT FARMER_ID, FARMER_NAME, CUST_NAME, FLOWER_TYPE, TOTAL_WEIGHT, PERKG_RATE, PRICE " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND SALES_DATE = CURDATE()";

    private static final String ORDER_BY_FARMER = " ORDER BY FARMER_NAME";

    private final DataSource dataSource;

    public CurrentDayProfitDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Map<String, Object>> findTodaySummary(Long clientId) {
        logger.info("findTodaySummary: clientId={}", clientId);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_TODAY_SUMMARY_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    row.put("totalSalesAmt", RoundOffUtil.round(rs.getBigDecimal("TOTAL_SALES_AMT")));
                    row.put("totalNetAmt", RoundOffUtil.round(rs.getBigDecimal("TOTAL_NET_AMT")));
                    row.put("debitAmt", RoundOffUtil.round(rs.getBigDecimal("DEBIT_AMT")));
                    row.put("finalAmt", RoundOffUtil.round(rs.getBigDecimal("FINAL_AMT")));
                    row.put("commissionAmt", RoundOffUtil.round(rs.getBigDecimal("COMMISSION_AMT")));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findTodaySummary: SQL exception", e);
            throw new RuntimeException("Failed to fetch today's commission summary", e);
        }
        logger.info("findTodaySummary: rows={}", rows.size());
        return rows;
    }

    @Override
    public List<Map<String, Object>> findTodaySales(Long clientId, String farmerId) {
        logger.info("findTodaySales: clientId={}, farmerId={}", clientId, farmerId);
        boolean filterFarmer = farmerId != null && !farmerId.trim().isEmpty();
        String sql = SELECT_TODAY_SALES_SQL + (filterFarmer ? " AND FARMER_ID = ?" : "") + ORDER_BY_FARMER;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            if (filterFarmer) {
                ps.setString(2, farmerId.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    row.put("custName", rs.getString("CUST_NAME"));
                    row.put("flowerType", rs.getString("FLOWER_TYPE"));
                    row.put("totalWeight", rs.getBigDecimal("TOTAL_WEIGHT"));
                    row.put("perKgRate", RoundOffUtil.round(rs.getBigDecimal("PERKG_RATE")));
                    row.put("price", rs.getBigDecimal("PRICE"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findTodaySales: SQL exception", e);
            throw new RuntimeException("Failed to fetch today's sales details", e);
        }
        logger.info("findTodaySales: rows={}", rows.size());
        return rows;
    }
}
