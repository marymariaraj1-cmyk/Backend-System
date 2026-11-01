package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.SalesEditDeleteRequestDto;
import com.billing.dto.SalesEditRequestDto;
import com.billing.service.FlowerIntakeSaleEditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/flower-intake-sale-edit")
public class FlowerIntakeSaleEditController {

    private static final Logger logger = LoggerFactory.getLogger(FlowerIntakeSaleEditController.class);

    private final FlowerIntakeSaleEditService flowerIntakeSaleEditService;

    public FlowerIntakeSaleEditController(FlowerIntakeSaleEditService flowerIntakeSaleEditService) {
        this.flowerIntakeSaleEditService = flowerIntakeSaleEditService;
    }

    @GetMapping("/fetch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fetch(@RequestParam String farmerName,
                                                                  @RequestParam String salesDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("fetch: farmer={}, date={}, clientId={}", farmerName, salesDate, clientId);
        Map<String, Object> data = flowerIntakeSaleEditService.fetch(farmerName, salesDate, clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(@RequestBody SalesEditRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: farmer={}, rows={}, clientId={}",
                request == null ? null : request.getFarmerName(),
                request == null || request.getRows() == null ? 0 : request.getRows().size(), clientId);
        Map<String, Object> result = flowerIntakeSaleEditService.saveEdits(request, clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Flower intake sale details updated successfully", result));
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@RequestBody SalesEditDeleteRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("delete: salesId={}, clientId={}", request == null ? null : request.getSalesId(), clientId);
        Map<String, Object> result = flowerIntakeSaleEditService.delete(
                request == null ? null : request.getSalesId(), clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Flower intake sale entry deleted successfully", result));
    }
}
