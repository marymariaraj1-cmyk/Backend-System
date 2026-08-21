package com.billing.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class CurrencyFormatUtil {

    private static final Locale INDIAN_LOCALE = new Locale("en", "IN");

    private CurrencyFormatUtil() {
    }

    public static String formatAmount(BigDecimal value) {
        BigDecimal rounded = RoundOffUtil.round(value);
        NumberFormat nf = NumberFormat.getNumberInstance(INDIAN_LOCALE);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(true);
        return "\u20B9 " + nf.format(rounded);
    }

    public static String formatAmount(double value) {
        return formatAmount(BigDecimal.valueOf(value));
    }

    public static String formatAmount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "\u20B9 0.00";
        }
        try {
            return formatAmount(new BigDecimal(value.trim()));
        } catch (NumberFormatException e) {
            return "\u20B9 0.00";
        }
    }
}
