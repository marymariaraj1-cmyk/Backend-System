package com.billing.dao.impl;

import com.billing.dao.DailyCashBookDao;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DailyCashBookDaoImpl implements DailyCashBookDao {

    private static final Logger logger = LoggerFactory.getLogger(DailyCashBookDaoImpl.class);

    private static final String BUYER_PURCHASE_TOTAL_SQL =
            "SELECT COALESCE(SUM(PRICE), 0) AS TOTAL FROM BLOOMBUDDY_SALES " +
            "WHERE CLIENT_ID = ? AND SALES_DATE = ?";

    private static final String FARMER_EXCESS_DEBIT_LIST_SQL =
            "SELECT FARMER_ID, FARMER_NAME, COALESCE(SUM(EXCESS_DEBIT_AMT), 0) AS AMOUNT " +
            "FROM BLOOMBUDDY_FARMER_TRANSACTION " +
            "WHERE CLIENT_ID = ? AND TRANSACTION_DATE = ? AND EXCESS_DEBIT_AMT > 0 AND %s " +
            "GROUP BY FARMER_ID, FARMER_NAME ORDER BY FARMER_NAME ASC";

    private static final String BUYER_RECEIVED_TOTAL_SQL =
            "SELECT COALESCE(SUM(CASH_PAID_AMT), 0) AS TOTAL FROM BLOOMBUDDY_BUYER_TRANSACTION " +
            "WHERE CLIENT_ID = ? AND TRANSACTION_DATE = ? AND PAYMENT_MODE = 'C'";

    private static final String COMMISSION_TOTAL_SQL =
            "SELECT COALESCE(SUM(COMMISSION_AMT), 0) AS TOTAL FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND SALES_DATE = ?";

    private static final String INSTALLMENT_TOTAL_SQL =
            "SELECT COALESCE(SUM(FINAL_AMT), 0) AS TOTAL FROM BLOOMBUDDY_SALES_TOTALSUMMARY " +
            "WHERE CLIENT_ID = ? AND SALES_DATE = ?";

    private static final String FIND_HEADER_SQL =
            "SELECT DAILY_CASH_BOOK_ID, CLIENT_ID, CLIENT_USERNAME, BOOK_DATE, " +
            "RENT_AMT, EXPENSE_AMT, CHIT_AMT, FINANCE_AMT, NOTE_AMT, SALARY_AMT, COIN_AMT, " +
            "BUYER_PURCHASE_TOTAL, FARMER_EXCESS_DEBIT_CASH, " +
            "CASH_IN_HAND_AMT, OPENING_BALANCE, " +
            "BUYER_RECEIVED_TOTAL, COMMISSION_TOTAL, INSTALLMENT_TOTAL, " +
            "FARMER_EXCESS_DEBIT_NONCASH, TOTAL_DEBIT_SIDE, TOTAL_CREDIT_SIDE, CLOSING_BALANCE, REMARKS " +
            "FROM BLOOMBUDDY_DAILY_CASH_BOOK WHERE CLIENT_ID = ? AND BOOK_DATE = ?";

    private static final String FIND_HEADER_ID_SQL =
            "SELECT DAILY_CASH_BOOK_ID FROM BLOOMBUDDY_DAILY_CASH_BOOK WHERE CLIENT_ID = ? AND BOOK_DATE = ?";

    private static final String UPSERT_HEADER_SQL =
            "INSERT INTO BLOOMBUDDY_DAILY_CASH_BOOK (CLIENT_ID, CLIENT_USERNAME, BOOK_DATE, " +
            "RENT_AMT, EXPENSE_AMT, CHIT_AMT, FINANCE_AMT, NOTE_AMT, SALARY_AMT, COIN_AMT, " +
            "BUYER_PURCHASE_TOTAL, FARMER_EXCESS_DEBIT_CASH, " +
            "CASH_IN_HAND_AMT, OPENING_BALANCE, " +
            "BUYER_RECEIVED_TOTAL, COMMISSION_TOTAL, INSTALLMENT_TOTAL, " +
            "FARMER_EXCESS_DEBIT_NONCASH, TOTAL_DEBIT_SIDE, TOTAL_CREDIT_SIDE, CLOSING_BALANCE, REMARKS) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE CLIENT_USERNAME = VALUES(CLIENT_USERNAME), " +
            "RENT_AMT = VALUES(RENT_AMT), EXPENSE_AMT = VALUES(EXPENSE_AMT), CHIT_AMT = VALUES(CHIT_AMT), " +
            "FINANCE_AMT = VALUES(FINANCE_AMT), NOTE_AMT = VALUES(NOTE_AMT), SALARY_AMT = VALUES(SALARY_AMT), " +
            "COIN_AMT = VALUES(COIN_AMT), BUYER_PURCHASE_TOTAL = VALUES(BUYER_PURCHASE_TOTAL), " +
            "FARMER_EXCESS_DEBIT_CASH = VALUES(FARMER_EXCESS_DEBIT_CASH), " +
            "CASH_IN_HAND_AMT = VALUES(CASH_IN_HAND_AMT), OPENING_BALANCE = VALUES(OPENING_BALANCE), " +
            "BUYER_RECEIVED_TOTAL = VALUES(BUYER_RECEIVED_TOTAL), COMMISSION_TOTAL = VALUES(COMMISSION_TOTAL), " +
            "INSTALLMENT_TOTAL = VALUES(INSTALLMENT_TOTAL), " +
            "FARMER_EXCESS_DEBIT_NONCASH = VALUES(FARMER_EXCESS_DEBIT_NONCASH), " +
            "TOTAL_DEBIT_SIDE = VALUES(TOTAL_DEBIT_SIDE), TOTAL_CREDIT_SIDE = VALUES(TOTAL_CREDIT_SIDE), " +
            "CLOSING_BALANCE = VALUES(CLOSING_BALANCE), REMARKS = VALUES(REMARKS)";

    private static final String FIND_DETAILS_SQL =
            "SELECT CASH_BOOK_DETAIL_ID, DAILY_CASH_BOOK_ID, CLIENT_ID, BOOK_DATE, " +
            "ENTRY_TYPE, ENTRY_LABEL, FARMER_ID, AMOUNT, DISPLAY_ORDER " +
            "FROM BLOOMBUDDY_DAILY_CASH_BOOK_DETAIL WHERE DAILY_CASH_BOOK_ID = ? ORDER BY DISPLAY_ORDER ASC";

    private static final String DELETE_DETAILS_SQL =
            "DELETE FROM BLOOMBUDDY_DAILY_CASH_BOOK_DETAIL WHERE DAILY_CASH_BOOK_ID = ?";

    private static final String INSERT_DETAIL_SQL =
            "INSERT INTO BLOOMBUDDY_DAILY_CASH_BOOK_DETAIL (DAILY_CASH_BOOK_ID, CLIENT_ID, CLIENT_USERNAME, " +
            "BOOK_DATE, ENTRY_TYPE, ENTRY_LABEL, FARMER_ID, AMOUNT, DISPLAY_ORDER) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final DataSource dataSource;

    public DailyCashBookDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public BigDecimal sumBuyerPurchaseTotal(Long clientId, LocalDate bookDate) {
        return querySingleTotal(BUYER_PURCHASE_TOTAL_SQL, clientId, bookDate, "sumBuyerPurchaseTotal");
    }

    @Override
    public List<Map<String, Object>> findFarmerExcessDebitCashList(Long clientId, LocalDate bookDate) {
        return findFarmerExcessDebitList(clientId, bookDate, "PAYMENT_MODE = 'C'");
    }

    @Override
    public BigDecimal sumBuyerReceivedTotal(Long clientId, LocalDate bookDate) {
        return querySingleTotal(BUYER_RECEIVED_TOTAL_SQL, clientId, bookDate, "sumBuyerReceivedTotal");
    }

    @Override
    public BigDecimal sumCommissionTotal(Long clientId, LocalDate bookDate) {
        return querySingleTotal(COMMISSION_TOTAL_SQL, clientId, bookDate, "sumCommissionTotal");
    }

    @Override
    public BigDecimal sumInstallmentTotal(Long clientId, LocalDate bookDate) {
        return querySingleTotal(INSTALLMENT_TOTAL_SQL, clientId, bookDate, "sumInstallmentTotal");
    }

    @Override
    public List<Map<String, Object>> findFarmerExcessDebitNonCashList(Long clientId, LocalDate bookDate) {
        return findFarmerExcessDebitList(clientId, bookDate, "PAYMENT_MODE <> 'C'");
    }

    private BigDecimal querySingleTotal(String sql, Long clientId, LocalDate bookDate, String op) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(bookDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal total = rs.getBigDecimal("TOTAL");
                    return RoundOffUtil.round(total != null ? total : BigDecimal.ZERO);
                }
            }
        } catch (SQLException e) {
            logger.error("DailyCashBook {}: SQL exception", op, e);
            throw new RuntimeException("Failed to calculate daily cash book block", e);
        }
        return BigDecimal.ZERO;
    }

    private List<Map<String, Object>> findFarmerExcessDebitList(Long clientId, LocalDate bookDate, String modeFilter) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = String.format(FARMER_EXCESS_DEBIT_LIST_SQL, modeFilter);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(bookDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("farmerName", rs.getString("FARMER_NAME"));
                    BigDecimal amount = rs.getBigDecimal("AMOUNT");
                    row.put("amount", RoundOffUtil.round(amount != null ? amount : BigDecimal.ZERO));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("DailyCashBook findFarmerExcessDebitList: SQL exception", e);
            throw new RuntimeException("Failed to fetch farmer excess debit list", e);
        }
        return results;
    }

    @Override
    public Map<String, Object> findHeaderByClientAndDate(Long clientId, LocalDate bookDate) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_HEADER_SQL)) {
            ps.setLong(1, clientId);
            ps.setDate(2, java.sql.Date.valueOf(bookDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("dailyCashBookId", rs.getLong("DAILY_CASH_BOOK_ID"));
                    row.put("clientId", rs.getLong("CLIENT_ID"));
                    row.put("bookDate", rs.getDate("BOOK_DATE").toLocalDate().toString());
                    row.put("rentAmt", dec(rs, "RENT_AMT"));
                    row.put("expenseAmt", dec(rs, "EXPENSE_AMT"));
                    row.put("chitAmt", dec(rs, "CHIT_AMT"));
                    row.put("financeAmt", dec(rs, "FINANCE_AMT"));
                    row.put("noteAmt", dec(rs, "NOTE_AMT"));
                    row.put("salaryAmt", dec(rs, "SALARY_AMT"));
                    row.put("coinAmt", dec(rs, "COIN_AMT"));
                    row.put("buyerPurchaseTotal", dec(rs, "BUYER_PURCHASE_TOTAL"));
                    row.put("farmerExcessDebitCash", dec(rs, "FARMER_EXCESS_DEBIT_CASH"));
                    row.put("cashInHandAmt", dec(rs, "CASH_IN_HAND_AMT"));
                    row.put("openingBalance", dec(rs, "OPENING_BALANCE"));
                    row.put("buyerReceivedTotal", dec(rs, "BUYER_RECEIVED_TOTAL"));
                    row.put("commissionTotal", dec(rs, "COMMISSION_TOTAL"));
                    row.put("installmentTotal", dec(rs, "INSTALLMENT_TOTAL"));
                    row.put("farmerExcessDebitNonCash", dec(rs, "FARMER_EXCESS_DEBIT_NONCASH"));
                    row.put("totalDebitSide", dec(rs, "TOTAL_DEBIT_SIDE"));
                    row.put("totalCreditSide", dec(rs, "TOTAL_CREDIT_SIDE"));
                    row.put("closingBalance", dec(rs, "CLOSING_BALANCE"));
                    row.put("remarks", rs.getString("REMARKS"));
                    return row;
                }
            }
        } catch (SQLException e) {
            logger.error("DailyCashBook findHeaderByClientAndDate: SQL exception", e);
            throw new RuntimeException("Failed to fetch daily cash book", e);
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> findDetailsByHeaderId(Long headerId) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_DETAILS_SQL)) {
            ps.setLong(1, headerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("entryType", rs.getString("ENTRY_TYPE"));
                    row.put("entryLabel", rs.getString("ENTRY_LABEL"));
                    row.put("farmerId", rs.getString("FARMER_ID"));
                    row.put("amount", dec(rs, "AMOUNT"));
                    row.put("displayOrder", rs.getInt("DISPLAY_ORDER"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("DailyCashBook findDetailsByHeaderId: SQL exception", e);
            throw new RuntimeException("Failed to fetch daily cash book details", e);
        }
        return results;
    }

    @Override
    public Long upsertHeader(Map<String, Object> header, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_HEADER_SQL)) {
            ps.setLong(1, (Long) header.get("clientId"));
            ps.setString(2, (String) header.get("clientUsername"));
            ps.setDate(3, java.sql.Date.valueOf((LocalDate) header.get("bookDate")));
            int i = 4;
            ps.setBigDecimal(i++, (BigDecimal) header.get("rentAmt"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("expenseAmt"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("chitAmt"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("financeAmt"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("noteAmt"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("salaryAmt"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("coinAmt"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("buyerPurchaseTotal"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("farmerExcessDebitCash"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("cashInHandAmt"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("openingBalance"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("buyerReceivedTotal"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("commissionTotal"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("installmentTotal"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("farmerExcessDebitNonCash"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("totalDebitSide"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("totalCreditSide"));
            ps.setBigDecimal(i++, (BigDecimal) header.get("closingBalance"));
            ps.setString(i, (String) header.get("remarks"));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("DailyCashBook upsertHeader: SQL exception", e);
            throw new RuntimeException("Failed to save daily cash book", e);
        }
        try (PreparedStatement ps = conn.prepareStatement(FIND_HEADER_ID_SQL)) {
            ps.setLong(1, (Long) header.get("clientId"));
            ps.setDate(2, java.sql.Date.valueOf((LocalDate) header.get("bookDate")));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("DAILY_CASH_BOOK_ID");
                }
            }
        } catch (SQLException e) {
            logger.error("DailyCashBook upsertHeader (id lookup): SQL exception", e);
            throw new RuntimeException("Failed to save daily cash book", e);
        }
        throw new RuntimeException("Failed to save daily cash book");
    }

    @Override
    public void deleteDetailsByHeaderId(Long headerId, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(DELETE_DETAILS_SQL)) {
            ps.setLong(1, headerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("DailyCashBook deleteDetailsByHeaderId: SQL exception", e);
            throw new RuntimeException("Failed to save daily cash book", e);
        }
    }

    @Override
    public void insertDetail(Long headerId, Long clientId, String clientUsername, LocalDate bookDate,
                             String entryType, String entryLabel, String farmerId, BigDecimal amount,
                             int displayOrder, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_DETAIL_SQL)) {
            ps.setLong(1, headerId);
            ps.setLong(2, clientId);
            ps.setString(3, clientUsername);
            ps.setDate(4, java.sql.Date.valueOf(bookDate));
            ps.setString(5, entryType);
            ps.setString(6, entryLabel);
            ps.setString(7, farmerId);
            ps.setBigDecimal(8, amount);
            ps.setInt(9, displayOrder);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("DailyCashBook insertDetail: SQL exception", e);
            throw new RuntimeException("Failed to save daily cash book", e);
        }
    }

    private BigDecimal dec(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return RoundOffUtil.round(value != null ? value : BigDecimal.ZERO);
    }
}
