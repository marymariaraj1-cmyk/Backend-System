package com.billing.service.impl;

import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerSalesReportDao;
import com.billing.dao.SalesTotalSummaryDao;
import com.billing.service.FarmerSalesReportService;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FarmerSalesReportServiceImpl implements FarmerSalesReportService {

    private static final Logger logger = LoggerFactory.getLogger(FarmerSalesReportServiceImpl.class);

    private final FarmerSalesReportDao farmerSalesReportDao;
    private final FarmerLedgerDao farmerLedgerDao;
    private final SalesTotalSummaryDao salesTotalSummaryDao;

    public FarmerSalesReportServiceImpl(FarmerSalesReportDao farmerSalesReportDao,
                                        FarmerLedgerDao farmerLedgerDao,
                                        SalesTotalSummaryDao salesTotalSummaryDao) {
        this.farmerSalesReportDao = farmerSalesReportDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.salesTotalSummaryDao = salesTotalSummaryDao;
    }

    @Override
    public Map<String, Object> getFarmerSalesByDate(Long clientId, String clientUsername, String farmerId, LocalDate date, String ledgerActive, BigDecimal creditAmt) {
        logger.info("getFarmerSalesByDate: clientId={}, farmerId={}, date={}, ledgerActive={}, creditAmt={}", clientId, farmerId, date, ledgerActive, creditAmt);
        String salesIdsStr = farmerLedgerDao.findSalesIds(clientId, farmerId, date, ledgerActive);
        List<Map<String, Object>> rows;
        if (salesIdsStr == null || salesIdsStr.trim().isEmpty()) {
            rows = Collections.emptyList();
        } else {
            List<Long> ids = parseSalesIds(salesIdsStr);
            rows = ids.isEmpty() ? Collections.emptyList() : farmerSalesReportDao.findSalesByIds(clientId, ids);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            Object priceObj = row.get("price");
            if (priceObj instanceof BigDecimal) {
                total = total.add((BigDecimal) priceObj);
            }
        }
        total = RoundOffUtil.round(total);

        BigDecimal commission = RoundOffUtil.round(total.multiply(new BigDecimal("0.10")));
        BigDecimal netAmount = RoundOffUtil.round(total.subtract(commission));

        BigDecimal finalTotal = creditAmt != null ? RoundOffUtil.round(creditAmt) : BigDecimal.ZERO;
        BigDecimal debit = netAmount.compareTo(finalTotal) == 0 ? BigDecimal.ZERO : RoundOffUtil.round(netAmount.subtract(finalTotal));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("commission", commission);
        summary.put("netAmount", netAmount);
        summary.put("debit", debit);
        summary.put("finalTotal", finalTotal);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", rows);
        data.put("summary", summary);
        return data;
    }

    private List<Long> parseSalesIds(String salesIdsStr) {
        if (salesIdsStr == null || salesIdsStr.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = salesIdsStr.split(",");
        List<Long> ids = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    ids.add(Long.parseLong(trimmed));
                } catch (NumberFormatException e) {
                    logger.warn("parseSalesIds: skipping invalid ID '{}'", trimmed);
                }
            }
        }
        return ids;
    }

    @Override
    public Map<String, Object> getFarmerSummaryByDate(Long clientId, String farmerId, LocalDate date) {
        logger.info("getFarmerSummaryByDate: clientId={}, farmerId={}, date={}", clientId, farmerId, date);
        Map<String, Object> summary = salesTotalSummaryDao.findRow(clientId, farmerId, date);
        if (summary == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("farmerId", summary.get("farmerId"));
        out.put("farmerName", summary.get("farmerName"));
        out.put("salesDate", summary.get("salesDate"));
        out.put("total", RoundOffUtil.round((BigDecimal) summary.get("totalSalesAmt")));
        out.put("commission", RoundOffUtil.round((BigDecimal) summary.get("commissionAmt")));
        out.put("netAmount", RoundOffUtil.round((BigDecimal) summary.get("totalNetAmt")));
        out.put("debit", RoundOffUtil.round((BigDecimal) summary.get("debitAmt")));
        out.put("finalTotal", RoundOffUtil.round((BigDecimal) summary.get("finalAmt")));
        return out;
    }

    @Override
    public List<Map<String, Object>> getFarmerSalesReport(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate) {
        logger.info("getFarmerSalesReport: clientId={}, clientUsername={}, fromDate={}, toDate={}", clientId, clientUsername, fromDate, toDate);

        List<Map<String, Object>> summaryRows = salesTotalSummaryDao.findSummaryByDateRange(clientId, fromDate, toDate);
        List<Map<String, Object>> rawRows = farmerSalesReportDao.findSalesByDateRange(clientId, clientUsername, fromDate, toDate);

        Map<String, List<Map<String, Object>>> itemsByFarmer = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            String farmerId = (String) row.get("farmerId");
            itemsByFarmer.computeIfAbsent(farmerId, k -> new ArrayList<>()).add(row);
        }

        Map<String, Map<String, Object>> byFarmer = new LinkedHashMap<>();
        for (Map<String, Object> summary : summaryRows) {
            String farmerId = (String) summary.get("farmerId");
            Map<String, Object> farmerRow = byFarmer.computeIfAbsent(farmerId, k -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("farmerId", farmerId);
                map.put("farmerName", summary.get("farmerName"));
                map.put("total", BigDecimal.ZERO);
                map.put("commission", BigDecimal.ZERO);
                map.put("debit", BigDecimal.ZERO);
                map.put("netAmount", BigDecimal.ZERO);
                map.put("totalNetAmt", BigDecimal.ZERO);
                map.put("finalTotal", BigDecimal.ZERO);
                map.put("items", itemsByFarmer.getOrDefault(farmerId, new ArrayList<>()));
                return map;
            });
            farmerRow.put("total", ((BigDecimal) farmerRow.get("total"))
                    .add((BigDecimal) summary.get("totalSalesAmt")));
            farmerRow.put("commission", ((BigDecimal) farmerRow.get("commission"))
                    .add((BigDecimal) summary.get("commissionAmt")));
            farmerRow.put("debit", ((BigDecimal) farmerRow.get("debit"))
                    .add((BigDecimal) summary.get("debitAmt")));
            farmerRow.put("netAmount", ((BigDecimal) farmerRow.get("netAmount"))
                    .add((BigDecimal) summary.get("totalNetAmt")));
            farmerRow.put("totalNetAmt", ((BigDecimal) farmerRow.get("totalNetAmt"))
                    .add((BigDecimal) summary.get("finalAmt")));
            farmerRow.put("finalTotal", ((BigDecimal) farmerRow.get("finalTotal"))
                    .add((BigDecimal) summary.get("finalAmt")));
        }

        List<Map<String, Object>> report = new ArrayList<>();
        for (Map<String, Object> farmerRow : byFarmer.values()) {
            farmerRow.put("total", RoundOffUtil.round((BigDecimal) farmerRow.get("total")));
            farmerRow.put("commission", RoundOffUtil.round((BigDecimal) farmerRow.get("commission")));
            farmerRow.put("debit", RoundOffUtil.round((BigDecimal) farmerRow.get("debit")));
            farmerRow.put("netAmount", RoundOffUtil.round((BigDecimal) farmerRow.get("netAmount")));
            farmerRow.put("totalNetAmt", RoundOffUtil.round((BigDecimal) farmerRow.get("totalNetAmt")));
            farmerRow.put("finalTotal", RoundOffUtil.round((BigDecimal) farmerRow.get("finalTotal")));
            report.add(farmerRow);
        }

        logger.info("getFarmerSalesReport: farmers={}", report.size());
        return report;
    }

    @Override
    public List<Map<String, Object>> getTodayFarmerSales(Long clientId, String clientUsername) {
        logger.info("getTodayFarmerSales: clientId={}, clientUsername={}", clientId, clientUsername);

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> summaryRows = salesTotalSummaryDao.findSummaryForDate(clientId, today);
        List<Map<String, Object>> rawRows = farmerSalesReportDao.findTodaySales(clientId, clientUsername);

        Map<String, List<Map<String, Object>>> itemsByFarmer = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            String farmerId = (String) row.get("farmerId");
            itemsByFarmer.computeIfAbsent(farmerId, k -> new ArrayList<>()).add(row);
        }

        Map<String, Map<String, Object>> byFarmer = new LinkedHashMap<>();
        for (Map<String, Object> summary : summaryRows) {
            String farmerId = (String) summary.get("farmerId");
            Map<String, Object> farmerRow = byFarmer.computeIfAbsent(farmerId, k -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("farmerId", farmerId);
                map.put("farmerName", summary.get("farmerName"));
                map.put("total", BigDecimal.ZERO);
                map.put("commission", BigDecimal.ZERO);
                map.put("debit", BigDecimal.ZERO);
                map.put("netAmount", BigDecimal.ZERO);
                map.put("totalNetAmt", BigDecimal.ZERO);
                map.put("finalTotal", BigDecimal.ZERO);
                map.put("items", itemsByFarmer.getOrDefault(farmerId, new ArrayList<>()));
                return map;
            });
            farmerRow.put("total", ((BigDecimal) farmerRow.get("total"))
                    .add((BigDecimal) summary.get("totalSalesAmt")));
            farmerRow.put("commission", ((BigDecimal) farmerRow.get("commission"))
                    .add((BigDecimal) summary.get("commissionAmt")));
            farmerRow.put("debit", ((BigDecimal) farmerRow.get("debit"))
                    .add((BigDecimal) summary.get("debitAmt")));
            farmerRow.put("netAmount", ((BigDecimal) farmerRow.get("netAmount"))
                    .add((BigDecimal) summary.get("totalNetAmt")));
            farmerRow.put("totalNetAmt", ((BigDecimal) farmerRow.get("totalNetAmt"))
                    .add((BigDecimal) summary.get("finalAmt")));
            farmerRow.put("finalTotal", ((BigDecimal) farmerRow.get("finalTotal"))
                    .add((BigDecimal) summary.get("finalAmt")));
        }

        List<Map<String, Object>> report = new ArrayList<>();
        for (Map<String, Object> farmerRow : byFarmer.values()) {
            farmerRow.put("total", RoundOffUtil.round((BigDecimal) farmerRow.get("total")));
            farmerRow.put("commission", RoundOffUtil.round((BigDecimal) farmerRow.get("commission")));
            farmerRow.put("debit", RoundOffUtil.round((BigDecimal) farmerRow.get("debit")));
            farmerRow.put("netAmount", RoundOffUtil.round((BigDecimal) farmerRow.get("netAmount")));
            farmerRow.put("totalNetAmt", RoundOffUtil.round((BigDecimal) farmerRow.get("totalNetAmt")));
            farmerRow.put("finalTotal", RoundOffUtil.round((BigDecimal) farmerRow.get("finalTotal")));
            report.add(farmerRow);
        }

        logger.info("getTodayFarmerSales: farmers={}", report.size());
        return report;
    }
}
