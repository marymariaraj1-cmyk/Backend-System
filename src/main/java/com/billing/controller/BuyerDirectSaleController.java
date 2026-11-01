package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.BuyerDirectSaleDto;
import com.billing.entity.Sales;
import com.billing.service.BuyerDirectSaleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/buyer-direct-sale")
public class BuyerDirectSaleController {

    private static final Logger logger = LoggerFactory.getLogger(BuyerDirectSaleController.class);

    private final BuyerDirectSaleService buyerDirectSaleService;

    public BuyerDirectSaleController(BuyerDirectSaleService buyerDirectSaleService) {
        this.buyerDirectSaleService = buyerDirectSaleService;
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<List<Sales>>> save(@RequestBody BuyerDirectSaleDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: buyer direct sale for buyer={}, rows={}, clientId={}",
                request == null ? null : request.getBuyerName(),
                request == null || request.getRows() == null ? 0 : request.getRows().size(), clientId);
        List<Sales> saved = buyerDirectSaleService.saveBuyerDirectSale(request, clientId, clientUsername);
        return ResponseEntity.ok(ApiResponse.success("Buyer direct sale saved successfully", saved));
    }
}
