package com.billing.service.impl;

import com.billing.dto.FlowerPriceRow;
import com.billing.dto.VayalAgroPriceData;
import com.billing.service.VayalAgroPriceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VayalAgroPriceServiceImpl implements VayalAgroPriceService {

    private static final Logger logger = LoggerFactory.getLogger(VayalAgroPriceServiceImpl.class);

    private static final String BASE_URL = "https://vaiyal-app.herokuapp.com";
    private static final String PLACE_MARKET_URL = BASE_URL + "/getplacemarket";
    private static final String DISTRICT_URL = BASE_URL + "/getdistrict";
    private static final String CITIES_URL = BASE_URL + "/getcities";
    private static final String LISTS_URL = BASE_URL + "/get/lists1";
    private static final String RECENT_PRICE_URL = BASE_URL + "/subcategory/getrecentprice";
    private static final String CATEGORY_ID = "35";
    private static final String MARKET_ID = "5";
    private static final String MARKET_PLACE_ID = "21";
    private static final int MAX_BACKDATE_DAYS = 7;
    private static final long SUBCATEGORY_CACHE_TTL_MS = 15 * 60 * 1000L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedSubcategory> subcategoryCache = new ConcurrentHashMap<>();

    @Override
    public VayalAgroPriceData getSalemFlowerPrices() {
        LocalDate date = LocalDate.now();
        for (int attempt = 0; attempt < MAX_BACKDATE_DAYS; attempt++) {
            List<FlowerPriceRow> rows = fetchAllPages(MARKET_ID, MARKET_PLACE_ID, date.toString());
            if (!rows.isEmpty()) {
                return new VayalAgroPriceData(date.toString(), rows);
            }
            date = date.minusDays(1);
        }
        return new VayalAgroPriceData(LocalDate.now().toString(), List.of());
    }

    @Override
    public List<Map<String, Object>> getDistricts() {
        logger.info("getDistricts");
        String url = DISTRICT_URL + "?type=market&categoryid=" + CATEGORY_ID;
        JsonNode root = getJson(url);
        List<Map<String, Object>> results = new ArrayList<>();
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return results;
        }
        for (JsonNode item : data) {
            String marketId = textOf(item, "market_id", "marketid", "id");
            if (marketId.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("marketId", marketId);
            row.put("marketName", textOf(item, "market_name", "marketname", "name"));
            row.put("marketNameTn", textOf(item, "tn_name", "tnname", "tamil_name"));
            results.add(row);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> getCities(String marketId) {
        logger.info("getCities: marketId={}", marketId);
        requireNonBlank(marketId, "District is required");
        String url = CITIES_URL + "?type=market&categoryid=" + CATEGORY_ID + "&marketid=" + encode(marketId);
        JsonNode root = getJson(url);
        List<Map<String, Object>> results = new ArrayList<>();
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return results;
        }
        for (JsonNode item : data) {
            String marketPlaceId = textOf(item, "market_place_id", "marketplaceid", "place_id", "id");
            if (marketPlaceId.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("marketPlaceId", marketPlaceId);
            row.put("place", textOf(item, "place", "placename", "name"));
            row.put("placeTn", textOf(item, "tn_place", "tnplace", "tamil_place"));
            results.add(row);
        }
        return results;
    }

    @Override
    public VayalAgroPriceData getPrices(String marketId, String marketPlaceId, String date) {
        logger.info("getPrices: marketId={}, marketPlaceId={}, date={}", marketId, marketPlaceId, date);
        requireNonBlank(marketId, "District is required");
        requireNonBlank(marketPlaceId, "City is required");
        requireNonBlank(date, "Date is required");
        List<FlowerPriceRow> rows = fetchAllPages(marketId.trim(), marketPlaceId.trim(), date.trim());
        return new VayalAgroPriceData(date.trim(), rows);
    }

    @Override
    public Map<String, Object> getPriceHistory(String flowerName, String marketPlaceId) {
        logger.info("getPriceHistory: flowerName={}, marketPlaceId={}", flowerName, marketPlaceId);
        requireNonBlank(flowerName, "Flower name is required");
        requireNonBlank(marketPlaceId, "City is required");
        String subcategoryId = resolveSubcategoryId(flowerName.trim());
        JsonNode root = postJson(RECENT_PRICE_URL,
                "{\"subcategoryid\":\"" + escapeJson(subcategoryId)
                        + "\",\"marketplaceid\":\"" + escapeJson(marketPlaceId.trim()) + "\"}");
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() == 0) {
            throw new IllegalStateException("No price history available for this flower");
        }
        JsonNode details = root.path("details");
        List<Map<String, Object>> history = new ArrayList<>();
        for (JsonNode item : data) {
            String created = item.path("created").asText("");
            String date = created.length() >= 10 ? created.substring(0, 10) : created;
            if (date.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date);
            row.put("price", textOf(item, "price"));
            row.put("units", textOf(item, "quantity_type", "units"));
            row.put("unitsTn", textOf(item, "quantity_type_tamil", "units_tamil", "tn_units"));
            history.add(row);
        }
        if (history.isEmpty()) {
            throw new IllegalStateException("No price history available for this flower");
        }
        history.sort((a, b) -> String.valueOf(a.get("date")).compareTo(String.valueOf(b.get("date"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("flowerName", textOf(details, "sub_category_name", "flower_name"));
        out.put("flowerNameTn", textOf(details, "tn_sub_name", "tn_name"));
        out.put("marketName", textOf(details, "market_name"));
        out.put("marketNameTn", textOf(details, "tn_name"));
        out.put("history", history);
        return out;
    }

    private String resolveSubcategoryId(String flowerName) {
        String key = flowerName.toLowerCase();
        CachedSubcategory cached = subcategoryCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.resolvedAt < SUBCATEGORY_CACHE_TTL_MS) {
            return cached.subcategoryId;
        }
        JsonNode root = getJson(LISTS_URL + "?search=" + encode(flowerName));
        JsonNode data = root.path("data");
        List<JsonNode> matches = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode item : data) {
                if (!"35".equals(item.path("category_id").asText(""))
                        || !"market price".equalsIgnoreCase(item.path("category_type").asText(""))) {
                    continue;
                }
                if (flowerName.equalsIgnoreCase(item.path("sub_category_name").asText("").trim())) {
                    matches.add(item);
                }
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("Could not resolve price history reference for this flower");
        }
        String subcategoryId = matches.get(0).path("sub_category_id").asText("");
        if (subcategoryId.isEmpty()) {
            throw new IllegalStateException("Could not resolve price history reference for this flower");
        }
        subcategoryCache.put(key, new CachedSubcategory(subcategoryId, System.currentTimeMillis()));
        return subcategoryId;
    }

    private List<FlowerPriceRow> fetchAllPages(String marketId, String marketPlaceId, String created) {
        JsonNode firstPage = fetchPricePage(1, marketId, marketPlaceId, created);
        int pages = firstPage.path("pages").asInt(1);
        if (pages < 1) {
            pages = 1;
        }

        List<FlowerPriceRow> rows = new ArrayList<>();
        collectRows(firstPage, rows);
        for (int page = 2; page <= pages; page++) {
            collectRows(fetchPricePage(page, marketId, marketPlaceId, created), rows);
        }
        return rows;
    }

    private JsonNode fetchPricePage(int page, String marketId, String marketPlaceId, String created) {
        String body = "{\"categoryid\":\"" + CATEGORY_ID
                + "\",\"marketid\":\"" + escapeJson(marketId)
                + "\",\"marketplaceid\":\"" + escapeJson(marketPlaceId)
                + "\",\"created\":\"" + escapeJson(created) + "\"}";
        return postJson(PLACE_MARKET_URL + "?page=" + page, body);
    }

    private JsonNode getJson(String url) {
        try {
            Connection connection = Jsoup.connect(url)
                    .timeout(20000)
                    .ignoreContentType(true)
                    .header("Accept", "application/json")
                    .header("Origin", "https://vayalagro.com")
                    .header("Referer", "https://vayalagro.com/")
                    .method(Connection.Method.GET);
            JsonNode root = objectMapper.readTree(connection.execute().body());
            assertSuccess(root);
            return root;
        } catch (Exception e) {
            logger.error("upstream GET failed: url={}", url, e);
            throw new RuntimeException("Failed to fetch data from Vayal Agro. Please try again later.", e);
        }
    }

    private JsonNode postJson(String url, String body) {
        try {
            Connection connection = Jsoup.connect(url)
                    .timeout(20000)
                    .ignoreContentType(true)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Origin", "https://vayalagro.com")
                    .header("Referer", "https://vayalagro.com/")
                    .requestBody(body);
            JsonNode root = objectMapper.readTree(connection.post().body().text());
            assertSuccess(root);
            return root;
        } catch (Exception e) {
            logger.error("upstream POST failed: url={}", url, e);
            throw new RuntimeException("Failed to fetch data from Vayal Agro. Please try again later.", e);
        }
    }

    private void assertSuccess(JsonNode root) {
        JsonNode status = root.path("status");
        if (!status.isMissingNode() && !"success".equalsIgnoreCase(status.asText())) {
            throw new IllegalStateException("Upstream returned status: " + status.asText());
        }
    }

    private void collectRows(JsonNode root, List<FlowerPriceRow> rows) {
        JsonNode dates = root.path("data").path("Dates");
        if (!dates.isArray()) {
            return;
        }
        for (JsonNode record : dates) {
            rows.add(new FlowerPriceRow(
                    record.path("sub_category_name").asText(""),
                    record.path("tn_sub_name").asText(""),
                    record.path("market_name").asText(""),
                    record.path("tn_name").asText(""),
                    record.path("place").asText(""),
                    record.path("tn_place").asText(""),
                    record.path("price").asText(""),
                    record.path("quantity_type").asText(""),
                    record.path("quantity_type_tamil").asText("")));
        }
    }

    private String textOf(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (!child.isMissingNode() && !child.isNull()) {
                String text = child.asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static final class CachedSubcategory {
        private final String subcategoryId;
        private final long resolvedAt;

        private CachedSubcategory(String subcategoryId, long resolvedAt) {
            this.subcategoryId = subcategoryId;
            this.resolvedAt = resolvedAt;
        }
    }
}
