package com.billing.service;

import com.billing.dto.SalesRequestDto;
import com.billing.entity.Sales;

import java.util.List;

/**
 * Flower Intake Sale (save): farmer side only.
 * Writes SALES rows with CUST_NAME = marker and BUYER_ID NULL, then updates the
 * farmer ledger and BLOOMBUDDY_SALES_TOTALSUMMARY exactly as Sales Entry does.
 * The buyer ledger is never touched.
 */
public interface FlowerIntakeSaleService {

    List<Sales> saveFlowerIntakeSale(SalesRequestDto request, Long clientId, String clientUsername);
}
