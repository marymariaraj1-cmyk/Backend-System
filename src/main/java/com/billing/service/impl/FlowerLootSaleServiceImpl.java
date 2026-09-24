package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.BuyerMasterDao;
import com.billing.dao.ClientSlotSequenceDao;
import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerMasterDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.SalesDao;
import com.billing.dao.SalesTotalSummaryDao;
import com.billing.dto.FlowerLootSaleDto;
import com.billing.entity.BuyerLedger;
import com.billing.entity.FarmerLedger;
import com.billing.entity.Sales;
import com.billing.entity.SalesTotalSummary;
import com.billing.service.FlowerLootSaleService;
import com.billing.service.LedgerSettlementService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FlowerLootSaleServiceImpl implements FlowerLootSaleService {

    private static final Logger logger = LoggerFactory.getLogger(FlowerLootSaleServiceImpl.class);

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
    public FlowerLootSaleServiceImpl(DataSource dataSource,
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
    public List<Sales> saveFlowerLootSale(FlowerLootSaleDto dto, Long clientId, String clientUsername) {
        logger.info("saveFlowerLootSale: entering, buyerRows={}, farmerRows={}, clientId={}",
                dto.getBuyerRows() == null ? 0 : dto.getBuyerRows().size(),
                dto.getFarmerRows() == null ? 0 : dto.getFarmerRows().size(), clientId);

        if (dto.getBuyerRows() == null || dto.getBuyerRows().isEmpty()) {
            throw new IllegalArgumentException("At least one Buyer Purchase row is required");
        }
        if (dto.getFarmerRows() == null || dto.getFarmerRows().isEmpty()) {
            throw new IllegalArgumentException("At least one Farmer Sale row is required");
        }

        List<String> validFlowers = flowerMasterDao.findNamesByClientId(clientId);
        Set<String> flowerSet = validFlowers.stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        if (isBlank(dto.getFlowerName())) {
            throw new IllegalArgumentException("Flower name is required");
        }
        if (!flowerSet.contains(dto.getFlowerName().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Flower '" + dto.getFlowerName() + "' not found in Flower Master. Please add it there first.");
        }

        LocalDate date = SalesUtil.parseDate(dto.getSalesDate());

        List<Sales> buyerEntities = new ArrayList<>();
        Map<String, List<Sales>> buyerRowsByBuyerId = new LinkedHashMap<>();

        for (FlowerLootSaleDto.FlowerLootRowDto row : dto.getBuyerRows()) {
            validateBuyerRow(row);

            String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, row.getName().trim());
            if (buyerId == null) {
                throw new IllegalArgumentException("Buyer '" + row.getName() + "' not found in Buyer Master. Please add it there first.");
            }

            Sales sales = buildSales(clientId, clientUsername, date, dto, row);
            sales.setFarmerId(null);
            sales.setFarmerName(null);
            sales.setBuyerId(buyerId);
            sales.setCustName(row.getName().trim());
            buyerEntities.add(sales);

            buyerRowsByBuyerId.computeIfAbsent(buyerId, k -> new ArrayList<>()).add(sales);
        }

        List<Sales> farmerEntities = new ArrayList<>();
        Map<String, List<Sales>> farmerRowsByFarmerId = new LinkedHashMap<>();

        for (FlowerLootSaleDto.FlowerLootRowDto row : dto.getFarmerRows()) {
            validateFarmerRow(row);

            String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, row.getName().trim());
            if (farmerId == null) {
                throw new IllegalArgumentException("Farmer '" + row.getName() + "' not found in Farmer Master. Please add it there first.");
            }

            Sales sales = buildSales(clientId, clientUsername, date, dto, row);
            sales.setFarmerId(farmerId);
            sales.setFarmerName(row.getName().trim());
            sales.setBuyerId(null);
            sales.setCustName(null);
            farmerEntities.add(sales);

            farmerRowsByFarmerId.computeIfAbsent(farmerId, k -> new ArrayList<>()).add(sales);
        }

        BigDecimal grandTotal = farmerEntities.stream()
                .map(Sales::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commission = RoundOffUtil.round(grandTotal.multiply(new BigDecimal("0.10")));

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            int slotNo = clientSlotSequenceDao.nextSlotNumber(clientId, clientUsername, conn);
            String saleSlotId = clientUsername + "Slot" + slotNo;

            for (Sales sales : buyerEntities) {
                sales.setSaleSlotId(saleSlotId);
            }
            for (Sales sales : farmerEntities) {
                sales.setSaleSlotId(saleSlotId);
            }

            List<Sales> savedBuyerSales = salesDao.saveBatch(buyerEntities, conn);

            for (Map.Entry<String, List<Sales>> entry : buyerRowsByBuyerId.entrySet()) {
                List<Sales> buyerRows = entry.getValue();
                BigDecimal rowSum = buyerRows.stream()
                        .map(Sales::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BuyerLedger buyerLedger = new BuyerLedger();
                buyerLedger.setClientId(clientId);
                buyerLedger.setClientUsername(clientUsername);
                buyerLedger.setBuyerId(entry.getKey());
                buyerLedger.setBuyerName(buyerRows.get(0).getCustName());
                buyerLedger.setSalesDate(date);
                String buyerSalesIds = buyerRows.stream()
                        .map(s -> String.valueOf(s.getSalesId()))
                        .collect(Collectors.joining(","));
                buyerLedger.setSalesIds(buyerSalesIds);

                boolean isDirect = isDirectPayment(buyerRows.get(0).getCustName());
                if (isDirect) {
                    buyerLedger.setCreditAmt(rowSum);
                    buyerLedger.setDebitAmt(BigDecimal.ZERO);
                } else {
                    buyerLedger.setCreditAmt(BigDecimal.ZERO);
                    buyerLedger.setDebitAmt(rowSum);
                }
                buyerLedgerDao.insert(buyerLedger, conn);
                ledgerSettlementService.settleBuyerIfClosed(clientId, entry.getKey(), date, conn);
                logger.info("saveFlowerLootSale: buyer ledger inserted for buyerId={}, date={}, debit={}, credit={}",
                        entry.getKey(), date, buyerLedger.getDebitAmt(), buyerLedger.getCreditAmt());
            }

            List<Sales> savedFarmerSales = salesDao.saveBatch(farmerEntities, conn);

            for (Map.Entry<String, List<Sales>> entry : farmerRowsByFarmerId.entrySet()) {
                List<Sales> farmerRows = entry.getValue();
                String farmerName = farmerRows.get(0).getFarmerName();
                BigDecimal farmerTotal = farmerRows.stream()
                        .map(Sales::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal farmerCredit;
                BigDecimal farmerCommission;
                if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                    farmerCommission = RoundOffUtil.round(
                            farmerTotal.multiply(commission).divide(grandTotal, 2, RoundingMode.HALF_UP));
                    farmerCredit = RoundOffUtil.round(farmerTotal.subtract(farmerCommission));
                } else {
                    farmerCommission = BigDecimal.ZERO;
                    farmerCredit = farmerTotal;
                }

                FarmerLedger farmerLedger = new FarmerLedger();
                farmerLedger.setClientId(clientId);
                farmerLedger.setClientUsername(clientUsername);
                farmerLedger.setFarmerId(entry.getKey());
                farmerLedger.setFarmerName(farmerName);
                farmerLedger.setSalesDate(date);
                farmerLedger.setCreditAmt(farmerCredit);
                farmerLedger.setDebitAmt(BigDecimal.ZERO);
                String farmerSalesIds = farmerRows.stream()
                        .map(s -> String.valueOf(s.getSalesId()))
                        .collect(Collectors.joining(","));
                farmerLedger.setSalesIds(farmerSalesIds);
                farmerLedgerDao.insert(farmerLedger, conn);
                ledgerSettlementService.settleFarmerIfClosed(clientId, entry.getKey(), date, conn);

                SalesTotalSummary summary = new SalesTotalSummary();
                summary.setClientId(clientId);
                summary.setClientUsername(clientUsername);
                summary.setFarmerId(entry.getKey());
                summary.setFarmerName(farmerName);
                summary.setSalesDate(date);
                summary.setTotalSalesAmt(farmerTotal);
                summary.setCommissionAmt(farmerCommission);
                summary.setTotalNetAmt(farmerCredit);
                summary.setDebitAmt(BigDecimal.ZERO);
                summary.setFinalAmt(farmerCredit);
                salesTotalSummaryDao.upsert(summary, conn);
                logger.info("saveFlowerLootSale: farmer ledger + summary for farmerId={}, farmerTotal={}, commission={}, credit={}",
                        entry.getKey(), farmerTotal, farmerCommission, farmerCredit);
            }

            conn.commit();
            logger.info("saveFlowerLootSale: committed, slotId={}, buyerSales={}, farmerSales={}",
                    saleSlotId, savedBuyerSales.size(), savedFarmerSales.size());
            return savedFarmerSales;

        } catch (Exception e) {
            logger.error("saveFlowerLootSale: transaction failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception ex) {
                    logger.error("saveFlowerLootSale: rollback failed", ex);
                }
            }
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to save flower loot sale", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception ex) {
                    logger.error("saveFlowerLootSale: close failed", ex);
                }
            }
        }
    }

    private Sales buildSales(Long clientId, String clientUsername, LocalDate date,
                             FlowerLootSaleDto dto, FlowerLootSaleDto.FlowerLootRowDto row) {
        BigDecimal weight = new BigDecimal(row.getQty().trim());
        BigDecimal rate = new BigDecimal(row.getRate().trim());
        BigDecimal amount = RoundOffUtil.round(new BigDecimal(row.getAmount().trim()));

        String flowerId = flowerMasterDao.findIdByNameAndClientId(clientId, dto.getFlowerName().trim());

        Sales sales = new Sales();
        sales.setClientId(clientId);
        sales.setClientUsername(clientUsername);
        sales.setSalesDate(date);
        sales.setFlowerType(dto.getFlowerName().trim());
        sales.setFlowerId(flowerId);
        sales.setBagCount(row.getBag() == null || row.getBag().trim().isEmpty()
                ? null : Integer.parseInt(row.getBag().trim()));
        sales.setTotalWeight(weight);
        sales.setPerKgRate(rate);
        sales.setPrice(amount);
        sales.setDebitCreditFlag("C");
        return sales;
    }

    private void validateBuyerRow(FlowerLootSaleDto.FlowerLootRowDto row) {
        if (isBlank(row.getName())) {
            throw new IllegalArgumentException("Buyer name is required in each Buyer Purchase row");
        }
        if (isBlank(row.getQty()) || !SalesUtil.isDecimal(row.getQty())) {
            throw new IllegalArgumentException("Qty must be a valid decimal number in each Buyer Purchase row");
        }
        if (isBlank(row.getRate()) || !SalesUtil.isDecimal(row.getRate())) {
            throw new IllegalArgumentException("Rate must be a valid decimal number in each Buyer Purchase row");
        }
        validateAmount(row);
    }

    private void validateFarmerRow(FlowerLootSaleDto.FlowerLootRowDto row) {
        if (isBlank(row.getName())) {
            throw new IllegalArgumentException("Farmer name is required in each Farmer Sale row");
        }
        if (isBlank(row.getQty()) || !SalesUtil.isDecimal(row.getQty())) {
            throw new IllegalArgumentException("Qty must be a valid decimal number in each Farmer Sale row");
        }
        if (isBlank(row.getRate()) || !SalesUtil.isDecimal(row.getRate())) {
            throw new IllegalArgumentException(
                    "Rate must be finalized via the average rate calculation in each Farmer Sale row");
        }
        validateAmount(row);
    }

    private void validateAmount(FlowerLootSaleDto.FlowerLootRowDto row) {
        if (isBlank(row.getAmount()) || !SalesUtil.isDecimal(row.getAmount())) {
            throw new IllegalArgumentException("Amount must be a valid decimal number in each row");
        }
        if (new BigDecimal(row.getAmount().trim()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero in each row");
        }
    }

    private boolean isDirectPayment(String customerName) {
        if (customerName == null) {
            return false;
        }
        String lower = customerName.trim().toLowerCase(Locale.ROOT);
        if (DIRECT_PAYMENT_NAMES.contains(lower)) {
            return true;
        }
        return lower.contains("cash") || lower.contains("upi") || lower.contains("google pay")
                || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm")
                || lower.contains("online") || lower.contains("card payment")
                || lower.contains("neft") || lower.contains("rtgs") || lower.contains("imps");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}