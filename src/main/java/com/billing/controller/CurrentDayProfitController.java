package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.service.CurrentDayProfitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CurrentDayProfitController {

    private static final Logger logger = LoggerFactory.getLogger(CurrentDayProfitController.class);

    private final CurrentDayProfitService currentDayProfitService;

    public CurrentDayProfitController(CurrentDayProfitService currentDayProfitService) {
        this.currentDayProfitService = currentDayProfitService;
    }

    @GetMapping("/current-day-profit/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTodaySummary() {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            List<Map<String, Object>> report = currentDayProfitService.getTodaySummary(clientId);
            return ResponseEntity.ok(ApiResponse.success("OK", report));
        } catch (Exception e) {
            logger.error("getTodaySummary: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch total commission report"));
        }
    }

    @PostMapping("/current-day-profit/sales-details")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSalesDetails(@RequestBody Map<String, String> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            String farmerId = request.get("farmerId");
            List<Map<String, Object>> details = currentDayProfitService.getTodaySales(clientId, farmerId);
            return ResponseEntity.ok(ApiResponse.success("OK", details));
        } catch (Exception e) {
            logger.error("getSalesDetails: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch sales details"));
        }
    }
}
