package com.billing.service;

import com.billing.entity.BuyerMaster;

import java.util.List;

public interface BuyerMasterService {
    BuyerMaster saveBuyer(BuyerMaster buyerMaster);
    BuyerMaster getBuyerById(String buyerId);
    List<BuyerMaster> getBuyersByClientId(Long clientId);
    boolean existsByClientIdAndBuyerName(Long clientId, String buyerName);
    boolean existsByClientIdAndBuyerNameExcludingId(Long clientId, String buyerName, String buyerId);
    void deleteBuyer(String buyerId, Long clientId);
}
