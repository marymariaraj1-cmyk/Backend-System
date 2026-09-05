package com.billing.service.impl;

import com.billing.dao.InactiveListDao;
import com.billing.service.InactiveListService;
import com.billing.util.RoundOffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class InactiveListServiceImpl implements InactiveListService {

    private static final Logger logger = LoggerFactory.getLogger(InactiveListServiceImpl.class);

    private static final Set<String> EXCLUDED_BUYER_NAMES = Set.of(
            "cash", "upi", "google pay", "gpay", "phonepe", "paytm", "online", "card", "neft", "rtgs", "imps");

    private final InactiveListDao inactiveListDao;

    public InactiveListServiceImpl(InactiveListDao inactiveListDao) {
        this.inactiveListDao = inactiveListDao;
    }

    @Override
    public List<Map<String, Object>> getInactiveFarmers(Long clientId) {
        logger.info("getInactiveFarmers: clientId={}", clientId);
        List<Map<String, Object>> rows = inactiveListDao.findInactiveFarmers(clientId, LocalDate.now());
        for (Map<String, Object> row : rows) {
            row.put("outstandingBalance", toAmount(row.get("outstandingBalance")));
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> getInactiveBuyers(Long clientId) {
        logger.info("getInactiveBuyers: clientId={}", clientId);
        List<Map<String, Object>> rows = inactiveListDao.findInactiveBuyers(clientId, LocalDate.now());
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String buyerName = row.get("buyerName") == null ? "" : row.get("buyerName").toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED_BUYER_NAMES.stream().anyMatch(buyerName::contains)) {
                continue;
            }
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("outstandingBalance", toAmount(out.get("outstandingBalance")));
            filtered.add(out);
        }
        return filtered;
    }

    private BigDecimal toAmount(Object value) {
        if (value instanceof BigDecimal) {
            return RoundOffUtil.round((BigDecimal) value);
        }
        return BigDecimal.ZERO;
    }
}