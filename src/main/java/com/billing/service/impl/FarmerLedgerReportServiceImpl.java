package com.billing.service.impl;

import com.billing.dao.FarmerLedgerReportDao;
import com.billing.service.FarmerLedgerReportService;
import com.billing.util.RoundOffUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FarmerLedgerReportServiceImpl implements FarmerLedgerReportService {

    private final FarmerLedgerReportDao farmerLedgerReportDao;

    public FarmerLedgerReportServiceImpl(FarmerLedgerReportDao farmerLedgerReportDao) {
        this.farmerLedgerReportDao = farmerLedgerReportDao;
    }

    @Override
    public List<Map<String, Object>> getFarmerLedgerReport(Long clientId, String clientUsername, String farmerId,
                                                           LocalDate fromDate, LocalDate toDate) {
        return farmerLedgerReportDao.findByDateRange(clientId, clientUsername, farmerId, fromDate, toDate);
    }

    @Override
    public List<Map<String, Object>> getFarmerList(Long clientId) {
        return farmerLedgerReportDao.findFarmerList(clientId);
    }

    @Override
    public List<Map<String, Object>> getFarmerLedgerDetail(Long clientId, String clientUsername, String farmerId,
                                                           LocalDate fromDate, LocalDate toDate) {
        List<Map<String, Object>> rows = farmerLedgerReportDao.findAll(clientId, clientUsername, farmerId);
        return buildDetail(rows, clientId, farmerId, true, fromDate, toDate);
    }

    @Override
    public List<Map<String, Object>> getFarmerLedgerDetailAll(Long clientId, String clientUsername, String farmerId) {
        List<Map<String, Object>> rows = farmerLedgerReportDao.findAll(clientId, clientUsername, farmerId);
        return buildDetail(rows, clientId, farmerId, false, null, null);
    }

    @Override
    public List<Map<String, Object>> getFarmerLedgerReportDetail(Long clientId, String clientUsername, String farmerId,
                                                                 LocalDate fromDate, LocalDate toDate) {
        List<Map<String, Object>> rows = farmerLedgerReportDao.findAll(clientId, clientUsername, farmerId);
        return buildDetail(rows, clientId, farmerId, false, fromDate, toDate);
    }

    private List<Map<String, Object>> buildDetail(List<Map<String, Object>> rows, Long clientId, String farmerId,
                                                  boolean activeOnly, LocalDate fromDate, LocalDate toDate) {
        List<Map<String, Object>> result = new ArrayList<>();
        BigDecimal obValue = farmerLedgerReportDao.findConfiguredOpeningBalance(clientId, farmerId);
        LocalDate obDate = farmerLedgerReportDao.findConfiguredOpeningBalanceDate(clientId, farmerId);
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
            BigDecimal credit = toDecimal(row.get("creditAmt"));
            BigDecimal debit = toDecimal(row.get("debitAmt"));
            BigDecimal closing = RoundOffUtil.round(opening.add(credit).subtract(debit));
            boolean inRange = (fromDate == null || !date.isBefore(fromDate))
                    && (toDate == null || !date.isAfter(toDate));
            if (inRange && (!activeOnly || active)) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("salesDate", row.get("salesDate"));
                out.put("openingBalance", RoundOffUtil.round(opening));
                out.put("sales", RoundOffUtil.round(credit));
                out.put("creditAmount", RoundOffUtil.round(debit));
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
