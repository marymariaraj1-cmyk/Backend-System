package com.billing.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardData {

    private Map<String, Object> kpis = new HashMap<>();
    private Map<String, Object> masterCounts = new HashMap<>();
    private List<Map<String, Object>> trend = new ArrayList<>();
    private List<Map<String, Object>> monthTrend = new ArrayList<>();
    private List<Map<String, Object>> topFlowers = new ArrayList<>();
    private List<Map<String, Object>> topFarmers = new ArrayList<>();
    private List<Map<String, Object>> outstandingFarmers = new ArrayList<>();
    private List<Map<String, Object>> outstandingBuyers = new ArrayList<>();
    private Map<String, Object> directPayments = new HashMap<>();

    public Map<String, Object> getKpis() {
        return kpis;
    }

    public void setKpis(Map<String, Object> kpis) {
        this.kpis = kpis;
    }

    public Map<String, Object> getMasterCounts() {
        return masterCounts;
    }

    public void setMasterCounts(Map<String, Object> masterCounts) {
        this.masterCounts = masterCounts;
    }

    public List<Map<String, Object>> getTrend() {
        return trend;
    }

    public void setTrend(List<Map<String, Object>> trend) {
        this.trend = trend;
    }

    public List<Map<String, Object>> getMonthTrend() {
        return monthTrend;
    }

    public void setMonthTrend(List<Map<String, Object>> monthTrend) {
        this.monthTrend = monthTrend;
    }

    public List<Map<String, Object>> getTopFlowers() {
        return topFlowers;
    }

    public void setTopFlowers(List<Map<String, Object>> topFlowers) {
        this.topFlowers = topFlowers;
    }

    public List<Map<String, Object>> getTopFarmers() {
        return topFarmers;
    }

    public void setTopFarmers(List<Map<String, Object>> topFarmers) {
        this.topFarmers = topFarmers;
    }

    public List<Map<String, Object>> getOutstandingFarmers() {
        return outstandingFarmers;
    }

    public void setOutstandingFarmers(List<Map<String, Object>> outstandingFarmers) {
        this.outstandingFarmers = outstandingFarmers;
    }

    public List<Map<String, Object>> getOutstandingBuyers() {
        return outstandingBuyers;
    }

    public void setOutstandingBuyers(List<Map<String, Object>> outstandingBuyers) {
        this.outstandingBuyers = outstandingBuyers;
    }

    public Map<String, Object> getDirectPayments() {
        return directPayments;
    }

    public void setDirectPayments(Map<String, Object> directPayments) {
        this.directPayments = directPayments;
    }
}
