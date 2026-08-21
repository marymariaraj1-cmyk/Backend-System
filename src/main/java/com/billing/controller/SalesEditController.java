package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.SalesEditRequestDto;
import com.billing.service.SalesEditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-edit")
public class SalesEditController {

    private static final Logger logger = LoggerFactory.getLogger(SalesEditController.class);

    private final SalesEditService salesEditService;

    public SalesEditController(SalesEditService salesEditService) {
        this.salesEditService = salesEditService;
    }

    @GetMapping("/master-data")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getMasterData() {
        Long clientId = SessionConfig.getCurrentClientId();
        Map<String, List<String>> data = salesEditService.getMasterNames(clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @GetMapping("/fetch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fetch(@RequestParam String farmerName,
                                                                  @RequestParam String salesDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("fetch: farmer={}, date={}, clientId={}", farmerName, salesDate, clientId);
        Map<String, Object> data = salesEditService.fetchSalesData(farmerName, salesDate, clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(@RequestBody SalesEditRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: farmer={}, rows={}, clientId={}",
                request == null ? null : request.getFarmerName(),
                request == null || request.getRows() == null ? 0 : request.getRows().size(),
                clientId);
        Map<String, Object> result = salesEditService.saveEdits(request, clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Sales details updated successfully", result));
    }
}
