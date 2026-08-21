package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.FarmerTransactionRequestDto;
import com.billing.entity.FarmerTransaction;
import com.billing.service.FarmerTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/farmer-transaction")
public class FarmerTransactionController {

    private static final Logger logger = LoggerFactory.getLogger(FarmerTransactionController.class);

    private final FarmerTransactionService farmerTransactionService;

    public FarmerTransactionController(FarmerTransactionService farmerTransactionService) {
        this.farmerTransactionService = farmerTransactionService;
    }

    @GetMapping("/master-data")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getMasterData() {
        Long clientId = SessionConfig.getCurrentClientId();
        Map<String, List<String>> data = new HashMap<>();
        data.put("farmers", farmerTransactionService.getFarmerNames(clientId));
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<FarmerTransaction>> save(@RequestBody FarmerTransactionRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: received farmer transaction for farmer={}, date={}, clientId={}",
                request.getFarmerName(), request.getTransactionDate(), clientId);
        FarmerTransaction saved = farmerTransactionService.saveTransaction(
                request.getFarmerName(), request.getTransactionDate(),
                request.getExcessDebitAmt(), request.getDebitAmt(),
                clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Transaction saved successfully", saved));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<FarmerTransaction>>> history(@RequestParam String farmerName,
                                                                        @RequestParam String fromDate,
                                                                        @RequestParam String toDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("history: farmer={}, from={}, to={}, clientId={}", farmerName, fromDate, toDate, clientId);
        List<FarmerTransaction> history = farmerTransactionService.getTransactionHistory(farmerName, fromDate, toDate, clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", history));
    }
}
