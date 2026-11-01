package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.CashBookFieldConfigRequest;
import com.billing.exception.FieldInUseException;
import com.billing.service.CashBookFieldConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cash-book-field-config")
public class CashBookFieldConfigController {

    private static final Logger logger = LoggerFactory.getLogger(CashBookFieldConfigController.class);

    private final CashBookFieldConfigService fieldConfigService;

    public CashBookFieldConfigController(CashBookFieldConfigService fieldConfigService) {
        this.fieldConfigService = fieldConfigService;
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            return ResponseEntity.ok(ApiResponse.success("OK", fieldConfigService.listFields(clientId)));
        } catch (Exception e) {
            logger.error("cash-book-field-config list: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to load field configuration"));
        }
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody CashBookFieldConfigRequest request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            Map<String, Object> created = fieldConfigService.create(clientId, clientUsername, request);
            return ResponseEntity.ok(ApiResponse.success("Field added successfully", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("cash-book-field-config create: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to save field configuration"));
        }
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@RequestBody CashBookFieldConfigRequest request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            Map<String, Object> updated = fieldConfigService.update(clientId, clientUsername, request);
            return ResponseEntity.ok(ApiResponse.success("Field updated successfully", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("cash-book-field-config update: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to update field configuration"));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<Void>> delete(@RequestParam Long fieldConfigId) {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            fieldConfigService.delete(clientId, fieldConfigId);
            return ResponseEntity.ok(ApiResponse.success("Field deleted successfully", null));
        } catch (FieldInUseException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("cash-book-field-config delete: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to delete field configuration"));
        }
    }

    @PutMapping("/reorder")
    public ResponseEntity<ApiResponse<Void>> reorder(@RequestBody Map<String, Object> request) {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            String fieldType = request.get("fieldType") != null ? String.valueOf(request.get("fieldType")) : null;
            @SuppressWarnings("unchecked")
            List<Number> rawIds = (List<Number>) request.get("ids");
            List<Long> ids = rawIds != null
                    ? rawIds.stream().map(Number::longValue).toList()
                    : List.of();
            fieldConfigService.reorder(clientId, fieldType, ids);
            return ResponseEntity.ok(ApiResponse.success("Field order updated successfully", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("cash-book-field-config reorder: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to reorder fields"));
        }
    }
}