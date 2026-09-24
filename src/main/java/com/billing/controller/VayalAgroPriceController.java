package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.VayalAgroPriceData;
import com.billing.service.VayalAgroPriceService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/poc/flower-price")
public class VayalAgroPriceController {

    private static final Logger logger = LoggerFactory.getLogger(VayalAgroPriceController.class);

    private final VayalAgroPriceService vayalAgroPriceService;

    public VayalAgroPriceController(VayalAgroPriceService vayalAgroPriceService) {
        this.vayalAgroPriceService = vayalAgroPriceService;
    }

    @GetMapping("/salem")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSalemPrices() {
        try {
            VayalAgroPriceData result = vayalAgroPriceService.getSalemFlowerPrices();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rows", result.getRows());
            data.put("fetchedDate", result.getDate());
            return ResponseEntity.ok(ApiResponse.success("OK", data));
        } catch (Exception e) {
            logger.error("getSalemPrices: failed to fetch Vayal Agro prices", e);
            return ResponseEntity.status(502).body(ApiResponse.error("Failed to fetch Vayal Agro flower prices"));
        }
    }
}