package com.billing.service;

import com.billing.entity.ClientMaster;

import java.util.List;

public interface ClientMasterService {
    ClientMaster saveClient(ClientMaster clientMaster);
    ClientMaster getClientById(Long clientId);
    ClientMaster getClientByUsername(String username);
    List<ClientMaster> getAllClients();
    void deleteClient(Long clientId);
    boolean existsByUsername(String username);
}
