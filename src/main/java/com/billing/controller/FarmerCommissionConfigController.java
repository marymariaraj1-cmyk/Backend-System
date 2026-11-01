package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.entity.FarmerCommissionConfig;
import com.billing.service.FarmerCommissionConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/commission-config")
public class FarmerCommissionConfigController {
    private static final Logger logger = LoggerFactory.getLogger(FarmerCommissionConfigController.class);
    private final FarmerCommissionConfigService service;

    @Autowired
    public FarmerCommissionConfigController(FarmerCommissionConfigService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<FarmerCommissionConfig>>> list() {
        try {
            Long clientId = SessionConfig.getCurrentClientId();
            List<FarmerCommissionConfig> list = service.getAllConfigs(clientId);
            return ResponseEntity.ok(ApiResponse.success("OK", list));
        } catch (Exception e) {
            logger.error("Failed to list commission configs", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to load commission config"));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> current() {
        try {
            Long clientId = SessionConfig.getCurrentClientId();
            BigDecimal percent = service.getCommissionPercent(clientId);
            Map<String, Object> m = new HashMap<>();
            m.put("commissionPercent", percent);
            return ResponseEntity.ok(ApiResponse.success("OK", m));
        } catch (Exception e) {
            logger.error("Failed to get current commission config", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to load commission config"));
        }
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<FarmerCommissionConfig>> save(@RequestBody Map<String, Object> body) {
        try {
            Long clientId = SessionConfig.getCurrentClientId();
            String clientUsername = SessionConfig.getCurrentClientUsername();
            BigDecimal percent = body.get("commissionPercent") != null
                    ? new BigDecimal(String.valueOf(body.get("commissionPercent"))) : null;
            String desc = body.get("description") != null ? String.valueOf(body.get("description")) : null;
            FarmerCommissionConfig saved = service.saveOrUpdate(clientId, clientUsername, percent, desc);
            return ResponseEntity.ok(ApiResponse.success("Saved successfully", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to save commission config", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to save commission config"));
        }
    }
}
