package com.billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RoundOffUtil {

    private static final int SCALE = 0;

    private static final BigDecimal ROUND_UP_THRESHOLD = new BigDecimal("0.60");

    private RoundOffUtil() {
    }

    public static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal abs = value.abs();
        BigDecimal fraction = abs.remainder(BigDecimal.ONE);
        BigDecimal roundedAbs;
        if (fraction.compareTo(ROUND_UP_THRESHOLD) >= 0) {
            roundedAbs = abs.setScale(SCALE, RoundingMode.CEILING);
        } else {
            roundedAbs = abs.setScale(SCALE, RoundingMode.FLOOR);
        }
        return value.signum() < 0 ? roundedAbs.negate() : roundedAbs;
    }

    public static double round(double value) {
        return round(BigDecimal.valueOf(value)).doubleValue();
    }

    public static String round(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "0";
        }
        try {
            return round(new BigDecimal(value.trim())).toPlainString();
        } catch (NumberFormatException e) {
            return value.trim();
        }
    }
}
