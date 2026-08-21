package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.DashboardData;
import com.billing.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard/data")
    @ResponseBody
    public ResponseEntity<?> getDashboardData() {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            DashboardData data = dashboardService.getDashboardData(clientId);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("getDashboardData: error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to fetch dashboard data"));
        }
    }
}
