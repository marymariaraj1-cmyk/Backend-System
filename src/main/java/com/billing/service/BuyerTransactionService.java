package com.billing.service;

import com.billing.entity.BuyerTransaction;

import java.math.BigDecimal;
import java.util.List;

public interface BuyerTransactionService {

    List<String> getBuyerNames(Long clientId);

    BuyerTransaction saveTransaction(String buyerName, String transactionDate,
                                     String cashPaidAmt, String disAmt, String paymentMode,
                                     Long clientId, String clientUsername);

    List<BuyerTransaction> getTransactionHistory(String buyerName, String fromDate, String toDate, Long clientId);

    BuyerTransaction updateTransaction(Long buyerTransactionId, String amountReceived, String discountAmt,
                                       String paymentMode, Long clientId);

    void deleteTransaction(Long buyerTransactionId, Long clientId);

    BigDecimal getOpeningBalance(String buyerName, Long clientId);
}
