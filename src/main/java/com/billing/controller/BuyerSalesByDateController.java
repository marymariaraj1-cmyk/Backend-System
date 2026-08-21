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
public class BuyerSalesByDateController {

    private static final Logger logger = LoggerFactory.getLogger(BuyerSalesByDateController.class);

    private final BuyerSalesReportService buyerSalesReportService;

    public BuyerSalesByDateController(BuyerSalesReportService buyerSalesReportService) {
        this.buyerSalesReportService = buyerSalesReportService;
    }

    @GetMapping("/buyer-sales-by-date/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSalesByDate(@RequestParam String buyerId,
                                                                                 @RequestParam String date,
                                                                                 @RequestParam(required = false) String ledgerActive) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            if (buyerId == null || buyerId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Buyer id is required"));
            }
            if (date == null || date.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Date is required"));
            }
            LocalDate salesDate = LocalDate.parse(date.trim());
            List<Map<String, Object>> rows = buyerSalesReportService.getBuyerSalesByDate(clientId, clientUsername, buyerId, salesDate, ledgerActive);
            return ResponseEntity.ok(ApiResponse.success("OK", rows));
        } catch (Exception e) {
            logger.error("getSalesByDate: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch buyer sales for date"));
        }
    }
}
