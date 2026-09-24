package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.BuyerMasterDao;
import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerMasterDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.SalesEditDao;
import com.billing.dao.SalesTotalSummaryDao;
import com.billing.dto.SalesEditDeleteRequestDto;
import com.billing.dto.SalesEditRequestDto;
import com.billing.dto.SalesEditRowDto;
import com.billing.entity.BuyerLedger;
import com.billing.entity.FarmerLedger;
import com.billing.service.BagCountConfigService;
import com.billing.service.LedgerSettlementService;
import com.billing.service.SalesEditService;
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
public class SalesEditServiceImpl implements SalesEditService {

    private static final Logger logger = LoggerFactory.getLogger(SalesEditServiceImpl.class);

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
    private final FarmerLedgerDao farmerLedgerDao;
    private final BuyerLedgerDao buyerLedgerDao;
    private final BuyerMasterDao buyerMasterDao;
    private final SalesTotalSummaryDao salesTotalSummaryDao;
    private final SalesEditDao salesEditDao;
    private final LedgerSettlementService ledgerSettlementService;
    private final BagCountConfigService bagCountConfigService;

    @Autowired
    public SalesEditServiceImpl(DataSource dataSource,
                                FarmerMasterDao farmerMasterDao,
                                FlowerMasterDao flowerMasterDao,
                                FarmerLedgerDao farmerLedgerDao,
                                BuyerLedgerDao buyerLedgerDao,
                                BuyerMasterDao buyerMasterDao,
                                SalesTotalSummaryDao salesTotalSummaryDao,
                                SalesEditDao salesEditDao,
                                LedgerSettlementService ledgerSettlementService,
                                BagCountConfigService bagCountConfigService) {
        this.dataSource = dataSource;
        this.farmerMasterDao = farmerMasterDao;
        this.flowerMasterDao = flowerMasterDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.buyerLedgerDao = buyerLedgerDao;
        this.buyerMasterDao = buyerMasterDao;
        this.salesTotalSummaryDao = salesTotalSummaryDao;
        this.salesEditDao = salesEditDao;
        this.ledgerSettlementService = ledgerSettlementService;
        this.bagCountConfigService = bagCountConfigService;
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
    public Map<String, Object> fetchSalesData(String farmerName, String salesDate, Long clientId) {
        logger.info("fetchSalesData: farmer={}, date={}, clientId={}", farmerName, salesDate, clientId);

        if (farmerName == null || farmerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Farmer name is required");
        }
        if (salesDate == null || salesDate.trim().isEmpty()) {
            throw new IllegalArgumentException("Sales date is required");
        }

        String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, farmerName.trim());
        if (farmerId == null) {
            throw new IllegalArgumentException("Farmer name '" + farmerName + "' not found in Farmer Master.");
        }

        LocalDate date = SalesUtil.parseDate(salesDate);

        List<Map<String, Object>> rows = salesEditDao.fetchSales(clientId, farmerId, date);
        Map<String, Object> summary = salesEditDao.fetchSummary(clientId, farmerId, date);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("farmerId", farmerId);
        result.put("farmerName", farmerName.trim());
        result.put("salesDate", SalesUtil.formatDate(date));
        result.put("rows", rows);
        result.put("summary", summary);
        return result;
    }

    @Override
    public Map<String, Object> saveEdits(SalesEditRequestDto request, Long clientId, String clientUsername) {
        logger.info("saveEdits: farmer={}, date={}, rows={}, clientId={}",
                request == null ? null : request.getFarmerName(),
                request == null ? null : request.getSalesDate(),
                request == null || request.getRows() == null ? 0 : request.getRows().size(),
                clientId);

        if (request == null) {
            throw new IllegalArgumentException("Invalid request");
        }
        if (request.getFarmerName() == null || request.getFarmerName().trim().isEmpty()) {
            throw new IllegalArgumentException("Farmer name is required");
        }
        if (request.getSalesDate() == null || request.getSalesDate().trim().isEmpty()) {
            throw new IllegalArgumentException("Sales date is required");
        }

        List<SalesEditRowDto> editedRows = request.getRows() == null ? new ArrayList<>() : request.getRows();
        if (editedRows.isEmpty() && !request.isDebitEdited()) {
            throw new IllegalArgumentException("No edits to save");
        }

        String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, request.getFarmerName().trim());
        if (farmerId == null) {
            throw new IllegalArgumentException("Farmer name '" + request.getFarmerName() + "' not found in Farmer Master.");
        }

        LocalDate date = SalesUtil.parseDate(request.getSalesDate());

        Set<String> flowerSet = flowerMasterDao.findNamesByClientId(clientId).stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        BigDecimal totalRowDelta = BigDecimal.ZERO;
        BigDecimal submittedTotal = BigDecimal.ZERO;
        Map<String, BuyerDelta> buyerDeltas = new HashMap<>();

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            String farmerActive = farmerLedgerDao.findLedgerActive(clientId, farmerId, date, conn);
            if (farmerActive == null || !"Y".equals(farmerActive)) {
                throw new IllegalArgumentException(
                        "This sales date belongs to a settled ledger period and cannot be edited.");
            }

            Set<String> checkedBuyers = new HashSet<>();

            for (SalesEditRowDto row : editedRows) {
                validateRow(row, flowerSet);

                BigDecimal newAmount = RoundOffUtil.round(new BigDecimal(row.getAmount().trim()));
                submittedTotal = submittedTotal.add(newAmount);

                BigDecimal weight = BigDecimal.ZERO;
                if (row.getTotalWeight() != null && !row.getTotalWeight().trim().isEmpty()) {
                    weight = new BigDecimal(row.getTotalWeight().trim());
                }
                BigDecimal rate = BigDecimal.ZERO;
                if (row.getPrice() != null && !row.getPrice().trim().isEmpty()) {
                    rate = new BigDecimal(row.getPrice().trim());
                }

                Map<String, Object> existing = salesEditDao.findSalesRow(clientId, row.getSalesId());
                if (existing == null) {
                    throw new IllegalArgumentException("Sales record not found for SALES_ID=" + row.getSalesId());
                }

                String rowBuyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
                if (rowBuyerId != null && !rowBuyerId.isEmpty() && checkedBuyers.add(rowBuyerId)) {
                    String buyerActive = buyerLedgerDao.findLedgerActive(clientId, rowBuyerId, date, conn);
                    if (buyerActive == null || !"Y".equals(buyerActive)) {
                        throw new IllegalArgumentException(
                                "This sales date belongs to a settled ledger period for the buyer and cannot be edited.");
                    }
                }

                BigDecimal oldAmount = existing.get("price") != null
                        ? new BigDecimal(String.valueOf(existing.get("price"))) : BigDecimal.ZERO;
                BigDecimal delta = newAmount.subtract(oldAmount);

                String oldCustomerName = existing.get("customerName") != null
                        ? String.valueOf(existing.get("customerName")) : "";
                String newCustomer = row.getCustomerName();
                boolean customerChanged = newCustomer != null && !newCustomer.trim().isEmpty()
                        && !newCustomer.trim().equalsIgnoreCase(oldCustomerName);

                String flowerId = flowerMasterDao.findIdByNameAndClientId(clientId, row.getFlowerType().trim());
                Integer bagCount = row.getBagCount();

                Integer oldBag = existing.get("bagCount") != null ? (Integer) existing.get("bagCount") : null;
                boolean bagChanged = !java.util.Objects.equals(oldBag, bagCount);
                boolean flowerChanged = existing.get("flowerType") == null
                        || !String.valueOf(existing.get("flowerType")).trim().equalsIgnoreCase(row.getFlowerType().trim());

                if (delta.compareTo(BigDecimal.ZERO) == 0 && !bagChanged && !flowerChanged && !customerChanged) {
                    logger.info("saveEdits: no change for salesId={}, skipping", row.getSalesId());
                    continue;
                }

                salesEditDao.updateSalesRow(row.getSalesId(), clientId, row.getFlowerType().trim(),
                        weight, rate, newAmount, flowerId, bagCount, conn);

                // Bag count validation for edits: account for the edited value replacing the old one
                if (bagCount != null && bagCount > 0 && flowerId != null) {
                    String existingFarmerId = existing.get("farmerId") != null
                            ? String.valueOf(existing.get("farmerId")) : null;
                    validateEditedBagCount(clientId, date, existingFarmerId, flowerId, oldBag, bagCount);
                }

                totalRowDelta = totalRowDelta.add(delta);

                if (customerChanged) {
                    String newBuyerId = buyerMasterDao.findIdByNameAndClientId(clientId, newCustomer.trim());
                    if (newBuyerId == null) {
                        throw new IllegalArgumentException(
                                "Buyer '" + newCustomer.trim() + "' not found in Buyer Master. Please add it there first.");
                    }
                    if (checkedBuyers.add(newBuyerId)) {
                        String newBuyerActive = buyerLedgerDao.findLedgerActive(clientId, newBuyerId, date, conn);
                        if (newBuyerActive == null || !"Y".equals(newBuyerActive)) {
                            throw new IllegalArgumentException(
                                    "This sales date belongs to a settled ledger period for the buyer and cannot be edited.");
                        }
                    }

                    salesEditDao.updateSalesRowCustomerName(row.getSalesId(), clientId, newCustomer.trim(), newBuyerId, conn);

                    if (rowBuyerId != null && !rowBuyerId.isEmpty()) {
                        reverseBuyerLedger(clientId, clientUsername, rowBuyerId, oldCustomerName,
                                date, oldAmount, row.getSalesId(), conn);
                        ledgerSettlementService.settleBuyerIfClosed(clientId, rowBuyerId, date, conn);
                    }
                    applyBuyerLedger(clientId, clientUsername, newBuyerId, newCustomer.trim(),
                            date, newAmount, row.getSalesId(), conn);
                    ledgerSettlementService.settleBuyerIfClosed(clientId, newBuyerId, date, conn);
                } else {
                    String buyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
                    String customerName = oldCustomerName;
                    if (buyerId != null && !buyerId.isEmpty()) {
                        boolean isDirect = isDirectPayment(customerName);
                        BuyerDelta bd = buyerDeltas.computeIfAbsent(buyerId, k -> new BuyerDelta(customerName));
                        if (isDirect) {
                            bd.creditDelta = bd.creditDelta.add(delta);
                        } else {
                            bd.debitDelta = bd.debitDelta.add(delta);
                        }
                    }
                }
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
            }

            boolean debitEdited = request.isDebitEdited();
            boolean rowsSubmitted = !editedRows.isEmpty();
            FarmerLedger ledgerRow = farmerLedgerDao.findRow(clientId, farmerId, date, conn);

            if (rowsSubmitted) {
                Map<String, Object> summaryRow = salesEditDao.fetchSummary(clientId, farmerId, date);
                BigDecimal oldDebit = BigDecimal.ZERO;
                if (summaryRow != null) {
                    if (summaryRow.get("debitAmt") != null) {
                        oldDebit = new BigDecimal(String.valueOf(summaryRow.get("debitAmt")));
                    }
                }

                BigDecimal newTotal = submittedTotal;
                BigDecimal newCommission = RoundOffUtil.round(newTotal.multiply(TEN_PERCENT));
                BigDecimal newNet = newTotal.subtract(newCommission);
                BigDecimal newDebit = debitEdited ? parseDebitAmount(request.getDebitAmount()) : oldDebit;
                BigDecimal newFinal = newNet.subtract(newDebit);

                salesEditDao.reconcileSummary(clientId, clientUsername, farmerId,
                        request.getFarmerName().trim(), date, newTotal, newCommission,
                        newNet, newDebit, newFinal, conn);

                if (ledgerRow != null) {
                    farmerLedgerDao.setCreditAmt(clientId, clientUsername, farmerId,
                            request.getFarmerName().trim(), date, newFinal, conn);
                } else {
                    FarmerLedger freshLedger = new FarmerLedger();
                    freshLedger.setClientId(clientId);
                    freshLedger.setClientUsername(clientUsername);
                    freshLedger.setFarmerId(farmerId);
                    freshLedger.setFarmerName(request.getFarmerName().trim());
                    freshLedger.setSalesDate(date);
                    freshLedger.setDebitAmt(BigDecimal.ZERO);
                    freshLedger.setCreditAmt(newFinal);
                    farmerLedgerDao.insert(freshLedger, conn);
                }
            } else if (debitEdited) {
                applyDebitAdjustment(request.getDebitAmount(), clientId, clientUsername,
                        farmerId, request.getFarmerName().trim(), date, conn);
            }

            ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, date, conn);

            conn.commit();
            logger.info("saveEdits: committed successfully for farmerId={}, date={}", farmerId, date);

            Map<String, Object> result = new HashMap<>();
            result.put("updatedRows", editedRows.size());
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
            throw new RuntimeException("Failed to save sales edits", e);
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

        String farmerId = (String) existing.get("farmerId");
        if (farmerId == null || farmerId.isEmpty()) {
            throw new IllegalArgumentException("Sales record has no farmer reference for SALES_ID=" + salesId);
        }
        String farmerName = existing.get("farmerName") != null ? String.valueOf(existing.get("farmerName")) : "";
        LocalDate salesDate = (LocalDate) existing.get("salesDate");
        BigDecimal price = existing.get("price") != null
                ? new BigDecimal(String.valueOf(existing.get("price"))) : BigDecimal.ZERO;
        String buyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
        String customerName = existing.get("customerName") != null
                ? String.valueOf(existing.get("customerName")) : "";

        // Pre-fetch the remaining rows for the farmer+date BEFORE deleting, because fetchSales
        // opens its own connection and would not see rows deleted inside this transaction.
        List<Map<String, Object>> allRows = salesEditDao.fetchSales(clientId, farmerId, salesDate);
        List<Map<String, Object>> remainingRows = new ArrayList<>();
        BigDecimal recomputedTotal = BigDecimal.ZERO;
        for (Map<String, Object> rowMap : allRows) {
            Long rowId = rowMap.get("salesId") != null
                    ? Long.valueOf(String.valueOf(rowMap.get("salesId"))) : null;
            if (rowId != null && rowId.equals(salesId)) {
                continue;
            }
            remainingRows.add(rowMap);
            BigDecimal rowPrice = rowMap.get("price") != null
                    ? new BigDecimal(String.valueOf(rowMap.get("price"))) : BigDecimal.ZERO;
            recomputedTotal = recomputedTotal.add(rowPrice);
        }

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            String farmerActive = farmerLedgerDao.findLedgerActive(clientId, farmerId, salesDate, conn);
            if (farmerActive == null || !"Y".equals(farmerActive)) {
                throw new IllegalArgumentException(
                        "This sales date belongs to a settled ledger period and cannot be deleted.");
            }

            if (buyerId != null && !buyerId.isEmpty()) {
                String buyerActive = buyerLedgerDao.findLedgerActive(clientId, buyerId, salesDate, conn);
                if (buyerActive == null || !"Y".equals(buyerActive)) {
                    throw new IllegalArgumentException(
                            "This sales date belongs to a settled ledger period for the buyer and cannot be deleted.");
                }

                reverseBuyerLedger(clientId, clientUsername, buyerId, customerName,
                        salesDate, price, salesId, conn);
                ledgerSettlementService.settleBuyerIfClosed(clientId, buyerId, salesDate, conn);
            }

            salesEditDao.deleteSalesRow(salesId, clientId, conn);

            // Remove this sales id from the farmer's day-level SALES_IDS reference column
            FarmerLedger farmerLedger = farmerLedgerDao.findRow(clientId, farmerId, salesDate, conn);
            if (farmerLedger != null) {
                farmerLedgerDao.updateSalesIds(clientId, farmerId, salesDate,
                        removeSalesId(farmerLedger.getSalesIds(), salesId), conn);
            }

            if (remainingRows.isEmpty()) {
                // No remaining entries for this farmer+date -> remove the summary row entirely
                salesTotalSummaryDao.deleteRow(clientId, farmerId, salesDate, conn);
                if (farmerLedger != null) {
                    farmerLedgerDao.setCreditAmt(clientId, clientUsername, farmerId, farmerName,
                            salesDate, BigDecimal.ZERO, conn);
                    farmerLedgerDao.deleteRowIfZero(clientId, farmerId, salesDate, conn);
                }
            } else {
                BigDecimal newCommission = RoundOffUtil.round(recomputedTotal.multiply(TEN_PERCENT));
                BigDecimal newNet = recomputedTotal.subtract(newCommission);
                Map<String, Object> summaryRow = salesEditDao.fetchSummary(clientId, farmerId, salesDate);
                BigDecimal newDebit = BigDecimal.ZERO;
                if (summaryRow != null && summaryRow.get("debitAmt") != null) {
                    newDebit = new BigDecimal(String.valueOf(summaryRow.get("debitAmt")));
                }
                BigDecimal newFinal = newNet.subtract(newDebit);

                salesEditDao.reconcileSummary(clientId, clientUsername, farmerId, farmerName,
                        salesDate, recomputedTotal, newCommission, newNet, newDebit, newFinal, conn);

                if (farmerLedger != null) {
                    farmerLedgerDao.setCreditAmt(clientId, clientUsername, farmerId, farmerName,
                            salesDate, newFinal, conn);
                } else {
                    FarmerLedger freshLedger = new FarmerLedger();
                    freshLedger.setClientId(clientId);
                    freshLedger.setClientUsername(clientUsername);
                    freshLedger.setFarmerId(farmerId);
                    freshLedger.setFarmerName(farmerName);
                    freshLedger.setSalesDate(salesDate);
                    freshLedger.setDebitAmt(BigDecimal.ZERO);
                    freshLedger.setCreditAmt(newFinal);
                    farmerLedgerDao.insert(freshLedger, conn);
                }
            }

            ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, salesDate, conn);

            conn.commit();
            logger.info("deleteSalesEntry: committed successfully for salesId={}", salesId);

            Map<String, Object> result = new HashMap<>();
            result.put("deletedSalesId", salesId);
            result.put("remainingRows", remainingRows.size());
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
            throw new RuntimeException("Failed to delete sales entry", e);
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

    private void applyDebitAdjustment(String debitAmountStr, Long clientId, String clientUsername,
                                       String farmerId, String farmerName, LocalDate date, Connection conn) {
        String farmerActive = farmerLedgerDao.findLedgerActive(clientId, farmerId, date, conn);
        if (farmerActive == null || !"Y".equals(farmerActive)) {
            throw new IllegalArgumentException(
                    "This sales date belongs to a settled ledger period and cannot be edited.");
        }

        BigDecimal newDebit = parseDebitAmount(debitAmountStr);

        Map<String, Object> summaryRow = salesEditDao.fetchSummary(clientId, farmerId, date);
        BigDecimal oldDebit = BigDecimal.ZERO;
        if (summaryRow != null && summaryRow.get("debitAmt") != null) {
            oldDebit = new BigDecimal(String.valueOf(summaryRow.get("debitAmt")));
        }
        BigDecimal debitDelta = newDebit.subtract(oldDebit);
        if (debitDelta.compareTo(BigDecimal.ZERO) == 0) {
            logger.info("applyDebitAdjustment: debit unchanged, skipping");
            return;
        }

        BigDecimal creditChange = debitDelta.negate();

        FarmerLedger debitTarget = farmerLedgerDao.findRow(clientId, farmerId, date, conn);
        if (debitTarget != null) {
            farmerLedgerDao.adjustCreditAmt(clientId, farmerId, date, creditChange, conn);
        } else {
            FarmerLedger debitFallback = farmerLedgerDao.findLatestBefore(clientId, farmerId, date, conn);
            if (debitFallback != null) {
                farmerLedgerDao.adjustCreditAmt(clientId, farmerId, debitFallback.getSalesDate(), creditChange, conn);
            } else {
                logger.warn("applyDebitAdjustment: no farmer ledger entry for farmerId={}; inserting fresh ledger row with negative credit", farmerId);
                FarmerLedger freshLedger = new FarmerLedger();
                freshLedger.setClientId(clientId);
                freshLedger.setClientUsername(clientUsername);
                freshLedger.setFarmerId(farmerId);
                freshLedger.setFarmerName(farmerName);
                freshLedger.setSalesDate(date);
                freshLedger.setDebitAmt(BigDecimal.ZERO);
                freshLedger.setCreditAmt(creditChange);
                farmerLedgerDao.insert(freshLedger, conn);
            }
        }

        LocalDate summaryDate = null;
        if (summaryRow != null) {
            summaryDate = date;
        } else {
            Map<String, Object> summaryFallback = salesTotalSummaryDao.findLatestBefore(clientId, farmerId, date);
            if (summaryFallback != null) {
                summaryDate = SalesUtil.parseDate(String.valueOf(summaryFallback.get("salesDate")));
            } else {
                logger.warn("applyDebitAdjustment: no sales total summary row for farmerId={}; skipping summary debit adjustment", farmerId);
            }
        }
        if (summaryDate != null) {
            salesTotalSummaryDao.adjustDebit(clientId, farmerId, summaryDate, debitDelta, conn);
        }
    }

    private void validateEditedBagCount(Long clientId, LocalDate date, String farmerId,
                                        String flowerId, Integer oldBag, Integer newBag) {
        if (farmerId == null || farmerId.trim().isEmpty()) {
            return;
        }
        Map<String, Object> config = bagCountConfigService.getConfig(clientId, farmerId, flowerId, date);
        if (config == null) {
            return;
        }
        Object limitObj = config.get("bagCount");
        if (limitObj == null || ((Number) limitObj).intValue() <= 0) {
            return;
        }
        int configuredLimit = ((Number) limitObj).intValue();
        int dayTotal = bagCountConfigService.getSavedBagTotal(clientId, farmerId, flowerId, date);
        int adjustedTotal = dayTotal - (oldBag == null ? 0 : oldBag) + (newBag == null ? 0 : newBag);
        if (adjustedTotal > configuredLimit) {
            String flowerName = config.get("flowerName") == null ? flowerId : String.valueOf(config.get("flowerName"));
            throw new IllegalArgumentException(
                    "Bag count exceeded for flower '" + flowerName + "'. Allowed " + configuredLimit
                            + " but total is " + adjustedTotal + ". Please increase the bag count in the configuration.");
        }
    }

    private void validateRow(SalesEditRowDto row, Set<String> flowerSet) {
        if (row.getSalesId() == null) {
            throw new IllegalArgumentException("Sales ID is required for edit");
        }
        if (row.getFlowerType() == null || row.getFlowerType().trim().isEmpty()) {
            throw new IllegalArgumentException("Flower type is required");
        }
        if (!flowerSet.contains(row.getFlowerType().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Flower '" + row.getFlowerType() + "' not found in Flower Master.");
        }
        if (row.getTotalWeight() != null && !row.getTotalWeight().trim().isEmpty()
                && !SalesUtil.isDecimal(row.getTotalWeight())) {
            throw new IllegalArgumentException("Total Weight must be a valid decimal number");
        }
        if (row.getPrice() != null && !row.getPrice().trim().isEmpty()
                && !SalesUtil.isDecimal(row.getPrice())) {
            throw new IllegalArgumentException("Rate must be a valid decimal number");
        }
        if (row.getAmount() == null || row.getAmount().trim().isEmpty()) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (!SalesUtil.isDecimal(row.getAmount())) {
            throw new IllegalArgumentException("Amount must be a valid decimal number");
        }
        if (new BigDecimal(row.getAmount().trim()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
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

    private BigDecimal parseDebitAmount(String value) {        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (!SalesUtil.isDecimal(value.trim())) {
            throw new IllegalArgumentException("Debit Amount must be a valid decimal number");
        }
        BigDecimal amount = RoundOffUtil.round(new BigDecimal(value.trim()));
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Debit Amount cannot be negative");
        }
        return amount;
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
