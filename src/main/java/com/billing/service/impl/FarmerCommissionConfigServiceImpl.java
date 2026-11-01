package com.billing.service.impl;
import com.billing.dao.FarmerCommissionConfigDao;
import com.billing.entity.FarmerCommissionConfig;
import com.billing.service.FarmerCommissionConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Service
public class FarmerCommissionConfigServiceImpl implements FarmerCommissionConfigService {
    private static final Logger logger = LoggerFactory.getLogger(FarmerCommissionConfigServiceImpl.class);
    private final FarmerCommissionConfigDao dao;
    private static final BigDecimal DEFAULT = new BigDecimal("10.00");
    @Autowired
    public FarmerCommissionConfigServiceImpl(FarmerCommissionConfigDao dao) { this.dao = dao; }
    @Override public BigDecimal getCommissionPercent(Long clientId) {
        if (clientId == null) return DEFAULT;
        FarmerCommissionConfig c = dao.findByClientId(clientId);
        if (c == null || c.getCommissionPercent() == null) return DEFAULT;
        return c.getCommissionPercent();
    }
    @Override public List<FarmerCommissionConfig> getAllConfigs(Long clientId) {
        if (clientId == null) return List.of();
        return dao.findAllByClientId(clientId);
    }
    @Override public FarmerCommissionConfig saveOrUpdate(Long clientId, String clientUsername, BigDecimal commissionPercent, String description) {
        if (clientId == null) throw new IllegalArgumentException("Client ID is required");
        if (commissionPercent == null || commissionPercent.compareTo(BigDecimal.ZERO) <= 0 || commissionPercent.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Commission percent must be between 0.01 and 100.00");
        }
        FarmerCommissionConfig existing = dao.findByClientId(clientId);
        if (existing == null) {
            existing = new FarmerCommissionConfig();
            existing.setClientId(clientId);
            existing.setClientUsername(clientUsername);
            existing.setCommissionPercent(commissionPercent);
            existing.setDescription(description);
            existing.setCreatedBy(clientUsername);
            existing.setCreatedDate(LocalDateTime.now());
            existing.setUpdatedBy(clientUsername);
            existing.setUpdatedDate(LocalDateTime.now());
        } else {
            existing.setCommissionPercent(commissionPercent);
            existing.setDescription(description);
            existing.setUpdatedBy(clientUsername);
            existing.setUpdatedDate(LocalDateTime.now());
        }
        dao.saveOrUpdate(existing);
        return existing;
    }
}