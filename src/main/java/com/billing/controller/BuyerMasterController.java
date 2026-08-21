package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.entity.BuyerMaster;
import com.billing.service.BuyerMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buyers")
public class BuyerMasterController {

    private static final Logger logger = LoggerFactory.getLogger(BuyerMasterController.class);

    private final BuyerMasterService buyerMasterService;

    public BuyerMasterController(BuyerMasterService buyerMasterService) {
        this.buyerMasterService = buyerMasterService;
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<BuyerMaster>>> getAllBuyers() {
        Long clientId = SessionConfig.getCurrentClientId();
        return ResponseEntity.ok(ApiResponse.success("OK", buyerMasterService.getBuyersByClientId(clientId)));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Void>> saveBuyer(@RequestBody BuyerMaster buyerMaster) {
        logger.info("saveBuyer: buyerName={}, buyerId={}", buyerMaster.getBuyerName(), buyerMaster.getBuyerId());
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        buyerMaster.setClientId(clientId);
        buyerMaster.setClientUsername(clientUsername);

        if (buyerMaster.getBuyerName() == null || buyerMaster.getBuyerName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Buyer name is required"));
        }

        boolean isUpdate = buyerMaster.getBuyerId() != null && !buyerMaster.getBuyerId().isEmpty();

        if (isUpdate) {
            if (buyerMasterService.existsByClientIdAndBuyerNameExcludingId(clientId, buyerMaster.getBuyerName().trim(), buyerMaster.getBuyerId())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Buyer name already exists"));
            }
        } else {
            if (buyerMasterService.existsByClientIdAndBuyerName(clientId, buyerMaster.getBuyerName().trim())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Buyer name already exists"));
            }
        }

        buyerMaster.setBuyerName(buyerMaster.getBuyerName().trim());
        buyerMasterService.saveBuyer(buyerMaster);
        return ResponseEntity.ok(ApiResponse.success(isUpdate ? "Buyer updated successfully" : "Buyer saved successfully", null));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBuyer(@PathVariable String id) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("deleteBuyer: id={}, clientId={}", id, clientId);
        buyerMasterService.deleteBuyer(id, clientId);
        return ResponseEntity.ok(ApiResponse.success("Buyer deleted successfully", null));
    }
}
