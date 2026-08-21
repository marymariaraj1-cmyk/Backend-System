package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.entity.FlowerMaster;
import com.billing.service.FlowerMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flowers")
public class FlowerMasterController {

    private static final Logger logger = LoggerFactory.getLogger(FlowerMasterController.class);

    private final FlowerMasterService flowerMasterService;

    public FlowerMasterController(FlowerMasterService flowerMasterService) {
        this.flowerMasterService = flowerMasterService;
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<FlowerMaster>>> getAllFlowers() {
        Long clientId = SessionConfig.getCurrentClientId();
        return ResponseEntity.ok(ApiResponse.success("OK", flowerMasterService.getFlowersByClientId(clientId)));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Void>> saveFlower(@RequestBody FlowerMaster flowerMaster) {
        logger.info("saveFlower: flowerName={}, flowerId={}", flowerMaster.getFlowerName(), flowerMaster.getFlowerId());
        Long clientId = SessionConfig.getCurrentClientId();
        String clientUsername = SessionConfig.getCurrentClientUsername();
        flowerMaster.setClientId(clientId);
        flowerMaster.setClientUsername(clientUsername);

        if (flowerMaster.getFlowerName() == null || flowerMaster.getFlowerName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Flower name is required"));
        }

        boolean isUpdate = flowerMaster.getFlowerId() != null && !flowerMaster.getFlowerId().isEmpty();

        if (isUpdate) {
            if (flowerMasterService.existsByClientIdAndFlowerNameExcludingId(clientId, flowerMaster.getFlowerName().trim(), flowerMaster.getFlowerId())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Flower name already exists"));
            }
        } else {
            if (flowerMasterService.existsByClientIdAndFlowerName(clientId, flowerMaster.getFlowerName().trim())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Flower name already exists"));
            }
        }

        flowerMaster.setFlowerName(flowerMaster.getFlowerName().trim());
        flowerMasterService.saveFlower(flowerMaster);
        return ResponseEntity.ok(ApiResponse.success(isUpdate ? "Flower updated successfully" : "Flower saved successfully", null));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFlower(@PathVariable String id) {
        Long clientId = SessionConfig.getCurrentClientId();
        logger.info("deleteFlower: id={}, clientId={}", id, clientId);
        flowerMasterService.deleteFlower(id, clientId);
        return ResponseEntity.ok(ApiResponse.success("Flower deleted successfully", null));
    }
}
