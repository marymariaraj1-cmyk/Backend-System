package com.billing.dao;

import com.billing.entity.FarmerMaster;

import java.util.List;

public interface FarmerMasterDao {

    FarmerMaster save(FarmerMaster farmerMaster);

    FarmerMaster findById(String farmerId);

    List<FarmerMaster> findByClientId(Long clientId);

    boolean existsByClientIdAndFarmerName(Long clientId, String farmerName);

    boolean existsByClientIdAndFarmerNameExcludingId(Long clientId, String farmerName, String farmerId);

    List<String> findNamesByClientId(Long clientId);

    String findIdByNameAndClientId(Long clientId, String farmerName);

    void deleteById(String farmerId, Long clientId);
}
