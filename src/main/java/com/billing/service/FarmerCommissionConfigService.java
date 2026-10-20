package com.billing.service;
import com.billing.entity.FarmerCommissionConfig;
import java.math.BigDecimal;
import java.util.List;
public interface FarmerCommissionConfigService {
    BigDecimal getCommissionPercent(Long clientId);
    List<FarmerCommissionConfig> getAllConfigs(Long clientId);
    FarmerCommissionConfig saveOrUpdate(Long clientId, String clientUsername, BigDecimal commissionPercent, String description);
}