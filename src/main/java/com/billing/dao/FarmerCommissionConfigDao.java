package com.billing.dao;
import com.billing.entity.FarmerCommissionConfig;
import java.sql.Connection;
import java.util.List;
public interface FarmerCommissionConfigDao {
    FarmerCommissionConfig findByClientId(Long clientId, Connection conn);
    FarmerCommissionConfig findByClientId(Long clientId);
    List<FarmerCommissionConfig> findAllByClientId(Long clientId);
    void saveOrUpdate(FarmerCommissionConfig config, Connection conn);
    void saveOrUpdate(FarmerCommissionConfig config);
}