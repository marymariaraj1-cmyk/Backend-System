package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.service.FarmerSalesReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FarmerSalesByDateController {

    private static final Logger logger = LoggerFactory.getLogger(FarmerSalesByDateController.class);

    private final FarmerSalesReportService farmerSalesReportService;

    public FarmerSalesByDateController(FarmerSalesReportService farmerSalesReportService) {
        this.farmerSalesReportService = farmerSalesReportService;
    }

    @GetMapping("/farmer-sales-by-date/data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSalesByDate(@RequestParam String farmerId,
                                                                           @RequestParam String date,
                                                                           @RequestParam(required = false) String ledgerActive,
                                                                           @RequestParam(required = false) BigDecimal creditAmt) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            if (farmerId == null || farmerId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Farmer id is required"));
            }
            if (date == null || date.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Date is required"));
            }
            LocalDate salesDate = LocalDate.parse(date.trim());
            Map<String, Object> result = farmerSalesReportService.getFarmerSalesByDate(clientId, clientUsername, farmerId, salesDate, ledgerActive, creditAmt);
            return ResponseEntity.ok(ApiResponse.success("OK", result));
        } catch (Exception e) {
            logger.error("getSalesByDate: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch farmer sales for date"));
        }
    }
}
