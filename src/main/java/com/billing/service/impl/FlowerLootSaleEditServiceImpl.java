package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.BuyerMasterDao;
import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerMasterDao;
import com.billing.dao.FlowerLootSaleEditDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.SalesEditDao;
import com.billing.dao.SalesTotalSummaryDao;
import com.billing.dto.FlowerLootSaleEditRequestDto;
import com.billing.dto.FlowerLootSaleEditRowDto;
import com.billing.entity.BuyerLedger;
import com.billing.entity.FarmerLedger;
import com.billing.service.FlowerLootSaleEditService;
import com.billing.service.LedgerSettlementService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FlowerLootSaleEditServiceImpl implements FlowerLootSaleEditService {

    private static final Logger logger = LoggerFactory.getLogger(FlowerLootSaleEditServiceImpl.class);

    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");

    private static final Set<String> DIRECT_PAYMENT_NAMES = new HashSet<>(java.util.Arrays.asList(
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
    private final FarmerMasterDao farmerMasterDao;
    private final FlowerMasterDao flowerMasterDao;
    private final BuyerMasterDao buyerMasterDao;
    private final FarmerLedgerDao farmerLedgerDao;
    private final BuyerLedgerDao buyerLedgerDao;
    private final SalesEditDao salesEditDao;
    private final SalesTotalSummaryDao salesTotalSummaryDao;
    private final FlowerLootSaleEditDao flowerLootSaleEditDao;
    private final LedgerSettlementService ledgerSettlementService;

    @Autowired
    public FlowerLootSaleEditServiceImpl(DataSource dataSource,
                                         FarmerMasterDao farmerMasterDao,
                                         FlowerMasterDao flowerMasterDao,
                                         BuyerMasterDao buyerMasterDao,
                                         FarmerLedgerDao farmerLedgerDao,
                                         BuyerLedgerDao buyerLedgerDao,
                                         SalesEditDao salesEditDao,
                                         SalesTotalSummaryDao salesTotalSummaryDao,
                                         FlowerLootSaleEditDao flowerLootSaleEditDao,
                                         LedgerSettlementService ledgerSettlementService) {
        this.dataSource = dataSource;
        this.farmerMasterDao = farmerMasterDao;
        this.flowerMasterDao = flowerMasterDao;
        this.buyerMasterDao = buyerMasterDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.buyerLedgerDao = buyerLedgerDao;
        this.salesEditDao = salesEditDao;
        this.salesTotalSummaryDao = salesTotalSummaryDao;
        this.flowerLootSaleEditDao = flowerLootSaleEditDao;
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
    public Map<String, Object> fetchFlowerLootSaleData(String flowerName, String salesDate, Long clientId) {
        logger.info("fetchFlowerLootSaleData: flower={}, date={}, clientId={}", flowerName, salesDate, clientId);

        if (isBlank(flowerName)) {
            throw new IllegalArgumentException("Flower name is required");
        }
        if (isBlank(salesDate)) {
            throw new IllegalArgumentException("Sales date is required");
        }

        String flowerId = flowerMasterDao.findIdByNameAndClientId(clientId, flowerName.trim());
        if (flowerId == null) {
            throw new IllegalArgumentException("Flower '" + flowerName.trim() + "' not found in Flower Master.");
        }

        LocalDate date = SalesUtil.parseDate(salesDate);

        List<Map<String, Object>> rows = flowerLootSaleEditDao.fetchFlowerLootSaleRows(clientId, flowerName.trim(), date);

        List<Map<String, Object>> buyerRows = new ArrayList<>();
        List<Map<String, Object>> farmerRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> dtoRow = new LinkedHashMap<>();
            dtoRow.put("salesId", row.get("salesId"));
            dtoRow.put("bag", row.get("bagCount") == null ? null : String.valueOf(row.get("bagCount")));
            dtoRow.put("qty", row.get("totalWeight"));
            dtoRow.put("rate", row.get("perKgRate"));
            dtoRow.put("amount", row.get("price"));
            Object farmerId = row.get("farmerId");
            if (farmerId == null || String.valueOf(farmerId).trim().isEmpty()) {
                dtoRow.put("name", row.get("customerName"));
                buyerRows.add(dtoRow);
            } else {
                dtoRow.put("name", row.get("farmerName"));
                farmerRows.add(dtoRow);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flowerName", flowerName.trim());
        result.put("salesDate", SalesUtil.formatDate(date));
        result.put("buyerRows", buyerRows);
        result.put("farmerRows", farmerRows);
        logger.info("fetchFlowerLootSaleData: buyerRows={}, farmerRows={}", buyerRows.size(), farmerRows.size());
        return result;
    }

    @Override
    public Map<String, Object> saveEdits(FlowerLootSaleEditRequestDto request, Long clientId, String clientUsername) {
        logger.info("saveEdits: flower={}, date={}, buyerRows={}, farmerRows={}, clientId={}",
                request == null ? null : request.getFlowerName(),
                request == null ? null : request.getSalesDate(),
                request == null || request.getBuyerRows() == null ? 0 : request.getBuyerRows().size(),
                request == null || request.getFarmerRows() == null ? 0 : request.getFarmerRows().size(),
                clientId);

        if (request == null) {
            throw new IllegalArgumentException("Invalid request");
        }
        if (isBlank(request.getFlowerName())) {
            throw new IllegalArgumentException("Flower name is required");
        }
        if (isBlank(request.getSalesDate())) {
            throw new IllegalArgumentException("Sales date is required");
        }

        List<FlowerLootSaleEditRowDto> buyerRows = request.getBuyerRows() == null
                ? new ArrayList<>() : request.getBuyerRows();
        List<FlowerLootSaleEditRowDto> farmerRows = request.getFarmerRows() == null
                ? new ArrayList<>() : request.getFarmerRows();
        if (buyerRows.isEmpty() && farmerRows.isEmpty()) {
            throw new IllegalArgumentException("No edits to save");
        }

        String flowerId = flowerMasterDao.findIdByNameAndClientId(clientId, request.getFlowerName().trim());
        if (flowerId == null) {
            throw new IllegalArgumentException("Flower '" + request.getFlowerName().trim() + "' not found in Flower Master.");
        }

        LocalDate date = SalesUtil.parseDate(request.getSalesDate());
        String flowerType = request.getFlowerName().trim();

        Set<String> buyerSet = buyerMasterDao.findNamesByClientId(clientId).stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> farmerSet = farmerMasterDao.findNamesByClientId(clientId).stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        int buyerChanges = 0;
        int farmerChanges = 0;
        Map<String, BuyerDelta> buyerDeltas = new HashMap<>();
        Map<String, String> affectedFarmers = new LinkedHashMap<>();

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            Set<String> checkedBuyers = new HashSet<>();
            Set<String> checkedFarmers = new HashSet<>();

            for (FlowerLootSaleEditRowDto row : buyerRows) {
                validateEditRow(row, buyerSet, "Buyer", "Buyer");

                Long salesId = row.getSalesId();
                Map<String, Object> existing = salesEditDao.findSalesRow(clientId, salesId);
                if (existing == null) {
                    throw new IllegalArgumentException("Sales record not found for SALES_ID=" + salesId);
                }
                if (!isFlowerLootBuyerRow(existing)) {
                    throw new IllegalArgumentException("Sales record SALES_ID=" + salesId + " is not a Flower Loot Sale buyer row");
                }

                LocalDate rowDate = (LocalDate) existing.get("salesDate");
                if (!date.equals(rowDate)) {
                    throw new IllegalArgumentException("Sales record SALES_ID=" + salesId + " belongs to a different sales date");
                }

                String buyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
                if (buyerId != null && !buyerId.isEmpty() && checkedBuyers.add(buyerId)) {
                    checkBuyerLedgerActive(clientId, buyerId, date, conn);
                }

                BigDecimal oldAmount = toBigDecimal(existing.get("price"));
                BigDecimal newAmount = parseAmount(row.getAmount());
                BigDecimal delta = newAmount.subtract(oldAmount);

                String oldCustomerName = existing.get("customerName") != null
                        ? String.valueOf(existing.get("customerName")) : "";
                String newCustomer = row.getName().trim();
                boolean customerChanged = !newCustomer.equalsIgnoreCase(oldCustomerName);

                if (delta.compareTo(BigDecimal.ZERO) == 0 && !customerChanged) {
                    logger.info("saveEdits: no change for buyer salesId={}, skipping", salesId);
                    continue;
                }

                BigDecimal weight = parseDecimal(row.getQty());
                BigDecimal rate = parseDecimal(row.getRate());
                salesEditDao.updateSalesRow(salesId, clientId, flowerType,
                        weight, rate, newAmount,
                        existing.get("flowerId") != null ? String.valueOf(existing.get("flowerId")) : null,
                        existing.get("bagCount") != null ? (Integer) existing.get("bagCount") : null,
                        conn);

                if (customerChanged) {
                    String newBuyerId = buyerMasterDao.findIdByNameAndClientId(clientId, newCustomer);
                    if (newBuyerId == null) {
                        throw new IllegalArgumentException(
                                "Buyer '" + newCustomer + "' not found in Buyer Master. Please add it there first.");
                    }
                    if (checkedBuyers.add(newBuyerId)) {
                        checkBuyerLedgerActive(clientId, newBuyerId, date, conn);
                    }

                    salesEditDao.updateSalesRowCustomerName(salesId, clientId, newCustomer, newBuyerId, conn);

                    if (buyerId != null && !buyerId.isEmpty()) {
                        reverseBuyerLedger(clientId, clientUsername, buyerId, oldCustomerName,
                                date, oldAmount, salesId, conn);
                        ledgerSettlementService.settleBuyerIfClosed(clientId, buyerId, date, conn);
                    }
                    applyBuyerLedger(clientId, clientUsername, newBuyerId, newCustomer,
                            date, newAmount, salesId, conn);
                    ledgerSettlementService.settleBuyerIfClosed(clientId, newBuyerId, date, conn);
                } else {
                    boolean isDirect = isDirectPayment(oldCustomerName);
                    BuyerDelta bd = buyerDeltas.computeIfAbsent(buyerId, k -> new BuyerDelta(oldCustomerName));
                    if (isDirect) {
                        bd.creditDelta = bd.creditDelta.add(delta);
                    } else {
                        bd.debitDelta = bd.debitDelta.add(delta);
                    }
                    logger.info("saveEdits: buyer ledger delta for buyerId={}, debitDelta={}, creditDelta={}",
                            buyerId, bd.debitDelta, bd.creditDelta);
                }

                buyerChanges++;
                logger.info("saveEdits: FLS buyer row updated salesId={}, oldAmount={}, newAmount={}, oldBuyer={}, newBuyer={}",
                        salesId, oldAmount, newAmount, oldCustomerName, customerChanged ? newCustomer : oldCustomerName);
            }

            for (Map.Entry<String, BuyerDelta> entry : buyerDeltas.entrySet()) {
                BuyerDelta bd = entry.getValue();
                BuyerLedger buyerLedger = new BuyerLedger();
                buyerLedger.setClientId(clientId);
                buyerLedger.setClientUsername(clientUsername);
                buyerLedger.setBuyerId(entry.getKey());
                buyerLedger.setBuyerName(bd.buyerName);
                buyerLedger.setSalesDate(date);
                buyerLedger.setDebitAmt(bd.debitDelta);
                buyerLedger.setCreditAmt(bd.creditDelta);
                buyerLedger.setDisAmt(BigDecimal.ZERO);
                buyerLedgerDao.insert(buyerLedger, conn);
                ledgerSettlementService.settleBuyerIfClosed(clientId, entry.getKey(), date, conn);
                logger.info("saveEdits: buyer delta applied for buyerId={}, debitDelta={}, creditDelta={}",
                        entry.getKey(), bd.debitDelta, bd.creditDelta);
            }

            for (FlowerLootSaleEditRowDto row : farmerRows) {
                validateEditRow(row, farmerSet, "Farmer", "Farmer");

                Long salesId = row.getSalesId();
                Map<String, Object> existing = salesEditDao.findSalesRow(clientId, salesId);
                if (existing == null) {
                    throw new IllegalArgumentException("Sales record not found for SALES_ID=" + salesId);
                }
                if (!isFlowerLootFarmerRow(existing)) {
                    throw new IllegalArgumentException("Sales record SALES_ID=" + salesId + " is not a Flower Loot Sale farmer row");
                }

                LocalDate rowDate = (LocalDate) existing.get("salesDate");
                if (!date.equals(rowDate)) {
                    throw new IllegalArgumentException("Sales record SALES_ID=" + salesId + " belongs to a different sales date");
                }

                String oldFarmerId = existing.get("farmerId") != null ? String.valueOf(existing.get("farmerId")) : null;
                String oldFarmerName = existing.get("farmerName") != null ? String.valueOf(existing.get("farmerName")) : "";
                if (oldFarmerId != null && !oldFarmerId.isEmpty() && checkedFarmers.add(oldFarmerId)) {
                    checkFarmerLedgerActive(clientId, oldFarmerId, date, conn);
                }

                BigDecimal oldAmount = toBigDecimal(existing.get("price"));
                BigDecimal newAmount = parseAmount(row.getAmount());
                BigDecimal delta = newAmount.subtract(oldAmount);

                String newFarmer = row.getName().trim();
                boolean farmerChanged = !newFarmer.equalsIgnoreCase(oldFarmerName);
                String newFarmerId = null;
                if (farmerChanged) {
                    newFarmerId = farmerMasterDao.findIdByNameAndClientId(clientId, newFarmer);
                    if (newFarmerId == null) {
                        throw new IllegalArgumentException(
                                "Farmer '" + newFarmer + "' not found in Farmer Master. Please add it there first.");
                    }
                    if (checkedFarmers.add(newFarmerId)) {
                        checkFarmerLedgerActive(clientId, newFarmerId, date, conn);
                    }
                }

                if (delta.compareTo(BigDecimal.ZERO) == 0 && !farmerChanged) {
                    logger.info("saveEdits: no change for farmer salesId={}, skipping", salesId);
                    continue;
                }

                BigDecimal weight = parseDecimal(row.getQty());
                BigDecimal rate = parseDecimal(row.getRate());
                salesEditDao.updateSalesRow(salesId, clientId, flowerType,
                        weight, rate, newAmount,
                        existing.get("flowerId") != null ? String.valueOf(existing.get("flowerId")) : null,
                        existing.get("bagCount") != null ? (Integer) existing.get("bagCount") : null,
                        conn);

                if (farmerChanged) {
                    flowerLootSaleEditDao.updateSalesRowFarmerName(salesId, clientId, newFarmer, newFarmerId, conn);
                    if (oldFarmerId != null && !oldFarmerId.isEmpty()) {
                        affectedFarmers.put(oldFarmerId, oldFarmerName);
                    }
                    affectedFarmers.put(newFarmerId, newFarmer);
                } else {
                    if (oldFarmerId != null && !oldFarmerId.isEmpty()) {
                        affectedFarmers.put(oldFarmerId, oldFarmerName);
                    }
                }

                farmerChanges++;
                logger.info("saveEdits: FLS farmer row updated salesId={}, oldAmount={}, newAmount={}, oldFarmer={}, newFarmer={}",
                        salesId, oldAmount, newAmount, oldFarmerName, farmerChanged ? newFarmer : oldFarmerName);
            }

            for (Map.Entry<String, String> entry : affectedFarmers.entrySet()) {
                recalcFarmerAggregate(clientId, clientUsername, entry.getKey(), entry.getValue(), date, conn);
            }

            conn.commit();
            logger.info("saveEdits: committed successfully, flower={}, date={}, buyerChanges={}, farmerChanges={}",
                    flowerType, date, buyerChanges, farmerChanges);

            Map<String, Object> result = new HashMap<>();
            result.put("updatedBuyerRows", buyerChanges);
            result.put("updatedFarmerRows", farmerChanges);
            return result;

        } catch (Exception e) {
            logger.error("saveEdits: transaction failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("saveEdits: transaction rolled back");
                } catch (Exception rollbackEx) {
                    logger.error("saveEdits: rollback failed", rollbackEx);
                }
            }
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to save flower loot sale edits", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception closeEx) {
                    logger.error("saveEdits: error closing connection", closeEx);
                }
            }
        }
    }

    @Override
    public Map<String, Object> deleteSalesEntry(Long salesId, Long clientId, String clientUsername) {
        logger.info("deleteSalesEntry: salesId={}, clientId={}", salesId, clientId);
        if (salesId == null) {
            throw new IllegalArgumentException("Sales ID is required");
        }

        Map<String, Object> existing = salesEditDao.findSalesRow(clientId, salesId);
        if (existing == null) {
            throw new IllegalArgumentException("Sales record not found for SALES_ID=" + salesId);
        }

        String farmerId = existing.get("farmerId") != null ? String.valueOf(existing.get("farmerId")) : null;
        String farmerName = existing.get("farmerName") != null ? String.valueOf(existing.get("farmerName")) : "";
        LocalDate salesDate = (LocalDate) existing.get("salesDate");
        BigDecimal price = toBigDecimal(existing.get("price"));
        String buyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
        String customerName = existing.get("customerName") != null ? String.valueOf(existing.get("customerName")) : "";

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            if (farmerId == null || farmerId.isEmpty()) {
                // Flower Loot Sale buyer row: reverse the buyer ledger entry then delete
                if (buyerId == null || buyerId.isEmpty()) {
                    throw new IllegalArgumentException("Sales record SALES_ID=" + salesId + " has no buyer reference");
                }
                checkBuyerLedgerActive(clientId, buyerId, salesDate, conn);

                reverseBuyerLedger(clientId, clientUsername, buyerId, customerName,
                        salesDate, price, salesId, conn);
                ledgerSettlementService.settleBuyerIfClosed(clientId, buyerId, salesDate, conn);

                salesEditDao.deleteSalesRow(salesId, clientId, conn);
                logger.info("deleteSalesEntry: FLS buyer row deleted salesId={}, buyerId={}, amount={}", salesId, buyerId, price);
            } else {
                if (buyerId != null && !buyerId.isEmpty()) {
                    throw new IllegalArgumentException("Sales record SALES_ID=" + salesId
                            + " is not a Flower Loot Sale row. Use Sales Details Edit instead.");
                }
                checkFarmerLedgerActive(clientId, farmerId, salesDate, conn);

                salesEditDao.deleteSalesRow(salesId, clientId, conn);
                recalcFarmerAggregate(clientId, clientUsername, farmerId, farmerName, salesDate, conn);
                logger.info("deleteSalesEntry: FLS farmer row deleted salesId={}, farmerId={}, amount={}", salesId, farmerId, price);
            }

            conn.commit();
            logger.info("deleteSalesEntry: committed successfully for salesId={}", salesId);

            Map<String, Object> result = new HashMap<>();
            result.put("deletedSalesId", salesId);
            return result;

        } catch (Exception e) {
            logger.error("deleteSalesEntry: transaction failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.info("deleteSalesEntry: transaction rolled back");
                } catch (Exception rollbackEx) {
                    logger.error("deleteSalesEntry: rollback failed", rollbackEx);
                }
            }
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to delete flower loot sale entry", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception closeEx) {
                    logger.error("deleteSalesEntry: error closing connection", closeEx);
                }
            }
        }
    }

    private void recalcFarmerAggregate(Long clientId, String clientUsername, String farmerId,
                                       String farmerName, LocalDate date, Connection conn) {
        List<Map<String, Object>> dayRows = flowerLootSaleEditDao.findFarmerDayRows(clientId, farmerId, date, conn);
        BigDecimal total = BigDecimal.ZERO;
        List<String> salesIds = new ArrayList<>();
        for (Map<String, Object> row : dayRows) {
            total = total.add(toBigDecimal(row.get("price")));
            salesIds.add(String.valueOf(row.get("salesId")));
        }

        if (dayRows.isEmpty()) {
            salesTotalSummaryDao.deleteRow(clientId, farmerId, date, conn);
            FarmerLedger ledgerRow = farmerLedgerDao.findRow(clientId, farmerId, date, conn);
            if (ledgerRow != null) {
                farmerLedgerDao.setCreditAmt(clientId, clientUsername, farmerId, farmerName,
                        date, BigDecimal.ZERO, conn);
                farmerLedgerDao.updateSalesIds(clientId, farmerId, date, null, conn);
                farmerLedgerDao.deleteRowIfZero(clientId, farmerId, date, conn);
            }
            ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, date, conn);
            logger.info("recalcFarmerAggregate: no remaining FLS farmer rows for farmerId={}, date={}; summary removed, ledger zeroed",
                    farmerId, date);
            return;
        }

        String storedName = String.valueOf(dayRows.get(0).get("farmerName"));
        BigDecimal commission = RoundOffUtil.round(total.multiply(TEN_PERCENT));
        BigDecimal net = total.subtract(commission);

        salesEditDao.reconcileSummary(clientId, clientUsername, farmerId, storedName,
                date, total, commission, net, BigDecimal.ZERO, net, conn);

        FarmerLedger ledgerRow = farmerLedgerDao.findRow(clientId, farmerId, date, conn);
        if (ledgerRow != null) {
            farmerLedgerDao.setCreditAmt(clientId, clientUsername, farmerId, storedName, date, net, conn);
        } else {
            FarmerLedger freshLedger = new FarmerLedger();
            freshLedger.setClientId(clientId);
            freshLedger.setClientUsername(clientUsername);
            freshLedger.setFarmerId(farmerId);
            freshLedger.setFarmerName(storedName);
            freshLedger.setSalesDate(date);
            freshLedger.setDebitAmt(BigDecimal.ZERO);
            freshLedger.setCreditAmt(net);
            farmerLedgerDao.insert(freshLedger, conn);
        }
        farmerLedgerDao.updateSalesIds(clientId, farmerId, date, String.join(",", salesIds), conn);

        ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, date, conn);

        logger.info("recalcFarmerAggregate: farmerId={}, date={}, rows={}, total={}, commission={}, net={}",
                farmerId, date, dayRows.size(), total, commission, net);
    }

    private void reverseBuyerLedger(Long clientId, String clientUsername, String buyerId, String customerName,
                                    LocalDate date, BigDecimal amount, Long salesId, Connection conn) {
        boolean isDirect = isDirectPayment(customerName);
        if (isDirect) {
            buyerLedgerDao.decreaseCreditAmt(clientId, buyerId, date, amount, conn);
        } else {
            buyerLedgerDao.decreaseDebitAmt(clientId, buyerId, date, amount, conn);
        }
        BuyerLedger row = buyerLedgerDao.findRow(clientId, buyerId, date, conn);
        String existingSalesIds = row != null ? row.getSalesIds() : null;
        buyerLedgerDao.updateSalesIds(clientId, buyerId, date,
                removeSalesId(existingSalesIds, salesId), conn);
        buyerLedgerDao.deleteRowIfZero(clientId, buyerId, date, conn);
    }

    private void applyBuyerLedger(Long clientId, String clientUsername, String buyerId, String buyerName,
                                  LocalDate date, BigDecimal amount, Long salesId, Connection conn) {
        BuyerLedger ledger = new BuyerLedger();
        ledger.setClientId(clientId);
        ledger.setClientUsername(clientUsername);
        ledger.setBuyerId(buyerId);
        ledger.setBuyerName(buyerName);
        ledger.setSalesDate(date);
        boolean isDirect = isDirectPayment(buyerName);
        if (isDirect) {
            ledger.setCreditAmt(amount);
            ledger.setDebitAmt(BigDecimal.ZERO);
        } else {
            ledger.setDebitAmt(amount);
            ledger.setCreditAmt(BigDecimal.ZERO);
        }
        ledger.setDisAmt(BigDecimal.ZERO);
        ledger.setSalesIds(String.valueOf(salesId));
        buyerLedgerDao.insert(ledger, conn);
    }

    private String removeSalesId(String salesIds, Long salesId) {
        if (salesIds == null || salesIds.trim().isEmpty()) {
            return null;
        }
        String target = String.valueOf(salesId);
        List<String> kept = new ArrayList<>();
        for (String part : salesIds.split(",")) {
            String p = part.trim();
            if (!p.isEmpty() && !p.equals(target)) {
                kept.add(p);
            }
        }
        return kept.isEmpty() ? null : String.join(",", kept);
    }

    private void validateEditRow(FlowerLootSaleEditRowDto row, Set<String> validNames,
                                 String label, String masterLabel) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (row.getSalesId() == null) {
            throw new IllegalArgumentException(label + " Sales ID is required for edit");
        }
        if (isBlank(row.getName())) {
            throw new IllegalArgumentException("Enter a valid " + lower + " name");
        }
        if (!validNames.contains(row.getName().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    label + " '" + row.getName().trim() + "' not found in " + masterLabel + " Master.");
        }
        if (isBlank(row.getQty()) || !SalesUtil.isDecimal(row.getQty().trim())) {
            throw new IllegalArgumentException("Qty must be a valid decimal number in each " + lower + " row");
        }
        if (isBlank(row.getRate()) || !SalesUtil.isDecimal(row.getRate().trim())) {
            throw new IllegalArgumentException("Rate must be a valid decimal number in each " + lower + " row");
        }
        if (isBlank(row.getAmount()) || !SalesUtil.isDecimal(row.getAmount().trim())) {
            throw new IllegalArgumentException("Amount must be a valid decimal number in each " + lower + " row");
        }
        if (new BigDecimal(row.getAmount().trim()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero in each " + lower + " row");
        }
    }

    private void checkFarmerLedgerActive(Long clientId, String farmerId, LocalDate date, Connection conn) {
        String active = farmerLedgerDao.findLedgerActive(clientId, farmerId, date, conn);
        if (active == null || !"Y".equals(active)) {
            throw new IllegalArgumentException(
                    "This sales date belongs to a settled ledger period and cannot be edited.");
        }
    }

    private void checkBuyerLedgerActive(Long clientId, String buyerId, LocalDate date, Connection conn) {
        String active = buyerLedgerDao.findLedgerActive(clientId, buyerId, date, conn);
        if (active == null || !"Y".equals(active)) {
            throw new IllegalArgumentException(
                    "This sales date belongs to a settled ledger period for the buyer and cannot be edited.");
        }
    }

    private boolean isFlowerLootBuyerRow(Map<String, Object> existing) {
        Object farmerId = existing.get("farmerId");
        Object farmerName = existing.get("farmerName");
        return farmerId == null && farmerName == null;
    }

    private boolean isFlowerLootFarmerRow(Map<String, Object> existing) {
        Object buyerId = existing.get("buyerId");
        Object customerName = existing.get("customerName");
        return buyerId == null && customerName == null;
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

    private BigDecimal parseAmount(String value) {
        return RoundOffUtil.round(new BigDecimal(value.trim()));
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class BuyerDelta {
        private final String buyerName;
        private BigDecimal debitDelta = BigDecimal.ZERO;
        private BigDecimal creditDelta = BigDecimal.ZERO;

        BuyerDelta(String buyerName) {
            this.buyerName = buyerName;
        }
    }
}