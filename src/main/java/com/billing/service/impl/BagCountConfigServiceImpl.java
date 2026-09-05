package com.billing.service.impl;

import com.billing.dao.BagCountConfigDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.service.BagCountConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class BagCountConfigServiceImpl implements BagCountConfigService {

    private static final Logger logger = LoggerFactory.getLogger(BagCountConfigServiceImpl.class);

    private final BagCountConfigDao bagCountConfigDao;
    private final FlowerMasterDao flowerMasterDao;

    public BagCountConfigServiceImpl(BagCountConfigDao bagCountConfigDao, FlowerMasterDao flowerMasterDao) {
        this.bagCountConfigDao = bagCountConfigDao;
        this.flowerMasterDao = flowerMasterDao;
    }

    @Override
    public List<Map<String, Object>> getFlowers(Long clientId) {
        return bagCountConfigDao.findAllFlowers(clientId);
    }

    @Override
    public List<Map<String, Object>> getConfigs(Long clientId) {
        List<Map<String, Object>> configs = bagCountConfigDao.findAll(clientId);
        return configs;
    }

    @Override
    public Map<String, Object> getConfig(Long clientId, String flowerId, LocalDate salesDate) {
        return bagCountConfigDao.findByFlowerAndDate(clientId, flowerId, salesDate);
    }

    @Override
    public void saveConfig(Long clientId, String clientUsername, String flowerId, String flowerName,
                           LocalDate salesDate, Integer bagCount, String bagCheck) {
        String check = (bagCheck == null || bagCheck.trim().isEmpty()) ? "D" : bagCheck.trim().toUpperCase();
        if (!check.equals("E") && !check.equals("D")) {
            check = "D";
        }
        bagCountConfigDao.upsert(clientId, clientUsername, flowerId, flowerName, salesDate, bagCount, check);
    }

    @Override
    public void deleteConfig(Long clientId, String flowerId, LocalDate salesDate) {
        bagCountConfigDao.delete(clientId, flowerId, salesDate);
    }

    @Override
    public int getSavedBagTotal(Long clientId, String flowerId, LocalDate salesDate) {
        Integer total = bagCountConfigDao.sumBagCountForFlowerAndDate(clientId, flowerId, salesDate);
        return total == null ? 0 : total;
    }
}
