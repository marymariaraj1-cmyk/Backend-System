package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.MultiSalesEntryDto;
import com.billing.entity.Sales;
import com.billing.service.MultiSalesEntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/multi-sales")
public class MultiSalesEntryController {

    private static final Logger logger = LoggerFactory.getLogger(MultiSalesEntryController.class);

    private final MultiSalesEntryService multiSalesEntryService;

    public MultiSalesEntryController(MultiSalesEntryService multiSalesEntryService) {
        this.multiSalesEntryService = multiSalesEntryService;
    }

    @GetMapping("/master-data")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getMasterData() {
        Long clientId = SessionConfig.getCurrentClientId();
        Map<String, List<String>> data = multiSalesEntryService.getMasterNames(clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @GetMapping("/today-entries")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTodayEntries() {
        Long clientId = SessionConfig.getCurrentClientId();
        List<Map<String, Object>> entries = multiSalesEntryService.getTodayEntries(clientId);
        return ResponseEntity.ok(ApiResponse.success("OK", entries));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<List<Sales>>> save(@RequestBody MultiSalesEntryDto request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        logger.info("save: received multi-sales entry, rows={}, clientId={}",
                request.getRows() == null ? 0 : request.getRows().size(), clientId);
        List<MultiSalesEntryService.MultiSalesLine> lines = new ArrayList<>();
        if (request.getRows() != null) {
            for (MultiSalesEntryDto.MultiSalesLineDto dto : request.getRows()) {
                MultiSalesEntryService.MultiSalesLine line = new MultiSalesEntryService.MultiSalesLine();
                line.setFarmerName(dto.getFarmerName());
                line.setFlowerType(dto.getFlowerType());
                line.setTotalWeight(dto.getTotalWeight());
                line.setPrice(dto.getPrice());
                line.setAmount(dto.getAmount());
                line.setCustomerName(dto.getCustomerName());
                line.setBagCount(dto.getBagCount());
                lines.add(line);
            }
        }
        String debitAmount = request.getDebitAmount();
        List<Sales> saved;
        if (debitAmount != null && !debitAmount.trim().isEmpty()) {
            saved = multiSalesEntryService.saveMultiSales(lines, debitAmount, clientId, clientUsername);
        } else {
            saved = multiSalesEntryService.saveMultiSales(lines, clientId, clientUsername);
        }
        return ResponseEntity.ok(ApiResponse.success("Multiple sales saved successfully", saved));
    }
}
