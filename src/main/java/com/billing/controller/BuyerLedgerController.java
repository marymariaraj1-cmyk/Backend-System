package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dao.BuyerMasterDao;
import com.billing.dto.ApiResponse;
import com.billing.service.BuyerLedgerReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class BuyerLedgerController {

    private static final Logger logger = LoggerFactory.getLogger(BuyerLedgerController.class);

    private static final Set<String> EXCLUDED_BUYER_NAMES = Set.of(
            "cash", "upi", "google pay", "gpay", "phonepe", "paytm", "online", "card", "neft", "rtgs", "imps"
    );

    private final BuyerLedgerReportService buyerLedgerReportService;
    private final BuyerMasterDao buyerMasterDao;

    public BuyerLedgerController(BuyerLedgerReportService buyerLedgerReportService,
                                 BuyerMasterDao buyerMasterDao) {
        this.buyerLedgerReportService = buyerLedgerReportService;
        this.buyerMasterDao = buyerMasterDao;
    }

    @GetMapping("/buyer-ledger-list/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuyerList() {
        Long clientId = SessionConfig.getCurrentClientId();
        List<Map<String, Object>> all = buyerLedgerReportService.getBuyerList(clientId);
        List<Map<String, Object>> filtered = all.stream()
                .filter(row -> {
                    Object nameObj = row.get("buyerName");
                    if (nameObj == null) return false;
                    String name = String.valueOf(nameObj).trim().toLowerCase();
                    return EXCLUDED_BUYER_NAMES.stream().noneMatch(ex -> name.contains(ex));
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("OK", filtered));
    }

    @GetMapping("/buyer-ledger-detail/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuyerLedgerDetail(
            @RequestParam String buyerId,
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
                List<Map<String, Object>> rows = buyerLedgerReportService.getBuyerLedgerReportDetail(
                        clientId, clientUsername, buyerId, from, to);
                return ResponseEntity.ok(ApiResponse.success("OK", rows));
            }
            if (fromDate == null || fromDate.isBlank() || toDate == null || toDate.isBlank()) {
                List<Map<String, Object>> allRows = buyerLedgerReportService.getBuyerLedgerDetailAll(
                        clientId, clientUsername, buyerId);
                return ResponseEntity.ok(ApiResponse.success("OK", allRows));
            }
            LocalDate from = LocalDate.parse(fromDate);
            LocalDate to = LocalDate.parse(toDate);
            if (from.isAfter(to)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("From date must not be after To date"));
            }
            List<Map<String, Object>> rows = buyerLedgerReportService.getBuyerLedgerDetail(
                    clientId, clientUsername, buyerId, from, to);
            return ResponseEntity.ok(ApiResponse.success("OK", rows));
        } catch (Exception e) {
            logger.error("getBuyerLedgerDetail: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch buyer ledger detail"));
        }
    }

    @GetMapping("/buyer-ledger-report/buyers")
    public ResponseEntity<ApiResponse<List<String>>> getBuyerNames() {
        Long clientId = SessionConfig.getCurrentClientId();
        List<String> allNames = buyerMasterDao.findNamesByClientId(clientId);
        List<String> filtered = allNames.stream()
                .filter(name -> EXCLUDED_BUYER_NAMES.stream()
                        .noneMatch(ex -> name.trim().toLowerCase().contains(ex)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("OK", filtered));
    }

    @PostMapping("/buyer-ledger-report/data")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getReportData(@RequestBody Map<String, String> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            String buyerName = request.get("buyerName");
            String fromDateStr = request.get("fromDate");
            String toDateStr = request.get("toDate");

            if (buyerName == null || buyerName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Buyer name is required"));
            }
            if (fromDateStr == null || fromDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("From date is required"));
            }
            if (toDateStr == null || toDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("To date is required"));
            }

            String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, buyerName.trim());
            if (buyerId == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Buyer not found"));
            }

            LocalDate fromDate = LocalDate.parse(fromDateStr.trim());
            LocalDate toDate = LocalDate.parse(toDateStr.trim());
            if (fromDate.isAfter(toDate)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("From date must not be after To date"));
            }

            List<Map<String, Object>> report = buyerLedgerReportService.getBuyerLedgerReport(
                    clientId, clientUsername, buyerId, fromDate, toDate);
            return ResponseEntity.ok(ApiResponse.success("OK", report));
        } catch (Exception e) {
            logger.error("getReportData: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch report"));
        }
    }
}
