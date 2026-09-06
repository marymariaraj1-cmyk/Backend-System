package com.billing.service;

import com.billing.entity.FarmerTransaction;

import java.util.List;

public interface FarmerTransactionService {

    List<String> getFarmerNames(Long clientId);

    FarmerTransaction saveTransaction(String farmerName, String transactionDate,
                                      String excessDebitAmt, String debitAmt, String paymentMode,
                                      Long clientId, String clientUsername);

    List<FarmerTransaction> getTransactionHistory(String farmerName, String fromDate, String toDate, Long clientId);
}
