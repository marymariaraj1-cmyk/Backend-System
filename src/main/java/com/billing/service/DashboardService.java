package com.billing.service;

import com.billing.dto.DashboardData;

public interface DashboardService {

    DashboardData getDashboardData(Long clientId);
}
