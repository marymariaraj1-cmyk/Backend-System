package com.billing.service;

import com.billing.dto.BuyerDirectSaleDto;
import com.billing.entity.Sales;

import java.util.List;

/**
 * Buyer Direct Sale (save): buyer side only, no commission.
 * Writes SALES rows with FARMER_NAME = marker and FARMER_ID NULL, then updates the buyer
 * ledger (debit = sum of the buyer's amounts for the date, opening carry-forward, running
 * balances, SALES_IDS) exactly as Multiple Sales Entry does.
 * The farmer ledger and BLOOMBUDDY_SALES_TOTALSUMMARY are never touched.
 */
public interface BuyerDirectSaleService {

    List<Sales> saveBuyerDirectSale(BuyerDirectSaleDto request, Long clientId, String clientUsername);
}
