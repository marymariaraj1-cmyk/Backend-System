package com.billing.service.impl;

import com.billing.dao.FarmerMasterDao;
import com.billing.entity.FarmerMaster;
import com.billing.service.FarmerMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FarmerMasterServiceImpl implements FarmerMasterService {

    private static final Logger logger = LoggerFactory.getLogger(FarmerMasterServiceImpl.class);
    private final FarmerMasterDao farmerMasterDao;

    @Autowired
    public FarmerMasterServiceImpl(FarmerMasterDao farmerMasterDao) {
        this.farmerMasterDao = farmerMasterDao;
    }

    @Override
    public FarmerMaster saveFarmer(FarmerMaster farmerMaster) {
        return farmerMasterDao.save(farmerMaster);
    }

    @Override
    public FarmerMaster getFarmerById(String farmerId) {
        return farmerMasterDao.findById(farmerId);
    }

    @Override
    public List<FarmerMaster> getFarmersByClientId(Long clientId) {
        return farmerMasterDao.findByClientId(clientId);
    }

    @Override
    public boolean existsByClientIdAndFarmerName(Long clientId, String farmerName) {
        return farmerMasterDao.existsByClientIdAndFarmerName(clientId, farmerName);
    }

    @Override
    public boolean existsByClientIdAndFarmerNameExcludingId(Long clientId, String farmerName, String farmerId) {
        return farmerMasterDao.existsByClientIdAndFarmerNameExcludingId(clientId, farmerName, farmerId);
    }

    @Override
    public void deleteFarmer(String farmerId, Long clientId) {
        farmerMasterDao.deleteById(farmerId, clientId);
    }
}
