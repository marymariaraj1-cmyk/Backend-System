package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.VayalAgroPriceData;
import com.billing.service.VayalAgroPriceService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flower-price")
public class FlowerPriceReportController {

    private static final Logger logger = LoggerFactory.getLogger(FlowerPriceReportController.class);

    private final VayalAgroPriceService vayalAgroPriceService;

    public FlowerPriceReportController(VayalAgroPriceService vayalAgroPriceService) {
        this.vayalAgroPriceService = vayalAgroPriceService;
    }

    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDistricts() {
        try {
            List<Map<String, Object>> districts = vayalAgroPriceService.getDistricts();
            return ResponseEntity.ok(ApiResponse.success("OK", districts));
        } catch (Exception e) {
            logger.error("getDistricts: failed to fetch districts", e);
            return ResponseEntity.status(502).body(ApiResponse.error("Failed to load districts"));
        }
    }

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCities(
            @RequestParam(required = false) String marketId) {
        try {
            if (marketId == null || marketId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("District is required"));
            }
            List<Map<String, Object>> cities = vayalAgroPriceService.getCities(marketId.trim());
            return ResponseEntity.ok(ApiResponse.success("OK", cities));
        } catch (Exception e) {
            logger.error("getCities: failed to fetch cities", e);
            return ResponseEntity.status(502).body(ApiResponse.error("Failed to load cities"));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPrices(
            @RequestParam(required = false) String marketId,
            @RequestParam(required = false) String marketPlaceId,
            @RequestParam(required = false) String date) {
        try {
            if (marketId == null || marketId.trim().isEmpty()
                    || marketPlaceId == null || marketPlaceId.trim().isEmpty()
                    || date == null || date.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("District, city and date are required"));
            }
            VayalAgroPriceData result =
                    vayalAgroPriceService.getPrices(marketId.trim(), marketPlaceId.trim(), date.trim());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rows", result.getRows());
            data.put("fetchedDate", result.getDate());
            return ResponseEntity.ok(ApiResponse.success("OK", data));
        } catch (Exception e) {
            logger.error("getPrices: failed to fetch flower prices", e);
            return ResponseEntity.status(502).body(ApiResponse.error("Failed to fetch flower prices"));
        }
    }

    @PostMapping("/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHistory(
            @RequestBody(required = false) Map<String, String> request) {
        try {
            String flowerName = request != null ? request.get("flowerName") : null;
            String marketPlaceId = request != null ? request.get("marketPlaceId") : null;
            if (flowerName == null || flowerName.trim().isEmpty()
                    || marketPlaceId == null || marketPlaceId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Flower name and city are required"));
            }
            Map<String, Object> history =
                    vayalAgroPriceService.getPriceHistory(flowerName.trim(), marketPlaceId.trim());
            return ResponseEntity.ok(ApiResponse.success("OK", history));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("getHistory: failed to fetch price history", e);
            return ResponseEntity.status(502).body(ApiResponse.error("Failed to fetch price history"));
        }
    }
}
