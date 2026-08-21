package com.billing.service.impl;

import com.billing.dao.FlowerMasterDao;
import com.billing.entity.FlowerMaster;
import com.billing.service.FlowerMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlowerMasterServiceImpl implements FlowerMasterService {

    private static final Logger logger = LoggerFactory.getLogger(FlowerMasterServiceImpl.class);
    private final FlowerMasterDao flowerMasterDao;

    @Autowired
    public FlowerMasterServiceImpl(FlowerMasterDao flowerMasterDao) {
        this.flowerMasterDao = flowerMasterDao;
    }

    @Override
    public FlowerMaster saveFlower(FlowerMaster flowerMaster) {
        return flowerMasterDao.save(flowerMaster);
    }

    @Override
    public FlowerMaster getFlowerById(String flowerId) {
        return flowerMasterDao.findById(flowerId);
    }

    @Override
    public List<FlowerMaster> getFlowersByClientId(Long clientId) {
        return flowerMasterDao.findByClientId(clientId);
    }

    @Override
    public boolean existsByClientIdAndFlowerName(Long clientId, String flowerName) {
        return flowerMasterDao.existsByClientIdAndFlowerName(clientId, flowerName);
    }

    @Override
    public boolean existsByClientIdAndFlowerNameExcludingId(Long clientId, String flowerName, String flowerId) {
        return flowerMasterDao.existsByClientIdAndFlowerNameExcludingId(clientId, flowerName, flowerId);
    }

    @Override
    public void deleteFlower(String flowerId, Long clientId) {
        flowerMasterDao.deleteById(flowerId, clientId);
    }
}
