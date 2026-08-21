package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dao.FarmerMasterDao;
import com.billing.dto.ApiResponse;
import com.billing.service.FarmerLedgerReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FarmerLedgerController {

    private static final Logger logger = LoggerFactory.getLogger(FarmerLedgerController.class);

    private final FarmerLedgerReportService farmerLedgerReportService;
    private final FarmerMasterDao farmerMasterDao;

    public FarmerLedgerController(FarmerLedgerReportService farmerLedgerReportService,
                                  FarmerMasterDao farmerMasterDao) {
        this.farmerLedgerReportService = farmerLedgerReportService;
        this.farmerMasterDao = farmerMasterDao;
    }

    @GetMapping("/farmer-ledger-list/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFarmerList() {
        Long clientId = SessionConfig.getCurrentClientId();
        List<Map<String, Object>> rows = farmerLedgerReportService.getFarmerList(clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", rows));
    }

    @GetMapping("/farmer-ledger-detail/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFarmerLedgerDetail(
            @RequestParam String farmerId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false, defaultValue = "false") boolean report) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            if (report) {
                if (fromDate == null || fromDate.isBlank() || toDate == null || toDate.isBlank()) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("From date and To date are required for report mode"));
                }
                LocalDate from = LocalDate.parse(fromDate);
                LocalDate to = LocalDate.parse(toDate);
                if (from.isAfter(to)) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("From date must not be after To date"));
                }
                List<Map<String, Object>> rows = farmerLedgerReportService.getFarmerLedgerReportDetail(
                        clientId, clientUsername, farmerId, from, to);
                return ResponseEntity.ok(ApiResponse.success("OK", rows));
            }
            if (fromDate == null || fromDate.isBlank() || toDate == null || toDate.isBlank()) {
                List<Map<String, Object>> allRows = farmerLedgerReportService.getFarmerLedgerDetailAll(
                        clientId, clientUsername, farmerId);
                return ResponseEntity.ok(ApiResponse.success("OK", allRows));
            }
            LocalDate from = LocalDate.parse(fromDate);
            LocalDate to = LocalDate.parse(toDate);
            if (from.isAfter(to)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("From date must not be after To date"));
            }
            List<Map<String, Object>> rows = farmerLedgerReportService.getFarmerLedgerDetail(
                    clientId, clientUsername, farmerId, from, to);
            return ResponseEntity.ok(ApiResponse.success("OK", rows));
        } catch (Exception e) {
            logger.error("getFarmerLedgerDetail: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch farmer ledger detail"));
        }
    }

    @GetMapping("/farmer-ledger-report/farmers")
    public ResponseEntity<ApiResponse<List<String>>> getFarmerNames() {
        Long clientId = SessionConfig.getCurrentClientId();
        List<String> names = farmerMasterDao.findNamesByClientId(clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", names));
    }

    @PostMapping("/farmer-ledger-report/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getReportData(@RequestBody Map<String, String> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            String farmerName = request.get("farmerName");
            String fromDateStr = request.get("fromDate");
            String toDateStr = request.get("toDate");

            if (farmerName == null || farmerName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Farmer name is required"));
            }
            if (fromDateStr == null || fromDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("From date is required"));
            }
            if (toDateStr == null || toDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("To date is required"));
            }

            String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, farmerName.trim());
            if (farmerId == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Farmer not found"));
            }

            LocalDate fromDate = LocalDate.parse(fromDateStr.trim());
            LocalDate toDate = LocalDate.parse(toDateStr.trim());
            if (fromDate.isAfter(toDate)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("From date must not be after To date"));
            }

            List<Map<String, Object>> report = farmerLedgerReportService.getFarmerLedgerReport(
                    clientId, clientUsername, farmerId, fromDate, toDate);
            return ResponseEntity.ok(ApiResponse.success("OK", report));
        } catch (Exception e) {
            logger.error("getReportData: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch report"));
        }
    }
}
