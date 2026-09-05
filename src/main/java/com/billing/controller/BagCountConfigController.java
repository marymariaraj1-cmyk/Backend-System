package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.service.BagCountConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bag-count-config")
public class BagCountConfigController {

    private static final Logger logger = LoggerFactory.getLogger(BagCountConfigController.class);

    private final BagCountConfigService bagCountConfigService;

    public BagCountConfigController(BagCountConfigService bagCountConfigService) {
        this.bagCountConfigService = bagCountConfigService;
    }

    @GetMapping("/flowers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFlowers() {
        Long clientId = SessionConfig.getCurrentClientId();
        return ResponseEntity.ok(ApiResponse.success("OK", bagCountConfigService.getFlowers(clientId)));
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getConfigs() {
        Long clientId = SessionConfig.getCurrentClientId();
        return ResponseEntity.ok(ApiResponse.success("OK", bagCountConfigService.getConfigs(clientId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig(
            @RequestParam String flowerId, @RequestParam String salesDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        LocalDate date = parseDate(salesDate);
        return ResponseEntity.ok(ApiResponse.success("OK", bagCountConfigService.getConfig(clientId, flowerId, date)));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Void>> saveConfig(@RequestBody Map<String, String> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        String flowerId = request.get("flowerId");
        String flowerName = request.get("flowerName");
        if (flowerId == null || flowerId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Flower id is required"));
        }
        if (flowerName == null || flowerName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Flower name is required"));
        }
        LocalDate salesDate = parseDate(request.get("salesDate"));
        if (salesDate == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Sales date is required"));
        }
        Integer bagCount = parseInteger(request.get("bagCount"));
        String bagCheck = request.get("bagCheck");
        bagCountConfigService.saveConfig(clientId, clientUsername, flowerId.trim(), flowerName.trim(),
                salesDate, bagCount, bagCheck);
        return ResponseEntity.ok(ApiResponse.success("Bag count configuration saved successfully", null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteConfig(
            @RequestParam String flowerId, @RequestParam String salesDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        LocalDate date = parseDate(salesDate);
        if (date == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Sales date is required"));
        }
        bagCountConfigService.deleteConfig(clientId, flowerId, date);
        return ResponseEntity.ok(ApiResponse.success("Bag count configuration deleted successfully", null));
    }

    @GetMapping("/saved-total")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSavedTotal(
            @RequestParam String flowerId, @RequestParam String salesDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        LocalDate date = parseDate(salesDate);
        int total = bagCountConfigService.getSavedBagTotal(clientId, flowerId, date);
        return ResponseEntity.ok(ApiResponse.success("OK", java.util.Map.of("total", total)));
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

    private Integer parseInteger(String intStr) {
        if (intStr == null || intStr.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(intStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid bag count");
        }
    }
}
