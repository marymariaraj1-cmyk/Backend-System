package com.billing.service.impl;

import com.billing.dao.BuyerMasterDao;
import com.billing.entity.BuyerMaster;
import com.billing.service.BuyerMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BuyerMasterServiceImpl implements BuyerMasterService {

    private static final Logger logger = LoggerFactory.getLogger(BuyerMasterServiceImpl.class);
    private final BuyerMasterDao buyerMasterDao;

    @Autowired
    public BuyerMasterServiceImpl(BuyerMasterDao buyerMasterDao) {
        this.buyerMasterDao = buyerMasterDao;
    }

    @Override
    public BuyerMaster saveBuyer(BuyerMaster buyerMaster) {
        return buyerMasterDao.save(buyerMaster);
    }

    @Override
    public BuyerMaster getBuyerById(String buyerId) {
        return buyerMasterDao.findById(buyerId);
    }

    @Override
    public List<BuyerMaster> getBuyersByClientId(Long clientId) {
        return buyerMasterDao.findByClientId(clientId);
    }

    @Override
    public boolean existsByClientIdAndBuyerName(Long clientId, String buyerName) {
        return buyerMasterDao.existsByClientIdAndBuyerName(clientId, buyerName);
    }

    @Override
    public boolean existsByClientIdAndBuyerNameExcludingId(Long clientId, String buyerName, String buyerId) {
        return buyerMasterDao.existsByClientIdAndBuyerNameExcludingId(clientId, buyerName, buyerId);
    }

    @Override
    public void deleteBuyer(String buyerId, Long clientId) {
        buyerMasterDao.deleteById(buyerId, clientId);
    }
}
