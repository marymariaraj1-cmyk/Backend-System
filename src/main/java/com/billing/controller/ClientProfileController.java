package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.dto.ApiResponse;
import com.billing.entity.ClientMaster;
import com.billing.service.ClientMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/client-profile")
public class ClientProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ClientProfileController.class);

    private final ClientMasterService clientMasterService;

    public ClientProfileController(ClientMasterService clientMasterService) {
        this.clientMasterService = clientMasterService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getShopProfile() {
        Long clientId = SessionConfig.getCurrentClientId();
        if (clientId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }
        try {
            ClientMaster client = clientMasterService.getClientById(clientId);
            if (client == null) {
                return ResponseEntity.status(404).body(ApiResponse.error("Client not found"));
            }
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("shopName", client.getClientShopName());
            profile.put("shopAddress", client.getClientShopAddress());
            profile.put("contactNo", client.getClientContactNo());
            return ResponseEntity.ok(ApiResponse.success("OK", profile));
        } catch (Exception e) {
            logger.error("getShopProfile: error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to load shop profile"));
        }
    }
}
