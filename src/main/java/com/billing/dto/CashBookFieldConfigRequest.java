package com.billing.dto;

public class CashBookFieldConfigRequest {

    private Long fieldConfigId;
    private String label;
    private String labelTa;
    private String fieldType;

    public CashBookFieldConfigRequest() {
    }

    public CashBookFieldConfigRequest(Long fieldConfigId, String label, String labelTa, String fieldType) {
        this.fieldConfigId = fieldConfigId;
        this.label = label;
        this.labelTa = labelTa;
        this.fieldType = fieldType;
    }

    public Long getFieldConfigId() {
        return fieldConfigId;
    }

    public void setFieldConfigId(Long fieldConfigId) {
        this.fieldConfigId = fieldConfigId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabelTa() {
        return labelTa;
    }

    public void setLabelTa(String labelTa) {
        this.labelTa = labelTa;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }
}