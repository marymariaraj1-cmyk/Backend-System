package com.billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class SalesUtil {

    private SalesUtil() {
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String DECIMAL_PATTERN = "^\\d+(\\.\\d+)?$";

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
            return LocalDate.now();
        }
        return LocalDate.parse(date.trim(), DATE_FORMATTER);
    }
}
