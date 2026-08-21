package com.billing.dao;

import com.billing.entity.ClientMaster;

import java.util.List;

public interface ClientMasterDao {

    ClientMaster save(ClientMaster clientMaster);

    ClientMaster findById(Long clientId);

    ClientMaster findByUsername(String username);

    List<ClientMaster> findAll();

    void deleteById(Long clientId);

    boolean existsByUsername(String username);
}
