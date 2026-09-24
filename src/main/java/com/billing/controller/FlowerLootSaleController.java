package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.FlowerLootSaleDto;
import com.billing.entity.Sales;
import com.billing.service.FlowerLootSaleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/flower-loot-sale")
public class FlowerLootSaleController {

    private static final Logger logger = LoggerFactory.getLogger(FlowerLootSaleController.class);

    private final FlowerLootSaleService flowerLootSaleService;

    public FlowerLootSaleController(FlowerLootSaleService flowerLootSaleService) {
        this.flowerLootSaleService = flowerLootSaleService;
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<List<Sales>>> save(@RequestBody FlowerLootSaleDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: received flower loot sale, buyerRows={}, farmerRows={}, clientId={}",
                request.getBuyerRows() == null ? 0 : request.getBuyerRows().size(),
                request.getFarmerRows() == null ? 0 : request.getFarmerRows().size(), clientId);
        List<Sales> saved = flowerLootSaleService.saveFlowerLootSale(request, clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Flower Loot Sale saved successfully", saved));
    }
}