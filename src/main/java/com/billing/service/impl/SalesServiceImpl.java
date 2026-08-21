package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.BuyerMasterDao;
import com.billing.dao.ClientSlotSequenceDao;
import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerMasterDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.SalesDao;
import com.billing.dao.SalesTotalSummaryDao;
import com.billing.entity.BuyerLedger;
import com.billing.entity.FarmerLedger;
import com.billing.entity.Sales;
import com.billing.entity.SalesTotalSummary;
import com.billing.service.LedgerSettlementService;
import com.billing.service.SalesService;
import com.billing.util.RoundOffUtil;
import com.billing.util.SalesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalesServiceImpl implements SalesService {

    private static final Logger logger = LoggerFactory.getLogger(SalesServiceImpl.class);

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
    private final SalesDao salesDao;
    private final FarmerMasterDao farmerMasterDao;
    private final FlowerMasterDao flowerMasterDao;
    private final BuyerMasterDao buyerMasterDao;
    private final FarmerLedgerDao farmerLedgerDao;
    private final BuyerLedgerDao buyerLedgerDao;
    private final ClientSlotSequenceDao clientSlotSequenceDao;
    private final SalesTotalSummaryDao salesTotalSummaryDao;
    private final LedgerSettlementService ledgerSettlementService;

    @Autowired
    public SalesServiceImpl(DataSource dataSource,
                            SalesDao salesDao,
                            FarmerMasterDao farmerMasterDao,
                            FlowerMasterDao flowerMasterDao,
                            BuyerMasterDao buyerMasterDao,
                            FarmerLedgerDao farmerLedgerDao,
                            BuyerLedgerDao buyerLedgerDao,
                            ClientSlotSequenceDao clientSlotSequenceDao,
                            SalesTotalSummaryDao salesTotalSummaryDao,
                            LedgerSettlementService ledgerSettlementService) {
        this.dataSource = dataSource;
        this.salesDao = salesDao;
        this.farmerMasterDao = farmerMasterDao;
        this.flowerMasterDao = flowerMasterDao;
        this.buyerMasterDao = buyerMasterDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.buyerLedgerDao = buyerLedgerDao;
        this.clientSlotSequenceDao = clientSlotSequenceDao;
        this.salesTotalSummaryDao = salesTotalSummaryDao;
        this.ledgerSettlementService = ledgerSettlementService;
    }

    @Override
    public Map<String, List<String>> getMasterNames(Long clientId) {
        Map<String, List<String>> data = new HashMap<>();
        data.put("farmers", farmerMasterDao.findNamesByClientId(clientId));
        data.put("flowers", flowerMasterDao.findNamesByClientId(clientId));
        data.put("buyers", buyerMasterDao.findNamesByClientId(clientId));
        return data;
    }

    @Override
    public List<Sales> saveSales(String farmerName, String salesDate, List<SalesLineInput> lines,
                                  Long clientId, String clientUsername,
                                  String totalSalesAmtStr, String commissionAmtStr, String netAmountStr,
                                  String finalTotalStr, String debitAmountStr) {
        logger.info("saveSales: entering for farmer={}, date={}, lineCount={}",
                farmerName, salesDate, lines == null ? 0 : lines.size());

        if (farmerName == null || farmerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Farmer name is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one sales row is required");
        }

        String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, farmerName.trim());
        if (farmerId == null) {
            throw new IllegalArgumentException("Farmer name '" + farmerName + "' not found in Farmer Master. Please add it there first.");
        }

        BigDecimal finalTotal = RoundOffUtil.round(parseBigDecimal(finalTotalStr));
        BigDecimal debitAmount = RoundOffUtil.round(parseBigDecimal(debitAmountStr));
        BigDecimal totalSalesAmt = RoundOffUtil.round(parseBigDecimal(totalSalesAmtStr));
        BigDecimal commissionAmt = RoundOffUtil.round(parseBigDecimal(commissionAmtStr));
        BigDecimal netAmount = RoundOffUtil.round(parseBigDecimal(netAmountStr));

        String flag = (debitAmount.compareTo(BigDecimal.ZERO) == 0) ? "C" : "D";

        LocalDate date = SalesUtil.parseDate(salesDate);

        List<Sales> entities = new ArrayList<>();
        List<String> validFlowers = flowerMasterDao.findNamesByClientId(clientId);
        Set<String> flowerSet = validFlowers.stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        for (SalesLineInput line : lines) {
            validateLine(line);

            String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, line.getCustomerName().trim());
            if (buyerId == null) {
                throw new IllegalArgumentException("Buyer '" + line.getCustomerName() + "' not found in Buyer Master. Please add it there first.");
            }

            if (!flowerSet.contains(line.getFlowerType().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Flower '" + line.getFlowerType() + "' not found in Flower Master. Please add it there first.");
            }

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
            sales.setFarmerName(farmerName.trim());
            sales.setSalesDate(date);
            sales.setFlowerType(line.getFlowerType().trim());
            sales.setTotalWeight(weight);
            sales.setPrice(amount);
            sales.setBuyerId(buyerId);
            sales.setPerKgRate(rate);
            sales.setCustName(line.getCustomerName().trim());
            sales.setDebitCreditFlag(flag);
            entities.add(sales);
        }

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            int slotNo = clientSlotSequenceDao.nextSlotNumber(clientId, clientUsername, conn);
            String saleSlotId = clientUsername + "Slot" + slotNo;
            logger.info("saveSales: generated saleSlotId={}", saleSlotId);

            for (Sales sales : entities) {
                sales.setSaleSlotId(saleSlotId);
            }

            List<Sales> saved = salesDao.saveBatch(entities, conn);
            logger.info("saveSales: sales batch inserted, count={}", saved.size());

            String allSalesIds = saved.stream()
                    .map(s -> String.valueOf(s.getSalesId()))
                    .collect(Collectors.joining(","));

            FarmerLedger farmerLedger = new FarmerLedger();
            farmerLedger.setClientId(clientId);
            farmerLedger.setClientUsername(clientUsername);
            farmerLedger.setFarmerId(farmerId);
            farmerLedger.setFarmerName(farmerName.trim());
            farmerLedger.setSalesDate(date);
            farmerLedger.setCreditAmt(finalTotal);
            farmerLedger.setDebitAmt(BigDecimal.ZERO);
            farmerLedger.setSalesIds(allSalesIds);
            farmerLedgerDao.insert(farmerLedger, conn);
            logger.info("saveSales: farmer ledger inserted, creditAmt={}", finalTotal);
            ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, date, conn);

            for (Sales sales : saved) {
                String customerName = sales.getCustName();
                String buyerId = sales.getBuyerId();
                BigDecimal rowAmount = sales.getPrice();

                boolean isDirectPayment = isDirectPayment(customerName);

                BuyerLedger buyerLedger = new BuyerLedger();
                buyerLedger.setClientId(clientId);
                buyerLedger.setClientUsername(clientUsername);
                buyerLedger.setBuyerId(buyerId);
                buyerLedger.setBuyerName(customerName);
                buyerLedger.setSalesDate(date);
                buyerLedger.setSalesIds(String.valueOf(sales.getSalesId()));

                if (isDirectPayment) {
                    buyerLedger.setCreditAmt(rowAmount);
                    buyerLedger.setDebitAmt(BigDecimal.ZERO);
                } else {
                    buyerLedger.setCreditAmt(BigDecimal.ZERO);
                    buyerLedger.setDebitAmt(rowAmount);
                }

                buyerLedgerDao.insert(buyerLedger, conn);
                ledgerSettlementService.settleBuyerIfClosed(clientId, buyerId, date, conn);
                logger.debug("saveSales: buyer ledger inserted/updated for buyer={}, amount={}, isDirectPayment={}", customerName, rowAmount, isDirectPayment);
            }

            SalesTotalSummary summary = new SalesTotalSummary();
            summary.setClientId(clientId);
            summary.setClientUsername(clientUsername);
            summary.setFarmerId(farmerId);
            summary.setFarmerName(farmerName.trim());
            summary.setSalesDate(date);
            summary.setTotalSalesAmt(totalSalesAmt);
            summary.setCommissionAmt(commissionAmt);
            summary.setTotalNetAmt(netAmount);
            summary.setDebitAmt(debitAmount);
            summary.setFinalAmt(finalTotal);
            salesTotalSummaryDao.upsert(summary, conn);
            logger.info("saveSales: sales total summary upserted for farmerId={}, date={}", farmerId, date);

            conn.commit();
            logger.info("saveSales: transaction committed successfully");
            return saved;

        } catch (Exception e) {
            logger.error("saveSales: transaction failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("saveSales: transaction rolled back");
                } catch (Exception rollbackEx) {
                    logger.error("saveSales: rollback failed", rollbackEx);
                }
            }
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to save sales records", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception closeEx) {
                    logger.error("saveSales: error closing connection", closeEx);
                }
            }
        }
    }

    @Override
    public Sales updateSales(Long salesId, Long clientId, String clientUsername, String farmerName, String salesDate,
                              String flowerType, String totalWeight, String price, String customerName) {
        logger.info("updateSales: entering for salesId={}", salesId);

        if (salesId == null) {
            throw new IllegalArgumentException("Sales ID is required for update");
        }
        if (farmerName == null || farmerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Farmer name is required");
        }
        if (flowerType == null || flowerType.trim().isEmpty()) {
            throw new IllegalArgumentException("Flower type is required");
        }
        if (customerName == null || customerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (!SalesUtil.isDecimal(totalWeight)) {
            throw new IllegalArgumentException("Total weight must be a valid decimal number");
        }
        if (!SalesUtil.isDecimal(price)) {
            throw new IllegalArgumentException("Price must be a valid decimal number");
        }
        if (new BigDecimal(totalWeight.trim()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total weight must be greater than zero");
        }
        if (new BigDecimal(price.trim()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }

        LocalDate date = SalesUtil.parseDate(salesDate);

        BigDecimal weight = new BigDecimal(totalWeight.trim());
        BigDecimal rate = new BigDecimal(price.trim());
        BigDecimal amount = RoundOffUtil.round(weight.multiply(rate));

        Sales sales = new Sales();
        sales.setSalesId(salesId);
        sales.setClientId(clientId);
        sales.setClientUsername(clientUsername);
        sales.setFarmerName(farmerName.trim());
        sales.setSalesDate(date);
        sales.setFlowerType(flowerType.trim());
        sales.setTotalWeight(weight);
        sales.setPrice(amount);
        sales.setPerKgRate(rate);
        sales.setCustName(customerName.trim());

        Sales updated = salesDao.updateSales(sales);
        logger.info("updateSales: exiting, updated salesId={}", updated.getSalesId());
        return updated;
    }

    @Override
    public List<String> getAutocompleteSuggestions(String field, String query, Long clientId) {
        logger.debug("getAutocompleteSuggestions: field={}, query={}, clientId={}", field, query, clientId);
        List<String> master = fetchMasterValues(field, clientId);
        if (query == null || query.trim().isEmpty()) {
            return master;
        }
        String lower = query.trim().toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String value : master) {
            if (value.toLowerCase(Locale.ROOT).contains(lower)) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    private List<String> fetchMasterValues(String field, Long clientId) {
        if (clientId == null) {
            return new ArrayList<>();
        }
        if ("farmer".equalsIgnoreCase(field)) {
            return farmerMasterDao.findNamesByClientId(clientId);
        } else if ("flower".equalsIgnoreCase(field)) {
            return flowerMasterDao.findNamesByClientId(clientId);
        } else if ("customer".equalsIgnoreCase(field)) {
            return buyerMasterDao.findNamesByClientId(clientId);
        }
        return new ArrayList<>();
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

    private void validateLine(SalesLineInput line) {
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

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
