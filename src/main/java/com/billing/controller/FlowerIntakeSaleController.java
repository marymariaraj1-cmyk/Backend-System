package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.SalesRequestDto;
import com.billing.entity.Sales;
import com.billing.service.FlowerIntakeSaleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/flower-intake-sale")
public class FlowerIntakeSaleController {

    private static final Logger logger = LoggerFactory.getLogger(FlowerIntakeSaleController.class);

    private final FlowerIntakeSaleService flowerIntakeSaleService;

    public FlowerIntakeSaleController(FlowerIntakeSaleService flowerIntakeSaleService) {
        this.flowerIntakeSaleService = flowerIntakeSaleService;
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<List<Sales>>> save(@RequestBody SalesRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: flower intake sale for farmer={}, rows={}, clientId={}",
                request == null ? null : request.getFarmerName(),
                request == null || request.getRows() == null ? 0 : request.getRows().size(), clientId);
        List<Sales> saved = flowerIntakeSaleService.saveFlowerIntakeSale(request, clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Flower intake sale saved successfully", saved));
    }
}
