package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerMasterDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.SalesEditDao;
import com.billing.dao.SalesTotalSummaryDao;
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
                                SalesTotalSummaryDao salesTotalSummaryDao,
                                SalesEditDao salesEditDao,
                                LedgerSettlementService ledgerSettlementService,
                                BagCountConfigService bagCountConfigService) {
        this.dataSource = dataSource;
        this.farmerMasterDao = farmerMasterDao;
        this.flowerMasterDao = flowerMasterDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.buyerLedgerDao = buyerLedgerDao;
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

                String flowerId = flowerMasterDao.findIdByNameAndClientId(clientId, row.getFlowerType().trim());
                Integer bagCount = row.getBagCount();

                Integer oldBag = existing.get("bagCount") != null ? (Integer) existing.get("bagCount") : null;
                boolean bagChanged = !java.util.Objects.equals(oldBag, bagCount);
                boolean flowerChanged = existing.get("flowerType") == null
                        || !String.valueOf(existing.get("flowerType")).trim().equalsIgnoreCase(row.getFlowerType().trim());

                if (delta.compareTo(BigDecimal.ZERO) == 0 && !bagChanged && !flowerChanged) {
                    logger.info("saveEdits: no change for salesId={}, skipping", row.getSalesId());
                    continue;
                }

                salesEditDao.updateSalesRow(row.getSalesId(), clientId, row.getFlowerType().trim(),
                        weight, rate, newAmount, flowerId, bagCount, conn);

                // Bag count validation for edits: account for the edited value replacing the old one
                if (bagCount != null && bagCount > 0 && flowerId != null) {
                    validateEditedBagCount(clientId, date, flowerId, row.getSalesId(), oldBag, bagCount);
                }

                totalRowDelta = totalRowDelta.add(delta);

                String buyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
                String customerName = existing.get("customerName") != null ? String.valueOf(existing.get("customerName")) : "";
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

    private void validateEditedBagCount(Long clientId, LocalDate date, String flowerId,
                                        Long salesId, Integer oldBag, Integer newBag) {
        Map<String, Object> config = bagCountConfigService.getConfig(clientId, flowerId, date);
        if (config == null) {
            return;
        }
        String bagCheck = config.get("bagCheck") == null ? "" : String.valueOf(config.get("bagCheck"));
        if (!"E".equalsIgnoreCase(bagCheck)) {
            return;
        }
        int configuredLimit = config.get("bagCount") == null ? 0 : ((Number) config.get("bagCount")).intValue();
        int dayTotal = bagCountConfigService.getSavedBagTotal(clientId, flowerId, date);
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
