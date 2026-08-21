package com.billing.dao;

import com.billing.entity.BuyerMaster;

import java.util.List;

public interface BuyerMasterDao {

    BuyerMaster save(BuyerMaster buyerMaster);

    BuyerMaster findById(String buyerId);

    List<BuyerMaster> findByClientId(Long clientId);

    boolean existsByClientIdAndBuyerName(Long clientId, String buyerName);

    boolean existsByClientIdAndBuyerNameExcludingId(Long clientId, String buyerName, String buyerId);

    List<String> findNamesByClientId(Long clientId);

    String findIdByNameAndClientId(Long clientId, String buyerName);

    void deleteById(String buyerId, Long clientId);
}
