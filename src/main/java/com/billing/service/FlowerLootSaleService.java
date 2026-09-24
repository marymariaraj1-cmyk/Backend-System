package com.billing.service;

import com.billing.dto.FlowerLootSaleDto;
import com.billing.entity.Sales;

import java.util.List;

public interface FlowerLootSaleService {

    List<Sales> saveFlowerLootSale(FlowerLootSaleDto dto, Long clientId, String clientUsername);
}