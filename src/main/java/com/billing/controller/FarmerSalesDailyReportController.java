package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.service.FarmerSalesReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FarmerSalesDailyReportController {

    private static final Logger logger = LoggerFactory.getLogger(FarmerSalesDailyReportController.class);

    private final FarmerSalesReportService farmerSalesReportService;

    public FarmerSalesDailyReportController(FarmerSalesReportService farmerSalesReportService) {
        this.farmerSalesReportService = farmerSalesReportService;
    }

    @GetMapping("/farmer-sales-daily-report/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getReportData() {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            List<Map<String, Object>> report = farmerSalesReportService.getTodayFarmerSales(clientId, clientUsername);
            return ResponseEntity.ok(ApiResponse.success("OK", report));
        } catch (Exception e) {
            logger.error("getReportData: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch report"));
        }
    }
}
