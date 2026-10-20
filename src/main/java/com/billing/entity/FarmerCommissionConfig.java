package com.billing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "BLOOMBUDDY_FARMER_COMMISSION_PERCENTAGE")
public class FarmerCommissionConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMMISSION_CONFIG_ID", nullable = false, updatable = false)
    private Long commissionConfigId;

    @Column(name = "CLIENT_ID", nullable = false)
    private Long clientId;

    @Column(name = "CLIENT_USERNAME", nullable = false, length = 100)
    private String clientUsername;

    @Column(name = "COMMISSION_PERCENT", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Column(name = "DESCRIPTION", length = 255)
    private String description;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Column(name = "UPDATED_BY", length = 100)
    private String updatedBy;

    @Column(name = "UPDATED_DATE")
    private LocalDateTime updatedDate;

    public Long getCommissionConfigId() { return commissionConfigId; }
    public void setCommissionConfigId(Long v) { this.commissionConfigId = v; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long v) { this.clientId = v; }
    public String getClientUsername() { return clientUsername; }
    public void setClientUsername(String v) { this.clientUsername = v; }
    public BigDecimal getCommissionPercent() { return commissionPercent; }
    public void setCommissionPercent(BigDecimal v) { this.commissionPercent = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime v) { this.createdDate = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public LocalDateTime getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(LocalDateTime v) { this.updatedDate = v; }
}