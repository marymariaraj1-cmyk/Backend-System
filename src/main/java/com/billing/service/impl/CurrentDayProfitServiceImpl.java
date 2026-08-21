package com.billing.service.impl;

import com.billing.dao.CurrentDayProfitDao;
import com.billing.service.CurrentDayProfitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CurrentDayProfitServiceImpl implements CurrentDayProfitService {

    private static final Logger logger = LoggerFactory.getLogger(CurrentDayProfitServiceImpl.class);

    private final CurrentDayProfitDao currentDayProfitDao;

    public CurrentDayProfitServiceImpl(CurrentDayProfitDao currentDayProfitDao) {
        this.currentDayProfitDao = currentDayProfitDao;
    }

    @Override
    public List<Map<String, Object>> getTodaySummary(Long clientId) {
        logger.info("getTodaySummary: clientId={}", clientId);
        return currentDayProfitDao.findTodaySummary(clientId);
    }

    @Override
    public List<Map<String, Object>> getTodaySales(Long clientId, String farmerId) {
        logger.info("getTodaySales: clientId={}, farmerId={}", clientId, farmerId);
        return currentDayProfitDao.findTodaySales(clientId, farmerId);
    }
}
