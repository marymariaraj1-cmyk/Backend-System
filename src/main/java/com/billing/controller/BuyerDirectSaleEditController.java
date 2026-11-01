package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.BuyerDirectSaleDto;
import com.billing.dto.SalesEditDeleteRequestDto;
import com.billing.service.BuyerDirectSaleEditService;
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
@RequestMapping("/api/buyer-direct-sale-edit")
public class BuyerDirectSaleEditController {

    private static final Logger logger = LoggerFactory.getLogger(BuyerDirectSaleEditController.class);

    private final BuyerDirectSaleEditService buyerDirectSaleEditService;

    public BuyerDirectSaleEditController(BuyerDirectSaleEditService buyerDirectSaleEditService) {
        this.buyerDirectSaleEditService = buyerDirectSaleEditService;
    }

    @GetMapping("/fetch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fetch(@RequestParam String buyerName,
                                                                  @RequestParam String salesDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("fetch: buyer={}, date={}, clientId={}", buyerName, salesDate, clientId);
        Map<String, Object> data = buyerDirectSaleEditService.fetch(buyerName, salesDate, clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(@RequestBody BuyerDirectSaleDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: buyer={}, rows={}, clientId={}",
                request == null ? null : request.getBuyerName(),
                request == null || request.getRows() == null ? 0 : request.getRows().size(), clientId);
        Map<String, Object> result = buyerDirectSaleEditService.saveEdits(request, clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Buyer direct sale details updated successfully", result));
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@RequestBody SalesEditDeleteRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("delete: salesId={}, clientId={}", request == null ? null : request.getSalesId(), clientId);
        Map<String, Object> result = buyerDirectSaleEditService.delete(
                request == null ? null : request.getSalesId(), clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Buyer direct sale entry deleted successfully", result));
    }
}
