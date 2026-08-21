package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.service.OpeningBalanceConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/opening-balance")
public class OpeningBalanceConfigController {

    private static final Logger logger = LoggerFactory.getLogger(OpeningBalanceConfigController.class);

    private static final Set<String> EXCLUDED_BUYER_NAMES = Set.of(
            "cash", "upi", "google pay", "gpay", "phonepe", "paytm", "online", "card", "neft", "rtgs", "imps");

    private final OpeningBalanceConfigService openingBalanceConfigService;

    public OpeningBalanceConfigController(OpeningBalanceConfigService openingBalanceConfigService) {
        this.openingBalanceConfigService = openingBalanceConfigService;
    }

    @GetMapping("/farmers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFarmers() {
        Long clientId = SessionConfig.getCurrentClientId();
        List<Map<String, Object>> farmers = openingBalanceConfigService.getFarmersWithOpeningBalance(clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", farmers));
    }

    @GetMapping("/buyers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuyers() {
        Long clientId = SessionConfig.getCurrentClientId();
        List<Map<String, Object>> buyers = openingBalanceConfigService.getBuyersWithOpeningBalance(clientId);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> buyer : buyers) {
            String buyerName = buyer.get("buyerName") == null ? "" : buyer.get("buyerName").toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED_BUYER_NAMES.stream().anyMatch(buyerName::contains)) {
                continue;
            }
            filtered.add(buyer);
        }
        return ResponseEntity.ok(ApiResponse.success("OK", filtered));
    }

    @PostMapping("/farmer/save")
    public ResponseEntity<ApiResponse<Void>> saveFarmerOpeningBalance(@RequestBody Map<String, String> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        String farmerId = request.get("farmerId");
        if (farmerId == null || farmerId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Farmer id is required"));
        }
        try {
            BigDecimal openingBalance = parseAmount(request.get("openingBalance"));
            LocalDate openingBalanceDate = parseDate(request.get("openingBalanceDate"));
            openingBalanceConfigService.saveFarmerOpeningBalance(clientId, clientUsername, farmerId.trim(), openingBalance, openingBalanceDate);
            return ResponseEntity.ok(ApiResponse.success("Opening balance saved successfully", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/buyer/save")
    public ResponseEntity<ApiResponse<Void>> saveBuyerOpeningBalance(@RequestBody Map<String, String> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        String buyerId = request.get("buyerId");
        if (buyerId == null || buyerId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Buyer id is required"));
        }
        try {
            BigDecimal openingBalance = parseAmount(request.get("openingBalance"));
            LocalDate openingBalanceDate = parseDate(request.get("openingBalanceDate"));
            openingBalanceConfigService.saveBuyerOpeningBalance(clientId, clientUsername, buyerId.trim(), openingBalance, openingBalanceDate);
            return ResponseEntity.ok(ApiResponse.success("Opening balance saved successfully", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(amountStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid amount");
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter a valid date");
        }
    }
}
