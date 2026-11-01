package com.billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SalesUtil {

    private SalesUtil() {
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String DEFAULT_BUSINESS_ZONE = "Asia/Kolkata";

    private static volatile ZoneId businessZone = resolveBusinessZone();

    private static ZoneId resolveBusinessZone() {
        String configured = System.getProperty("app.timezone");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("APP_TIMEZONE");
        }
        if (configured == null || configured.trim().isEmpty()) {
            configured = DEFAULT_BUSINESS_ZONE;
        }
        return ZoneId.of(configured.trim());
    }

    public static void setBusinessZone(String zoneId) {
        if (zoneId != null && !zoneId.trim().isEmpty()) {
            businessZone = ZoneId.of(zoneId.trim());
        }
    }

    public static ZoneId businessZone() {
        return businessZone;
    }

    public static LocalDate today() {
        return LocalDate.now(businessZone);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(businessZone);
    }

    public static final String DECIMAL_PATTERN = "^\\d+(\\.\\d+)?$";

    public static final String BUYER_SIDE_FILTER = " AND BUYER_ID IS NOT NULL";
    public static final String FARMER_SIDE_FILTER = " AND FARMER_ID IS NOT NULL";

    public static boolean isDecimal(String value) {
        if (value == null) {
            return false;
        }
        return value.trim().matches(DECIMAL_PATTERN);
    }

    public static BigDecimal calculateTotal(BigDecimal weight, BigDecimal price) {
        if (weight == null || price == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return weight.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    public static String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DATE_FORMATTER);
    }

    public static LocalDate parseDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return today();
        }
        return LocalDate.parse(date.trim(), DATE_FORMATTER);
    }
}
