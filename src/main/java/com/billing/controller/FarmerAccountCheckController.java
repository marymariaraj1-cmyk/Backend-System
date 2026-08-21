package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.service.FarmerAccountCheckService;
import com.billing.service.FarmerLedgerReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/farmer-account-check")
public class FarmerAccountCheckController {

    private static final Logger logger = LoggerFactory.getLogger(FarmerAccountCheckController.class);

    private final FarmerAccountCheckService farmerAccountCheckService;
    private final FarmerLedgerReportService farmerLedgerReportService;

    public FarmerAccountCheckController(FarmerAccountCheckService farmerAccountCheckService,
                                         FarmerLedgerReportService farmerLedgerReportService) {
        this.farmerAccountCheckService = farmerAccountCheckService;
        this.farmerLedgerReportService = farmerLedgerReportService;
    }

    @GetMapping("/active-ledger")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getActiveLedgerRows(
            @RequestParam String farmerId) {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            List<Map<String, Object>> rows = farmerAccountCheckService.getActiveLedgerRows(clientId, farmerId);
            return ResponseEntity.ok(ApiResponse.success("OK", rows));
        } catch (Exception e) {
            logger.error("getActiveLedgerRows: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch active ledger rows"));
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> preview(@RequestBody Map<String, Object> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            String farmerId = (String) request.get("farmerId");
            String farmerName = (String) request.get("farmerName");
            BigDecimal finalAmount = new BigDecimal(request.get("finalAmount").toString());

            if (farmerId == null || farmerId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Farmer ID is required"));
            }
            if (finalAmount == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Final amount is required"));
            }

            boolean willClose = farmerAccountCheckService.previewWillCauseZeroClose(
                    clientId, clientUsername, farmerId, farmerName, finalAmount);
            return ResponseEntity.ok(ApiResponse.success("OK", Map.of("willCauseZeroClose", willClose)));
        } catch (Exception e) {
            logger.error("preview: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to preview ledger update"));
        }
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<String>> commit(@RequestBody Map<String, Object> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            String farmerId = (String) request.get("farmerId");
            String farmerName = (String) request.get("farmerName");
            BigDecimal finalAmount = new BigDecimal(request.get("finalAmount").toString());

            if (farmerId == null || farmerId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Farmer ID is required"));
            }
            if (finalAmount == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Final amount is required"));
            }

            farmerAccountCheckService.commitDebitWrite(clientId, clientUsername, farmerId, farmerName, finalAmount);
            return ResponseEntity.ok(ApiResponse.success("Ledger updated successfully", "OK"));
        } catch (Exception e) {
            logger.error("commit: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to commit ledger update"));
        }
    }
}
