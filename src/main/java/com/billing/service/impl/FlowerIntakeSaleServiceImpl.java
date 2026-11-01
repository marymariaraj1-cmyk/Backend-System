package com.billing.service.impl;

import com.billing.dao.BuyerMasterDao;
import com.billing.dao.ClientSlotSequenceDao;
import com.billing.dao.FarmerLedgerDao;
import com.billing.dao.FarmerMasterDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.IntakeDirectSaleDao;
import com.billing.dao.SalesDao;
import com.billing.dao.SalesEditDao;
import com.billing.dto.SalesLineDto;
import com.billing.dto.SalesRequestDto;
import com.billing.entity.FarmerLedger;
import com.billing.entity.Sales;
import com.billing.service.FarmerCommissionConfigService;
import com.billing.service.FlowerIntakeSaleService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flower Intake Sale (save). Farmer side only:
 *  - SALES rows written like Sales Entry but with CUST_NAME = marker and BUYER_ID NULL.
 *  - Farmer aggregate (ledger + BLOOMBUDDY_SALES_TOTALSUMMARY) recalculated from ALL of the
 *    farmer's rows for the date (intake + regular), so commission is on the combined total.
 *  - Buyer ledger never touched.
 */
@Service
public class FlowerIntakeSaleServiceImpl implements FlowerIntakeSaleService {

    private static final Logger logger = LoggerFactory.getLogger(FlowerIntakeSaleServiceImpl.class);

    private final DataSource dataSource;
    private final SalesDao salesDao;
    private final SalesEditDao salesEditDao;
    private final IntakeDirectSaleDao intakeDirectSaleDao;
    private final FarmerMasterDao farmerMasterDao;
    private final FlowerMasterDao flowerMasterDao;
    private final BuyerMasterDao buyerMasterDao;
    private final FarmerLedgerDao farmerLedgerDao;
    private final ClientSlotSequenceDao clientSlotSequenceDao;
    private final LedgerSettlementService ledgerSettlementService;
    private final FarmerCommissionConfigService commissionConfigService;

    @Autowired
    public FlowerIntakeSaleServiceImpl(DataSource dataSource,
                                       SalesDao salesDao,
                                       SalesEditDao salesEditDao,
                                       IntakeDirectSaleDao intakeDirectSaleDao,
                                       FarmerMasterDao farmerMasterDao,
                                       FlowerMasterDao flowerMasterDao,
                                       BuyerMasterDao buyerMasterDao,
                                       FarmerLedgerDao farmerLedgerDao,
                                       ClientSlotSequenceDao clientSlotSequenceDao,
                                       LedgerSettlementService ledgerSettlementService,
                                       FarmerCommissionConfigService commissionConfigService) {
        this.dataSource = dataSource;
        this.salesDao = salesDao;
        this.salesEditDao = salesEditDao;
        this.intakeDirectSaleDao = intakeDirectSaleDao;
        this.farmerMasterDao = farmerMasterDao;
        this.flowerMasterDao = flowerMasterDao;
        this.buyerMasterDao = buyerMasterDao;
        this.farmerLedgerDao = farmerLedgerDao;
        this.clientSlotSequenceDao = clientSlotSequenceDao;
        this.ledgerSettlementService = ledgerSettlementService;
        this.commissionConfigService = commissionConfigService;
    }

    @Override
    public List<Sales> saveFlowerIntakeSale(SalesRequestDto request, Long clientId, String clientUsername) {
        String farmerName = request.getFarmerName();
        logger.info("saveFlowerIntakeSale: entering for farmer={}, date={}, rows={}",
                farmerName, request.getSalesDate(), request.getRows() == null ? 0 : request.getRows().size());

        if (farmerName == null || farmerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Farmer name is required");
        }
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new IllegalArgumentException("At least one sales row is required");
        }

        String farmerId = farmerMasterDao.findIdByNameAndClientId(clientId, farmerName.trim());
        if (farmerId == null) {
            throw new IllegalArgumentException("Farmer name '" + farmerName + "' not found in Farmer Master. Please add it there first.");
        }

        BigDecimal debitAmount = RoundOffUtil.round(parseBigDecimal(request.getDebitAmount()));
        String flag = (debitAmount.compareTo(BigDecimal.ZERO) == 0) ? "C" : "D";

        LocalDate date = SalesUtil.parseDate(request.getSalesDate());

        List<String> validFlowers = flowerMasterDao.findNamesByClientId(clientId);
        Set<String> flowerSet = validFlowers.stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        List<Sales> entities = new ArrayList<>();
        for (SalesLineDto line : request.getRows()) {
            if (line.getFlowerType() == null || line.getFlowerType().trim().isEmpty()) {
                throw new IllegalArgumentException("Flower type is required");
            }
            if (!flowerSet.contains(line.getFlowerType().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Flower '" + line.getFlowerType() + "' not found in Flower Master. Please add it there first.");
            }
            if (line.getAmount() == null || line.getAmount().trim().isEmpty()
                    || !SalesUtil.isDecimal(line.getAmount())
                    || new BigDecimal(line.getAmount().trim()).compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
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
            sales.setFarmerName(farmerName.trim());
            sales.setSalesDate(date);
            sales.setFlowerType(line.getFlowerType().trim());
            sales.setFlowerId(flowerId);
            sales.setBagCount(line.getBagCount());
            sales.setTotalWeight(weight);
            sales.setPrice(amount);
            sales.setPerKgRate(rate);
            sales.setBuyerId(null);
            sales.setCustName(SalesRecordType.FLOWER_INTAKE_SALE_MARKER);
            sales.setDebitCreditFlag(flag);
            entities.add(sales);
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

            List<Sales> saved = salesDao.saveBatch(entities, conn);
            logger.info("saveFlowerIntakeSale: sales batch inserted, count={}", saved.size());

            recalcFarmerAggregate(clientId, clientUsername, farmerId, farmerName.trim(), date, debitAmount, conn);

            conn.commit();
            logger.info("saveFlowerIntakeSale: committed, slotId={}, saved={}", saleSlotId, saved.size());
            return saved;
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    logger.error("saveFlowerIntakeSale: rollback failed", rb);
                }
            }
            logger.error("saveFlowerIntakeSale: failed, rolling back", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (Exception c) {
                    logger.error("saveFlowerIntakeSale: close failed", c);
                }
            }
        }
    }

    /**
     * Recompute the farmer's aggregate from ALL rows of the day, overwrite ledger credit + summary.
     * Debit is the amount passed from the screen (as Sales Entry stores it), audit row recorded when > 0.
     */
    private void recalcFarmerAggregate(Long clientId, String clientUsername, String farmerId, String farmerName,
                                       LocalDate date, BigDecimal debitAmount, Connection conn) {
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
        total = RoundOffUtil.round(total);

        BigDecimal commission = RoundOffUtil.round(
                total.multiply(commissionConfigService.getCommissionPercent(clientId).movePointLeft(2)));
        BigDecimal net = RoundOffUtil.round(total.subtract(commission));
        BigDecimal effectiveDebit = resolveDebit(clientId, farmerId, date, debitAmount);
        BigDecimal finalAmt = RoundOffUtil.round(net.subtract(effectiveDebit));

        salesEditDao.reconcileSummary(clientId, clientUsername, farmerId, storedName,
                date, total, commission, net, effectiveDebit, finalAmt, conn);

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
                farmerId, date, dayRows.size(), total, commission, net, effectiveDebit, finalAmt);
    }

    /**
     * The intake screen has no debit input, so a debit recorded earlier for the same farmer/date
     * (from Farmer Transaction) must not be wiped when the sale is saved. Prefer any debit coming
     * from the request, otherwise keep the debit already stored on the day's summary row.
     */
    private BigDecimal resolveDebit(Long clientId, String farmerId, LocalDate date, BigDecimal requestDebit) {
        if (requestDebit != null && requestDebit.compareTo(BigDecimal.ZERO) > 0) {
            return requestDebit;
        }
        Map<String, Object> summaryRow = salesEditDao.fetchSummary(clientId, farmerId, date);
        if (summaryRow != null && summaryRow.get("debitAmt") != null) {
            return RoundOffUtil.round(new BigDecimal(String.valueOf(summaryRow.get("debitAmt"))));
        }
        return BigDecimal.ZERO;
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
