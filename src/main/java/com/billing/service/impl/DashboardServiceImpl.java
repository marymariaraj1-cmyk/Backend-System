package com.billing.service.impl;

import com.billing.dao.DashboardDao;
import com.billing.dto.DashboardData;
import com.billing.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardServiceImpl.class);

    private static final int TREND_DAYS = 7;

    private final DashboardDao dashboardDao;

    @Autowired
    public DashboardServiceImpl(DashboardDao dashboardDao) {
        this.dashboardDao = dashboardDao;
    }

    @Override
    public DashboardData getDashboardData(Long clientId) {
        if (clientId == null) {
            logger.info("getDashboardData: no client in session, returning empty dashboard");
            return emptyDashboard();
        }

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(TREND_DAYS - 1);
        LocalDate monthStart = today.withDayOfMonth(1);

        DashboardData data = new DashboardData();
        data.setKpis(dashboardDao.findTodayKpi(clientId));
        data.setMasterCounts(dashboardDao.findMasterCounts(clientId));
        data.setTrend(fillMissingTrendDays(dashboardDao.findSalesTrend(clientId, from, today), from, today));
        data.setMonthTrend(fillMissingTrendDays(dashboardDao.findSalesTrend(clientId, monthStart, today), monthStart, today));
        data.setTopFlowers(dashboardDao.findTopFlowersByWeight(clientId, from, today));
        data.setTopFarmers(dashboardDao.findTopFarmersBySales(clientId, from, today));
        data.setOutstandingFarmers(dashboardDao.findTopOutstandingFarmers(clientId));
        data.setOutstandingBuyers(dashboardDao.findTopOutstandingBuyers(clientId));
        data.setDirectPayments(loadDirectPayments(clientId, today, monthStart));

        logger.info("getDashboardData: clientId={}, trendDays={}", clientId, TREND_DAYS);
        return data;
    }

    private Map<String, Object> loadDirectPayments(Long clientId, LocalDate today, LocalDate monthStart) {
        Map<String, Object> todayPayments = dashboardDao.findDirectPayments(clientId, today, today);
        Map<String, Object> monthPayments = dashboardDao.findDirectPayments(clientId, monthStart, today);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cashToday", todayPayments.getOrDefault("cash", BigDecimal.ZERO));
        result.put("upiToday", todayPayments.getOrDefault("upi", BigDecimal.ZERO));
        result.put("otherToday", todayPayments.getOrDefault("other", BigDecimal.ZERO));
        result.put("cashMonth", monthPayments.getOrDefault("cash", BigDecimal.ZERO));
        result.put("upiMonth", monthPayments.getOrDefault("upi", BigDecimal.ZERO));
        result.put("otherMonth", monthPayments.getOrDefault("other", BigDecimal.ZERO));
        return result;
    }

    private List<Map<String, Object>> fillMissingTrendDays(List<Map<String, Object>> rows, LocalDate from, LocalDate to) {
        Map<String, Map<String, Object>> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byDate.put(String.valueOf(row.get("salesDate")), row);
        }
        List<Map<String, Object>> filled = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = d.toString();
            Map<String, Object> row = byDate.get(key);
            if (row == null) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("salesDate", key);
                empty.put("amount", BigDecimal.ZERO);
                filled.add(empty);
            } else {
                filled.add(row);
            }
        }
        return filled;
    }

    private DashboardData emptyDashboard() {
        DashboardData data = new DashboardData();
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("todaySales", BigDecimal.ZERO);
        kpis.put("todayCommission", BigDecimal.ZERO);
        kpis.put("todayKg", BigDecimal.ZERO);
        kpis.put("activeFarmers", 0);
        kpis.put("activeBuyers", 0);
        kpis.put("yesterdaySales", BigDecimal.ZERO);
        kpis.put("yesterdayCommission", BigDecimal.ZERO);
        data.setKpis(kpis);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("farmers", 0);
        counts.put("buyers", 0);
        counts.put("flowers", 0);
        data.setMasterCounts(counts);
        return data;
    }
}
