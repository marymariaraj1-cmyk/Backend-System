package com.billing.service;

import com.billing.entity.FarmerMaster;

import java.util.List;

public interface FarmerMasterService {
    FarmerMaster saveFarmer(FarmerMaster farmerMaster);
    FarmerMaster getFarmerById(String farmerId);
    List<FarmerMaster> getFarmersByClientId(Long clientId);
    boolean existsByClientIdAndFarmerName(Long clientId, String farmerName);
    boolean existsByClientIdAndFarmerNameExcludingId(Long clientId, String farmerName, String farmerId);
    void deleteFarmer(String farmerId, Long clientId);
}
