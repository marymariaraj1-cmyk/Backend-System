package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.BuyerMasterDao;
import com.billing.dao.ClientSlotSequenceDao;
import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerMasterDao;
import com.billing.dao.FarmerTransactionDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.MultiSalesEntryDao;
import com.billing.dao.SalesTotalSummaryDao;
import com.billing.entity.FarmerTransaction;
import com.billing.entity.BuyerLedger;
import com.billing.entity.FarmerLedger;
import com.billing.entity.Sales;
import com.billing.entity.SalesTotalSummary;
import com.billing.service.BagCountConfigService;
import com.billing.service.LedgerSettlementService;
import com.billing.service.MultiSalesEntryService;
import com.billing.util.RoundOffUtil;
import com.billing.util.SalesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MultiSalesEntryServiceImpl implements MultiSalesEntryService {

    private static final Logger logger = LoggerFactory.getLogger(MultiSalesEntryServiceImpl.class);

    private static final Set<String> DIRECT_PAYMENT_NAMES = new HashSet<>(Arrays.asList(
            "cash", "cash payment", "cash sale",
            "upi", "upi payment",
            "google pay", "gpay",
            "phonepe", "phone pe",
            "paytm",
            "online", "online payment",
            "card", "card payment",
            "neft", "rtgs", "imps"
    ));

    private final DataSource dataSource;
    private final MultiSalesEntryDao multiSalesEntryDao;
    private final FarmerMasterDao farmerMasterDao;
    private final FlowerMasterDao flowerMasterDao;
    private final BuyerMasterDao buyerMasterDao;
    private final FarmerLedgerDao farmerLedgerDao;
    private final BuyerLedgerDao buyerLedgerDao;
    private final ClientSlotSequenceDao clientSlotSequenceDao;
    private final SalesTotalSummaryDao salesTotalSummaryDao;
    private final FarmerTransactionDao farmerTransactionDao;
    private final LedgerSettlementService ledgerSettlementService;
    private final BagCountConfigService bagCountConfigService;

    @Autowired
    public MultiSalesEntryServiceImpl(DataSource dataSource,
                                       MultiSalesEntryDao multiSalesEntryDao,
                                       FarmerMasterDao farmerMasterDao,
                                       FlowerMasterDao flowerMasterDao,
                                       BuyerMasterDao buyerMasterDao,
                                       FarmerLedgerDao farmerLedgerDao,
                                       BuyerLedgerDao buyerLedgerDao,
                                       ClientSlotSequenceDao clientSlotSequenceDao,
                                       SalesTotalSummaryDao salesTotalSummaryDao,
                                       FarmerTransactionDao farmerTransactionDao,
                                       LedgerSettlementService ledgerSettlementService,
                                       BagCountConfigService bagCountConfigService) {
        this.dataSource = dataSource;
        this.multiSalesEntryDao = multiSalesEntryDao;
        this.farmerMasterDao = farmerMasterDao;
        this.flowerMasterDao = flowerMasterDao;
        this.buyerMasterDao = buyerMasterDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.buyerLedgerDao = buyerLedgerDao;
        this.clientSlotSequenceDao = clientSlotSequenceDao;
        this.salesTotalSummaryDao = salesTotalSummaryDao;
        this.farmerTransactionDao = farmerTransactionDao;
        this.ledgerSettlementService = ledgerSettlementService;
        this.bagCountConfigService = bagCountConfigService;
    }

    @Override
    public List<Sales> saveMultiSales(List<MultiSalesLine> lines, Long clientId, String clientUsername) {
        return saveMultiSales(lines, "0", clientId, clientUsername);
    }

    @Override
    public List<Sales> saveMultiSales(List<MultiSalesLine> lines, String debitAmountStr, Long clientId, String clientUsername) {
        logger.info("saveMultiSales: entering, lineCount={}, debitAmount={}, clientId={}", lines == null ? 0 : lines.size(), debitAmountStr, clientId);

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one sales row is required");
        }

        List<String> validFlowers = flowerMasterDao.findNamesByClientId(clientId);
        Set<String> flowerSet = validFlowers.stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        LocalDate date = LocalDate.now();
        List<Sales> entities = new ArrayList<>();

        for (MultiSalesLine line : lines) {
            if (line.getFarmerName() == null || line.getFarmerName().trim().isEmpty()) {
                throw new IllegalArgumentException("Farmer name is required in each row");
            }
            validateLine(line);

            String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, line.getFarmerName().trim());
            if (farmerId == null) {
                throw new IllegalArgumentException("Farmer '" + line.getFarmerName() + "' not found in Farmer Master. Please add it there first.");
            }

            String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, line.getCustomerName().trim());
            if (buyerId == null) {
                throw new IllegalArgumentException("Buyer '" + line.getCustomerName() + "' not found in Buyer Master. Please add it there first.");
            }

            if (!flowerSet.contains(line.getFlowerType().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Flower '" + line.getFlowerType() + "' not found in Flower Master. Please add it there first.");
            }

            String flowerId = flowerMasterDao.findIdByNameAndClientId(clientId, line.getFlowerType().trim());

            BigDecimal weight = BigDecimal.ZERO;
            BigDecimal rate = BigDecimal.ZERO;
            if (line.getTotalWeight() != null && !line.getTotalWeight().trim().isEmpty()) {
                weight = new BigDecimal(line.getTotalWeight().trim());
            }
            if (line.getPrice() != null && !line.getPrice().trim().isEmpty()) {
                rate = new BigDecimal(line.getPrice().trim());
            }
            BigDecimal amount = RoundOffUtil.round(new BigDecimal(line.getAmount().trim()));

            Sales sales = new Sales();
            sales.setClientId(clientId);
            sales.setClientUsername(clientUsername);
            sales.setFarmerId(farmerId);
            sales.setFarmerName(line.getFarmerName().trim());
            sales.setSalesDate(date);
            sales.setFlowerType(line.getFlowerType().trim());
            sales.setFlowerId(flowerId);
            sales.setBagCount(line.getBagCount());
            sales.setTotalWeight(weight);
            sales.setPrice(amount);
            sales.setBuyerId(buyerId);
            sales.setPerKgRate(rate);
            sales.setCustName(line.getCustomerName().trim());
            sales.setDebitCreditFlag("C");
            entities.add(sales);
        }

        validateBagCounts(clientId, date, entities);

        BigDecimal grandTotal = entities.stream()
                .map(Sales::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal commission = RoundOffUtil.round(grandTotal.multiply(new BigDecimal("0.10")));
        BigDecimal netAmount = RoundOffUtil.round(grandTotal.subtract(commission));
        BigDecimal finalTotal = netAmount;

        // Parse global debit for multi-sales (Task1)
        BigDecimal globalDebit = BigDecimal.ZERO;
        if (debitAmountStr != null && !debitAmountStr.trim().isEmpty()) {
            try {
                globalDebit = RoundOffUtil.round(new BigDecimal(debitAmountStr.trim()));
                if (globalDebit.compareTo(BigDecimal.ZERO) < 0) globalDebit = BigDecimal.ZERO;
            } catch (NumberFormatException e) {
                globalDebit = BigDecimal.ZERO;
            }
        }

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            int slotNo = clientSlotSequenceDao.nextSlotNumber(clientId, clientUsername, conn);
            String saleSlotId = clientUsername + "Slot" + slotNo;

            for (Sales sales : entities) {
                sales.setSaleSlotId(saleSlotId);
            }

            List<Sales> saved = multiSalesEntryDao.saveBatch(entities, conn);

            Map<String, List<Sales>> byFarmer = new LinkedHashMap<>();
            for (Sales s : saved) {
                byFarmer.computeIfAbsent(s.getFarmerId(), k -> new ArrayList<>()).add(s);
            }

            BigDecimal remainingDebit = globalDebit;
            int farmerIdx = 0;
            int farmerCount = byFarmer.size();

            for (Map.Entry<String, List<Sales>> entry : byFarmer.entrySet()) {
                String farmerId = entry.getKey();
                List<Sales> farmerRows = entry.getValue();
                String farmerName = farmerRows.get(0).getFarmerName();

                BigDecimal farmerTotal = farmerRows.stream()
                        .map(Sales::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal farmerCredit;
                if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                    farmerCredit = RoundOffUtil.round(farmerTotal.subtract(
                            farmerTotal.multiply(commission).divide(grandTotal, 2, RoundingMode.HALF_UP)
                    ));
                } else {
                    farmerCredit = farmerTotal;
                }

                FarmerLedger farmerLedger = new FarmerLedger();
                farmerLedger.setClientId(clientId);
                farmerLedger.setClientUsername(clientUsername);
                farmerLedger.setFarmerId(farmerId);
                farmerLedger.setFarmerName(farmerName);
                farmerLedger.setSalesDate(date);
                farmerLedger.setCreditAmt(farmerCredit);
                farmerLedger.setDebitAmt(BigDecimal.ZERO);
                String farmerSalesIds = farmerRows.stream()
                        .map(s -> String.valueOf(s.getSalesId()))
                        .collect(Collectors.joining(","));
                farmerLedger.setSalesIds(farmerSalesIds);
                farmerLedgerDao.insert(farmerLedger, conn);
                ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, date, conn);

                BigDecimal farmerCommission = BigDecimal.ZERO;
                if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                    farmerCommission = RoundOffUtil.round(
                            farmerTotal.multiply(commission).divide(grandTotal, 2, RoundingMode.HALF_UP));
                }

                // === Task 1: Distribute global debit per farmer proportionally (Task1 insert before Task2 SUM) ===
                BigDecimal farmerDebit;
                if (globalDebit.compareTo(BigDecimal.ZERO) > 0 && grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                    if (farmerIdx == farmerCount - 1) {
                        farmerDebit = RoundOffUtil.round(remainingDebit);
                    } else {
                        BigDecimal proportion = farmerTotal.divide(grandTotal, 10, RoundingMode.HALF_UP);
                        farmerDebit = RoundOffUtil.round(globalDebit.multiply(proportion));
                        remainingDebit = remainingDebit.subtract(farmerDebit);
                    }
                } else {
                    farmerDebit = BigDecimal.ZERO;
                }
                farmerIdx++;

                if (farmerDebit != null && farmerDebit.compareTo(BigDecimal.ZERO) > 0) {
                    FarmerTransaction auditTxn = new FarmerTransaction();
                    auditTxn.setClientId(clientId);
                    auditTxn.setClientUsername(clientUsername);
                    auditTxn.setFarmerId(farmerId);
                    auditTxn.setFarmerName(farmerName);
                    auditTxn.setTransactionDate(date);
                    auditTxn.setCashPaidAmt(BigDecimal.ZERO);
                    auditTxn.setExcessDebitAmt(BigDecimal.ZERO);
                    auditTxn.setDebAmt(farmerDebit);
                    auditTxn.setPaymentMode("C");
                    farmerTransactionDao.insert(auditTxn, conn);
                    logger.info("saveMultiSales: Task1 - inserted farmer transaction audit DEB_AMT={} for farmerId={}, date={}", farmerDebit, farmerId, date);
                }

                // === Task 2 & 3: Handle BLOOMBUDDY_SALES_TOTALSUMMARY DEBIT_AMT ===
                Map<String, Object> existingSummary = salesTotalSummaryDao.findRow(clientId, farmerId, date);
                SalesTotalSummary summary = new SalesTotalSummary();
                summary.setClientId(clientId);
                summary.setClientUsername(clientUsername);
                summary.setFarmerId(farmerId);
                summary.setFarmerName(farmerName);
                summary.setSalesDate(date);
                summary.setTotalSalesAmt(farmerTotal);
                summary.setCommissionAmt(farmerCommission);
                summary.setTotalNetAmt(farmerCredit);
                if (existingSummary == null) {
                    // Task 2: First-ever summary row - seed DEBIT from SUM of farmer transactions (includes Task1 if inserted)
                    BigDecimal totalDebitFromTxn = farmerTransactionDao.sumDebAmt(clientId, farmerId, date, conn);
                    if (totalDebitFromTxn == null) {
                        totalDebitFromTxn = BigDecimal.ZERO;
                    }
                    BigDecimal seededDebit = RoundOffUtil.round(totalDebitFromTxn);
                    BigDecimal seededFinal = farmerCredit.subtract(seededDebit);
                    summary.setDebitAmt(seededDebit);
                    summary.setFinalAmt(RoundOffUtil.round(seededFinal));
                    logger.info("saveMultiSales: Task2 - first summary row for farmerId={}, seeded DEBIT_AMT={} (SUM), FINAL={}", farmerId, seededDebit, summary.getFinalAmt());
                } else {
                    // Task 3: Existing summary - delta (add this batch's debit, currently 0)
                    summary.setDebitAmt(farmerDebit);
                    summary.setFinalAmt(farmerCredit);
                    logger.info("saveMultiSales: Task3 - existing summary for farmerId={}, delta DEBIT_AMT={}", farmerId, farmerDebit);
                }
                salesTotalSummaryDao.upsert(summary, conn);
            }

            for (Sales sales : saved) {
                String customerName = sales.getCustName();
                String buyerId = sales.getBuyerId();
                BigDecimal rowAmount = sales.getPrice();

                boolean isDirect = isDirectPayment(customerName);

                BuyerLedger buyerLedger = new BuyerLedger();
                buyerLedger.setClientId(clientId);
                buyerLedger.setClientUsername(clientUsername);
                buyerLedger.setBuyerId(buyerId);
                buyerLedger.setBuyerName(customerName);
                buyerLedger.setSalesDate(date);
                buyerLedger.setSalesIds(String.valueOf(sales.getSalesId()));

                if (isDirect) {
                    buyerLedger.setCreditAmt(rowAmount);
                    buyerLedger.setDebitAmt(BigDecimal.ZERO);
                } else {
                    buyerLedger.setCreditAmt(BigDecimal.ZERO);
                    buyerLedger.setDebitAmt(rowAmount);
                }

                buyerLedgerDao.insert(buyerLedger, conn);
                ledgerSettlementService.settleBuyerIfClosed(clientId, buyerId, date, conn);
            }

            conn.commit();
            logger.info("saveMultiSales: committed, slotId={}, rows={}", saleSlotId, saved.size());
            return saved;

        } catch (Exception e) {
            logger.error("saveMultiSales: transaction failed, rolling back", e);
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ex) { logger.error("rollback failed", ex); }
            }
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new RuntimeException("Failed to save multiple sales records", e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ex) { logger.error("close failed", ex); }
            }
        }
    }

    @Override
    public List<Map<String, Object>> getTodayEntries(Long clientId) {
        LocalDate today = LocalDate.now();
        List<Sales> rows = multiSalesEntryDao.findByClientIdAndDate(clientId, today);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Sales s : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("salesId", s.getSalesId());
            map.put("farmerName", s.getFarmerName());
            map.put("flowerType", s.getFlowerType());
            map.put("totalWeight", s.getTotalWeight());
            map.put("price", RoundOffUtil.round(s.getPerKgRate()));
            map.put("amount", RoundOffUtil.round(s.getPrice()));
            map.put("customerName", s.getCustName());
            map.put("saleSlotId", s.getSaleSlotId());
            result.add(map);
        }
        return result;
    }

    @Override
    public Map<String, List<String>> getMasterNames(Long clientId) {
        Map<String, List<String>> data = new HashMap<>();
        data.put("farmers", farmerMasterDao.findNamesByClientId(clientId));
        data.put("flowers", flowerMasterDao.findNamesByClientId(clientId));
        data.put("buyers", buyerMasterDao.findNamesByClientId(clientId));
        return data;
    }

    private boolean isDirectPayment(String customerName) {
        if (customerName == null) return false;
        String lower = customerName.trim().toLowerCase(Locale.ROOT);
        if (DIRECT_PAYMENT_NAMES.contains(lower)) return true;
        if (lower.contains("cash") || lower.contains("upi") || lower.contains("google pay")
                || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm")
                || lower.contains("online") || lower.contains("card payment")
                || lower.contains("neft") || lower.contains("rtgs") || lower.contains("imps")) {
            return true;
        }
        return false;
    }

    private void validateLine(MultiSalesLine line) {
        if (line.getFlowerType() == null || line.getFlowerType().trim().isEmpty()) {
            throw new IllegalArgumentException("Flower type is required");
        }
        if (line.getCustomerName() == null || line.getCustomerName().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (line.getTotalWeight() != null && !line.getTotalWeight().trim().isEmpty()
                && !SalesUtil.isDecimal(line.getTotalWeight())) {
            throw new IllegalArgumentException("Total weight must be a valid decimal number");
        }
        if (line.getPrice() != null && !line.getPrice().trim().isEmpty()
                && !SalesUtil.isDecimal(line.getPrice())) {
            throw new IllegalArgumentException("Price must be a valid decimal number");
        }
        if (line.getAmount() == null || line.getAmount().trim().isEmpty()) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (!SalesUtil.isDecimal(line.getAmount())) {
            throw new IllegalArgumentException("Amount must be a valid decimal number");
        }
        if (new BigDecimal(line.getAmount().trim()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private void validateBagCounts(Long clientId, LocalDate date, List<Sales> entities) {
        Map<String, Integer> bagByFlower = new LinkedHashMap<>();
        for (Sales sales : entities) {
            if (sales.getBagCount() == null || sales.getBagCount() <= 0) {
                continue;
            }
            bagByFlower.merge(sales.getFlowerId(), sales.getBagCount(), Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : bagByFlower.entrySet()) {
            String flowerId = entry.getKey();
            if (flowerId == null) {
                continue;
            }
            int sessionTotal = entry.getValue();
            Map<String, Object> config = bagCountConfigService.getConfig(clientId, flowerId, date);
            if (config == null) {
                continue;
            }
            String bagCheck = config.get("bagCheck") == null ? "" : String.valueOf(config.get("bagCheck"));
            if (!"E".equalsIgnoreCase(bagCheck)) {
                continue;
            }
            int configuredLimit = config.get("bagCount") == null ? 0 : ((Number) config.get("bagCount")).intValue();
            int savedTotal = bagCountConfigService.getSavedBagTotal(clientId, flowerId, date);
            if (savedTotal + sessionTotal > configuredLimit) {
                String flowerName = config.get("flowerName") == null ? flowerId : String.valueOf(config.get("flowerName"));
                throw new IllegalArgumentException(
                        "Bag count exceeded for flower '" + flowerName + "'. Allowed " + configuredLimit
                                + " but total is " + (savedTotal + sessionTotal) + ". Please increase the bag count in the configuration.");
            }
        }
    }
}
