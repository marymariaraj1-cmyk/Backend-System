package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.dto.DailyCashBookSaveRequest;
import com.billing.service.DailyCashBookService;
import com.billing.util.SalesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/daily-cash-book")
public class DailyCashBookController {

    private static final Logger logger = LoggerFactory.getLogger(DailyCashBookController.class);

    private final DailyCashBookService dailyCashBookService;

    public DailyCashBookController(DailyCashBookService dailyCashBookService) {
        this.dailyCashBookService = dailyCashBookService;
    }

    @GetMapping("/compute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compute(@RequestParam String bookDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        String shopName = SessionConfig.getCurrentShopName();
        try {
            LocalDate date = SalesUtil.parseDate(bookDate);
            Map<String, Object> blocks = dailyCashBookService.computeBlocks(clientId, date);
            blocks.put("shopName", shopName);
            return ResponseEntity.ok(ApiResponse.success("OK", blocks));
        } catch (Exception e) {
            logger.error("compute: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to calculate daily cash book"));
        }
    }

    @GetMapping("/load")
    public ResponseEntity<ApiResponse<Map<String, Object>>> load(@RequestParam String bookDate) {
        Long clientId = SessionConfig.getCurrentClientId();
        String shopName = SessionConfig.getCurrentShopName();
        try {
            LocalDate date = SalesUtil.parseDate(bookDate);
            Map<String, Object> book = dailyCashBookService.loadBook(clientId, date);
            if (book == null) {
                return ResponseEntity.ok(ApiResponse.success("NOT_FOUND", null));
            }
            book.put("shopName", shopName);
            return ResponseEntity.ok(ApiResponse.success("OK", book));
        } catch (Exception e) {
            logger.error("load: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to load daily cash book"));
        }
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(@RequestBody DailyCashBookSaveRequest request) {
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        try {
            Map<String, Object> saved = dailyCashBookService.saveBook(clientId, clientUsername, request);
            saved.put("shopName", SessionConfig.getCurrentShopName());
            return ResponseEntity.ok(ApiResponse.success("Daily cash book saved successfully", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("save: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to save daily cash book"));
        }
    }
}
