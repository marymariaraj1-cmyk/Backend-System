package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.service.InactiveListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inactive")
public class InactiveListController {

    private static final Logger logger = LoggerFactory.getLogger(InactiveListController.class);

    private final InactiveListService inactiveListService;

    public InactiveListController(InactiveListService inactiveListService) {
        this.inactiveListService = inactiveListService;
    }

    @GetMapping("/farmers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getInactiveFarmers() {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            List<Map<String, Object>> rows = inactiveListService.getInactiveFarmers(clientId);
            return ResponseEntity.ok(ApiResponse.success("OK", rows));
        } catch (Exception e) {
            logger.error("getInactiveFarmers: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch inactive farmer list"));
        }
    }

    @GetMapping("/buyers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getInactiveBuyers() {
        Long clientId = SessionConfig.getCurrentClientId();
        try {
            List<Map<String, Object>> rows = inactiveListService.getInactiveBuyers(clientId);
            return ResponseEntity.ok(ApiResponse.success("OK", rows));
        } catch (Exception e) {
            logger.error("getInactiveBuyers: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to fetch inactive buyer list"));
        }
    }
}