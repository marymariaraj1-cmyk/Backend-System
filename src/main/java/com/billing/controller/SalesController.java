package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.SalesLineDto;
import com.billing.dto.SalesRequestDto;
import com.billing.entity.Sales;
import com.billing.service.SalesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private static final Logger logger = LoggerFactory.getLogger(SalesController.class);

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @GetMapping("/master-data")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getMasterData() {
        Long clientId = SessionConfig.getCurrentClientId();
        Map<String, List<String>> data = salesService.getMasterNames(clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<List<String>>> autocomplete(@RequestParam String field,
                                                                  @RequestParam(required = false) String query) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.debug("autocomplete: field={}, query={}, clientId={}", field, query, clientId);
        List<String> suggestions = salesService.getAutocompleteSuggestions(field, query, clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", suggestions));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<List<Sales>>> save(@RequestBody SalesRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: received sales entry for farmer={}, rows={}, clientId={}",
                request.getFarmerName(), request.getRows() == null ? 0 : request.getRows().size(), clientId);
        List<SalesService.SalesLineInput> lines = new ArrayList<>();
        if (request.getRows() != null) {
            for (SalesLineDto dto : request.getRows()) {
                SalesService.SalesLineInput line = new SalesService.SalesLineInput();
                line.setFlowerType(dto.getFlowerType());
                line.setTotalWeight(dto.getTotalWeight());
                line.setPrice(dto.getPrice());
                line.setAmount(dto.getAmount());
                line.setCustomerName(dto.getCustomerName());
                line.setBagCount(dto.getBagCount());
                lines.add(line);
            }
        }
        List<Sales> saved = salesService.saveSales(
                request.getFarmerName(), request.getSalesDate(), lines,
                clientId, clientUsername,
                request.getTotalSalesAmt(), request.getCommissionAmt(), request.getNetAmount(),
                request.getFinalTotal(), request.getDebitAmount());
        return ResponseEntity.ok(ApiResponse.success("Sales saved successfully", saved));
    }

    @PutMapping("/update/{salesId}")
    public ResponseEntity<ApiResponse<Sales>> update(@PathVariable Long salesId, @RequestBody SalesLineDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("update: received update request for salesId={}, clientId={}", salesId, clientId);
        Sales updated = salesService.updateSales(
                salesId,
                clientId,
                clientUsername,
                request.getFarmerName(),
                request.getSalesDate(),
                request.getFlowerType(),
                request.getTotalWeight(),
                request.getPrice(),
                request.getCustomerName()
        );
        return ResponseEntity.ok(ApiResponse.success("Sales record updated successfully", updated));
    }
}
