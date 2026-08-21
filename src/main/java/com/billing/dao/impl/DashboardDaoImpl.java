package com.billing.dao.impl;

import com.billing.dao.DashboardDao;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DashboardDaoImpl implements DashboardDao {

    private static final Logger logger = LoggerFactory.getLogger(DashboardDaoImpl.class);

    private final DataSource dataSource;

    public DashboardDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String TODAY_SUMMARY_SQL =
            "SELECT COALESCE(SUM(TOTAL_SALES_AMT), 0) AS TODAY_SALES, " +
            "COALESCE(SUM(COMMISSION_AMT), 0) AS TODAY_COMMISSION " +
            "FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND SALES_DATE = CURDATE()";

    private static final String YESTERDAY_SUMMARY_SQL =
            "SELECT COALESCE(SUM(TOTAL_SALES_AMT), 0) AS YESTERDAY_SALES, " +
            "COALESCE(SUM(COMMISSION_AMT), 0) AS YESTERDAY_COMMISSION " +
            "FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND SALES_DATE = DATE_SUB(CURDATE(), INTERVAL 1 DAY)";

    private static final String TODAY_SALES_METRICS_SQL =
            "SELECT COALESCE(SUM(TOTAL_WEIGHT), 0) AS TODAY_KG, " +
            "COUNT(DISTINCT FARMER_ID) AS ACTIVE_FARMERS, " +
            "COUNT(DISTINCT BUYER_ID) AS ACTIVE_BUYERS " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND SALES_DATE = CURDATE()";

    private static final String FARMER_COUNT_SQL =
            "SELECT COUNT(*) AS CNT FROM BLOOMBUDDY_FARMER_MASTER WHERE CLIENT_ID = ?";

    private static final String BUYER_COUNT_SQL =
            "SELECT COUNT(*) AS CNT FROM BLOOMBUDDY_BUYER_MASTER WHERE CLIENT_ID = ?";

    private static final String FLOWER_COUNT_SQL =
            "SELECT COUNT(*) AS CNT FROM BLOOMBUDDY_FLOWER_MASTER WHERE CLIENT_ID = ?";

    private static final String SALES_TREND_SQL =
            "SELECT SALES_DATE, COALESCE(SUM(TOTAL_SALES_AMT), 0) AS AMOUNT " +
            "FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND SALES_DATE BETWEEN ? AND ? " +
            "GROUP BY SALES_DATE ORDER BY SALES_DATE ASC";

    private static final String TOP_FLOWERS_SQL =
            "SELECT FLOWER_TYPE, COALESCE(SUM(TOTAL_WEIGHT), 0) AS TOTAL_KG " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND SALES_DATE BETWEEN ? AND ? " +
            "AND FLOWER_TYPE IS NOT NULL AND FLOWER_TYPE <> '' " +
            "GROUP BY FLOWER_TYPE ORDER BY TOTAL_KG DESC LIMIT 5";

    private static final String TOP_FARMERS_SQL =
            "SELECT FARMER_ID, FARMER_NAME, COALESCE(SUM(TOTAL_SALES_AMT), 0) AS TOTAL_SALES " +
            "FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND SALES_DATE BETWEEN ? AND ? " +
            "GROUP BY FARMER_ID, FARMER_NAME ORDER BY TOTAL_SALES DESC LIMIT 5";

    private static final String TOP_OUTSTANDING_FARMERS_SQL =
            "SELECT fl.FARMER_ID, fl.FARMER_NAME, " +
            "(COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.CREDIT_AMT ELSE 0 END), 0) " +
            " - COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.DEBIT_AMT ELSE 0 END), 0) " +
            " + COALESCE((SELECT ob.OPENING_BALANCE FROM BLOOMBUDDY_FARMER_OPENING_BALANCE ob " +
            "             WHERE ob.CLIENT_ID = fl.CLIENT_ID AND ob.FARMER_ID = fl.FARMER_ID " +
            "               AND EXISTS (SELECT 1 FROM BLOOMBUDDY_FARMER_LEDGER y " +
            "                           WHERE y.CLIENT_ID = ob.CLIENT_ID AND y.FARMER_ID = ob.FARMER_ID " +
            "                             AND y.SALES_DATE = ob.OPENING_BALANCE_DATE AND y.LEDGER_ACTIVE = 'Y')), 0)) AS OUTSTANDING " +
            "FROM BLOOMBUDDY_FARMER_LEDGER fl " +
            "WHERE fl.CLIENT_ID = ? " +
            "GROUP BY fl.FARMER_ID, fl.FARMER_NAME " +
            "ORDER BY ABS(OUTSTANDING) DESC LIMIT 5";

    private static final String TOP_OUTSTANDING_BUYERS_SQL =
            "SELECT fl.BUYER_ID, fl.BUYER_NAME, " +
            "(COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.DEBIT_AMT ELSE 0 END), 0) " +
            " - COALESCE(SUM(CASE WHEN fl.LEDGER_ACTIVE = 'Y' THEN fl.CREDIT_AMT ELSE 0 END), 0) " +
            " + COALESCE((SELECT ob.OPENING_BALANCE FROM BLOOMBUDDY_BUYER_OPENING_BALANCE ob " +
            "             WHERE ob.CLIENT_ID = fl.CLIENT_ID AND ob.BUYER_ID = fl.BUYER_ID " +
            "               AND EXISTS (SELECT 1 FROM BLOOMBUDDY_BUYER_LEDGER y " +
            "                           WHERE y.CLIENT_ID = ob.CLIENT_ID AND y.BUYER_ID = ob.BUYER_ID " +
            "                             AND y.SALES_DATE = ob.OPENING_BALANCE_DATE AND y.LEDGER_ACTIVE = 'Y')), 0)) AS OUTSTANDING " +
            "FROM BLOOMBUDDY_BUYER_LEDGER fl " +
            "WHERE fl.CLIENT_ID = ? " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%cash%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%upi%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%google pay%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%gpay%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%phonepe%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%paytm%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%online%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%card%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%neft%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%rtgs%' " +
            "AND LOWER(COALESCE(fl.BUYER_NAME, '')) NOT LIKE '%imps%' " +
            "GROUP BY fl.BUYER_ID, fl.BUYER_NAME " +
            "ORDER BY ABS(OUTSTANDING) DESC LIMIT 5";

    private static final String DIRECT_PAYMENTS_SQL =
            "SELECT " +
            "COALESCE(SUM(CASE WHEN LOWER(COALESCE(CUST_NAME,'')) LIKE '%cash%' THEN PRICE ELSE 0 END), 0) AS CASH_AMT, " +
            "COALESCE(SUM(CASE WHEN LOWER(COALESCE(CUST_NAME,'')) LIKE '%upi%' " +
            "OR LOWER(COALESCE(CUST_NAME,'')) LIKE '%google pay%' OR LOWER(COALESCE(CUST_NAME,'')) LIKE '%gpay%' " +
            "OR LOWER(COALESCE(CUST_NAME,'')) LIKE '%phonepe%' OR LOWER(COALESCE(CUST_NAME,'')) LIKE '%paytm%' " +
            "THEN PRICE ELSE 0 END), 0) AS UPI_AMT, " +
            "COALESCE(SUM(CASE WHEN LOWER(COALESCE(CUST_NAME,'')) LIKE '%online%' " +
            "OR LOWER(COALESCE(CUST_NAME,'')) LIKE '%card%' OR LOWER(COALESCE(CUST_NAME,'')) LIKE '%neft%' " +
            "OR LOWER(COALESCE(CUST_NAME,'')) LIKE '%rtgs%' OR LOWER(COALESCE(CUST_NAME,'')) LIKE '%imps%' " +
            "THEN PRICE ELSE 0 END), 0) AS OTHER_AMT " +
            "FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND SALES_DATE BETWEEN ? AND ?";

    @Override
    public Map<String, Object> findTodayKpi(Long clientId) {
        logger.info("findTodayKpi: clientId={}", clientId);
        Map<String, Object> kpi = new LinkedHashMap<>();
        Map<String, Object> today = querySingleRow(TODAY_SUMMARY_SQL, clientId);
        Map<String, Object> yesterday = querySingleRow(YESTERDAY_SUMMARY_SQL, clientId);
        Map<String, Object> metrics = querySingleRow(TODAY_SALES_METRICS_SQL, clientId);

        kpi.put("todaySales", RoundOffUtil.round(toBigDecimal(today.get("TODAY_SALES"))));
        kpi.put("todayCommission", RoundOffUtil.round(toBigDecimal(today.get("TODAY_COMMISSION"))));
        kpi.put("yesterdaySales", RoundOffUtil.round(toBigDecimal(yesterday.get("YESTERDAY_SALES"))));
        kpi.put("yesterdayCommission", RoundOffUtil.round(toBigDecimal(yesterday.get("YESTERDAY_COMMISSION"))));
        kpi.put("todayKg", toBigDecimal(metrics.get("TODAY_KG")));
        kpi.put("activeFarmers", toInt(metrics.get("ACTIVE_FARMERS")));
        kpi.put("activeBuyers", toInt(metrics.get("ACTIVE_BUYERS")));
        return kpi;
    }

    @Override
    public Map<String, Object> findMasterCounts(Long clientId) {
        logger.info("findMasterCounts: clientId={}", clientId);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("farmers", queryCount(FARMER_COUNT_SQL, clientId));
        counts.put("buyers", queryCount(BUYER_COUNT_SQL, clientId));
        counts.put("flowers", queryCount(FLOWER_COUNT_SQL, clientId));
        return counts;
    }

    @Override
    public List<Map<String, Object>> findSalesTrend(Long clientId, LocalDate fromDate, LocalDate toDate) {
        logger.info("findSalesTrend: clientId={}, from={}, to={}", clientId, fromDate, toDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SALES_TREND_SQL)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(fromDate));
            ps.setDate(3, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesDate", rs.getDate("SALES_DATE").toLocalDate().toString());
                    row.put("amount", RoundOffUtil.round(rs.getBigDecimal("AMOUNT")));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findSalesTrend: SQL exception", e);
            throw new RuntimeException("Failed to fetch sales trend", e);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> findTopFlowersByWeight(Long clientId, LocalDate fromDate, LocalDate toDate) {
        logger.info("findTopFlowersByWeight: clientId={}, from={}, to={}", clientId, fromDate, toDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(TOP_FLOWERS_SQL)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(fromDate));
            ps.setDate(3, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("flowerType", rs.getString("FLOWER_TYPE"));
                    row.put("totalKg", rs.getBigDecimal("TOTAL_KG"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findTopFlowersByWeight: SQL exception", e);
            throw new RuntimeException("Failed to fetch top flowers", e);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> findTopFarmersBySales(Long clientId, LocalDate fromDate, LocalDate toDate) {
        logger.info("findTopFarmersBySales: clientId={}, from={}, to={}", clientId, fromDate, toDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(TOP_FARMERS_SQL)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(fromDate));
            ps.setDate(3, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    row.put("totalSalesAmt", RoundOffUtil.round(rs.getBigDecimal("TOTAL_SALES")));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findTopFarmersBySales: SQL exception", e);
            throw new RuntimeException("Failed to fetch top farmers", e);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> findTopOutstandingFarmers(Long clientId) {
        logger.info("findTopOutstandingFarmers: clientId={}", clientId);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(TOP_OUTSTANDING_FARMERS_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    row.put("outstandingBalance", RoundOffUtil.round(rs.getBigDecimal("OUTSTANDING")));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findTopOutstandingFarmers: SQL exception", e);
            throw new RuntimeException("Failed to fetch outstanding farmers", e);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> findTopOutstandingBuyers(Long clientId) {
        logger.info("findTopOutstandingBuyers: clientId={}", clientId);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(TOP_OUTSTANDING_BUYERS_SQL)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("buyerId", rs.getString("BUYER_ID"));
                    row.put("buyerName", rs.getString("BUYER_NAME"));
                    row.put("outstandingBalance", RoundOffUtil.round(rs.getBigDecimal("OUTSTANDING")));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("findTopOutstandingBuyers: SQL exception", e);
            throw new RuntimeException("Failed to fetch outstanding buyers", e);
        }
        return rows;
    }

    @Override
    public Map<String, Object> findDirectPayments(Long clientId, LocalDate fromDate, LocalDate toDate) {
        logger.info("findDirectPayments: clientId={}, from={}, to={}", clientId, fromDate, toDate);
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DIRECT_PAYMENTS_SQL)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(fromDate));
            ps.setDate(3, java.sql.Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("cash", RoundOffUtil.round(rs.getBigDecimal("CASH_AMT")));
                    result.put("upi", RoundOffUtil.round(rs.getBigDecimal("UPI_AMT")));
                    result.put("other", RoundOffUtil.round(rs.getBigDecimal("OTHER_AMT")));
                } else {
                    result.put("cash", BigDecimal.ZERO);
                    result.put("upi", BigDecimal.ZERO);
                    result.put("other", BigDecimal.ZERO);
                }
            }
        } catch (SQLException e) {
            logger.error("findDirectPayments: SQL exception", e);
            throw new RuntimeException("Failed to fetch direct payments", e);
        }
        return result;
    }

    private Map<String, Object> querySingleRow(String sql, Long clientId) {
        Map<String, Object> row = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("querySingleRow: SQL exception for clientId={}", clientId, e);
            throw new RuntimeException("Failed to fetch dashboard metric", e);
        }
        return row;
    }

    private long queryCount(String sql, Long clientId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("CNT") : 0L;
            }
        } catch (SQLException e) {
            logger.error("queryCount: SQL exception for clientId={}", clientId, e);
            throw new RuntimeException("Failed to fetch master count", e);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        return ((Number) value).intValue();
    }
}
