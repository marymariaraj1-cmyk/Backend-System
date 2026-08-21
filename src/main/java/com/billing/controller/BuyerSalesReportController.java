package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.service.BuyerSalesReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BuyerSalesReportController {

    private static final Logger logger = LoggerFactory.getLogger(BuyerSalesReportController.class);

    private final BuyerSalesReportService buyerSalesReportService;

    public BuyerSalesReportController(BuyerSalesReportService buyerSalesReportService) {
        this.buyerSalesReportService = buyerSalesReportService;
    }

    @PostMapping("/buyer-sales-report/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getReportData(@RequestBody Map<String, String> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            String fromDateStr = request.get("fromDate");
            String toDateStr = request.get("toDate");

            if (fromDateStr == null || fromDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("From date is required"));
            }
            if (toDateStr == null || toDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("To date is required"));
            }

            LocalDate fromDate = LocalDate.parse(fromDateStr.trim());
            LocalDate toDate = LocalDate.parse(toDateStr.trim());
            if (fromDate.isAfter(toDate)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("From date must not be after To date"));
            }

            List<Map<String, Object>> report = buyerSalesReportService.getBuyerSalesReport(
                    clientId, clientUsername, fromDate, toDate);
            return ResponseEntity.ok(ApiResponse.success("OK", report));
        } catch (Exception e) {
            logger.error("getReportData: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch report"));
        }
    }
}
