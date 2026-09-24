package com.billing.service.impl;

import com.billing.dao.BagCountConfigDao;
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

    public BagCountConfigServiceImpl(BagCountConfigDao bagCountConfigDao) {
        this.bagCountConfigDao = bagCountConfigDao;
    }

    @Override
    public List<Map<String, Object>> getFlowers(Long clientId) {
        return bagCountConfigDao.findAllFlowers(clientId);
    }

    @Override
    public List<Map<String, Object>> getFarmers(Long clientId) {
        return bagCountConfigDao.findAllFarmers(clientId);
    }

    @Override
    public List<Map<String, Object>> getConfigs(Long clientId) {
        return bagCountConfigDao.findAll(clientId);
    }

    @Override
    public Map<String, Object> getConfig(Long clientId, String farmerId, String flowerId, LocalDate salesDate) {
        return bagCountConfigDao.findByFarmerFlowerAndDate(clientId, farmerId, flowerId, salesDate);
    }

    @Override
    public List<Map<String, Object>> getConfigReport(Long clientId, String farmerId,
                                                     LocalDate fromDate, LocalDate toDate) {
        return bagCountConfigDao.findForReport(clientId, farmerId, fromDate, toDate);
    }

    @Override
    public void saveConfig(Long clientId, String clientUsername, String farmerId, String farmerName,
                           String flowerId, String flowerName, LocalDate salesDate, Integer bagCount) {
        bagCountConfigDao.upsert(clientId, clientUsername, farmerId, farmerName,
                flowerId, flowerName, salesDate, bagCount);
    }

    @Override
    public void deleteConfig(Long clientId, String farmerId, String flowerId, LocalDate salesDate) {
        bagCountConfigDao.delete(clientId, farmerId, flowerId, salesDate);
    }

    @Override
    public int getSavedBagTotal(Long clientId, String farmerId, String flowerId, LocalDate salesDate) {
        Integer total = bagCountConfigDao.sumBagCountForFarmerFlowerAndDate(clientId, farmerId, flowerId, salesDate);
        return total == null ? 0 : total;
    }
}