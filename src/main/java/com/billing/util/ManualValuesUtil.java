package com.billing.util;

import com.billing.dto.ManualAmountEntry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared build/parse logic for the dynamic manual-value columns
 * (MANUAL_DEBIT_VALUES / MANUAL_CREDIT_VALUES) of the Daily Cash Book.
 *
 * <p>Storage format: entries joined by "&&", each entry is "Label=Amount".
 * Only entries whose amount is greater than zero are written. Labels never
 * contain '=' or '&'. Amounts use plain decimal representation.
 */
public final class ManualValuesUtil {

    private static final int MAX_LABEL_LENGTH = 50;
    private static final int MAX_VALUES_LENGTH = 2000;

    private ManualValuesUtil() {
    }

    public static String normalizeLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("Field label is required");
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Field label is required");
        }
        if (trimmed.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Field label must be at most " + MAX_LABEL_LENGTH + " characters");
        }
        if (trimmed.indexOf('=') >= 0 || trimmed.indexOf('&') >= 0) {
            throw new IllegalArgumentException("Field label must not contain '=' or '&'");
        }
        return trimmed;
    }

    public static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amounts must not be negative");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amounts must have at most 2 decimal places");
        }
        return amount;
    }

    /**
     * Builds the stored string for a side. Silent entries (blank or 0 amount)
     * are omitted. Returns null when the side has no payable entries.
     */
    public static String build(List<ManualAmountEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ManualAmountEntry entry : entries) {
            String label = normalizeLabel(entry.getLabel());
            BigDecimal amount = normalizeAmount(entry.getAmount());
            if (amount.signum() <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("&&");
            }
            sb.append(label).append('=').append(amount.toPlainString());
        }
        if (sb.length() == 0) {
            return null;
        }
        if (sb.length() > MAX_VALUES_LENGTH) {
            throw new IllegalArgumentException("Manual values exceed the maximum allowed length");
        }
        return sb.toString();
    }

    /**
     * Parses a stored string back into entries, preserving their stored order.
     * Malformed pairs are skipped silently.
     */
    public static List<ManualAmountEntry> parse(String values) {
        List<ManualAmountEntry> entries = new ArrayList<>();
        if (values == null || values.trim().isEmpty()) {
            return entries;
        }
        for (String part : values.split("&&")) {
            if (part.trim().isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) {
                continue;
            }
            String label = part.substring(0, eq).trim();
            if (label.isEmpty()) {
                continue;
            }
            try {
                BigDecimal amount = new BigDecimal(part.substring(eq + 1).trim());
                if (amount.signum() >= 0) {
                    entries.add(new ManualAmountEntry(label, amount));
                }
            } catch (NumberFormatException ignored) {
                // skip malformed amount
            }
        }
        return entries;
    }

    /**
     * True when the stored values contain the given label on this side with a
     * non-zero amount. Exact key match on the trimmed label, case-insensitive.
     */
    public static boolean isUsed(String values, String label) {
        if (values == null || label == null) {
            return false;
        }
        String needle = label.trim();
        if (needle.isEmpty()) {
            return false;
        }
        for (ManualAmountEntry entry : parse(values)) {
            if (entry.getLabel().trim().equalsIgnoreCase(needle) && entry.getAmount() != null
                    && entry.getAmount().signum() > 0) {
                return true;
            }
        }
        return false;
    }
}