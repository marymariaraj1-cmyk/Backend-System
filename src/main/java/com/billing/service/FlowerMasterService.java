package com.billing.service;

import com.billing.entity.FlowerMaster;

import java.util.List;

public interface FlowerMasterService {
    FlowerMaster saveFlower(FlowerMaster flowerMaster);
    FlowerMaster getFlowerById(String flowerId);
    List<FlowerMaster> getFlowersByClientId(Long clientId);
    boolean existsByClientIdAndFlowerName(Long clientId, String flowerName);
    boolean existsByClientIdAndFlowerNameExcludingId(Long clientId, String flowerName, String flowerId);
    void deleteFlower(String flowerId, Long clientId);
}
