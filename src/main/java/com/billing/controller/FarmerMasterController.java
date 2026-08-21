package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.entity.FarmerMaster;
import com.billing.service.FarmerMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/farmers")
public class FarmerMasterController {

    private static final Logger logger = LoggerFactory.getLogger(FarmerMasterController.class);

    private final FarmerMasterService farmerMasterService;

    public FarmerMasterController(FarmerMasterService farmerMasterService) {
        this.farmerMasterService = farmerMasterService;
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<FarmerMaster>>> getAllFarmers() {
        Long clientId = SessionConfig.getCurrentClientId();
        return ResponseEntity.ok(ApiResponse.success("OK", farmerMasterService.getFarmersByClientId(clientId)));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Void>> saveFarmer(@RequestBody FarmerMaster farmerMaster) {
        logger.info("saveFarmer: farmerName={}, farmerId={}", farmerMaster.getFarmerName(), farmerMaster.getFarmerId());
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        farmerMaster.setClientId(clientId);
        farmerMaster.setClientUsername(clientUsername);

        if (farmerMaster.getFarmerName() == null || farmerMaster.getFarmerName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Farmer name is required"));
        }

        boolean isUpdate = farmerMaster.getFarmerId() != null && !farmerMaster.getFarmerId().isEmpty();

        if (isUpdate) {
            if (farmerMasterService.existsByClientIdAndFarmerNameExcludingId(clientId, farmerMaster.getFarmerName().trim(), farmerMaster.getFarmerId())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Farmer name already exists"));
            }
        } else {
            if (farmerMasterService.existsByClientIdAndFarmerName(clientId, farmerMaster.getFarmerName().trim())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Farmer name already exists"));
            }
        }

        farmerMaster.setFarmerName(farmerMaster.getFarmerName().trim());
        farmerMasterService.saveFarmer(farmerMaster);
        return ResponseEntity.ok(ApiResponse.success(isUpdate ? "Farmer updated successfully" : "Farmer saved successfully", null));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFarmer(@PathVariable String id) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("deleteFarmer: id={}, clientId={}", id, clientId);
        farmerMasterService.deleteFarmer(id, clientId);
        return ResponseEntity.ok(ApiResponse.success("Farmer deleted successfully", null));
    }
}
