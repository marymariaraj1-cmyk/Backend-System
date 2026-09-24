package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.FlowerLootSaleEditDeleteRequestDto;
import com.billing.dto.FlowerLootSaleEditRequestDto;
import com.billing.service.FlowerLootSaleEditService;
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
@RequestMapping("/api/flower-loot-sale-edit")
public class FlowerLootSaleEditController {

    private static final Logger logger = LoggerFactory.getLogger(FlowerLootSaleEditController.class);

    private final FlowerLootSaleEditService flowerLootSaleEditService;

    public FlowerLootSaleEditController(FlowerLootSaleEditService flowerLootSaleEditService) {
        this.flowerLootSaleEditService = flowerLootSaleEditService;
    }

    @GetMapping("/master-data")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getMasterData() {
        Long clientId = SessionConfig.getCurrentClientId();
        Map<String, List<String>> data = flowerLootSaleEditService.getMasterNames(clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @GetMapping("/fetch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fetch(@RequestParam String flowerName,
                                                                  @RequestParam String salesDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("fetch: flower={}, date={}, clientId={}", flowerName, salesDate, clientId);
        Map<String, Object> data = flowerLootSaleEditService.fetchFlowerLootSaleData(flowerName, salesDate, clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(@RequestBody FlowerLootSaleEditRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: flower={}, date={}, buyerRows={}, farmerRows={}, clientId={}",
                request == null ? null : request.getFlowerName(),
                request == null ? null : request.getSalesDate(),
                request == null || request.getBuyerRows() == null ? 0 : request.getBuyerRows().size(),
                request == null || request.getFarmerRows() == null ? 0 : request.getFarmerRows().size(),
                clientId);
        Map<String, Object> result = flowerLootSaleEditService.saveEdits(request, clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Flower Loot Sale details updated successfully", result));
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(
            @RequestBody FlowerLootSaleEditDeleteRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("delete: salesId={}, clientId={}",
                request == null ? null : request.getSalesId(), clientId);
        Map<String, Object> result = flowerLootSaleEditService.deleteSalesEntry(
                request == null ? null : request.getSalesId(), clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Flower Loot Sale entry deleted successfully", result));
    }
}