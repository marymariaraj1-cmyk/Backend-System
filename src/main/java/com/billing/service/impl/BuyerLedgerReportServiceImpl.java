package com.billing.service.impl;

import com.billing.dao.BuyerLedgerReportDao;
import com.billing.service.BuyerLedgerReportService;
import com.billing.util.RoundOffUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BuyerLedgerReportServiceImpl implements BuyerLedgerReportService {

    private final BuyerLedgerReportDao buyerLedgerReportDao;

    public BuyerLedgerReportServiceImpl(BuyerLedgerReportDao buyerLedgerReportDao) {
        this.buyerLedgerReportDao = buyerLedgerReportDao;
    }

    @Override
    public List<Map<String, Object>> getBuyerLedgerReport(Long clientId, String clientUsername, String buyerId,
                                                          LocalDate fromDate, LocalDate toDate) {
        return buyerLedgerReportDao.findByDateRange(clientId, clientUsername, buyerId, fromDate, toDate);
    }

    @Override
    public List<Map<String, Object>> getBuyerList(Long clientId) {
        return buyerLedgerReportDao.findBuyerList(clientId);
    }

    @Override
    public List<Map<String, Object>> getBuyerLedgerDetail(Long clientId, String clientUsername, String buyerId,
                                                          LocalDate fromDate, LocalDate toDate) {
        List<Map<String, Object>> rows = buyerLedgerReportDao.findAll(clientId, clientUsername, buyerId);
        return buildDetail(rows, clientId, buyerId, true, fromDate, toDate);
    }

    @Override
    public List<Map<String, Object>> getBuyerLedgerDetailAll(Long clientId, String clientUsername, String buyerId) {
        List<Map<String, Object>> rows = buyerLedgerReportDao.findAll(clientId, clientUsername, buyerId);
        return buildDetail(rows, clientId, buyerId, false, null, null);
    }

    @Override
    public List<Map<String, Object>> getBuyerLedgerReportDetail(Long clientId, String clientUsername, String buyerId,
                                                                LocalDate fromDate, LocalDate toDate) {
        List<Map<String, Object>> rows = buyerLedgerReportDao.findAll(clientId, clientUsername, buyerId);
        return buildDetail(rows, clientId, buyerId, false, fromDate, toDate);
    }

    private List<Map<String, Object>> buildDetail(List<Map<String, Object>> rows, Long clientId, String buyerId,
                                                  boolean activeOnly, LocalDate fromDate, LocalDate toDate) {
        List<Map<String, Object>> result = new ArrayList<>();
        BigDecimal obValue = buyerLedgerReportDao.findConfiguredOpeningBalance(clientId, buyerId);
        LocalDate obDate = buyerLedgerReportDao.findConfiguredOpeningBalanceDate(clientId, buyerId);
        BigDecimal running = BigDecimal.ZERO;
        BigDecimal activeRunning = BigDecimal.ZERO;
        boolean haveActive = false;
        for (Map<String, Object> row : rows) {
            LocalDate date = LocalDate.parse((String) row.get("salesDate"));
            boolean active = row.get("ledgerActive") == null
                    || "Y".equalsIgnoreCase(((String) row.get("ledgerActive")).trim());
            BigDecimal opening;
            BigDecimal storedOpening = row.get("openingBalanceStored") instanceof BigDecimal
                    ? (BigDecimal) row.get("openingBalanceStored") : null;
            if (active) {
                opening = haveActive ? activeRunning : BigDecimal.ZERO;
                if (obValue != null && obDate != null && obDate.equals(date)) {
                    opening = opening.add(obValue);
                }
            } else {
                opening = storedOpening != null ? storedOpening : running;
            }
            BigDecimal debit = toDecimal(row.get("debitAmt"));
            BigDecimal credit = toDecimal(row.get("creditAmt"));
            BigDecimal discount = toDecimal(row.get("disAmt"));
            BigDecimal closing = RoundOffUtil.round(opening.add(debit).subtract(credit));
            boolean inRange = (fromDate == null || !date.isBefore(fromDate))
                    && (toDate == null || !date.isAfter(toDate));
            if (inRange && (!activeOnly || active)) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("salesDate", row.get("salesDate"));
                out.put("openingBalance", RoundOffUtil.round(opening));
                out.put("purchase", RoundOffUtil.round(debit));
                out.put("cash", RoundOffUtil.round(credit));
                out.put("discount", RoundOffUtil.round(discount));
                out.put("closingBalance", closing);
                out.put("ledgerActive", row.get("ledgerActive"));
                result.add(out);
            }
            running = closing;
            if (active) {
                activeRunning = closing;
                haveActive = true;
            }
        }
        return result;
    }

    private BigDecimal toDecimal(Object value) {
        return value instanceof BigDecimal ? (BigDecimal) value : BigDecimal.ZERO;
    }
}
