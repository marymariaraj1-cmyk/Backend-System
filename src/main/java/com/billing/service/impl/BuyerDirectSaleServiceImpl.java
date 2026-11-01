package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.BuyerMasterDao;
import com.billing.dao.ClientSlotSequenceDao;
import com.billing.dao.FlowerMasterDao;
import com.billing.dao.SalesDao;
import com.billing.dto.BuyerDirectSaleDto;
import com.billing.dto.BuyerDirectSaleLineDto;
import com.billing.entity.BuyerLedger;
import com.billing.entity.Sales;
import com.billing.service.BuyerDirectSaleService;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Buyer Direct Sale (save). Buyer side only, no commission:
 *  - SALES rows written like Sales Entry but with FARMER_NAME = marker and FARMER_ID NULL.
 *  - Buyer ledger updated per row (debit = buyer's amount for the date), opening carry-forward
 *    and running balances handled by LedgerSettlementService, exactly as Multiple Sales Entry.
 *  - Farmer ledger and BLOOMBUDDY_SALES_TOTALSUMMARY never touched.
 */
@Service
public class BuyerDirectSaleServiceImpl implements BuyerDirectSaleService {

    private static final Logger logger = LoggerFactory.getLogger(BuyerDirectSaleServiceImpl.class);

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
    private final SalesDao salesDao;
    private final BuyerMasterDao buyerMasterDao;
    private final FlowerMasterDao flowerMasterDao;
    private final BuyerLedgerDao buyerLedgerDao;
    private final ClientSlotSequenceDao clientSlotSequenceDao;
    private final LedgerSettlementService ledgerSettlementService;

    @Autowired
    public BuyerDirectSaleServiceImpl(DataSource dataSource,
                                      SalesDao salesDao,
                                      BuyerMasterDao buyerMasterDao,
                                      FlowerMasterDao flowerMasterDao,
                                      BuyerLedgerDao buyerLedgerDao,
                                      ClientSlotSequenceDao clientSlotSequenceDao,
                                      LedgerSettlementService ledgerSettlementService) {
        this.dataSource = dataSource;
        this.salesDao = salesDao;
        this.buyerMasterDao = buyerMasterDao;
        this.flowerMasterDao = flowerMasterDao;
        this.buyerLedgerDao = buyerLedgerDao;
        this.clientSlotSequenceDao = clientSlotSequenceDao;
        this.ledgerSettlementService = ledgerSettlementService;
    }

    @Override
    public List<Sales> saveBuyerDirectSale(BuyerDirectSaleDto request, Long clientId, String clientUsername) {
        String buyerName = request.getBuyerName();
        logger.info("saveBuyerDirectSale: entering for buyer={}, date={}, rows={}",
                buyerName, request.getSalesDate(), request.getRows() == null ? 0 : request.getRows().size());

        if (buyerName == null || buyerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Buyer name is required");
        }
        if (request.getRows() == null || request.getRows().isEmpty()) {
            throw new IllegalArgumentException("At least one sales row is required");
        }

        String buyerId = buyerMasterDao.findIdByNameAndClientId(clientId, buyerName.trim());
        if (buyerId == null) {
            throw new IllegalArgumentException("Buyer '" + buyerName + "' not found in Buyer Master. Please add it there first.");
        }

        LocalDate date = SalesUtil.parseDate(request.getSalesDate());

        List<String> validFlowers = flowerMasterDao.findNamesByClientId(clientId);
        Set<String> flowerSet = validFlowers.stream()
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        boolean isDirect = isDirectPayment(buyerName.trim());

        List<Sales> entities = new ArrayList<>();
        for (BuyerDirectSaleLineDto line : request.getRows()) {
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
            if (line.getRate() != null && !line.getRate().trim().isEmpty()) {
                rate = new BigDecimal(line.getRate().trim());
            }
            BigDecimal amount = RoundOffUtil.round(new BigDecimal(line.getAmount().trim()));

            Sales sales = new Sales();
            sales.setClientId(clientId);
            sales.setClientUsername(clientUsername);
            sales.setFarmerId(null);
            sales.setFarmerName(SalesRecordType.BUYER_DIRECT_SALE_MARKER);
            sales.setSalesDate(date);
            sales.setFlowerType(line.getFlowerType().trim());
            sales.setFlowerId(flowerId);
            sales.setBagCount(line.getBagCount());
            sales.setTotalWeight(weight);
            sales.setPrice(amount);
            sales.setPerKgRate(rate);
            sales.setBuyerId(buyerId);
            sales.setCustName(buyerName.trim());
            sales.setDebitCreditFlag("C");
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
            logger.info("saveBuyerDirectSale: sales batch inserted, count={}", saved.size());

            for (Sales sales : saved) {
                BigDecimal rowAmount = sales.getPrice();

                BuyerLedger buyerLedger = new BuyerLedger();
                buyerLedger.setClientId(clientId);
                buyerLedger.setClientUsername(clientUsername);
                buyerLedger.setBuyerId(buyerId);
                buyerLedger.setBuyerName(sales.getCustName());
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
            logger.info("saveBuyerDirectSale: committed, slotId={}, saved={}", saleSlotId, saved.size());
            return saved;
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    logger.error("saveBuyerDirectSale: rollback failed", rb);
                }
            }
            logger.error("saveBuyerDirectSale: failed, rolling back", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (Exception c) {
                    logger.error("saveBuyerDirectSale: close failed", c);
                }
            }
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
}
