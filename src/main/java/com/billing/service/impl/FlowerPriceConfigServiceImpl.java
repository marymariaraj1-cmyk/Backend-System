package com.billing.service.impl;

import com.billing.dao.FlowerPriceConfigDao;
import com.billing.service.FlowerPriceConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FlowerPriceConfigServiceImpl implements FlowerPriceConfigService {

    private static final Logger logger = LoggerFactory.getLogger(FlowerPriceConfigServiceImpl.class);

    private final FlowerPriceConfigDao flowerPriceConfigDao;

    public FlowerPriceConfigServiceImpl(FlowerPriceConfigDao flowerPriceConfigDao) {
        this.flowerPriceConfigDao = flowerPriceConfigDao;
    }

    @Override
    public List<Map<String, Object>> getFlowers(Long clientId) {
        return flowerPriceConfigDao.findAllFlowers(clientId);
    }

    @Override
    public List<Map<String, Object>> getPrices(Long clientId, LocalDate priceDate) {
        return flowerPriceConfigDao.findByClientAndDate(clientId, priceDate);
    }

    @Override
    public Map<String, Object> savePrices(Long clientId, String clientUsername, LocalDate priceDate,
                                          List<Map<String, Object>> items) {
        if (priceDate == null) {
            throw new IllegalArgumentException("Sales date is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("No prices entered");
        }
        int added = 0;
        int updated = 0;
        for (Map<String, Object> item : items) {
            Object flowerIdObj = item.get("flowerId");
            Object flowerNameObj = item.get("flowerName");
            Object priceObj = item.get("price");
            if (flowerIdObj == null || String.valueOf(flowerIdObj).trim().isEmpty()) {
                throw new IllegalArgumentException("Flower id is required");
            }
            String flowerId = String.valueOf(flowerIdObj).trim();
            String flowerName = flowerNameObj == null ? "" : String.valueOf(flowerNameObj).trim();
            BigDecimal price = parsePrice(priceObj);
            Long existingId = flowerPriceConfigDao.findIdByClientFlowerDate(clientId, flowerId, priceDate);
            if (existingId == null) {
                flowerPriceConfigDao.insert(clientId, clientUsername, flowerId, flowerName, priceDate, price, clientUsername);
                added++;
            } else {
                flowerPriceConfigDao.updateByClientFlowerDate(clientId, flowerName, priceDate, price, clientUsername, flowerId);
                updated++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", added);
        result.put("updated", updated);
        return result;
    }

    @Override
    public void updatePrice(Long clientId, String updatedBy, Long priceConfigId, BigDecimal price) {
        if (priceConfigId == null) {
            throw new IllegalArgumentException("Price record id is required");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Enter a valid positive price");
        }
        flowerPriceConfigDao.updatePrice(clientId, priceConfigId, price, updatedBy);
    }

    @Override
    public void deletePrice(Long clientId, Long priceConfigId) {
        if (priceConfigId == null) {
            throw new IllegalArgumentException("Price record id is required");
        }
        flowerPriceConfigDao.delete(clientId, priceConfigId);
    }

    @Override
    public List<Map<String, Object>> getTickerData(Long clientId) {
        return flowerPriceConfigDao.findTickerData(clientId, LocalDate.now());
    }

    @Override
    public List<Map<String, Object>> getPriceHistory(Long clientId, String flowerId, LocalDate fromDate, LocalDate toDate) {
        if (flowerId == null || flowerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Flower id is required");
        }
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("From and to dates are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("From date must not be after To date");
        }
        return flowerPriceConfigDao.findPriceHistory(clientId, flowerId.trim(), fromDate, toDate);
    }

    private BigDecimal parsePrice(Object priceObj) {
        if (priceObj == null || String.valueOf(priceObj).trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a valid positive price");
        }
        try {
            BigDecimal price = new BigDecimal(String.valueOf(priceObj).trim());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Enter a valid positive price");
            }
            return price;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid positive price");
        }
    }
}