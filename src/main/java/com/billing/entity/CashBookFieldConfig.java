package com.billing.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "BLOOMBUDDY_DAILY_CASH_BOOK_FIELD_CONFIG")
public class CashBookFieldConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FIELD_CONFIG_ID", nullable = false, updatable = false)
    private Long fieldConfigId;

    @Column(name = "CLIENT_ID", nullable = false)
    private Long clientId;

    @Column(name = "CLIENT_USERNAME", nullable = false, length = 50)
    private String clientUsername;

    @Column(name = "FIELD_LABEL", nullable = false, length = 50)
    private String fieldLabel;

    @Column(name = "FIELD_LABEL_TA", length = 100)
    private String fieldLabelTa;

    @Column(name = "FIELD_TYPE", nullable = false, length = 10)
    private String fieldType;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Column(name = "UPDATED_DATE")
    private LocalDateTime updatedDate;

    public CashBookFieldConfig() {
    }

    public Long getFieldConfigId() {
        return fieldConfigId;
    }

    public void setFieldConfigId(Long fieldConfigId) {
        this.fieldConfigId = fieldConfigId;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getClientUsername() {
        return clientUsername;
    }

    public void setClientUsername(String clientUsername) {
        this.clientUsername = clientUsername;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    public String getFieldLabelTa() {
        return fieldLabelTa;
    }

    public void setFieldLabelTa(String fieldLabelTa) {
        this.fieldLabelTa = fieldLabelTa;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }
}