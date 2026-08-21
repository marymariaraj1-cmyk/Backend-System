package com.billing.service.impl;

import com.billing.dao.BuyerLedgerDao;
import com.billing.dao.BuyerSalesReportDao;
import com.billing.service.BuyerSalesReportService;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BuyerSalesReportServiceImpl implements BuyerSalesReportService {

    private static final Logger logger = LoggerFactory.getLogger(BuyerSalesReportServiceImpl.class);

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

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BuyerSalesReportDao buyerSalesReportDao;
    private final BuyerLedgerDao buyerLedgerDao;

    public BuyerSalesReportServiceImpl(BuyerSalesReportDao buyerSalesReportDao,
                                       BuyerLedgerDao buyerLedgerDao) {
        this.buyerSalesReportDao = buyerSalesReportDao;
        this.buyerLedgerDao = buyerLedgerDao;
    }

    @Override
    public List<Map<String, Object>> getBuyerSalesByDate(Long clientId, String clientUsername, String buyerId, LocalDate date, String ledgerActive) {
        logger.info("getBuyerSalesByDate: clientId={}, buyerId={}, date={}, ledgerActive={}", clientId, buyerId, date, ledgerActive);
        String salesIdsStr = buyerLedgerDao.findSalesIds(clientId, buyerId, date, ledgerActive);
        if (salesIdsStr == null || salesIdsStr.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = parseSalesIds(salesIdsStr);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return buyerSalesReportDao.findSalesByIds(clientId, ids);
    }

    private List<Long> parseSalesIds(String salesIdsStr) {
        if (salesIdsStr == null || salesIdsStr.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = salesIdsStr.split(",");
        List<Long> ids = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    ids.add(Long.parseLong(trimmed));
                } catch (NumberFormatException e) {
                    logger.warn("parseSalesIds: skipping invalid ID '{}'", trimmed);
                }
            }
        }
        return ids;
    }

    @Override
    public List<Map<String, Object>> getBuyerSalesReport(Long clientId, String clientUsername, LocalDate fromDate, LocalDate toDate) {
        logger.info("getBuyerSalesReport: clientId={}, clientUsername={}, fromDate={}, toDate={}", clientId, clientUsername, fromDate, toDate);

        List<Map<String, Object>> summaryRows = buyerSalesReportDao.findBuyerSummaryByDateRange(clientId, clientUsername, fromDate, toDate);
        List<Map<String, Object>> discountRows = buyerSalesReportDao.findDiscountByDateRange(clientId, fromDate, toDate);
        List<Map<String, Object>> rawDetailRows = buyerSalesReportDao.findSalesDetailByDateRange(clientId, clientUsername, fromDate, toDate);

        Map<String, BigDecimal> discountByBuyer = new LinkedHashMap<>();
        for (Map<String, Object> discount : discountRows) {
            String buyerId = (String) discount.get("buyerId");
            BigDecimal discountAmt = discount.get("discount") != null ? (BigDecimal) discount.get("discount") : BigDecimal.ZERO;
            discountByBuyer.put(buyerId, discountAmt);
        }

        Map<String, List<Map<String, Object>>> itemsByBuyer = new LinkedHashMap<>();
        for (Map<String, Object> detail : rawDetailRows) {
            String buyerId = (String) detail.get("buyerId");
            itemsByBuyer.computeIfAbsent(buyerId, k -> new java.util.ArrayList<>()).add(detail);
        }

        List<Map<String, Object>> report = new java.util.ArrayList<>();
        for (Map<String, Object> summary : summaryRows) {
            String buyerId = (String) summary.get("buyerId");
            String buyerName = (String) summary.get("buyerName");

            if (isDirectPayment(buyerName)) {
                continue;
            }

            Map<String, Object> buyerRow = new LinkedHashMap<>();
            buyerRow.put("buyerId", buyerId);
            buyerRow.put("buyerName", buyerName);
            buyerRow.put("totalWeight", RoundOffUtil.round((BigDecimal) summary.get("totalWeight")));
            buyerRow.put("discount", RoundOffUtil.round(discountByBuyer.getOrDefault(buyerId, BigDecimal.ZERO)));
            buyerRow.put("totalAmount", RoundOffUtil.round((BigDecimal) summary.get("totalAmount")));
            buyerRow.put("items", itemsByBuyer.getOrDefault(buyerId, new java.util.ArrayList<>()));

            report.add(buyerRow);
        }

        logger.info("getBuyerSalesReport: buyers={}", report.size());
        return report;
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
}
