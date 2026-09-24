package com.billing.service;

import com.billing.dto.VayalAgroPriceData;

import java.util.List;
import java.util.Map;

public interface VayalAgroPriceService {

    /** Fetch the latest available Salem flower market prices from the Vayal Agro API (all pages merged). */
    VayalAgroPriceData getSalemFlowerPrices();

    /** Districts (markets) for category 35 (Flowers). */
    List<Map<String, Object>> getDistricts();

    /** Cities (market places) for a district market id. */
    List<Map<String, Object>> getCities(String marketId);

    /** Flower prices for an explicit district/city/date (all pages merged). */
    VayalAgroPriceData getPrices(String marketId, String marketPlaceId, String date);

    /**
     * Price history for a flower in a city: resolves subcategoryid from the
     * flower name, then fetches recent prices. Returns a clean DTO with the
     * history sorted by date ascending.
     */
    Map<String, Object> getPriceHistory(String flowerName, String marketPlaceId);
}