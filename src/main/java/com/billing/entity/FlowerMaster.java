package com.billing.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "BLOOMBUDDY_FLOWER_MASTER")
public class FlowerMaster {

    @Id
    @Column(name = "FLOWER_ID", length = 60)
    private String flowerId;

    @Column(name = "CLIENT_ID", nullable = false)
    private Long clientId;

    @Column(name = "CLIENT_USERNAME", nullable = false, length = 50)
    private String clientUsername;

    @Column(name = "FLOWER_NAME", nullable = false, length = 100)
    private String flowerName;

    public FlowerMaster() {
    }

    public String getFlowerId() {
        return flowerId;
    }

    public void setFlowerId(String flowerId) {
        this.flowerId = flowerId;
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

    public String getFlowerName() {
        return flowerName;
    }

    public void setFlowerName(String flowerName) {
        this.flowerName = flowerName;
    }
}
