package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.entity.ClientMaster;
import com.billing.service.ClientMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientMasterController {

    private static final Logger logger = LoggerFactory.getLogger(ClientMasterController.class);

    private final ClientMasterService clientMasterService;
    private final PasswordEncoder passwordEncoder;

    public ClientMasterController(ClientMasterService clientMasterService, PasswordEncoder passwordEncoder) {
        this.clientMasterService = clientMasterService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<ClientMaster>>> getAllClients() {
        List<ClientMaster> clients = clientMasterService.getAllClients();
        clients.forEach(c -> c.setClientPassword(null));
        return ResponseEntity.ok(ApiResponse.success("OK", clients));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<ClientMaster>> saveClient(@RequestBody ClientMaster clientMaster) {
        logger.info("saveClient: username={}", clientMaster.getClientUsername());
        if (clientMaster.getClientId() == null && clientMasterService.existsByUsername(clientMaster.getClientUsername())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Client username already exists"));
        }

        if (clientMaster.getClientId() != null) {
            ClientMaster existing = clientMasterService.getClientById(clientMaster.getClientId());
            if (existing != null) {
                clientMaster.setClientPassword(
                        clientMaster.getClientPassword() != null && !clientMaster.getClientPassword().isEmpty()
                                ? passwordEncoder.encode(clientMaster.getClientPassword())
                                : existing.getClientPassword()
                );
            }
        } else {
            if (clientMaster.getClientPassword() == null || clientMaster.getClientPassword().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Password is required"));
            }
            clientMaster.setClientPassword(passwordEncoder.encode(clientMaster.getClientPassword()));
        }

        ClientMaster saved = clientMasterService.saveClient(clientMaster);
        saved.setClientPassword(null);
        return ResponseEntity.ok(ApiResponse.success("Client saved successfully", saved));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable Long id) {
        logger.info("deleteClient: id={}", id);
        clientMasterService.deleteClient(id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted successfully", null));
    }
}
