package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.service.FlowerPriceConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flower-price-config")
public class FlowerPriceConfigController {

    private static final Logger logger = LoggerFactory.getLogger(FlowerPriceConfigController.class);

    private final FlowerPriceConfigService flowerPriceConfigService;

    public FlowerPriceConfigController(FlowerPriceConfigService flowerPriceConfigService) {
        this.flowerPriceConfigService = flowerPriceConfigService;
    }

    @GetMapping("/flowers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFlowers() {
        Long clientId = SessionConfig.getCurrentClientId();
        return ResponseEntity.ok(ApiResponse.success("OK", flowerPriceConfigService.getFlowers(clientId)));
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPrices(
            @RequestParam String priceDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        LocalDate date = parseDate(priceDate);
        if (date == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Sales date is required"));
        }
        return ResponseEntity.ok(ApiResponse.success("OK", flowerPriceConfigService.getPrices(clientId, date)));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Map<String, Object>>> savePrices(@RequestBody Map<String, Object> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            LocalDate priceDate = parseDate(String.valueOf(request.get("priceDate")));
            Object itemsObj = request.get("items");
            List<Map<String, Object>> items =
                    itemsObj instanceof List ? (List<Map<String, Object>>) itemsObj : List.of();
            Map<String, Object> result = flowerPriceConfigService.savePrices(clientId, clientUsername, priceDate, items);
            int added = (int) result.get("added");
            int updated = (int) result.get("updated");
            return ResponseEntity.ok(ApiResponse.success(
                    "Flower market prices saved successfully (" + added + " added, " + updated + " updated)", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse<Void>> updatePrice(@RequestBody Map<String, Object> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            Long priceConfigId = request.get("priceConfigId") == null
                    ? null : Long.valueOf(String.valueOf(request.get("priceConfigId")));
            java.math.BigDecimal price = new java.math.BigDecimal(String.valueOf(request.get("price")));
            flowerPriceConfigService.updatePrice(clientId, clientUsername, priceConfigId, price);
            return ResponseEntity.ok(ApiResponse.success("Flower price updated successfully", null));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Enter a valid positive price"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deletePrice(@RequestParam Long priceConfigId) {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            flowerPriceConfigService.deletePrice(clientId, priceConfigId);
            return ResponseEntity.ok(ApiResponse.success("Flower price deleted successfully", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/ticker")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTicker() {
        Long clientId = SessionConfig.getCurrentClientId();
        return ResponseEntity.ok(ApiResponse.success("OK", flowerPriceConfigService.getTickerData(clientId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHistory(
            @RequestParam String flowerId,
            @RequestParam String fromDate,
            @RequestParam String toDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            LocalDate from = parseDate(fromDate);
            LocalDate to = parseDate(toDate);
            if (from == null || to == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("From and to dates are required"));
            }
            return ResponseEntity.ok(ApiResponse.success("OK",
                    flowerPriceConfigService.getPriceHistory(clientId, flowerId, from, to)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "null".equalsIgnoreCase(dateStr)) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter a valid date");
        }
    }
}