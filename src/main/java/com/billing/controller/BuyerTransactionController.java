package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.BuyerTransactionRequestDto;
import com.billing.entity.BuyerTransaction;
import com.billing.service.BuyerTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/buyer-transaction")
public class BuyerTransactionController {

    private static final Logger logger = LoggerFactory.getLogger(BuyerTransactionController.class);

    private final BuyerTransactionService buyerTransactionService;

    public BuyerTransactionController(BuyerTransactionService buyerTransactionService) {
        this.buyerTransactionService = buyerTransactionService;
    }

    @GetMapping("/master-data")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getMasterData() {
        Long clientId = SessionConfig.getCurrentClientId();
        Map<String, List<String>> data = new HashMap<>();
        data.put("buyers", buyerTransactionService.getBuyerNames(clientId));
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<BuyerTransaction>> save(@RequestBody BuyerTransactionRequestDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: received buyer transaction for buyer={}, date={}, clientId={}",
                request.getBuyerName(), request.getTransactionDate(), clientId);
        BuyerTransaction saved = buyerTransactionService.saveTransaction(
                request.getBuyerName(), request.getTransactionDate(),
                request.getAmountReceived(), request.getDiscountAmt(),
                clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Transaction saved successfully", saved));
    }

    @GetMapping("/opening-balance")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> openingBalance(@RequestParam String buyerName) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("openingBalance: buyer={}, clientId={}", buyerName, clientId);
        BigDecimal balance = buyerTransactionService.getOpeningBalance(buyerName, clientId);
        Map<String, BigDecimal> data = new HashMap<>();
        data.put("openingBalance", balance);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<BuyerTransaction>>> history(@RequestParam String buyerName,
                                                                       @RequestParam String fromDate,
                                                                       @RequestParam String toDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("history: buyer={}, from={}, to={}, clientId={}", buyerName, fromDate, toDate, clientId);
        List<BuyerTransaction> history = buyerTransactionService.getTransactionHistory(buyerName, fromDate, toDate, clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", history));
    }
}
