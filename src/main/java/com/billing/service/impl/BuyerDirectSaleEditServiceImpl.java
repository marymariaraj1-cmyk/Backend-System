package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.BuyerMasterDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.IntakeDirectSaleDao;
import com.billing.dao.SalesEditDao;
import com.billing.dto.BuyerDirectSaleDto;
import com.billing.dto.BuyerDirectSaleLineDto;
import com.billing.entity.BuyerLedger;
import com.billing.service.BuyerDirectSaleEditService;
import com.billing.service.LedgerSettlementService;
import com.billing.util.RoundOffUtil;
import com.billing.util.SalesRecordType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Buyer Direct Sale Edit. Fetch returns only Buyer Direct rows (marker + no farmer).
 * Edits adjust the buyer ledger by the difference; deletes subtract the row amount and drop
 * its SALES_ID, then running balances are recalculated (settleBuyerIfClosed).
 * Farmer ledger and BLOOMBUDDY_SALES_TOTALSUMMARY are never touched.
 */
@Service
public class BuyerDirectSaleEditServiceImpl implements BuyerDirectSaleEditService {

    private static final Logger logger = LoggerFactory.getLogger(BuyerDirectSaleEditServiceImpl.class);

    private static final Set<String> DIRECT_PAYMENT_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
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
    private final BuyerMasterDao buyerMasterDao;
    private final FlowerMasterDao flowerMasterDao;
    private final IntakeDirectSaleDao intakeDirectSaleDao;
    private final SalesEditDao salesEditDao;
    private final BuyerLedgerDao buyerLedgerDao;
    private final LedgerSettlementService ledgerSettlementService;

    @Autowired
    public BuyerDirectSaleEditServiceImpl(DataSource dataSource,
                                          BuyerMasterDao buyerMasterDao,
                                          FlowerMasterDao flowerMasterDao,
                                          IntakeDirectSaleDao intakeDirectSaleDao,
                                          SalesEditDao salesEditDao,
                                          BuyerLedgerDao buyerLedgerDao,
                                          LedgerSettlementService ledgerSettlementService) {
        this.dataSource = dataSource;
        this.buyerMasterDao = buyerMasterDao;
        this.flowerMasterDao = flowerMasterDao;
        this.intakeDirectSaleDao = intakeDirectSaleDao;
        this.salesEditDao = salesEditDao;
        this.buyerLedgerDao = buyerLedgerDao;
        this.ledgerSettlementService = ledgerSettlementService;
    }

    @Override
    public Map<String, Object> fetch(String buyerName, String salesDate, Long clientId) {
        logger.info("fetch: buyer={}, date={}, clientId={}", buyerName, salesDate, clientId);
        if (buyerName == null || buyerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Buyer name is required");
        }
        if (salesDate == null || salesDate.trim().isEmpty()) {
            throw new IllegalArgumentException("Sales date is required");
        }
        String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, buyerName.trim());
        if (buyerId == null) {
            throw new IllegalArgumentException("Buyer '" + buyerName + "' not found in Buyer Master.");
        }
        LocalDate date = SalesUtil.parseDate(salesDate);
        List<Map<String, Object>> rows = intakeDirectSaleDao.findBuyerDirectRows(clientId, buyerId, date);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buyerId", buyerId);
        result.put("buyerName", buyerName.trim());
        result.put("salesDate", SalesUtil.formatDate(date));
        result.put("rows", rows);
        return result;
    }

    @Override
    public Map<String, Object> saveEdits(BuyerDirectSaleDto request, Long clientId, String clientUsername) {
        logger.info("saveEdits: buyer={}, date={}, rows={}",
                request == null ? null : request.getBuyerName(),
                request == null ? null : request.getSalesDate(),
                request == null || request.getRows() == null ? 0 : request.getRows().size());
        if (request == null) {
            throw new IllegalArgumentException("Invalid request");
        }
        if (request.getBuyerName() == null || request.getBuyerName().trim().isEmpty()) {
            throw new IllegalArgumentException("Buyer name is required");
        }
        if (request.getSalesDate() == null || request.getSalesDate().trim().isEmpty()) {
            throw new IllegalArgumentException("Sales date is required");
        }
        List<BuyerDirectSaleLineDto> editedRows = request.getRows() == null ? new ArrayList<>() : request.getRows();
        if (editedRows.isEmpty()) {
            throw new IllegalArgumentException("No edits to save");
        }
        String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, request.getBuyerName().trim());
        if (buyerId == null) {
            throw new IllegalArgumentException("Buyer '" + request.getBuyerName() + "' not found in Buyer Master.");
        }
        LocalDate date = SalesUtil.parseDate(request.getSalesDate());
        Set<String> flowerSet = flowerMasterDao.findNamesByClientId(clientId).stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        boolean isDirect = isDirectPayment(request.getBuyerName().trim());

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            String buyerActive = buyerLedgerDao.findLedgerActive(clientId, buyerId, date, conn);
            if (buyerActive == null || !"Y".equals(buyerActive)) {
                throw new IllegalArgumentException("This sales date belongs to a settled ledger period and cannot be edited.");
            }

            for (BuyerDirectSaleLineDto row : editedRows) {
                if (row.getSalesId() == null) {
                    throw new IllegalArgumentException("Sales ID is required for each edited row");
                }
                Map<String, Object> existing = salesEditDao.findSalesRow(clientId, row.getSalesId());
                if (existing == null) {
                    throw new IllegalArgumentException("Sales record not found for SALES_ID=" + row.getSalesId());
                }
                String rowFarmerId = existing.get("farmerId") != null ? String.valueOf(existing.get("farmerId")) : null;
                String rowFarmerName = existing.get("farmerName") != null ? String.valueOf(existing.get("farmerName")) : null;
                String rowBuyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
                if (!SalesRecordType.isBuyerDirectSale(rowFarmerName, rowFarmerId, rowBuyerId)) {
                    throw new IllegalArgumentException("SALES_ID=" + row.getSalesId() + " is not a Buyer Direct Sale row.");
                }

                if (row.getFlowerType() == null || row.getFlowerType().trim().isEmpty()) {
                    throw new IllegalArgumentException("Flower type is required");
                }
                if (!flowerSet.contains(row.getFlowerType().trim().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Flower '" + row.getFlowerType() + "' not found in Flower Master. Please add it there first.");
                }
                if (row.getAmount() == null || row.getAmount().trim().isEmpty()
                        || !SalesUtil.isDecimal(row.getAmount())
                        || new BigDecimal(row.getAmount().trim()).compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Amount must be greater than zero");
                }

                BigDecimal oldAmount = existing.get("price") != null
                        ? new BigDecimal(String.valueOf(existing.get("price"))) : BigDecimal.ZERO;
                BigDecimal newAmount = RoundOffUtil.round(new BigDecimal(row.getAmount().trim()));
                BigDecimal delta = newAmount.subtract(oldAmount);

                String flowerId = flowerMasterDao.findIdByNameAndClientId(clientId, row.getFlowerType().trim());
                BigDecimal weight = BigDecimal.ZERO;
                BigDecimal rate = BigDecimal.ZERO;
                if (row.getTotalWeight() != null && !row.getTotalWeight().trim().isEmpty()) {
                    weight = new BigDecimal(row.getTotalWeight().trim());
                }
                if (row.getRate() != null && !row.getRate().trim().isEmpty()) {
                    rate = new BigDecimal(row.getRate().trim());
                }
                if ((rate == null || rate.compareTo(BigDecimal.ZERO) == 0) && weight.compareTo(BigDecimal.ZERO) > 0 && newAmount.compareTo(BigDecimal.ZERO) > 0) {
                    rate = RoundOffUtil.round(newAmount.divide(weight, 10, java.math.RoundingMode.HALF_UP));
                } else if (rate == null) {
                    rate = BigDecimal.ZERO;
                }

                intakeDirectSaleDao.updateSalesRow(row.getSalesId(), clientId, row.getFlowerType().trim(),
                        weight, rate, newAmount, flowerId, row.getBagCount(), conn);

                if (delta.compareTo(BigDecimal.ZERO) != 0) {
                    BuyerLedger ledger = new BuyerLedger();
                    ledger.setClientId(clientId);
                    ledger.setClientUsername(clientUsername);
                    ledger.setBuyerId(buyerId);
                    ledger.setBuyerName(existing.get("customerName") != null
                            ? String.valueOf(existing.get("customerName")) : request.getBuyerName().trim());
                    ledger.setSalesDate(date);
                    ledger.setSalesIds("");   // delta-only; do not re-add an existing id
                    if (isDirect) {
                        ledger.setCreditAmt(delta);
                        ledger.setDebitAmt(BigDecimal.ZERO);
                    } else {
                        ledger.setDebitAmt(delta);
                        ledger.setCreditAmt(BigDecimal.ZERO);
                    }
                    ledger.setDisAmt(BigDecimal.ZERO);
                    buyerLedgerDao.insert(ledger, conn);
                }
            }

            ledgerSettlementService.settleBuyerIfClosed(clientId, buyerId, date, conn);

            conn.commit();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("editedRows", editedRows.size());
            return result;
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    logger.error("saveEdits: rollback failed", rb);
                }
            }
            logger.error("saveEdits: failed, rolling back", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e.getMessage(), e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public Map<String, Object> delete(Long salesId, Long clientId, String clientUsername) {
        logger.info("delete: salesId={}, clientId={}", salesId, clientId);
        if (salesId == null) {
            throw new IllegalArgumentException("Sales ID is required");
        }
        Map<String, Object> existing = salesEditDao.findSalesRow(clientId, salesId);
        if (existing == null) {
            throw new IllegalArgumentException("Sales record not found for SALES_ID=" + salesId);
        }
        String farmerId = existing.get("farmerId") != null ? String.valueOf(existing.get("farmerId")) : null;
        String farmerName = existing.get("farmerName") != null ? String.valueOf(existing.get("farmerName")) : null;
        String buyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
        String customerName = existing.get("customerName") != null ? String.valueOf(existing.get("customerName")) : null;
        if (!SalesRecordType.isBuyerDirectSale(farmerName, farmerId, buyerId)) {
            throw new IllegalArgumentException("SALES_ID=" + salesId + " is not a Buyer Direct Sale row.");
        }
        LocalDate salesDate = (LocalDate) existing.get("salesDate");
        BigDecimal price = existing.get("price") != null
                ? new BigDecimal(String.valueOf(existing.get("price"))) : BigDecimal.ZERO;

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            String buyerActive = buyerLedgerDao.findLedgerActive(clientId, buyerId, salesDate, conn);
            if (buyerActive == null || !"Y".equals(buyerActive)) {
                throw new IllegalArgumentException("This sales date belongs to a settled ledger period and cannot be deleted.");
            }

            // Subtract the row amount from the buyer ledger and remove its SALES_ID.
            boolean isDirect = isDirectPayment(customerName);
            if (isDirect) {
                buyerLedgerDao.decreaseCreditAmt(clientId, buyerId, salesDate, price, conn);
            } else {
                buyerLedgerDao.decreaseDebitAmt(clientId, buyerId, salesDate, price, conn);
            }
            BuyerLedger row = buyerLedgerDao.findRow(clientId, buyerId, salesDate, conn);
            String existingSalesIds = row != null ? row.getSalesIds() : null;
            buyerLedgerDao.updateSalesIds(clientId, buyerId, salesDate, removeSalesId(existingSalesIds, salesId), conn);
            buyerLedgerDao.deleteRowIfZero(clientId, buyerId, salesDate, conn);

            salesEditDao.deleteSalesRow(salesId, clientId, conn);

            ledgerSettlementService.settleBuyerIfClosed(clientId, buyerId, salesDate, conn);

            conn.commit();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deletedSalesId", salesId);
            return result;
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    logger.error("delete: rollback failed", rb);
                }
            }
            logger.error("delete: failed, rolling back", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e.getMessage(), e);
        } finally {
            closeQuietly(conn);
        }
    }

    private boolean isDirectPayment(String buyerName) {
        if (buyerName == null) return false;
        String lower = buyerName.trim().toLowerCase(Locale.ROOT);
        if (DIRECT_PAYMENT_NAMES.contains(lower)) return true;
        if (lower.contains("cash") || lower.contains("upi") || lower.contains("google pay")
                || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm")
                || lower.contains("online") || lower.contains("card payment")
                || lower.contains("neft") || lower.contains("rtgs") || lower.contains("imps")) {
            return true;
        }
        return false;
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

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (Exception c) {
                logger.error("closeQuietly: close failed", c);
            }
        }
    }
}
