package com.billing.service.impl;

import com.billing.dao.ClientMasterDao;
import com.billing.entity.ClientMaster;
import com.billing.service.ClientMasterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientMasterServiceImpl implements ClientMasterService {

    private static final Logger logger = LoggerFactory.getLogger(ClientMasterServiceImpl.class);
    private final ClientMasterDao clientMasterDao;

    @Autowired
    public ClientMasterServiceImpl(ClientMasterDao clientMasterDao) {
        this.clientMasterDao = clientMasterDao;
    }

    @Override
    public ClientMaster saveClient(ClientMaster clientMaster) {
        return clientMasterDao.save(clientMaster);
    }

    @Override
    public ClientMaster getClientById(Long clientId) {
        return clientMasterDao.findById(clientId);
    }

    @Override
    public ClientMaster getClientByUsername(String username) {
        return clientMasterDao.findByUsername(username);
    }

    @Override
    public List<ClientMaster> getAllClients() {
        return clientMasterDao.findAll();
    }

    @Override
    public void deleteClient(Long clientId) {
        clientMasterDao.deleteById(clientId);
    }

    @Override
    public boolean existsByUsername(String username) {
        return clientMasterDao.existsByUsername(username);
    }
}
