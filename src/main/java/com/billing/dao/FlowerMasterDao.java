package com.billing.dao;

import com.billing.entity.FlowerMaster;

import java.util.List;

public interface FlowerMasterDao {

    FlowerMaster save(FlowerMaster flowerMaster);

    FlowerMaster findById(String flowerId);

    List<FlowerMaster> findByClientId(Long clientId);

    boolean existsByClientIdAndFlowerName(Long clientId, String flowerName);

    boolean existsByClientIdAndFlowerNameExcludingId(Long clientId, String flowerName, String flowerId);

    List<String> findNamesByClientId(Long clientId);

    void deleteById(String flowerId, Long clientId);
}
