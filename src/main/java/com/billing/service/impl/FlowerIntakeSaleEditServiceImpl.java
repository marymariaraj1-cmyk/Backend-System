package com.billing.service.impl;

import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerMasterDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.IntakeDirectSaleDao;
import com.billing.dao.SalesEditDao;
import com.billing.dao.SalesTotalSummaryDao;
import com.billing.dto.SalesEditRequestDto;
import com.billing.dto.SalesEditRowDto;
import com.billing.entity.FarmerLedger;
import com.billing.service.FarmerCommissionConfigService;
import com.billing.service.FlowerIntakeSaleEditService;
import com.billing.service.LedgerSettlementService;
import com.billing.util.RoundOffUtil;
import com.billing.util.SalesRecordType;
import com.billing.util.SalesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Flower Intake Sale Edit. Fetch returns only Flower Intake rows (marker + no buyer);
 * after any edit/delete the farmer aggregate is recalculated from ALL of the farmer's
 * rows of the day. The buyer ledger is never touched.
 */
@Service
public class FlowerIntakeSaleEditServiceImpl implements FlowerIntakeSaleEditService {

    private static final Logger logger = LoggerFactory.getLogger(FlowerIntakeSaleEditServiceImpl.class);

    private final DataSource dataSource;
    private final FarmerMasterDao farmerMasterDao;
    private final FlowerMasterDao flowerMasterDao;
    private final IntakeDirectSaleDao intakeDirectSaleDao;
    private final SalesEditDao salesEditDao;
    private final FarmerLedgerDao farmerLedgerDao;
    private final SalesTotalSummaryDao salesTotalSummaryDao;
    private final LedgerSettlementService ledgerSettlementService;
    private final FarmerCommissionConfigService commissionConfigService;

    @Autowired
    public FlowerIntakeSaleEditServiceImpl(DataSource dataSource,
                                           FarmerMasterDao farmerMasterDao,
                                           FlowerMasterDao flowerMasterDao,
                                           IntakeDirectSaleDao intakeDirectSaleDao,
                                           SalesEditDao salesEditDao,
                                           FarmerLedgerDao farmerLedgerDao,
                                           SalesTotalSummaryDao salesTotalSummaryDao,
                                           LedgerSettlementService ledgerSettlementService,
                                           FarmerCommissionConfigService commissionConfigService) {
        this.dataSource = dataSource;
        this.farmerMasterDao = farmerMasterDao;
        this.flowerMasterDao = flowerMasterDao;
        this.intakeDirectSaleDao = intakeDirectSaleDao;
        this.salesEditDao = salesEditDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.salesTotalSummaryDao = salesTotalSummaryDao;
        this.ledgerSettlementService = ledgerSettlementService;
        this.commissionConfigService = commissionConfigService;
    }

    @Override
    public Map<String, Object> fetch(String farmerName, String salesDate, Long clientId) {
        logger.info("fetch: farmer={}, date={}, clientId={}", farmerName, salesDate, clientId);
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
        List<Map<String, Object>> rows = intakeDirectSaleDao.findFlowerIntakeRows(clientId, farmerId, date);
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
        logger.info("saveEdits: farmer={}, date={}, rows={}",
                request == null ? null : request.getFarmerName(),
                request == null ? null : request.getSalesDate(),
                request == null || request.getRows() == null ? 0 : request.getRows().size());
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
        if (editedRows.isEmpty()) {
            throw new IllegalArgumentException("No edits to save");
        }
        String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, request.getFarmerName().trim());
        if (farmerId == null) {
            throw new IllegalArgumentException("Farmer name '" + request.getFarmerName() + "' not found in Farmer Master.");
        }
        LocalDate date = SalesUtil.parseDate(request.getSalesDate());
        Set<String> flowerSet = flowerMasterDao.findNamesByClientId(clientId).stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            String farmerActive = farmerLedgerDao.findLedgerActive(clientId, farmerId, date, conn);
            if (farmerActive == null || !"Y".equals(farmerActive)) {
                throw new IllegalArgumentException("This sales date belongs to a settled ledger period and cannot be edited.");
            }

            for (SalesEditRowDto row : editedRows) {
                if (row.getSalesId() == null) {
                    throw new IllegalArgumentException("Sales ID is required for each edited row");
                }
                Map<String, Object> existing = salesEditDao.findSalesRow(clientId, row.getSalesId());
                if (existing == null) {
                    throw new IllegalArgumentException("Sales record not found for SALES_ID=" + row.getSalesId());
                }
                // Only Flower Intake rows may be edited here.
                String rowFarmerId = existing.get("farmerId") != null ? String.valueOf(existing.get("farmerId")) : null;
                String rowFarmerName = existing.get("farmerName") != null ? String.valueOf(existing.get("farmerName")) : null;
                String rowBuyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
                String rowCustName = existing.get("customerName") != null ? String.valueOf(existing.get("customerName")) : null;
                if (!SalesRecordType.isFlowerIntakeSale(rowCustName, rowBuyerId, rowFarmerId)) {
                    throw new IllegalArgumentException("SALES_ID=" + row.getSalesId() + " is not a Flower Intake Sale row.");
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

                String flowerId = flowerMasterDao.findIdByNameAndClientId(clientId, row.getFlowerType().trim());
                BigDecimal weight = BigDecimal.ZERO;
                BigDecimal rate = BigDecimal.ZERO;
                if (row.getTotalWeight() != null && !row.getTotalWeight().trim().isEmpty()) {
                    weight = new BigDecimal(row.getTotalWeight().trim());
                }
                // Accept price or rate from DTO (and try reflection in case JSON maps differently)
                if (row.getPrice() != null && !row.getPrice().trim().isEmpty()) {
                    rate = new BigDecimal(row.getPrice().trim());
                } else {
                    try {
                        Method m = row.getClass().getMethod("getRate");
                        Object v = m.invoke(row);
                        if (v != null) {
                            String s = String.valueOf(v).trim();
                            if (!s.isEmpty()) {
                                rate = new BigDecimal(s);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                BigDecimal amount = RoundOffUtil.round(new BigDecimal(row.getAmount().trim()));
                if ((rate == null || rate.compareTo(BigDecimal.ZERO) == 0) && weight.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(BigDecimal.ZERO) > 0) {
                    rate = RoundOffUtil.round(amount.divide(weight, 10, RoundingMode.HALF_UP));
                } else if (rate == null) {
                    rate = BigDecimal.ZERO;
                }

                intakeDirectSaleDao.updateSalesRow(row.getSalesId(), clientId, row.getFlowerType().trim(),
                        weight, rate, amount, flowerId, row.getBagCount(), conn);
            }

            recalcFarmerAggregate(clientId, clientUsername, farmerId, request.getFarmerName().trim(), date, conn);

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
        String farmerName = existing.get("farmerName") != null ? String.valueOf(existing.get("farmerName")) : "";
        String buyerId = existing.get("buyerId") != null ? String.valueOf(existing.get("buyerId")) : null;
        String custName = existing.get("customerName") != null ? String.valueOf(existing.get("customerName")) : null;
        if (!SalesRecordType.isFlowerIntakeSale(custName, buyerId, farmerId)) {
            throw new IllegalArgumentException("SALES_ID=" + salesId + " is not a Flower Intake Sale row.");
        }
        LocalDate salesDate = (LocalDate) existing.get("salesDate");

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            String farmerActive = farmerLedgerDao.findLedgerActive(clientId, farmerId, salesDate, conn);
            if (farmerActive == null || !"Y".equals(farmerActive)) {
                throw new IllegalArgumentException("This sales date belongs to a settled ledger period and cannot be deleted.");
            }

            salesEditDao.deleteSalesRow(salesId, clientId, conn);

            // Drop this sales id from the ledger's SALES_IDS before the aggregate recalc rewrites it.
            FarmerLedger farmerLedger = farmerLedgerDao.findRow(clientId, farmerId, salesDate, conn);
            if (farmerLedger != null) {
                farmerLedgerDao.updateSalesIds(clientId, farmerId, salesDate,
                        removeSalesId(farmerLedger.getSalesIds(), salesId), conn);
            }

            recalcFarmerAggregate(clientId, clientUsername, farmerId, farmerName, salesDate, conn);

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

    /**
     * Recompute the farmer aggregate from ALL rows of the day. When none remain, remove the
     * summary row and clear/delete the ledger entry (as Flower Loot Sale Edit does).
     * Debit is preserved from the existing summary when present.
     */
    private void recalcFarmerAggregate(Long clientId, String clientUsername, String farmerId, String farmerName,
                                       LocalDate date, Connection conn) {
        List<Map<String, Object>> dayRows = intakeDirectSaleDao.findFarmerDayRowsAll(clientId, farmerId, date, conn);
        BigDecimal total = BigDecimal.ZERO;
        List<String> salesIds = new ArrayList<>();
        String storedName = farmerName;
        for (Map<String, Object> row : dayRows) {
            total = total.add(toBigDecimal(row.get("price")));
            salesIds.add(String.valueOf(row.get("salesId")));
            Object name = row.get("farmerName");
            if (name != null && !String.valueOf(name).trim().isEmpty()) {
                storedName = String.valueOf(name);
            }
        }

        if (dayRows.isEmpty()) {
            BigDecimal debit = currentDebit(clientId, farmerId, date);
            if (debit.compareTo(BigDecimal.ZERO) > 0) {
                // A Farmer Transaction debit remains for this date; keep it instead of deleting it.
                salesEditDao.reconcileSummary(clientId, clientUsername, farmerId, farmerName, date,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, debit,
                        RoundOffUtil.round(debit.negate()), conn);
                FarmerLedger ledgerRow = farmerLedgerDao.findRow(clientId, farmerId, date, conn);
                if (ledgerRow != null) {
                    farmerLedgerDao.setCreditAmt(clientId, clientUsername, farmerId, farmerName, date,
                            RoundOffUtil.round(debit.negate()), conn);
                    farmerLedgerDao.updateSalesIds(clientId, farmerId, date, null, conn);
                }
                ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, date, conn);
                logger.info("recalcFarmerAggregate: no remaining rows for farmerId={}, date={}; kept debit-only summary debit={}",
                        farmerId, date, debit);
                return;
            }
            salesTotalSummaryDao.deleteRow(clientId, farmerId, date, conn);
            FarmerLedger ledgerRow = farmerLedgerDao.findRow(clientId, farmerId, date, conn);
            if (ledgerRow != null) {
                farmerLedgerDao.setCreditAmt(clientId, clientUsername, farmerId, farmerName, date, BigDecimal.ZERO, conn);
                farmerLedgerDao.updateSalesIds(clientId, farmerId, date, null, conn);
                farmerLedgerDao.deleteRowIfZero(clientId, farmerId, date, conn);
            }
            ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, date, conn);
            logger.info("recalcFarmerAggregate: no remaining rows for farmerId={}, date={}; summary removed, ledger cleared", farmerId, date);
            return;
        }

        total = RoundOffUtil.round(total);
        BigDecimal commission = RoundOffUtil.round(
                total.multiply(commissionConfigService.getCommissionPercent(clientId).movePointLeft(2)));
        BigDecimal net = RoundOffUtil.round(total.subtract(commission));

        BigDecimal debit = currentDebit(clientId, farmerId, date);
        BigDecimal finalAmt = RoundOffUtil.round(net.subtract(debit));

        salesEditDao.reconcileSummary(clientId, clientUsername, farmerId, storedName,
                date, total, commission, net, debit, finalAmt, conn);

        FarmerLedger ledgerRow = farmerLedgerDao.findRow(clientId, farmerId, date, conn);
        if (ledgerRow != null) {
            farmerLedgerDao.setCreditAmt(clientId, clientUsername, farmerId, storedName, date, finalAmt, conn);
        } else {
            FarmerLedger freshLedger = new FarmerLedger();
            freshLedger.setClientId(clientId);
            freshLedger.setClientUsername(clientUsername);
            freshLedger.setFarmerId(farmerId);
            freshLedger.setFarmerName(storedName);
            freshLedger.setSalesDate(date);
            freshLedger.setDebitAmt(BigDecimal.ZERO);
            freshLedger.setCreditAmt(finalAmt);
            farmerLedgerDao.insert(freshLedger, conn);
        }
        farmerLedgerDao.updateSalesIds(clientId, farmerId, date, String.join(",", salesIds), conn);

        ledgerSettlementService.settleFarmerIfClosed(clientId, farmerId, date, conn);

        logger.info("recalcFarmerAggregate: farmerId={}, date={}, rows={}, total={}, commission={}, net={}, debit={}, final={}",
                farmerId, date, dayRows.size(), total, commission, net, debit, finalAmt);
    }

    private BigDecimal currentDebit(Long clientId, String farmerId, LocalDate date) {
        Map<String, Object> summaryRow = salesEditDao.fetchSummary(clientId, farmerId, date);
        if (summaryRow != null && summaryRow.get("debitAmt") != null) {
            return RoundOffUtil.round(toBigDecimal(summaryRow.get("debitAmt")));
        }
        return BigDecimal.ZERO;
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

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(String.valueOf(value));
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
