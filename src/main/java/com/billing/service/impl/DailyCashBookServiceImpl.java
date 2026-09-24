package com.billing.service.impl;

import com.billing.dao.DailyCashBookDao;
import com.billing.dto.DailyCashBookSaveRequest;
import com.billing.service.DailyCashBookService;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyCashBookServiceImpl implements DailyCashBookService {

    private static final Logger logger = LoggerFactory.getLogger(DailyCashBookServiceImpl.class);

    private final DataSource dataSource;
    private final DailyCashBookDao dailyCashBookDao;

    public DailyCashBookServiceImpl(DataSource dataSource, DailyCashBookDao dailyCashBookDao) {
        this.dataSource = dataSource;
        this.dailyCashBookDao = dailyCashBookDao;
    }

    @Override
    public Map<String, Object> computeBlocks(Long clientId, LocalDate bookDate) {
        logger.info("computeBlocks: clientId={}, bookDate={}", clientId, bookDate);
        BigDecimal buyerPurchaseTotal = dailyCashBookDao.sumBuyerPurchaseTotal(clientId, bookDate);
        List<Map<String, Object>> excessCashList =
                dailyCashBookDao.findFarmerExcessDebitCashList(clientId, bookDate);
        BigDecimal buyerReceivedTotal = dailyCashBookDao.sumBuyerReceivedTotal(clientId, bookDate);
        BigDecimal commissionTotal = dailyCashBookDao.sumCommissionTotal(clientId, bookDate);
        BigDecimal installmentTotal = dailyCashBookDao.sumInstallmentTotal(clientId, bookDate);
        List<Map<String, Object>> excessNonCashList =
                dailyCashBookDao.findFarmerExcessDebitNonCashList(clientId, bookDate);

        BigDecimal farmerExcessDebitCash = sumList(excessCashList);
        BigDecimal farmerExcessDebitNonCash = sumList(excessNonCashList);

        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("buyerPurchaseTotal", buyerPurchaseTotal);
        blocks.put("farmerExcessDebitCashList", excessCashList);
        blocks.put("farmerExcessDebitCash", farmerExcessDebitCash);
        blocks.put("buyerReceivedTotal", buyerReceivedTotal);
        blocks.put("commissionTotal", commissionTotal);
        blocks.put("installmentTotal", installmentTotal);
        blocks.put("farmerExcessDebitNonCashList", excessNonCashList);
        blocks.put("farmerExcessDebitNonCash", farmerExcessDebitNonCash);
        return blocks;
    }

    @Override
    public Map<String, Object> loadBook(Long clientId, LocalDate bookDate) {
        logger.info("loadBook: clientId={}, bookDate={}", clientId, bookDate);
        Map<String, Object> header = dailyCashBookDao.findHeaderByClientAndDate(clientId, bookDate);
        if (header == null) {
            return null;
        }
        Long headerId = (Long) header.get("dailyCashBookId");
        List<Map<String, Object>> details = dailyCashBookDao.findDetailsByHeaderId(headerId);
        List<Map<String, Object>> cashList = new ArrayList<>();
        List<Map<String, Object>> nonCashList = new ArrayList<>();
        for (Map<String, Object> detail : details) {
            if ("FARMER_EXCESS_DEBIT_CASH".equals(detail.get("entryType"))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("farmerId", detail.get("farmerId"));
                row.put("farmerName", detail.get("entryLabel"));
                row.put("amount", detail.get("amount"));
                cashList.add(row);
            } else if ("FARMER_EXCESS_DEBIT_NONCASH".equals(detail.get("entryType"))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("farmerId", detail.get("farmerId"));
                row.put("farmerName", detail.get("entryLabel"));
                row.put("amount", detail.get("amount"));
                nonCashList.add(row);
            }
        }
        header.put("farmerExcessDebitCashList", cashList);
        header.put("farmerExcessDebitNonCashList", nonCashList);
        return header;
    }

    @Override
    public Map<String, Object> saveBook(Long clientId, String clientUsername, DailyCashBookSaveRequest request) {
        if (request.getBookDate() == null || request.getBookDate().trim().isEmpty()) {
            throw new IllegalArgumentException("Book date is required");
        }
        LocalDate bookDate;
        try {
            bookDate = LocalDate.parse(request.getBookDate().trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Book date must be yyyy-MM-dd");
        }
        if (!bookDate.equals(LocalDate.now())) {
            throw new IllegalArgumentException("Only today's cash book can be saved");
        }
        logger.info("saveBook: clientId={}, bookDate={}", clientId, bookDate);

        // Snapshot live system values at save time — never trust client-supplied block totals.
        Map<String, Object> blocks = computeBlocks(clientId, bookDate);
        BigDecimal buyerPurchaseTotal = (BigDecimal) blocks.get("buyerPurchaseTotal");
        BigDecimal farmerExcessDebitCash = (BigDecimal) blocks.get("farmerExcessDebitCash");
        BigDecimal buyerReceivedTotal = (BigDecimal) blocks.get("buyerReceivedTotal");
        BigDecimal commissionTotal = (BigDecimal) blocks.get("commissionTotal");
        BigDecimal installmentTotal = (BigDecimal) blocks.get("installmentTotal");
        BigDecimal farmerExcessDebitNonCash = (BigDecimal) blocks.get("farmerExcessDebitNonCash");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cashList =
                (List<Map<String, Object>>) blocks.get("farmerExcessDebitCashList");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nonCashList =
                (List<Map<String, Object>>) blocks.get("farmerExcessDebitNonCashList");

        BigDecimal rent = nz(request.getRentAmt());
        BigDecimal expense = nz(request.getExpenseAmt());
        BigDecimal chit = nz(request.getChitAmt());
        BigDecimal finance = nz(request.getFinanceAmt());
        BigDecimal note = nz(request.getNoteAmt());
        BigDecimal salary = nz(request.getSalaryAmt());
        BigDecimal coin = nz(request.getCoinAmt());
        BigDecimal cashInHand = nz(request.getCashInHandAmt());
        BigDecimal openingBalance = nz(request.getOpeningBalance());

        BigDecimal totalDebit = RoundOffUtil.round(rent.add(expense).add(chit).add(finance)
                .add(note).add(salary).add(coin).add(buyerPurchaseTotal).add(farmerExcessDebitCash));
        BigDecimal totalCredit = RoundOffUtil.round(cashInHand.add(openingBalance)
                .add(buyerReceivedTotal).add(commissionTotal).add(installmentTotal));
        BigDecimal closing = RoundOffUtil.round(totalDebit.subtract(totalCredit));

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("clientId", clientId);
        header.put("clientUsername", clientUsername);
        header.put("bookDate", bookDate);
        header.put("rentAmt", rent);
        header.put("expenseAmt", expense);
        header.put("chitAmt", chit);
        header.put("financeAmt", finance);
        header.put("noteAmt", note);
        header.put("salaryAmt", salary);
        header.put("coinAmt", coin);
        header.put("buyerPurchaseTotal", buyerPurchaseTotal);
        header.put("farmerExcessDebitCash", farmerExcessDebitCash);
        header.put("cashInHandAmt", cashInHand);
        header.put("openingBalance", openingBalance);
        header.put("buyerReceivedTotal", buyerReceivedTotal);
        header.put("commissionTotal", commissionTotal);
        header.put("installmentTotal", installmentTotal);
        header.put("farmerExcessDebitNonCash", farmerExcessDebitNonCash);
        header.put("totalDebitSide", totalDebit);
        header.put("totalCreditSide", totalCredit);
        header.put("closingBalance", closing);
        header.put("remarks", request.getRemarks() != null ? request.getRemarks().trim() : null);

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            Long headerId = dailyCashBookDao.upsertHeader(header, conn);
            dailyCashBookDao.deleteDetailsByHeaderId(headerId, conn);
            int order = 0;
            for (Map<String, Object> row : cashList) {
                dailyCashBookDao.insertDetail(headerId, clientId, clientUsername, bookDate,
                        "FARMER_EXCESS_DEBIT_CASH", String.valueOf(row.get("farmerName")),
                        row.get("farmerId") != null ? String.valueOf(row.get("farmerId")) : null,
                        (BigDecimal) row.get("amount"), order++, conn);
            }
            for (Map<String, Object> row : nonCashList) {
                dailyCashBookDao.insertDetail(headerId, clientId, clientUsername, bookDate,
                        "FARMER_EXCESS_DEBIT_NONCASH", String.valueOf(row.get("farmerName")),
                        row.get("farmerId") != null ? String.valueOf(row.get("farmerId")) : null,
                        (BigDecimal) row.get("amount"), order++, conn);
            }
            conn.commit();
            logger.info("saveBook: committed, headerId={}", headerId);
        } catch (Exception e) {
            logger.error("saveBook: failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("saveBook: rollback failed", rollbackEx);
                }
            }
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to save daily cash book", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception closeEx) {
                    logger.error("saveBook: close failed", closeEx);
                }
            }
        }
        return loadBook(clientId, bookDate);
    }

    private BigDecimal sumList(List<Map<String, Object>> rows) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            Object amount = row.get("amount");
            if (amount instanceof BigDecimal) {
                sum = sum.add((BigDecimal) amount);
            }
        }
        return RoundOffUtil.round(sum);
    }

    private BigDecimal nz(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amounts must not be negative");
        }
        return RoundOffUtil.round(value);
    }
}
